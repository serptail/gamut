package com.gamut.feature.search

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.ArrowClockwise
import com.gamut.core.designsystem.ink
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
class SlingshotRefreshState internal constructor(
    private val scope: CoroutineScope,
    val thresholdPx: Float,
    private val maxPx: Float,
    private val enabled: () -> Boolean,
    private val onRefresh: () -> Unit,
) {
    var pull by mutableFloatStateOf(0f)
        private set
    var refreshing by mutableStateOf(false)
        private set

    private var anim: Job? = null
    private val holdPx get() = thresholdPx * 0.8f

    private fun springTo(target: Float) {
        anim?.cancel()
        anim = scope.launch {
            animate(
                initialValue = pull,
                targetValue = target,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            ) { v, _ -> pull = v }
        }
    }

    fun trigger() {
        if (refreshing) return
        refreshing = true
        onRefresh()
        springTo(holdPx)
        scope.launch {
            delay(MIN_SPIN_MS)
            refreshing = false
            springTo(0f)
        }
    }

    private fun release() {
        if (refreshing) {
            springTo(holdPx)
        } else if (pull >= thresholdPx) {
            trigger()
        } else {
            springTo(0f)
        }
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!enabled() || source != NestedScrollSource.UserInput || pull <= 0f || available.y >= 0f) return Offset.Zero
            anim?.cancel()
            val next = (pull + available.y).coerceAtLeast(0f)
            val used = next - pull
            pull = next
            return Offset(0f, used)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!enabled() || refreshing || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
            anim?.cancel()
            val resistance = (1f - pull / maxPx).coerceIn(0.12f, 1f) * 0.55f
            pull = (pull + available.y * resistance).coerceAtMost(maxPx)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (pull > 0f && enabled()) {
                release()
                return available
            }
            return Velocity.Zero
        }
    }

    private companion object {
        const val MIN_SPIN_MS = 1_100L
    }
}

@Composable
fun rememberSlingshotRefresh(enabled: Boolean, onRefresh: () -> Unit): SlingshotRefreshState {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val enabledNow by rememberUpdatedState(enabled)
    val refreshNow by rememberUpdatedState(onRefresh)
    return remember(density) {
        SlingshotRefreshState(
            scope = scope,
            thresholdPx = with(density) { 96.dp.toPx() },
            maxPx = with(density) { 190.dp.toPx() },
            enabled = { enabledNow },
            onRefresh = { refreshNow() },
        )
    }
}

@Composable
fun BoxScope.SlingshotIndicator(state: SlingshotRefreshState, topOffsetPx: Int = -1) {
    val progress = state.pull / state.thresholdPx
    if (state.pull < 1f && !state.refreshing) return
    val ready = progress >= 1f || state.refreshing
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(ready) { if (ready && !state.refreshing) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    val spin by rememberInfiniteTransition(label = "refresh-spin")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spin")
    val sizePx = with(LocalDensity.current) { 44.dp.toPx() }
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .then(if (topOffsetPx < 0) Modifier.statusBarsPadding() else Modifier)
            .offset { IntOffset(0, topOffsetPx.coerceAtLeast(0) + (state.pull.coerceAtLeast(0f) / 2f - sizePx / 2f).roundToInt()) }
            .graphicsLayer {
                val p = progress.coerceIn(0f, 1f)
                alpha = if (state.refreshing) 1f else p
                scaleX = 0.55f + 0.45f * p
                scaleY = 0.55f + 0.45f * p
            }
            .size(44.dp)
            .clip(CircleShape)
            .background(ink(if (ready) 0.22f else 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            PhosphorIcons.Bold.ArrowClockwise,
            "Refresh",
            tint = ink(),
            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = if (state.refreshing) spin else progress.coerceIn(0f, 1.2f) * 270f },
        )
    }
}
