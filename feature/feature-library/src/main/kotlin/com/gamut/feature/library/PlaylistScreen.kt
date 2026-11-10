package com.gamut.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.ArrowCircleDown
import com.adamglin.phosphoricons.bold.ArrowDown
import com.adamglin.phosphoricons.bold.ArrowLeft
import com.adamglin.phosphoricons.bold.ArrowUp
import com.adamglin.phosphoricons.bold.BoxArrowDown
import com.adamglin.phosphoricons.bold.DotsThreeVertical
import com.adamglin.phosphoricons.bold.Heart
import com.adamglin.phosphoricons.bold.ImageSquare
import com.adamglin.phosphoricons.bold.MagnifyingGlass
import com.adamglin.phosphoricons.bold.PencilSimple
import com.adamglin.phosphoricons.bold.Plus
import com.adamglin.phosphoricons.bold.RowsPlusTop
import com.adamglin.phosphoricons.bold.ShareNetwork
import com.adamglin.phosphoricons.bold.Shuffle
import com.adamglin.phosphoricons.bold.Trash
import com.adamglin.phosphoricons.bold.User
import com.adamglin.phosphoricons.bold.VinylRecord
import com.adamglin.phosphoricons.bold.X
import com.adamglin.phosphoricons.fill.Archive
import com.adamglin.phosphoricons.fill.CheckCircle
import com.adamglin.phosphoricons.fill.Heart
import com.adamglin.phosphoricons.fill.Play
import com.gamut.core.designsystem.ArtistLine
import com.gamut.core.designsystem.ArtistPickerSheet
import com.gamut.core.designsystem.CoverTheme
import com.gamut.core.designsystem.GamutSheet
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.LocalNavSettled
import com.gamut.core.designsystem.NowPlayingBars
import com.gamut.core.designsystem.ShareSheet
import com.gamut.core.designsystem.TransparentStatusBar
import com.gamut.core.designsystem.focusRing
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.isWideLayout
import com.gamut.core.designsystem.pressScale
import com.gamut.core.designsystem.rememberArtistTap
import com.gamut.core.designsystem.splitArtists
import com.gamut.core.model.Track
import com.gamut.feature.resolver.DownloadState
import kotlinx.coroutines.launch

private val HeaderCoverShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(50)
private val PanelShape = RoundedCornerShape(16.dp)
private val DangerColor = Color(0xFFFF6B6B)
private const val MAX_NAME_LENGTH = 60

@Composable
private fun sheetInk(alpha: Float = 1f): Color = ink(alpha)

private enum class PlaylistSheet { None, Options, Edit }

private typealias Listed = IndexedValue<Track>

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: String,
    title: String = "Playlist",
    onBack: () -> Unit,
    onPlay: (List<Track>) -> Unit = {},
    onShuffle: (List<Track>) -> Unit = {},
    onTrackSelected: (Track) -> Unit = {},
    onAddSongs: () -> Unit = {},
    nowPlayingIsrc: String? = null,
    favoriteIsrcs: Set<String> = emptySet(),
    existingLocalIsrcs: Set<String>? = null,
    onToggleFavorite: (Track) -> Unit = {},
    onTrackArtist: (Track) -> Unit = {},
    viewModel: PlaylistViewModel = viewModel(),
    downloads: PlaylistDownloadsViewModel = viewModel(),
    nowPlaying: @Composable () -> Unit = {},
) {
    val storedSongs by viewModel.tracks.collectAsStateWithLifecycle()
    val songs = remember(storedSongs, existingLocalIsrcs) { storedSongs.withoutMissingLocal(existingLocalIsrcs) }
    LaunchedEffect(storedSongs, existingLocalIsrcs) {
        if (songs.size != storedSongs.size) viewModel.setTracks(songs)
    }
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val downloadStates by downloads.states.collectAsStateWithLifecycle()
    val palette = LocalCoverPalette.current
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    TransparentStatusBar(darkIcons = palette.isLight)
    val settled = LocalNavSettled.current

    val name = playlist?.name ?: title
    var sheet by remember { mutableStateOf(PlaylistSheet.None) }
    var showManage by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRemoveDownloads by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }
    var draftCover by remember { mutableStateOf<String?>(null) }
    val draftTags = remember { mutableStateListOf<String>() }
    var menuIndex by remember { mutableStateOf<Int?>(null) }
    var shareTrack by remember { mutableStateOf<Track?>(null) }
    var artistPickTrack by remember { mutableStateOf<Track?>(null) }
    var query by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val searching = query.isNotBlank()

    val streamed = remember(songs) { songs.filter { !it.isLocal } }
    val streamedIsrcs = remember(streamed) { streamed.map { it.isrc }.distinct() }
    val states = remember(downloadStates, songs) {
        downloadStates + songs.filter { it.isLocal }.associate { it.isrc to DownloadState.Done }
    }
    val isrcs = remember(songs) { songs.map { it.isrc }.distinct() }
    val downloadUi = remember(isrcs, states) { playlistDownloadUi(isrcs, states) }
    val downloadedCount = remember(isrcs, states) { isrcs.count { states[it] == DownloadState.Done } }
    val streamedDownloaded = remember(streamedIsrcs, downloadStates) { streamedIsrcs.count { downloadStates[it] == DownloadState.Done } }

    val visible: List<Listed> = remember(songs, query) {
        val q = query.trim().lowercase()
        songs.withIndex().filter { (_, t) ->
            q.isEmpty() || t.title.lowercase().contains(q) || t.artist.lowercase().contains(q) || t.album.orEmpty().lowercase().contains(q)
        }
    }

    val customCover = playlist?.coverUri
    val coverUrl = customCover ?: songs.lastOrNull()?.albumArtUrl

    val pickDraftCover = rememberCoverPicker { draftCover = it }
    val pickQuickCover = rememberCoverPicker { uri -> playlist?.let { viewModel.save(it.name, it.tags, uri, null) } }

    fun startEdit() {
        draftName = name
        draftCover = playlist?.coverUri
        draftTags.clear()
        draftTags.addAll(playlist?.tags.orEmpty())
        sheet = PlaylistSheet.Edit
    }

    fun saveEdit() {
        if (draftName.isBlank()) return
        viewModel.save(draftName, draftTags.toList(), draftCover, null)
        sheet = PlaylistSheet.None
    }

    fun closeSheet(state: SheetState, then: () -> Unit = {}) {
        scope.launch { state.hide() }.invokeOnCompletion {
            sheet = PlaylistSheet.None
            then()
        }
    }

    val onDownloadClick: () -> Unit = {
        when (downloadUi.status) {
            PlaylistDownloadStatus.Idle -> downloads.download(streamed, label = name)
            PlaylistDownloadStatus.Active -> downloads.cancel(streamedIsrcs)
            PlaylistDownloadStatus.Done -> if (streamedIsrcs.isNotEmpty()) confirmRemoveDownloads = true
        }
    }

    BackHandler(enabled = searching) {
        query = ""
        focus.clearFocus()
    }

    CoverTheme(palette) {
        val accent = MaterialTheme.colorScheme.primary
        val wide = isWideLayout()
        Column(Modifier.fillMaxSize().statusBarsPadding().then(if (searchFocused) Modifier.imePadding() else Modifier)) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                TopIcon(PhosphorIcons.Bold.ArrowLeft, "Back", onClick = onBack)
            }
            if (settled && songs.isNotEmpty() && !wide) {
                SearchField(
                    query = query,
                    onChange = { query = it },
                    onFocus = { searchFocused = it },
                    onDone = { focus.clearFocus() },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp),
                )
            }
            val headerSlot: @Composable () -> Unit = {
                PlaylistHeader(
                    title = name,
                    coverUrl = coverUrl,
                    tags = playlist?.tags.orEmpty(),
                    songCount = songs.size,
                    artistCount = songs.flatMap { splitArtists(it.artist) }.distinctBy { it.lowercase() }.size,
                    statusLine = when (downloadUi.status) {
                        PlaylistDownloadStatus.Active -> "Downloading ${minOf(downloadUi.done + 1, downloadUi.total)} of ${downloadUi.total}"
                        PlaylistDownloadStatus.Done -> null
                        PlaylistDownloadStatus.Idle -> if (downloadedCount > 0) "$downloadedCount of ${isrcs.size} downloaded" else null
                    },
                    downloadUi = downloadUi,
                    offline = downloadUi.status == PlaylistDownloadStatus.Done,
                    onDownload = onDownloadClick,
                    onMore = if (playlist != null) ({ sheet = PlaylistSheet.Options }) else null,
                    onPlay = { onPlay(songs) },
                    onShuffle = { onShuffle(songs) },
                )
            }
            val listBody: LazyListScope.(Boolean) -> Unit = { showHeader ->
                if (showHeader && !searching) {
                    item(key = "header") { headerSlot() }
                }
                if (songs.isEmpty()) {
                    item(key = "empty") {
                        EmptyStateCard(
                            title = "Add songs to your playlist",
                            subtitle = "Search for songs and add them here.",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            buttonLabel = "Find songs",
                            onButton = onAddSongs,
                        )
                    }
                } else if (searching && visible.isEmpty()) {
                    item(key = "no-match") {
                        Text(
                            "No songs match \u201C${query.trim()}\u201D",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink(0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                        )
                    }
                }
                itemsIndexed(visible, key = { _, item -> "${item.value.isrc}-${item.index}" }) { _, item ->
                    val song = item.value
                    PlaylistTrackRow(
                        track = song,
                        isPlaying = song.isrc == nowPlayingIsrc,
                        accent = accent,
                        downloadState = states[song.isrc],
                        onDownload = { downloads.download(listOf(song)) },
                        onCancelDownload = { downloads.cancel(listOf(song.isrc)) },
                        onArtist = { name -> onTrackArtist(song.copy(artist = name)) },
                        onClick = { onTrackSelected(song) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuIndex = item.index
                        },
                        onMore = { menuIndex = item.index },
                    )
                }
                item(key = "nav-space") { Box(Modifier.navigationBarsPadding()) }
            }
            if (settled && wide) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.weight(0.4f).fillMaxHeight().verticalScroll(rememberScrollState())) { headerSlot() }
                    Column(Modifier.weight(0.6f).fillMaxHeight()) {
                        if (songs.isNotEmpty()) {
                            SearchField(
                                query = query,
                                onChange = { query = it },
                                onFocus = { searchFocused = it },
                                onDone = { focus.clearFocus() },
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp),
                            )
                        }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) { listBody(false) }
                    }
                }
            } else if (settled) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) { listBody(true) }
            } else {
                Spacer(Modifier.weight(1f))
            }
            nowPlaying()
        }

        val menuTrack = menuIndex?.let { songs.getOrNull(it) }
        if (menuIndex != null && menuTrack != null) {
            val i = menuIndex!!
            TrackOptionsPanel(
                track = menuTrack,
                isFavorite = menuTrack.isrc in favoriteIsrcs,
                downloadState = states[menuTrack.isrc],
                local = menuTrack.isLocal,
                onDownload = { downloads.download(listOf(menuTrack)) },
                onRemoveDownload = { downloads.remove(listOf(menuTrack.isrc)) },
                canMoveUp = !searching && i > 0,
                canMoveDown = !searching && i < songs.lastIndex,
                onPlay = { onTrackSelected(menuTrack) },
                onToggleFavorite = { onToggleFavorite(menuTrack) },
                onGoToArtist = { if (splitArtists(menuTrack.artist).size > 1) artistPickTrack = menuTrack else onTrackArtist(menuTrack) },
                onShare = { shareTrack = menuTrack },
                onMoveUp = { viewModel.move(i, i - 1) },
                onMoveDown = { viewModel.move(i, i + 1) },
                onRemove = { viewModel.removeAt(i) },
                onDismiss = { menuIndex = null },
            )
        }

        shareTrack?.let { t ->
            ShareSheet(title = t.title, artist = t.artist, artUrl = t.albumArtUrl, onDismiss = { shareTrack = null })
        }
        artistPickTrack?.let { t ->
            ArtistPickerSheet(
                names = splitArtists(t.artist),
                albumTitle = t.album,
                onPick = { name -> onTrackArtist(t.copy(artist = name)) },
                onDismiss = { artistPickTrack = null },
            )
        }

        if (sheet == PlaylistSheet.Options) {
            val state = rememberModalBottomSheetState()
            GamutSheet(
                onDismiss = { sheet = PlaylistSheet.None },
                sheetState = state,
                dragHandle = { BottomSheetDefaults.DragHandle(color = ink(0.3f)) },
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (coverUrl.isNullOrBlank()) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(sheetInk(0.08f)), contentAlignment = Alignment.Center) {
                                Icon(PhosphorIcons.Bold.VinylRecord, null, tint = sheetInk(0.6f), modifier = Modifier.size(24.dp))
                            }
                        } else {
                            CoverImage(remember(coverUrl) { resolveCoverUrl(coverUrl, 160) }, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
                        }
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(name, style = MaterialTheme.typography.titleMedium, color = sheetInk(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(plural(songs.size, "song"), style = MaterialTheme.typography.bodyMedium, color = sheetInk(0.6f))
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(sheetInk(0.10f)))
                    SheetAction(PhosphorIcons.Bold.PencilSimple, "Edit playlist") { closeSheet(state) { startEdit() } }
                    if (songs.isNotEmpty()) SheetAction(PhosphorIcons.Bold.RowsPlusTop, "Manage playlist") { closeSheet(state) { showManage = true } }
                    SheetAction(PhosphorIcons.Bold.ImageSquare, "Change cover art") {
                        pickQuickCover()
                        closeSheet(state)
                    }
                    if (streamedDownloaded > 0) SheetAction(PhosphorIcons.Bold.X, "Remove downloads") { closeSheet(state) { confirmRemoveDownloads = true } }
                    SheetAction(PhosphorIcons.Bold.Trash, "Delete playlist", danger = true) { closeSheet(state) { confirmDelete = true } }
                }
            }
        }

        if (sheet == PlaylistSheet.Edit) {
            val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            GamutSheet(
                onDismiss = { sheet = PlaylistSheet.None },
                sheetState = state,
                dragHandle = { BottomSheetDefaults.DragHandle(color = ink(0.3f)) },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Edit playlist", style = MaterialTheme.typography.titleLarge, color = sheetInk(), modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    val shownCover = draftCover ?: songs.lastOrNull()?.albumArtUrl
                    Box(Modifier.size(150.dp).pressScale(onClick = pickDraftCover)) {
                        if (shownCover.isNullOrBlank()) {
                            Box(Modifier.fillMaxSize().clip(HeaderCoverShape).background(sheetInk(0.07f)), contentAlignment = Alignment.Center) {
                                Icon(PhosphorIcons.Bold.Plus, "Add cover", tint = sheetInk(0.6f), modifier = Modifier.size(44.dp))
                            }
                        } else {
                            CoverImage(remember(shownCover) { resolveCoverUrl(shownCover, 400) }, Modifier.fillMaxSize().clip(HeaderCoverShape))
                        }
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(PhosphorIcons.Bold.PencilSimple, "Change cover", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (draftCover != null) {
                        Text(
                            "Use the last song's art instead",
                            style = MaterialTheme.typography.labelLarge,
                            color = sheetInk(0.7f),
                            modifier = Modifier.padding(top = 10.dp).pressScale(onClick = { draftCover = null }).padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    val style = MaterialTheme.typography.headlineMedium
                    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 20.dp)) {
                        BasicTextField(
                            value = draftName,
                            onValueChange = { if (it.length <= MAX_NAME_LENGTH) draftName = it },
                            singleLine = true,
                            textStyle = style.copy(color = sheetInk(), textAlign = TextAlign.Center),
                            cursorBrush = SolidColor(sheetInk()),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    if (draftName.isEmpty()) Text("Playlist name", style = style, color = sheetInk(0.35f), textAlign = TextAlign.Center)
                                    inner()
                                }
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(sheetInk(0.25f)))
                    }
                    Text(
                        "Tags",
                        style = MaterialTheme.typography.titleMedium,
                        color = sheetInk(),
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp),
                    )
                    TagsEditor(tags = draftTags, onChange = {
                        draftTags.clear()
                        draftTags.addAll(it)
                    })
                    Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SheetButton("Cancel", filled = false, enabled = true) { closeSheet(state) }
                        SheetButton("Save", filled = true, enabled = draftName.isNotBlank()) { saveEdit() }
                    }
                }
            }
        }

        if (showManage) {
            ManageTracksSheet(
                title = "Manage playlist",
                removeDescription = "Remove from playlist",
                tracks = songs,
                downloadStates = downloadStates,
                onDismiss = { showManage = false },
                onReorder = { viewModel.setTracks(it) },
                onRemove = { viewModel.removeTrack(it.isrc) },
                onDownloadAll = { downloads.download(streamed, label = name) },
                onCancelDownloads = { downloads.cancel(streamedIsrcs) },
                onRemoveDownloads = { downloads.remove(streamedIsrcs) },
            )
        }

        if (confirmDelete) {
            ConfirmDialog(
                title = "Delete playlist?",
                message = "\u201C$name\u201D will be deleted." + if (streamedDownloaded > 0) " Its downloaded songs will be removed from this device too." else "",
                confirmLabel = "Delete",
                danger = true,
                onConfirm = {
                    confirmDelete = false
                    if (streamedDownloaded > 0 || downloadUi.status == PlaylistDownloadStatus.Active) downloads.remove(streamedIsrcs)
                    viewModel.delete(onDone = onBack)
                },
                onDismiss = { confirmDelete = false },
            )
        }

        if (confirmRemoveDownloads) {
            ConfirmDialog(
                title = "Remove downloads?",
                message = "The songs stay in the playlist, but won't play offline until you download them again.",
                confirmLabel = "Remove",
                danger = true,
                onConfirm = {
                    confirmRemoveDownloads = false
                    downloads.remove(streamedIsrcs)
                },
                onDismiss = { confirmRemoveDownloads = false },
            )
        }
    }
}

@Composable
private fun TopIcon(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).then(if (enabled) Modifier.pressScale(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = ink(if (enabled) 0.85f else 0.3f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onFocus: (Boolean) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val textStyle = MaterialTheme.typography.bodyLarge
    Row(
        modifier.fillMaxWidth().height(42.dp).clip(PillShape).background(ink(0.08f)).padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Bold.MagnifyingGlass, null, tint = ink(0.6f), modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search in playlist", style = textStyle, color = ink(0.4f), maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = textStyle.copy(color = ink()),
                cursorBrush = SolidColor(ink()),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onDone() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) },
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(30.dp).pressScale(onClick = { onChange("") }), contentAlignment = Alignment.Center) {
                Icon(PhosphorIcons.Bold.X, "Clear search", tint = ink(0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    title: String,
    coverUrl: String?,
    tags: List<String>,
    songCount: Int,
    artistCount: Int,
    statusLine: String?,
    downloadUi: PlaylistDownloadUi,
    offline: Boolean,
    onDownload: () -> Unit,
    onMore: (() -> Unit)?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(0.62f).aspectRatio(1f)) {
            if (coverUrl.isNullOrBlank()) {
                Box(Modifier.fillMaxSize().clip(HeaderCoverShape).background(ink(0.07f)), contentAlignment = Alignment.Center) {
                    Icon(PhosphorIcons.Bold.VinylRecord, null, tint = ink(0.6f), modifier = Modifier.size(56.dp))
                }
            } else {
                CoverImage(
                    remember(coverUrl) { resolveCoverUrl(coverUrl, 600) },
                    Modifier
                        .fillMaxSize()
                        .shadow(22.dp, HeaderCoverShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                        .clip(HeaderCoverShape),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.displayMedium,
            color = ink(),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp).padding(top = 24.dp),
        )
        Text(
            if (songCount == 0) "Empty" else "${plural(songCount, "song")} \u00B7 ${plural(artistCount, "artist")}",
            style = MaterialTheme.typography.bodyMedium,
            color = ink(0.6f),
            modifier = Modifier.padding(top = 4.dp),
        )
        if (statusLine != null) {
            Text(statusLine, style = MaterialTheme.typography.bodyMedium, color = ink(0.6f), modifier = Modifier.padding(top = 2.dp))
        }
        if (tags.isNotEmpty()) TagsRow(tags, Modifier.padding(horizontal = 24.dp).padding(top = 14.dp))
        if (offline) AvailableOfflineLine(Modifier.padding(top = if (tags.isNotEmpty()) 10.dp else 8.dp))
        if (songCount > 0 || onMore != null) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (songCount > 0) {
                    ActionPill("Play", PhosphorIcons.Fill.Play, filled = true, onClick = onPlay)
                    Spacer(Modifier.width(10.dp))
                    ActionPill("Shuffle", PhosphorIcons.Bold.Shuffle, filled = false, onClick = onShuffle)
                }
                Spacer(Modifier.weight(1f))
                if (songCount > 0) {
                    DownloadButton(
                        downloadUi,
                        onDownload,
                        size = 40.dp,
                        glyphSize = 22.dp,
                        idleIcon = PhosphorIcons.Bold.BoxArrowDown,
                        doneIcon = PhosphorIcons.Fill.Archive,
                    )
                }
                if (onMore != null) {
                    Box(Modifier.size(40.dp).pressScale(onClick = onMore), contentAlignment = Alignment.Center) {
                        Icon(PhosphorIcons.Bold.DotsThreeVertical, "Playlist options", tint = ink(0.85f), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(label: String, icon: ImageVector, filled: Boolean, onClick: () -> Unit) {
    val isLight = LocalCoverPalette.current.isLight
    val content = if (filled) (if (isLight) Color.White else Color.Black) else ink()
    Row(
        Modifier
            .pressScale(onClick = onClick)
            .background(if (filled) ink() else ink(0.10f), PillShape)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = content, modifier = Modifier.padding(start = 8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.rowPress(onClick: () -> Unit, onLongClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(90), label = "row-press")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
        .focusRing(RoundedCornerShape(12.dp))
        .combinedClickable(interactionSource = source, indication = null, onClick = onClick, onLongClick = onLongClick)
}

@Composable
private fun PlaylistTrackRow(
    track: Track,
    isPlaying: Boolean,
    accent: Color,
    downloadState: DownloadState?,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onArtist: (String) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .rowPress(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgedCover(local = track.isLocal) {
            CoverImage(remember(track.albumArtUrl) { resolveCoverUrl(track.albumArtUrl, 160) }, Modifier.size(54.dp).softCover())
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPlaying) NowPlayingBars(color = accent, modifier = Modifier.padding(end = 8.dp))
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (isPlaying) accent else ink(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            ArtistLine(track.artist, track.album, onArtist, stackSize = 18.dp, modifier = Modifier.padding(vertical = 1.dp)) { m ->
                Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = ink(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = m)
            }
            Box(Modifier.padding(top = 5.dp).height(2.dp)) { DownloadProgressLine(downloadState) }
        }
        TrackDownloadButton(downloadState, onDownload = onDownload, onCancel = onCancelDownload, onDone = onMore, glyph = 20.dp)
        Box(Modifier.size(40.dp).pressScale(onClick = onMore), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Bold.DotsThreeVertical, "More options", tint = ink(0.7f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TrackOptionsPanel(
    track: Track,
    isFavorite: Boolean,
    downloadState: DownloadState?,
    local: Boolean,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onGoToArtist: () -> Unit,
    onShare: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val base = LocalCoverPalette.current.base
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(PanelShape)
                .background(base)
                .background(ink(0.08f))
                .padding(vertical = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(resolveCoverUrl(track.albumArtUrl, 160), Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = ink(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(ink(0.10f)))
            PanelAction(PhosphorIcons.Fill.Play, "Play", true) {
                onPlay()
                onDismiss()
            }
            if (!local) {
                when (downloadState) {
                    DownloadState.Done -> PanelAction(PhosphorIcons.Fill.CheckCircle, "Remove download", true) {
                        onRemoveDownload()
                        onDismiss()
                    }

                    null, is DownloadState.Failed -> PanelAction(PhosphorIcons.Bold.ArrowCircleDown, "Download", true) {
                        onDownload()
                        onDismiss()
                    }

                    else -> Unit
                }
            }
            PanelAction(
                if (isFavorite) PhosphorIcons.Fill.Heart else PhosphorIcons.Bold.Heart,
                if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                true,
            ) {
                onToggleFavorite()
                onDismiss()
            }
            PanelAction(PhosphorIcons.Bold.User, if (splitArtists(track.artist).size > 1) "Go to artist\u2026" else "Go to artist", true) {
                onGoToArtist()
                onDismiss()
            }
            PanelAction(PhosphorIcons.Bold.ShareNetwork, "Share", true) {
                onShare()
                onDismiss()
            }
            PanelAction(PhosphorIcons.Bold.ArrowUp, "Move up", canMoveUp) {
                onMoveUp()
                onDismiss()
            }
            PanelAction(PhosphorIcons.Bold.ArrowDown, "Move down", canMoveDown) {
                onMoveDown()
                onDismiss()
            }
            PanelAction(PhosphorIcons.Bold.Trash, "Remove from playlist", true) {
                onRemove()
                onDismiss()
            }
        }
    }
}

@Composable
private fun PanelAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.3f
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.pressScale(onClick = onClick, scaleDown = 0.98f) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink(0.85f * alpha), modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = ink(alpha), modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(onClick = onClick, scaleDown = 0.98f)
            .padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger) DangerColor else sheetInk(0.85f), modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (danger) DangerColor else sheetInk(), modifier = Modifier.padding(start = 18.dp))
    }
}

@Composable
private fun SheetButton(label: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val content = if (filled) LocalCoverPalette.current.base else ink()
    Box(
        Modifier
            .then(if (enabled) Modifier.pressScale(onClick = onClick) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .background(if (filled) sheetInk() else sheetInk(0.10f), PillShape)
            .padding(horizontal = 30.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = content)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    danger: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val base = LocalCoverPalette.current.base
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(PanelShape)
                .background(base)
                .background(ink(0.08f))
                .padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = ink())
            Text(message, style = MaterialTheme.typography.bodyMedium, color = ink(0.7f), modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.titleMedium,
                    color = ink(0.8f),
                    modifier = Modifier.pressScale(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 10.dp),
                )
                Text(
                    confirmLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (danger) DangerColor else ink(),
                    modifier = Modifier.pressScale(onClick = onConfirm).padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
