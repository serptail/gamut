package com.gamut.core.sync

import android.util.Base64
import com.gamut.core.database.FavoriteDao
import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import com.gamut.feature.library.LibraryRepository
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
data class ExportTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0,
    val artworkUrl: String? = null,
    val source: String? = null,
)

@Serializable data class ExportFavorite(val track: ExportTrack, val addedAt: Long)

@Serializable data class ExportFollow(val id: String, val name: String, val artworkUrl: String? = null, val followedAt: Long)

@Serializable data class ExportPlaylist(val name: String, val tags: List<String> = emptyList(), val tracks: List<ExportTrack> = emptyList())

@Serializable
data class ExportDocument(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: Long,
    val favorites: List<ExportFavorite>,
    val playlists: List<ExportPlaylist>,
    val follows: List<ExportFollow> = emptyList(),
    val settings: JsonObject = JsonObject(emptyMap()),
) {
    companion object {
        const val SCHEMA_VERSION = 2
    }
}

@Serializable private data class Envelope(val enc: Int, val salt: String, val iv: String, val data: String)

class BackupPasswordException(val wrong: Boolean, message: String) : IllegalArgumentException(message)

data class PlaylistOutcome(
    val name: String,
    val created: Boolean,
    val songsAdded: Int,
    val songsAlreadyThere: Int,
    val songsSkipped: Int,
)

data class ImportReport(
    val backupVersion: Int,
    val exportedAt: Long,
    val committed: Boolean,
    val favoritesAdded: Int,
    val favoritesAlreadyThere: Int,
    val favoritesSkipped: Int,
    val playlists: List<PlaylistOutcome>,
    val playlistsSkipped: Int,
    val followsAdded: Int,
    val followsAlreadyThere: Int,
    val followsSkipped: Int,
    val duplicatesInFile: Int,
    val warnings: List<String>,
    val replaced: Boolean = false,
    val removedFavorites: Int = 0,
    val removedPlaylists: Int = 0,
    val removedFollows: Int = 0,
) {
    val playlistsCreated get() = playlists.count { it.created }
    val playlistsMerged get() = playlists.count { !it.created }
    val playlistSongsAdded get() = playlists.sumOf { it.songsAdded }
    val playlistSongsAlreadyThere get() = playlists.sumOf { it.songsAlreadyThere }
    val playlistSongsSkipped get() = playlists.sumOf { it.songsSkipped }
    val totalAdded get() = favoritesAdded + playlistsCreated + playlistSongsAdded + followsAdded
    val totalSkipped get() = favoritesSkipped + playlistsSkipped + playlistSongsSkipped + followsSkipped
}

class DataExporter @Inject constructor(
    private val library: LibraryRepository,
    private val followRepository: FollowRepository,
    private val favoriteDao: FavoriteDao,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private suspend fun buildDocument(): ExportDocument {
        val now = System.currentTimeMillis()
        val favs = library.observeFavorites().first().distinctBy { it.isrc }
        val addedAt = favoriteDao.all().associate { it.isrc to it.addedAtEpochMs }
        return ExportDocument(
            exportedAt = now,
            favorites = favs.map { ExportFavorite(it.toExport(), addedAt[it.isrc] ?: now) },
            playlists = library.observePlaylists().first().map { p ->
                ExportPlaylist(p.name, p.tags, library.observeTracks(p.id).first().map { it.toExport() })
            },
            follows = followRepository.observeAll().first()
                .map { ExportFollow(it.artistKey, it.name, it.imageUrl, it.followedAtEpochMs) },
        )
    }

    private suspend fun encode(doc: ExportDocument, password: CharArray?): ByteArray = withContext(Dispatchers.Default) {
        val plain = json.encodeToString(ExportDocument.serializer(), doc).toByteArray()
        if (password == null || password.isEmpty()) plain else encrypt(plain, password)
    }

    suspend fun export(password: CharArray?): ByteArray = encode(buildDocument(), password)

    suspend fun exportOrNull(password: CharArray?): ByteArray? {
        val doc = buildDocument()
        return if (doc.favorites.isEmpty() && doc.playlists.isEmpty() && doc.follows.isEmpty()) null else encode(doc, password)
    }

    fun isEncrypted(bytes: ByteArray): Boolean = runCatching { json.parseToJsonElement(bytes.decodeToString()).jsonObject.containsKey("enc") }.getOrDefault(false)

    suspend fun preview(bytes: ByteArray, password: CharArray?, replace: Boolean = false): ImportReport = run(bytes, password, commit = false, replace = replace)

    suspend fun import(bytes: ByteArray, password: CharArray?, replace: Boolean = false): ImportReport = run(bytes, password, commit = true, replace = replace)

    private suspend fun run(bytes: ByteArray, password: CharArray?, commit: Boolean, replace: Boolean): ImportReport = importLock.withLock {
        val doc = withContext(Dispatchers.Default) { parse(bytes, password) }
        if (!replace) return@withLock apply(doc, commit, simulateEmpty = false)

        val favs = library.observeFavorites().first().size
        val pls = library.observePlaylists().first().size
        val fols = followRepository.observeAll().first().size
        fun ImportReport.marked() = copy(replaced = true, removedFavorites = favs, removedPlaylists = pls, removedFollows = fols)
        if (!commit) return@withLock apply(doc, commit = false, simulateEmpty = true).marked()

        val snapshot = buildDocument()
        wipeLibrary()
        try {
            apply(doc, commit = true, simulateEmpty = false).marked()
        } catch (e: Throwable) {
            withContext(NonCancellable) { runCatching { apply(snapshot, commit = true, simulateEmpty = false) } }
            throw e
        }
    }

    private suspend fun wipeLibrary() {
        library.observeFavorites().first().forEach { library.toggleFavorite(it) }
        library.observePlaylists().first().forEach { library.deletePlaylist(it.id) }
        followRepository.observeAll().first().forEach { followRepository.toggle(it.artistKey, it.name, it.imageUrl) }
    }

    private fun parse(bytes: ByteArray, password: CharArray?): ExportDocument {
        require(bytes.isNotEmpty()) { "This file is empty" }
        require(bytes.size <= MAX_BYTES) { "File is too large" }
        var root = try {
            json.parseToJsonElement(bytes.decodeToString()).jsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("This isn't a Gamut backup file")
        }
        if (root.containsKey("enc")) {
            if (password == null || password.isEmpty()) throw BackupPasswordException(false, "This backup is encrypted: enter its password")
            val env = try {
                json.decodeFromJsonElement(Envelope.serializer(), root)
            } catch (e: Exception) {
                throw IllegalArgumentException("The backup file is damaged")
            }
            require(env.enc == 1) { "Unsupported encryption version" }
            val plain = decrypt(env, password)
            root = try {
                json.parseToJsonElement(plain.decodeToString()).jsonObject
            } catch (e: Exception) {
                throw IllegalArgumentException("The backup file is damaged")
            }
        }
        val doc = try {
            json.decodeFromJsonElement(ExportDocument.serializer(), root)
        } catch (e: Exception) {
            throw IllegalArgumentException("This isn't a valid Gamut backup")
        }
        require(doc.schemaVersion in 1..ExportDocument.SCHEMA_VERSION) {
            "This backup was made by a newer version of Gamut (format ${doc.schemaVersion}). Update the app to restore it."
        }
        require(doc.favorites.size <= 50_000 && doc.playlists.size <= 1_000 && doc.follows.size <= 10_000) { "Too many items" }
        require(doc.playlists.all { it.tracks.size <= 10_000 }) { "A playlist has too many songs" }
        return doc
    }

    private suspend fun apply(doc: ExportDocument, commit: Boolean, simulateEmpty: Boolean): ImportReport {
        val now = System.currentTimeMillis()
        val warnings = ArrayList<String>()
        fun warn(m: String) {
            if (warnings.size < MAX_WARNINGS) warnings += m
        }
        var duplicates = 0

        val haveFavs = HashSet<String>().also { set -> if (!simulateEmpty) library.observeFavorites().first().mapTo(set) { it.isrc } }
        val seenFav = HashSet<String>()
        var fAdded = 0
        var fThere = 0
        var fSkipped = 0
        var fCap = 0
        for (f in doc.favorites) {
            val k = f.track.key
            if (!seenFav.add(k)) {
                duplicates++
                continue
            }
            if (k in haveFavs) {
                fThere++
                continue
            }
            val track = f.track.toTrackOrNull()
            if (track == null) {
                fSkipped++
                continue
            }
            if (haveFavs.size >= MAX_FAVORITES) {
                fSkipped++
                fCap++
                continue
            }
            if (commit) {
                try {
                    library.addFavorite(track, f.addedAt.sane(now))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fSkipped++
                    warn("Couldn't add favorite “${track.title.take(40)}”")
                    continue
                }
            }
            haveFavs += k
            fAdded++
        }
        if (fCap > 0) warn("$fCap favorites skipped: the limit is $MAX_FAVORITES")

        val existing = library.observePlaylists().first().let { if (simulateEmpty) it.take(0) else it }
        val pool = existing.groupBy { norm(it.name) }.mapValues { ArrayDeque(it.value) }
        var playlistCount = existing.size
        val outcomes = ArrayList<PlaylistOutcome>()
        var plSkipped = 0
        for (p in doc.playlists) {
            val name = p.name.trim().take(100)
            if (name.isBlank()) {
                plSkipped++
                continue
            }
            val match = pool[norm(name)]?.removeFirstOrNull()
            val id: Long
            val have: MutableSet<String>
            val created: Boolean
            if (match != null) {
                id = match.id
                have = library.observeTracks(id).first().mapTo(HashSet()) { it.isrc }
                created = false
            } else {
                if (playlistCount >= MAX_PLAYLISTS) {
                    plSkipped++
                    warn("Playlist “$name” skipped: the limit is $MAX_PLAYLISTS playlists")
                    continue
                }
                val tags = p.tags.map { it.trim().take(50) }.filter { it.isNotBlank() }.distinct().take(20)
                id = if (commit) {
                    try {
                        library.createPlaylist(name, tags)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        plSkipped++
                        warn("Couldn't create playlist “$name”")
                        continue
                    }
                } else {
                    -1L
                }
                have = HashSet()
                created = true
                playlistCount++
            }
            var added = 0
            var there = 0
            var skipped = 0
            val seen = HashSet<String>()
            for (t in p.tracks) {
                val k = t.key
                if (!seen.add(k)) {
                    duplicates++
                    continue
                }
                if (k in have) {
                    there++
                    continue
                }
                val track = t.toTrackOrNull()
                if (track == null || have.size >= MAX_PLAYLIST_TRACKS) {
                    skipped++
                    continue
                }
                if (commit) {
                    try {
                        library.appendTrack(id, track)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        skipped++
                        continue
                    }
                }
                have += k
                added++
            }
            outcomes += PlaylistOutcome(name, created, added, there, skipped)
        }

        val haveFollows = HashSet<String>().also { set -> if (!simulateEmpty) followRepository.observeAll().first().mapTo(set) { it.artistKey } }
        val seenFollow = HashSet<String>()
        var folAdded = 0
        var folThere = 0
        var folSkipped = 0
        for (f in doc.follows) {
            val k = f.id.trim()
            if (!seenFollow.add(k)) {
                duplicates++
                continue
            }
            if (k in haveFollows) {
                folThere++
                continue
            }
            val name = f.name.trim().take(200)
            if (parseArtistKey(k) == null || name.isBlank() || haveFollows.size >= MAX_FOLLOWS) {
                folSkipped++
                continue
            }
            val art = f.artworkUrl?.takeIf { it.startsWith("https://") }?.take(500)
            if (commit) {
                val added = try {
                    followRepository.importFollow(k, name, art, f.followedAt.sane(now))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    folSkipped++
                    continue
                }
                if (!added) {
                    folThere++
                    continue
                }
            }
            haveFollows += k
            folAdded++
        }

        return ImportReport(
            backupVersion = doc.schemaVersion, exportedAt = doc.exportedAt, committed = commit,
            favoritesAdded = fAdded, favoritesAlreadyThere = fThere, favoritesSkipped = fSkipped,
            playlists = outcomes, playlistsSkipped = plSkipped,
            followsAdded = folAdded, followsAlreadyThere = folThere, followsSkipped = folSkipped,
            duplicatesInFile = duplicates, warnings = warnings,
        )
    }

    fun readLimited(stream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            require(out.size() <= MAX_BYTES) { "File is too large" }
        }
        return out.toByteArray()
    }

    private fun Track.toExport() = ExportTrack(isrc, title, artist, album, durationMs, albumArtUrl, source.name)

    private val ExportTrack.key get() = id.trim()

    private fun ExportTrack.toTrackOrNull(): Track? {
        val k = key
        val local = LOCAL_ID.matches(k)
        if (parseIsrc(k) == null && !BARE_ISRC.matches(k) && !local) return null
        val yt = k.startsWith("youtube:")
        val known = TrackSource.entries.firstOrNull { it.name == source && it != TrackSource.YOUTUBE }
        if (local && known == null) return null
        val src = if (yt) TrackSource.YOUTUBE else known ?: TrackSource.ITUNES
        return Track(
            isrc = k,
            title = title.trim().take(300).ifBlank { "Unknown title" },
            artist = artist.trim().take(200).ifBlank { "Unknown artist" },
            album = album?.trim()?.take(300)?.ifBlank { null },
            durationMs = durationMs.coerceIn(0, 24L * 60 * 60 * 1000),
            albumArtUrl = artworkUrl?.takeIf { it.startsWith("https://") || (local && (it.startsWith("file:") || it.startsWith("content:"))) }?.take(500),
            source = src,
            youtubeVideoId = if (yt) k.removePrefix("youtube:") else null,
        )
    }

    private fun Long.sane(now: Long) = if (this <= 0L || this > now) now else this
    private fun norm(name: String) = name.trim().lowercase()

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
        val env = Envelope(1, b64(salt), b64(iv), b64(cipher.doFinal(plain)))
        return json.encodeToString(Envelope.serializer(), env).toByteArray()
    }

    private fun decrypt(env: Envelope, password: CharArray): ByteArray = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, key(password, unb64(env.salt)), GCMParameterSpec(128, unb64(env.iv))) }
        cipher.doFinal(unb64(env.data))
    } catch (e: Exception) {
        throw BackupPasswordException(true, "Wrong password or damaged file")
    }

    private companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        const val MAX_WARNINGS = 20
        const val MAX_FAVORITES = 20_000
        const val MAX_PLAYLISTS = 200
        const val MAX_PLAYLIST_TRACKS = 5_000
        const val MAX_FOLLOWS = 5_000
        val BARE_ISRC = Regex("^[A-Za-z0-9]{12}$")
        val LOCAL_ID = Regex("^local:\\S{1,200}$")
        val importLock = Mutex()
    }
}
