package com.gamut.feature.search

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.Heart
import com.adamglin.phosphoricons.bold.ListPlus
import com.adamglin.phosphoricons.bold.MusicNote
import com.adamglin.phosphoricons.bold.PlusCircle
import com.adamglin.phosphoricons.bold.ShareNetwork
import com.adamglin.phosphoricons.bold.UserCircle
import com.adamglin.phosphoricons.fill.CheckCircle
import com.adamglin.phosphoricons.fill.Heart as HeartFill
import com.gamut.core.designsystem.ArtImage
import com.gamut.core.designsystem.CoverPalette
import com.gamut.core.designsystem.GamutSheet
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.squareArtUrl
import com.gamut.core.model.Track

internal val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

data class TrackPlaylistOption(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int? = null,
    val contains: Boolean = false,
    val trackIsrcs: Set<String> = emptySet(),
)

val LocalPlaylistRemove = staticCompositionLocalOf<(Track, String) -> Unit> { { _, _ -> } }

internal fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounce",
    )
    val tint by animateColorAsState(
        targetValue = ink().copy(
            alpha = if (pressed) {
                0.10f
            } else if (hovered) {
                0.06f
            } else {
                0f
            },
        ),
        animationSpec = tween(140),
        label = "tint",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(RoundedCornerShape(16.dp))
        .background(tint)
        .hoverable(source)
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackActionsSheet(
    track: Track,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onGoToArtist: (() -> Unit)?,
) {
    GamutSheet(onDismiss = onDismiss, shape = SheetShape) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            TrackHeader(track)
            HorizontalDivider(Modifier.padding(top = 14.dp, bottom = 6.dp), color = ink(0.1f))
            ActionRow(PhosphorIcons.Bold.ShareNetwork, "Share") { onShare() }
            ActionRow(
                if (isFavorite) PhosphorIcons.Fill.HeartFill else PhosphorIcons.Bold.Heart,
                if (isFavorite) "Added to favorites" else "Add to favorites",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else ink(),
            ) { onToggleFavorite() }
            ActionRow(PhosphorIcons.Bold.ListPlus, "Add to playlist") { onAddToPlaylist() }
            if (onGoToArtist != null) ActionRow(PhosphorIcons.Bold.UserCircle, "Go to artist") { onGoToArtist() }
        }
    }
}

@Composable
private fun TrackHeader(track: Track) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        ArtImage(
            url = remember(track.albumArtUrl) { squareArtUrl(track.albumArtUrl, 256) },
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
            fallback = { Icon(PhosphorIcons.Bold.MusicNote, null, tint = ink(0.5f)) },
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = ink(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, tint: Color = ink(), onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedInSheet(
    track: Track,
    playlists: List<TrackPlaylistOption>,
    accent: Color,
    showFavorites: Boolean,
    onAddToPlaylist: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val removeFromPlaylist = LocalPlaylistRemove.current
    val ticked = remember {
        mutableStateListOf<String>().apply { addAll(playlists.filter { it.contains || track.isrc in it.trackIsrcs }.map { it.id }) }
    }
    GamutSheet(onDismiss = onDismiss, shape = SheetShape) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showFavorites) {
                Icon(PhosphorIcons.Fill.CheckCircle, null, tint = accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(if (showFavorites) "Saved in" else "Add to playlist", style = MaterialTheme.typography.titleLarge, color = ink())
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            if (showFavorites) {
                item {
                    PlaylistRow("Favorites", "Liked songs", null, checked = true, accent = accent, icon = PhosphorIcons.Fill.HeartFill, iconTint = accent, onClick = {})
                }
            }
            if (playlists.isEmpty()) {
                item {
                    Text(
                        "You don't have any playlists yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink(0.6f),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                    )
                }
            }
            items(playlists, key = { it.id }) { p ->
                val checked = p.id in ticked
                PlaylistRow(
                    name = p.name,
                    sub = p.trackCount?.let { if (it == 1) "1 song" else "$it songs" },
                    coverUrl = p.coverUrl,
                    checked = checked,
                    accent = accent,
                    onClick = {
                        if (checked) {
                            ticked.remove(p.id)
                            removeFromPlaylist(track, p.id)
                        } else {
                            ticked.add(p.id)
                            onAddToPlaylist(p.id)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    name: String,
    sub: String?,
    coverUrl: String?,
    checked: Boolean,
    accent: Color,
    icon: ImageVector = PhosphorIcons.Bold.MusicNote,
    iconTint: Color = ink(0.5f),
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(ink(0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl != null) {
                ArtImage(url = coverUrl, modifier = Modifier.size(48.dp), fallback = {})
            } else {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = ink(0.6f))
        }
        Crossfade(targetState = checked, label = "added") { on ->
            if (on) {
                Icon(PhosphorIcons.Fill.CheckCircle, "Added", tint = accent, modifier = Modifier.size(28.dp))
            } else {
                Icon(PhosphorIcons.Bold.PlusCircle, "Add", tint = ink(0.6f), modifier = Modifier.size(28.dp))
            }
        }
    }
}
