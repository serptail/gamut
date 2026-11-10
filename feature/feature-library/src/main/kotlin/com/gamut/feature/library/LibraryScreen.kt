package com.gamut.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.CaretRight
import com.adamglin.phosphoricons.bold.MagnifyingGlass
import com.adamglin.phosphoricons.bold.MusicNote
import com.adamglin.phosphoricons.bold.MusicNoteSimple
import com.adamglin.phosphoricons.bold.Plus
import com.adamglin.phosphoricons.bold.PlusCircle
import com.adamglin.phosphoricons.bold.Rows
import com.adamglin.phosphoricons.bold.SquaresFour
import com.adamglin.phosphoricons.fill.CheckCircle
import com.adamglin.phosphoricons.fill.DeviceMobileCamera
import com.adamglin.phosphoricons.fill.Heart
import com.gamut.core.designsystem.AcrylicMaterials
import com.gamut.core.designsystem.AcrylicTone
import com.gamut.core.designsystem.BottomBarItem
import com.gamut.core.designsystem.CoverTheme
import com.gamut.core.designsystem.GamutBottomBar
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.LocalNotifier
import com.gamut.core.designsystem.TransparentStatusBar
import com.gamut.core.designsystem.acrylicFill
import com.gamut.core.designsystem.adaptiveGutter
import com.gamut.core.designsystem.fadeEdges
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.isWideLayout
import com.gamut.core.designsystem.pressScale
import com.gamut.core.model.Track

private val TextPrimary: Color
    @Composable @ReadOnlyComposable
    get() = ink()
private val TextSecondary: Color
    @Composable @ReadOnlyComposable
    get() = ink(0.6f)

private val PanelShape = RoundedCornerShape(12.dp)
private val ButtonShape = RoundedCornerShape(8.dp)

data class LibraryArtist(val id: String, val name: String, val imageUrl: String? = null)

private enum class LibraryFilter(val label: String) { Playlists("Playlists"), Artists("Artists"), Offline("Offline") }

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    selectedTrack: Track?,
    onOpenPlaylist: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    barHeight: androidx.compose.ui.unit.Dp,
    artists: List<LibraryArtist> = emptyList(),
    artistsLoading: Boolean = false,
    onOpenArtist: (String) -> Unit = {},
    suggestedArtists: List<LibraryArtist> = emptyList(),
    onOpenSearch: () -> Unit = {},
    onOpenLocal: () -> Unit = {},
    onPlayTrack: (Track, List<Track>) -> Unit = { _, _ -> },
) {
    val downloaded by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistInfo by viewModel.playlistInfo.collectAsStateWithLifecycle()
    val likedOffline by viewModel.likedOffline.collectAsStateWithLifecycle()
    val notifier = LocalNotifier.current

    val palette = LocalCoverPalette.current

    var filter by rememberSaveable { mutableStateOf<LibraryFilter?>(null) }
    var isGrid by rememberSaveable { mutableStateOf(false) }
    val addedTo = remember(selectedTrack?.isrc) { mutableStateListOf<Long>() }

    CoverTheme(palette) {
        val showPlaylists = filter == null || filter == LibraryFilter.Playlists
        val showArtists = filter == null || filter == LibraryFilter.Artists
        val showOffline = filter == LibraryFilter.Offline
        val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

        val wide = isWideLayout()
        val gutter = if (wide) 16.dp else adaptiveGutter()
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = barHeight)) {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp + gutter, end = 24.dp + gutter, top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Library",
                            style = MaterialTheme.typography.displayLarge,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.size(44.dp).pressScale(onClick = onCreatePlaylist), contentAlignment = Alignment.Center) {
                            Icon(PhosphorIcons.Bold.Plus, "Create playlist", tint = ink(0.85f), modifier = Modifier.size(24.dp))
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp + gutter, end = 24.dp + gutter, top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LibraryFilter.values().forEach { f ->
                            FilterPill(f.label, selected = filter == f) { filter = if (filter == f) null else f }
                        }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(36.dp).pressScale(onClick = { isGrid = !isGrid }), contentAlignment = Alignment.Center) {
                            Icon(
                                if (isGrid) PhosphorIcons.Bold.Rows else PhosphorIcons.Bold.SquaresFour,
                                if (isGrid) "Switch to list" else "Switch to grid",
                                tint = ink(0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    if (selectedTrack != null) ReadyToAddCard(selectedTrack, Modifier.padding(horizontal = 24.dp + gutter).padding(top = 12.dp))

                    LazyVerticalGrid(
                        columns = when {
                            wide -> GridCells.Adaptive(if (isGrid) 170.dp else 380.dp)
                            else -> GridCells.Fixed(if (isGrid) 3 else 1)
                        },
                        modifier = Modifier.weight(1f).fadeEdges(),
                        contentPadding = PaddingValues(start = 24.dp + gutter, end = 24.dp + gutter, top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isGrid) 18.dp else 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (showOffline) {
                            if (downloaded.isEmpty()) {
                                item(key = "offline-empty", span = fullSpan) {
                                    EmptyStateCard(
                                        title = "No downloaded songs yet",
                                        subtitle = "Songs you download show up here and play without internet.",
                                        modifier = Modifier.padding(top = 8.dp),
                                        buttonLabel = "Find songs",
                                        onButton = onOpenSearch,
                                    )
                                }
                            }
                            items(downloaded, key = { "off-${it.isrc}" }, contentType = { "offline" }) { song ->
                                if (isGrid) {
                                    GridCell(song.title, { onPlayTrack(song, downloaded) }, offline = true) {
                                        TileCover(song.albumArtUrl, PhosphorIcons.Bold.MusicNoteSimple)
                                    }
                                } else {
                                    LibraryRow(
                                        title = song.title,
                                        meta = {
                                            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        },
                                        onClick = { onPlayTrack(song, downloaded) },
                                        cover = { RowCover(song.albumArtUrl, PhosphorIcons.Bold.MusicNoteSimple) },
                                        offline = true,
                                        trailing = {},
                                    )
                                }
                            }
                        }

                        if (showPlaylists) {
                            item(key = "liked") {
                                if (isGrid) {
                                    GridCell("Liked songs", { onOpenPlaylist("liked") }, offline = likedOffline) { TileCover(null, PhosphorIcons.Fill.Heart) }
                                } else {
                                    LibraryRow(
                                        title = "Liked songs",
                                        onClick = { onOpenPlaylist("liked") },
                                        cover = { RowCover(null, PhosphorIcons.Fill.Heart) },
                                        offline = likedOffline,
                                    )
                                }
                            }

                            item(key = "on-device") {
                                if (isGrid) {
                                    GridCell("On this device", onOpenLocal) { TileCover(null, PhosphorIcons.Fill.DeviceMobileCamera) }
                                } else {
                                    LibraryRow(
                                        title = "On this device",
                                        meta = { Text("Music stored on your phone", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        onClick = onOpenLocal,
                                        cover = { RowCover(null, PhosphorIcons.Fill.DeviceMobileCamera) },
                                    )
                                }
                            }

                            if (filter == null) item(key = "playlists-header", span = fullSpan) { SectionTitle("Your playlists") }

                            if (userPlaylists.isEmpty()) {
                                item(key = "playlists-empty", span = fullSpan) {
                                    EmptyStateCard(
                                        title = "Create your first playlist",
                                        subtitle = "Make a playlist and start filling it with songs.",
                                        modifier = Modifier.padding(top = 8.dp),
                                        buttonLabel = "Create playlist",
                                        onButton = onCreatePlaylist,
                                    )
                                }
                            }
                            items(userPlaylists, key = { "v-${it.id}" }, contentType = { "playlist" }) { playlist ->
                                val cover = playlist.coverUri ?: playlistInfo[playlist.id]?.coverUrl
                                val offline = playlistInfo[playlist.id]?.offline == true
                                val added = playlist.id in addedTo
                                val addOrOpen: () -> Unit = {
                                    if (selectedTrack != null) {
                                        if (!added) {
                                            addedTo.add(playlist.id)
                                            viewModel.addTrack(playlist.id, selectedTrack)
                                            notifier.success("Added to ${playlist.name}")
                                        }
                                    } else {
                                        onOpenPlaylist(playlist.id.toString())
                                    }
                                }
                                if (isGrid) {
                                    GridCell(playlist.name, addOrOpen, offline = offline) { TileCover(cover, PhosphorIcons.Bold.MusicNoteSimple) }
                                } else {
                                    LibraryRow(
                                        title = playlist.name,
                                        meta = if (playlist.tags.isEmpty()) null else ({ TagRow(playlist.tags) }),
                                        onClick = { onOpenPlaylist(playlist.id.toString()) },
                                        cover = { RowCover(cover, PhosphorIcons.Bold.MusicNoteSimple) },
                                        offline = offline,
                                        trailing = {
                                            if (selectedTrack != null) {
                                                Box(
                                                    Modifier.size(44.dp).then(if (added) Modifier else Modifier.pressScale(onClick = { addOrOpen() })),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        if (added) PhosphorIcons.Fill.CheckCircle else PhosphorIcons.Bold.PlusCircle,
                                                        if (added) "Added" else "Add to playlist",
                                                        tint = if (added) TextPrimary else ink(0.85f),
                                                        modifier = Modifier.size(28.dp),
                                                    )
                                                }
                                            } else {
                                                ChevronIcon()
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        if (showArtists) {
                            if (filter == null) item(key = "artists-header", span = fullSpan) { SectionTitle("Artists you follow") }
                            if (artists.isEmpty() && !artistsLoading) {
                                item(key = "artists-empty", span = fullSpan) {
                                    EmptyStateCard(
                                        title = "You haven\u2019t followed any artists yet",
                                        subtitle = "Follow your favorites to keep them close.",
                                        modifier = Modifier.padding(top = 8.dp),
                                        buttonLabel = "Find artists",
                                        onButton = onOpenSearch,
                                        covers = suggestedArtists.mapNotNull { it.imageUrl?.takeIf { u -> u.isNotBlank() } }.take(5).ifEmpty { EmptyStateArtistCovers.urls },
                                        circleCovers = true,
                                    )
                                }
                            }
                            items(artists, key = { "a-${it.id}" }, contentType = { "artist" }) { artist ->
                                if (isGrid) {
                                    GridCell(artist.name, { onOpenArtist(artist.id) }, circle = true) {
                                        TileCover(artist.imageUrl, PhosphorIcons.Bold.MusicNote, circle = true)
                                    }
                                } else {
                                    LibraryRow(
                                        title = artist.name,
                                        onClick = { onOpenArtist(artist.id) },
                                        cover = { RowCover(artist.imageUrl, PhosphorIcons.Bold.MusicNote, circle = true) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val light = LocalCoverPalette.current.isLight
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) (if (light) Color.White else Color.Black) else TextPrimary,
        modifier = Modifier
            .pressScale(onClick = onClick)
            .background(if (selected) ink(0.95f) else ink(0.12f), CircleShape)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun GridCell(title: String, onClick: () -> Unit, circle: Boolean = false, offline: Boolean = false, cover: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().pressScale(onClick = onClick, scaleDown = 0.97f),
        horizontalAlignment = if (circle) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        cover()
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (circle) TextAlign.Center else TextAlign.Start,
        )
        if (offline) AvailableOfflineLine(Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun TileCover(coverUrl: String?, icon: ImageVector, circle: Boolean = false) {
    val shape = if (circle) CircleShape else RoundedCornerShape(10.dp)
    Box(Modifier.fillMaxWidth()) {
        if (!coverUrl.isNullOrBlank()) {
            CoverImage(
                remember(coverUrl) { resolveCoverUrl(coverUrl, 400) },
                Modifier.fillMaxWidth().aspectRatio(1f).then(if (circle) Modifier.clip(shape) else Modifier.softCover()),
            )
        } else {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).then(if (circle) Modifier.clip(shape) else Modifier.softCover()).background(flatCoverFill()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = ink(), modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun ReadyToAddCard(track: Track, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val tone = if (LocalCoverPalette.current.isLight) AcrylicTone.Light else AcrylicTone.Dark
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .acrylicFill(AcrylicMaterials.Regular, tone)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            remember(track.albumArtUrl) { resolveCoverUrl(track.albumArtUrl, 160) },
            Modifier.size(48.dp).softCover(RoundedCornerShape(10.dp)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text("Ready to add \u00B7 pick a playlist", style = MaterialTheme.typography.labelMedium, color = ink(0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = TextPrimary,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
    )
}

@Composable
private fun ChevronIcon() {
    Icon(PhosphorIcons.Bold.CaretRight, null, tint = ink(0.45f), modifier = Modifier.size(18.dp))
}

@Composable
private fun RowCover(coverUrl: String?, icon: ImageVector, circle: Boolean = false) {
    if (!coverUrl.isNullOrBlank()) {
        CoverImage(
            remember(coverUrl) { resolveCoverUrl(coverUrl, 200) },
            Modifier.size(58.dp).then(if (circle) Modifier.clip(CircleShape) else Modifier.softCover()),
        )
    } else {
        Box(
            Modifier.size(58.dp).then(if (circle) Modifier.clip(CircleShape) else Modifier.softCover()).background(flatCoverFill()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = ink(), modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun TagRow(tags: List<String>) {
    val clean = remember(tags) { tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() } }
    if (clean.isEmpty()) return
    val shown = clean.take(MAX_TAGS_SHOWN)
    val extra = clean.size - shown.size
    Row(
        Modifier.fillMaxWidth().clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEach { tag -> TagPill(tag, Modifier.weight(1f, fill = false)) }
        if (extra > 0) TagPill("+$extra")
    }
}

@Composable
private fun TagPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = ink(0.75f),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(ink(0.10f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private const val MAX_TAGS_SHOWN = 3

@Composable
private fun LibraryRow(
    title: String,
    onClick: () -> Unit,
    cover: @Composable () -> Unit,
    meta: (@Composable () -> Unit)? = null,
    offline: Boolean = false,
    trailing: @Composable () -> Unit = { ChevronIcon() },
) {
    Row(
        Modifier.fillMaxWidth().pressScale(onClick = onClick, scaleDown = 0.98f).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cover()
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta != null) {
                Spacer(Modifier.height(5.dp))
                meta()
            }
            if (offline) AvailableOfflineLine(Modifier.padding(top = 6.dp))
        }
        trailing()
    }
}
