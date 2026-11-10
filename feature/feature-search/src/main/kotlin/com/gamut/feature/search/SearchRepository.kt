package com.gamut.feature.search

import com.gamut.core.common.TextMatch
import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import com.gamut.core.network.DeezerApi
import com.gamut.core.network.DeezerArtistDto
import com.gamut.core.network.DeezerTrackDto
import com.gamut.core.network.ItunesApi
import com.gamut.core.network.ItunesTrackDto
import com.gamut.core.network.YoutubeCandidate
import com.gamut.core.network.YoutubeMusicSearchSource
import com.gamut.core.network.isValidYoutubeVideoId
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class SearchResult(val tracks: List<Track>, val isFinal: Boolean, val artist: ArtistSignal? = null)

interface SearchRepository {
    fun search(query: String): Flow<SearchResult>

    fun clearCache() {}
}

class SearchRepositoryImpl @Inject constructor(
    private val itunesApi: ItunesApi,
    private val deezerApi: DeezerApi,
    private val youtubeSource: YoutubeMusicSearchSource,
) : SearchRepository {
    private val resultCache = ConcurrentHashMap<String, CachedResult>()

    override fun clearCache() {
        resultCache.clear()
    }

    private val itunesRateGate = Mutex()
    private val itunesRequestTimes = ArrayDeque<Long>()

    override fun search(query: String): Flow<SearchResult> = channelFlow {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            send(SearchResult(emptyList(), true))
            return@channelFlow
        }

        val cacheKey = trimmed.lowercase()
        val cached = resultCache[cacheKey]?.takeIf { it.isFresh() }
        if (cached != null) {
            send(SearchResult(cached.tracks, true, cached.artist))
            return@channelFlow
        }

        val itunesDeferred = async { searchItunes(trimmed) }
        val deezerDeferred = async { searchDeezerTracks(trimmed) }
        val artistDeferred = async { lookupArtist(trimmed) }
        val youtubeDeferred = async {
            withTimeoutOrNull(YOUTUBE_BUDGET_MS) {
                runCatching {
                    val songs = youtubeSource.searchSongs(trimmed)
                    if (songs.isNotEmpty()) songs else youtubeSource.searchBroad(trimmed)
                }.getOrDefault(emptyList())
            }.orEmpty()
        }

        val itunes = itunesDeferred.await()
        val deezer = deezerDeferred.await()
        val first = assemble(trimmed, itunes, deezer, emptyList(), boostArtist = false, youtube = emptyList())
        send(SearchResult(first, isFinal = false))

        val artist = artistDeferred.await()
        val signal = ArtistSignal(TextMatch.norm(trimmed), artist?.name, artist?.fanCount)
        send(SearchResult(first, isFinal = false, artist = signal))

        val confidence = artist?.let { SearchRanking.artistConfidence(trimmed, it.name, it.fanCount, first) } ?: 0.0
        val boost = artist != null && confidence >= SearchRanking.ARTIST_BOOST_THRESHOLD
        val artistTracks = if (boost) topTracks(artist!!.id) else emptyList()

        val youtube = youtubeDeferred.await()
        val finalTracks = assemble(trimmed, itunes, deezer, artistTracks, boost, youtube)
        if (finalTracks.isNotEmpty()) {
            resultCache[cacheKey] = CachedResult(finalTracks, signal, System.currentTimeMillis())
        }
        send(SearchResult(finalTracks, isFinal = true, artist = signal))
    }

    private suspend fun searchItunes(rawQuery: String): List<Track> {
        val response = withItunesRateLimit {
            withTimeoutOrNull(ITUNES_TIMEOUT_MS) {
                runCatching { itunesApi.search(term = rawQuery, limit = SEARCH_LIMIT) }.getOrNull()
            }
        } ?: return emptyList()

        if (!response.isSuccessful) {
            return emptyList()
        }

        return response.body()?.results.orEmpty()
            .asSequence()
            .filter { it.isPlayableSong() }
            .distinctBy { it.trackId }
            .map { it.toDomain() }
            .toList()
    }

    private suspend fun <T> withItunesRateLimit(block: suspend () -> T): T {
        itunesRateGate.withLock {
            while (true) {
                val now = System.currentTimeMillis()
                while (itunesRequestTimes.firstOrNull()?.let { now - it >= ITUNES_WINDOW_MS } == true) {
                    itunesRequestTimes.removeFirst()
                }
                if (itunesRequestTimes.size < ITUNES_MAX_REQUESTS) {
                    itunesRequestTimes.addLast(now)
                    break
                }
                delay(ITUNES_WINDOW_MS - (now - itunesRequestTimes.first()))
            }
        }
        return block()
    }

    private suspend fun searchDeezerTracks(query: String): List<DeezerTrackDto> = withTimeoutOrNull(DEEZER_TIMEOUT_MS) {
        runCatching { deezerApi.searchTracks(query, limit = SEARCH_LIMIT).data }.getOrDefault(emptyList())
    }.orEmpty()

    private suspend fun lookupArtist(query: String): DeezerArtistDto? = withTimeoutOrNull(DEEZER_TIMEOUT_MS) {
        runCatching {
            deezerApi.searchArtists(query, limit = 5).data
                .map { it to SearchRanking.nameMatch(query, it.name) }
                .filter { (_, match) -> match > 0.0 }
                .maxWithOrNull(compareBy({ it.second }, { it.first.fanCount ?: 0L }))
                ?.first
        }.getOrNull()
    }

    private suspend fun topTracks(artistId: Long): List<DeezerTrackDto> = withTimeoutOrNull(DEEZER_TIMEOUT_MS) {
        runCatching {
            deezerApi.topTracks(artistId, limit = ARTIST_TRACK_LIMIT).data
                .distinctBy { it.id }
                .sortedByDescending { it.rank }
                .take(ARTIST_BOOST_TRACKS)
        }.getOrDefault(emptyList())
    }.orEmpty()

    private fun ItunesTrackDto.isPlayableSong(): Boolean {
        if (trackId == null || trackName.isNullOrBlank() || artistName.isNullOrBlank()) return false
        if (kind != null && kind != "song") return false
        return true
    }

    private fun Track.key() = TextMatch.norm(title) + "|" + TextMatch.primaryArtist(artist)

    private fun assemble(
        query: String,
        itunes: List<Track>,
        deezer: List<DeezerTrackDto>,
        artistTracks: List<DeezerTrackDto>,
        boostArtist: Boolean,
        youtube: List<YoutubeCandidate>,
    ): List<Track> {
        val popularityByKey = HashMap<String, Double>()
        (deezer + artistTracks).forEach { dto ->
            popularityByKey.merge(dto.toDomain().key(), SearchRanking.trackPopularity(dto.rank), ::maxOf)
        }
        val artistKeys = if (boostArtist) artistTracks.map { it.toDomain().key() }.toSet() else emptySet()
        fun boostFor(key: String) = if (key in artistKeys) ARTIST_BOOST else 0.0

        val candidates = ArrayList<Candidate>()
        val known = HashSet<String>()
        itunes.forEachIndexed { i, track ->
            val key = track.key()
            known += key
            candidates += Candidate(track, popularityByKey[key] ?: 0.0, i, boost = boostFor(key))
        }
        deezer.forEachIndexed { i, dto ->
            val track = dto.toDomain()
            val key = track.key()
            if (known.add(key)) {
                candidates += Candidate(track, SearchRanking.trackPopularity(dto.rank), i, penalty = 2.0, boost = boostFor(key))
            }
        }
        artistTracks.forEachIndexed { i, dto ->
            val track = dto.toDomain()
            if (known.add(track.key())) {
                candidates += Candidate(track, SearchRanking.trackPopularity(dto.rank), i, boost = boostFor(track.key()))
            }
        }

        val catalog = candidates.map { it.track }
        youtube
            .filter { isCleanYoutubeResult(it, query) }
            .filter { candidate -> catalog.none { duplicates(candidate, it) } }
            .forEachIndexed { i, candidate ->
                candidates += Candidate(
                    candidate.toYoutubeOnlyTrack(),
                    SearchRanking.viewsPopularity(candidate.viewCount),
                    i,
                    penalty = YOUTUBE_PENALTY,
                )
            }
        return SearchRanking.rank(query, candidates)
    }

    private fun YoutubeCandidate.toYoutubeOnlyTrack(): Track = Track(
        isrc = "youtube:$videoId",
        musicBrainzId = null,
        title = title,
        artist = channelName.removeSuffix(" - Topic"),
        album = null,
        durationMs = durationMs,
        albumArtUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
        source = TrackSource.YOUTUBE,
        youtubeVideoId = videoId,
    )

    private fun duplicates(candidate: YoutubeCandidate, track: Track): Boolean {
        val titleTokens = TextMatch.toks(track.title)
        val candidateTokens = TextMatch.toks(candidate.title)
        val coverage = titleTokens.count { it in candidateTokens }.toDouble() / titleTokens.size.coerceAtLeast(1)
        val artistHit = TextMatch.toks(track.artist).any { it in candidateTokens || it in TextMatch.toks(candidate.channelName) }
        val durationOk = candidate.durationMs <= 0 || track.durationMs <= 0 ||
            kotlin.math.abs(candidate.durationMs - track.durationMs) <= 10_000
        return coverage >= 0.8 && artistHit && durationOk
    }

    private fun isCleanYoutubeResult(candidate: YoutubeCandidate, query: String): Boolean {
        if (!isValidYoutubeVideoId(candidate.videoId)) return false
        if (candidate.durationMs > 0 && (candidate.durationMs < 60_000 || candidate.durationMs > 10 * 60_000)) return false
        val tokens = TextMatch.toks(candidate.title + " " + candidate.channelName)
        val queryTokens = TextMatch.toks(query)
        val queryEvidence = queryTokens.count { it in tokens }.toDouble() / queryTokens.size.coerceAtLeast(1)
        if (!candidate.isMusicCatalog && !candidate.channelIsTopicChannel && queryEvidence < 0.5) return false
        return tokens.none { it in TextMatch.DERIVATIVE }
    }

    private data class CachedResult(
        val tracks: List<Track>,
        val artist: ArtistSignal,
        val cachedAtMs: Long,
    ) {
        fun isFresh(): Boolean = System.currentTimeMillis() - cachedAtMs < CACHE_TTL_MS
    }

    private companion object {
        const val ITUNES_TIMEOUT_MS = 8_000L
        const val SEARCH_LIMIT = 25
        const val YOUTUBE_BUDGET_MS = 5_000L
        const val DEEZER_TIMEOUT_MS = 3_000L
        const val ARTIST_TRACK_LIMIT = 30
        const val ARTIST_BOOST_TRACKS = 15
        const val ARTIST_BOOST = 15.0
        const val YOUTUBE_PENALTY = 10.0
        const val ITUNES_MAX_REQUESTS = 15
        const val ITUNES_WINDOW_MS = 60_000L
        const val CACHE_TTL_MS = 10 * 60 * 1_000L
    }
}

private fun ItunesTrackDto.toDomain(): Track {
    val stableIsrc = "itunes:$trackId"
    return Track(
        isrc = stableIsrc,
        musicBrainzId = null,
        title = trackName.orEmpty(),
        artist = artistName.orEmpty(),
        album = collectionName,
        durationMs = trackTimeMillis ?: 0L,
        albumArtUrl = artworkUrl100?.upscaleArtwork(),
        source = TrackSource.ITUNES,
        youtubeVideoId = null,
    )
}

private fun DeezerTrackDto.toDomain(): Track = Track(
    isrc = "deezer:$id",
    title = listOfNotNull(titleShort ?: title, titleVersion?.takeIf { it.isNotBlank() })
        .joinToString(" "),
    artist = artist.name,
    album = album.title,
    durationMs = duration * 1_000L,
    albumArtUrl = album.coverXl ?: album.coverBig,
    source = TrackSource.ITUNES,
)

private fun String.upscaleArtwork(size: Int = 600): String = replace(Regex("/\\d+x\\d+bb\\."), "/${size}x${size}bb.")

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchRepositoryModule {
    @Binds abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository
}
