package com.gamut.feature.playerui.components

import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.SpeakerHigh
import com.adamglin.phosphoricons.regular.SpeakerLow
import com.adamglin.phosphoricons.regular.SpeakerX
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Stable
internal class VolumeHud(private val audio: AudioManager, private val haptic: HapticFeedback) {
    private val maxSteps = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private var lastStep = -1

    var holding by mutableStateOf(false)
        private set
    var fraction by mutableFloatStateOf(0f)
        private set

    var anchor by mutableStateOf(Rect.Zero)
        internal set

    var headerBottom by mutableFloatStateOf(0f)
        internal set

    private fun current(): Float = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxSteps.toFloat()

    fun begin() {
        fraction = current()
        lastStep = (fraction * maxSteps).roundToInt()
        holding = true
    }

    fun set(value: Float) {
        fraction = value.coerceIn(0f, 1f)
        val step = (fraction * maxSteps).roundToInt()
        if (step != lastStep) {
            lastStep = step
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun end() {
        holding = false
    }

    fun fractionNow(): Float = fraction
}

internal fun Modifier.volumeHudAnchor(hud: VolumeHud): Modifier = onGloballyPositioned { hud.anchor = it.boundsInRoot() }

internal fun Modifier.volumeHudHeaderAnchor(hud: VolumeHud): Modifier = onGloballyPositioned { hud.headerBottom = it.boundsInRoot().bottom }

private val CoverArtInset = 18.dp

private val CoverSwipeInset = 28.dp

internal fun Modifier.swipeVolumeGesture(
    hud: VolumeHud,
    fullRange: Dp = 240.dp,
): Modifier = pointerInput(hud) {
    val rangePx = fullRange.toPx()
    val insetPx = CoverSwipeInset.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val zone = hud.anchor.deflate(insetPx)
        if (zone.width <= 0f || zone.height <= 0f || !zone.contains(down.position)) return@awaitEachGesture

        val slop = viewConfiguration.touchSlop
        var claimed = false
        var startX = down.position.x
        var startFraction = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val c = event.changes.firstOrNull { it.id == down.id } ?: break
            if (c.changedToUp() || !c.pressed) break
            if (!claimed) {
                if (c.isConsumed || event.changes.size > 1) break
                val dx = c.position.x - down.position.x
                val dy = c.position.y - down.position.y
                if (abs(dx) > slop) {
                    if (abs(dx) > abs(dy) * 1.5f) {
                        claimed = true
                        hud.begin()
                        startFraction = hud.fractionNow()
                        startX = c.position.x
                        c.consume()
                    } else {
                        break
                    }
                }
            } else {
                c.consume()
                hud.set(startFraction + (c.position.x - startX) / rangePx)
            }
        }
        if (claimed) hud.end()
    }
}

@Composable
internal fun VolumeHudOverlay(hud: VolumeHud, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(hud.holding) {
        if (hud.holding) {
            visible = true
        } else {
            delay(700)
            visible = false
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val pillHeightPx = with(density) { PillHeight.toPx() }
    val artTop = hud.anchor.top + with(density) { CoverArtInset.toPx() }
    val centerY = if (hud.headerBottom > 0f && hud.anchor.height > 0f) (hud.headerBottom + artTop) / 2f else hud.anchor.top
    val top = (centerY - origin.y - pillHeightPx / 2f - with(density) { PillLift.toPx() }).roundToInt()

    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(260)),
        ) {
            Box(Modifier.fillMaxSize()) {
                VolumePill(hud.fraction, Modifier.align(Alignment.TopCenter).offset { IntOffset(0, top) })
            }
        }
    }
}

private val PillWidth = 176.dp
private val PillHeight = 34.dp

private val PillLift = 9.dp

@Composable
private fun VolumePill(fraction: Float, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(60), label = "volume-fill")
    val icon = when {
        f <= 0f -> PhosphorIcons.Regular.SpeakerX
        f < 0.5f -> PhosphorIcons.Regular.SpeakerLow
        else -> PhosphorIcons.Regular.SpeakerHigh
    }
    val shape = RoundedCornerShape(PillHeight / 2)
    val iconTint = if (f > 0.15f) Color.Black.copy(alpha = 0.8f) else Color.White
    val numberTint = if (f > 0.88f) Color.Black.copy(alpha = 0.8f) else Color.White
    Box(
        modifier
            .width(PillWidth)
            .height(PillHeight)
            .shadow(14.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.42f)),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(f)
                .background(Color.White.copy(alpha = 0.95f)),
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(18.dp),
        )
        Text(
            "${(f * 100).roundToInt()}",
            color = numberTint,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
        )
    }
}
