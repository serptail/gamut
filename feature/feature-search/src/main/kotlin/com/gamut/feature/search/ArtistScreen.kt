package com.gamut.feature.search

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.ArrowLeft
import com.adamglin.phosphoricons.bold.DotsThreeVertical
import com.gamut.core.designsystem.ArtPlaceholder
import com.gamut.core.designsystem.ArtistLine
import com.gamut.core.designsystem.ArtistPickerSheet
import com.gamut.core.designsystem.CoverTheme
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.LocalNavSettled
import com.gamut.core.designsystem.NowPlayingBars
import com.gamut.core.designsystem.ShareSheet
import com.gamut.core.designsystem.TrackRowSkeleton
import com.gamut.core.designsystem.TransparentStatusBar
import com.gamut.core.designsystem.VerticalTabs
import com.gamut.core.designsystem.fadeEdges
import com.gamut.core.designsystem.focusRing
import com.gamut.core.designsystem.glass
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.isWideLayout
import com.gamut.core.designsystem.pressScale
import com.gamut.core.designsystem.rememberArtistTap
import com.gamut.core.designsystem.shimmer
import com.gamut.core.designsystem.splitArtists
import com.gamut.core.designsystem.squareArtUrl
import com.gamut.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DiscoverRepository,
) : ViewModel() {
    private val artistId = savedStateHandle.get<String>("artistId")?.toLongOrNull()
    private val _state = MutableStateFlow<ArtistDetails?>(null)
    val state: StateFlow<ArtistDetails?> = _state.asStateFlow()
    private val _followKey = MutableStateFlow<String?>(null)
    val followKey: StateFlow<String?> = _followKey.asStateFlow()
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    init {
        viewModelScope.launch {
            val id = artistId ?: repository.findArtistId(savedStateHandle.get<String>("artistName").orEmpty())
            _followKey.value = id?.let { "deezer:$it" }
            val base = id?.let { repository.artistOverview(it) }
            _state.value = base
            _failed.value = base == null
            if (base == null) return@launch
            launch {
                val similar = repository.similarArtists(base)
                _state.update { it?.copy(similar = similar) }
            }
            launch { loadPortrait(base) }
            if (base.songsLoaded) return@launch
            val full = repository.artistSongs(base)
            _state.update { it?.copy(songs = full.songs, latestSongs = full.latestSongs, songsLoaded = true) }
        }
    }

    private suspend fun loadPortrait(base: ArtistDetails) {
        repeat(4) { attempt ->
            val photo = repository.artistPhoto(base)
            if (photo != null) {
                _state.update { it?.copy(imageUrl = photo) }
                return
            }
            if (attempt < 3) delay(1_500L * (attempt + 1))
        }
    }
}

@Composable
fun ArtistScreen(
    viewModel: ArtistViewModel = viewModel(),
    onBack: () -> Unit,
    onTrackSelected: (Track, List<Track>) -> Unit,
    onAlbumSelected: (DiscoverItem) -> Unit = {},
    onSimilarArtist: (String) -> Unit = {},
    onTrackArtist: (Track) -> Unit = {},
    favoriteIsrcs: Set<String> = emptySet(),
    onToggleFavorite: (Track) -> Unit = {},
    playlists: List<TrackPlaylistOption> = emptyList(),
    onAddToPlaylist: (Track, String) -> Unit = { _, _ -> },
    nowPlayingIsrc: String? = null,
    nowPlaying: @Composable () -> Unit = {},
    followedKeys: Set<String> = emptySet(),
    onToggleFollow: (key: String, name: String, imageUrl: String?) -> Unit = { _, _, _ -> },
) {
    val artist by viewModel.state.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val palette = LocalCoverPalette.current
    TransparentStatusBar(darkIcons = palette.isLight)
    val settled = LocalNavSettled.current
    val wide = isWideLayout()
    CoverTheme(palette) {
        if (artist == null || !settled) {
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                if (!failed) {
                    if (wide) {
                        Row(Modifier.fillMaxSize().padding(top = 78.dp, start = 56.dp, end = 24.dp, bottom = 24.dp)) {
                            Box(Modifier.weight(0.38f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Box(Modifier.widthIn(max = 300.dp).fillMaxWidth(0.92f).aspectRatio(1f).clip(CircleShape).shimmer())
                            }
                            Column(Modifier.weight(0.62f).padding(start = 12.dp)) { repeat(4) { TrackRowSkeleton() } }
                        }
                    } else {
                        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(78.dp))
                            Box(Modifier.fillMaxWidth(0.7f).aspectRatio(1f).clip(CircleShape).shimmer())
                            Spacer(Modifier.height(28.dp))
                            repeat(4) { TrackRowSkeleton() }
                        }
                    }
                }
                Box(Modifier.padding(start = 10.dp, top = 14.dp).size(44.dp).pressScale(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(PhosphorIcons.Bold.ArrowLeft, "Back", tint = ink(), modifier = Modifier.size(22.dp))
                }
                if (failed) {
                    Text("Couldn't load this artist.", color = ink(0.6f), modifier = Modifier.align(Alignment.Center).padding(24.dp))
                }
            }
            return@CoverTheme
        }
        val value = artist ?: return@CoverTheme
        val followKey by viewModel.followKey.collectAsStateWithLifecycle()
        val notifier = com.gamut.core.designsystem.LocalNotifier.current
        val following = followKey != null && followKey in followedKeys
        val onFollowClick: () -> Unit = {
            followKey?.let { key ->
                onToggleFollow(key, value.name, value.imageUrl)
                if (following) notifier.info("Unfollowed ${value.name}") else notifier.success("Following ${value.name}")
            }
        }
        val tabs = listOf("Overview", "Songs", "Albums", "Latest")
        var actionsTrack by remember { mutableStateOf<Track?>(null) }
        var shareTrack by remember { mutableStateOf<Track?>(null) }
        var artistPickTrack by remember { mutableStateOf<Track?>(null) }
        var pickerTrack by remember { mutableStateOf<Track?>(null) }
        val accent = MaterialTheme.colorScheme.primary
        var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 24.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).pressScale(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(PhosphorIcons.Bold.ArrowLeft, "Back", tint = ink(), modifier = Modifier.size(22.dp))
                }
                Text(
                    value.name,
                    style = MaterialTheme.typography.displayMedium,
                    color = ink(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (wide) TextAlign.Start else TextAlign.End,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                VerticalTabs(
                    labels = tabs,
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    modifier = Modifier.fillMaxHeight().padding(start = 12.dp),
                )
                Crossfade(selectedTab, label = "artist-tab", modifier = Modifier.weight(1f)) { tab ->
                    when (tab) {
                        0 -> ArtistOverview(
                            artist = value,
                            wide = wide,
                            onTrack = { picked -> onTrackSelected(picked, value.popular) },
                            nowPlayingIsrc = nowPlayingIsrc,
                            accent = accent,
                            onMore = { actionsTrack = it },
                            onAlbum = onAlbumSelected,
                            onArtist = onSimilarArtist,
                            onViewAll = { selectedTab = 1 },
                            following = following,
                            onFollowClick = onFollowClick,
                        )

                        1 -> TrackList(value.songs, loading = !value.songsLoaded, wide = wide, nowPlayingIsrc = nowPlayingIsrc, accent = accent, onMore = { actionsTrack = it }) { picked -> onTrackSelected(picked, value.songs) }

                        2 -> AlbumGrid(value.albums, onAlbumSelected, wide)

                        else -> TrackList(value.latestSongs, loading = !value.songsLoaded, wide = wide, nowPlayingIsrc = nowPlayingIsrc, accent = accent, onMore = { actionsTrack = it }) { picked -> onTrackSelected(picked, value.latestSongs) }
                    }
                }
            }
            nowPlaying()
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
        artistPickTrack?.let { t ->
            ArtistPickerSheet(
                names = splitArtists(t.artist),
                albumTitle = t.album,
                onPick = { name -> onTrackArtist(t.copy(artist = name)) },
                onDismiss = { artistPickTrack = null },
            )
        }
        shareTrack?.let { t -> ShareSheet(title = t.title, artist = t.artist, artUrl = t.albumArtUrl, onDismiss = { shareTrack = null }) }
    }
}

private const val OVERVIEW_ALBUM_FRACTION = 0.8f

@Composable
private fun ArtistOverview(
    artist: ArtistDetails,
    wide: Boolean = false,
    onTrack: (Track) -> Unit,
    nowPlayingIsrc: String?,
    accent: Color,
    onMore: (Track) -> Unit,
    onAlbum: (DiscoverItem) -> Unit,
    onArtist: (String) -> Unit,
    onViewAll: () -> Unit,
    following: Boolean,
    onFollowClick: () -> Unit,
) {
    val portrait: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SubcomposeAsyncImage(
                model = artist.imageUrl,
                contentDescription = artist.name,
                modifier = Modifier.then(if (wide) Modifier.widthIn(max = 300.dp) else Modifier).fillMaxWidth(0.92f).aspectRatio(1f).clip(CircleShape),
                contentScale = ContentScale.Crop,
                loading = { ArtistPlaceholder() },
                error = { ArtistPlaceholder() },
            )
            FollowButton(following, onFollowClick, Modifier.padding(top = 14.dp))
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(0.38f).fillMaxHeight()) {
                val paneHeight = maxHeight
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = paneHeight).padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { portrait() }
            }
            LazyColumn(
                Modifier.weight(0.62f).fillMaxHeight().fadeEdges(),
                contentPadding = PaddingValues(start = 12.dp, bottom = 24.dp),
            ) { overviewBody(artist, true, onTrack, nowPlayingIsrc, accent, onMore, onAlbum, onArtist, onViewAll, endPad = 24.dp) }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().fadeEdges(),
            contentPadding = PaddingValues(start = 12.dp, end = 24.dp, bottom = 24.dp),
        ) {
            item { portrait() }
            overviewBody(artist, false, onTrack, nowPlayingIsrc, accent, onMore, onAlbum, onArtist, onViewAll)
        }
    }
}

private fun LazyListScope.overviewBody(
    artist: ArtistDetails,
    wide: Boolean,
    onTrack: (Track) -> Unit,
    nowPlayingIsrc: String?,
    accent: Color,
    onMore: (Track) -> Unit,
    onAlbum: (DiscoverItem) -> Unit,
    onArtist: (String) -> Unit,
    onViewAll: () -> Unit,
    endPad: Dp = 0.dp,
) {
    item {
        Row(Modifier.fillMaxWidth().padding(top = if (wide) 12.dp else 24.dp, bottom = 6.dp, end = endPad), verticalAlignment = Alignment.CenterVertically) {
            Text("Popular songs", style = MaterialTheme.typography.titleLarge, color = ink(), modifier = Modifier.weight(1f))
            Text("View all", color = ink(0.6f), modifier = Modifier.pressScale(onClick = onViewAll).padding(vertical = 6.dp))
        }
    }
    if (wide) {
        item(key = "popular") {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(end = endPad)) {
                val columns = ((maxWidth + 20.dp) / (380.dp + 20.dp)).toInt().coerceIn(1, 2)
                Column {
                    artist.popular.take(4).chunked(columns).forEach { rowTracks ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            rowTracks.forEach { t ->
                                TrackLine(t, isPlaying = t.isrc == nowPlayingIsrc, accent = accent, onClick = onTrack, onMore = onMore, modifier = Modifier.weight(1f))
                            }
                            repeat(columns - rowTracks.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    } else {
        itemsIndexed(artist.popular.take(4), key = { i, t -> "${t.isrc}-$i" }) { _, t -> TrackLine(t, isPlaying = t.isrc == nowPlayingIsrc, accent = accent, onClick = onTrack, onMore = onMore) }
    }
    item {
        Text("Albums", style = MaterialTheme.typography.titleLarge, color = ink(), modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = (maxWidth * OVERVIEW_ALBUM_FRACTION).let { if (wide) it.coerceAtMost(220.dp) else it }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = endPad)) {
                items(artist.albums.take(6), key = { it.id }) { AlbumCard(it, onAlbum, Modifier.width(cardWidth)) }
            }
        }
    }
    if (artist.similar.isNotEmpty()) {
        item(key = "similar") {
            Text("Similar artists", style = MaterialTheme.typography.titleLarge, color = ink(), modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = endPad)) {
                items(artist.similar, key = { it.id }) { SimilarArtistCard(it, onArtist) }
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    loading: Boolean = false,
    wide: Boolean = false,
    nowPlayingIsrc: String?,
    accent: Color,
    onMore: (Track) -> Unit,
    onTrack: (Track) -> Unit,
) {
    if (tracks.isEmpty() && !loading) {
        Text("Nothing here yet.", color = ink(0.6f), modifier = Modifier.padding(16.dp))
        return
    }
    if (wide) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(380.dp),
            modifier = Modifier.fillMaxSize().fadeEdges(),
            contentPadding = PaddingValues(start = 12.dp, end = 24.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(tracks, key = { i, t -> "${t.isrc}-$i" }) { _, t -> TrackLine(t, isPlaying = t.isrc == nowPlayingIsrc, accent = accent, onClick = onTrack, onMore = onMore) }
            if (loading) {
                item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ink(), strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().fadeEdges(), contentPadding = PaddingValues(start = 12.dp, end = 24.dp, bottom = 24.dp)) {
        itemsIndexed(tracks, key = { i, t -> "${t.isrc}-$i" }) { _, t -> TrackLine(t, isPlaying = t.isrc == nowPlayingIsrc, accent = accent, onClick = onTrack, onMore = onMore) }
        if (loading) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ink(), strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<DiscoverItem>, onAlbum: (DiscoverItem) -> Unit, wide: Boolean = false) {
    LazyVerticalGrid(
        columns = if (wide) GridCells.Adaptive(170.dp) else GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().fadeEdges(),
        contentPadding = PaddingValues(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(albums, key = { it.id }) { AlbumCard(it, onAlbum) }
    }
}

@Composable
private fun AlbumCard(album: DiscoverItem, onClick: (DiscoverItem) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().pressScale(onClick = { onClick(album) }, scaleDown = 0.97f)) {
        AsyncImage(
            model = remember(album.imageUrl) { squareArtUrl(album.imageUrl, 480) },
            contentDescription = album.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(ink(0.08f)),
            contentScale = ContentScale.Crop,
        )
        Text(album.title, color = ink(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(album.subtitle, color = ink(0.6f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrackLine(track: Track, isPlaying: Boolean, accent: Color, onClick: (Track) -> Unit, onMore: (Track) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().pressScale(onClick = { onClick(track) }, scaleDown = 0.98f).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = remember(track.albumArtUrl) { squareArtUrl(track.albumArtUrl, 200) },
            contentDescription = null,
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)).background(ink(0.08f)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPlaying) NowPlayingBars(color = accent, modifier = Modifier.padding(end = 8.dp))
                Text(
                    track.title,
                    color = if (isPlaying) accent else ink(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Text(track.album ?: track.artist, color = ink(0.6f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TrackMoreButton { onMore(track) }
    }
}

@Composable
private fun TrackMoreButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .focusRing(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "More options", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(PhosphorIcons.Bold.DotsThreeVertical, "More options", tint = ink(0.6f), modifier = Modifier.size(24.dp)) }
}

@Composable
private fun SimilarArtistCard(artist: DiscoverItem, onClick: (String) -> Unit) {
    Column(
        Modifier.width(96.dp).pressScale(onClick = { onClick(artist.title) }, scaleDown = 0.96f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SubcomposeAsyncImage(
            model = artist.imageUrl,
            contentDescription = artist.title,
            modifier = Modifier.size(96.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
            loading = { ArtistPlaceholder() },
            error = { ArtistPlaceholder() },
        )
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                artist.title,
                color = ink(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isVerifiedArtist(artist.followers)) VerifiedBadge(Modifier.padding(start = 4.dp), size = 14.dp)
        }
    }
}

@Composable
private fun AlbumLine(album: DiscoverItem) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SubcomposeAsyncImage(
            model = album.imageUrl,
            contentDescription = album.title,
            modifier = Modifier.size(64.dp).shadow(3.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
            loading = { ArtistPlaceholder() },
            error = { ArtistPlaceholder() },
        )
        Column(Modifier.padding(horizontal = 14.dp)) {
            Text(album.title, color = ink(), style = MaterialTheme.typography.titleMedium)
            Text(album.subtitle, color = ink(0.6f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ArtistPlaceholder() {
    ArtPlaceholder()
}

@Composable
private fun FollowButton(following: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fg = ink()
    val onFg = if (fg.luminance() > 0.5f) Color.Black else Color.White
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (following) ink(0.12f) else fg, shape)
            .then(if (following) Modifier.border(1.dp, ink(0.35f), shape) else Modifier)
            .pressScale(onClick = onClick, scaleDown = 0.96f)
            .padding(horizontal = 28.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (following) "Following" else "Follow",
            color = if (following) fg else onFg,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
