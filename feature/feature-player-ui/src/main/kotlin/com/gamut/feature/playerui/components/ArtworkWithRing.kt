package com.gamut.feature.playerui.components

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Pause
import com.adamglin.phosphoricons.regular.Play
import com.gamut.core.designsystem.ArtPlaceholder
import com.gamut.core.designsystem.LocalFocusLive
import com.gamut.core.designsystem.focusRing
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

private val CoverShape = RoundedCornerShape(12.dp)
private val CoverCornerRadius = 12.dp
private val MaxCoverSize = 360.dp
private val RingGap = 3.5.dp
private val RingStrokeWidth = 1.5.dp
private val RingOutlineWidth = 0.5.dp
private val RingOutlineColor = Color(0xFF8C8C8C)
private const val PausedCoverScale = 0.96f

private val WaveBarWidth = 2.2.dp
private val WaveBarPitch = 3.dp
private val WaveStartOffset = 2.5.dp
private val WaveMinLength = 1.dp
private val WaveMaxLength = 10.dp
private val ScrubKnobRadius = 4.dp
private val ScrubDashHalfLength = 3.dp
private val ScrubDashWidth = 2.dp
private val RingClearance = 36.dp
private val RingInset = RingClearance / 2

@Composable
internal fun ArtworkWithRing(
    modifier: Modifier = Modifier,
    url: String?,
    progress: () -> Float,
    isPlaying: Boolean,
    enabled: Boolean,
    loading: Boolean = false,
    waveform: FloatArray,
    onTogglePlay: () -> Unit,
    onScrub: (Float?) -> Unit,
    onSeekFraction: (Float) -> Unit,
    seekStep: Float = 0.04f,
) {
    val live = LocalFocusLive.current
    val shapedWaveform = remember(waveform) { shapeWaveform(waveform) }
    val shapedState = rememberUpdatedState(shapedWaveform)
    var tapTrigger by remember { mutableIntStateOf(0) }
    var iconVisible by remember { mutableStateOf(true) }
    var ringSize by remember { mutableStateOf(IntSize.Zero) }
    var scrubbing by remember { mutableStateOf(false) }
    val scrub by animateFloatAsState(if (scrubbing) 1f else 0f, tween(180), label = "scrub")

    val currentToggle by rememberUpdatedState(onTogglePlay)
    val currentSeek by rememberUpdatedState(onSeekFraction)
    val currentScrub by rememberUpdatedState(onScrub)
    val haptics = LocalHapticFeedback.current

    val coverScale by animateFloatAsState(
        targetValue = if (enabled && !isPlaying) PausedCoverScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "cover-scale",
    )

    LaunchedEffect(isPlaying, tapTrigger) {
        iconVisible = true
        if (isPlaying) {
            delay(1200.milliseconds)
            iconVisible = false
        }
    }
    val iconAlpha by animateFloatAsState(if (iconVisible) 1f else 0f, animationSpec = tween(220), label = "icon-alpha")

    val candidates = remember(url) { coverArtCandidates(url) }
    val isThumb = remember(url) { url?.let(::youtubeThumbCandidates) != null }
    var attempt by remember(candidates) { mutableIntStateOf(0) }
    val shownUrl = candidates.getOrNull(attempt)
    val placeholderUrl = if (isThumb) null else url
    var loadedAspect by remember(shownUrl) { mutableStateOf<Float?>(null) }
    val targetAspect = if (enabled && isThumb) (loadedAspect ?: (16f / 9f)).coerceIn(1f, 2f) else 1f
    val aspect by animateFloatAsState(targetAspect, tween(250), label = "cover-aspect")

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = MaxCoverSize + RingClearance)
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val w = constraints.maxWidth
                    val clearance = RingClearance.roundToPx()
                    val h = ((w - clearance) / aspect).roundToInt() + clearance
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    scaleX = coverScale
                    scaleY = coverScale
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .padding(RingInset)
                    .fillMaxSize()
                    .shadow(elevation = 14.dp, shape = CoverShape, ambientColor = Color.Black, spotColor = Color.Black, clip = false)
                    .clip(CoverShape),
                contentAlignment = Alignment.Center,
            ) {
                if (enabled) {
                    if (shownUrl == null) {
                        CoverPlaceholder()
                    } else {
                        if (isThumb) {
                            AsyncImage(
                                model = shownUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = 1.3f
                                        scaleY = 1.3f
                                    }
                                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(28.dp) else Modifier),
                            )
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                        }
                        SubcomposeAsyncImage(
                            model = shownUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = if (isThumb) ContentScale.Fit else ContentScale.Crop,
                            loading = {
                                if (placeholderUrl != null && placeholderUrl != shownUrl) {
                                    AsyncImage(
                                        model = placeholderUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize())
                                }
                            },
                            error = { CoverPlaceholder() },
                            onError = { if (attempt < candidates.size) attempt++ },
                            onSuccess = { state ->
                                val size = state.painter.intrinsicSize
                                if (size.width > 0f && size.height > 0f) loadedAspect = size.width / size.height
                            },
                        )
                    }
                }
                if (enabled) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .graphicsLayer { alpha = if (loading) 1f else iconAlpha },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (loading) {
                            PlayerSpinner(Modifier.size(40.dp), Color.White, 3.dp)
                        } else {
                            DepthIcon(
                                if (isPlaying) PhosphorIcons.Regular.Pause else PhosphorIcons.Regular.Play,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                size = 44.dp,
                            )
                        }
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { ringSize = it }
                    .focusRing(RoundedCornerShape(CoverCornerRadius))
                    .onKeyEvent { e ->
                        if (!enabled || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                                currentToggle()
                                tapTrigger++
                                true
                            }

                            Key.MediaFastForward, Key.MediaStepForward -> {
                                currentSeek((progress() + seekStep).coerceIn(0f, 1f))
                                true
                            }

                            Key.MediaRewind, Key.MediaStepBackward -> {
                                currentSeek((progress() - seekStep).coerceIn(0f, 1f))
                                true
                            }

                            else -> false
                        }
                    }
                    .focusProperties { if (!live) canFocus = false }
                    .focusable(enabled)
                    .pointerInput(enabled, ringSize) {
                        if (!enabled || ringSize.width == 0) return@pointerInput
                        val marginPx = RingInset.toPx()
                        val coverW = ringSize.width - RingClearance.toPx()
                        val coverH = ringSize.height - RingClearance.toPx()
                        val gapPx = RingGap.toPx() + RingStrokeWidth.toPx() / 2f
                        val path = outerRingPath(coverW, coverH, marginPx, gapPx, CoverCornerRadius.toPx())
                        val measure = PathMeasure().apply { setPath(path, false) }
                        val total = measure.length
                        val samples = 300
                        val pts = List(samples) { measure.getPosition(total * it / samples) }

                        val coverInner = Rect(marginPx, marginPx, marginPx + coverW, marginPx + coverH)
                            .deflate(10.dp.toPx())
                        val hitRadius = 16.dp.toPx()
                        fun nearLine(p: Offset) = !coverInner.contains(p) && pts.minOf { hypot(it.x - p.x, it.y - p.y) } < hitRadius
                        fun fractionAt(p: Offset): Float {
                            var best = 0
                            var bestD = Float.MAX_VALUE
                            pts.forEachIndexed { i, o ->
                                val d = hypot(o.x - p.x, o.y - p.y)
                                if (d < bestD) {
                                    bestD = d
                                    best = i
                                }
                            }
                            return (best.toFloat() / samples).coerceIn(0f, 1f)
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (nearLine(down.position)) {
                                down.consume()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                scrubbing = true
                                var last = fractionAt(down.position)
                                try {
                                    currentScrub(last)
                                    val released = drag(down.id) { change ->
                                        change.consume()
                                        val f = fractionAt(change.position)
                                        last = if (abs(f - last) > 0.5f) (if (last > 0.5f) 1f else 0f) else f
                                        currentScrub(last)
                                    }
                                    if (released) currentSeek(last)
                                } finally {
                                    scrubbing = false
                                    currentScrub(null)
                                }
                            } else {
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentToggle()
                                    tapTrigger++
                                }
                            }
                        }
                    }
                    .drawWithCache {
                        if (!enabled) return@drawWithCache onDrawBehind {}
                        val marginPx = RingInset.toPx()
                        val coverW = size.width - RingClearance.toPx()
                        val coverH = size.height - RingClearance.toPx()
                        val gapPx = RingGap.toPx() + RingStrokeWidth.toPx() / 2f
                        val path = outerRingPath(coverW, coverH, marginPx, gapPx, CoverCornerRadius.toPx())
                        val measure = PathMeasure().apply { setPath(path, false) }
                        val total = measure.length
                        val strokePx = RingStrokeWidth.toPx()
                        val outlinePx = RingOutlineWidth.toPx()

                        val filled = Path()
                        val outlinePaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = strokePx + outlinePx * 2f
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            color = RingOutlineColor.toArgb()
                            setShadowLayer(3.dp.toPx(), 0f, 1.dp.toPx(), android.graphics.Color.argb(80, 0, 0, 0))
                        }
                        val linePaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = strokePx
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            color = android.graphics.Color.WHITE
                        }

                        val barW = WaveBarWidth.toPx()
                        val startOff = WaveStartOffset.toPx()
                        val minLen = WaveMinLength.toPx()
                        val maxLen = WaveMaxLength.toPx()
                        val barCount = (total / WaveBarPitch.toPx()).toInt().coerceAtLeast(1)
                        val barX = FloatArray(barCount)
                        val barY = FloatArray(barCount)
                        val barNx = FloatArray(barCount)
                        val barNy = FloatArray(barCount)
                        for (i in 0 until barCount) {
                            val dist = total * (i + 0.5f) / barCount
                            val pos = measure.getPosition(dist)
                            val tan = measure.getTangent(dist)
                            barX[i] = pos.x
                            barY[i] = pos.y
                            barNx[i] = tan.y
                            barNy[i] = -tan.x
                        }
                        val dashHalfLength = ScrubDashHalfLength.toPx()
                        val dashWidth = ScrubDashWidth.toPx()
                        val knobRadius = ScrubKnobRadius.toPx()
                        val startPoint = measure.getPosition(0f)
                        var ampsFor: FloatArray? = null
                        var amps = FloatArray(barCount)

                        onDrawBehind {
                            val fill = progress()
                            val reveal = scrub

                            drawPath(
                                path = path,
                                color = RingOutlineColor.copy(alpha = 0.45f),
                                style = Stroke(width = strokePx + outlinePx * 2f),
                            )
                            drawPath(
                                path = path,
                                color = Color.White.copy(alpha = 0.22f),
                                style = Stroke(width = strokePx),
                            )

                            if (fill > 0f) {
                                filled.rewind()
                                measure.getSegment(0f, total * fill, filled, true)
                                drawIntoCanvas { canvas ->
                                    val androidPath = filled.asAndroidPath()
                                    canvas.nativeCanvas.drawPath(androidPath, outlinePaint)
                                    canvas.nativeCanvas.drawPath(androidPath, linePaint)
                                }
                            }

                            if (reveal > 0.01f) {
                                val wave = shapedState.value
                                if (wave !== ampsFor) {
                                    amps = barHeights(wave, barCount)
                                    ampsFor = wave
                                }
                                for (i in 0 until barCount) {
                                    val f = (i + 0.5f) / barCount
                                    val amp = amps[i]
                                    val len = (minLen + (maxLen - minLen) * amp) * reveal
                                    drawLine(
                                        color = Color.White.copy(alpha = (if (f <= fill) 0.95f else 0.35f) * reveal),
                                        start = Offset(barX[i] + barNx[i] * startOff, barY[i] + barNy[i] * startOff),
                                        end = Offset(barX[i] + barNx[i] * (startOff + len), barY[i] + barNy[i] * (startOff + len)),
                                        strokeWidth = barW,
                                        cap = StrokeCap.Round,
                                    )
                                }

                                val half = dashHalfLength * reveal
                                drawLine(
                                    color = Color.White,
                                    start = Offset(startPoint.x, startPoint.y - half),
                                    end = Offset(startPoint.x, startPoint.y + half),
                                    strokeWidth = dashWidth,
                                    cap = StrokeCap.Round,
                                )

                                drawCircle(
                                    color = Color.White,
                                    radius = knobRadius * reveal,
                                    center = measure.getPosition(total * fill),
                                )
                            }
                        }
                    },
            )
        }
    }
}

private fun outerRingPath(coverW: Float, coverH: Float, marginPx: Float, gapPx: Float, coverRadiusPx: Float): Path {
    val left = marginPx - gapPx
    val top = marginPx - gapPx
    val right = marginPx + coverW + gapPx
    val bottom = marginPx + coverH + gapPx
    val r = coverRadiusPx + gapPx
    val d = r * 2f
    val cx = (left + right) / 2f
    return Path().apply {
        moveTo(cx, top)
        lineTo(right - r, top)
        arcTo(Rect(right - d, top, right, top + d), -90f, 90f, false)
        lineTo(right, bottom - r)
        arcTo(Rect(right - d, bottom - d, right, bottom), 0f, 90f, false)
        lineTo(left + r, bottom)
        arcTo(Rect(left, bottom - d, left + d, bottom), 90f, 90f, false)
        lineTo(left, top + r)
        arcTo(Rect(left, top, left + d, top + d), 180f, 90f, false)
        close()
    }
}

@Composable
private fun CoverPlaceholder() {
    ArtPlaceholder(
        tile = Color(0xFF232327),
        tint = Color(0xFF6B6B72),
        iconFraction = 0.28f,
        iconAlignment = BiasAlignment(0f, -0.45f),
    )
}
