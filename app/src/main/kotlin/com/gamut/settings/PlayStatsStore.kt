package com.gamut.settings

import android.content.Context
import com.gamut.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class PlayedSong(
    val isrc: String,
    val title: String,
    val artist: String,
    val artUrl: String?,
    val album: String? = null,
    val plays: Int,
    val lastPlayedMs: Long,
)

data class PlayEvent(val isrc: String, val atMs: Long)

data class PlayStatsData(
    val songs: Map<String, PlayedSong> = emptyMap(),
    val events: List<PlayEvent> = emptyList(),
)

enum class StatsPeriod(val label: String, val days: Long?) {
    WEEK("7 days", 7),
    MONTH("30 days", 30),
    ALL("All time", null),
}

data class RankedSong(val song: PlayedSong, val plays: Int)
data class RankedArtist(val name: String, val plays: Int, val album: String? = null)

data class StatsSummary(
    val totalPlays: Int = 0,
    val uniqueSongs: Int = 0,
    val uniqueArtists: Int = 0,
    val topSongs: List<RankedSong> = emptyList(),
    val topArtists: List<RankedArtist> = emptyList(),
)

private const val DAY_MS = 24L * 60 * 60 * 1000

private class ArtistTally(val name: String, var plays: Int = 0, var album: String? = null)

private val KEEP_TOGETHER = listOf(
    "Simon & Garfunkel", "Tyler, The Creator", "Earth, Wind & Fire", "Hall & Oates", "Daryl Hall & John Oates",
    "Crosby, Stills, Nash & Young", "Crosby, Stills & Nash", "Emerson, Lake & Palmer", "Sly & The Family Stone",
    "Kool & The Gang", "Gladys Knight & The Pips", "Echo & The Bunnymen", "Bob Marley & The Wailers",
    "Tom Petty & The Heartbreakers", "Bruce Springsteen & The E Street Band", "Huey Lewis & The News",
    "Katrina & The Waves", "Florence & The Machine", "Marina & The Diamonds", "Diana Ross & The Supremes",
    "Smokey Robinson & The Miracles", "Martha Reeves & The Vandellas", "Ike & Tina Turner", "Brooks & Dunn",
    "Big & Rich", "Sam & Dave", "Years & Years", "Mumford & Sons", "Belle & Sebastian", "Matt & Kim",
    "Chase & Status", "Lil Nas X", "Malcolm X",
).sortedByDescending { it.length }

private val CREDIT_SPLIT = Regex("""\s*[,;]\s*|\s+(?:&|x|feat\.?|ft\.?|featuring|vs\.?)\s+""", RegexOption.IGNORE_CASE)
private val LEADING_FEAT = Regex("""^(?:feat\.?|ft\.?|featuring|vs\.?)\s+""", RegexOption.IGNORE_CASE)

internal fun splitArtists(raw: String): List<String> {
    var rest = raw.replace(Regex("""[()\[\]]"""), " ")
    val out = LinkedHashMap<String, String>()
    for (name in KEEP_TOGETHER) {
        val i = rest.indexOf(name, ignoreCase = true)
        if (i >= 0) {
            out.putIfAbsent(name.lowercase(), rest.substring(i, i + name.length))
            rest = rest.replaceRange(i, i + name.length, ",")
        }
    }
    rest.split(CREDIT_SPLIT)
        .map { it.trim().replace(LEADING_FEAT, "").trim() }
        .filter { it.isNotEmpty() }
        .forEach { out.putIfAbsent(it.lowercase(), it) }
    return out.values.toList().ifEmpty { listOfNotNull(raw.trim().takeIf { it.isNotEmpty() }) }
}

internal fun summarize(data: PlayStatsData, period: StatsPeriod, nowMs: Long): StatsSummary {
    val days = period.days
    val counts: Map<String, Int> =
        if (days == null) {
            data.songs.mapValues { it.value.plays }
        } else {
            val cutoff = nowMs - days * DAY_MS
            data.events.asSequence().filter { it.atMs >= cutoff }.groupingBy { it.isrc }.eachCount()
        }
    val ranked = counts
        .mapNotNull { (isrc, n) -> data.songs[isrc]?.let { RankedSong(it, n) } }
        .sortedWith(compareByDescending<RankedSong> { it.plays }.thenByDescending { it.song.lastPlayedMs })
    val tally = LinkedHashMap<String, ArtistTally>()
    for (r in ranked) {
        for (name in splitArtists(r.song.artist)) {
            val t = tally.getOrPut(name.lowercase()) { ArtistTally(name) }
            t.plays += r.plays
            if (t.album == null) t.album = r.song.album
        }
    }
    val artists = tally.values
        .map { RankedArtist(it.name, it.plays, it.album) }
        .sortedByDescending { it.plays }
    return StatsSummary(
        totalPlays = ranked.sumOf { it.plays },
        uniqueSongs = ranked.size,
        uniqueArtists = artists.size,
        topSongs = ranked.take(10),
        topArtists = artists.take(10),
    )
}

@Singleton
class PlayStatsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val _snapshot = MutableStateFlow(load())
    val snapshot: StateFlow<PlayStatsData> = _snapshot.asStateFlow()

    fun record(track: Track, atMs: Long = System.currentTimeMillis()) {
        if (track.isrc.isBlank()) return
        val changed = synchronized(lock) {
            val cur = _snapshot.value
            val last = cur.events.lastOrNull()
            if (last != null && last.isrc == track.isrc && atMs - last.atMs < DEDUPE_MS) return@synchronized false
            val prev = cur.songs[track.isrc]
            val song = PlayedSong(
                isrc = track.isrc,
                title = track.title,
                artist = track.artist,
                artUrl = track.albumArtUrl ?: prev?.artUrl,
                album = track.album ?: prev?.album,
                plays = (prev?.plays ?: 0) + 1,
                lastPlayedMs = atMs,
            )
            val events = (cur.events + PlayEvent(track.isrc, atMs)).let { if (it.size > MAX_EVENTS) it.takeLast(MAX_EVENTS) else it }
            _snapshot.value = PlayStatsData(cur.songs + (track.isrc to song), events)
            true
        }
        if (changed) persist()
    }

    fun clear() {
        synchronized(lock) { _snapshot.value = PlayStatsData() }
        persist()
    }

    private fun persist() {
        scope.launch {
            writeMutex.withLock {
                try {
                    prefs.edit().putString(KEY, encode(_snapshot.value)).apply()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun load(): PlayStatsData {
        val raw = prefs.getString(KEY, null) ?: return PlayStatsData()
        return try {
            val root = JSONObject(raw)
            val songs = HashMap<String, PlayedSong>()
            root.optJSONArray("songs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.getString("i")
                    songs[id] = PlayedSong(
                        isrc = id,
                        title = o.optString("t"),
                        artist = o.optString("a"),
                        artUrl = o.optString("u").takeIf { it.isNotBlank() },
                        album = o.optString("b").takeIf { it.isNotBlank() },
                        plays = o.optInt("p"),
                        lastPlayedMs = o.optLong("l"),
                    )
                }
            }
            val events = ArrayList<PlayEvent>()
            root.optJSONArray("events")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONArray(i)
                    events.add(PlayEvent(e.getString(0), e.getLong(1)))
                }
            }
            PlayStatsData(songs, events)
        } catch (_: Exception) {
            PlayStatsData()
        }
    }

    private fun encode(data: PlayStatsData): String {
        val songs = JSONArray()
        data.songs.values.forEach { s ->
            songs.put(
                JSONObject().put("i", s.isrc).put("t", s.title).put("a", s.artist)
                    .put("u", s.artUrl ?: "").put("b", s.album ?: "").put("p", s.plays).put("l", s.lastPlayedMs),
            )
        }
        val events = JSONArray()
        data.events.forEach { events.put(JSONArray().put(it.isrc).put(it.atMs)) }
        return JSONObject().put("songs", songs).put("events", events).toString()
    }

    private companion object {
        const val PREFS = "play_stats"
        const val KEY = "data_v1"
        const val DEDUPE_MS = 3_000L
        const val MAX_EVENTS = 5_000
    }
}
