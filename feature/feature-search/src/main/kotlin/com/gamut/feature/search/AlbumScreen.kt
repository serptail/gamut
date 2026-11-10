package com.gamut.feature.search

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.SubcomposeAsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.ArrowLeft
import com.adamglin.phosphoricons.bold.DotsThreeVertical
import com.adamglin.phosphoricons.fill.Play
import com.gamut.core.designsystem.AcrylicCircleButton
import com.gamut.core.designsystem.AcrylicHost
import com.gamut.core.designsystem.ArtPlaceholder
import com.gamut.core.designsystem.ArtistLine
import com.gamut.core.designsystem.ArtistPickerSheet
import com.gamut.core.designsystem.CoverTheme
import com.gamut.core.designsystem.HeroListSkeleton
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.LocalNavSettled
import com.gamut.core.designsystem.NowPlayingBars
import com.gamut.core.designsystem.ShareSheet
import com.gamut.core.designsystem.TransparentStatusBar
import com.gamut.core.designsystem.artTileColor
import com.gamut.core.designsystem.focusRing
import com.gamut.core.designsystem.glass
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.isWideLayout
import com.gamut.core.designsystem.pressScale
import com.gamut.core.designsystem.rememberArtistTap
import com.gamut.core.designsystem.splitArtists
import com.gamut.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DiscoverRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<AlbumDetails?>(null)
    val state: StateFlow<AlbumDetails?> = _state.asStateFlow()
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()
    private val _artistImage = MutableStateFlow<String?>(null)
    val artistImage: StateFlow<String?> = _artistImage.asStateFlow()

    init {
        val item = DiscoverItem(
            id = savedStateHandle.get<String>("id").orEmpty(),
            title = savedStateHandle.get<String>("title").orEmpty(),
            subtitle = savedStateHandle.get<String>("subtitle").orEmpty(),
            imageUrl = savedStateHandle.get<String>("art").orEmpty(),
        )
        viewModelScope.launch {
            val album = repository.album(item)
            _state.value = album
            _failed.value = album == null
            if (album != null) _artistImage.value = repository.artistPhotoFor(album.artist, album.title)
        }
    }
}

@Composable
fun AlbumScreen(
    viewModel: AlbumViewModel,
    onBack: () -> Unit,
    onTrackSelected: (Track, List<Track>) -> Unit,
    onTrackArtist: (Track) -> Unit = {},
    favoriteIsrcs: Set<String> = emptySet(),
    onToggleFavorite: (Track) -> Unit = {},
    playlists: List<TrackPlaylistOption> = emptyList(),
    onAddToPlaylist: (Track, String) -> Unit = { _, _ -> },
    nowPlayingIsrc: String? = null,
    nowPlaying: @Composable () -> Unit = {},
) {
    val album by viewModel.state.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val artistImage by viewModel.artistImage.collectAsStateWithLifecycle()
    var artistPickTrack by remember { mutableStateOf<Track?>(null) }
    val palette = LocalCoverPalette.current
    TransparentStatusBar(darkIcons = palette.isLight)
    val settled = LocalNavSettled.current
    CoverTheme(palette) {
        var actionsTrack by remember { mutableStateOf<Track?>(null) }
        var shareTrack by remember { mutableStateOf<Track?>(null) }
        var pickerTrack by remember { mutableStateOf<Track?>(null) }
        val accent = MaterialTheme.colorScheme.primary
        AcrylicHost(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val value = album
                    when {
                        value != null && settled -> {
                            val heroContent: @Composable () -> Unit = {
                                AlbumHero(
                                    album = value,
                                    artistImageUrl = artistImage,
                                    onPlay = { value.tracks.firstOrNull()?.let { onTrackSelected(it, value.tracks) } },
                                    onArtist = { name -> value.tracks.firstOrNull()?.let { onTrackArtist(it.copy(artist = name)) } },
                                )
                            }
                            val trackItems: LazyListScope.() -> Unit = {
                                itemsIndexed(value.tracks, key = { i, t -> "${t.isrc}-$i" }) { index, track ->
                                    AlbumTrackRow(
                                        number = index + 1,
                                        track = track,
                                        showArtist = !track.artist.equals(value.artist, ignoreCase = true),
                                        onArtist = { name -> onTrackArtist(track.copy(artist = name)) },
                                        isPlaying = track.isrc == nowPlayingIsrc,
                                        accent = accent,
                                        onMore = { actionsTrack = it },
                                    ) { picked -> onTrackSelected(picked, value.tracks) }
                                }
                                item(key = "nav-space") { Box(Modifier.navigationBarsPadding()) }
                            }
                            if (isWideLayout()) {
                                Row(Modifier.fillMaxSize()) {
                                    Column(Modifier.weight(0.4f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                                        heroContent()
                                    }
                                    LazyColumn(
                                        Modifier.weight(0.6f).fillMaxHeight().statusBarsPadding(),
                                        contentPadding = PaddingValues(top = 16.dp, end = 12.dp, bottom = 24.dp),
                                    ) { trackItems() }
                                }
                            } else {
                                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                                    item(key = "hero") { heroContent() }
                                    trackItems()
                                }
                            }
                            BackButton(onBack)
                        }

                        value == null && failed -> {
                            BackButton(onBack)
                            Text(
                                "Couldn't load this album. Go back and try again.",
                                color = ink(0.6f),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            )
                        }

                        else -> {
                            HeroListSkeleton()
                            BackButton(onBack)
                        }
                    }
                }
                nowPlaying()
            }
        }

        actionsTrack?.let { t ->
            TrackActionsSheet(
                track = t,
                isFavorite = t.isrc in favoriteIsrcs,
                onDismiss = { actionsTrack = null },
                onShare = {
                    actionsTrack = null
                    shareTrack = t
                },
                onToggleFavorite = { onToggleFavorite(t) },
                onAddToPlaylist = {
                    actionsTrack = null
                    pickerTrack = t
                },
                onGoToArtist = {
                    actionsTrack = null
                    if (splitArtists(t.artist).size > 1) artistPickTrack = t else onTrackArtist(t)
                },
            )
        }
        artistPickTrack?.let { t ->
            ArtistPickerSheet(
                names = splitArtists(t.artist),
                albumTitle = t.album,
                onPick = { name -> onTrackArtist(t.copy(artist = name)) },
                onDismiss = { artistPickTrack = null },
            )
        }
        pickerTrack?.let { t ->
            SavedInSheet(
                track = t,
                playlists = playlists,
                accent = accent,
                showFavorites = false,
                onAddToPlaylist = { id -> onAddToPlaylist(t, id) },
                onDismiss = { pickerTrack = null },
            )
        }
        shareTrack?.let { t -> ShareSheet(title = t.title, artist = t.artist, artUrl = t.albumArtUrl, onDismiss = { shareTrack = null }) }
    }
}

@Composable
private fun AlbumHero(album: AlbumDetails, artistImageUrl: String?, onPlay: () -> Unit, onArtist: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 68.dp),
    ) {
        Box(
            Modifier
                .padding(horizontal = 44.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(14.dp),
                    ambientColor = Color.Black.copy(alpha = 0.30f),
                    spotColor = Color.Black.copy(alpha = 0.45f),
                )
                .clip(RoundedCornerShape(14.dp))
                .background(artTileColor()),
        ) {
            SubcomposeAsyncImage(
                model = album.imageUrl,
                contentDescription = album.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {},
                error = { ArtPlaceholder() },
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    album.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                    ),
                    color = ink(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(8.dp))
                ArtistLine(
                    credit = album.artist,
                    albumTitle = album.title,
                    onArtist = onArtist,
                    stackSize = 24.dp,
                    showSingle = true,
                    singlePhoto = artistImageUrl,
                ) { m ->
                    Text(
                        album.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = ink(0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = m,
                    )
                }
                Spacer(Modifier.size(2.dp))
                Text(
                    if (album.tracks.size == 1) "1 song" else "${album.tracks.size} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = ink(0.5f),
                )
            }
            PlayFab(onClick = onPlay)
        }
    }
}

@Composable
private fun ArtistAvatar(url: String?, artist: String) {
    val modifier = Modifier.size(24.dp).clip(CircleShape)
    if (url != null) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = artist,
            modifier = modifier.border(1.dp, ink(0.15f), CircleShape),
            contentScale = ContentScale.Crop,
            loading = { ArtistAvatarFallback() },
            error = { ArtistAvatarFallback() },
        )
    } else {
        Box(modifier) { ArtistAvatarFallback() }
    }
}

@Composable
private fun PlayFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isLight = LocalCoverPalette.current.isLight
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(ink())
            .pressScale(onClick = onClick, scaleDown = 0.92f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            PhosphorIcons.Fill.Play,
            "Play",
            tint = if (isLight) Color.White else Color.Black,
            modifier = Modifier.padding(start = 2.dp).size(22.dp),
        )
    }
}

@Composable
private fun AlbumTrackRow(
    number: Int,
    track: Track,
    showArtist: Boolean,
    onArtist: (String) -> Unit,
    isPlaying: Boolean,
    accent: Color,
    onMore: (Track) -> Unit,
    onClick: (Track) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = hovered || pressed

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "rowScale",
    )
    val tint by animateColorAsState(
        targetValue = ink(
            if (pressed) {
                0.10f
            } else if (hovered) {
                0.06f
            } else {
                0f
            },
        ),
        animationSpec = tween(140),
        label = "rowTint",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .drawBehind { drawRect(tint) }
            .focusRing(RoundedCornerShape(12.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick(track) }
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.CenterStart) {
            if (active) {
                Icon(PhosphorIcons.Fill.Play, null, tint = ink(), modifier = Modifier.size(14.dp))
            } else if (isPlaying) {
                NowPlayingBars(color = accent)
            } else {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink(0.4f),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (isPlaying) accent else ink(),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showArtist || splitArtists(track.artist).size > 1) {
                ArtistLine(track.artist, track.album, onArtist, stackSize = 16.dp, modifier = Modifier.padding(vertical = 2.dp)) { m ->
                    Text(
                        track.artist,
                        color = ink(0.55f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = m,
                    )
                }
            }
        }
        AlbumMoreButton { onMore(track) }
    }
}

@Composable
private fun AlbumMoreButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .focusRing(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "More options", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(PhosphorIcons.Bold.DotsThreeVertical, "More options", tint = ink(0.5f), modifier = Modifier.size(22.dp)) }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    AcrylicCircleButton(
        onClick = onBack,
        modifier = Modifier.statusBarsPadding().padding(start = 20.dp, top = 12.dp),
        size = 44.dp,
    ) {
        Icon(PhosphorIcons.Bold.ArrowLeft, "Back", tint = ink(), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ArtistAvatarFallback() {
    ArtPlaceholder(iconFraction = 0.6f)
}
