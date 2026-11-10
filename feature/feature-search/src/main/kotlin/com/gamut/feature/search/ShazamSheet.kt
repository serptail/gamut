package com.gamut.feature.search

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.DotsThree
import com.adamglin.phosphoricons.bold.Heart
import com.adamglin.phosphoricons.fill.Heart
import com.gamut.core.designsystem.AlertTick
import com.gamut.core.designsystem.ArtistPickerSheet
import com.gamut.core.designsystem.CoverPalette
import com.gamut.core.designsystem.ErrorTick
import com.gamut.core.designsystem.GamutSheet
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.R
import com.gamut.core.designsystem.ShareSheet
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale
import com.gamut.core.designsystem.splitArtists
import com.gamut.core.model.Track

private val FavoriteRed = Color(0xFFFF4D6D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShazamSheet(
    state: ShazamState,
    level: Float,
    favoriteIsrcs: Set<String>,
    playlists: List<TrackPlaylistOption>,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlay: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Track, String) -> Unit,
    onTrackArtist: (Track) -> Unit,
) {
    GamutSheet(onDismiss = onDismiss) {
        when (state) {
            is ShazamState.Listening -> ListeningContent(level, state.searching, onDismiss)

            is ShazamState.Found -> FoundContent(
                track = state.track,
                isFavorite = state.track.isrc in favoriteIsrcs,
                playlists = playlists,
                onPlay = onPlay,
                onToggleFavorite = onToggleFavorite,
                onAddToQueue = onAddToQueue,
                onAddToPlaylist = onAddToPlaylist,
                onTrackArtist = onTrackArtist,
            )

            ShazamState.NotFound -> MessageContent(
                icon = AlertTick,
                title = "Couldn't recognize this one",
                message = "Move closer to the speaker and try again.",
                action = "Try again",
                onAction = onRetry,
            )

            is ShazamState.Failed -> MessageContent(
                icon = ErrorTick,
                title = "Something went wrong",
                message = state.message,
                action = "Try again",
                onAction = onRetry,
            )

            ShazamState.PermissionDenied -> MessageContent(
                icon = AlertTick,
                title = "Microphone access is off",
                message = "Turn it on in the app settings so Gamut can listen to the music around you.",
                action = "Open settings",
                onAction = onOpenSettings,
            )

            ShazamState.Idle -> Unit
        }
    }
}

@Composable
private fun ListeningContent(level: Float, searching: Boolean, onCancel: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "shazam-pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
    val smoothLevel = animateFloatAsState(level, tween(120), label = "level")
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            for (offset in floatArrayOf(0f, 0.5f)) {
                Box(
                    Modifier
                        .size(200.dp)
                        .graphicsLayer {
                            val p = (pulse.value + offset) % 1f
                            scaleX = 0.5f + 0.5f * p
                            scaleY = 0.5f + 0.5f * p
                            alpha = (1f - p) * 0.35f
                        }
                        .border(2.dp, ink(), CircleShape),
                )
            }
            Box(
                Modifier
                    .size(128.dp)
                    .graphicsLayer {
                        val s = 1f + 0.3f * smoothLevel.value
                        scaleX = s
                        scaleY = s
                    }
                    .background(ink(0.14f), CircleShape),
            )
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(82.dp).background(LocalCoverPalette.current.base, CircleShape))
                Icon(painterResource(R.drawable.ic_shazam), null, tint = ink(), modifier = Modifier.size(88.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (searching) "Identifying\u2026" else "Listening\u2026",
            style = MaterialTheme.typography.titleLarge,
            color = ink(),
        )
        Text(
            "Hold your phone near the music",
            style = MaterialTheme.typography.bodyMedium,
            color = ink(0.6f),
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 12.dp)) { Text("Cancel", color = ink(0.8f)) }
    }
}

@Composable
private fun MessageContent(icon: ImageVector, title: String, message: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = ink(0.9f), modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = ink(), textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = ink(0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Box(
            Modifier
                .pressScale(onClick = onAction)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(ink())
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) { Text(action, style = MaterialTheme.typography.labelLarge, color = LocalCoverPalette.current.base) }
    }
}

@Composable
private fun FoundContent(
    track: Track,
    isFavorite: Boolean,
    playlists: List<TrackPlaylistOption>,
    onPlay: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Track, String) -> Unit,
    onTrackArtist: (Track) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    var actionsOpen by remember { mutableStateOf(false) }
    var savedOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var artistPickOpen by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = track.albumArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(104.dp).clip(RoundedCornerShape(14.dp)).background(ink(0.08f)),
            )
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleLarge, color = ink(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodyLarge, color = ink(0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                track.album?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = ink(0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .pressScale(onClick = { onPlay(track) })
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(ink()),
                contentAlignment = Alignment.Center,
            ) { Text("Play", style = MaterialTheme.typography.labelLarge, color = LocalCoverPalette.current.base) }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .pressScale(
                        onClick = {
                            note = null
                            onToggleFavorite(track)
                            if (!isFavorite) savedOpen = true
                        },
                        scaleDown = 0.88f,
                    )
                    .clip(CircleShape)
                    .background(ink(0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isFavorite) PhosphorIcons.Fill.Heart else PhosphorIcons.Bold.Heart,
                    if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) FavoriteRed else ink(),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .pressScale(onClick = { actionsOpen = true }, scaleDown = 0.88f)
                    .clip(CircleShape)
                    .background(ink(0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(PhosphorIcons.Bold.DotsThree, "More", tint = ink(), modifier = Modifier.size(24.dp)) }
        }
        note?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = ink(0.6f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        ActionRow("Add to queue") {
            onAddToQueue(track)
            note = "Added to the queue"
        }
    }

    if (actionsOpen) {
        TrackActionsSheet(
            track = track,
            isFavorite = isFavorite,
            onDismiss = { actionsOpen = false },
            onShare = {
                actionsOpen = false
                shareOpen = true
            },
            onToggleFavorite = { onToggleFavorite(track) },
            onAddToPlaylist = {
                actionsOpen = false
                pickerOpen = true
            },
            onGoToArtist = {
                actionsOpen = false
                if (splitArtists(track.artist).size > 1) artistPickOpen = true else onTrackArtist(track)
            },
        )
    }
    if (savedOpen) {
        SavedInSheet(
            track = track,
            playlists = playlists,
            accent = accent,
            showFavorites = true,
            onAddToPlaylist = { id -> onAddToPlaylist(track, id) },
            onDismiss = { savedOpen = false },
        )
    }
    if (pickerOpen) {
        SavedInSheet(
            track = track,
            playlists = playlists,
            accent = accent,
            showFavorites = false,
            onAddToPlaylist = { id -> onAddToPlaylist(track, id) },
            onDismiss = { pickerOpen = false },
        )
    }
    if (artistPickOpen) {
        ArtistPickerSheet(
            names = splitArtists(track.artist),
            albumTitle = track.album,
            onPick = { name -> onTrackArtist(track.copy(artist = name)) },
            onDismiss = { artistPickOpen = false },
        )
    }
    if (shareOpen) {
        ShareSheet(title = track.title, artist = track.artist, artUrl = track.albumArtUrl, onDismiss = { shareOpen = false })
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = ink(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(onClick = onClick, scaleDown = 0.98f)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}
