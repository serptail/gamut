package com.gamut.feature.search

import android.util.Log
import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import com.gamut.core.network.AppleChartItem
import com.gamut.core.network.AppleChartsApi
import com.gamut.core.network.ItunesApi
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

data class DiscoverState(
    val albums: List<DiscoverItem> = emptyList(),
    val artists: List<DiscoverItem> = emptyList(),
    val tabs: List<DiscoverTab> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val feed: List<FeedSection> = emptyList(),
    val feedLoadingMore: Boolean = false,
    val feedExhausted: Boolean = false,
    val feedFailed: Boolean = false,
    val feedEnabled: Boolean = true,
)

data class ArtistDetails(
    val id: Long,
    val name: String,
    val imageUrl: String?,
    val popular: List<Track>,
    val songs: List<Track>,
    val albums: List<DiscoverItem>,
    val latestSongs: List<Track> = emptyList(),
    val listeners: String = "",
    val songsLoaded: Boolean = true,
    val similar: List<DiscoverItem> = emptyList(),
)

data class AlbumDetails(
    val id: String,
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val tracks: List<Track>,
)

@Singleton
class DiscoverRepository @Inject constructor(
    private val apple: AppleChartsApi,
    private val itunes: ItunesApi,
    private val catalog: AppleCatalogRepository,
    private val diskCache: DiscoverDiskCache,
    private val billboard: BillboardRepository,
    private val feedRepo: FeedRepository,
) {
    @Volatile private var cachedDiscover: DiscoverState? = null
    private val albumCache = ConcurrentHashMap<String, AlbumDetails>()

    fun clearCache() {
        cachedDiscover = null
        albumCache.clear()
        billboard.clearCache()
        feedRepo.clearCache()
    }

    private inline fun <T> safe(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun <T> fetchList(label: String, timeoutMs: Long, attempts: Int = 3, block: suspend () -> List<T>): List<T> {
        repeat(attempts) { n ->
            val budget = if (n == 0) timeoutMs else timeoutMs * 2 / 3
            var failure: Throwable? = null
            val rows = withTimeoutOrNull(budget) { safe { block() }.onFailure { failure = it }.getOrNull() }
            if (!rows.isNullOrEmpty()) return rows
            val why = when {
                failure != null -> "failed: $failure"
                rows == null -> "timed out after ${budget}ms"
                else -> "returned an empty list"
            }
            logW("$label attempt ${n + 1}/$attempts $why")
            if (n < attempts - 1) delay(500L * (n + 1))
        }
        return emptyList()
    }

    private suspend fun <T : Any> fetchOne(attempts: Int = 3, label: String = "fetchOne", block: suspend () -> T?): T? {
        var last: Throwable? = null
        repeat(attempts) { n ->
            val result = safe { block() }
            result.getOrNull()?.let { return it }
            result.exceptionOrNull()?.let { last = it }
            if (n < attempts - 1) delay(600L * (n + 1))
        }
        logW("$label gave up after $attempts attempts: ${last ?: "no result"}")
        return null
    }

    private fun logW(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: Throwable) { }
    }

    fun discoverFlow(): Flow<DiscoverState> = channelFlow {
        cachedDiscover?.let {
            send(it.copy(isLoading = false))
            return@channelFlow
        }
        val stale = diskCache.read()
        if (stale != null) send(stale)
        val (songs, rawAlbums, bb) = coroutineScope {
            val songsJob = async { fetchList("apple top songs", CHART_TIMEOUT_MS) { apple.topSongs(100).feed.results } }
            val albumsJob = async { fetchList("apple top albums", CHART_TIMEOUT_MS) { apple.topAlbums(20).feed.results.take(20) } }
            val billboardJob = async { withTimeoutOrNull(CHART_TIMEOUT_MS) { billboard.chart() }.orEmpty() }
            Triple(songsJob.await(), albumsJob.await(), billboardJob.await())
        }
        if (songs.isEmpty() && rawAlbums.isEmpty()) {
            logW("charts unavailable (songs and albums both empty); stale copy " + if (stale != null) "kept on screen" else "missing -> error state")
            if (stale == null) send(DiscoverState(isLoading = false, error = "Discover is unavailable. Check your connection and try again."))
            return@channelFlow
        }
        val albums = boostAlbums(rawAlbums, bb)
        val latest = latestFrom(songs)
        val resolved = HashMap<String, Track>()

        val feedSeed = feedRepo.seed()
        val feedMap = LinkedHashMap<String, FeedSection>()
        val freshFeedIds = HashSet<String>()
        stale?.feed?.forEach { feedMap[it.id] = it }
        fun putFeed(section: FeedSection) {
            freshFeedIds += section.id
            val family = section.id.substringBefore(':')
            feedMap.keys.removeAll { it !in freshFeedIds && it.substringBefore(':') == family }
            feedMap[section.id] = section
        }
        fun feedNow(): List<FeedSection> = feedMap.values.sortedWith(compareBy<FeedSection>({ it.slot.ordinal }, { it.order }))
        feedRepo.quickPicks(songs.map { it.toDiscoverItem() }, feedSeed)?.let { putFeed(it) }

        fun buildTabs(): List<DiscoverTab> = buildList {
            blendTrending(songs, bb, resolved).takeIf { it.isNotEmpty() }?.let { add(DiscoverTab("Trending", "Trending now", it)) }
            if (bb.isNotEmpty()) {
                add(DiscoverTab("Billboard", "Billboard Hot 100", bb.take(BILLBOARD_TAB_LIMIT).map { it.toDiscoverItem(resolved[it.key]) }))
            }
            if (latest.isNotEmpty()) add(DiscoverTab("Latest", "New on the charts", latest.map { it.toDiscoverItem().copy(meta = releasedAgo(it.releaseDate)) }))
            if (albums.isNotEmpty()) add(DiscoverTab("Albums", "Apple Music top albums", albums.map { it.toDiscoverAlbum() }))
        }

        val seeds = songs.mapNotNull { item -> item.artistId?.toLongOrNull()?.let { Triple(it, item.artistName, item) } }
            .distinctBy { it.first }.take(10)
        val slots = arrayOfNulls<DiscoverItem>(seeds.size)
        fun placeholder(seed: Triple<Long, String, AppleChartItem>) = DiscoverItem(
            id = "artist-seed:${seed.first}",
            title = seed.second,
            subtitle = "",
            imageUrl = seed.third.artworkUrl100.big(),
            artistId = seed.first,
        )
        fun shown(): List<DiscoverItem> = seeds.mapIndexed { i, s -> slots[i] ?: placeholder(s) }

        val progressive = stale == null || stale.artists.isEmpty()
        var artistsNow: List<DiscoverItem> = if (progressive) shown() else stale!!.artists
        val albumRow = if (albums.isNotEmpty()) albums.take(10).map { it.toDiscoverAlbum() } else stale?.albums.orEmpty()
        var tabsNow = buildTabs()
        fun state() = DiscoverState(albums = albumRow, artists = artistsNow, tabs = tabsNow, isLoading = false, feed = feedNow())
        send(state())

        val lock = Mutex()
        val gate = Semaphore(4)
        coroutineScope {
            launch {
                withTimeoutOrNull(FEED_TIMEOUT_MS) {
                    safe {
                        val known = lock.withLock { artistsNow.map { artistKey(it.title) }.toSet() }
                        feedRepo.head(known) { section ->
                            lock.withLock {
                                putFeed(section)
                                send(state())
                            }
                        }
                    }
                }
            }
            launch {
                withTimeoutOrNull(ARTIST_IMAGES_TIMEOUT_MS) {
                    coroutineScope {
                        seeds.forEachIndexed { index, (id, name, _) ->
                            launch {
                                val card = gate.withPermit { fetchOne(label = "artist card $name") { catalog.artistCard(id, name) } } ?: return@launch
                                lock.withLock {
                                    slots[index] = card
                                    if (progressive) {
                                        artistsNow = shown()
                                        send(state())
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (bb.isNotEmpty()) {
                launch {
                    var lastPushMs = 0L
                    withTimeoutOrNull(BILLBOARD_RESOLVE_TIMEOUT_MS) {
                        billboard.resolve(bb.take(BILLBOARD_TAB_LIMIT)) { entry, track ->
                            lock.withLock {
                                resolved[entry.key] = track
                                val now = System.currentTimeMillis()
                                if (now - lastPushMs >= PUSH_THROTTLE_MS) {
                                    lastPushMs = now
                                    tabsNow = buildTabs()
                                    send(state())
                                }
                            }
                        }
                    }
                    lock.withLock {
                        tabsNow = buildTabs()
                        send(state())
                    }
                }
            }
        }
        val portraitsComplete = seeds.isNotEmpty() && slots.all { it != null }
        lock.withLock {
            if (portraitsComplete || progressive) artistsNow = shown()
            send(state())
        }
        val finalState = state()
        if (portraitsComplete && rawAlbums.isNotEmpty() && bb.isNotEmpty()) {
            cachedDiscover = finalState
            safe { diskCache.write(finalState) }
        } else if (finalState.tabs.isNotEmpty()) {
            val partial = finalState.copy(artists = if (portraitsComplete) finalState.artists else stale?.artists.orEmpty())
            if (stale == null || partial.quality() >= stale.quality()) safe { diskCache.write(partial) }
        }
    }

    private fun DiscoverState.quality(): Int = tabs.size * 10 + (if (albums.isNotEmpty()) 5 else 0) + artists.count { !it.id.startsWith("artist-seed:") } + feed.size * 2

    private fun blendTrending(apple: List<AppleChartItem>, bb: List<BillboardEntry>, resolved: Map<String, Track>): List<DiscoverItem> {
        val bbByKey = bb.associateBy { it.key }
        val seen = HashSet<String>()
        val scored = ArrayList<Pair<Double, DiscoverItem>>()
        apple.take(100).forEachIndexed { i, item ->
            val key = chartKey(item.name, item.artistName)
            seen += key
            val billboardPoints = bbByKey[key]?.let { BLEND_WEIGHT * (101 - it.rank) } ?: 0.0
            scored += (BLEND_WEIGHT * (100 - i) + billboardPoints) to item.toDiscoverItem()
        }
        bb.forEach { entry ->
            if (entry.key !in seen) resolved[entry.key]?.let { scored += BLEND_WEIGHT * (101 - entry.rank) to entry.toDiscoverItem(it) }
        }
        return scored.sortedByDescending { it.first }.take(50).map { it.second }
    }

    private fun boostAlbums(albums: List<AppleChartItem>, bb: List<BillboardEntry>): List<AppleChartItem> {
        if (bb.isEmpty()) return albums
        val heat = HashMap<String, Double>()
        bb.forEach { heat.merge(it.artistKey, (101 - it.rank) / 4.0, Double::plus) }
        return albums.withIndex()
            .sortedByDescending { (i, a) -> (albums.size - i) + (heat[artistKey(a.artistName)] ?: 0.0).coerceAtMost(ALBUM_BOOST_CAP) }
            .map { it.value }
    }

    private fun latestFrom(chart: List<AppleChartItem>): List<AppleChartItem> {
        val dated = chart.filter { !it.releaseDate.isNullOrBlank() }.sortedByDescending { it.releaseDate }
        fun within(days: Long) = LocalDate.now().minusDays(days).toString().let { cut -> dated.filter { it.releaseDate.orEmpty() >= cut } }
        val recent = within(60).takeIf { it.size >= 5 } ?: within(180)
        return recent.take(10)
    }

    suspend fun findArtistId(name: String): Long? = catalog.findArtistId(name)

    suspend fun artistOverview(id: Long): ArtistDetails? = catalog.artistOverview(id)

    suspend fun artistSongs(base: ArtistDetails): ArtistDetails = catalog.artistSongs(base)

    suspend fun artistPhoto(base: ArtistDetails): String? = catalog.artistPhoto(base.id, base.name, base.albums.map { it.title }.take(40))

    suspend fun similarArtists(base: ArtistDetails): List<DiscoverItem> = catalog.similarArtists(base)

    suspend fun artistPhotoFor(name: String, albumTitle: String): String? = catalog.artistPhotoFor(name, albumTitle)

    suspend fun artistHit(query: String): DiscoverItem? = catalog.searchArtistCard(query)

    suspend fun artistIdFor(item: DiscoverItem): Long? = item.artistId ?: catalog.findArtistId(item.title)

    suspend fun moreFeed(consumed: Set<String>, order: Int): MoreFeed = feedRepo.more(consumed, order)

    suspend fun album(item: DiscoverItem): AlbumDetails? {
        albumCache[item.id]?.let { return it }
        if (item.id.startsWith(FeedRepository.DEEZER_ALBUM_PREFIX)) return feedRepo.album(item)?.also { albumCache[item.id] = it }
        if (!item.id.startsWith("apple-album:")) return null
        return withTimeoutOrNull(ALBUM_TIMEOUT_MS) {
            safe {
                val rows = itunes.lookup(item.id.removePrefix("apple-album:")).body()?.results.orEmpty()
                val tracks = rows.filter { it.kind == "song" && it.trackName != null }.map {
                    Track(
                        isrc = "apple:${it.trackId}",
                        title = it.trackName.orEmpty(),
                        artist = it.artistName ?: item.subtitle,
                        album = item.title,
                        durationMs = it.trackTimeMillis ?: 0L,
                        albumArtUrl = item.imageUrl,
                        source = TrackSource.ITUNES,
                    )
                }
                if (tracks.isEmpty()) null else AlbumDetails(item.id, item.title, tracks.first().artist.ifBlank { item.subtitle }, item.imageUrl, tracks)
            }.getOrNull()
        }?.also { albumCache[item.id] = it }
    }

    private fun AppleChartItem.toDiscoverItem() = DiscoverItem(
        id = "apple-track:$id",
        title = name,
        subtitle = artistName,
        imageUrl = artworkUrl100.big(),
        track = toTrack(),
    )

    private fun AppleChartItem.toTrack() = Track(
        isrc = "apple:$id",
        title = name,
        artist = artistName,
        album = null,
        durationMs = 0L,
        albumArtUrl = artworkUrl100.big(),
        source = TrackSource.ITUNES,
    )

    private fun AppleChartItem.toDiscoverAlbum() = DiscoverItem(
        id = "apple-album:$id",
        title = name,
        subtitle = artistName,
        imageUrl = artworkUrl100.big(),
    )

    private companion object {
        const val TAG = "DiscoverRepo"
        const val CHART_TIMEOUT_MS = 10_000L
        const val ARTIST_IMAGES_TIMEOUT_MS = 20_000L
        const val ALBUM_TIMEOUT_MS = 10_000L
        const val FEED_TIMEOUT_MS = 25_000L
        const val BILLBOARD_RESOLVE_TIMEOUT_MS = 45_000L
        const val BILLBOARD_TAB_LIMIT = 100
        const val PUSH_THROTTLE_MS = 500L
        const val BLEND_WEIGHT = 0.5
        const val ALBUM_BOOST_CAP = 30.0
    }
}
internal fun releasedAgo(releaseDate: String?): String? {
    val date = runCatching { LocalDate.parse(releaseDate.orEmpty().take(10)) }.getOrNull() ?: return null
    val days = java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now())
    return when {
        days < 0 -> null
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        days < 7 -> "$days days ago"
        days < 14 -> "1 week ago"
        days < 31 -> "${days / 7} weeks ago"
        else -> date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) + " " + date.year
    }
}
