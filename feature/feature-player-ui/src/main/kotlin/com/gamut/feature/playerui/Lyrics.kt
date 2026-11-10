package com.gamut.feature.playerui

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.CaretDown
import com.adamglin.phosphoricons.bold.MusicNote
import com.gamut.core.designsystem.AcrylicMaterials
import com.gamut.core.designsystem.AcrylicSurface
import com.gamut.core.model.Track
import com.gamut.feature.playerui.components.PlayerSpinner
import com.gamut.feature.playerui.components.fadeOutBetween
import com.gamut.feature.playerui.components.fadingEdges
import com.gamut.feature.playerui.components.tapTarget
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface LyricsState {
    @Immutable
    object Loading : LyricsState

    @Immutable
    data class Loaded(val lines: List<LyricsLine>, val isSynced: Boolean = true) : LyricsState

    @Immutable
    data class NotAvailable(val reason: String) : LyricsState

    @Immutable
    data class Error(val message: String) : LyricsState
}

@Immutable
data class LyricsWord(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

@Immutable
data class LyricsLine(
    val timestampMs: Long,
    val text: String,
    val endMs: Long = timestampMs,
    val words: List<LyricsWord> = emptyList(),
)

private class FetchedLyrics(val lines: List<LyricsLine>, val isSynced: Boolean)

object LyricsRepository {
    private const val BASE_URL = "https://lrclib.net/api"
    private const val USER_AGENT = "Gamut/1.0 (Android)"
    private const val TAG = "Lyrics"

    private const val MAX_DURATION_DIFF_S = 10.0

    private val lineStampsRegex = Regex("""^(?:\s*\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?\])+""")
    private val stampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
    private val wordStampRegex = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val whitespaceRegex = Regex("\\s+")

    private val bracketNoise = Regex(
        """\s*[(\[][^)\]]*\b(feat\.?|ft\.?|featuring|with|official|video|audio|lyrics?|visuali[sz]er|remaster(?:ed)?|live|explicit)\b[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val dashNoise = Regex("""\s+-\s+(?:\d{4}\s+)?(?:remaster(?:ed)?|live|single version|radio edit)\b.*$""", RegexOption.IGNORE_CASE)
    private val featuringNoise = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)

    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, LyricsState.Loaded>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsState.Loaded>?) = size > 40
        },
    )

    private fun cacheKey(track: Track) = track.isrc.orEmpty().ifBlank { "${track.title}|${track.artist}" }

    private val cachedSeconds = Collections.synchronizedMap(HashMap<String, Int>())

    fun cached(track: Track): LyricsState.Loaded? = cache[cacheKey(track)]

    suspend fun fetchLyrics(track: Track, durationMs: Long = track.durationMs): LyricsState {
        val key = cacheKey(track)
        val seconds = (durationMs / 1000).toInt()
        cache[key]?.let { hit -> if (hit.isSynced || cachedSeconds[key] == seconds) return hit }
        return try {
            val found = withContext(Dispatchers.IO) { lookup(track, durationMs) }
            if (found == null || found.lines.isEmpty()) {
                Log.i(TAG, "\"${track.title}\": nothing found (looked up with ${seconds}s)")
                LyricsState.NotAvailable("No lyrics found for this track")
            } else {
                Log.i(TAG, "\"${track.title}\": ${if (found.isSynced) "synced" else "PLAIN (not synced)"}, ${found.lines.size} lines (looked up with ${seconds}s)")
                LyricsState.Loaded(found.lines, found.isSynced).also {
                    cache[key] = it
                    cachedSeconds[key] = seconds
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            LyricsState.Error("Couldn't reach the lyrics service")
        } catch (e: Exception) {
            LyricsState.Error(e.message ?: "Failed to fetch lyrics")
        }
    }

    private val artistSplit = Regex("""\s*(?:,|;|/|&|\band\b|\bx\b|\bwith\b|\bfeat\.?|\bft\.?|\bfeaturing\b)\s*""", RegexOption.IGNORE_CASE)
    private fun primaryArtist(artist: String) = artist.split(artistSplit).firstOrNull { it.isNotBlank() }?.trim() ?: artist

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun looksLike(o: JSONObject, title: String, artist: String, seconds: Double): Boolean {
        val t = norm(o.optString("trackName", o.optString("name", "")))
        val a = norm(o.optString("artistName", ""))
        val wantT = norm(title)
        val wantA = norm(artist)
        val titleOk = t.isNotEmpty() && wantT.isNotEmpty() && (t == wantT || t.startsWith(wantT) || wantT.startsWith(t))
        val artistOk = a.isNotEmpty() && wantA.isNotEmpty() && (a.contains(wantA) || wantA.contains(a))
        val durationOk = seconds > 0 && o.has("duration") && !o.isNull("duration") && abs(o.optDouble("duration") - seconds) <= 3.0
        return titleOk && (artistOk || durationOk)
    }

    private fun lookup(track: Track, durationMs: Long): FetchedLyrics? {
        val title = cleanTitle(track.title)
        val coreTitle = title.substringBefore(" (").substringBefore(" - ").trim().ifBlank { title }
        val artist = track.artist.replace(featuringNoise, "").trim().ifBlank { track.artist }
        val lead = primaryArtist(track.artist)
        val seconds = durationMs / 1000.0
        var failed = false
        var offline = false
        fun get(url: String): String? {
            if (offline) return null
            return try {
                httpGetRetrying(url)
            } catch (e: UnknownHostException) {
                failed = true
                offline = true
                null
            } catch (e: IOException) {
                failed = true
                null
            }
        }

        var plainFallback: FetchedLyrics? = null
        fun synced(found: FetchedLyrics?): FetchedLyrics? {
            if (found == null) return null
            if (found.isSynced) return found
            if (plainFallback == null) plainFallback = found
            return null
        }

        if (seconds > 0) {
            for (a in listOf(artist, lead).distinct()) {
                val exact = get("$BASE_URL/get?track_name=${enc(title)}&artist_name=${enc(a)}&duration=${seconds.roundToInt()}")
                if (exact != null) runCatching { toLyrics(JSONObject(exact)) }.getOrNull()?.let { r -> synced(r)?.let { return it } }
            }
        }

        data class Q(val query: String, val strict: Boolean)
        val queries = listOf(
            Q("track_name=${enc(title)}&artist_name=${enc(artist)}", false),
            Q("track_name=${enc(title)}&artist_name=${enc(lead)}", false),
            Q("track_name=${enc(coreTitle)}&artist_name=${enc(lead)}", false),
            Q("q=${enc("$title $artist")}", true),
            Q("q=${enc("$coreTitle $lead")}", true),
            Q("q=${enc(coreTitle)}", true),
        ).distinctBy { it.query }
        for ((query, strict) in queries) {
            val body = get("$BASE_URL/search?$query") ?: continue
            val results = runCatching { JSONArray(body) }.getOrNull() ?: continue
            var items = List(results.length()) { results.getJSONObject(it) }
            if (strict) items = items.filter { looksLike(it, coreTitle, lead, seconds) }
            synced(pickBest(items, seconds))?.let { return it }
        }
        plainFallback?.let { return it }
        if (failed) throw IOException("lyrics lookup failed")
        return null
    }

    private fun pickBest(items: List<JSONObject>, seconds: Double): FetchedLyrics? {
        fun diff(o: JSONObject) = if (seconds > 0 && o.has("duration") && !o.isNull("duration")) abs(o.optDouble("duration") - seconds) else 0.0
        val synced = items.filter { hasText(it, "syncedLyrics") }.minByOrNull(::diff)
        if (synced != null && diff(synced) <= MAX_DURATION_DIFF_S) return toLyrics(synced)
        if (synced != null) Log.i(TAG, "synced version exists but its length is ${"%.1f".format(diff(synced))}s off the audio: not used")
        val plain = items.filter { hasText(it, "plainLyrics") }.minByOrNull(::diff)
        if (plain != null) return toLyrics(plain, preferPlain = true)
        return synced?.let { toLyrics(it) }
    }

    private fun hasText(o: JSONObject, key: String) = o.optString(key, "").let { it.isNotBlank() && it != "null" }

    private fun toLyrics(o: JSONObject, preferPlain: Boolean = false): FetchedLyrics? {
        val synced = o.optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" }
        val plain = o.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }
        if (synced != null && !preferPlain) {
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) return FetchedLyrics(lines, isSynced = true)
        }
        return plain?.let { FetchedLyrics(parsePlainLyrics(it), isSynced = false) }
    }

    private fun cleanTitle(title: String) = title.replace('\u2019', '\'').replace(bracketNoise, "").replace(dashNoise, "").trim().ifBlank { title }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    private fun httpGetRetrying(url: String): String? {
        var last: IOException? = null
        repeat(3) { attempt ->
            try {
                return httpGet(url)
            } catch (e: UnknownHostException) {
                throw e
            } catch (e: IOException) {
                last = e
                if (attempt < 2) Thread.sleep(500L * (attempt + 1))
            }
        }
        throw last ?: IOException("request failed")
    }

    private fun httpGet(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 6000
            connection.readTimeout = 8000
            return when (val code = connection.responseCode) {
                200 -> connection.inputStream.bufferedReader().use { it.readText() }
                404 -> null
                else -> throw IOException("HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun toMs(min: String, sec: String, frac: String): Long {
        val fracMs = frac.padEnd(3, '0').take(3).toLong()
        return (min.toLong() * 60 + sec.toLong()) * 1000 + fracMs
    }

    private fun parseLrc(lrcText: String): List<LyricsLine> {
        val raw = mutableListOf<Pair<Long, String>>()
        lrcText.lines().forEach { line ->
            val lead = lineStampsRegex.find(line) ?: return@forEach
            val body = line.substring(lead.range.last + 1).trim()
            if (body.replace(wordStampRegex, "").isBlank()) return@forEach
            stampRegex.findAll(lead.value).forEach { m ->
                raw.add(toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3]) to body)
            }
        }
        raw.sortBy { it.first }

        return raw.mapIndexed { i, (ts, body) ->
            val text = body.replace(wordStampRegex, "").replace(whitespaceRegex, " ").trim()
            val end = lineEnd(ts, text, raw.getOrNull(i + 1)?.first)
            val words = parseEnhancedWords(body, end) ?: estimateWords(text, ts, end)
            LyricsLine(timestampMs = ts, text = text, endMs = end, words = words)
        }
    }

    private fun lineEnd(start: Long, text: String, nextStart: Long?): Long {
        val estimate = 400L + text.length * 85L
        val gap = nextStart?.let { it - start }
        val duration = when {
            gap == null -> estimate
            gap <= estimate * 3 / 2 -> gap
            else -> estimate
        }
        return start + duration.coerceAtLeast(300L)
    }

    private fun parseEnhancedWords(body: String, lineEnd: Long): List<LyricsWord>? {
        val stamps = wordStampRegex.findAll(body).toList()
        if (stamps.isEmpty()) return null
        val words = mutableListOf<LyricsWord>()
        stamps.forEachIndexed { i, m ->
            val start = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            val next = stamps.getOrNull(i + 1)
            val end = next?.let { toMs(it.groupValues[1], it.groupValues[2], it.groupValues[3]) } ?: lineEnd
            val segment = body.substring(m.range.last + 1, next?.range?.first ?: body.length)
            when {
                segment.isBlank() -> {
                    if (words.isNotEmpty() && segment.isNotEmpty()) {
                        val last = words.removeAt(words.lastIndex)
                        words.add(last.copy(text = last.text.trimEnd() + " "))
                    }
                }

                else -> words.add(LyricsWord(start, end.coerceAtLeast(start + 1), segment.trimStart()))
            }
        }
        return words.ifEmpty { null }
    }

    private fun estimateWords(text: String, start: Long, end: Long): List<LyricsWord> {
        val parts = text.split(whitespaceRegex).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return emptyList()
        val weights = parts.map { maxOf(it.length, 2) }
        val totalWeight = weights.sum().toFloat()
        val span = (end - start).coerceAtLeast(1L)
        var cursor = start.toFloat()
        return parts.mapIndexed { i, part ->
            val wordStart = cursor.toLong()
            cursor += span * weights[i] / totalWeight
            val wordEnd = if (i == parts.lastIndex) end else cursor.toLong()
            LyricsWord(wordStart, wordEnd, if (i == parts.lastIndex) part else "$part ")
        }
    }

    private fun parsePlainLyrics(plainText: String): List<LyricsLine> = plainText.lines()
        .filter { it.isNotBlank() }
        .map { LyricsLine(timestampMs = 0L, text = it.trim()) }
}

private val LyricsTitleHeight = 36.dp
private val LyricsCollapsedHeight = 92.dp
private val PreviewPaddingV = 14.dp
private val PreviewLineGap = 8.dp
private const val PreviewFadeStart = 0.3f
private const val PreviewFadeEnd = 0.75f
private val LyricsCardShape = RoundedCornerShape(14.dp)
private val AcrylicBlur = 72.dp

@Composable
fun LyricsSection(
    lyricsState: LyricsState,
    positionMs: () -> Long,
    accent: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    BoxWithConstraints(modifier) {
        val fullHeight = maxHeight
        val expandedCardHeight = (fullHeight - LyricsTitleHeight).coerceAtLeast(0.dp)
        val collapsedCardHeight = minOf(LyricsCollapsedHeight, expandedCardHeight)
        val cardHeight by animateDpAsState(
            targetValue = if (expanded) expandedCardHeight else collapsedCardHeight,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "lyrics-height",
        )
        val chevron by animateFloatAsState(if (expanded) 0f else 180f, tween(250), label = "lyrics-chevron")

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(LyricsTitleHeight)
                    .tapTarget(onToggle)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Lyrics", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Icon(
                    PhosphorIcons.Bold.CaretDown,
                    contentDescription = if (expanded) "Hide lyrics" else "Show lyrics",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevron },
                )
            }

            AcrylicSurface(
                Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .shadow(
                        elevation = 14.dp,
                        shape = LyricsCardShape,
                        ambientColor = Color.Black.copy(alpha = 0.35f),
                        spotColor = Color.Black.copy(alpha = 0.45f),
                        clip = false,
                    )
                    .tapTarget { if (!expanded) onToggle() },
                shape = LyricsCardShape,
                material = AcrylicMaterials.UltraThin,
            ) {
                Crossfade(targetState = expanded, animationSpec = tween(250), label = "lyrics-content") { isExpanded ->
                    if (!isExpanded) {
                        LyricsPreview(lyricsState)
                    } else {
                        Box(Modifier.fillMaxSize()) {
                            when (lyricsState) {
                                is LyricsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    PlayerSpinner(Modifier.size(26.dp), Color.White.copy(alpha = 0.75f))
                                }

                                is LyricsState.Loaded -> {
                                    if (lyricsState.lines.isEmpty()) {
                                        LyricsEmptyState("No timed lyrics available")
                                    } else {
                                        LyricsList(
                                            lines = lyricsState.lines,
                                            isSynced = lyricsState.isSynced,
                                            positionMs = positionMs,
                                            accent = accent,
                                            viewport = fullHeight - LyricsTitleHeight,
                                        )
                                    }
                                }

                                is LyricsState.NotAvailable -> LyricsEmptyState("${lyricsState.reason}\nTap to retry", onRetry)

                                is LyricsState.Error -> LyricsEmptyState("${lyricsState.message}\nRetrying\u2026 tap to retry now", onRetry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsPreview(lyricsState: LyricsState) {
    val font = rememberLyricsFont()
    when (lyricsState) {
        is LyricsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PlayerSpinner(Modifier.size(22.dp), Color.White.copy(alpha = 0.75f))
        }

        is LyricsState.Loaded -> {
            if (lyricsState.lines.isEmpty()) {
                LyricsPreviewMessage("No timed lyrics available")
            } else {
                val style = TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                val density = LocalDensity.current
                var firstLinePx by remember { mutableIntStateOf(0) }
                var secondLinePx by remember { mutableIntStateOf(0) }
                val secondTop = {
                    with(density) { (PreviewPaddingV + PreviewLineGap).toPx() } + firstLinePx
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .fadeOutBetween(
                            start = { secondTop() + secondLinePx * PreviewFadeStart },
                            end = { secondTop() + secondLinePx * PreviewFadeEnd },
                            hasSecondLine = { secondLinePx > 0 },
                        ),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = PreviewPaddingV),
                        verticalArrangement = Arrangement.spacedBy(PreviewLineGap),
                    ) {
                        lyricsState.lines.take(2).forEachIndexed { i, line ->
                            Text(
                                text = line.text,
                                color = Color.White.copy(alpha = 0.6f),
                                style = style,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { if (i == 0) firstLinePx = it.height else secondLinePx = it.height },
                            )
                        }
                    }
                }
            }
        }

        is LyricsState.NotAvailable -> LyricsPreviewMessage(lyricsState.reason)

        is LyricsState.Error -> LyricsPreviewMessage("Error: ${lyricsState.message}")
    }
}

@Composable
private fun LyricsPreviewMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
internal fun LyricsEmptyState(message: String, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.fillMaxSize().let { if (onClick != null) it.tapTarget(onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                PhosphorIcons.Bold.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun rememberLyricsFont(): FontFamily {
    val context = LocalContext.current
    return remember {
        runCatching {
            val id = context.resources.getIdentifier("lyrics", "font", context.packageName)
            FontFamily(ResourcesCompat.getFont(context, id)!!)
        }.getOrDefault(FontFamily.SansSerif)
    }
}

private const val HighlightStepMs = 40L

@Composable
internal fun LyricsList(
    lines: List<LyricsLine>,
    isSynced: Boolean,
    positionMs: () -> Long,
    accent: Color,
    viewport: Dp,
) {
    val font = rememberLyricsFont()
    val listState = rememberLazyListState()

    val latestPosition by rememberUpdatedState(positionMs)
    val readPosition = remember { { latestPosition() } }

    val activeIndex by remember(lines, isSynced) {
        derivedStateOf { if (isSynced) activeLineIndex(lines, readPosition()) else -1 }
    }
    val dragged by listState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(lines) { listState.scrollToItem(activeIndex.coerceAtLeast(0)) }
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && !dragged) listState.animateScrollToItem(activeIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().fadingEdges(),
        contentPadding = PaddingValues(
            start = 22.dp,
            end = 22.dp,
            top = if (isSynced) viewport * 0.28f else 8.dp,
            bottom = if (isSynced) viewport * 0.60f else 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            LyricLine(
                line = line,
                isSynced = isSynced,
                isActive = index == activeIndex,
                isPast = index < activeIndex,
                readPosition = readPosition,
                accent = accent,
                font = font,
            )
        }
    }
}

@Composable
private fun LyricLine(
    line: LyricsLine,
    isSynced: Boolean,
    isActive: Boolean,
    isPast: Boolean,
    readPosition: () -> Long,
    accent: Color,
    font: FontFamily,
) {
    val style = TextStyle(
        fontFamily = font,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )

    if (!isSynced || line.words.isEmpty()) {
        Text(
            text = line.text,
            color = Color.White.copy(alpha = 0.85f),
            style = style,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    val lineAlpha by animateFloatAsState(
        targetValue = when {
            isActive -> 1f
            isPast -> 0.4f
            else -> 0.6f
        },
        animationSpec = tween(300),
        label = "lyric-line-alpha",
    )
    val lineScale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.96f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "lyric-line-scale",
    )
    val unsung = Color.White.copy(alpha = 0.6f)
    val step by remember(isActive, readPosition) {
        derivedStateOf { if (isActive) readPosition() / HighlightStepMs else 0L }
    }
    val now = when {
        isActive -> step * HighlightStepMs
        isPast -> Long.MAX_VALUE / 2
        else -> -1_000_000L
    }
    val text = buildAnnotatedString {
        line.words.forEach { word ->
            val span = (word.endMs - word.startMs).coerceAtLeast(1L)
            val p = ((now - word.startMs).toFloat() / span).coerceIn(0f, 1f)
            withStyle(SpanStyle(color = lerp(unsung, accent, p))) { append(word.text) }
        }
    }
    Text(
        text = text,
        style = style,
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            alpha = lineAlpha
            scaleX = lineScale
            scaleY = lineScale
        },
    )
}

internal fun activeLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
    var lo = 0
    var hi = lines.lastIndex
    var answer = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].timestampMs <= positionMs) {
            answer = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return answer
}
