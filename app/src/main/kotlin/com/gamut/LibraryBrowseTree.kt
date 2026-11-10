package com.gamut

import android.net.Uri
import com.gamut.core.database.DownloadDao
import com.gamut.core.database.RecentDao
import com.gamut.core.database.TrackDao
import com.gamut.core.database.toDomain
import com.gamut.core.model.Track
import com.gamut.core.player.BrowseEntry
import com.gamut.core.player.BrowseQueue
import com.gamut.core.player.BrowseTreeProvider
import com.gamut.feature.library.LibraryRepository
import com.gamut.feature.search.SearchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class LibraryBrowseTree @Inject constructor(
    private val library: LibraryRepository,
    private val recents: RecentDao,
    private val downloads: DownloadDao,
    private val trackDao: TrackDao,
    private val searchRepository: SearchRepository,
) : BrowseTreeProvider {
    private val searchCache = LinkedHashMap<String, List<Track>>()

    override suspend fun children(parentId: String): List<BrowseEntry> = when {
        parentId == BrowseTreeProvider.ROOT -> topLevel()

        parentId == FAV -> songs(FAV, favorites())

        parentId == RECENT -> songs(RECENT, recentTracks())

        parentId == DL -> songs(DL, downloadedTracks())

        parentId == PL -> library.observePlaylists().first().map { BrowseEntry.Folder("$PL|${it.id}", it.name, it.tags.joinToString(" · ").ifBlank { null }, it.coverUri) }

        parentId.startsWith("$PL|") -> {
            val playlistId = parentId.substringAfter('|').toLongOrNull()
            if (playlistId == null) emptyList() else songs(parentId, library.observeTracks(playlistId).first())
        }

        else -> emptyList()
    }

    override suspend fun item(id: String): BrowseEntry? {
        topLevel().firstOrNull { it.id == id }?.let { return it }
        if (id == BrowseTreeProvider.ROOT) return BrowseEntry.Folder(id, "Gamut")
        if (id == PL) return BrowseEntry.Folder(PL, "Playlists")
        if (id.startsWith("$PL|") && id.count { it == '|' } == 1) {
            val playlistId = id.substringAfter('|').toLongOrNull() ?: return null
            return library.observePlaylists().first().firstOrNull { it.id == playlistId }?.let { BrowseEntry.Folder(id, it.name) }
        }
        val queue = queueFor(id) ?: return null
        return BrowseEntry.Song(id, queue.tracks[queue.startIndex])
    }

    override suspend fun queueFor(mediaId: String): BrowseQueue? {
        val parts = mediaId.split('|')
        val (tracks, isrc) = when (parts[0]) {
            FAV -> favorites() to mediaId.substringAfter('|', "")

            RECENT -> recentTracks() to mediaId.substringAfter('|', "")

            DL -> downloadedTracks() to mediaId.substringAfter('|', "")

            PL -> {
                if (parts.size < 3) return null
                val playlistId = parts[1].toLongOrNull() ?: return null
                library.observeTracks(playlistId).first() to mediaId.substringAfter('|').substringAfter('|')
            }

            SEARCH -> {
                if (parts.size < 3) return null
                searchTracks(Uri.decode(parts[1])) to mediaId.substringAfter('|').substringAfter('|')
            }

            else -> return null
        }
        val index = tracks.indexOfFirst { it.isrc == isrc }
        return if (index >= 0) BrowseQueue(tracks, index) else null
    }

    override suspend fun search(query: String): List<BrowseEntry.Song> {
        val encoded = Uri.encode(query)
        return searchTracks(query).map { BrowseEntry.Song("$SEARCH|$encoded|${it.isrc}", it) }
    }

    override suspend fun resumption(): BrowseQueue? {
        val tracks = recentTracks()
        return if (tracks.isEmpty()) null else BrowseQueue(tracks, 0)
    }

    private fun topLevel(): List<BrowseEntry> = listOf(
        BrowseEntry.Folder(FAV, "Favorites"),
        BrowseEntry.Folder(PL, "Playlists"),
        BrowseEntry.Folder(RECENT, "Recently played"),
        BrowseEntry.Folder(DL, "Downloads"),
    )

    private fun songs(listId: String, tracks: List<Track>): List<BrowseEntry> = tracks.map { BrowseEntry.Song("$listId|${it.isrc}", it) }

    private suspend fun favorites(): List<Track> = library.observeFavorites().first()

    private suspend fun recentTracks(): List<Track> = recents.observeTracks(RECENT_LIMIT).first().map { it.toDomain() }

    private suspend fun downloadedTracks(): List<Track> {
        val isrcs = downloads.observeAll().first().map { it.isrc }
        if (isrcs.isEmpty()) return emptyList()
        val byIsrc = isrcs.chunked(500).flatMap { trackDao.getByIsrcs(it) }.associateBy { it.isrc }
        return isrcs.mapNotNull { byIsrc[it]?.toDomain() }
    }

    private suspend fun searchTracks(query: String): List<Track> {
        val q = query.trim()
        if (q.isEmpty()) return recentTracks().ifEmpty { favorites() }.take(SEARCH_LIMIT)
        val key = q.lowercase()
        synchronized(searchCache) { searchCache[key] }?.let { return it }
        val local = trackDao.observeAll().first()
            .filter { it.title.contains(q, true) || it.artist.contains(q, true) || (it.album?.contains(q, true) == true) }
            .map { it.toDomain() }
        val remote = withTimeoutOrNull(REMOTE_SEARCH_MS) {
            runCatching { searchRepository.search(q).first().tracks }.getOrDefault(emptyList())
        }.orEmpty()
        val merged = (local + remote).distinctBy { it.isrc }.take(SEARCH_LIMIT)
        synchronized(searchCache) {
            if (searchCache.size >= 8) searchCache.remove(searchCache.keys.first())
            if (merged.isNotEmpty()) searchCache[key] = merged
        }
        return merged
    }

    private companion object {
        const val FAV = "fav"
        const val PL = "pl"
        const val RECENT = "recent"
        const val DL = "dl"
        const val SEARCH = "search"
        const val RECENT_LIMIT = 50
        const val SEARCH_LIMIT = 40
        const val REMOTE_SEARCH_MS = 6_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BrowseTreeModule {
    @Binds abstract fun bindBrowseTree(impl: LibraryBrowseTree): BrowseTreeProvider
}
