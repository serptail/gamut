package com.gamut.feature.library

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.MusicNote
import com.adamglin.phosphoricons.bold.Plus
import com.adamglin.phosphoricons.bold.VinylRecord
import com.adamglin.phosphoricons.bold.X
import com.adamglin.phosphoricons.fill.Heart
import com.gamut.core.designsystem.CoverPalette
import com.gamut.core.designsystem.CoverTheme
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.LocalFocusLive
import com.gamut.core.designsystem.LocalNotifier
import com.gamut.core.designsystem.TransparentStatusBar
import com.gamut.core.designsystem.focusRing
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.isWideLayout
import com.gamut.core.designsystem.pressScale
import com.gamut.core.designsystem.softResize
import com.gamut.core.model.Track

private val PreviewShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(50)
private val PanelShape = RoundedCornerShape(14.dp)
private const val MAX_NAME_LENGTH = 60

@Composable
fun CreatePlaylistScreen(
    viewModel: LibraryViewModel,
    selectedTrack: Track?,
    onBack: () -> Unit,
    active: Boolean = true,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val notifier = LocalNotifier.current
    val focus = LocalFocusManager.current
    val palette = LocalCoverPalette.current
    if (active) TransparentStatusBar(darkIcons = palette.isLight)
    LaunchedEffect(active) { if (!active) focus.clearFocus() }

    val defaultName = remember { "My playlist #${playlists.size + 1}" }
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(defaultName, TextRange(0, defaultName.length)))
    }
    var coverUri by rememberSaveable { mutableStateOf<String?>(null) }
    val pickCover = rememberCoverPicker { coverUri = it }
    val tags: SnapshotStateList<String> = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf() }
    var addPicked by rememberSaveable { mutableStateOf(false) }
    var copyLiked by rememberSaveable { mutableStateOf(false) }

    val chosen: List<Track> = remember(addPicked, copyLiked, selectedTrack, favorites) {
        val base = if (copyLiked) favorites else emptyList()
        val picked = selectedTrack.takeIf { addPicked }
        (if (picked != null) base.filter { it.isrc != picked.isrc } + picked else base).distinctBy { it.isrc }
    }
    val canCreate = name.text.isNotBlank()
    val create = {
        val finalName = name.text.trim()
        if (finalName.isNotEmpty()) {
            viewModel.createPlaylist(finalName, chosen, tags.toList(), coverUri)
            notifier.success("Playlist \u201c$finalName\u201d created")
            onBack()
        }
    }

    CoverTheme(palette) {
        Column(
            Modifier
                .fillMaxSize()
                .background(palette.base)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).pressScale(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(PhosphorIcons.Bold.X, "Close", tint = ink(0.85f), modifier = Modifier.size(22.dp))
                }
                Text("New playlist", style = MaterialTheme.typography.titleLarge, color = ink(), modifier = Modifier.padding(start = 8.dp))
            }

            val wide = isWideLayout()
            val leftContent: @Composable () -> Unit = {
                Spacer(Modifier.height(20.dp))
                CoverPreview(coverUri, onPick = pickCover, onClear = { coverUri = null })
                Box(Modifier.fillMaxWidth().softResize(), contentAlignment = Alignment.Center) {
                    if (chosen.isNotEmpty()) {
                        Text(
                            songsLabel(chosen.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink(0.6f),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }

                NameField(
                    value = name,
                    onChange = { if (it.text.length <= MAX_NAME_LENGTH) name = it },
                    onDone = { focus.clearFocus() },
                )
            }
            val rightContent: @Composable () -> Unit = {
                Text(
                    "Tags",
                    style = MaterialTheme.typography.titleLarge,
                    color = ink(),
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp),
                )
                TagsEditor(tags = tags, onChange = {
                    tags.clear()
                    tags.addAll(it)
                })

                if (selectedTrack != null || favorites.isNotEmpty()) {
                    Text(
                        "Start with",
                        style = MaterialTheme.typography.titleLarge,
                        color = ink(),
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 12.dp),
                    )
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (selectedTrack != null) {
                            OptionRow(
                                icon = PhosphorIcons.Bold.MusicNote,
                                title = "Add this song",
                                subtitle = "${selectedTrack.title} \u00B7 ${selectedTrack.artist}",
                                checked = addPicked,
                                onChange = { addPicked = it },
                            )
                        }
                        if (favorites.isNotEmpty()) {
                            OptionRow(
                                icon = PhosphorIcons.Fill.Heart,
                                title = "Copy your Liked songs",
                                subtitle = songsLabel(favorites.size),
                                checked = copyLiked,
                                onChange = { copyLiked = it },
                            )
                        }
                    }
                }
                Text(
                    "You can add more songs from Discover or the player at any time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink(0.45f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 28.dp),
                )
            }

            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { leftContent() }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(20.dp))
                        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { rightContent() }
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        leftContent()
                        rightContent()
                    }
                }
            }

            val buttonBg by animateColorAsState(if (canCreate) ink() else ink(0.12f), tween(180), label = "create-bg")
            val buttonText by animateColorAsState(if (canCreate) palette.base else ink(0.3f), tween(180), label = "create-text")
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .height(54.dp)
                        .then(if (canCreate) Modifier.pressScale(onClick = create) else Modifier)
                        .background(buttonBg, PillShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Create playlist",
                        style = MaterialTheme.typography.titleMedium,
                        color = buttonText,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverPreview(url: String?, onPick: () -> Unit, onClear: () -> Unit) {
    Box(Modifier.size(168.dp)) {
        Crossfade(targetState = url, animationSpec = tween(220), label = "playlist-preview") { u ->
            if (u.isNullOrBlank()) {
                Box(
                    Modifier.fillMaxSize().pressScale(onClick = onPick).clip(PreviewShape).background(ink(0.07f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(PhosphorIcons.Bold.Plus, "Add cover", tint = ink(0.6f), modifier = Modifier.size(44.dp))
                }
            } else {
                CoverImage(
                    resolveCoverUrl(u, 500),
                    Modifier
                        .fillMaxSize()
                        .pressScale(onClick = onPick)
                        .shadow(22.dp, PreviewShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                        .clip(PreviewShape),
                )
            }
        }
        if (!url.isNullOrBlank()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(30.dp)
                    .pressScale(onClick = onClear)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PhosphorIcons.Bold.X, "Remove cover", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun NameField(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, onDone: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val style = MaterialTheme.typography.headlineLarge
    val remote = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val live = LocalFocusLive.current
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (editing) {
            runCatching { fieldFocus.requestFocus() }
            keyboard?.show()
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 28.dp)
            .then(if (remote && !editing && !focused) Modifier.pressScale(onClick = { editing = true }, scaleDown = 0.99f) else Modifier),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = style.copy(color = ink(), textAlign = TextAlign.Center),
            cursorBrush = SolidColor(ink()),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties { canFocus = (!remote || editing) && live }
                .focusRequester(fieldFocus)
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused) editing = false
                },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (value.text.isEmpty()) {
                        Text("Playlist name", style = style, color = ink(0.35f), textAlign = TextAlign.Center)
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        val lineHeight by animateDpAsState(if (focused) 2.dp else 1.dp, tween(160), label = "name-line-h")
        val lineColor by animateColorAsState(if (focused) ink() else ink(0.25f), tween(160), label = "name-line-c")
        Box(Modifier.fillMaxWidth().height(lineHeight).background(lineColor))
    }
}

@Composable
private fun OptionRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(ink(0.06f))
            .focusRing(PanelShape)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink(0.8f), modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ink())
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ink(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FlatSwitch(checked)
    }
}

@Composable
private fun FlatSwitch(checked: Boolean) {
    val thumbX by animateDpAsState(if (checked) 20.dp else 2.dp, tween(160), label = "switch-x")
    val track by animateColorAsState(if (checked) ink() else ink(0.18f), tween(160), label = "switch-track")
    val base = LocalCoverPalette.current.base
    val thumb by animateColorAsState(if (checked) base else ink(0.8f), tween(160), label = "switch-thumb")
    Box(Modifier.size(width = 46.dp, height = 28.dp).background(track, PillShape)) {
        Box(Modifier.align(Alignment.CenterStart).offset(x = thumbX).size(24.dp).background(thumb, CircleShape))
    }
}

private fun songsLabel(n: Int) = if (n == 1) "1 song" else "$n songs"
