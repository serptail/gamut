package com.gamut.feature.search

import com.gamut.core.database.FollowDao
import com.gamut.core.database.RecentDao
import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import com.gamut.core.network.DeezerAlbumDto
import com.gamut.core.network.DeezerArtistDto
import com.gamut.core.network.DeezerChartsApi
import com.gamut.core.network.DeezerTrackDto
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class MoreFeed(val section: FeedSection?, val consumed: List<String>, val exhausted: Boolean) {
    val failed: Boolean get() = section == null && !exhausted
}

@Singleton
class FeedRepository @Inject constructor(
    private val api: DeezerChartsApi,
    private val follows: FollowDao,
    private val recents: RecentDao,
) {
    private data class Genre(val id: Int, val name: String)
    private data class Taste(val name: String, val followed: Boolean)
    private enum class SpecType { GENRE_TRACKS, GENRE_ALBUMS, GENRE_ARTISTS, BECAUSE_TOP, BECAUSE_RELATED }
    private data class Spec(
        val id: String,
        val type: SpecType,
        val genre: Genre? = null,
        val taste: Taste? = null,
        val rails: Boolean = false,
    )

    private val nonce = AtomicInteger(0)

    @Volatile private var tasteCache: List<Taste>? = null
    private val deezerArtistIds = ConcurrentHashMap<String, Long>()

    @Volatile private var globalArtistIds: Set<Any?>? = null

    @Volatile private var globalAlbumIds: Set<Any?>? = null

    @Volatile private var artistGenreChartIgnoresGenre = false

    @Volatile private var albumGenreChartIgnoresGenre = false

    fun clearCache() {
        nonce.incrementAndGet()
        tasteCache = null
        globalArtistIds = null
        globalAlbumIds = null
    }

    fun seed(): Long = LocalDate.now().toEpochDay() * 31 + nonce.get()

    fun quickPicks(chart: List<DiscoverItem>, seed: Long): FeedSection? {
        val pool = chart.filter { it.track != null }.distinctBy { it.id }.drop(3).take(QUICK_PICK_POOL)
        if (pool.size < 4) return null
        val picks = pool.shuffled(Random(seed)).take(QUICK_PICK_COUNT).let { if (it.size % 2 == 1) it.dropLast(1) else it }
        return FeedSection(
            id = "quick:today",
            kind = FeedKind.QUICK_PICKS,
            slot = FeedSlot.TOP,
            order = 0,
            title = "Picked for today",
            subtitle = "A different mix every day",
            items = picks,
        )
    }

    suspend fun head(knownArtistKeys: Set<String>, onSection: suspend (FeedSection) -> Unit) {
        coroutineScope {
            launch { releases().forEach { onSection(it) } }
            launch { worldwide()?.let { onSection(it) } }
            launch { hotArtists(knownArtistKeys)?.let { onSection(it) } }
            launch {
                val taste = taste().firstOrNull() ?: return@launch
                val artistId = deezerArtistId(taste.name, attempts = 2) ?: return@launch
                coroutineScope {
                    launch { becauseTop(artistId, taste, FeedSlot.MIDDLE, order = 10, attempts = 2)?.let { onSection(it) } }
                    launch { becauseRelated(artistId, taste, FeedSlot.MIDDLE, order = 11, attempts = 2)?.let { onSection(it) } }
                }
            }
        }
    }

    private suspend fun releases(): List<FeedSection> {
        val today = LocalDate.now().toString()
        val albums = load { api.newReleases(0, 40).data }
            .filter { !it.title.isNullOrBlank() && (it.coverXl ?: it.coverBig) != null && it.releaseDate.orEmpty().take(10) <= today }
            .distinctBy { it.id }
            .sortedByDescending { it.releaseDate.orEmpty() }
        if (albums.size < 4) return emptyList()
        val spotlight = albums.first()
        val chip = when (spotlight.recordType) {
            "single" -> "New single"
            "ep" -> "New EP"
            else -> "New album"
        }
        return listOf(
            FeedSection(
                id = "releases:spotlight",
                kind = FeedKind.SPOTLIGHT,
                slot = FeedSlot.MIDDLE,
                order = 20,
                title = chip,
                items = listOf(spotlight.toAlbumItem(large = true)),
            ),
            FeedSection(
                id = "releases:new",
                kind = FeedKind.ALBUM_CAROUSEL,
                slot = FeedSlot.MIDDLE,
                order = 21,
                title = "New releases",
                subtitle = "Just dropped",
                items = albums.drop(1).take(20).map { it.toAlbumItem() },
            ),
        )
    }

    private suspend fun worldwide(): FeedSection? {
        val tracks = load { api.chartTracks(0, 40).data }.distinctBy { it.id }
        if (tracks.size < 6) return null
        return FeedSection(
            id = "worldwide:tracks",
            kind = FeedKind.TRACK_RAILS,
            slot = FeedSlot.BOTTOM,
            order = 30,
            title = "Trending in the community",
            subtitle = "What listeners are playing right now",
            items = tracks.take(RAILS_COUNT).map { it.toTrackItem() },
        )
    }

    private suspend fun hotArtists(knownKeys: Set<String>): FeedSection? {
        val artists = load { api.chartArtists(0, 30).data }
            .filter { artistKey(it.name) !in knownKeys }
            .distinctBy { it.id }
            .take(CAROUSEL_COUNT)
        if (artists.size < 4) return null
        return FeedSection(
            id = "hot:artists",
            kind = FeedKind.ARTIST_CAROUSEL,
            slot = FeedSlot.BOTTOM,
            order = 31,
            title = "Popular artists",
            subtitle = "Top of the community chart",
            items = artists.map { it.toArtistItem() },
        )
    }

    private suspend fun taste(): List<Taste> {
        tasteCache?.let { return it }
        val result = try {
            val out = LinkedHashMap<String, Taste>()
            follows.all().sortedByDescending { it.followedAtEpochMs }.forEach { f ->
                val key = artistKey(f.name)
                if (key.isNotBlank()) out.putIfAbsent(key, Taste(f.name, followed = true))
            }
            val counts = LinkedHashMap<String, Int>()
            val names = HashMap<String, String>()
            recents.observeTracks(RECENT_SCAN).first().forEach { t ->
                val name = leadName(t.artist)
                val key = artistKey(name)
                if (key.isNotBlank()) {
                    counts.merge(key, 1, Int::plus)
                    names.putIfAbsent(key, name)
                }
            }
            counts.entries.sortedByDescending { it.value }.forEach { (key, _) -> out.putIfAbsent(key, Taste(names.getValue(key), followed = false)) }
            out.values.take(MAX_TASTE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return emptyList()
        }
        tasteCache = result
        return result
    }

    private suspend fun deezerArtistId(name: String, attempts: Int): Long? {
        val key = artistKey(name)
        deezerArtistIds[key]?.let { return it }
        val id = load(attempts) { api.searchArtists(name, 5).data }
            .filter { artistKey(it.name) == key }
            .maxByOrNull { it.fanCount ?: 0L }?.id
        if (id != null) deezerArtistIds[key] = id
        return id
    }

    private suspend fun becauseTop(artistId: Long, taste: Taste, slot: FeedSlot, order: Int, attempts: Int): FeedSection? {
        val tracks = load(attempts) { api.artistTop(artistId, 20).data }.distinctBy { it.id }
        if (tracks.size < 4) return null
        val key = artistKey(taste.name)
        return FeedSection(
            id = if (slot == FeedSlot.BOTTOM) "because-top:$key" else "because:top:$key",
            kind = FeedKind.TRACK_CAROUSEL,
            slot = slot,
            order = order,
            title = if (taste.followed) "Because you follow ${taste.name}" else "Because you listen to ${taste.name}",
            subtitle = if (taste.followed) "More from an artist you follow" else "More of what you have been playing",
            items = tracks.take(CAROUSEL_COUNT).map { it.toTrackItem() },
        )
    }

    private suspend fun becauseRelated(artistId: Long, taste: Taste, slot: FeedSlot, order: Int, attempts: Int): FeedSection? {
        val related = load(attempts) { api.relatedArtists(artistId, 20).data }.distinctBy { it.id }
        if (related.size < 4) return null
        val key = artistKey(taste.name)
        return FeedSection(
            id = if (slot == FeedSlot.BOTTOM) "because-rel:$key" else "because:related:$key",
            kind = FeedKind.ARTIST_CAROUSEL,
            slot = slot,
            order = order,
            title = "Fans of ${taste.name} also like",
            items = related.take(CAROUSEL_COUNT).map { it.toArtistItem() },
        )
    }

    suspend fun more(consumed: Set<String>, order: Int): MoreFeed {
        val pending = plan(seed(), taste()).filter { it.id !in consumed }
        val tried = ArrayList<String>()
        for ((index, spec) in pending.withIndex()) {
            if (tried.size >= MAX_SKIPS) return MoreFeed(null, tried, exhausted = false)
            tried += spec.id
            val section = build(spec, order)
            if (section != null) return MoreFeed(section, tried, exhausted = index == pending.lastIndex)
        }
        return MoreFeed(null, tried, exhausted = true)
    }

    private fun plan(seed: Long, taste: List<Taste>): List<Spec> {
        val out = ArrayList<Spec>()
        GENRES.shuffled(Random(seed)).forEachIndexed { i, g ->
            out += Spec("g-tracks:${g.id}", SpecType.GENRE_TRACKS, genre = g, rails = i % 2 == 0)
            if (i % 2 == 1) out += Spec("g-albums:${g.id}", SpecType.GENRE_ALBUMS, genre = g)
            if (i % 3 == 2) out += Spec("g-artists:${g.id}", SpecType.GENRE_ARTISTS, genre = g)
        }
        taste.drop(HEAD_TASTE).take(4).forEachIndexed { i, t ->
            val at = (1 + i * 4).coerceAtMost(out.size)
            out.add(at, Spec("because-top:${artistKey(t.name)}", SpecType.BECAUSE_TOP, taste = t))
            if (i == 0) out.add((at + 2).coerceAtMost(out.size), Spec("because-rel:${artistKey(t.name)}", SpecType.BECAUSE_RELATED, taste = t))
        }
        return out
    }

    private suspend fun build(spec: Spec, order: Int): FeedSection? {
        val genre = spec.genre
        return when (spec.type) {
            SpecType.GENRE_TRACKS -> {
                genre ?: return null
                val tracks = load(attempts = 1) { api.chartTracks(genre.id, 30).data }.distinctBy { it.id }
                if (tracks.size < 6) return null
                FeedSection(
                    id = spec.id,
                    kind = if (spec.rails) FeedKind.TRACK_RAILS else FeedKind.TRACK_CAROUSEL,
                    slot = FeedSlot.BOTTOM,
                    order = order,
                    title = "Trending in ${genre.name}",
                    subtitle = "Most played by the community in ${genre.name}",
                    items = tracks.take(if (spec.rails) RAILS_COUNT else CAROUSEL_COUNT).map { it.toTrackItem() },
                )
            }

            SpecType.GENRE_ALBUMS -> {
                genre ?: return null
                if (albumGenreChartIgnoresGenre) return null
                val albums = load(attempts = 1) { api.chartAlbums(genre.id, 30).data }
                    .filter { !it.title.isNullOrBlank() && (it.coverBig ?: it.coverXl) != null }.distinctBy { it.id }
                if (albums.size < 4) return null
                if (isCopyOf(albums.take(CAROUSEL_COUNT).map { it.id }, globalAlbums())) {
                    albumGenreChartIgnoresGenre = true
                    return null
                }
                FeedSection(
                    id = spec.id,
                    kind = FeedKind.ALBUM_CAROUSEL,
                    slot = FeedSlot.BOTTOM,
                    order = order,
                    title = "${genre.name} albums",
                    subtitle = "Community favorites in ${genre.name}",
                    items = albums.take(CAROUSEL_COUNT).map { it.toAlbumItem() },
                )
            }

            SpecType.GENRE_ARTISTS -> {
                genre ?: return null
                if (artistGenreChartIgnoresGenre) return null
                val artists = load(attempts = 1) { api.chartArtists(genre.id, 30).data }.distinctBy { it.id }
                if (artists.size < 4) return null
                if (isCopyOf(artists.take(CAROUSEL_COUNT).map { it.id }, globalArtists())) {
                    artistGenreChartIgnoresGenre = true
                    return null
                }
                FeedSection(
                    id = spec.id,
                    kind = FeedKind.ARTIST_CAROUSEL,
                    slot = FeedSlot.BOTTOM,
                    order = order,
                    title = "Top in ${genre.name}",
                    subtitle = "Most followed by the community in ${genre.name}",
                    items = artists.take(CAROUSEL_COUNT).map { it.toArtistItem() },
                )
            }

            SpecType.BECAUSE_TOP -> {
                val taste = spec.taste ?: return null
                val id = deezerArtistId(taste.name, attempts = 1) ?: return null
                becauseTop(id, taste, FeedSlot.BOTTOM, order, attempts = 1)
            }

            SpecType.BECAUSE_RELATED -> {
                val taste = spec.taste ?: return null
                val id = deezerArtistId(taste.name, attempts = 1) ?: return null
                becauseRelated(id, taste, FeedSlot.BOTTOM, order, attempts = 1)
            }
        }
    }

    private fun isCopyOf(ids: List<Any?>, global: Set<Any?>?): Boolean = global != null && global.isNotEmpty() && ids.isNotEmpty() && ids.count { it in global } * 10 >= ids.size * 6

    private suspend fun globalArtists(): Set<Any?>? = globalArtistIds ?: load(attempts = 1) { api.chartArtists(0, 30).data }.map { it.id }.toSet()
        .takeIf { it.isNotEmpty() }?.also { globalArtistIds = it }

    private suspend fun globalAlbums(): Set<Any?>? = globalAlbumIds ?: load(attempts = 1) { api.chartAlbums(0, 30).data }.map { it.id }.toSet()
        .takeIf { it.isNotEmpty() }?.also { globalAlbumIds = it }

    suspend fun album(item: DiscoverItem): AlbumDetails? {
        val id = item.id.removePrefix(DEEZER_ALBUM_PREFIX).toLongOrNull() ?: return null
        val rows = load(attempts = 2) { api.albumTracks(id, 100).data }
        val tracks = rows.map { it.toTrack(albumTitle = item.title, art = item.imageUrl.ifBlank { null }) }
        if (tracks.isEmpty()) return null
        return AlbumDetails(item.id, item.title, item.subtitle.ifBlank { tracks.first().artist }, item.imageUrl.ifBlank { null }, tracks)
    }

    private suspend fun <T> load(attempts: Int = 2, block: suspend () -> List<T>): List<T> {
        repeat(attempts) { n ->
            val rows = withTimeoutOrNull(if (n == 0) FIRST_TRY_MS else RETRY_MS) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            if (!rows.isNullOrEmpty()) return rows
            if (n < attempts - 1) delay(400L)
        }
        return emptyList()
    }

    private fun leadName(artist: String): String = artist.split(LEAD_SPLIT).first().trim()

    private fun trackTitle(t: DeezerTrackDto): String = listOfNotNull(t.titleShort ?: t.title, t.titleVersion?.takeIf { it.isNotBlank() }).joinToString(" ")

    private fun DeezerTrackDto.toTrack(albumTitle: String? = null, art: String? = null) = Track(
        isrc = "deezer:$id",
        title = trackTitle(this),
        artist = artist.name,
        album = albumTitle ?: album.title,
        durationMs = duration * 1_000L,
        albumArtUrl = art ?: album.coverBig ?: album.coverXl,
        source = TrackSource.ITUNES,
    )

    private fun DeezerTrackDto.toTrackItem() = toTrack().let { track ->
        DiscoverItem(id = "deezer-track:$id", title = track.title, subtitle = artist.name, imageUrl = track.albumArtUrl.orEmpty(), track = track)
    }

    private fun DeezerAlbumDto.toAlbumItem(large: Boolean = false) = DiscoverItem(
        id = DEEZER_ALBUM_PREFIX + id,
        title = title.orEmpty(),
        subtitle = artist?.name.orEmpty(),
        imageUrl = (if (large) coverXl ?: coverBig else coverBig ?: coverXl).orEmpty(),
        meta = releasedAgo(releaseDate),
    )

    private fun DeezerArtistDto.toArtistItem() = DiscoverItem(
        id = "deezer-artist:$id",
        title = name,
        subtitle = "",
        imageUrl = (pictureBig ?: pictureXl).orEmpty(),
        followers = fanCount,
    )

    companion object {
        const val DEEZER_ALBUM_PREFIX = "deezer-album:"

        private const val QUICK_PICK_POOL = 60
        private const val QUICK_PICK_COUNT = 8
        private const val RAILS_COUNT = 24
        private const val CAROUSEL_COUNT = 20
        private const val RECENT_SCAN = 60
        private const val MAX_TASTE = 6
        private const val HEAD_TASTE = 1
        private const val MAX_SKIPS = 3
        private const val FIRST_TRY_MS = 9_000L
        private const val RETRY_MS = 6_000L

        private val LEAD_SPLIT = Regex("(?i)\\s+(featuring|feat\\.?|ft\\.?|with|x|&|and)\\s+|,|/")

        private val GENRES = listOf(
            Genre(132, "Pop"),
            Genre(116, "Rap & Hip-Hop"),
            Genre(165, "R&B"),
            Genre(152, "Rock"),
            Genre(113, "Dance"),
            Genre(85, "Alternative"),
            Genre(106, "Electro"),
            Genre(197, "Latin"),
            Genre(2, "Africa"),
            Genre(129, "Jazz"),
            Genre(144, "Reggae"),
            Genre(464, "Metal"),
            Genre(98, "Classical"),
        )
    }
}
