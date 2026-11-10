package com.gamut.feature.library

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamut.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

const val LOCAL_ISRC_PREFIX = "local:"

val Track.isLocal: Boolean get() = isrc.startsWith(LOCAL_ISRC_PREFIX)

fun List<Track>.withoutMissingLocal(existing: Set<String>?): List<Track> = if (existing == null) this else filter { !it.isLocal || it.isrc in existing }

fun Track.localUri(): Uri? {
    if (!isLocal) return null
    val id = isrc.removePrefix(LOCAL_ISRC_PREFIX).toLongOrNull() ?: return null
    return ContentUris.withAppendedId(audioCollection(), id)
}

private fun audioCollection(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
} else {
    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
}

private const val MIN_QUICK_MS = 30_000L
private const val MIN_DEEP_MS = 5_000L
private const val MIN_SCAN_UI_MS = 3_500L
internal const val UNKNOWN_ARTIST = "Unknown artist"

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "m4a", "m4b", "aac", "adts", "flac", "ogg", "oga", "opus", "wav", "aif", "aiff",
    "amr", "awb", "mka", "mp2", "ac3", "ec3", "3gp", "3ga",
)

val LOCAL_AUDIO_PERMISSION: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

data class MetaCandidate(val title: String, val artist: String, val album: String?, val artUrl: String?, val source: String)

sealed interface CoverEdit {
    object Keep : CoverEdit
    data class Url(val url: String) : CoverEdit
    data class Photo(val uri: Uri) : CoverEdit
}

data class LocalSong(val track: Track, val format: String, val sizeBytes: Long) {
    val needsInfo: Boolean get() = track.artist == UNKNOWN_ARTIST || track.album.isNullOrBlank()
}

private fun Track.hasRealArt(): Boolean = albumArtUrl?.let { it.startsWith("file:") || it.startsWith("http") } == true

internal fun Track.isInfoComplete(): Boolean = artist != UNKNOWN_ARTIST && !album.isNullOrBlank() && hasRealArt()

@Singleton
class LocalMetadataStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val file = File(context.filesDir, "local_song_info.json")
    private val pinPrefs = context.getSharedPreferences("local_music_pinned", Context.MODE_PRIVATE)
    private val map = HashMap<String, Track>()
    private var loaded = false

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    @Synchronized private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            if (!file.exists()) return@runCatching
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val isrc = o.getString("isrc")
                map[isrc] = Track(
                    isrc = isrc,
                    title = o.getString("title"),
                    artist = o.getString("artist"),
                    album = o.optString("album").ifBlank { null },
                    durationMs = o.optLong("durationMs"),
                    albumArtUrl = o.optString("art").ifBlank { null },
                )
            }
        }
    }

    @Synchronized fun pinnedIds(): Set<Long> = pinPrefs.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    @Synchronized fun pin(id: Long) {
        val next = pinPrefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet().apply { add(id.toString()) }
        pinPrefs.edit().putStringSet("ids", next).apply()
        _version.update { it + 1 }
    }

    @Synchronized fun get(isrc: String): Track? {
        ensureLoaded()
        return map[isrc]
    }

    @Synchronized fun snapshot(): Map<String, Track> {
        ensureLoaded()
        return HashMap(map)
    }

    @Synchronized fun put(track: Track) {
        ensureLoaded()
        map[track.isrc] = track
        runCatching {
            val arr = JSONArray()
            map.values.forEach { t ->
                arr.put(
                    JSONObject().apply {
                        put("isrc", t.isrc)
                        put("title", t.title)
                        put("artist", t.artist)
                        put("durationMs", t.durationMs)
                        t.album?.let { put("album", it) }
                        t.albumArtUrl?.let { put("art", it) }
                    },
                )
            }
            file.writeText(arr.toString())
        }
        _version.update { it + 1 }
    }
}

@Singleton
class LocalMetadataResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: LocalMetadataStore,
) {
    class Resolved(val track: Track, val found: Boolean)

    private fun Track.fillFrom(o: Track) = copy(
        artist = if (artist == UNKNOWN_ARTIST) o.artist else artist,
        album = if (album.isNullOrBlank()) o.album else album,
        albumArtUrl = if (hasRealArt()) albumArtUrl else o.albumArtUrl?.takeIf { o.hasRealArt() } ?: albumArtUrl,
    )

    private fun fileOriginal(track: Track): Track {
        val uri = track.localUri() ?: return track
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                fun col(i: Int) = c.getString(i)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                track.copy(
                    title = col(0) ?: col(3)?.substringBeforeLast('.') ?: "Untitled",
                    artist = col(1) ?: UNKNOWN_ARTIST,
                    album = col(2),
                    albumArtUrl = null,
                )
            }
        }.getOrNull() ?: track
    }

    suspend fun resolve(track: Track): Resolved = withContext(Dispatchers.IO) {
        if (!track.isLocal) return@withContext Resolved(track, true)
        val stored = store.get(track.isrc)?.copy(durationMs = track.durationMs)
        if (stored != null && stored.isInfoComplete()) return@withContext Resolved(stored, true)

        val file = fileOriginal(track)
        val emb = readEmbedded(track)
        var looked = file.copy(
            title = emb.title ?: file.title,
            artist = if (file.artist == UNKNOWN_ARTIST) emb.artist ?: file.artist else file.artist,
            album = file.album ?: emb.album,
            albumArtUrl = emb.art,
        )
        if (!looked.isInfoComplete()) online(looked)?.let { looked = it }

        val merged = (stored ?: looked).fillFrom(looked)
        val found = merged.artist != UNKNOWN_ARTIST
        if (found) store.put(merged)
        Resolved(merged, found)
    }

    private class Embedded(val title: String?, val artist: String?, val album: String?, val art: String?)

    private fun readEmbedded(track: Track): Embedded {
        val uri = track.localUri() ?: return Embedded(null, null, null, null)
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            fun tag(key: Int) = r.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }
            Embedded(
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                art = r.embeddedPicture?.let { saveArt(track.isrc, it) },
            )
        } catch (_: Exception) {
            Embedded(null, null, null, null)
        } finally {
            runCatching { r.release() }
        }
    }

    private fun saveArt(isrc: String, bytes: ByteArray, stamp: Boolean = false): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 800) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@runCatching null
        val dir = File(context.filesDir, "local_art").apply { mkdirs() }
        val file = File(dir, isrc.removePrefix(LOCAL_ISRC_PREFIX) + (if (stamp) "-${System.currentTimeMillis()}" else "") + ".jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        Uri.fromFile(file).toString()
    }.getOrNull()

    private fun importPhoto(isrc: String, uri: Uri): String? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
        saveArt(isrc, bytes, stamp = true)
    }.getOrNull()

    suspend fun saveEdit(track: Track, title: String, artist: String, cover: CoverEdit): Track = withContext(Dispatchers.IO) {
        val current = store.get(track.isrc)
        val art = when (cover) {
            CoverEdit.Keep -> current?.albumArtUrl ?: track.albumArtUrl
            is CoverEdit.Url -> cover.url
            is CoverEdit.Photo -> importPhoto(track.isrc, cover.uri) ?: current?.albumArtUrl ?: track.albumArtUrl
        }
        val t = track.copy(
            title = title.trim().ifBlank { track.title },
            artist = artist.trim().ifBlank { UNKNOWN_ARTIST },
            albumArtUrl = art,
        )
        store.put(t)
        t
    }

    fun autoFetch(track: Track): Flow<MetaCandidate> = flow {
        val seen = HashSet<String>()
        suspend fun offer(c: MetaCandidate) {
            if (seen.add(norm(c.title) + "|" + norm(c.artist))) emit(c)
        }

        val emb = readEmbedded(track)
        if (emb.title != null && emb.artist != null) {
            offer(MetaCandidate(emb.title, emb.artist, emb.album, emb.art, "File tags"))
        }

        val file = fileOriginal(track)
        val baseTitle = clean(file.title)
        var title = baseTitle
        var artist: String? = file.artist.takeIf { it != UNKNOWN_ARTIST }
        if (artist == null && " - " in title) {
            val left = title.substringBefore(" - ").trim()
            val right = title.substringAfter(" - ").trim()
            if (left.isNotEmpty() && right.isNotEmpty() && left.any { it.isLetter() }) {
                artist = left
                title = right
            }
        }
        val terms = listOf(listOfNotNull(artist, title).joinToString(" "), baseTitle, title).filter { it.isNotBlank() }.distinct()
        val want = norm(title)
        for (term in terms) {
            val hits = runCatching { searchCatalog(term) }.getOrNull() ?: continue
            val ranked = hits.mapNotNull { r ->
                val name = norm(r.optString("trackName"))
                var score = when {
                    name.isEmpty() || want.isEmpty() -> return@mapNotNull null
                    name == want -> 3
                    name.contains(want) || want.contains(name) -> 1
                    else -> return@mapNotNull null
                }
                val a = artist?.let { norm(it) }
                if (a != null && norm(r.optString("artistName")).let { it.contains(a) || a.contains(it) }) score += 2
                val dur = r.optLong("trackTimeMillis")
                if (track.durationMs > 0 && dur > 0) {
                    val diff = abs(dur - track.durationMs)
                    if (diff <= 6_000) {
                        score += 2
                    } else if (diff > 15_000) {
                        score -= 2
                    }
                }
                if (score >= 2) score to r else null
            }.sortedByDescending { it.first }
            for ((_, r) in ranked.take(5)) {
                offer(
                    MetaCandidate(
                        title = r.optString("trackName"),
                        artist = r.optString("artistName"),
                        album = r.optString("collectionName").ifBlank { null },
                        artUrl = r.optString("artworkUrl100").ifBlank { null }?.replace("100x100bb", "600x600bb"),
                        source = "Apple Music",
                    ),
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun searchCatalog(term: String): List<JSONObject> {
        val url = URL("https://itunes.apple.com/search?media=music&entity=song&limit=10&term=" + URLEncoder.encode(term, "UTF-8"))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 6_000
        }
        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun clean(raw: String): String = raw
        .replace('_', ' ')
        .replace(Regex("^\\s*\\d{1,3}\\s*[-.)]?\\s+"), "")
        .replace(Regex("\\s*[(\\[](official|lyrics?|video|audio|hd|hq|4k|remaster|visualizer)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun online(t: Track): Track? = runCatching {
        var title = clean(t.title)
        var artist: String? = t.artist.takeIf { it != UNKNOWN_ARTIST }
        if (artist == null && " - " in title) {
            val left = title.substringBefore(" - ").trim()
            val right = title.substringAfter(" - ").trim()
            if (left.isNotEmpty() && right.isNotEmpty() && left.any { it.isLetter() }) {
                artist = left
                title = right
            }
        }
        if (title.isBlank()) return@runCatching null
        val term = listOfNotNull(artist, title).joinToString(" ")
        val url = URL("https://itunes.apple.com/search?media=music&entity=song&limit=10&term=" + URLEncoder.encode(term, "UTF-8"))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 6_000
        }
        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val results = JSONObject(body).optJSONArray("results") ?: return@runCatching null

        var best: JSONObject? = null
        var bestScore = 0
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val name = norm(r.optString("trackName"))
            val want = norm(title)
            var score = when {
                name.isEmpty() || want.isEmpty() -> continue
                name == want -> 3
                name.contains(want) || want.contains(name) -> 1
                else -> continue
            }
            if (artist != null && norm(r.optString("artistName")).let { it.contains(norm(artist!!)) || norm(artist!!).contains(it) }) score += 2
            val dur = r.optLong("trackTimeMillis")
            if (t.durationMs > 0 && dur > 0) {
                val diff = abs(dur - t.durationMs)
                if (diff <= 6_000) {
                    score += 2
                } else if (diff > 15_000) {
                    score -= 3
                }
            }
            if (score > bestScore) {
                bestScore = score
                best = r
            }
        }
        val hit = best ?: return@runCatching null
        if (bestScore < 3) return@runCatching null

        val found = t.copy(
            title = if (title != t.title && norm(hit.optString("trackName")) == norm(title)) hit.getString("trackName") else t.title,
            artist = if (t.artist == UNKNOWN_ARTIST) hit.optString("artistName").ifBlank { t.artist } else t.artist,
            album = if (t.album.isNullOrBlank()) hit.optString("collectionName").ifBlank { null } else t.album,
            albumArtUrl = if (!t.hasRealArt()) hit.optString("artworkUrl100").ifBlank { null }?.replace("100x100bb", "600x600bb") ?: t.albumArtUrl else t.albumArtUrl,
        )
        if (found == t) null else found
    }.getOrNull()
}

data class DeepScanResult(val filesSeen: Int, val newlyIndexed: Int)

sealed interface ImportResult {
    data class Added(val title: String) : ImportResult
    data class Failed(val reason: String) : ImportResult
}

@Singleton
class LocalMediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: LocalMetadataStore,
) {
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, LOCAL_AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun observe(deep: Boolean): Flow<List<LocalSong>> = callbackFlow<Unit> {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(audioCollection(), true, observer)
        val edits = launch { store.version.collect { trySend(Unit) } }
        trySend(Unit)
        awaitClose {
            edits.cancel()
            context.contentResolver.unregisterContentObserver(observer)
        }
    }.conflate().map { load(deep) }.flowOn(Dispatchers.IO)

    fun observeExistingIds(): Flow<Set<String>?> = callbackFlow<Unit> {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(audioCollection(), true, observer)
        trySend(Unit)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.conflate().map { existingIds() }.flowOn(Dispatchers.IO)

    private fun existingIds(): Set<String>? {
        if (!hasPermission()) return null
        val out = HashSet<String>()
        val ok = runCatching {
            context.contentResolver.query(audioCollection(), arrayOf(MediaStore.Audio.Media._ID), null, null, null)?.use { c ->
                while (c.moveToNext()) out += "$LOCAL_ISRC_PREFIX${c.getLong(0)}"
                true
            } ?: false
        }.getOrDefault(false)
        return if (ok) out else null
    }

    private fun load(deep: Boolean): List<LocalSong> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.SIZE)
        val base = if (deep) {
            "${MediaStore.Audio.Media.DURATION} >= ? AND COALESCE(${MediaStore.Audio.Media.IS_RINGTONE},0) = 0 AND COALESCE(${MediaStore.Audio.Media.IS_ALARM},0) = 0 AND COALESCE(${MediaStore.Audio.Media.IS_NOTIFICATION},0) = 0"
        } else {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        }
        val pinned = store.pinnedIds()
        val selection = if (pinned.isEmpty()) base else "($base) OR ${MediaStore.Audio.Media._ID} IN (${pinned.joinToString(",")})"
        val minMs = if (deep) MIN_DEEP_MS else MIN_QUICK_MS
        val overrides = store.snapshot()
        val out = ArrayList<LocalSong>()
        runCatching {
            context.contentResolver.query(audioCollection(), projection, selection, arrayOf(minMs.toString()), "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val isrc = "$LOCAL_ISRC_PREFIX$id"
                    val artist = c.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: UNKNOWN_ARTIST
                    val album = c.getString(albumCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    val name = c.getString(nameCol).orEmpty()
                    var track = Track(
                        isrc = isrc,
                        title = c.getString(titleCol).orEmpty().ifBlank { name.substringBeforeLast('.') }.ifBlank { "Untitled" },
                        artist = artist,
                        album = album,
                        albumArtUrl = "content://media/external/audio/albumart/${c.getLong(albumIdCol)}",
                        durationMs = c.getLong(durCol),
                    )
                    overrides[isrc]?.let { o ->
                        track = track.copy(title = o.title, artist = o.artist, album = o.album ?: track.album, albumArtUrl = o.albumArtUrl ?: track.albumArtUrl)
                    }
                    out += LocalSong(track, name.substringAfterLast('.', "").uppercase().ifBlank { "AUDIO" }, c.getLong(sizeCol))
                }
            }
        }
        return out
    }

    private fun indexedPaths(): Set<String> {
        if (!hasPermission()) return emptySet()
        val set = HashSet<String>()
        runCatching {
            @Suppress("DEPRECATION")
            context.contentResolver.query(audioCollection(), arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { c ->
                while (c.moveToNext()) c.getString(0)?.let { set += it }
            }
        }
        return set
    }

    private fun storageRoots(): List<File> {
        val roots = LinkedHashSet<File>()
        Environment.getExternalStorageDirectory()?.let { roots += it }
        context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
            val p = dir.absolutePath
            val i = p.indexOf("/Android/")
            if (i > 0) roots += File(p.substring(0, i))
        }
        return roots.filter { it.exists() }
    }

    suspend fun deepScan(onProgress: (Int) -> Unit): DeepScanResult = withContext(Dispatchers.IO) {
        val known = indexedPaths()
        val unindexed = ArrayList<String>()
        var seen = 0
        fun walk(dir: File, depth: Int) {
            if (!isActive || depth > 12) return
            val kids = dir.listFiles() ?: return
            if (kids.any { it.name == ".nomedia" }) return
            for (k in kids) {
                if (!isActive) return
                if (k.isDirectory) {
                    if (k.name.startsWith(".") || k.name == "Android" || k.name == "LOST.DIR") continue
                    walk(k, depth + 1)
                } else if (k.extension.lowercase() in AUDIO_EXTENSIONS && k.length() > 0) {
                    seen++
                    if (k.absolutePath !in known) unindexed += k.absolutePath
                    if (seen % 20 == 0) onProgress(seen)
                }
            }
        }
        storageRoots().forEach { walk(it, 0) }
        onProgress(seen)
        unindexed.chunked(100).forEach { chunk -> scanFiles(chunk) }
        val after = if (unindexed.isEmpty()) emptySet() else indexedPaths()
        DeepScanResult(seen, unindexed.count { it in after })
    }

    suspend fun importFile(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext ImportResult.Failed("Allow access to your music first")
        val id = runCatching { mediaStoreId(uri) ?: indexInPlace(uri) ?: copyIntoLibrary(uri) }.getOrNull()
            ?: return@withContext ImportResult.Failed("Couldn\u2019t import that file")
        store.pin(id)
        ImportResult.Added(titleOf(id))
    }

    private fun mediaStoreId(uri: Uri): Long? {
        if (uri.authority != "com.android.providers.media.documents") return null
        val doc = DocumentsContract.getDocumentId(uri)
        return if (doc.startsWith("audio:")) doc.removePrefix("audio:").toLongOrNull() else null
    }

    private fun filePath(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val doc = DocumentsContract.getDocumentId(uri)
        val volume = doc.substringBefore(':')
        val relative = doc.substringAfter(':', "")
        if (relative.isEmpty()) return null
        val root = if (volume.equals("primary", ignoreCase = true)) Environment.getExternalStorageDirectory().path else "/storage/$volume"
        return "$root/$relative"
    }

    private fun idForPath(path: String): Long? = runCatching {
        @Suppress("DEPRECATION")
        context.contentResolver.query(
            audioCollection(),
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DATA} = ?",
            arrayOf(path),
            null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }.getOrNull()

    private suspend fun idAfterScan(path: String): Long? {
        idForPath(path)?.let { return it }
        withTimeoutOrNull(6_000) { scanFiles(listOf(path)) }
        return idForPath(path)
    }

    private suspend fun indexInPlace(uri: Uri): Long? {
        val path = filePath(uri) ?: return null
        if (!File(path).isFile) return null
        return idAfterScan(path)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private suspend fun copyIntoLibrary(uri: Uri): Long? {
        val resolver = context.contentResolver
        val name = displayName(uri)?.takeIf { it.isNotBlank() } ?: "imported-${System.currentTimeMillis()}.mp3"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mime = resolver.getType(uri)?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Gamut")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val target = resolver.insert(audioCollection(), values) ?: return null
            return try {
                val input = resolver.openInputStream(uri) ?: error("no input")
                val output = resolver.openOutputStream(target) ?: error("no output")
                input.use { i -> output.use { o -> i.copyTo(o) } }
                resolver.update(target, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                ContentUris.parseId(target)
            } catch (_: Exception) {
                runCatching { resolver.delete(target, null, null) }
                null
            }
        }
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return null
        val out = File(dir, name)
        val input = resolver.openInputStream(uri) ?: return null
        input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        return idAfterScan(out.absolutePath)
    }

    private fun titleOf(id: Long): String = runCatching {
        context.contentResolver.query(
            ContentUris.withAppendedId(audioCollection(), id),
            arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { c ->
            if (!c.moveToFirst()) {
                null
            } else {
                c.getString(0)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: c.getString(1)?.substringBeforeLast('.')
            }
        }
    }.getOrNull() ?: "Song"

    private suspend fun scanFiles(paths: List<String>) = suspendCancellableCoroutine<Unit> { cont ->
        var left = paths.size
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
            synchronized(this) {
                left--
                if (left == 0 && cont.isActive) cont.resume(Unit)
            }
        }
    }
}

@HiltViewModel
class LocalAvailabilityViewModel @Inject constructor(scanner: LocalMediaScanner) : ViewModel() {
    val existing: StateFlow<Set<String>?> = scanner.observeExistingIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

enum class LocalSort(val label: String) { Recent("Recently added"), Title("Title"), Artist("Artist") }

data class ScanUi(val running: Boolean = false, val seen: Int = 0, val message: String? = null)

data class AutoFetchUi(val loading: Boolean = true, val results: List<MetaCandidate> = emptyList())

@HiltViewModel
class LocalMusicViewModel @Inject constructor(
    private val scanner: LocalMediaScanner,
    private val resolver: LocalMetadataResolver,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val prefs = context.getSharedPreferences("local_music", Context.MODE_PRIVATE)

    private val permitted = MutableStateFlow(scanner.hasPermission())
    val hasPermission: StateFlow<Boolean> = permitted

    private val _denied = MutableStateFlow(false)
    val denied: StateFlow<Boolean> = _denied

    private val deep = MutableStateFlow(prefs.getBoolean("deep", false))
    val deepScanned: StateFlow<Boolean> = deep

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val songs: StateFlow<List<LocalSong>> =
        combine(permitted, deep) { ok, d -> ok to d }
            .flatMapLatest { (ok, d) -> if (ok) scanner.observe(d) else flowOf(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sort = MutableStateFlow(LocalSort.Recent)
    val sort: StateFlow<LocalSort> = _sort
    fun setSort(s: LocalSort) {
        _sort.value = s
    }

    private val _format = MutableStateFlow<String?>(null)
    val format: StateFlow<String?> = _format
    fun setFormat(f: String?) {
        _format.value = f
    }

    val formats: StateFlow<List<String>> = songs.map { l -> l.map { it.format }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sorted: StateFlow<List<LocalSong>> = combine(songs, _sort, _format) { list, s, f ->
        val filtered = if (f == null) list else list.filter { it.format == f }
        when (s) {
            LocalSort.Recent -> filtered
            LocalSort.Title -> filtered.sortedBy { it.track.title.lowercase() }
            LocalSort.Artist -> filtered.sortedWith(compareBy({ it.track.artist.lowercase() }, { it.track.title.lowercase() }))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _scan = MutableStateFlow(ScanUi())
    val scan: StateFlow<ScanUi> = _scan

    fun deepScan() {
        if (_scan.value.running || !permitted.value) return
        _scan.value = ScanUi(running = true)
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val r = runCatching { scanner.deepScan { n -> _scan.update { it.copy(seen = n) } } }.getOrNull()
            val remaining = MIN_SCAN_UI_MS - (System.currentTimeMillis() - startedAt)
            if (remaining > 0) kotlinx.coroutines.delay(remaining)
            deep.value = true
            prefs.edit().putBoolean("deep", true).apply()
            _scan.value = ScanUi(
                running = false,
                seen = r?.filesSeen ?: 0,
                message = when {
                    r == null -> "Couldn’t finish the scan"
                    r.newlyIndexed > 0 -> "${r.filesSeen} audio files · ${r.newlyIndexed} newly added"
                    else -> "${r.filesSeen} audio files · nothing new"
                },
            )
        }
    }

    suspend fun prepareForSave(track: Track): LocalMetadataResolver.Resolved = resolver.resolve(track)

    suspend fun importPicked(uri: Uri): ImportResult = scanner.importFile(uri)

    private val _auto = MutableStateFlow(AutoFetchUi())
    val auto: StateFlow<AutoFetchUi> = _auto
    private var autoJob: Job? = null

    fun startAutoFetch(track: Track) {
        autoJob?.cancel()
        _auto.value = AutoFetchUi()
        autoJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            runCatching {
                resolver.autoFetch(track).collect { c ->
                    val wait = 1_000L - (System.currentTimeMillis() - startedAt)
                    if (wait > 0) delay(wait)
                    _auto.update { it.copy(results = it.results + c) }
                    delay(220)
                }
            }
            val wait = 1_400L - (System.currentTimeMillis() - startedAt)
            if (wait > 0) delay(wait)
            _auto.update { it.copy(loading = false) }
        }
    }

    fun stopAutoFetch() {
        autoJob?.cancel()
        autoJob = null
    }

    suspend fun saveEdit(track: Track, title: String, artist: String, cover: CoverEdit): Track = resolver.saveEdit(track, title, artist, cover)

    suspend fun applyCandidate(track: Track, c: MetaCandidate): Track = resolver.saveEdit(track, c.title, c.artist, c.artUrl?.let { CoverEdit.Url(it) } ?: CoverEdit.Keep)

    fun refreshPermission(requestResultGranted: Boolean? = null) {
        val now = requestResultGranted ?: scanner.hasPermission()
        _denied.value = !now && requestResultGranted == false
        permitted.value = now
    }
}
