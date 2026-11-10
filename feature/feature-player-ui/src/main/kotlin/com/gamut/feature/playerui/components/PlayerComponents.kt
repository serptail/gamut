package com.gamut.feature.playerui.components

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.core.graphics.get
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.MusicNote
import com.gamut.core.designsystem.focusRing
import com.gamut.core.designsystem.squareArtUrl
import com.gamut.core.model.Track
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ArtworkPlaceholder() {
    Box(
        Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(PhosphorIcons.Bold.MusicNote, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
    }
}

internal fun formatTime(milliseconds: Long): String = "%d:%02d".format(milliseconds / 60_000, (milliseconds / 1_000) % 60)

@Composable
internal fun TimeRow(position: () -> Long, durationMs: Long) {
    val elapsedSecond by remember(position) { derivedStateOf { position() / 1_000 } }
    val style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum", letterSpacing = 0.4.sp)
    val color = Color.White.copy(alpha = 0.6f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(elapsedSecond * 1_000), color = color, style = style)
        Text(formatTime(durationMs), color = color, style = style)
    }
}

@Composable
internal fun Modifier.tapTarget(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.focusRing().clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

internal fun Modifier.bounceTap(onClick: () -> Unit, scaleDown: Float = 0.88f): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> scaleDown
            hovered -> 1.08f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounce-tap",
    )
    this.scale(scale).hoverable(interaction).focusRing().clickable(interactionSource = interaction, indication = null) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
}

@Composable
internal fun DepthIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp,
) {
    Box(modifier.size(size)) {
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        Icon(
            imageVector,
            contentDescription = null,
            tint = Color.Black.copy(alpha = if (canBlur) 0.45f else 0.22f),
            modifier = Modifier
                .fillMaxSize()
                .offset(y = 1.5.dp)
                .then(if (canBlur) Modifier.blur(2.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
        )
        Icon(imageVector, contentDescription, tint = tint, modifier = Modifier.fillMaxSize())
    }
}

internal fun Modifier.fadeOutBetween(
    start: () -> Float,
    end: () -> Float,
    hasSecondLine: () -> Boolean,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (!hasSecondLine()) return@drawWithContent
        val s = start()
        val e = end()
        if (e <= s) return@drawWithContent
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Black,
                    0.5f to Color.Black.copy(alpha = 0.3f),
                    1f to Color.Transparent,
                ),
                startY = s,
                endY = e,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

internal fun Modifier.fadingEdges(fade: Float = 0.12f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                fade to Color.Black,
                1f - fade to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

private const val BounceSpeedDpPerSecond = 26f
private const val BounceRestStartMs = 2400L
private const val BounceRestEndMs = 1700L

@Composable
internal fun BouncingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var boxWidth by remember { mutableIntStateOf(0) }
    var textWidth by remember { mutableIntStateOf(0) }
    val overflowPx = (textWidth - boxWidth).coerceAtLeast(0)
    val offset = remember { Animatable(0f) }

    LaunchedEffect(text, overflowPx) {
        offset.snapTo(0f)
        if (overflowPx <= 0) return@LaunchedEffect
        val travelMs = (overflowPx / density.density / BounceSpeedDpPerSecond * 1000f).toInt().coerceAtLeast(400)
        while (true) {
            delay(BounceRestStartMs)
            offset.animateTo(overflowPx.toFloat(), tween(travelMs, easing = FastOutSlowInEasing))
            delay(BounceRestEndMs)
            offset.animateTo(0f, tween(travelMs, easing = FastOutSlowInEasing))
        }
    }

    Box(modifier.fillMaxWidth().clipToBounds().onSizeChanged { boxWidth = it.width }) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
            modifier = Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .onSizeChanged { textWidth = it.width }
                .graphicsLayer { translationX = -offset.value },
        )
    }
}

@Composable
internal fun LoadingVinyl(progress: Float?, modifier: Modifier = Modifier) {
    Box(modifier.size(104.dp), contentAlignment = Alignment.Center) {
        VinylDisc(Modifier.fillMaxSize())
        if (progress != null) {
            LoadingBar(progress, Modifier.align(Alignment.BottomCenter).offset(y = 29.dp).width(72.dp))
        }
    }
}

@Composable
private fun LoadingBar(progress: Float, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "loading-progress",
    )
    Box(
        modifier
            .height(3.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .semantics { contentDescription = "Loading" },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(Color.White.copy(alpha = 0.85f)))
    }
}

@Composable
internal fun PlayerSpinner(modifier: Modifier = Modifier, color: Color = Color.White, strokeWidth: Dp = 2.dp) {
    val transition = rememberInfiniteTransition(label = "player-spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1500, easing = LinearEasing)),
        label = "spinner-rotation",
    )
    val sweep by transition.animateFloat(
        initialValue = 40f,
        targetValue = 250f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "spinner-sweep",
    )
    Canvas(modifier.semantics { contentDescription = "Loading" }) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(color.copy(alpha = 0.14f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(color, rotation, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun VinylDisc(modifier: Modifier = Modifier) {
    val spin = rememberInfiniteTransition(label = "vinyl-spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2400, easing = LinearEasing)),
        label = "vinyl-angle",
    )
    val black = Color(0xFF050506)
    Canvas(
        modifier
            .graphicsLayer { rotationZ = angle },
    ) {
        val c = center
        val r = size.minDimension / 2f
        val labelR = r * 0.35f

        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFF16161A), black), center = c, radius = r),
            radius = r,
            center = c,
        )
        val groove = 0.6.dp.toPx()
        var g = labelR + r * 0.12f
        var i = 0
        while (g < r * 0.95f) {
            drawCircle(Color.White.copy(alpha = if (i % 3 == 0) 0.06f else 0.03f), radius = g, center = c, style = Stroke(groove))
            g += 2.5.dp.toPx()
            i++
        }
        drawCircle(
            brush = Brush.sweepGradient(
                0.00f to Color.Transparent,
                0.07f to Color.White.copy(alpha = 0.16f),
                0.15f to Color.Transparent,
                0.50f to Color.Transparent,
                0.57f to Color.White.copy(alpha = 0.16f),
                0.65f to Color.Transparent,
                1.00f to Color.Transparent,
                center = c,
            ),
            radius = r * 0.97f,
            center = c,
        )
        drawCircle(Color.White.copy(alpha = 0.05f), radius = r * 0.965f, center = c, style = Stroke(1.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.14f), radius = r - 0.5.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFF4F4F4), Color(0xFFDADADA)), center = c, radius = labelR),
            radius = labelR,
            center = c,
        )
        drawLine(Color.Black.copy(alpha = 0.18f), start = c, end = Offset(c.x - labelR * 0.96f, c.y), strokeWidth = 0.7.dp.toPx())
        drawCircle(black, radius = r * 0.03f, center = c)
    }
}

internal fun waveformAt(wave: FloatArray, index: Int, count: Int): Float {
    if (wave.isEmpty()) return 0f
    val from = (index.toLong() * wave.size / count).toInt().coerceIn(0, wave.lastIndex)
    val to = ((index + 1).toLong() * wave.size / count).toInt().coerceIn(from + 1, wave.size)
    var m = 0f
    for (k in from until to) if (wave[k] > m) m = wave[k]
    return m.coerceIn(0f, 1f)
}

internal fun barHeights(wave: FloatArray, count: Int): FloatArray {
    if (wave.isEmpty() || count <= 0) return FloatArray(maxOf(count, 0))
    var row = FloatArray(count) { waveformAt(wave, it, count) }
    if (count < 5) return row
    repeat(2) {
        val next = FloatArray(count)
        for (i in 0 until count) {
            val l = row[(i - 1).coerceAtLeast(0)]
            val r = row[(i + 1).coerceAtMost(count - 1)]
            next[i] = 0.25f * l + 0.5f * row[i] + 0.25f * r
        }
        row = next
    }
    val sorted = row.sortedArray()
    val lo = sorted[(count * 0.08f).toInt().coerceIn(0, count - 1)]
    val hi = sorted[(count * 0.97f).toInt().coerceIn(0, count - 1)]
    val range = (hi - lo).coerceAtLeast(1e-3f)
    return FloatArray(count) { ((row[it] - lo) / range).coerceIn(0f, 1f) }
}

object WaveformSource {
    private val urls = java.util.concurrent.ConcurrentHashMap<String, String>()

    internal fun keyOf(track: Track): String = track.isrc.orEmpty().ifBlank { "${track.title}|${track.artist}" }

    @Volatile internal var appContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    private val recent = java.util.ArrayDeque<String>()

    fun register(track: Track, audioUrl: String) {
        val key = keyOf(track)
        urls[key] = audioUrl
        val stale = synchronized(recent) {
            recent.remove(key)
            recent.addLast(key)
            buildList { while (recent.size > 2) add(recent.removeFirst()) }
        }
        stale.forEach { k -> waveformEntries.remove(k)?.job?.cancel() }
        waveformEntry(track)
    }

    @Volatile internal var provider: ((Track) -> String?)? = null

    internal fun urlFor(track: Track): String? = urls[keyOf(track)] ?: provider?.invoke(track)
}

private const val EnvelopeSamples = 480

private const val WaveformTag = "Waveform"

private const val WaveformUserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

private const val DownloadChunk = 1_048_576
private const val MaxDownload = 48L * 1_048_576

private val waveformCache = object : LinkedHashMap<String, FloatArray>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > 24
}

private class WaveformEntry {
    val data = mutableStateOf(FloatArray(0))

    @Volatile var job: Job? = null
}

private val NoWaveform = FloatArray(0)
private val waveformScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val waveformEntries = java.util.concurrent.ConcurrentHashMap<String, WaveformEntry>()

private fun waveformEntry(track: Track): WaveformEntry {
    val key = WaveformSource.keyOf(track)
    synchronized(waveformCache) { waveformCache[key] }?.let { cached ->
        return WaveformEntry().also { it.data.value = cached }
    }
    var created: WaveformEntry? = null
    val entry = waveformEntries.computeIfAbsent(key) { k ->
        WaveformEntry().also { e ->
            e.job = waveformScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                try {
                    delay(WaveformStartDelayMs)
                    val real = loadWaveform(track) { partial -> e.data.value = partial }
                    if (real != null && real.isNotEmpty()) e.data.value = real
                } finally {
                    waveformEntries.remove(k, e)
                }
            }
            created = e
        }
    }
    if (created === entry) entry.job?.start()
    return entry
}

private const val WaveformStartDelayMs = 400L

private val waveformGate = kotlinx.coroutines.sync.Semaphore(1)

private inline fun <T> lowPriority(block: () -> T): T {
    val old = android.os.Process.getThreadPriority(android.os.Process.myTid())
    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
    try {
        return block()
    } finally {
        android.os.Process.setThreadPriority(old)
    }
}

private class LocalAudio(val file: java.io.File, val temp: Boolean)

private fun asLocalAudio(source: String): LocalAudio? {
    fun existing(path: String?): java.io.File? = path?.let { java.io.File(it) }?.takeIf { it.isFile && it.length() > 0 }
    return when {
        source.startsWith("/") -> existing(source)?.let { LocalAudio(it, false) }

        source.startsWith("file:") -> existing(android.net.Uri.parse(source).path)?.let { LocalAudio(it, false) }

        source.startsWith("content:") -> {
            val ctx = WaveformSource.appContext ?: return null
            val tmp = java.io.File.createTempFile("waveform", ".audio")
            val ok = runCatching {
                ctx.contentResolver.openInputStream(android.net.Uri.parse(source))?.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
            }.isSuccess && tmp.length() > 0
            if (ok) {
                LocalAudio(tmp, true)
            } else {
                tmp.delete()
                null
            }
        }

        else -> null
    }
}

private suspend fun loadWaveform(track: Track, onPartial: (FloatArray) -> Unit): FloatArray? {
    val key = WaveformSource.keyOf(track)
    synchronized(waveformCache) { waveformCache[key] }?.let { return it }
    var url = WaveformSource.urlFor(track)
    var tries = 0
    while (url == null && tries < 40) {
        delay(500)
        url = WaveformSource.urlFor(track)
        tries++
    }
    val source = url ?: run {
        Log.w(WaveformTag, "No audio URL registered for \"${track.title}\" (played without WaveformSource.register?)")
        return null
    }
    waveformGate.acquire()
    val startedAt = System.currentTimeMillis()
    val result = try {
        withContext(Dispatchers.IO) {
            lowPriority {
                val local = asLocalAudio(source)
                if (local != null) {
                    try {
                        sampleEnvelope(local.file.path)
                    } finally {
                        if (local.temp) local.file.delete()
                    }
                } else {
                    val job = currentCoroutineContext()[Job]
                    val file = downloadAudio(source) { job?.isActive != false }
                    if (file == null) {
                        null
                    } else {
                        try {
                            sampleEnvelope(file.path)
                        } finally {
                            file.delete()
                        }
                    }
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.w(WaveformTag, "Decoding failed for \"${track.title}\"", e)
        null
    } finally {
        waveformGate.release()
    }
    if (result == null) {
        Log.w(WaveformTag, "No waveform for \"${track.title}\"")
    } else {
        Log.i(
            WaveformTag,
            "Waveform ready for \"${track.title}\" in ${System.currentTimeMillis() - startedAt} ms " +
                "(min ${result.min()}, max ${result.max()})",
        )
    }
    if (result != null) synchronized(waveformCache) { waveformCache[key] = result }
    return result
}

private const val DownloadParallel = 4

private fun fetchChunk(url: String, start: Long, chunked: Boolean): Pair<Int, ByteArray>? {
    val end = start + DownloadChunk - 1
    val connection = java.net.URL(if (chunked) "$url&range=$start-$end" else url).openConnection() as java.net.HttpURLConnection
    return try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", WaveformUserAgent)
        if (!chunked) connection.setRequestProperty("Range", "bytes=$start-$end")
        val code = connection.responseCode
        if (code == 416) return code to ByteArray(0)
        if (code !in 200..299) {
            Log.w(WaveformTag, "Download failed: HTTP $code")
            return null
        }
        code to connection.inputStream.use { it.readBytes() }
    } catch (e: java.io.IOException) {
        Log.w(WaveformTag, "Download failed", e)
        null
    } finally {
        connection.disconnect()
    }
}

private fun downloadAudio(url: String, stillActive: () -> Boolean): java.io.File? {
    val file = java.io.File.createTempFile("waveform", ".audio")
    var ok = false
    val pool = java.util.concurrent.Executors.newFixedThreadPool(DownloadParallel)
    try {
        val chunked = url.contains("googlevideo.com") && !url.contains("range=")
        file.outputStream().use { out ->
            val first = fetchChunk(url, 0L, chunked) ?: return null
            out.write(first.second)
            var start = first.second.size.toLong()
            var more = first.second.size >= DownloadChunk && (chunked || first.first == 206)
            while (more && start < MaxDownload) {
                if (!stillActive()) return null
                val base = start
                val wave = (0 until DownloadParallel).map { i ->
                    pool.submit<Pair<Int, ByteArray>?> { fetchChunk(url, base + i.toLong() * DownloadChunk, chunked) }
                }
                var done = false
                for (f in wave) {
                    if (done) {
                        f.cancel(true)
                        continue
                    }
                    val r = f.get() ?: return null
                    out.write(r.second)
                    start += r.second.size
                    if (r.first == 416 || r.second.size < DownloadChunk) done = true
                }
                more = !done
            }
        }
        ok = file.length() > 0
        if (!ok) Log.w(WaveformTag, "Download returned no data")
        return if (ok) file else null
    } finally {
        pool.shutdownNow()
        if (!ok) file.delete()
    }
}

private fun sampleEnvelope(path: String): FloatArray? {
    val context = WaveformSource.appContext
    if (context == null) {
        Log.w(WaveformTag, "WaveformSource.init(context) was never called")
        return null
    }
    val amps: List<Int> = com.linc.amplituda.Amplituda(context).processAudio(path).get()?.amplitudesAsList() ?: emptyList()
    val peak = amps.maxOrNull() ?: 0
    if (peak <= 0) {
        Log.w(WaveformTag, "Amplituda returned no audio (${amps.size} samples)")
        return null
    }
    val n = amps.size
    val levels = FloatArray(EnvelopeSamples) { b ->
        val from = (b.toLong() * n / EnvelopeSamples).toInt().coerceIn(0, n - 1)
        val to = ((b + 1).toLong() * n / EnvelopeSamples).toInt().coerceIn(from + 1, n)
        var sum = 0L
        for (k in from until to) sum += amps[k]
        sum.toFloat() / (to - from) / peak
    }
    return levelsToEnvelope(levels, EnvelopeSamples - 1)
}

private fun levelsToEnvelope(levels: FloatArray, upTo: Int): FloatArray {
    var carry = 0f
    return FloatArray(levels.size) { i ->
        if (i > upTo) {
            0f
        } else {
            if (levels[i] > 0f) carry = levels[i]
            val db = 20f * kotlin.math.log10(carry.coerceAtLeast(1e-5f))
            ((db + 55f) / 55f).coerceIn(0f, 1f)
        }
    }
}

internal fun shapeWaveform(wave: FloatArray): FloatArray {
    if (wave.isEmpty()) return wave
    val measured = wave.indexOfLast { it > 0f } + 1
    if (measured < 8) return FloatArray(wave.size)
    val r = 12
    val prefix = DoubleArray(measured + 1).also { for (i in 0 until measured) it[i + 1] = it[i] + wave[i] }
    val mixed = FloatArray(measured) { i ->
        val a = (i - r).coerceAtLeast(0)
        val b = (i + r + 1).coerceAtMost(measured)
        val local = ((prefix[b] - prefix[a]) / (b - a)).toFloat()
        0.5f * wave[i] + 1.0f * (wave[i] - local)
    }
    val sorted = mixed.sortedArray()
    val lo = sorted[(sorted.size * 0.05f).toInt().coerceIn(0, sorted.lastIndex)]
    val hi = sorted[(sorted.size * 0.97f).toInt().coerceIn(0, sorted.lastIndex)]
    val range = (hi - lo).coerceAtLeast(1e-3f)
    return FloatArray(wave.size) { if (it < measured) ((mixed[it] - lo) / range).coerceIn(0f, 1f).pow(1.3f) else 0f }
}

@Composable
internal fun rememberWaveform(track: Track?): FloatArray {
    val key = track?.let(WaveformSource::keyOf)
    var entry by remember(key) { mutableStateOf(track?.let { waveformEntry(it) }) }
    LaunchedEffect(key) {
        val t = track ?: return@LaunchedEffect
        repeat(4) { attempt ->
            delay(8_000L * (attempt + 1))
            val e = entry ?: return@LaunchedEffect
            if (e.data.value.isNotEmpty()) return@LaunchedEffect
            if (e.job?.isActive != true) entry = waveformEntry(t)
        }
    }
    return entry?.data?.value ?: NoWaveform
}

private val DefaultAccent = Color.White
private val accentCache = HashMap<String, Color>()

@Composable
internal fun rememberCoverAccent(url: String?): Color {
    val context = LocalContext.current
    var accent by remember { mutableStateOf(url?.let { accentCache[it] } ?: DefaultAccent) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            accent = DefaultAccent
            return@LaunchedEffect
        }
        accent = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
                val result = context.imageLoader.execute(request)
                val bitmap = (result as? SuccessResult)?.drawable?.toBitmapOrNull() ?: return@runCatching null
                accentFromBitmap(bitmap)
            }.getOrNull()
        } ?: DefaultAccent
        accentCache[url] = accent
    }
    val animated by animateColorAsState(accent, tween(600), label = "lyrics-accent")
    return animated
}

private fun accentFromBitmap(bitmap: Bitmap): Color? = accentsFromBitmap(bitmap).first

private fun subAccentFromBitmap(bitmap: Bitmap): Color? = accentsFromBitmap(bitmap).second

private fun accentsFromBitmap(bitmap: Bitmap): Pair<Color?, Color?> {
    val size = 40
    val sample = Bitmap.createScaledBitmap(bitmap, size, size, true)
    val bins = 12
    val weight = FloatArray(bins)
    val count = IntArray(bins)
    val sumH = FloatArray(bins)
    val sumS = FloatArray(bins)
    val hsv = FloatArray(3)
    for (x in 0 until size) {
        for (y in 0 until size) {
            android.graphics.Color.colorToHSV(sample[x, y], hsv)
            val s = hsv[1]
            val v = hsv[2]
            if (s < 0.28f || v < 0.30f) continue
            val w = s * v
            val bin = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
            weight[bin] += w
            count[bin]++
            sumH[bin] += hsv[0] * w
            sumS[bin] += s * w
        }
    }
    var best = 0
    for (i in 1 until bins) if (weight[i] > weight[best]) best = i
    if (count[best] < 12 || weight[best] <= 0f) return null to null
    val hue = sumH[best] / weight[best]
    val sat = (sumS[best] / weight[best]).coerceIn(0.5f, 0.85f)
    val main = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 0.96f)))

    return main to subAccentFor(bitmap)
}

private fun hueDistance(a: Float, b: Float): Float = kotlin.math.abs(a - b).let { if (it > 180f) 360f - it else it }

private fun subAccentFor(bitmap: Bitmap): Color? {
    val size = 64
    val sample = Bitmap.createScaledBitmap(bitmap, size, size, true)
    val fine = 36
    val binW = FloatArray(fine)
    val binN = IntArray(fine)
    val binS = FloatArray(fine)
    val binV = FloatArray(fine)
    val hsv = FloatArray(3)
    var total = 0f
    var pixels = 0
    for (x in 0 until size) {
        for (y in 0 until size) {
            android.graphics.Color.colorToHSV(sample[x, y], hsv)
            if (hsv[1] < 0.22f || hsv[2] < 0.30f) continue
            val w = hsv[1] * hsv[2]
            val bin = ((hsv[0] / 360f) * fine).toInt().coerceIn(0, fine - 1)
            binW[bin] += w
            binN[bin]++
            binS[bin] += hsv[1] * w
            binV[bin] += hsv[2] * w
            total += w
            pixels++
        }
    }
    if (pixels < 20 || total <= 0f) return null

    val claimed = BooleanArray(fine)
    class Family(val weight: Float, val count: Int, val hue: Float, val sat: Float, val value: Float)
    val families = ArrayList<Family>()
    for (seed in (0 until fine).sortedByDescending { binW[it] }) {
        if (binW[seed] <= 0f || claimed[seed]) continue
        val seedHue = seed * 10f + 5f
        var w = 0f
        var n = 0
        var s = 0f
        var v = 0f
        var cx = 0f
        var cy = 0f
        for (j in 0 until fine) {
            if (claimed[j] || binW[j] <= 0f || hueDistance(seedHue, j * 10f + 5f) > 30f) continue
            claimed[j] = true
            val rad = Math.toRadians((j * 10f + 5f).toDouble())
            w += binW[j]
            n += binN[j]
            s += binS[j]
            v += binV[j]
            cx += binW[j] * kotlin.math.cos(rad).toFloat()
            cy += binW[j] * kotlin.math.sin(rad).toFloat()
        }
        val hue = ((Math.toDegrees(kotlin.math.atan2(cy.toDouble(), cx.toDouble())).toFloat()) + 360f) % 360f
        families.add(Family(w, n, hue, s / w, v / w))
    }

    val dominant = families.maxByOrNull { it.weight } ?: return null
    var best: Family? = null
    var bestScore = 0f
    for (f in families) {
        if (f === dominant) continue
        val distance = hueDistance(f.hue, dominant.hue)
        if (distance < 35f) continue
        if (f.weight < total * 0.02f || f.count < pixels * 0.012f) continue
        val score = kotlin.math.sqrt(f.weight / total) * f.sat * (0.6f + 0.4f * f.value) * (minOf(distance, 110f) / 110f)
        if (score > bestScore) {
            bestScore = score
            best = f
        }
    }
    val pick = best ?: return null
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(pick.hue, (pick.sat * 1.15f).coerceIn(0.55f, 0.92f), 1f)))
}

private val subAccentCache = HashMap<String, Color?>()

@Composable
internal fun rememberCoverSubAccent(url: String?, fallback: Color): Color {
    val context = LocalContext.current
    var sub by remember { mutableStateOf<Color?>(url?.let { subAccentCache[it] }) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            sub = null
            return@LaunchedEffect
        }
        if (subAccentCache.containsKey(url)) {
            sub = subAccentCache[url]
            return@LaunchedEffect
        }
        sub = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
                val result = context.imageLoader.execute(request)
                val bitmap = (result as? SuccessResult)?.drawable?.toBitmapOrNull() ?: return@runCatching null
                subAccentFromBitmap(bitmap)
            }.getOrNull()
        }
        subAccentCache[url] = sub
    }
    val animated by animateColorAsState(sub ?: fallback, tween(600), label = "lyrics-sub-accent")
    return animated
}

internal fun coverArtCandidates(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    youtubeThumbCandidates(raw)?.let { return it + raw }
    return listOfNotNull(squareArtUrl(raw), raw).distinct()
}

internal fun backdropArtUrl(url: String?): String? = squareArtUrl(url, 200)

private val YouTubeThumb = Regex("""^(https?://[^/]*(?:ytimg\.com|img\.youtube\.com))/vi(?:_webp)?/([^/]+)/""")

internal fun youtubeThumbCandidates(url: String): List<String>? {
    val m = YouTubeThumb.find(url) ?: return null
    val base = m.groupValues[1]
    val id = m.groupValues[2]
    return listOf("hq720", "maxresdefault", "mqdefault").map { "$base/vi/$id/$it.jpg" }
}
