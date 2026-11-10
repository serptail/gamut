package com.gamut.feature.library

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.ArrowCircleDown
import com.adamglin.phosphoricons.fill.CheckCircle
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale
import com.gamut.feature.resolver.DownloadState

private val FailureColor = Color(0xFFFF6B6B)

enum class PlaylistDownloadStatus { Idle, Active, Done }

data class PlaylistDownloadUi(
    val status: PlaylistDownloadStatus = PlaylistDownloadStatus.Idle,
    val progress: Float = 0f,
    val done: Int = 0,
    val total: Int = 0,
)

fun playlistDownloadUi(isrcs: List<String>, states: Map<String, DownloadState>): PlaylistDownloadUi {
    if (isrcs.isEmpty()) return PlaylistDownloadUi()
    var done = 0
    var busy = false
    var fraction = 0f
    isrcs.forEach { isrc ->
        when (val state = states[isrc]) {
            DownloadState.Done -> {
                done++
                fraction += 1f
            }

            is DownloadState.Active -> {
                busy = true
                fraction += state.progress
            }

            DownloadState.Queued -> busy = true

            else -> Unit
        }
    }
    val status = when {
        busy -> PlaylistDownloadStatus.Active
        done == isrcs.size -> PlaylistDownloadStatus.Done
        else -> PlaylistDownloadStatus.Idle
    }
    return PlaylistDownloadUi(status, fraction / isrcs.size, done, isrcs.size)
}

private val GlyphSize = 28.dp

@Composable
fun DownloadButton(
    ui: PlaylistDownloadUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = ink(0.85f),
    accent: Color = ink(),
    glyphSize: Dp = GlyphSize,
    idleIcon: ImageVector = PhosphorIcons.Bold.ArrowCircleDown,
    doneIcon: ImageVector = PhosphorIcons.Fill.CheckCircle,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.88f else 1f, tween(120), label = "downloadPress")
    var watched by remember { mutableStateOf(false) }
    LaunchedEffect(ui.status) { if (ui.status == PlaylistDownloadStatus.Active) watched = true }

    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = when (ui.status) {
                    PlaylistDownloadStatus.Idle -> "Download playlist"
                    PlaylistDownloadStatus.Active -> "Stop downloading"
                    PlaylistDownloadStatus.Done -> "Downloaded"
                },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(ui.status, modifier = Modifier.size(glyphSize), animationSpec = tween(220), label = "downloadStatus") { status ->
            when (status) {
                PlaylistDownloadStatus.Idle ->
                    Icon(idleIcon, "Download playlist", tint = tint, modifier = Modifier.size(glyphSize))

                PlaylistDownloadStatus.Active ->
                    ActiveRing(ui.progress.coerceAtLeast(0.04f), tint.copy(alpha = 0.2f), accent, glyphSize)

                PlaylistDownloadStatus.Done ->
                    CheckCirclePop(animate = watched, color = accent, size = glyphSize, icon = doneIcon)
            }
        }
    }
}

private enum class Kind { Idle, Queued, Active, Done, Failed }

@Composable
fun TrackDownloadButton(
    state: DownloadState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    glyph: Dp = 24.dp,
    tint: Color = ink(0.7f),
    accent: Color = ink(),
) {
    var watched by remember { mutableStateOf(false) }
    LaunchedEffect(state is DownloadState.Active) { if (state is DownloadState.Active) watched = true }
    val kind = when (state) {
        null -> Kind.Idle
        DownloadState.Queued -> Kind.Queued
        is DownloadState.Active -> Kind.Active
        DownloadState.Done -> Kind.Done
        is DownloadState.Failed -> Kind.Failed
    }
    Box(
        modifier
            .size(size)
            .pressScale(
                onClick = when (kind) {
                    Kind.Idle, Kind.Failed -> onDownload
                    Kind.Queued, Kind.Active -> onCancel
                    Kind.Done -> onDone
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(kind, modifier = Modifier.size(glyph), animationSpec = tween(180), label = "trackDownload") { shown ->
            when (shown) {
                Kind.Idle -> Icon(PhosphorIcons.Bold.ArrowCircleDown, "Download", tint = tint, modifier = Modifier.size(glyph))
                Kind.Failed -> Icon(PhosphorIcons.Bold.ArrowCircleDown, "Download failed, tap to retry", tint = FailureColor, modifier = Modifier.size(glyph))
                Kind.Queued -> SpinnerArc(tint, Modifier.size(glyph), 2.dp)
                Kind.Active -> ActiveRing(((state as? DownloadState.Active)?.progress ?: 0f).coerceAtLeast(0.05f), tint.copy(alpha = 0.25f), accent, glyph)
                Kind.Done -> CheckCirclePop(animate = watched, color = accent, size = glyph)
            }
        }
    }
}

@Composable
fun DownloadProgressLine(
    state: DownloadState?,
    modifier: Modifier = Modifier,
    color: Color = ink(),
) {
    val target = when (state) {
        is DownloadState.Active -> state.progress.coerceIn(0f, 1f)
        DownloadState.Done -> 1f
        else -> 0f
    }
    val progress by animateFloatAsState(target, tween(240, easing = LinearEasing), label = "lineProgress")
    val alpha by animateFloatAsState(
        if (state is DownloadState.Active) 1f else 0f,
        tween(if (state == DownloadState.Done) 700 else 120),
        label = "lineAlpha",
    )
    if (alpha > 0.01f) {
        Box(
            modifier
                .fillMaxWidth()
                .height(2.dp)
                .graphicsLayer { this.alpha = alpha }
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(color))
        }
    }
}

@Composable
private fun CheckCirclePop(animate: Boolean, color: Color, size: Dp, icon: ImageVector = PhosphorIcons.Fill.CheckCircle) {
    val scale = remember { Animatable(if (animate) 0.35f else 1f) }
    LaunchedEffect(animate) {
        if (animate) scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
    }
    Icon(
        icon,
        "Downloaded",
        tint = color,
        modifier = Modifier.size(size).graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}

@Composable
private fun ActiveRing(progress: Float, track: Color, color: Color, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        ProgressRing(progress, track, color, Modifier.size(size - 2.dp), 2.5.dp)
        Box(Modifier.size(size * 0.3f).clip(RoundedCornerShape(2.dp)).background(color))
    }
}

@Composable
private fun ProgressRing(progress: Float, track: Color, color: Color, modifier: Modifier, stroke: Dp) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(250), label = "ring")
    Canvas(modifier) {
        val w = stroke.toPx()
        val box = Size(size.width - w, size.height - w)
        val corner = Offset(w / 2f, w / 2f)
        drawArc(track, 0f, 360f, false, corner, box, style = Stroke(w))
        drawArc(color, -90f, 360f * shown, false, corner, box, style = Stroke(w, cap = StrokeCap.Round))
    }
}

@Composable
private fun SpinnerArc(color: Color, modifier: Modifier, stroke: Dp) {
    val transition = rememberInfiniteTransition(label = "queued")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "queuedAngle")
    Canvas(modifier) {
        val w = stroke.toPx()
        val box = Size(size.width - w, size.height - w)
        val corner = Offset(w / 2f, w / 2f)
        drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, corner, box, style = Stroke(w))
        drawArc(color, angle, 90f, false, corner, box, style = Stroke(w, cap = StrokeCap.Round))
    }
}

@Composable
fun AvailableOfflineLine(modifier: Modifier = Modifier, color: Color = ink(0.6f)) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(PhosphorIcons.Fill.CheckCircle, null, tint = color, modifier = Modifier.size(13.dp))
        Text(
            "Available offline",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}
