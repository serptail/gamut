package com.gamut.feature.playerui

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.X
import com.gamut.core.designsystem.GamutSheet
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.ink
import com.gamut.core.model.QueueState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    queue: QueueState,
    accent: Color,
    onDismiss: () -> Unit,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    GamutSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Text(
            text = "Up next",
            style = MaterialTheme.typography.titleLarge,
            color = ink(),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (queue.tracks.isEmpty()) {
            Text(
                text = "The queue is empty",
                style = MaterialTheme.typography.bodyMedium,
                color = ink(0.6f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
            return@GamutSheet
        }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = queue.currentIndex.coerceAtLeast(0))
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(queue.tracks, key = { index, track -> "${track.isrc}#$index" }) { index, track ->
                val isCurrent = index == queue.currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPlay(index) }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = track.albumArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isCurrent) accent else ink(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink(0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!isCurrent) {
                        Box(
                            Modifier.size(40.dp).clickable { onRemove(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(PhosphorIcons.Bold.X, "Remove from queue", tint = ink(0.6f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
