package com.gamut.feature.playerui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.CaretDown
import com.adamglin.phosphoricons.bold.DotsThreeVertical
import com.adamglin.phosphoricons.bold.ShareNetwork
import com.adamglin.phosphoricons.bold.SkipBack
import com.adamglin.phosphoricons.bold.SkipForward
import com.gamut.feature.playerui.components.CoverTint
import com.gamut.feature.playerui.components.DepthIcon
import com.gamut.feature.playerui.components.PlayPauseButton
import com.gamut.feature.playerui.components.PlayerSpinner
import com.gamut.feature.playerui.components.SeekBar
import com.gamut.feature.playerui.components.TimeRow
import com.gamut.feature.playerui.components.bounceTap
import com.gamut.feature.playerui.components.fadingEdges
import com.gamut.feature.playerui.components.tapTarget
import kotlinx.coroutines.flow.collectLatest

private val PreviewShape = RoundedCornerShape(16.dp)

private fun vividLyricColor(base: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    if (hsv[1] < 0.12f) return Color.White
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], (hsv[1] * 1.5f).coerceIn(0.65f, 0.95f), 1f)))
}
private const val PanelStepMs = 40L

@Composable
internal fun CurrentLyricLine(
    lyricsState: LyricsState,
    activeIndex: () -> Int,
    positionMs: () -> Long,
    tint: CoverTint,
    highlight: Color? = null,
    modifier: Modifier = Modifier,
) {
    val loaded = lyricsState as? LyricsState.Loaded
    val show = loaded != null && loaded.isSynced && loaded.lines.isNotEmpty()
    val font = rememberLyricsFont()
    val sungColor = highlight ?: remember(tint.top) { vividLyricColor(tint.top) }
    val unsungColor = Color.White.copy(alpha = 0.5f)
    val latest by rememberUpdatedState(positionMs)
    val style = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 19.sp, lineHeight = 25.sp, textAlign = TextAlign.Center)
    AnimatedVisibility(
        visible = show,
        modifier = modifier,
        enter = fadeIn(tween(250)) + expandVertically(),
        exit = fadeOut(tween(150)) + shrinkVertically(),
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp).clipToBounds(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = activeIndex(),
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 3 }) togetherWith
                        (fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 3 })
                },
                label = "current-lyric",
            ) { index ->
                val line = loaded?.lines?.getOrNull(index)
                val step by remember(index) { derivedStateOf { latest() / PanelStepMs } }
                val text = if (line == null || line.words.isEmpty()) {
                    buildAnnotatedString { append(line?.text ?: "\u266A") }
                } else {
                    val now = step * PanelStepMs
                    buildAnnotatedString {
                        line.words.forEach { w ->
                            val span = (w.endMs - w.startMs).coerceAtLeast(1L)
                            val p = ((now - w.startMs).toFloat() / span).coerceIn(0f, 1f)
                            withStyle(SpanStyle(color = lerp(unsungColor, sungColor, p))) { append(w.text) }
                        }
                    }
                }
                Text(
                    text = text,
                    style = style,
                    color = if (line == null) Color.White.copy(alpha = 0.92f) else Color.Unspecified,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun LyricsPreviewCard(
    lyricsState: LyricsState,
    activeIndex: () -> Int,
    tint: CoverTint,
    onShowLyrics: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = rememberLyricsFont()
    val soft = lerp(Color.White, tint.bottom, 0.5f)
    Column(
        modifier
            .fillMaxWidth()
            .clip(PreviewShape)
            .background(Brush.linearGradient(listOf(tint.top, tint.bottom)))
            .tapTarget {
                if (lyricsState is LyricsState.Loaded) {
                    onShowLyrics()
                } else if (lyricsState !is LyricsState.Loading) {
                    onRetry()
                }
            }
            .padding(20.dp),
    ) {
        Text("Lyrics preview", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        when (lyricsState) {
            is LyricsState.Loading -> Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                PlayerSpinner(Modifier.size(26.dp), Color.White.copy(alpha = 0.8f))
            }

            is LyricsState.Loaded -> {
                val lines = lyricsState.lines
                val idx = if (lyricsState.isSynced) activeIndex() else -1
                if (!lyricsState.isSynced) {
                    Text("Not synced for this song", color = soft.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                }
                val start = if (idx <= 0) 0 else (idx - 1).coerceAtMost((lines.size - 4).coerceAtLeast(0))
                Box(Modifier.fillMaxWidth().height(236.dp).clipToBounds()) {
                    Crossfade(targetState = start, animationSpec = tween(300), label = "preview-window") { s ->
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            lines.drop(s).take(4).forEachIndexed { i, line ->
                                val active = s + i == idx
                                Text(
                                    text = line.text,
                                    color = if (active) Color.White else soft.copy(alpha = if (idx < 0) 0.9f else 0.7f),
                                    style = TextStyle(
                                        fontFamily = font,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = if (active) 24.sp else 20.sp,
                                        lineHeight = if (active) 30.sp else 26.sp,
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .tapTarget(onShowLyrics)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text("Show lyrics", color = Color.Black, style = MaterialTheme.typography.labelLarge)
                }
            }

            is LyricsState.NotAvailable -> PreviewMessage("No lyrics found for this track\nTap to try again", soft)

            is LyricsState.Error -> PreviewMessage("Couldn't load the lyrics\nRetrying\u2026 tap to retry now", soft)
        }
    }
}

@Composable
private fun PreviewMessage(text: String, color: Color) {
    Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
        Text(text, color = color.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun FullLyricsPanel(
    title: String,
    artist: String,
    lyricsState: LyricsState,
    positionMs: () -> Long,
    activeIndex: () -> Int,
    tint: CoverTint,
    isPlaying: Boolean,
    isLoading: Boolean,
    progress: () -> Float,
    labelPositionMs: () -> Long,
    durationMs: Long,
    onScrub: (Float?) -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val soft = lerp(Color.White, tint.bottom, 0.55f)
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(tint.top, tint.bottom)))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp)) {
                DepthIcon(
                    PhosphorIcons.Bold.CaretDown,
                    "Close lyrics",
                    Modifier.align(Alignment.CenterStart).tapTarget(onClose).padding(12.dp),
                )
                Column(Modifier.align(Alignment.Center).padding(horizontal = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (lyricsState is LyricsState.Loaded && !lyricsState.isSynced) {
                Text(
                    "These lyrics aren't synced for this song",
                    color = soft.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (lyricsState) {
                    is LyricsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PlayerSpinner(Modifier.size(30.dp), Color.White.copy(alpha = 0.8f))
                    }

                    is LyricsState.Loaded -> PanelLyricsList(
                        lines = lyricsState.lines,
                        isSynced = lyricsState.isSynced,
                        activeIndex = activeIndex,
                        positionMs = positionMs,
                        soft = soft,
                        onSeekToMs = onSeekToMs,
                        modifier = Modifier.fillMaxSize(),
                    )

                    is LyricsState.NotAvailable -> LyricsEmptyState("${lyricsState.reason}\nTap to retry", onRetry)

                    is LyricsState.Error -> LyricsEmptyState("${lyricsState.message}\nRetrying\u2026 tap to retry now", onRetry)
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                DepthIcon(PhosphorIcons.Bold.ShareNetwork, "Share", Modifier.bounceTap(onClick = onShare).padding(6.dp), size = 22.dp)
                Spacer(Modifier.weight(1f))
                DepthIcon(PhosphorIcons.Bold.DotsThreeVertical, "More", Modifier.bounceTap(onClick = onMore).padding(6.dp), size = 22.dp)
            }
            Column(Modifier.padding(horizontal = 24.dp)) {
                SeekBar(progress = progress, onScrub = onScrub, onSeek = onSeekFraction)
                TimeRow(labelPositionMs, durationMs)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DepthIcon(PhosphorIcons.Bold.SkipBack, "Previous", Modifier.bounceTap(onClick = onPrevious).padding(8.dp), size = 30.dp)
                PlayPauseButton(isPlaying = isPlaying, isLoading = isLoading, onClick = onTogglePlay, size = 64.dp)
                DepthIcon(PhosphorIcons.Bold.SkipForward, "Next", Modifier.bounceTap(onClick = onNext).padding(8.dp), size = 30.dp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PanelLyricsList(
    lines: List<LyricsLine>,
    isSynced: Boolean,
    activeIndex: () -> Int,
    positionMs: () -> Long,
    soft: Color,
    onSeekToMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    val latestPosition by rememberUpdatedState(positionMs)
    val readPosition = remember { { latestPosition() } }

    BoxWithConstraints(modifier) {
        val viewport = maxHeight
        LaunchedEffect(lines) { listState.scrollToItem((if (isSynced) activeIndex() else 0).coerceAtLeast(0)) }
        LaunchedEffect(lines, isSynced) {
            if (!isSynced) return@LaunchedEffect
            snapshotFlow { activeIndex() }.collectLatest { i ->
                if (i >= 0 && !dragged) listState.animateScrollToItem(i)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().fadingEdges(0.08f),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = if (isSynced) viewport * 0.14f else 12.dp,
                bottom = viewport * 0.55f,
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            itemsIndexed(lines) { index, line ->
                PanelLine(
                    line = line,
                    isSynced = isSynced,
                    isActive = isSynced && index == activeIndex(),
                    isPast = isSynced && index < activeIndex(),
                    readPosition = readPosition,
                    soft = soft,
                    onClick = if (isSynced) ({ onSeekToMs(line.timestampMs) }) else null,
                )
            }
        }
    }
}

@Composable
private fun PanelLine(
    line: LyricsLine,
    isSynced: Boolean,
    isActive: Boolean,
    isPast: Boolean,
    readPosition: () -> Long,
    soft: Color,
    onClick: (() -> Unit)?,
) {
    val font = rememberLyricsFont()
    val style = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp, textAlign = TextAlign.Start)
    val rowModifier = Modifier.fillMaxWidth().let { if (onClick != null) it.tapTarget(onClick) else it }

    if (!isSynced || line.words.isEmpty()) {
        Text(line.text, color = if (isSynced) soft.copy(alpha = 0.55f) else soft.copy(alpha = 0.95f), style = style, modifier = rowModifier)
        return
    }
    val dim = soft.copy(alpha = 0.5f)
    val sung = Color.White.copy(alpha = 0.85f)
    val step by remember(isActive, readPosition) { derivedStateOf { if (isActive) readPosition() / PanelStepMs else 0L } }
    val now = when {
        isActive -> step * PanelStepMs
        isPast -> Long.MAX_VALUE / 2
        else -> -1_000_000L
    }
    val text = buildAnnotatedString {
        line.words.forEach { word ->
            val span = (word.endMs - word.startMs).coerceAtLeast(1L)
            val p = ((now - word.startMs).toFloat() / span).coerceIn(0f, 1f)
            val target = if (isActive) Color.White else sung
            withStyle(SpanStyle(color = lerp(dim, target, p))) { append(word.text) }
        }
    }
    Text(text, style = style, modifier = rowModifier)
}
