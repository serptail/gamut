package com.gamut.feature.search

import com.gamut.core.common.TextMatch
import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import com.gamut.core.network.AppleLookupItem
import com.gamut.core.network.DeezerApi
import com.gamut.core.network.DeezerArtistDto
import com.gamut.core.network.DeezerPhotoArtist
import com.gamut.core.network.ItunesApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class AppleCatalogRepository @Inject constructor(
    private val itunes: ItunesApi,
    private val deezer: DeezerApi,
) {
    private val cache = ConcurrentHashMap<Long, ArtistDetails>()
    private val overviewCache = ConcurrentHashMap<Long, ArtistDetails>()
    private val photoByName = ConcurrentHashMap<String, String>()
    private val photoCache = ConcurrentHashMap<String, String>()
    private val fansByName = ConcurrentHashMap<String, Long>()
    private val noPhotoChecked = ConcurrentHashMap<String, Int>()
    private val similarCache = ConcurrentHashMap<Long, List<DiscoverItem>>()

    suspend fun findArtistId(name: String): Long? = withTimeoutOrNull(5_000) {
        val wanted = TextMatch.primaryArtist(name)
        val hits = runCatching { itunes.searchArtists(name).results }.getOrDefault(emptyList())
            .filter { it.artistId != null }
        (hits.firstOrNull { TextMatch.norm(it.artistName.orEmpty()) == wanted } ?: hits.firstOrNull())?.artistId
    }

    suspend fun searchArtistCard(query: String): DiscoverItem? = withTimeoutOrNull(4_000) {
        if (TextMatch.norm(query).length < 2) return@withTimeoutOrNull null
        val hit = runCatching { itunes.searchArtists(query, limit = 5).results }
            .getOrDefault(emptyList())
            .filter { it.artistId != null }
            .map { it to SearchRanking.nameMatch(query, it.artistName.orEmpty()) }
            .filter { (_, match) -> match > 0.0 }
            .maxByOrNull { (_, match) -> match }
            ?.first ?: return@withTimeoutOrNull null
        artistCard(hit.artistId!!, hit.artistName.orEmpty())
    }

    suspend fun artistCard(id: Long, fallbackName: String): DiscoverItem? {
        val rows = runCatching { itunes.lookupAll(id.toString(), "album", 5).results }.getOrNull() ?: return null
        val albums = rows.filter { it.wrapperType == "collection" }
        val name = rows.firstOrNull { it.wrapperType == "artist" }?.artistName ?: fallbackName
        val photo = artistPhoto(id, name, albums.mapNotNull { it.collectionName })
        val art = photo ?: albums.firstOrNull()?.artworkUrl100.big()
        return DiscoverItem(
            id = "artist:$id",
            title = name,
            subtitle = "Artist",
            imageUrl = art,
            artistId = id,
            followers = fansByName[TextMatch.norm(name)],
        )
    }

    suspend fun artistPhoto(appleId: Long, name: String, appleAlbumTitles: List<String>): String? = photoByName[TextMatch.norm(name)] ?: cachedPhoto("id:$appleId", name, appleAlbumTitles)

    suspend fun artistPhotoFor(name: String, albumTitle: String): String? {
        photoByName[TextMatch.norm(name)]?.let { return it }
        return cachedPhoto("n:${TextMatch.norm(name)}|${TextMatch.norm(albumTitle)}", name, listOf(albumTitle))
    }

    private suspend fun cachedPhoto(key: String, name: String, appleAlbumTitles: List<String>): String? {
        photoCache[key]?.let { return it }
        if (name.isBlank()) return null
        val titles = appleAlbumTitles.count { it.isNotBlank() }
        if ((noPhotoChecked[key] ?: -1) >= titles) return null
        val outcome = withTimeoutOrNull(PHOTO_TIMEOUT_MS) {
            runCatching { findDeezerPhoto(name, appleAlbumTitles) }
                .onFailure { if (it is CancellationException) throw it }
        } ?: return null
        outcome.onSuccess { url ->
            if (url != null) {
                photoCache[key] = url
                photoByName[TextMatch.norm(name)] = url
                noPhotoChecked.remove(key)
            } else {
                noPhotoChecked[key] = maxOf(noPhotoChecked[key] ?: 0, titles)
            }
        }
        return outcome.getOrNull()
    }

    private suspend fun findDeezerPhoto(name: String, appleAlbumTitles: List<String>): String? {
        val artist = verifiedDeezerArtist(name, appleAlbumTitles) ?: return null
        val fans: Long? = artist.nbFan
        if (fans != null) fansByName[TextMatch.norm(name)] = fans
        return artist.photo()
    }

    private suspend fun verifiedDeezerArtist(name: String, appleAlbumTitles: List<String>): DeezerPhotoArtist? {
        val wanted = TextMatch.norm(name)
        val candidates = deezer.searchArtistPhotos(name).data
            .filter { TextMatch.norm(it.name) == wanted }
            .sortedByDescending { it.nbFan }
        val known = appleAlbumTitles.map { TextMatch.norm(it) }.filter { it.isNotBlank() }
        if (known.isEmpty()) return candidates.singleOrNull()
        for (candidate in candidates.take(3)) {
            val theirs = deezer.artistAlbumTitles(candidate.id).data.map { TextMatch.norm(it.title) }
            if (theirs.any { t -> t.isNotBlank() && known.any { k -> sameAlbum(t, k) } }) return candidate
        }
        val top = candidates.firstOrNull() ?: return null
        val second = candidates.getOrNull(1)
        return top.takeIf { second == null || (top.nbFan ?: 0L) >= (second.nbFan ?: 0L) * 5 }
    }

    private fun sameAlbum(a: String, b: String): Boolean = a == b || (minOf(a.length, b.length) >= 4 && (a.startsWith(b) || b.startsWith(a)))

    private fun DeezerPhotoArtist.photo(): String? = (pictureXl ?: pictureBig)?.takeIf { it.isNotBlank() && !it.contains("/artist//") }

    suspend fun similarArtists(base: ArtistDetails): List<DiscoverItem> {
        similarCache[base.id]?.let { return it }
        val found = withTimeoutOrNull(SIMILAR_TIMEOUT_MS) {
            try {
                loadSimilar(base)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }.orEmpty()
        if (found.isNotEmpty()) similarCache[base.id] = found
        return found
    }

    private suspend fun loadSimilar(base: ArtistDetails): List<DiscoverItem> {
        val self = verifiedDeezerArtist(base.name, base.albums.map { it.title }.take(40)) ?: return emptyList()
        val ownName = TextMatch.norm(base.name)
        return deezer.relatedArtists(self.id).data
            .filter { it.id != self.id && TextMatch.norm(it.name) != ownName }
            .mapNotNull { related ->
                val photo = related.photo() ?: return@mapNotNull null
                DiscoverItem(
                    id = "similar:${related.id}",
                    title = related.name,
                    subtitle = "Artist",
                    imageUrl = photo,
                    followers = related.fanCount,
                )
            }
            .take(SIMILAR_LIMIT)
    }

    private fun DeezerArtistDto.photo(): String? = (pictureXl ?: pictureBig)?.takeIf { it.isNotBlank() && !it.contains("/artist//") }

    private fun ArtistDetails.withKnownPhoto(): ArtistDetails = photoByName[TextMatch.norm(name)]?.let { copy(imageUrl = it) } ?: this

    suspend fun artistOverview(id: Long): ArtistDetails? {
        (cache[id] ?: overviewCache[id])?.let { return it.withKnownPhoto() }
        return withTimeoutOrNull(14_000) {
            var details: ArtistDetails? = null
            for (attempt in 0..1) {
                details = try {
                    loadOverview(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (details != null) break
                if (attempt == 0) delay(1_500)
            }
            details
        }?.withKnownPhoto()?.also { overviewCache[id] = it }
    }

    private suspend fun loadOverview(id: Long): ArtistDetails? = coroutineScope {
        val topJob = async { itunes.lookupAll(id.toString(), "song", 50).results }
        val rows = itunes.lookupAll(id.toString(), "album", 200).results
        val albums = rows.filter { it.wrapperType == "collection" && it.collectionId != null }
            .distinctBy { TextMatch.norm(it.collectionName.orEmpty()) }
            .sortedByDescending { it.releaseDate.orEmpty() }
        val top = topJob.await().filter { it.kind == "song" }
        if (top.isEmpty() && albums.isEmpty()) return@coroutineScope null
        ArtistDetails(
            id = id,
            name = rows.firstOrNull { it.wrapperType == "artist" }?.artistName
                ?: top.firstOrNull()?.artistName.orEmpty(),
            imageUrl = (top.firstOrNull()?.artworkUrl100 ?: albums.firstOrNull()?.artworkUrl100).big(),
            popular = top.take(10).map { it.toTrack() },
            songs = top.map { it.toTrack() },
            albums = albums.map {
                DiscoverItem(
                    id = "apple-album:${it.collectionId}",
                    title = it.collectionName.orEmpty(),
                    subtitle = it.releaseDate.orEmpty().take(4),
                    imageUrl = it.artworkUrl100.big(),
                )
            },
            songsLoaded = false,
        )
    }

    suspend fun artistSongs(base: ArtistDetails): ArtistDetails {
        cache[base.id]?.let { return it }
        val albumIds = base.albums.mapNotNull { it.id.removePrefix("apple-album:").toLongOrNull() }
        val gate = Semaphore(2)
        val failedBatches = AtomicInteger(0)
        val all = withTimeoutOrNull(40_000) {
            coroutineScope {
                albumIds.chunked(6).map { batch ->
                    async {
                        gate.withPermit {
                            var rows: List<AppleLookupItem>? = null
                            for (attempt in 0..1) {
                                rows = try {
                                    itunes.lookupAll(batch.joinToString(","), "song", 200).results
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    null
                                }
                                if (rows != null) break
                                if (attempt == 0) delay(1_500)
                            }
                            if (rows == null) failedBatches.incrementAndGet()
                            rows.orEmpty()
                        }
                    }
                }.awaitAll().flatten()
            }
        }.orEmpty()
        val rest = all.filter { it.kind == "song" && it.artistId == base.id }
            .sortedByDescending { it.releaseDate.orEmpty() }
        val restTracks = rest.map { it.toTrack() }
        val full = base.copy(
            songs = (base.songs + restTracks).distinctBy { TextMatch.norm(it.title) },
            latestSongs = restTracks.distinctBy { TextMatch.norm(it.title) }.take(30),
            songsLoaded = true,
        )
        if (all.isNotEmpty() && failedBatches.get() == 0) cache[base.id] = full
        return full
    }

    private fun AppleLookupItem.toTrack() = Track(
        isrc = "apple:$trackId",
        title = trackName.orEmpty(),
        artist = artistName.orEmpty(),
        album = collectionName,
        durationMs = trackTimeMillis ?: 0L,
        albumArtUrl = artworkUrl100.big(),
        source = TrackSource.ITUNES,
    )
}

private const val PHOTO_TIMEOUT_MS = 6_000L
private const val SIMILAR_TIMEOUT_MS = 8_000L
private const val SIMILAR_LIMIT = 12

internal fun String?.big(): String = this.orEmpty().replace(Regex("/\\d+x\\d+bb\\."), "/600x600bb.")
