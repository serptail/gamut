package com.gamut.core.designsystem

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Devices
import com.adamglin.phosphoricons.regular.Pause
import com.adamglin.phosphoricons.regular.Play
import com.adamglin.phosphoricons.regular.SkipBack
import com.adamglin.phosphoricons.regular.SkipForward

@Composable
fun MiniPlayerStrip(
    title: String,
    artist: String,
    artUrl: String?,
    isPlaying: Boolean,
    progress: Float,
    isLoading: Boolean = false,
    loadProgress: Float = 0f,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit = {},
    onDevicesClick: () -> Unit = {},
    devicesActive: Boolean = false,
    modifier: Modifier = Modifier,
    dockedToEdge: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.primary
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = if (isWide) navInset.coerceAtMost(16.dp) else navInset
    val acrylicFillStrip = Modifier.acrylicFill(AcrylicMaterials.Thick, if (LocalCoverPalette.current.isLight) AcrylicTone.Light else AcrylicTone.Dark)
    Column(
        modifier
            .fillMaxWidth()
            .then(if (dockedToEdge) Modifier.background(ink(0.05f)).padding(bottom = bottomInset) else acrylicFillStrip)
            .pressScale(onClick = onOpen, scaleDown = 0.99f),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(ink(0.08f))) {
            val shown = if (isLoading) loadProgress.coerceIn(0.08f, 1f) else progress.coerceIn(0f, 1f)
            val animated by animateFloatAsState(shown, tween(if (isLoading) 350 else 0), label = "mini-progress")
            Box(Modifier.fillMaxHeight().fillMaxWidth(animated).background(accent))
        }
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val artShape = RoundedCornerShape(8.dp)
            Box(Modifier.size(42.dp).clip(artShape).background(ink(0.08f))) {
                if (artUrl != null) {
                    AsyncImage(
                        model = artUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = ink(if (isLoading) 0.6f else 1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    artist,
                    color = ink(0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.size(40.dp).pressScale(onClick = onDevicesClick, scaleDown = 0.88f), contentAlignment = Alignment.Center) {
                Icon(PhosphorIcons.Regular.Devices, "Connect to a device", tint = if (devicesActive) accent else ink(0.9f), modifier = Modifier.size(22.dp))
            }
            Box(Modifier.size(40.dp).pressScale(onClick = onPrevious, scaleDown = 0.88f), contentAlignment = Alignment.Center) {
                Icon(PhosphorIcons.Regular.SkipBack, "Previous", tint = ink(0.9f), modifier = Modifier.size(22.dp))
            }
            Box(Modifier.size(48.dp).pressScale(onClick = onTogglePlay, scaleDown = 0.88f), contentAlignment = Alignment.Center) {
                Crossfade(
                    if (isLoading) {
                        2
                    } else if (isPlaying) {
                        1
                    } else {
                        0
                    },
                    animationSpec = tween(160),
                    label = "mini-play",
                ) { s ->
                    when (s) {
                        2 -> CircularProgressIndicator(color = ink(), strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        1 -> Icon(PhosphorIcons.Regular.Pause, "Pause", tint = ink(), modifier = Modifier.size(24.dp))
                        else -> Icon(PhosphorIcons.Regular.Play, "Play", tint = ink(), modifier = Modifier.size(24.dp))
                    }
                }
            }
            Box(Modifier.size(40.dp).pressScale(onClick = onNext, scaleDown = 0.88f), contentAlignment = Alignment.Center) {
                Icon(PhosphorIcons.Regular.SkipForward, "Next", tint = ink(0.9f), modifier = Modifier.size(22.dp))
            }
        }
    }
}
