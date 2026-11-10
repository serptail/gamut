package com.gamut.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.gamut.BuildConfig
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long = 0L,
    val minVersionCode: Int = 0,
    val notes: String = "",
)

private val json = Json { ignoreUnknownKeys = true }

class UpdateChecker(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_update", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    val enabled: Boolean get() = BuildConfig.UPDATE_MANIFEST_URL.isNotBlank() && !BuildConfig.DEBUG

    suspend fun latest(allowCache: Boolean = true): UpdateInfo? = withContext(Dispatchers.IO) {
        val fresh = fetch()
        when {
            fresh == null -> if (allowCache) cached() else null
            !apkExists(fresh) -> null
            else -> fresh.also { prefs.edit().putString(KEY, json.encodeToString(UpdateInfo.serializer(), it)).apply() }
        }
    }

    private fun apkExists(info: UpdateInfo): Boolean = try {
        val request = Request.Builder().url(info.apkUrl).header("Range", "bytes=0-0").build()
        client.newCall(request).execute().use { response ->
            if (response.code in 400..499 && response.code != 429) {
                false
            } else {
                val total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                total == null || info.sizeBytes <= 0 || total == info.sizeBytes
            }
        }
    } catch (_: Exception) {
        true
    }

    private fun fetch(): UpdateInfo? = try {
        val request = Request.Builder().url(BuildConfig.UPDATE_MANIFEST_URL).header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            json.decodeFromString(UpdateInfo.serializer(), response.body?.string() ?: return@use null)
                .takeIf { it.versionCode > 0 && it.apkUrl.startsWith("https://github.com/") && it.sha256.length == 64 }
        }
    } catch (_: Exception) {
        null
    }

    private fun cached(): UpdateInfo? = prefs.getString(KEY, null)?.let {
        runCatching { json.decodeFromString(UpdateInfo.serializer(), it) }.getOrNull()
    }

    private companion object {
        const val KEY = "latest"
    }
}

class UpdateInstaller(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        if (!info.apkUrl.startsWith("https://github.com/")) throw IOException("Unexpected download address")
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val target = File(dir, "gamut-${info.versionCode}.apk")
        val part = File(dir, "gamut-${info.versionCode}.part")
        dir.listFiles()?.filter { it != target && it != part }?.forEach { it.delete() }
        if (target.exists() && sha256(target).equals(info.sha256, ignoreCase = true)) return@withContext target
        target.delete()

        val digest = MessageDigest.getInstance("SHA-256")
        var done = 0L
        if (part.exists()) {
            part.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                    done += n
                }
            }
            if (info.sizeBytes > 0 && done > info.sizeBytes) {
                part.delete()
                digest.reset()
                done = 0L
            }
        }

        if (info.sizeBytes <= 0 || done != info.sizeBytes) {
            val request = Request.Builder().url(info.apkUrl).apply { if (done > 0) header("Range", "bytes=$done-") }.build()
            client.newCall(request).execute().use { response ->
                if (response.code == 416) {
                    part.delete()
                    throw IOException("The download restarted. Trying again.")
                }
                if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
                val body = response.body ?: throw IOException("Empty download")
                val resumed = response.code == 206 && done > 0
                if (!resumed) {
                    digest.reset()
                    done = 0L
                }
                val total = info.sizeBytes.takeIf { it > 0 } ?: body.contentLength().takeIf { it > 0 }?.let { done + it } ?: 0L
                var lastPercent = -1
                if (total > 0) {
                    lastPercent = (done * 100 / total).toInt()
                    onProgress(lastPercent / 100f)
                }
                body.byteStream().use { input ->
                    java.io.FileOutputStream(part, resumed).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            done += n
                            if (total > 0) {
                                val percent = (done * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent / 100f)
                                }
                            }
                        }
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(info.sha256, ignoreCase = true)) {
            part.delete()
            throw IOException("The download is damaged. Try again.")
        }
        if (!part.renameTo(target)) throw IOException("Couldn't save the update")
        onProgress(1f)
        target
    }

    fun signerProblem(apk: File): String? {
        if (Build.VERSION.SDK_INT < 28) return null
        return runCatching {
            val pm = context.packageManager
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val incoming = pm.getPackageArchiveInfo(apk.path, flags)?.signingInfo?.apkContentsSigners ?: return null
            val installed = pm.getPackageInfo(context.packageName, flags).signingInfo?.apkContentsSigners ?: return null
            fun hashes(signatures: Array<android.content.pm.Signature>) = signatures.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toList() }.toSet()
            if (hashes(incoming).intersect(hashes(installed)).isEmpty()) {
                "This update is signed with a different key than the installed app. Uninstall Gamut once, then install the latest APK from GitHub."
            } else {
                null
            }
        }.getOrNull()
    }

    fun abandonSessions() {
        val installer = context.packageManager.packageInstaller
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
    }

    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        abandonSessions()
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("gamut.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val callback = Intent(context, UpdateInstallReceiver::class.java).setAction(ACTION_RESULT)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, callback, flags).intentSender)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val ACTION_RESULT = "com.gamut.update.INSTALL_RESULT"
    }
}

object UpdateStatusBus {
    private val _failures = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val failures: SharedFlow<String> = _failures

    fun fail(message: String) {
        _failures.tryEmit(message)
    }
}

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }

            PackageInstaller.STATUS_SUCCESS -> Unit

            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                UpdateStatusBus.fail(
                    when {
                        status == PackageInstaller.STATUS_FAILURE_ABORTED -> "The install was cancelled."

                        status == PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage to install the update."

                        status == PackageInstaller.STATUS_FAILURE_CONFLICT || detail.contains("INCOMPATIBLE", ignoreCase = true) ->
                            "This update is signed with a different key than the installed app. Uninstall Gamut once, then install the latest APK from GitHub."

                        else -> "The install failed${if (detail.isNotBlank()) ": $detail" else "."}"
                    },
                )
            }
        }
    }
}