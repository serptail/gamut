package com.gamut.account

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable private data class AvatarRow(@SerialName("avatar_url") val avatarUrl: String? = null)

@Singleton
class AvatarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
) {
    private val prefs = context.getSharedPreferences("gamut_avatar", Context.MODE_PRIVATE)
    private val tick = MutableStateFlow(0)
    private val dir get() = File(context.filesDir, "avatars").also { it.mkdirs() }

    private fun key(userId: String?) = userId ?: "local"
    private fun urlKey(userId: String?) = "url_${key(userId)}"
    private fun dirtyKey(userId: String) = "dirty_$userId"
    private fun localFile(userId: String?): File? = dir.listFiles { f -> f.name.startsWith("${key(userId)}_") }?.maxByOrNull { it.lastModified() }

    fun model(userId: String?): Flow<Any?> = tick.map { localFile(userId) ?: prefs.getString(urlKey(userId), null) }

    suspend fun set(userId: String?, resolver: ContentResolver, uri: Uri): Result<Boolean> = runCatching {
        val bytes = withContext(Dispatchers.IO) { encode(resolver, uri) }
        withContext(Dispatchers.IO) {
            val old = dir.listFiles { f -> f.name.startsWith("${key(userId)}_") }.orEmpty()
            File(dir, "${key(userId)}_${System.currentTimeMillis()}.webp").writeBytes(bytes)
            old.forEach { it.delete() }
        }
        tick.value++
        if (userId == null) return@runCatching true
        prefs.edit().putBoolean(dirtyKey(userId), true).apply()
        runCatching { upload(userId, bytes) }.isSuccess
    }

    suspend fun remove(userId: String?) {
        withContext(Dispatchers.IO) { dir.listFiles { f -> f.name.startsWith("${key(userId)}_") }.orEmpty().forEach { it.delete() } }
        prefs.edit().remove(urlKey(userId)).apply()
        tick.value++
        if (userId == null) return
        runCatching {
            supabase.storage.from(BUCKET).delete("$userId.webp")
            supabase.from("profiles").update({ set("avatar_url", null as String?) }) { filter { eq("id", userId) } }
        }
    }

    suspend fun refresh(userId: String?) {
        if (userId == null) return
        runCatching {
            if (prefs.getBoolean(dirtyKey(userId), false)) {
                val file = localFile(userId)
                if (file != null) {
                    upload(userId, withContext(Dispatchers.IO) { file.readBytes() })
                    return@runCatching
                }
                prefs.edit().putBoolean(dirtyKey(userId), false).apply()
            }
            val remote = supabase.from("profiles").select(Columns.list("avatar_url")) { filter { eq("id", userId) } }
                .decodeSingleOrNull<AvatarRow>()?.avatarUrl
            if (remote != prefs.getString(urlKey(userId), null)) {
                prefs.edit().apply { if (remote == null) remove(urlKey(userId)) else putString(urlKey(userId), remote) }.apply()
                withContext(Dispatchers.IO) { dir.listFiles { f -> f.name.startsWith("${key(userId)}_") }.orEmpty().forEach { it.delete() } }
            }
            tick.value++
        }
    }

    suspend fun clearLocal() {
        withContext(Dispatchers.IO) { dir.listFiles().orEmpty().forEach { it.delete() } }
        prefs.edit().clear().apply()
        tick.value++
    }

    private suspend fun upload(uid: String, bytes: ByteArray) {
        val bucket = supabase.storage.from(BUCKET)
        bucket.upload("$uid.webp", bytes) {
            upsert = true
            contentType = ContentType.parse("image/webp")
        }
        val url = "${bucket.publicUrl("$uid.webp")}?v=${System.currentTimeMillis()}"
        supabase.from("profiles").update({ set("avatar_url", url) }) { filter { eq("id", uid) } }
        prefs.edit().putString(urlKey(uid), url).putBoolean(dirtyKey(uid), false).apply()
    }

    private fun encode(resolver: ContentResolver, uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val src = resolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Unreadable image")
        val rotation = resolver.openInputStream(uri)!!.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
        val upright = if (rotation == 0f) src else Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(rotation) }, true)
        val side = min(upright.width, upright.height)
        val square = Bitmap.createBitmap(upright, (upright.width - side) / 2, (upright.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)

        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        return ByteArrayOutputStream().also { out.compress(format, 85, it) }.toByteArray()
    }

    private companion object {
        const val BUCKET = "avatars"
        const val SIZE = 256
    }
}
