package com.gamut.feature.playerui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.CaretDown
import com.adamglin.phosphoricons.bold.DotsThreeVertical
import com.adamglin.phosphoricons.fill.Heart as HeartFilled
import com.adamglin.phosphoricons.regular.Heart
import com.adamglin.phosphoricons.regular.ListPlus
import com.adamglin.phosphoricons.regular.Queue
import com.adamglin.phosphoricons.regular.Repeat
import com.adamglin.phosphoricons.regular.RepeatOnce
import com.adamglin.phosphoricons.regular.ShareNetwork
import com.adamglin.phosphoricons.regular.Shuffle
import com.adamglin.phosphoricons.regular.SkipBack
import com.adamglin.phosphoricons.regular.SkipForward
import com.adamglin.phosphoricons.regular.Sparkle
import com.gamut.core.designsystem.AcrylicHost
import com.gamut.core.designsystem.AdaptiveLayout
import com.gamut.core.designsystem.ArtistLine
import com.gamut.core.designsystem.ArtistPickerSheet
import com.gamut.core.designsystem.CoverPalette
import com.gamut.core.designsystem.FocusLive
import com.gamut.core.designsystem.GamutMotion
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.ShareSheet
import com.gamut.core.designsystem.acrylicSource
import com.gamut.core.designsystem.adaptiveGutter
import com.gamut.core.designsystem.isWideLayout
import com.gamut.core.designsystem.rememberArtistTap
import com.gamut.core.designsystem.splitArtists
import com.gamut.core.model.PlaybackState
import com.gamut.core.model.RepeatMode as PlaybackRepeatMode
import com.gamut.core.model.Track
import com.gamut.core.player.EqualizerController
import com.gamut.core.player.ModController
import com.gamut.core.player.ModPreset
import com.gamut.core.player.MusicPlayer
import com.gamut.feature.playerui.components.ArtworkWithRing
import com.gamut.feature.playerui.components.BouncingText
import com.gamut.feature.playerui.components.DepthIcon
import com.gamut.feature.playerui.components.TimeRow
import com.gamut.feature.playerui.components.VolumeHud
import com.gamut.feature.playerui.components.VolumeHudOverlay
import com.gamut.feature.playerui.components.WaveformSource
import com.gamut.feature.playerui.components.backdropArtUrl
import com.gamut.feature.playerui.components.bounceTap
import com.gamut.feature.playerui.components.rememberCoverAccent
import com.gamut.feature.playerui.components.rememberCoverSubAccent
import com.gamut.feature.playerui.components.rememberCoverTint
import com.gamut.feature.playerui.components.rememberWaveform
import com.gamut.feature.playerui.components.swipeVolumeGesture
import com.gamut.feature.playerui.components.tapTarget
import com.gamut.feature.playerui.components.volumeHudAnchor
import com.gamut.feature.playerui.components.volumeHudHeaderAnchor
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine

private val FavoriteColor = Color(0xFFFF4D6D)
private const val PrefsName = "player_ui"
private const val PrefLyricsOn = "lyrics_on"

@Composable
fun PlayerScreen(
    player: MusicPlayer,
    onBack: () -> Unit = {},
    favoriteIsrcs: Set<String> = emptySet(),
    onToggleFavorite: (Track) -> Unit = {},
    playlists: List<PlaylistPickerItem> = emptyList(),
    onAddToPlaylist: (Track, String) -> Unit = { _, _ -> },
    playlistIdsWithTrack: Set<String> = emptySet(),
    onRemoveFromPlaylist: (Track, String) -> Unit = { _, _ -> },
    onGoToArtist: (Track) -> Unit = {},
    active: Boolean = true,
) {
    val state by player.playbackState.collectAsState()
    val liveTrack by remember { derivedStateOf { (state as? PlaybackState.Playing)?.track ?: (state as? PlaybackState.Paused)?.track } }
    var lastTrack by remember { mutableStateOf<Track?>(null) }
    LaunchedEffect(liveTrack) { liveTrack?.let { lastTrack = it } }
    val track: Track? = liveTrack ?: lastTrack

    val isPlaying by remember { derivedStateOf { state is PlaybackState.Playing } }
    var shownPlaying by remember { mutableStateOf(isPlaying) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            shownPlaying = true
        } else {
            delay(700)
            shownPlaying = false
        }
    }
    val showLoading by player.loadingTrack.collectAsState()
    val loadProgressState = player.loadProgress.collectAsState()
    val awaiting by player.awaitingAudio.collectAsState()
    var bufferingShown by remember { mutableStateOf(false) }
    LaunchedEffect(awaiting) {
        if (awaiting) {
            delay(500)
            bufferingShown = true
        } else {
            bufferingShown = false
        }
    }
    val spinnerOn = showLoading || bufferingShown

    val streamDuration by player.streamDurationMs.collectAsState()
    val catalogDuration = track?.durationMs ?: 0L
    val streamPlausible = streamDuration > 0L &&
        (catalogDuration <= 0L || streamDuration in (catalogDuration / 2)..(catalogDuration * 2))
    val effectiveDurationMs = if (streamPlausible) streamDuration else catalogDuration
    val duration = effectiveDurationMs.coerceAtLeast(1L)
    val shuffleOn by player.shuffleEnabled.collectAsState()
    val repeatMode by player.repeatMode.collectAsState()
    val queueState by player.queue.collectAsState()
    var showQueue by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val eq = remember { EqualizerController.get(context) }
    val eqState by eq.state.collectAsState()
    var showEq by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    val playlistChanges = remember { androidx.compose.runtime.mutableStateMapOf<String, Map<String, Boolean>>() }
    val modState by ModController.state.collectAsState()
    var showMod by remember { mutableStateOf(false) }
    val sleepEndsAt by SleepTimer.endsAt.collectAsState()
    val haptic = LocalHapticFeedback.current
    val volumeHud = remember { VolumeHud(context.getSystemService(AudioManager::class.java), haptic) }

    val prefs = remember { context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE) }
    var lyricsOn by remember { mutableStateOf(prefs.getBoolean(PrefLyricsOn, true)) }
    var lyricsPanelOpen by remember { mutableStateOf(false) }

    val leave: () -> Unit = { if (active) onBack() }
    BackHandler(enabled = active && !lyricsPanelOpen) { leave() }
    BackHandler(enabled = active && lyricsPanelOpen) { lyricsPanelOpen = false }
    LaunchedEffect(active) {
        if (!active) {
            delay(GamutMotion.PLAYER_LAYER_OUT_MS + 120L)
            lyricsPanelOpen = false
        }
    }
    val clock = remember { PlayerClock() }
    LaunchedEffect(modState.settings.speed) { clock.setSpeed(modState.settings.speed) }
    LaunchedEffect(player) { clock.follow(player) }
    LaunchedEffect(clock.live, duration, active) { if (clock.live && active) clock.tick(duration) }

    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val durationKnown = effectiveDurationMs > 0L
    val currentKey = track?.let { it.isrc.orEmpty() + "|" + it.title }
    val progressProvider = remember(duration, durationKnown, currentKey, showLoading) {
        {
            val clockPosition = clock.positionMs
            val clockKey = clock.anchoredKey
            scrubFraction ?: when {
                showLoading -> loadProgressState.value.coerceIn(0f, 1f)
                durationKnown && clockKey == currentKey -> (clockPosition / duration).coerceIn(0f, 1f)
                else -> 0f
            }
        }
    }
    val positionProvider = remember(progressProvider, duration) { { (progressProvider() * duration).toLong() } }
    val lyricsPosition = remember(clock) { { clock.positionMs.toLong() + LyricsSyncOffsetMs } }

    val seekToFraction: (Float) -> Unit = { fraction ->
        val target = (fraction * duration).toLong()
        clock.anchor(target)
        player.seekTo(target)
    }
    val seekToMs: (Long) -> Unit = { ms ->
        val target = (ms - LyricsSyncOffsetMs).coerceAtLeast(0L)
        clock.anchor(target)
        player.seekTo(target)
    }
    val togglePlay: () -> Unit = {
        if (shownPlaying) player.pause() else player.resume()
        shownPlaying = !shownPlaying
    }
    val coverFocus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(active) {
        if (active && inputMode.inputMode == InputMode.Keyboard) {
            delay(GamutMotion.PLAYER_LAYER_IN_MS + 60L)
            runCatching { coverFocus.requestFocus() }
        }
    }

    val lyricsKey = track?.let { it.isrc.orEmpty().ifBlank { "${it.title}|${it.artist}" } }
    var lyricsState by remember(lyricsKey) {
        mutableStateOf<LyricsState>(track?.let { LyricsRepository.cached(it) } ?: LyricsState.Loading)
    }
    var lyricsRetry by remember(lyricsKey) { mutableIntStateOf(0) }
    var lyricsTriedSeconds by remember(lyricsKey) { mutableIntStateOf(-1) }
    val lyricsSeconds = (effectiveDurationMs / 1000).toInt()
    LaunchedEffect(lyricsKey, lyricsRetry, lyricsOn, lyricsSeconds, active) {
        val t = track ?: return@LaunchedEffect
        if (!lyricsOn || !active) return@LaunchedEffect
        val shown = lyricsState
        if (shown is LyricsState.Loaded && (shown.isSynced || lyricsTriedSeconds == lyricsSeconds)) return@LaunchedEffect
        if (shown !is LyricsState.Loaded) lyricsState = LyricsState.Loading
        lyricsTriedSeconds = lyricsSeconds
        var attempt = 0
        while (true) {
            val result = LyricsRepository.fetchLyrics(t, effectiveDurationMs)
            if (result is LyricsState.Loaded || shown !is LyricsState.Loaded) lyricsState = result
            if (result !is LyricsState.Error || ++attempt >= 20) break
            delay((2_000L * attempt).coerceAtMost(15_000L))
        }
    }
    val activeLine = remember(lyricsState, clock) {
        derivedStateOf {
            val s = lyricsState
            if (s is LyricsState.Loaded && s.isSynced) activeLineIndex(s.lines, lyricsPosition()) else -1
        }
    }
    val activeIndex: () -> Int = remember(activeLine) { { activeLine.value } }

    var artistInfo by remember(lyricsKey) { mutableStateOf(track?.let { ArtistInfoRepository.cached(it) }) }
    var artistLoading by remember(lyricsKey) { mutableStateOf(artistInfo == null) }
    LaunchedEffect(lyricsKey, active) {
        val t = track ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        if (artistInfo != null) {
            artistLoading = false
            return@LaunchedEffect
        }
        var attempt = 0
        while (artistInfo == null && attempt < 3) {
            artistInfo = ArtistInfoRepository.fetch(t).takeIf { it.imageUrl != null || it.credits.size > 1 }
            if (artistInfo == null) {
                attempt++
                if (attempt < 3) delay(10_000L * attempt)
            }
        }
        artistLoading = false
    }

    val rawArt = track?.albumArtUrl
    val artUrl = remember(rawArt) { backdropArtUrl(rawArt) }
    val accent = rememberCoverAccent(artUrl)
    val lyricHighlight = rememberCoverSubAccent(artUrl, fallback = accent)
    val tint = rememberCoverTint(artUrl)
    SideEffect {
        WaveformSource.init(context)
        WaveformSource.provider = { t -> player.sourceUrlFor(t.isrc) }
    }
    val waveform = rememberWaveform(track.takeIf { active })
    val waveformDone = waveform.isNotEmpty() && waveform.indexOfLast { it > 0f } >= waveform.size * 9 / 10
    val shownLyrics = remember(lyricsState, waveformDone, effectiveDurationMs) {
        val s = lyricsState
        if (s is LyricsState.Loaded && s.isSynced && waveformDone) {
            LyricsState.Loaded(LyricsAudioAligner.refine(s.lines, waveform, effectiveDurationMs), true)
        } else {
            s
        }
    }

    val artistTap = rememberArtistTap(track?.artist.orEmpty(), track?.album) { name ->
        track?.let { t -> onGoToArtist(t.copy(artist = name)) }
    }
    val goToArtist: () -> Unit = artistTap.onClick

    val gutter = adaptiveGutter(AdaptiveLayout.PlayerMaxWidth)
    CompositionLocalProvider(LocalCoverPalette provides CoverPalette.Neutral) {
        AcrylicHost(
            Modifier
                .fillMaxSize()
                .background(CoverPalette.Neutral.base)
                .then(if (lyricsPanelOpen) Modifier else Modifier.swipeVolumeGesture(volumeHud)),
        ) {
            if (artUrl != null) PlayerBackdrop(artUrl, animateChanges = active)

            val wide = isWideLayout()
            val edge = if (wide) 0.dp else gutter

            val playerContent: @Composable (Dp) -> Unit = { coverMax ->
                Spacer(Modifier.height(10.dp))
                Box(Modifier.volumeHudHeaderAnchor(volumeHud)) {
                    TrackHeader(
                        title = track?.title.orEmpty(),
                        artist = track?.artist.orEmpty(),
                        albumTitle = track?.album,
                        onArtist = { name -> track?.let { t -> onGoToArtist(t.copy(artist = name)) } },
                        isFavorite = track?.isrc?.let { it in favoriteIsrcs } == true,
                        onToggleFavorite = { track?.let(onToggleFavorite) },
                    )
                }
                Spacer(Modifier.height(36.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val extra = ArtworkExtraWidth.roundToPx()
                            val w = constraints.maxWidth + extra * 2
                            val placeable = measurable.measure(Constraints(minWidth = w, maxWidth = w))
                            layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DepthIcon(
                        PhosphorIcons.Regular.SkipBack,
                        "Previous",
                        Modifier.bounceTap(onClick = player::skipPrevious).padding(SideButtonPadding),
                        size = SideButtonSize,
                    )
                    Box(Modifier.weight(1f).volumeHudAnchor(volumeHud), contentAlignment = Alignment.Center) {
                        ArtworkWithRing(
                            modifier = Modifier.fillMaxWidth().widthIn(max = coverMax).focusRequester(coverFocus),
                            url = rawArt,
                            progress = progressProvider,
                            onScrub = { scrubFraction = it },
                            isPlaying = shownPlaying,
                            enabled = true,
                            loading = spinnerOn,
                            waveform = waveform,
                            onTogglePlay = togglePlay,
                            onSeekFraction = seekToFraction,
                            seekStep = if (duration > 0) (10_000f / duration).coerceIn(0.005f, 0.1f) else 0.04f,
                        )
                    }
                    DepthIcon(
                        PhosphorIcons.Regular.SkipForward,
                        "Next",
                        Modifier.bounceTap(onClick = player::skipNext).padding(SideButtonPadding),
                        size = SideButtonSize,
                    )
                }

                Spacer(Modifier.height(10.dp))
                TimeRow(positionProvider, effectiveDurationMs)

                if (lyricsOn) CurrentLyricLine(shownLyrics, activeIndex, lyricsPosition, tint, highlight = lyricHighlight)

                Spacer(Modifier.height(4.dp))
                ControlsGrid(
                    shuffleOn = shuffleOn,
                    repeatMode = repeatMode,
                    accent = accent,
                    modActive = modState.preset != ModPreset.ORIGINAL,
                    onShuffle = { player.setShuffle(!shuffleOn) },
                    onQueue = { showQueue = true },
                    onAddToPlaylist = { showAddToPlaylist = true },
                    onRepeat = {
                        player.setRepeatMode(
                            when (repeatMode) {
                                PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
                                PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
                                PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
                            },
                        )
                    },
                    onShare = { showShare = true },
                    onMod = { showMod = true },
                )
            }

            val extrasContent: @Composable () -> Unit = {
                if (lyricsOn) {
                    LyricsPreviewCard(
                        lyricsState = lyricsState,
                        activeIndex = activeIndex,
                        tint = tint,
                        onShowLyrics = { lyricsPanelOpen = true },
                        onRetry = { lyricsRetry++ },
                    )
                    Spacer(Modifier.height(24.dp))
                }
                if (track != null) {
                    ArtistAboutSection(
                        artistName = track.artist,
                        info = artistInfo,
                        loading = artistLoading,
                        fallbackArtUrl = rawArt,
                        onArtistClick = goToArtist,
                    )
                }
            }

            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                FocusLive(!lyricsPanelOpen) {
                    PlayerTopBar(leave, onMore = { showMore = true }, modifier = Modifier.fillMaxWidth().padding(start = 24.dp + edge, end = 24.dp + edge, top = 14.dp))

                    if (wide) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                                val coverMax = (maxHeight - 360.dp).coerceIn(220.dp, 560.dp)
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(start = 32.dp, end = 16.dp),
                                ) {
                                    playerContent(coverMax)
                                    Spacer(Modifier.height(32.dp).navigationBarsPadding())
                                }
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(start = 16.dp, end = 32.dp),
                            ) {
                                Spacer(Modifier.height(10.dp))
                                extrasContent()
                                Spacer(Modifier.height(32.dp).navigationBarsPadding())
                            }
                        }
                    } else {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp + gutter),
                        ) {
                            playerContent(Dp.Infinity)
                            Spacer(Modifier.height(28.dp))
                            extrasContent()
                            Spacer(Modifier.height(32.dp).navigationBarsPadding())
                        }
                    }
                }
            }

            VolumeHudOverlay(volumeHud)

            AnimatedVisibility(
                visible = lyricsPanelOpen && track != null,
                enter = slideInVertically(tween(GamutMotion.PLAYER_IN_MS, easing = GamutMotion.EmphasizedDecelerate)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(GamutMotion.PLAYER_OUT_MS, easing = GamutMotion.EmphasizedAccelerate)) { it } + fadeOut(tween(200)),
            ) {
                FullLyricsPanel(
                    title = track?.title.orEmpty(),
                    artist = track?.artist.orEmpty(),
                    lyricsState = shownLyrics,
                    positionMs = lyricsPosition,
                    activeIndex = activeIndex,
                    tint = tint,
                    isPlaying = shownPlaying,
                    isLoading = spinnerOn,
                    progress = progressProvider,
                    labelPositionMs = positionProvider,
                    durationMs = effectiveDurationMs,
                    onScrub = { scrubFraction = it },
                    onSeekFraction = seekToFraction,
                    onSeekToMs = seekToMs,
                    onClose = { lyricsPanelOpen = false },
                    onShare = { showShare = true },
                    onMore = { showMore = true },
                    onTogglePlay = togglePlay,
                    onPrevious = player::skipPrevious,
                    onNext = player::skipNext,
                    onRetry = { lyricsRetry++ },
                )
            }
        }
    }

    if (showMore) {
        PlayerOptionsSheet(
            isFavorite = track?.isrc?.let { it in favoriteIsrcs } == true,
            lyricsOn = lyricsOn,
            eqActive = eqState.enabled,
            sleepActive = sleepEndsAt != null,
            onShare = {
                showMore = false
                showShare = true
            },
            onToggleLyrics = {
                lyricsOn = !lyricsOn
                prefs.edit().putBoolean(PrefLyricsOn, lyricsOn).apply()
                if (!lyricsOn) lyricsPanelOpen = false
            },
            onToggleFavorite = { track?.let(onToggleFavorite) },
            onAddToPlaylist = {
                showMore = false
                showAddToPlaylist = true
            },
            onGoToArtist = {
                showMore = false
                goToArtist()
            },
            onEqualizer = {
                showMore = false
                showEq = true
            },
            onSleepTimer = {
                showMore = false
                showSleep = true
            },
            onDismiss = { showMore = false },
        )
    }
    artistTap.Sheet()
    if (showShare) {
        track?.let { t ->
            ShareSheet(title = t.title, artist = t.artist, artUrl = t.albumArtUrl, onDismiss = { showShare = false })
        }
    }
    if (showAddToPlaylist) {
        AddToPlaylistSheet(
            playlists = playlists,
            alreadyIn = run {
                val delta = track?.isrc?.let { playlistChanges[it] }.orEmpty()
                playlistIdsWithTrack.filter { delta[it] != false }.toSet() + delta.filterValues { it }.keys
            },
            onPick = { picked ->
                track?.let {
                    onAddToPlaylist(it, picked.id)
                    playlistChanges[it.isrc] = (playlistChanges[it.isrc] ?: emptyMap()) + (picked.id to true)
                }
            },
            onRemove = { picked ->
                track?.let {
                    onRemoveFromPlaylist(it, picked.id)
                    playlistChanges[it.isrc] = (playlistChanges[it.isrc] ?: emptyMap()) + (picked.id to false)
                }
            },
            onDismiss = { showAddToPlaylist = false },
        )
    }
    if (showEq) EqualizerSheet(eq, accent, onDismiss = { showEq = false })
    if (showMod) ModSheet(accent, onDismiss = { showMod = false })
    if (showSleep) {
        SleepTimerSheet(
            onPick = { minutes ->
                SleepTimer.start(minutes) { player.pause() }
                showSleep = false
            },
            onTurnOff = {
                SleepTimer.cancel()
                showSleep = false
            },
            onDismiss = { showSleep = false },
        )
    }
    if (showQueue) {
        QueueSheet(
            queue = queueState,
            accent = accent,
            onDismiss = { showQueue = false },
            onPlay = player::skipTo,
            onRemove = player::removeFromQueue,
        )
    }
}

@Composable
private fun PlayerTopBar(onBack: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        DepthIcon(PhosphorIcons.Bold.CaretDown, "Collapse", Modifier.tapTarget(onClick = onBack).padding(8.dp))
        Spacer(Modifier.weight(1f))
        DepthIcon(PhosphorIcons.Bold.DotsThreeVertical, "More", Modifier.tapTarget(onClick = onMore).padding(8.dp))
    }
}

@Composable
private fun TrackHeader(
    title: String,
    artist: String,
    albumTitle: String?,
    onArtist: (String) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            BouncingText(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = (-0.3).sp),
                color = Color.White,
            )
            Spacer(Modifier.height(2.dp))
            ArtistLine(artist, albumTitle, onArtist, stackSize = 22.dp) { m ->
                Box(m) {
                    BouncingText(
                        text = artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }
        }
        DepthIcon(
            if (isFavorite) PhosphorIcons.Fill.HeartFilled else PhosphorIcons.Regular.Heart,
            if (isFavorite) "Remove from favorites" else "Add to favorites",
            Modifier.bounceTap(onClick = onToggleFavorite).padding(4.dp),
            tint = if (isFavorite) FavoriteColor else Color.White,
            size = 26.dp,
        )
    }
}

@Composable
private fun ControlsGrid(
    shuffleOn: Boolean,
    repeatMode: PlaybackRepeatMode,
    accent: Color,
    modActive: Boolean,
    onShuffle: () -> Unit,
    onQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRepeat: () -> Unit,
    onShare: () -> Unit,
    onMod: () -> Unit,
) {
    val idle = Color.White.copy(alpha = 0.85f)
    val iconSize = 24.dp
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GridCell {
                DepthIcon(
                    PhosphorIcons.Regular.Shuffle,
                    if (shuffleOn) "Shuffle on" else "Shuffle off",
                    Modifier.bounceTap(onClick = onShuffle).padding(8.dp),
                    tint = if (shuffleOn) accent else idle,
                    size = iconSize,
                )
            }
            GridCell { DepthIcon(PhosphorIcons.Regular.Queue, "Queue", Modifier.bounceTap(onClick = onQueue).padding(8.dp), tint = idle, size = iconSize) }
            GridCell { DepthIcon(PhosphorIcons.Regular.ListPlus, "Add to playlist", Modifier.bounceTap(onClick = onAddToPlaylist).padding(8.dp), tint = idle, size = iconSize) }
            GridCell {
                DepthIcon(
                    if (repeatMode == PlaybackRepeatMode.ONE) PhosphorIcons.Regular.RepeatOnce else PhosphorIcons.Regular.Repeat,
                    when (repeatMode) {
                        PlaybackRepeatMode.OFF -> "Repeat off"
                        PlaybackRepeatMode.ALL -> "Repeat all"
                        PlaybackRepeatMode.ONE -> "Repeat one"
                    },
                    Modifier.bounceTap(onClick = onRepeat).padding(8.dp),
                    tint = if (repeatMode != PlaybackRepeatMode.OFF) accent else idle,
                    size = iconSize,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GridCell {}
            GridCell { DepthIcon(PhosphorIcons.Regular.ShareNetwork, "Share", Modifier.bounceTap(onClick = onShare).padding(8.dp), tint = idle, size = iconSize) }
            GridCell {
                DepthIcon(
                    PhosphorIcons.Regular.Sparkle,
                    if (modActive) "Mod on" else "Mod",
                    Modifier.bounceTap(onClick = onMod).padding(8.dp),
                    tint = if (modActive) accent else idle,
                    size = iconSize,
                )
            }
            GridCell {}
        }
    }
}

@Composable
private fun RowScope.GridCell(content: @Composable () -> Unit) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
}

internal const val LyricsSyncOffsetMs = 250L

private val ArtworkExtraWidth = 14.dp

private val SideButtonSize = 30.dp
private val SideButtonPadding = 4.dp

private const val RESYNC_THRESHOLD_MS = 400L

@Stable
internal class PlayerClock {
    private var basePosition = 0L
    private var baseTimestamp = SystemClock.elapsedRealtime()
    private var speed = 1f

    var anchoredKey by mutableStateOf<String?>(null)
        private set

    var live by mutableStateOf(false)
        private set

    var positionMs by mutableFloatStateOf(0f)
        private set

    fun anchor(positionMs: Long) {
        basePosition = positionMs
        baseTimestamp = SystemClock.elapsedRealtime()
        this.positionMs = positionMs.toFloat()
    }

    fun setSpeed(value: Float) {
        if (value == speed) return
        anchor(positionMs.toLong())
        speed = value
    }

    suspend fun follow(player: MusicPlayer) {
        var lastTrackKey: String? = null
        var wasLive = false
        combine(player.playbackState, player.awaitingAudio) { s, awaiting -> s to awaiting }.collect { (s, awaiting) ->
            val reported: Long
            val track: Track
            when (s) {
                is PlaybackState.Playing -> {
                    reported = player.currentPositionMs().takeIf { it >= 0L } ?: s.positionMs
                    track = s.track
                }

                is PlaybackState.Paused -> {
                    reported = player.currentPositionMs().takeIf { it >= 0L } ?: s.positionMs
                    track = s.track
                }

                else -> {
                    live = false
                    wasLive = false
                    lastTrackKey = null
                    anchoredKey = null
                    anchor(0L)
                    return@collect
                }
            }
            val key = track.isrc.orEmpty() + "|" + track.title
            val nowLive = s is PlaybackState.Playing && !awaiting
            if (key != lastTrackKey) anchoredKey = null
            val now = SystemClock.elapsedRealtime()
            val estimate = if (wasLive) basePosition + ((now - baseTimestamp) * speed).toLong() else basePosition
            if (!nowLive || key != lastTrackKey || nowLive != wasLive || abs(reported - estimate) > RESYNC_THRESHOLD_MS) {
                anchoredKey = key
                anchor(reported)
            }
            anchoredKey = key
            lastTrackKey = key
            wasLive = nowLive
            live = nowLive
        }
    }

    suspend fun tick(durationMs: Long) {
        while (true) {
            withFrameNanos {}
            val elapsed = (SystemClock.elapsedRealtime() - baseTimestamp) * speed
            positionMs = (basePosition + elapsed).toFloat().coerceAtMost(durationMs.toFloat())
        }
    }
}

private val BackgroundBlur = 24.dp

@Composable
internal fun PlayerBackdrop(artUrl: String, animateChanges: Boolean = true) {
    Box(Modifier.fillMaxSize().acrylicSource()) {
        Box(Modifier.fillMaxSize().clipToBounds()) {
            AsyncImage(
                model = rememberFadeRequest(artUrl, durationMs = if (animateChanges) 600 else 0),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.3f
                        scaleY = 1.3f
                    }
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(BackgroundBlur) else Modifier),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        CoverPalette.Neutral.base.copy(alpha = 0.50f),
                        CoverPalette.Neutral.base.copy(alpha = 0.78f),
                    ),
                ),
            ),
        )
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.05f)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.02f), Color.White.copy(alpha = 0.06f)),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1700f),
                ),
            ),
        )
    }
}

@Composable
private fun rememberFadeRequest(url: String?, durationMs: Int = 600): ImageRequest {
    val context = LocalContext.current
    val firstUrl = remember { url }
    return remember(url, durationMs == 0) {
        ImageRequest.Builder(context).data(url)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .crossfade(if (url == firstUrl || durationMs == 0) 0 else durationMs).build()
    }
}
