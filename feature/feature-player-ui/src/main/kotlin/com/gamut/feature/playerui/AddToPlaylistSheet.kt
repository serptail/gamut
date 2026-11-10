package com.gamut.feature.playerui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.bold.MusicNote
import com.gamut.core.designsystem.GamutSheet
import com.gamut.core.designsystem.focusRing
import com.gamut.core.designsystem.ink

data class PlaylistPickerItem(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToPlaylistSheet(
    playlists: List<PlaylistPickerItem>,
    alreadyIn: Set<String>,
    onPick: (PlaylistPickerItem) -> Unit,
    onRemove: (PlaylistPickerItem) -> Unit,
    onDismiss: () -> Unit,
) {
    GamutSheet(onDismiss = onDismiss) {
        Text(
            "Add to playlist",
            style = MaterialTheme.typography.titleLarge,
            color = ink(),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        if (playlists.isEmpty()) {
            Text(
                "You don't have any playlists yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = ink(0.6f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
            return@GamutSheet
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(playlists, key = { it.id }) { playlist ->
                val added = playlist.id in alreadyIn
                Row(
                    Modifier
                        .fillMaxWidth()
                        .focusRing(RoundedCornerShape(12.dp))
                        .clickable { if (added) onRemove(playlist) else onPick(playlist) }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(ink(0.07f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(PhosphorIcons.Bold.MusicNote, contentDescription = null, tint = ink(0.4f), modifier = Modifier.size(20.dp))
                        if (!playlist.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = playlist.coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            playlist.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = ink(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        playlist.trackCount?.let { n ->
                            Text(
                                if (n == 1) "1 song" else "$n songs",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ink(0.6f),
                            )
                        }
                    }
                    AnimatedVisibility(added, enter = fadeIn(tween(160)) + scaleIn(tween(220), initialScale = 0.5f)) {
                        Icon(PhosphorIcons.Bold.Check, contentDescription = "In this playlist (tap to remove)", tint = ink(0.85f), modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}
