package com.gamut.feature.library

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.ArrowLeft
import com.adamglin.phosphoricons.bold.ArrowsDownUp
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.bold.Circle
import com.adamglin.phosphoricons.bold.DeviceMobileCamera
import com.adamglin.phosphoricons.bold.DotsThreeVertical
import com.adamglin.phosphoricons.bold.FolderOpen
import com.adamglin.phosphoricons.bold.Globe
import com.adamglin.phosphoricons.bold.Heart
import com.adamglin.phosphoricons.bold.Image
import com.adamglin.phosphoricons.bold.Link
import com.adamglin.phosphoricons.bold.MagnifyingGlass
import com.adamglin.phosphoricons.bold.PencilSimple
import com.adamglin.phosphoricons.bold.PlusCircle
import com.adamglin.phosphoricons.bold.ReadCvLogo
import com.adamglin.phosphoricons.bold.Scan
import com.adamglin.phosphoricons.bold.X
import com.adamglin.phosphoricons.fill.CheckCircle
import com.adamglin.phosphoricons.fill.DeviceMobileCamera
import com.adamglin.phosphoricons.fill.Heart
import com.adamglin.phosphoricons.fill.Play
import com.gamut.core.designsystem.CoverTheme
import com.gamut.core.designsystem.GamutSheet
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.LocalNavSettled
import com.gamut.core.designsystem.LocalNotifier
import com.gamut.core.designsystem.NowPlayingBars
import com.gamut.core.designsystem.R as DesignR
import com.gamut.core.designsystem.TransparentStatusBar
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.isWideLayout
import com.gamut.core.designsystem.pressScale
import com.gamut.core.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun deviceBrush(): Brush {
    val accent = MaterialTheme.colorScheme.primary
    return Brush.linearGradient(listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.55f)))
}

@Composable
internal fun DeviceTile(
    size: Dp?,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    background: Boolean = true,
    raised: Boolean = false,
) {
    val base = if (size != null) modifier.size(size) else modifier
    val surface = when {
        raised -> base.softCover(shape).background(flatCoverFill())
        background -> base.clip(shape).background(ink(0.10f))
        else -> base.clip(shape)
    }
    Box(surface, contentAlignment = Alignment.Center) {
        Icon(PhosphorIcons.Fill.DeviceMobileCamera, null, tint = ink(0.9f), modifier = Modifier.size(if (size != null) size * 0.48f else 34.dp))
    }
}

@Composable
internal fun BoxScopeDeviceBadge(modifier: Modifier = Modifier) {
    Icon(PhosphorIcons.Fill.DeviceMobileCamera, "On this device", tint = Color.White, modifier = modifier.size(15.dp))
}

@Composable
internal fun OnDeviceMark(modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Icon(PhosphorIcons.Bold.DeviceMobileCamera, "Stored on this device", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun DeviceTag(modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Box(
        modifier
            .size(size)
            .shadow(2.dp, CircleShape, clip = false)
            .background(LocalCoverPalette.current.base, CircleShape)
            .border(1.dp, ink(0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(PhosphorIcons.Fill.DeviceMobileCamera, "On this device", tint = ink(), modifier = Modifier.size(size * 0.58f))
    }
}

@Composable
internal fun BadgedCover(local: Boolean, tagSize: Dp = 20.dp, cover: @Composable () -> Unit) {
    Box {
        cover()
        if (local) DeviceTag(Modifier.align(Alignment.TopEnd).offset(x = tagSize / 2, y = -tagSize / 2), tagSize)
    }
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

private enum class SongSheetMode { Actions, Playlists, EditChoice, NotFound }

private enum class EditMode { Auto, Manual }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicScreen(
    libraryVm: LibraryViewModel,
    onBack: () -> Unit,
    onTrackSelected: (Track, List<Track>) -> Unit,
    nowPlayingIsrc: String?,
    viewModel: LocalMusicViewModel = viewModel(),
    nowPlaying: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val palette = LocalCoverPalette.current
    val notifier = LocalNotifier.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val settled = LocalNavSettled.current
    TransparentStatusBar(darkIcons = palette.isLight)

    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val denied by viewModel.denied.collectAsStateWithLifecycle()
    val songs by viewModel.sorted.collectAsStateWithLifecycle()
    val formats by viewModel.formats.collectAsStateWithLifecycle()
    val format by viewModel.format.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val deepScanned by viewModel.deepScanned.collectAsStateWithLifecycle()
    val favoriteIsrcs by libraryVm.favoriteIsrcs.collectAsStateWithLifecycle()
    val playlists by libraryVm.playlists.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var sheetSong by remember { mutableStateOf<LocalSong?>(null) }
    var sheetMode by remember { mutableStateOf(SongSheetMode.Actions) }
    var busy by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var editSong by remember { mutableStateOf<LocalSong?>(null) }
    var editMode by remember { mutableStateOf(EditMode.Manual) }
    val autoUi by viewModel.auto.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermission(granted)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val r = viewModel.importPicked(uri)) {
                    is ImportResult.Added -> notifier.success("Added \u201C${r.title}\u201D to this device")
                    is ImportResult.Failed -> notifier.info(r.reason)
                }
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermission() }

    val visible = remember(songs, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            songs
        } else {
            songs.filter {
                val t = it.track
                t.title.lowercase().contains(q) || t.artist.lowercase().contains(q) || t.album.orEmpty().lowercase().contains(q)
            }
        }
    }
    val visibleTracks = remember(visible) { visible.map { it.track } }
    val searching = query.isNotBlank()

    fun closeSheet() {
        sheetSong = null
        busy = false
        pendingSave = null
    }

    fun addToPlaylist(song: LocalSong, id: Long, name: String) {
        if (busy) return
        busy = true
        scope.launch {
            val r = viewModel.prepareForSave(song.track)
            val save: suspend () -> Unit = {
                libraryVm.addTrack(id, r.track)
                notifier.success("Added to $name")
                closeSheet()
            }
            if (r.found) {
                save()
            } else {
                pendingSave = save
                busy = false
                sheetMode = SongSheetMode.NotFound
            }
        }
    }

    fun toggleFavorite(song: LocalSong, isFavorite: Boolean) {
        if (busy) return
        if (isFavorite) {
            libraryVm.toggleFavorite(song.track)
            notifier.info("Removed from Favorites")
            closeSheet()
            return
        }
        busy = true
        scope.launch {
            val r = viewModel.prepareForSave(song.track)
            val save: suspend () -> Unit = {
                libraryVm.toggleFavorite(r.track)
                notifier.success("Added to Favorites")
                closeSheet()
            }
            if (r.found) {
                save()
            } else {
                pendingSave = save
                busy = false
                sheetMode = SongSheetMode.NotFound
            }
        }
    }

    CoverTheme(palette) {
        val accent = MaterialTheme.colorScheme.primary
        val wide = isWideLayout()
        Column(Modifier.fillMaxSize().statusBarsPadding().then(if (searchFocused) Modifier.imePadding() else Modifier)) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).pressScale(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(PhosphorIcons.Bold.ArrowLeft, "Back", tint = ink(0.85f), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                if (hasPermission && songs.isNotEmpty()) {
                    Text(sort.label, style = MaterialTheme.typography.labelMedium, color = ink(0.55f))
                    Box(
                        Modifier.size(44.dp).pressScale(onClick = {
                            val next = LocalSort.values()[(sort.ordinal + 1) % LocalSort.values().size]
                            viewModel.setSort(next)
                            notifier.info("Sorted by ${next.label.lowercase()}")
                        }),
                        contentAlignment = Alignment.Center,
                    ) { Icon(PhosphorIcons.Bold.ArrowsDownUp, "Sort: ${sort.label}", tint = ink(0.85f), modifier = Modifier.size(22.dp)) }
                }
            }

            if (settled && hasPermission && songs.isNotEmpty() && !wide) {
                LocalSearchField(
                    query = query,
                    onChange = { query = it },
                    onFocus = { searchFocused = it },
                    onDone = { focus.clearFocus() },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp),
                )
            }

            val headerSlot: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DeviceTile(88.dp, RoundedCornerShape(22.dp), background = false)
                        Column(Modifier.weight(1f).padding(start = 18.dp)) {
                            Text("On this device", style = MaterialTheme.typography.headlineMedium, color = ink(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                when {
                                    !hasPermission -> "Allow access to see your music"
                                    songs.isEmpty() -> "No songs found yet"
                                    else -> plural(songs.size, "song")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = ink(0.6f),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    if (hasPermission) {
                        ScanCard(
                            scan = scan,
                            deepScanned = deepScanned,
                            onScan = viewModel::deepScan,
                            onBrowse = { importPicker.launch(arrayOf("audio/*", "application/ogg")) },
                            modifier = Modifier.padding(top = 18.dp),
                        )
                    }
                }
            }
            val listBody: LazyListScope.(Boolean) -> Unit = { showHeader ->
                if (showHeader && !searching) {
                    item(key = "header") { headerSlot() }
                }

                if (hasPermission && formats.size > 1) {
                    item(key = "formats") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
                            item { FormatChip("All", selected = format == null) { viewModel.setFormat(null) } }
                            items(formats, key = { it }) { f -> FormatChip(f, selected = format == f) { viewModel.setFormat(if (format == f) null else f) } }
                        }
                    }
                }

                if (!hasPermission) {
                    item(key = "permission") {
                        EmptyStateCard(
                            title = if (denied) "Music access is off" else "Play any music on your device",
                            subtitle = if (denied) {
                                "Turn on “Music and audio” for this app in system settings."
                            } else {
                                "Allow access to your audio files. They stay on your device and are never uploaded."
                            },
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            buttonLabel = if (denied) "Open settings" else "Allow access",
                            onButton = {
                                if (denied) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                    )
                                } else {
                                    permissionLauncher.launch(LOCAL_AUDIO_PERMISSION)
                                }
                            },
                        )
                    }
                } else if (songs.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "No songs yet. Tap “Scan” to search every folder, or “Browse” to pick a song.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink(0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
                        )
                    }
                } else if (searching && visible.isEmpty()) {
                    item(key = "no-match") {
                        Text(
                            "No songs match “${query.trim()}”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink(0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                        )
                    }
                }

                itemsIndexed(visible, key = { _, s -> s.track.isrc }, contentType = { _, _ -> "local-song" }) { _, song ->
                    LocalSongRow(
                        song = song,
                        isPlaying = song.track.isrc == nowPlayingIsrc,
                        accent = accent,
                        isFavorite = song.track.isrc in favoriteIsrcs,
                        onClick = { onTrackSelected(song.track, visibleTracks) },
                        onMore = {
                            sheetSong = song
                            sheetMode = SongSheetMode.Actions
                            busy = false
                        },
                    )
                }
                item(key = "nav-space") { Box(Modifier.navigationBarsPadding()) }
            }
            if (settled && wide) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.weight(0.4f).fillMaxHeight().verticalScroll(rememberScrollState())) { headerSlot() }
                    Column(Modifier.weight(0.6f).fillMaxHeight()) {
                        if (hasPermission && songs.isNotEmpty()) {
                            LocalSearchField(
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

        sheetSong?.let { song ->
            val state = rememberModalBottomSheetState()
            val isFavorite = song.track.isrc in favoriteIsrcs
            GamutSheet(
                onDismiss = { closeSheet() },
                sheetState = state,
                dragHandle = { BottomSheetDefaults.DragHandle(color = ink(0.3f)) },
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BadgedCover(local = true) {
                            CoverImage(song.track.albumArtUrl.orEmpty(), Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
                        }
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(song.track.title, style = MaterialTheme.typography.titleMedium, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.track.artist, style = MaterialTheme.typography.bodyMedium, color = ink(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(ink(0.10f)))
                    if (busy) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Text("Getting song info…", style = MaterialTheme.typography.titleMedium, color = ink(), modifier = Modifier.padding(start = 16.dp))
                        }
                    } else if (sheetMode == SongSheetMode.Actions) {
                        SongAction(PhosphorIcons.Fill.Play, "Play now") {
                            onTrackSelected(song.track, visibleTracks)
                            closeSheet()
                        }
                        SongAction(PhosphorIcons.Bold.PlusCircle, "Add to playlist…") { sheetMode = SongSheetMode.Playlists }
                        SongAction(
                            if (isFavorite) PhosphorIcons.Fill.Heart else PhosphorIcons.Bold.Heart,
                            if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                        ) { toggleFavorite(song, isFavorite) }
                        SongAction(PhosphorIcons.Bold.ReadCvLogo, "Edit metadata") { sheetMode = SongSheetMode.EditChoice }
                        if (song.needsInfo) {
                            Text(
                                "Missing artist or album? It’s filled in when you save the song.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ink(0.5f),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                            )
                        }
                    } else if (sheetMode == SongSheetMode.EditChoice) {
                        Row(
                            Modifier.fillMaxWidth().pressScale(onClick = { sheetMode = SongSheetMode.Actions }, scaleDown = 0.98f).padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(PhosphorIcons.Bold.ArrowLeft, "Back", tint = ink(0.7f), modifier = Modifier.size(18.dp))
                            Text("Edit metadata", style = MaterialTheme.typography.labelLarge, color = ink(0.6f), modifier = Modifier.padding(start = 12.dp))
                        }
                        SongAction(PhosphorIcons.Bold.Globe, "Auto fetch", subtitle = "Find the cover, name and artist online") {
                            editMode = EditMode.Auto
                            editSong = song
                            closeSheet()
                        }
                        SongAction(PhosphorIcons.Bold.PencilSimple, "Manual edit", subtitle = "Change the cover, name and artist yourself") {
                            editMode = EditMode.Manual
                            editSong = song
                            closeSheet()
                        }
                    } else if (sheetMode == SongSheetMode.NotFound) {
                        Text(
                            "Couldn’t find info for this song",
                            style = MaterialTheme.typography.titleMedium,
                            color = ink(),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                        Text(
                            "Nothing reliable matched online, so it has no artist yet. Fill it in yourself, or save it as it is.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink(0.6f),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                        SongAction(PhosphorIcons.Bold.PencilSimple, "Edit manually") {
                            val s = song
                            closeSheet()
                            editMode = EditMode.Manual
                            editSong = s
                        }
                        SongAction(PhosphorIcons.Bold.Check, "Save anyway") {
                            busy = true
                            scope.launch { pendingSave?.invoke() }
                        }
                    } else {
                        Text("Choose a playlist", style = MaterialTheme.typography.labelLarge, color = ink(0.6f), modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                        if (playlists.isEmpty()) {
                            Text("You have no playlists yet. Create one from the Library tab.", style = MaterialTheme.typography.bodyMedium, color = ink(0.6f), modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                        }
                        playlists.forEach { p -> SongAction(PhosphorIcons.Bold.PlusCircle, p.name) { addToPlaylist(song, p.id, p.name) } }
                    }
                }
            }
        }

        editSong?.let { song ->
            when (editMode) {
                EditMode.Manual -> ManualEditSheet(
                    song = song,
                    onDismiss = { editSong = null },
                    onSave = { title, artist, cover ->
                        viewModel.saveEdit(song.track, title, artist, cover)
                        notifier.success("Song info updated")
                        editSong = null
                    },
                )

                EditMode.Auto -> AutoFetchSheet(
                    song = song,
                    ui = autoUi,
                    onStart = { viewModel.startAutoFetch(song.track) },
                    onStop = { viewModel.stopAutoFetch() },
                    onDismiss = { editSong = null },
                    onApply = { c ->
                        viewModel.applyCandidate(song.track, c)
                        notifier.success("Song info updated")
                        editSong = null
                    },
                    onManual = { editMode = EditMode.Manual },
                )
            }
        }
    }
}

private val CodeNextFont = FontFamily(Font(DesignR.font.codenext_extrabold, FontWeight.ExtraBold))

private val ScanPlaces = listOf("Music", "Downloads", "Documents", "WhatsApp Audio", "Recordings", "Other folders")

@Composable
private fun ScanCard(scan: ScanUi, deepScanned: Boolean, onScan: () -> Unit, onBrowse: () -> Unit, modifier: Modifier = Modifier) {
    val running = scan.running
    val onFill = if (LocalCoverPalette.current.isLight) Color.White else Color.Black
    val shape = RoundedCornerShape(16.dp)
    val lineColor = onFill.copy(alpha = 0.6f)

    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val breathe by transition.animateFloat(
        0.45f,
        1f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val motion by animateFloatAsState(if (running) 1f else 0f, tween(450), label = "motion")

    var place by remember { mutableIntStateOf(0) }
    LaunchedEffect(running) {
        place = 0
        while (running) {
            delay(1100)
            place = (place + 1) % ScanPlaces.size
        }
    }
    val seenShown by animateIntAsState(scan.seen, tween(500), label = "seen")

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(ink())
                    .then(if (running) Modifier else Modifier.pressScale(onClick = onScan, scaleDown = 0.98f))
                    .drawWithContent {
                        drawContent()
                        if (motion > 0.01f) {
                            val h = 2.dp.toPx()
                            val w = size.width * 0.35f
                            val x = -w + (size.width + w) * sweep
                            drawRect(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, lineColor.copy(alpha = lineColor.alpha * motion), Color.Transparent),
                                    startX = x,
                                    endX = x + w,
                                ),
                                topLeft = Offset(x.coerceAtLeast(0f), size.height - h),
                                size = Size((x + w).coerceAtMost(size.width) - x.coerceAtLeast(0f), h),
                            )
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    PhosphorIcons.Bold.Scan,
                    null,
                    tint = onFill,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { alpha = 1f - (1f - breathe) * motion },
                )
                Crossfade(if (running) "Scanning…" else "Scan", animationSpec = tween(300), label = "title") {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = CodeNextFont),
                        color = onFill,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            Row(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(ink(0.12f))
                    .pressScale(onClick = onBrowse, scaleDown = 0.98f)
                    .padding(horizontal = 14.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(PhosphorIcons.Bold.FolderOpen, null, tint = ink(), modifier = Modifier.size(22.dp))
                Text(
                    "Browse",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = CodeNextFont),
                    color = ink(),
                    maxLines = 1,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        Crossfade(
            when {
                running -> "Looking in ${ScanPlaces[place]}" + if (seenShown > 0) " · $seenShown files checked" else "…"
                scan.message != null -> scan.message
                deepScanned -> "Searched every folder for all audio formats"
                else -> "Scan searches every folder, or browse to add one song"
            },
            animationSpec = tween(350),
            label = "subtitle",
        ) {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = ink(0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
        }
    }
}

@Composable
private fun FormatChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val light = LocalCoverPalette.current.isLight
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) (if (light) Color.White else Color.Black) else ink(),
        modifier = Modifier
            .pressScale(onClick = onClick)
            .background(if (selected) ink(0.95f) else ink(0.12f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

@Composable
private fun SongAction(icon: ImageVector, label: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressScale(onClick = onClick, scaleDown = 0.98f).padding(horizontal = 24.dp, vertical = if (subtitle != null) 12.dp else 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink(0.85f), modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 18.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ink(0.55f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualEditSheet(
    song: LocalSong,
    onDismiss: () -> Unit,
    onSave: suspend (title: String, artist: String, cover: CoverEdit) -> Unit,
) {
    val notifier = LocalNotifier.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(song.track.title) }
    var artist by remember { mutableStateOf(song.track.artist.takeIf { it != UNKNOWN_ARTIST }.orEmpty()) }
    var cover by remember { mutableStateOf<CoverEdit>(CoverEdit.Keep) }
    var linkOpen by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            cover = CoverEdit.Photo(uri)
            linkOpen = false
        }
    }
    val preview = when (val c = cover) {
        CoverEdit.Keep -> song.track.albumArtUrl.orEmpty()
        is CoverEdit.Url -> c.url
        is CoverEdit.Photo -> c.uri.toString()
    }
    fun applyLink() {
        val u = link.trim()
        if (u.startsWith("http://", true) || u.startsWith("https://", true)) {
            cover = CoverEdit.Url(u)
            focus.clearFocus()
        } else {
            notifier.info("Enter a link that starts with https://")
        }
    }
    val canSave = title.isNotBlank() && !saving
    val light = LocalCoverPalette.current.isLight

    GamutSheet(
        onDismiss = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(color = ink(0.3f)) },
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Edit metadata", style = MaterialTheme.typography.titleLarge, color = ink(), modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))

            Box(Modifier.size(132.dp)) {
                Crossfade(preview, animationSpec = tween(300), label = "cover") { url ->
                    CoverImage(url, Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)))
                }
                BoxScopeDeviceBadge(Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LocalPill("Choose photo", PhosphorIcons.Bold.Image, filled = false) { picker.launch("image/*") }
                LocalPill("Use link", PhosphorIcons.Bold.Link, filled = false) { linkOpen = !linkOpen }
            }
            AnimatedVisibility(linkOpen, enter = fadeIn(tween(250)) + expandVertically(tween(250))) {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
                    EditField(
                        label = "Image link",
                        value = link,
                        onChange = { link = it },
                        placeholder = "https://…",
                        modifier = Modifier.weight(1f),
                        imeAction = ImeAction.Done,
                        onDone = { applyLink() },
                    )
                    Box(
                        Modifier.padding(start = 10.dp).size(50.dp).pressScale(onClick = { applyLink() }).background(ink(0.95f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(PhosphorIcons.Bold.Check, "Use this link", tint = if (light) Color.White else Color.Black, modifier = Modifier.size(22.dp)) }
                }
            }

            EditField("Name", title, { title = it }, "Song name", Modifier.padding(top = 22.dp))
            EditField("Artist", artist, { artist = it }, "Artist", Modifier.padding(top = 14.dp), imeAction = ImeAction.Done, onDone = { focus.clearFocus() })

            Box(
                Modifier.fillMaxWidth().padding(top = 24.dp)
                    .pressScale(onClick = {
                        if (canSave) {
                            saving = true
                            scope.launch { onSave(title, artist, cover) }
                        }
                    }, scaleDown = 0.98f)
                    .background(if (canSave || saving) ink(0.95f) else ink(0.15f), RoundedCornerShape(50))
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (saving) {
                    CircularProgressIndicator(color = if (light) Color.White else Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text("Save", style = MaterialTheme.typography.titleMedium, color = if (canSave) (if (light) Color.White else Color.Black) else ink(0.4f))
                }
            }
        }
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    val textStyle = MaterialTheme.typography.bodyLarge
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ink(0.6f), modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ink(0.08f)).padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (value.isEmpty()) Text(placeholder, style = textStyle, color = ink(0.4f), maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = textStyle.copy(color = ink()),
                cursorBrush = SolidColor(ink()),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val AutoSteps = listOf("Reading file tags…", "Searching online…", "Matching results…")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoFetchSheet(
    song: LocalSong,
    ui: AutoFetchUi,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onApply: suspend (MetaCandidate) -> Unit,
    onManual: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()
    val light = LocalCoverPalette.current.isLight
    var selected by remember { mutableStateOf<MetaCandidate?>(null) }
    var applying by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }

    LaunchedEffect(song.track.isrc) { onStart() }
    DisposableEffect(Unit) { onDispose { onStop() } }
    LaunchedEffect(ui.loading) {
        step = 0
        while (ui.loading) {
            delay(1_300)
            step = (step + 1) % AutoSteps.size
        }
    }

    GamutSheet(
        onDismiss = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(color = ink(0.3f)) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("Auto fetch", style = MaterialTheme.typography.titleLarge, color = ink(), modifier = Modifier.padding(top = 4.dp))
                Text(
                    "For “${song.track.title}”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink(0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 14.dp)) {
                if (ui.loading) {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Crossfade(AutoSteps[step], animationSpec = tween(350), label = "step") {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = ink(0.7f), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    if (ui.results.isEmpty()) repeat(3) { SkeletonRow() }
                }
                ui.results.forEach { c ->
                    FoundRow(c, selected == c) { if (!applying) selected = c }
                }
                if (!ui.loading && ui.results.isEmpty()) {
                    Text(
                        "Nothing reliable found for this song. You can still fill it in yourself.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink(0.6f),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }

            val empty = !ui.loading && ui.results.isEmpty()
            val enabled = selected != null && !applying
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 14.dp)
                    .pressScale(onClick = {
                        if (empty) {
                            onManual()
                        } else {
                            selected?.let { c ->
                                if (!applying) {
                                    applying = true
                                    scope.launch { onApply(c) }
                                }
                            }
                        }
                    }, scaleDown = 0.98f)
                    .background(if (empty || enabled || applying) ink(0.95f) else ink(0.15f), RoundedCornerShape(50))
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (applying) {
                    CircularProgressIndicator(color = if (light) Color.White else Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        when {
                            empty -> "Edit manually"
                            selected == null -> "Select a result"
                            else -> "Use this result"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (empty || enabled) (if (light) Color.White else Color.Black) else ink(0.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FoundRow(c: MetaCandidate, selected: Boolean, onClick: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(shown, enter = fadeIn(tween(400)) + expandVertically(tween(400))) {
        Row(
            Modifier.fillMaxWidth().pressScale(onClick = onClick, scaleDown = 0.98f)
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) ink(0.12f) else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (c.artUrl != null) {
                CoverImage(c.artUrl, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(ink(0.10f)), contentAlignment = Alignment.Center) {
                    Icon(PhosphorIcons.Bold.Image, null, tint = ink(0.4f), modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(c.title, style = MaterialTheme.typography.titleMedium, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(c.artist, c.album).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink(0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    c.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = ink(0.7f),
                    modifier = Modifier.padding(top = 4.dp).background(ink(0.10f), CircleShape).padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
            Crossfade(selected, animationSpec = tween(200), label = "pick") { on ->
                if (on) {
                    Icon(PhosphorIcons.Fill.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                } else {
                    Icon(PhosphorIcons.Bold.Circle, null, tint = ink(0.35f), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SkeletonRow() {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.05f, 0.12f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(ink(a)))
        Column(Modifier.padding(start = 14.dp)) {
            Box(Modifier.width(150.dp).height(13.dp).clip(CircleShape).background(ink(a)))
            Box(Modifier.padding(top = 8.dp).width(100.dp).height(11.dp).clip(CircleShape).background(ink(a)))
        }
    }
}

@Composable
private fun LocalSongRow(
    song: LocalSong,
    isPlaying: Boolean,
    accent: Color,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val track = song.track
    Row(
        Modifier.fillMaxWidth().pressScale(onClick = onClick, scaleDown = 0.98f).padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgedCover(local = true) {
            CoverImage(remember(track.albumArtUrl) { track.albumArtUrl.orEmpty() }, Modifier.size(54.dp).softCover())
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
                if (isFavorite) {
                    Icon(PhosphorIcons.Fill.Heart, "In Favorites", tint = ink(0.6f), modifier = Modifier.padding(start = 6.dp).size(13.dp))
                }
            }
            Row(Modifier.padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(track.artist, track.album).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink(0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    song.format,
                    style = MaterialTheme.typography.labelSmall,
                    color = ink(0.7f),
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp).background(ink(0.10f), CircleShape).padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
        Text(formatDuration(track.durationMs), style = MaterialTheme.typography.labelMedium, color = ink(0.45f), modifier = Modifier.padding(end = 2.dp))
        Box(Modifier.size(40.dp).pressScale(onClick = onMore), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Bold.DotsThreeVertical, "More options", tint = ink(0.7f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LocalPill(label: String, icon: ImageVector, filled: Boolean, onClick: () -> Unit) {
    val isLight = LocalCoverPalette.current.isLight
    val content = if (filled) (if (isLight) Color.White else Color.Black) else ink()
    Row(
        Modifier
            .pressScale(onClick = onClick)
            .background(if (filled) ink() else ink(0.10f), RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = content, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun LocalSearchField(query: String, onChange: (String) -> Unit, onFocus: (Boolean) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val textStyle = MaterialTheme.typography.bodyLarge
    Row(
        modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(50)).background(ink(0.08f)).padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Bold.MagnifyingGlass, null, tint = ink(0.6f), modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search this device", style = textStyle, color = ink(0.4f), maxLines = 1)
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
