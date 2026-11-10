package com.gamut.onboarding

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gamut.core.sync.DataExporter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object AutoBackup {
    private const val WORK = "gamut-auto-backup"
    private const val PREFIX = "gamut-backup-"
    private const val EXT = ".json"
    private const val KEEP = 10
    private val writeLock = Mutex()

    data class BackupFile(val file: File, val name: String, val lastModified: Long, val sizeBytes: Long)

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    fun apply(context: Context, enabled: Boolean) = if (enabled) schedule(context) else cancel(context)

    fun backupDir(context: Context) = File(context.filesDir, "backups")

    suspend fun deleteAll(context: Context) = writeLock.withLock {
        withContext(Dispatchers.IO) { backupDir(context).listFiles()?.forEach { it.delete() } }
    }

    fun list(context: Context): List<BackupFile> = backupDir(context).listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(EXT) }
        ?.sortedByDescending { it.name }
        ?.map { BackupFile(it, it.name, it.lastModified(), it.length()) }
        .orEmpty()

    suspend fun write(context: Context, exporter: DataExporter): File? = writeLock.withLock {
        val bytes = exporter.exportOrNull(password = null) ?: return@withLock null
        withContext(Dispatchers.IO) {
            val dir = backupDir(context).apply { mkdirs() }
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val target = File(dir, "$PREFIX$day$EXT")
            val tmp = File(dir, "$PREFIX$day.tmp")
            try {
                tmp.outputStream().use {
                    it.write(bytes)
                    it.flush()
                    it.fd.sync()
                }
                if (tmp.length() != bytes.size.toLong()) throw IOException("Backup was not written completely")
                if (!tmp.renameTo(target)) {
                    target.delete()
                    if (!tmp.renameTo(target)) throw IOException("Could not save the backup file")
                }
            } finally {
                tmp.delete()
            }
            dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
            list(context).drop(KEEP).forEach { it.file.delete() }
            target
        }
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun exporter(): DataExporter
    }

    override suspend fun doWork(): Result = try {
        val exporter = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).exporter()
        AutoBackup.write(applicationContext, exporter)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
