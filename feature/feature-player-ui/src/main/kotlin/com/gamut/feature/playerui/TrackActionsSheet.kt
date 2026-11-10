package com.gamut.feature.playerui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamut.core.designsystem.CoverPalette
import com.gamut.core.designsystem.GamutMotion
import com.gamut.core.designsystem.GamutSheet
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.softResize
import com.gamut.core.model.Playlist
import com.gamut.core.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    track: Track,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val submitNew = {
        if (name.isNotBlank()) {
            onCreatePlaylist(name.trim())
            onDismiss()
        }
    }

    GamutSheet(onDismiss = onDismiss, sheetState = sheetState) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = ink(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink(0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item {
                ActionRow("Play next") {
                    onPlayNext()
                    onDismiss()
                }
            }
            item {
                ActionRow("Add to queue") {
                    onAddToQueue()
                    onDismiss()
                }
            }
            item {
                Text(
                    "Add to playlist",
                    style = MaterialTheme.typography.labelLarge,
                    color = ink(0.6f),
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
                )
            }
            item {
                Box(Modifier.softResize()) {
                    Crossfade(targetState = creating, animationSpec = tween(GamutMotion.CONTENT_MS), label = "new-playlist") { isCreating ->
                        if (isCreating) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .background(ink(0.1f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    if (name.isEmpty()) {
                                        Text("Playlist name", color = ink(0.4f), style = MaterialTheme.typography.bodyLarge)
                                    }
                                    BasicTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = ink())),
                                        cursorBrush = SolidColor(ink()),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { submitNew() }),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { submitNew() }, enabled = name.isNotBlank()) { Text("Create") }
                            }
                        } else {
                            ActionRow("New playlist\u2026") { creating = true }
                        }
                    }
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                ActionRow(playlist.name) {
                    onAddToPlaylist(playlist)
                    onDismiss()
                }
            }
        }
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
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}
