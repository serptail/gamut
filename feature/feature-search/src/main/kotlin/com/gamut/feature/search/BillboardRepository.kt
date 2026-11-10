package com.gamut.feature.search

import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import com.gamut.core.network.BillboardApi
import com.gamut.core.network.DeezerApi
import com.gamut.core.network.DeezerTrackDto
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class BillboardEntry(
    val rank: Int,
    val title: String,
    val artist: String,
    val lastWeek: Int?,
    val peak: Int,
    val weeks: Int,
) {
    val titleKey: String = titleKey(title)
    val artistKey: String = artistKey(artist)
    val key: String = "$titleKey|$artistKey"
}

private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
private val BRACKETED = Regex("\\s*[(\\[].*?[)\\]]")
private val FEAT_TAIL = Regex("(?i)\\s+(feat\\.?|ft\\.?|featuring)\\s+.*")
private val ARTIST_SPLIT = Regex("(?i)\\s+(featuring|feat\\.?|ft\\.?|with|x|&|and)\\s+|,|/")
private val BAD_VERSION = Regex("remix|karaoke|instrumental|live|cover")

private val DIACRITICS = Regex("\\p{M}+")

private fun norm(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(DIACRITICS, "")
    .lowercase().replace(NON_ALNUM, " ").trim()

internal fun titleKey(title: String): String = norm(title.replace(BRACKETED, "").replace(FEAT_TAIL, "").substringBefore(" - "))

internal fun artistKey(artist: String): String = norm(artist.split(ARTIST_SPLIT).first())

internal fun chartKey(title: String, artist: String) = "${titleKey(title)}|${artistKey(artist)}"

internal fun BillboardEntry.placeholderTrack() = Track(
    isrc = "billboard:$key", musicBrainzId = null, title = title, artist = artist, album = null,
    durationMs = 0L, albumArtUrl = null, source = TrackSource.ITUNES, youtubeVideoId = null,
)

internal fun BillboardEntry.toDiscoverItem(resolved: Track?): DiscoverItem {
    val track = resolved ?: placeholderTrack()
    return DiscoverItem(
        id = "billboard:$key",
        title = title,
        subtitle = artist,
        imageUrl = track.albumArtUrl.orEmpty(),
        track = track,
        meta = "Peak #$peak \u00B7 $weeks ${if (weeks == 1) "wk" else "wks"}",
        trend = when {
            lastWeek == null -> Trend.NEW
            lastWeek > rank -> Trend.UP
            lastWeek < rank -> Trend.DOWN
            else -> Trend.SAME
        },
        trendDelta = lastWeek?.let { kotlin.math.abs(it - rank) } ?: 0,
    )
}

@Singleton
class BillboardRepository @Inject constructor(
    private val api: BillboardApi,
    private val deezer: DeezerApi,
) {
    private data class Snapshot(val entries: List<BillboardEntry>, val atMs: Long)

    @Volatile private var snapshot: Snapshot? = null
    private val resolvedCache = ConcurrentHashMap<String, Track>()

    fun clearCache() {
        snapshot = null
        resolvedCache.clear()
    }

    suspend fun chart(): List<BillboardEntry> {
        snapshot?.takeIf { System.currentTimeMillis() - it.atMs < CHART_TTL_MS }?.let { return it.entries }
        return try {
            val entries = api.recent().data.map {
                BillboardEntry(it.thisWeek, it.song, it.artist, it.lastWeek, it.peak, it.weeks)
            }.sortedBy { it.rank }
            if (entries.isNotEmpty()) snapshot = Snapshot(entries, System.currentTimeMillis())
            entries.ifEmpty { snapshot?.entries.orEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            snapshot?.entries.orEmpty()
        }
    }

    suspend fun resolve(entries: List<BillboardEntry>, onResolved: suspend (BillboardEntry, Track) -> Unit) = coroutineScope {
        val gate = Semaphore(4)
        entries.forEach { entry ->
            launch {
                var track = resolvedCache[entry.key]
                if (track == null) {
                    track = gate.withPermit { lookup(entry) }
                    if (track != null) resolvedCache[entry.key] = track
                }
                if (track != null) onResolved(entry, track)
            }
        }
    }

    private suspend fun lookup(e: BillboardEntry): Track? = try {
        val lead = e.artist.split(ARTIST_SPLIT).first().replace("\"", "")
        val title = e.title.replace(BRACKETED, "").replace("\"", "").trim()
        pick(deezer.searchTracks("$lead $title", limit = 10).data, e)
            ?: pick(deezer.searchTracks("artist:\"$lead\" track:\"$title\"", limit = 5).data, e)
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        null
    }

    private fun pick(results: List<DeezerTrackDto>, e: BillboardEntry): Track? {
        val wantsVersion = BAD_VERSION.containsMatchIn(e.title.lowercase())
        return results
            .filter { titleKey(it.titleShort ?: it.title) == e.titleKey && artistKey(it.artist.name) == e.artistKey }
            .filter { wantsVersion || !BAD_VERSION.containsMatchIn(it.title.lowercase()) }
            .maxByOrNull { it.rank }
            ?.toTrack()
    }

    private fun DeezerTrackDto.toTrack() = Track(
        isrc = "deezer:$id",
        title = listOfNotNull(titleShort ?: title, titleVersion?.takeIf { it.isNotBlank() }).joinToString(" "),
        artist = artist.name,
        album = album.title,
        durationMs = duration * 1_000L,
        albumArtUrl = album.coverXl ?: album.coverBig,
        source = TrackSource.ITUNES,
    )

    private companion object {
        const val CHART_TTL_MS = 60 * 60 * 1_000L
    }
}
