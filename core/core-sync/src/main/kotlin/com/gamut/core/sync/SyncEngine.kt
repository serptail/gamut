package com.gamut.core.sync

import android.content.Context
import com.gamut.core.database.FavoriteDao
import com.gamut.core.database.FavoriteEntity
import com.gamut.core.database.FollowDao
import com.gamut.core.database.FollowEntity
import com.gamut.core.database.PendingSyncDao
import com.gamut.core.database.PendingSyncEntity
import com.gamut.core.database.PlaylistDao
import com.gamut.core.database.PlaylistEntity
import com.gamut.core.database.PlaylistTrackCrossRef
import com.gamut.core.database.PlaylistTrackDao
import com.gamut.core.database.SyncStateDao
import com.gamut.core.database.SyncStateEntity
import com.gamut.core.database.SyncTypes
import com.gamut.core.database.TrackDao
import com.gamut.core.database.toEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.call.body
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val SYNC_OWNER_KEY = "owner"

class SessionEndedException : Exception("This account was signed out elsewhere or deleted")

class SyncFailure(errors: List<Throwable>) :
    Exception(
        errors.first().let { (it.message ?: it::class.java.simpleName) + if (errors.size > 1) " (+${errors.size - 1} more)" else "" },
        errors.first(),
    ) {
    init {
        errors.drop(1).forEach { addSuppressed(it) }
    }
}

@Singleton
class SyncEngine @Inject constructor(
    private val supabase: SupabaseClient,
    private val pending: PendingSyncDao,
    private val state: SyncStateDao,
    private val trackDao: TrackDao,
    private val favorites: FavoriteDao,
    private val playlists: PlaylistDao,
    private val playlistTracks: PlaylistTrackDao,
    private val follows: FollowDao,
    private val exporter: DataExporter,
    @ApplicationContext private val context: Context,
) {
    private val songIds = ConcurrentHashMap<String, String>()

    suspend fun sync() {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return
        ensureSessionValid()
        val errors = ArrayList<Throwable>()
        claimOwner(uid)
        push(uid, errors)
        val now = System.currentTimeMillis()
        val last = state.get(LAST_SYNC)?.toLongOrNull()
        if (last != null && now - last > TOMBSTONE_WINDOW_MS) resetCursors()
        pull(errors)
        if (errors.isNotEmpty()) throw SyncFailure(errors)
        state.put(SyncStateEntity(LAST_SYNC, now.toString()))
    }

    private suspend inline fun <reified T : Any> upsertResilient(table: String, rows: List<T>, conflict: String, chunk: Int) {
        rows.chunked(chunk).forEach { part ->
            sendResilient(part) { piece -> supabase.from(table).upsert(piece) { onConflict = conflict } }
        }
    }

    private suspend fun <T> sendResilient(rows: List<T>, send: suspend (List<T>) -> Unit) {
        if (rows.isEmpty()) return
        try {
            send(rows)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RestException) {
            if (!e.isPermanent()) throw e
            if (rows.size == 1) {
                android.util.Log.w("SyncEngine", "Dropping a row the server rejects: ${e.message}")
                return
            }
            val mid = rows.size / 2
            sendResilient(rows.subList(0, mid), send)
            sendResilient(rows.subList(mid, rows.size), send)
        }
    }

    private fun RestException.isPermanent() = (statusCode in 400..499 && statusCode != 401 && statusCode != 408 && statusCode != 429) ||
        message.orEmpty().contains("quota exceeded", ignoreCase = true)

    private suspend fun ensureSessionValid() {
        try {
            supabase.auth.retrieveUserForCurrentSession(updateSession = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RestException) {
            if (e.statusCode == 401 || e.statusCode == 403 || e.statusCode == 404) {
                runCatching { supabase.auth.clearSession() }
                throw SessionEndedException()
            }
            throw e
        }
    }

    private suspend fun guarded(errors: MutableList<Throwable>, block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        errors += e
        false
    }

    private suspend fun claimOwner(uid: String) {
        val owner = state.get(OWNER)?.takeIf { it.isNotBlank() }
        if (owner == uid) return
        resetCursors()
        if (owner != null) {
            saveSafetyBackup()
            wipeLocalLibrary()
        }
        adoptRemotePlaylists()
        val now = System.currentTimeMillis()
        suspend fun mark(type: String, key: String, payload: String? = null) = pending.put(PendingSyncEntity(type = type, itemKey = key, payload = payload, createdAtEpochMs = now))
        playlists.getAll().forEach { p ->
            mark(SyncTypes.PL, p.id.toString())
            playlistTracks.isrcsFor(p.id).forEachIndexed { i, isrc -> mark(SyncTypes.PLS, "${p.id}|$isrc", i.toString()) }
        }
        favorites.all().forEach { mark(SyncTypes.FAV, it.isrc) }
        follows.all().forEach { mark(SyncTypes.FOLLOW, it.artistKey) }
        state.put(SyncStateEntity(OWNER, uid))
    }

    private suspend fun adoptRemotePlaylists() {
        val local = playlists.getAll()
        val unlinked = local.filter { it.remoteId == null }
        if (unlinked.isEmpty()) return
        val used = local.mapNotNull { it.remoteId }.toHashSet()
        val remote = ArrayList<RemotePlaylistRef>()
        var from = 0L
        while (true) {
            val rows = supabase.from("playlists").select(Columns.list("id", "name", "deleted_at")) {
                order("id", Order.ASCENDING)
                range(from, from + PAGE - 1)
            }.decodeList<RemotePlaylistRef>()
            remote += rows
            if (rows.size < PAGE) break
            from += PAGE
        }
        val byName = remote.filter { it.deletedAt == null && it.id !in used }
            .groupBy { it.name.trim().lowercase() }
            .mapValues { ArrayDeque(it.value) }
        for (p in unlinked) {
            val match = byName[p.name.trim().lowercase()]?.removeFirstOrNull() ?: continue
            playlists.setRemoteId(p.id, match.id)
        }
    }

    private suspend fun saveSafetyBackup() {
        val bytes = exporter.exportOrNull(null) ?: return
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "backups").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "gamut-backup-$stamp-previous-account.json")
            val tmp = File(dir, "gamut-backup-$stamp-previous-account.tmp")
            try {
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(file)) throw IOException("Could not save the previous account's backup")
            } finally {
                tmp.delete()
            }
        }
    }

    private suspend fun wipeLocalLibrary() {
        playlists.getAll().forEach {
            playlistTracks.clear(it.id)
            playlists.delete(it.id)
        }
        favorites.all().forEach { favorites.remove(it.isrc) }
        follows.all().forEach { follows.remove(it.artistKey) }
        pending.all().map { it.id }.chunked(500).forEach { pending.deleteIds(it) }
    }

    private suspend fun resetCursors() = CURSORS.forEach { state.put(SyncStateEntity(it, EPOCH)) }

    private suspend fun push(uid: String, errors: MutableList<Throwable>) {
        val entries = pending.all()
        if (entries.isEmpty()) return
        val byType = entries.groupBy { it.type }
        val now = Instant.now().toString()
        val nowMs = System.currentTimeMillis()

        suspend fun finish(ids: List<Long>) {
            ids.chunked(500).forEach { pending.deleteIds(it) }
        }
        fun retryable(e: PendingSyncEntity, isrc: String) = parseIsrc(isrc) != null && nowMs - e.createdAtEpochMs < RETRY_WINDOW_MS

        val delEntries = byType[SyncTypes.PL_DEL].orEmpty()
        guarded(errors) {
            for (chunk in delEntries.chunked(100)) {
                val ids = chunk.map { it.itemKey }
                supabase.from("playlists").update({ set("deleted_at", now) }) { filter { isIn("id", ids) } }
                finish(chunk.map { it.id })
            }
        }

        val plOk = guarded(errors) {
            val rows = ArrayList<PlaylistRow>()
            val doneIds = ArrayList<Long>()
            val dropIds = ArrayList<Long>()
            for (e in byType[SyncTypes.PL].orEmpty()) {
                val local = e.itemKey.toLongOrNull()?.let { playlists.get(it) }
                if (local == null) {
                    dropIds += e.id
                    continue
                }
                val remote = local.remoteId ?: UUID.randomUUID().toString().also { playlists.setRemoteId(local.id, it) }
                rows += PlaylistRow(
                    remote,
                    uid,
                    local.name.trim().take(100).ifBlank { "Untitled" },
                    local.tags.take(500),
                    Instant.ofEpochMilli(local.createdAtEpochMs).toString(),
                )
                doneIds += e.id
            }
            finish(dropIds)
            upsertResilient("playlists", rows, "id", 100)
            finish(doneIds)
        }

        val favEntries = byType[SyncTypes.FAV].orEmpty()
        val plsEntries = byType[SyncTypes.PLS].orEmpty()
        val ids = ensureSongIds((favEntries.map { it.itemKey } + plsEntries.map { it.itemKey.substringAfter('|') }).toSet(), errors)

        guarded(errors) {
            val upserts = ArrayList<FavoriteRow>()
            val removals = ArrayList<String>()
            val doneIds = ArrayList<Long>()
            for (e in favEntries) {
                val local = favorites.get(e.itemKey)
                val songId = ids[e.itemKey]
                if (songId == null) {
                    if (local != null && retryable(e, e.itemKey)) continue
                    doneIds += e.id
                    continue
                }
                doneIds += e.id
                if (local != null) {
                    upserts += FavoriteRow(uid, songId, Instant.ofEpochMilli(local.addedAtEpochMs).toString())
                } else {
                    removals += songId
                }
            }
            upsertResilient("favorites", upserts, "user_id,song_id", 200)
            removals.chunked(100).forEach { chunk ->
                supabase.from("favorites").update({ set("deleted_at", now) }) {
                    filter {
                        eq("user_id", uid)
                        isIn("song_id", chunk)
                    }
                }
            }
            finish(doneIds)
        }

        val followEntries = byType[SyncTypes.FOLLOW].orEmpty()
        guarded(errors) {
            val upserts = ArrayList<FollowRow>()
            val removals = HashMap<String, MutableList<String>>()
            val doneIds = ArrayList<Long>()
            for (e in followEntries) {
                doneIds += e.id
                val (provider, pid) = parseArtistKey(e.itemKey) ?: continue
                val local = follows.get(e.itemKey)
                if (local != null) {
                    upserts += FollowRow(
                        uid,
                        provider,
                        pid,
                        local.name.trim().take(200).ifBlank { "Unknown artist" },
                        local.imageUrl?.takeIf { it.startsWith("https://") }?.take(500),
                        Instant.ofEpochMilli(local.followedAtEpochMs).toString(),
                    )
                } else {
                    removals.getOrPut(provider) { ArrayList() } += pid
                }
            }
            upsertResilient("artist_follows", upserts, "user_id,provider,provider_artist_id", 200)
            removals.forEach { (provider, pids) ->
                pids.chunked(100).forEach { chunk ->
                    supabase.from("artist_follows").update({ set("deleted_at", now) }) {
                        filter {
                            eq("user_id", uid)
                            eq("provider", provider)
                            isIn("provider_artist_id", chunk)
                        }
                    }
                }
            }
            finish(doneIds)
        }

        if (plOk) {
            guarded(errors) {
                val upserts = ArrayList<PlaylistSongRow>()
                val removals = HashMap<String, MutableList<String>>()
                val doneIds = ArrayList<Long>()
                for (e in plsEntries) {
                    val local = playlists.get(e.itemKey.substringBefore('|').toLongOrNull() ?: -1)
                    if (local == null) {
                        doneIds += e.id
                        continue
                    }
                    val remote = local.remoteId ?: continue
                    val isrc = e.itemKey.substringAfter('|')
                    val position = e.payload?.toIntOrNull()
                    val songId = ids[isrc]
                    if (songId == null) {
                        if (position != null && retryable(e, isrc)) continue
                        doneIds += e.id
                        continue
                    }
                    doneIds += e.id
                    if (position != null) {
                        upserts += PlaylistSongRow(remote, songId, position.coerceAtLeast(0))
                    } else {
                        removals.getOrPut(remote) { ArrayList() } += songId
                    }
                }
                upsertResilient("playlist_songs", upserts, "playlist_id,song_id", 200)
                removals.forEach { (remote, songs) ->
                    songs.chunked(100).forEach { chunk ->
                        supabase.from("playlist_songs").update({ set("deleted_at", now) }) {
                            filter {
                                eq("playlist_id", remote)
                                isIn("song_id", chunk)
                            }
                        }
                    }
                }
                finish(doneIds)
            }
        }
    }

    private suspend fun ensureSongIds(isrcs: Set<String>, errors: MutableList<Throwable>): Map<String, String> {
        val parsed = isrcs.mapNotNull { isrc -> parseIsrc(isrc)?.let { isrc to it } }
        val need = parsed.filter { songIds[it.first] == null }
        lookup@ for ((provider, all) in need.groupBy({ it.second.first }, { it.second.second })) {
            for (chunk in all.chunked(100)) {
                val ok = guarded(errors) {
                    supabase.from("songs").select(Columns.list("id", "provider", "provider_song_id")) {
                        filter {
                            eq("provider", provider)
                            isIn("provider_song_id", chunk)
                        }
                    }.decodeList<SongRef>().forEach { songIds["${it.provider}:${it.providerSongId}"] = it.id }
                }
                if (!ok) break@lookup
            }
        }
        for (chunk in need.filter { songIds[it.first] == null }.chunked(50)) {
            val ok = guarded(errors) {
                val response = supabase.functions.invoke(
                    "resolve-metadata",
                    ResolveRequest(chunk.map { ResolveItem(it.second.first, it.second.second) }),
                )
                response.body<ResolveResponse>().songs.forEach { songIds["${it.provider}:${it.providerSongId}"] = it.id }
            }
            if (!ok) break
        }
        return parsed.mapNotNull { (isrc, _) -> songIds[isrc]?.let { isrc to it } }.toMap()
    }

    private suspend fun pull(errors: MutableList<Throwable>) {
        val playlistsOk = guarded(errors) {
            pageThrough<PlaylistPull>(
                "playlists",
                "id,name,tags,created_at,updated_at,deleted_at",
                CUR_PLAYLISTS,
                listOf("id"),
                { it.updatedAt },
            ) { rows ->
                for (r in rows) {
                    val local = playlists.getByRemoteId(r.id)
                    when {
                        r.deletedAt != null -> if (local != null) {
                            playlistTracks.clear(local.id)
                            playlists.delete(local.id)
                            pending.delete(SyncTypes.PL, local.id.toString())
                            pending.deleteByPrefix(SyncTypes.PLS, "${local.id}|%")
                        }

                        local == null -> playlists.insert(
                            PlaylistEntity(name = r.name, createdAtEpochMs = ms(r.createdAt), tags = r.tags, remoteId = r.id),
                        )

                        !pending.has(SyncTypes.PL, local.id.toString()) -> playlists.updateMeta(local.id, r.name, r.tags)
                    }
                }
            }
        }

        if (playlistsOk) {
            guarded(errors) {
                pageThrough<PlaylistSongPull>(
                    "playlist_songs",
                    "playlist_id,position,updated_at,deleted_at,$SONG_EMBED",
                    CUR_PLAYLIST_SONGS,
                    listOf("playlist_id", "song_id"),
                    { it.updatedAt },
                ) { rows ->
                    trackDao.insertIgnore(rows.filter { it.deletedAt == null }.mapNotNull { it.songs?.toTrack()?.toEntity() })
                    for (r in rows) {
                        val song = r.songs ?: continue
                        val local = playlists.getByRemoteId(r.playlistId) ?: continue
                        val isrc = song.isrc()
                        if (pending.has(SyncTypes.PLS, "${local.id}|$isrc")) continue
                        if (r.deletedAt != null) {
                            playlistTracks.remove(local.id, isrc)
                        } else {
                            playlistTracks.upsert(PlaylistTrackCrossRef(local.id, isrc, r.position))
                        }
                    }
                }
            }
        }

        guarded(errors) {
            pageThrough<FavoritePull>(
                "favorites",
                "created_at,updated_at,deleted_at,$SONG_EMBED",
                CUR_FAVORITES,
                listOf("song_id"),
                { it.updatedAt },
            ) { rows ->
                trackDao.insertIgnore(rows.filter { it.deletedAt == null }.mapNotNull { it.songs?.toTrack()?.toEntity() })
                for (r in rows) {
                    val isrc = r.songs?.isrc() ?: continue
                    if (pending.has(SyncTypes.FAV, isrc)) continue
                    if (r.deletedAt != null) favorites.remove(isrc) else favorites.add(FavoriteEntity(isrc, ms(r.createdAt)))
                }
            }
        }

        guarded(errors) {
            pageThrough<FollowPull>(
                "artist_follows",
                "provider,provider_artist_id,name,artwork_url,created_at,updated_at,deleted_at",
                CUR_FOLLOWS,
                listOf("provider", "provider_artist_id"),
                { it.updatedAt },
            ) { rows ->
                for (r in rows) {
                    val key = "${r.provider}:${r.providerArtistId}"
                    if (pending.has(SyncTypes.FOLLOW, key)) continue
                    if (r.deletedAt != null) follows.remove(key) else follows.add(FollowEntity(key, r.name, r.artworkUrl, ms(r.createdAt)))
                }
            }
        }
    }

    private suspend inline fun <reified T : Any> pageThrough(
        table: String,
        columns: String,
        cursorName: String,
        tieBreak: List<String>,
        updatedAt: (T) -> String,
        apply: suspend (List<T>) -> Unit,
    ) {
        val cursor = state.get(cursorName) ?: EPOCH
        val start = overlapStart(cursor)
        var max = cursor
        var from = 0L
        while (true) {
            val rows = supabase.from(table).select(Columns.raw(columns)) {
                filter { gte("updated_at", start) }
                order("updated_at", Order.ASCENDING)
                tieBreak.forEach { order(it, Order.ASCENDING) }
                range(from, from + PAGE - 1)
            }.decodeList<T>()
            if (rows.isEmpty()) break
            apply(rows)
            rows.forEach { if (ms(updatedAt(it)) > ms(max)) max = updatedAt(it) }
            if (rows.size < PAGE) break
            from += PAGE
        }
        state.put(SyncStateEntity(cursorName, max))
    }

    private fun overlapStart(cursor: String): String = if (cursor == EPOCH) {
        EPOCH
    } else {
        runCatching { OffsetDateTime.parse(cursor).toInstant().minusSeconds(OVERLAP_SECONDS).toString() }.getOrDefault(EPOCH)
    }

    private fun ms(timestamp: String): Long = OffsetDateTime.parse(timestamp).toInstant().toEpochMilli()

    private companion object {
        const val EPOCH = "1970-01-01T00:00:00+00:00"
        const val PAGE = 500L
        const val OVERLAP_SECONDS = 120L
        const val OWNER = SYNC_OWNER_KEY
        const val LAST_SYNC = "last_synced_ms"
        const val CUR_PLAYLISTS = "cur_playlists"
        const val CUR_PLAYLIST_SONGS = "cur_playlist_songs"
        const val CUR_FAVORITES = "cur_favorites"
        const val CUR_FOLLOWS = "cur_follows"
        val CURSORS = listOf(CUR_PLAYLISTS, CUR_PLAYLIST_SONGS, CUR_FAVORITES, CUR_FOLLOWS)
        const val TOMBSTONE_WINDOW_MS = 90L * 24 * 60 * 60 * 1000
        const val RETRY_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    }
}
