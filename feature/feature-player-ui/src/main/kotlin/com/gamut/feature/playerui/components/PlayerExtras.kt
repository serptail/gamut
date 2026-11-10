package com.gamut.feature.playerui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.core.graphics.get
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Pause
import com.adamglin.phosphoricons.bold.Play
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PlayPauseButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 68.dp,
) {
    Box(
        modifier
            .size(size)
            .bounceTap(onClick = onClick, scaleDown = 0.92f)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            PlayerSpinner(Modifier.size(size * 0.36f), Color.Black, 2.5.dp)
        } else {
            Icon(
                if (isPlaying) PhosphorIcons.Bold.Pause else PhosphorIcons.Bold.Play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.Black,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

@Composable
internal fun SeekBar(
    progress: () -> Float,
    onScrub: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    val knob by animateFloatAsState(if (dragging) 1f else 0f, tween(120), label = "seek-knob")
    val currentScrub by rememberUpdatedState(onScrub)
    val currentSeek by rememberUpdatedState(onSeek)
    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val inset = 7.dp.toPx()
                    val usable = (size.width - inset * 2).coerceAtLeast(1f)
                    fun fractionAt(x: Float) = ((x - inset) / usable).coerceIn(0f, 1f)
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var last = fractionAt(down.position.x)
                    dragging = true
                    try {
                        currentScrub(last)
                        val released = drag(down.id) { change ->
                            change.consume()
                            last = fractionAt(change.position.x)
                            currentScrub(last)
                        }
                        if (released) currentSeek(last)
                    } finally {
                        dragging = false
                        currentScrub(null)
                    }
                }
            }
            .drawBehind {
                val inset = 7.dp.toPx()
                val usable = size.width - inset * 2
                val y = size.height / 2f
                val stroke = 4.dp.toPx()
                val p = progress().coerceIn(0f, 1f)
                drawLine(Color.White.copy(alpha = 0.28f), Offset(inset, y), Offset(inset + usable, y), stroke, StrokeCap.Round)
                if (p > 0f) drawLine(Color.White, Offset(inset, y), Offset(inset + usable * p, y), stroke, StrokeCap.Round)
                drawCircle(Color.White, radius = 5.dp.toPx() + 3.dp.toPx() * knob, center = Offset(inset + usable * p, y))
            },
    )
}

@Immutable
internal data class CoverTint(val top: Color, val bottom: Color) {
    companion object {
        val Neutral = CoverTint(Color(0xFF262626), Color(0xFF3C3C3C))
    }
}

private val tintCache = HashMap<String, CoverTint>()

@Composable
internal fun rememberCoverTint(url: String?): CoverTint {
    val context = LocalContext.current
    var tint by remember { mutableStateOf(url?.let { tintCache[it] } ?: CoverTint.Neutral) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            tint = CoverTint.Neutral
            return@LaunchedEffect
        }
        tintCache[url]?.let {
            tint = it
            return@LaunchedEffect
        }
        val found = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
                val result = context.imageLoader.execute(request)
                (result as? SuccessResult)?.drawable?.toBitmapOrNull()?.let(::tintFromBitmap)
            }.getOrNull()
        } ?: CoverTint.Neutral
        tintCache[url] = found
        tint = found
    }
    val top by animateColorAsState(tint.top, tween(600), label = "tint-top")
    val bottom by animateColorAsState(tint.bottom, tween(600), label = "tint-bottom")
    return CoverTint(top, bottom)
}

private fun tintFromBitmap(bitmap: Bitmap): CoverTint {
    val size = 32
    val sample = Bitmap.createScaledBitmap(bitmap, size, size, true)
    val hsv = FloatArray(3)
    var weight = 0f
    var hx = 0f
    var hy = 0f
    var sat = 0f
    for (x in 0 until size) {
        for (y in 0 until size) {
            android.graphics.Color.colorToHSV(sample[x, y], hsv)
            val s = hsv[1]
            val v = hsv[2]
            if (v < 0.15f || s < 0.15f) continue
            val w = s * s * v
            val rad = Math.toRadians(hsv[0].toDouble())
            hx += (cos(rad) * w).toFloat()
            hy += (sin(rad) * w).toFloat()
            sat += s * w
            weight += w
        }
    }
    if (weight < 20f) return CoverTint.Neutral
    var hue = Math.toDegrees(atan2(hy.toDouble(), hx.toDouble())).toFloat()
    if (hue < 0f) hue += 360f
    val s = (sat / weight).coerceIn(0.35f, 0.8f)
    fun hsvColor(h: Float, sv: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, sv.coerceIn(0f, 1f), v)))
    return CoverTint(top = hsvColor(hue, s, 0.38f), bottom = hsvColor(hue, s * 0.85f, 0.55f))
}
