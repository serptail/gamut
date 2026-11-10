package com.gamut

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.BookmarkSimple
import com.adamglin.phosphoricons.bold.MagnifyingGlass
import com.adamglin.phosphoricons.bold.User
import com.gamut.account.AccountViewModel
import com.gamut.account.ProfileScreen
import com.gamut.core.database.DownloadIndex
import com.gamut.core.designsystem.AcrylicHost
import com.gamut.core.designsystem.AppearanceSettings
import com.gamut.core.designsystem.BottomBarItem
import com.gamut.core.designsystem.CoverPalette
import com.gamut.core.designsystem.CoverTheme
import com.gamut.core.designsystem.FlowingBackground
import com.gamut.core.designsystem.FocusLive
import com.gamut.core.designsystem.GamutBackdrop
import com.gamut.core.designsystem.GamutBoot
import com.gamut.core.designsystem.GamutBottomBar
import com.gamut.core.designsystem.GamutLoadingScreen
import com.gamut.core.designsystem.GamutMotion
import com.gamut.core.designsystem.GamutTheme
import com.gamut.core.designsystem.LocalAppearance
import com.gamut.core.designsystem.LocalArtistPhotoResolver
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.LocalNavSettled
import com.gamut.core.designsystem.LocalNotifier
import com.gamut.core.designsystem.MiniPlayerStrip
import com.gamut.core.designsystem.NoticeHost
import com.gamut.core.designsystem.ProfileAvatar
import com.gamut.core.designsystem.TransparentStatusBar
import com.gamut.core.designsystem.acrylicSource
import com.gamut.core.designsystem.appNeutralPalette
import com.gamut.core.designsystem.focusLayer
import com.gamut.core.designsystem.rememberAppPalette
import com.gamut.core.designsystem.rememberNotifier
import com.gamut.core.designsystem.squareArtUrl
import com.gamut.core.model.PlaybackState
import com.gamut.core.model.Track
import com.gamut.core.player.MusicPlayer
import com.gamut.feature.library.CreatePlaylistScreen
import com.gamut.feature.library.EmptyStateArtistCovers
import com.gamut.feature.library.EmptyStateCovers
import com.gamut.feature.library.FavoritesScreen
import com.gamut.feature.library.LibraryScreen
import com.gamut.feature.library.LibraryViewModel
import com.gamut.feature.library.LocalAvailabilityViewModel
import com.gamut.feature.library.LocalMusicScreen
import com.gamut.feature.library.LocalMusicViewModel
import com.gamut.feature.library.PlaylistDownloadsViewModel
import com.gamut.feature.library.PlaylistScreen
import com.gamut.feature.library.localUri
import com.gamut.feature.library.playlistIdsContaining
import com.gamut.feature.library.withoutMissingLocal
import com.gamut.feature.playerui.ConnectSheet
import com.gamut.feature.playerui.PlayerScreen
import com.gamut.feature.playerui.PlaylistPickerItem
import com.gamut.feature.playerui.TrackActionsSheet
import com.gamut.feature.playerui.rememberOutputDevices
import com.gamut.feature.resolver.AudioSourceRepository
import com.gamut.feature.resolver.NewPipeYoutubeSource
import com.gamut.feature.resolver.ResolveFailure
import com.gamut.feature.resolver.ResolveTrackSourceUseCase
import com.gamut.feature.resolver.TrackDownloadManager
import com.gamut.feature.search.AlbumScreen
import com.gamut.feature.search.AlbumViewModel
import com.gamut.feature.search.ArtistScreen as DiscoverArtistScreen
import com.gamut.feature.search.ArtistViewModel
import com.gamut.feature.search.DiscoverItem
import com.gamut.feature.search.DiscoverRepository
import com.gamut.feature.search.LocalPlaylistRemove
import com.gamut.feature.search.SearchScreen
import com.gamut.feature.search.SearchViewModel
import com.gamut.feature.search.ShazamSheet
import com.gamut.feature.search.ShazamState
import com.gamut.feature.search.ShazamViewModel
import com.gamut.feature.search.TrackPlaylistOption
import com.gamut.settings.AboutScreen
import com.gamut.settings.AppearanceScreen
import com.gamut.settings.AppearanceStore
import com.gamut.settings.FeedbackScreen
import com.gamut.settings.LocalPageActive
import com.gamut.settings.LocalSupportNudge
import com.gamut.settings.PlaybackSettingsScreen
import com.gamut.settings.RecentlyPlayedScreen
import com.gamut.settings.RecentlyPlayedViewModel
import com.gamut.settings.SettingsScreen
import com.gamut.settings.StatsViewModel
import com.gamut.settings.StorageSettingsScreen
import com.gamut.settings.SupportConfig
import com.gamut.settings.SupportHeartButton
import com.gamut.settings.SupportSheetHost
import com.gamut.settings.SupportViewModel
import com.gamut.settings.TelemetryCollector
import com.gamut.settings.supportNudgeCorona
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var player: MusicPlayer

    @Inject lateinit var resolveTrackSource: ResolveTrackSourceUseCase

    @Inject lateinit var audioSources: AudioSourceRepository

    @Inject lateinit var newPipe: NewPipeYoutubeSource

    @Inject lateinit var downloadIndex: DownloadIndex

    @Inject lateinit var downloadManager: TrackDownloadManager

    @Inject lateinit var appearanceStore: AppearanceStore

    @Inject lateinit var telemetry: TelemetryCollector

    @Inject lateinit var discoverRepository: DiscoverRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        telemetry.install()
        com.gamut.feature.playerui.components.WaveformSource.init(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        val systemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val startPalette = appearanceStore.settings.value.neutralPalette(systemDark)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = startPalette.isLight
        window.setBackgroundDrawable(ColorDrawable(startPalette.base.toArgb()))
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    moveTaskToBack(true)
                }
            },
        )
        setContent {
            val appearance by appearanceStore.settings.collectAsState()
            CompositionLocalProvider(LocalAppearance provides appearance) {
                com.gamut.update.UpdateGate {
                    com.gamut.onboarding.OnboardingGate {
                        GamutApp(player, resolveTrackSource, audioSources, newPipe, downloadIndex, downloadManager, appearance, telemetry, discoverRepository)
                    }
                }
            }
        }
    }
}

@Composable
private fun GamutApp(player: MusicPlayer, resolveTrackSource: ResolveTrackSourceUseCase, audioSources: AudioSourceRepository, newPipe: NewPipeYoutubeSource, downloadIndex: DownloadIndex, downloadManager: TrackDownloadManager, appearance: AppearanceSettings, telemetry: TelemetryCollector, discoverRepository: DiscoverRepository) {
    val nav = rememberNavController()
    val notifier = rememberNotifier()
    val supportVm = hiltViewModel<SupportViewModel>()
    val supportNudge by supportVm.store.nudge.collectAsState()
    val updateVm = hiltViewModel<com.gamut.update.AppUpdateViewModel>(viewModelStoreOwner = LocalContext.current as ComponentActivity)
    LaunchedEffect(downloadManager) {
        downloadManager.summaries.collect { s ->
            if (s.succeeded > 0) supportVm.store.recordDownloadSuccess()
            when {
                s.succeeded == 0 -> notifier.error(if (s.failed == 1) "Couldn\u2019t download this song" else "Downloads failed")
                s.failed > 0 -> notifier.info("${s.succeeded} downloaded \u00B7 ${s.failed} failed")
                s.succeeded == 1 -> notifier.success("Downloaded \u201C${s.title}\u201D")
                s.label != null -> notifier.success("\u201C${s.label}\u201D downloaded")
                else -> notifier.success("${s.succeeded} songs downloaded")
            }
        }
    }
    val searchVm = hiltViewModel<SearchViewModel>()
    val discover by searchVm.discover.collectAsState()
    LaunchedEffect(discover.albums, discover.artists) {
        EmptyStateCovers.offer(discover.albums.map { it.imageUrl } + discover.artists.map { it.imageUrl })
    }
    LaunchedEffect(discover.artists) {
        EmptyStateArtistCovers.offer(discover.artists.map { it.imageUrl })
    }
    var openSearchTick by remember { mutableIntStateOf(0) }
    var openLibraryTick by remember { mutableIntStateOf(0) }
    var showLoading by rememberSaveable { mutableStateOf(!GamutBoot.finished) }
    var forceReady by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(10_000)
        forceReady = true
    }
    val dataReady = forceReady || discover.tabs.isNotEmpty() || (!discover.isLoading && discover.error != null)
    LaunchedEffect(dataReady) {
        if (dataReady) {
            delay(500)
            newPipe.warmUp()
        }
    }
    LaunchedEffect(showLoading) {
        if (!showLoading) {
            delay(300)
            player.warmUp()
        }
    }
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    val homeShowing = currentRoute == null || currentRoute == "home"
    val focusManager = LocalFocusManager.current
    LaunchedEffect(homeShowing) { if (!homeShowing) focusManager.clearFocus(force = true) }
    val photoResolver = remember(discoverRepository) {
        val resolve: suspend (String, String?) -> String? = { name, album -> discoverRepository.artistPhotoFor(name, album.orEmpty()) }
        resolve
    }
    val homeAlpha = animateFloatAsState(
        targetValue = if (homeShowing) 1f else 0f,
        animationSpec = tween(GamutMotion.NAV_MS, easing = GamutMotion.Standard),
        label = "home-alpha",
    )
    val scope = rememberCoroutineScope()
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    val layerPos = remember { Animatable(0f) }
    var layerPage by remember { mutableStateOf<SettingsPage?>(null) }
    var layerOpen by remember { mutableStateOf(false) }
    var composedPages by remember { mutableStateOf(emptySet<SettingsPage>()) }
    LaunchedEffect(showLoading) {
        if (!showLoading) {
            delay(1500)
            for (page in SettingsPage.entries) {
                composedPages = composedPages + page
                delay(400)
            }
        }
    }
    val settingsX = remember { Animatable(1f) }
    var settingsComposed by remember { mutableStateOf(false) }
    var prevRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showLoading) {
        if (!showLoading) {
            delay(300)
            settingsComposed = true
        }
    }
    LaunchedEffect(currentRoute) {
        val prev = prevRoute
        val r = currentRoute
        prevRoute = r
        val spec = tween<Float>(GamutMotion.NAV_MS, easing = GamutMotion.Standard)
        when {
            r == "settings" -> settingsX.animateTo(0f, spec)
            prev == "settings" && (r == null || r == "home") -> settingsX.animateTo(1f, spec)
            prev == "settings" -> settingsX.animateTo(-1f, spec)
            r == null || r == "home" -> settingsX.snapTo(1f)
        }
    }
    val openLayer: (SettingsPage) -> Unit = { page ->
        telemetry.breadcrumb("nav", "opened ${page.name}")
        scope.launch {
            if (page !in composedPages) {
                composedPages = composedPages + page
                withFrameNanos { }
                withFrameNanos { }
            }
            if (layerPos.value > 0f && layerPage != page) layerPos.snapTo(0f)
            layerPage = page
            layerOpen = true
            layerPos.animateTo(1f, tween(GamutMotion.TAB_MS, easing = GamutMotion.Standard))
        }
    }
    val closeLayer: () -> Unit = {
        layerOpen = false
        scope.launch { layerPos.animateTo(0f, tween(GamutMotion.TAB_MS, easing = GamutMotion.Standard)) }
    }
    val playerPos = remember { Animatable(0f) }
    var playerOpen by remember { mutableStateOf(false) }
    var playerComposed by remember { mutableStateOf(false) }
    var playerCovering by remember { mutableStateOf(false) }
    LaunchedEffect(showLoading) {
        if (!showLoading) {
            delay(4400)
            playerComposed = true
        }
    }
    val openPlayer: () -> Unit = {
        if (!playerOpen) {
            playerOpen = true
            scope.launch {
                if (!playerComposed) {
                    playerComposed = true
                    withFrameNanos { }
                    withFrameNanos { }
                }
                playerPos.animateTo(1f, tween(GamutMotion.PLAYER_LAYER_IN_MS, easing = GamutMotion.EmphasizedDecelerate))
                playerCovering = true
            }
        }
    }
    val closePlayer: () -> Unit = {
        playerOpen = false
        playerCovering = false
        scope.launch { playerPos.animateTo(0f, tween(GamutMotion.PLAYER_LAYER_OUT_MS, easing = GamutMotion.PlayerClose)) }
    }
    val createPos = remember { Animatable(0f) }
    var createOpen by remember { mutableStateOf(false) }
    var createComposed by remember { mutableStateOf(false) }
    var createCovering by remember { mutableStateOf(false) }
    var createSession by remember { mutableIntStateOf(0) }
    LaunchedEffect(showLoading) {
        if (!showLoading) {
            delay(4800)
            createComposed = true
        }
    }
    val openCreate: () -> Unit = {
        if (!createOpen) {
            createOpen = true
            scope.launch {
                if (!createComposed) {
                    createComposed = true
                    withFrameNanos { }
                    withFrameNanos { }
                }
                createPos.animateTo(1f, tween(GamutMotion.TAB_MS, easing = GamutMotion.Standard))
                createCovering = true
            }
        }
    }
    val closeCreate: () -> Unit = {
        createOpen = false
        createCovering = false
        scope.launch {
            createPos.animateTo(0f, tween(GamutMotion.TAB_MS, easing = GamutMotion.Standard))
            createSession++
        }
    }
    val homeShift = remember { derivedStateOf { minOf(homeAlpha.value, 1f - createPos.value) } }
    LaunchedEffect(currentRoute) {
        if (layerOpen && (currentRoute == null || currentRoute == "home")) {
            layerOpen = false
            layerPos.snapTo(0f)
        }
    }
    var showConnect by remember { mutableStateOf(false) }
    var resolvingIsrc by remember { mutableStateOf<String?>(null) }
    var resolveJob by remember { mutableStateOf<Job?>(null) }
    val playback by player.playbackState.collectAsState()
    val loadedTrack by remember { derivedStateOf { (playback as? PlaybackState.Playing)?.track ?: (playback as? PlaybackState.Paused)?.track } }
    val isPlayingNow by remember { derivedStateOf { playback is PlaybackState.Playing } }
    LaunchedEffect(loadedTrack?.isrc) { loadedTrack?.let { selectedTrack = it } }

    val shazamVm = hiltViewModel<ShazamViewModel>()
    val shazamState by shazamVm.state.collectAsState()
    val appLibraryVm = hiltViewModel<LibraryViewModel>()
    LaunchedEffect(appLibraryVm) {
        appLibraryVm.tracksByPlaylist.collect { map -> supportVm.store.recordPlaylistSize(map.values.maxOfOrNull { it.size } ?: 0) }
    }
    var coldStartQuiet by remember { mutableStateOf(false) }
    LaunchedEffect(showLoading) {
        if (!showLoading) {
            delay(SupportConfig.COLD_START_QUIET_MS)
            coldStartQuiet = true
        }
    }
    val localAvailabilityVm = hiltViewModel<LocalAvailabilityViewModel>()
    LaunchedEffect(appLibraryVm, localAvailabilityVm) {
        kotlinx.coroutines.flow.combine(appLibraryVm.favorites, localAvailabilityVm.existing) { favs, existing -> favs to existing }
            .collect { (favs, existing) ->
                if (existing == null) return@collect
                val kept = favs.withoutMissingLocal(existing)
                if (kept.size != favs.size) appLibraryVm.setFavoriteOrder(kept)
            }
    }
    LaunchedEffect(appLibraryVm) { appLibraryVm.duplicateAdds.collect { name -> notifier.info("Already in \u201C$name\u201D") } }
    val recentVm = hiltViewModel<RecentlyPlayedViewModel>()
    LaunchedEffect(loadedTrack?.isrc) { loadedTrack?.let(recentVm::record) }
    val statsVm = hiltViewModel<StatsViewModel>()
    var countedIsrc by rememberSaveable { mutableStateOf<String?>(null) }
    var lastPositionMs by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        snapshotFlow { (playback as? PlaybackState.Playing)?.let { it.track to it.positionMs } }
            .filterNotNull()
            .collect { (track, positionMs) ->
                val isNewSong = track.isrc != countedIsrc
                val startedOver = !isNewSong && positionMs < 2_000L && lastPositionMs > 10_000L
                if (isNewSong || startedOver) {
                    statsVm.record(track)
                    supportVm.store.recordPlay()
                }
                countedIsrc = track.isrc
                lastPositionMs = positionMs
            }
    }
    val context = LocalContext.current
    var resumeAfterShazam by remember { mutableStateOf(false) }
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val startShazam = {
        resumeAfterShazam = playback is PlaybackState.Playing
        if (resumeAfterShazam) player.pause()
        shazamVm.start()
        try {
            ShazamListenService.start(context)
        } catch (e: Exception) { }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> startShazam() }
    val beginAfterMic = {
        val needsNotif = android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotif && !askedNotifications) {
            askedNotifications = true
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startShazam()
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginAfterMic() else shazamVm.permissionDenied()
    }
    val onShazam = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) beginAfterMic() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(shazamState) {
        if (shazamState !is ShazamState.Listening && resumeAfterShazam) {
            resumeAfterShazam = false
            player.resume()
        }
    }
    val artUrl = remember(loadedTrack?.albumArtUrl) { artFor(loadedTrack?.albumArtUrl, 200) }
    val palette = rememberAppPalette(artUrl, appearance)
    val neutralPalette = appNeutralPalette(appearance)
    val appDark = !palette.isLight
    var backgroundVisible by remember { mutableStateOf(true) }
    LaunchedEffect(playerCovering, createCovering) {
        backgroundVisible = !(playerCovering || createCovering)
    }
    val bgAlpha by animateFloatAsState(
        targetValue = if (backgroundVisible) 1f else 0f,
        animationSpec = tween(1),
        label = "background-alpha",
    )
    val appReveal by animateFloatAsState(
        targetValue = if (showLoading) 0f else 1f,
        animationSpec = tween(520, easing = GamutMotion.Standard),
        label = "app-reveal",
    )
    val activity = LocalContext.current as? ComponentActivity
    SideEffect { activity?.window?.setBackgroundDrawable(ColorDrawable(neutralPalette.base.toArgb())) }
    GamutTheme(dark = appDark) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val scale = 0.95f + 0.05f * appReveal
                    scaleX = scale
                    scaleY = scale
                },
            ) {
                GamutBackdrop {
                    AcrylicHost(Modifier.fillMaxSize()) {
                        FlowingBackground(
                            palette = palette,
                            isPlaying = isPlayingNow,
                            visible = backgroundVisible,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = bgAlpha }.acrylicSource(zIndex = -1f),
                        )
                        val removeFromPlaylist = remember(appLibraryVm) {
                            { t: Track, id: String ->
                                id.toLongOrNull()?.let { appLibraryVm.removeTrack(it, t) }
                                Unit
                            }
                        }
                        CompositionLocalProvider(LocalCoverPalette provides palette, LocalSupportNudge provides supportNudge, LocalNotifier provides notifier, LocalArtistPhotoResolver provides photoResolver, LocalPlaylistRemove provides removeFromPlaylist) {
                            val openArtist: (String) -> Unit = { artist -> nav.navigate("artist-name/${Uri.encode(artist)}") }
                            val openConnect = { showConnect = true }
                            val nowPlaying: @Composable () -> Unit = { NowPlayingStrip(player, docked = false, onOpen = openPlayer, onDevicesClick = openConnect) }
                            val nowPlayingDocked: @Composable () -> Unit = { NowPlayingStrip(player, docked = true, onOpen = openPlayer, onDevicesClick = openConnect) }
                            val playTrackWithQueue: (Track, List<Track>) -> Unit = tap@{ track, queue ->
                                if (loadedTrack?.isrc == track.isrc) {
                                    if (!isPlayingNow) player.resume()
                                    openPlayer()
                                    return@tap
                                }
                                if (resolvingIsrc == track.isrc && resolveJob?.isActive == true) return@tap
                                selectedTrack = track
                                resolvingIsrc = track.isrc
                                resolveJob?.cancel()
                                resolveJob = scope.launch {
                                    val deviceUri = track.localUri()
                                    if (deviceUri != null) {
                                        val startIndex = queue.indexOfFirst { it.isrc == track.isrc }
                                        if (startIndex >= 0) player.setQueue(queue, startIndex)
                                        com.gamut.feature.playerui.components.WaveformSource.register(track, deviceUri.toString())
                                        player.play(track, deviceUri.toString())
                                        resolvingIsrc = null
                                        openPlayer()
                                        return@launch
                                    }
                                    val localFile = downloadIndex.pathFor(track.isrc)?.let(::File)?.takeIf { it.exists() }
                                    if (localFile != null) {
                                        val startIndex = queue.indexOfFirst { it.isrc == track.isrc }
                                        if (startIndex >= 0) player.setQueue(queue, startIndex)
                                        com.gamut.feature.playerui.components.WaveformSource.register(track, localFile.absolutePath)
                                        player.play(track, Uri.fromFile(localFile).toString())
                                        resolvingIsrc = null
                                        openPlayer()
                                        return@launch
                                    }
                                    val noticeId = notifier.loading("Loading ${track.title}\u2026")
                                    resolveTrackSource(track).fold(
                                        onSuccess = { source ->
                                            notifier.update(noticeId, "Getting audio\u2026")
                                            audioSources.audioUrl(source.youtubeVideoId).fold(
                                                onSuccess = { audioUrl ->
                                                    val startIndex = queue.indexOfFirst { it.isrc == track.isrc }
                                                    if (startIndex >= 0) player.setQueue(queue, startIndex)
                                                    com.gamut.feature.playerui.components.WaveformSource.register(track, audioUrl)
                                                    player.play(track, audioUrl)
                                                    notifier.dismiss(noticeId)
                                                    openPlayer()
                                                },
                                                onFailure = { notifier.error("Couldn\u2019t play this song") },
                                            )
                                        },
                                        onFailure = { error ->
                                            notifier.error(
                                                when (error) {
                                                    is ResolveFailure.Timeout -> "Connection was slow. Try again."
                                                    is ResolveFailure.NoCandidates -> "No playable source was found."
                                                    is ResolveFailure.LowScore -> "No reliable match was found."
                                                    else -> "Couldn\u2019t play this song"
                                                },
                                            )
                                        },
                                    )
                                    resolvingIsrc = null
                                }
                            }
                            Box(Modifier.fillMaxSize().focusLayer(!playerOpen)) {
                                FocusLive(!playerOpen) {
                                    HomeTabs(
                                        active = (currentRoute == null || currentRoute == "home") && !createOpen,
                                        homeAlpha = homeShift,
                                        showBar = !(playerCovering || createCovering),
                                        searchVm = searchVm,
                                        libraryVm = appLibraryVm,
                                        resolvingIsrc = resolvingIsrc,
                                        selectedTrack = selectedTrack,
                                        nowPlayingIsrc = loadedTrack?.isrc,
                                        onTrackSelected = playTrackWithQueue,
                                        onArtistSelected = { artistId -> nav.navigate("artist-real/$artistId") },
                                        onAlbumSelected = { item -> nav.navigate(albumRoute(item)) },
                                        onTrackArtist = { t -> openArtist(t.artist) },
                                        onOpenPlaylist = { id -> nav.navigate(if (id == "liked") "favorites" else "playlist/$id") },
                                        onCreatePlaylist = { openCreate() },
                                        onOpenLocal = { nav.navigate("local") },
                                        openSearchTick = openSearchTick,
                                        openLibraryTick = openLibraryTick,
                                        onProfile = { nav.navigate("settings") { launchSingleTop = true } },
                                        onShazam = onShazam,
                                        nowPlaying = nowPlaying,
                                    )
                                    SettingsLayer(
                                        composed = settingsComposed || currentRoute == "settings",
                                        active = currentRoute == "settings",
                                        childOpen = layerOpen,
                                        x = settingsX,
                                        subPagePos = layerPos,
                                    ) {
                                        SettingsScreen(
                                            onBack = { nav.popBackStack() },
                                            onLikedSongs = { nav.navigate("favorites") },
                                            onPlaylists = {
                                                openLibraryTick++
                                                nav.popBackStack("home", false)
                                            },
                                            onRecentlyPlayed = { openLayer(SettingsPage.Recent) },
                                            onProfile = { openLayer(SettingsPage.Profile) },
                                            onAppearance = { openLayer(SettingsPage.Appearance) },
                                            onPlaybackAudio = { openLayer(SettingsPage.Playback) },
                                            onStorage = { openLayer(SettingsPage.Storage) },
                                            onFeedback = { openLayer(SettingsPage.Feedback) },
                                            onAbout = { openLayer(SettingsPage.About) },
                                            nowPlaying = nowPlayingDocked,
                                            onCheckForUpdate = { updateVm.checkNow() },
                                        )
                                    }
                                    SlideLayer(composed = SettingsPage.Recent in composedPages, selected = layerPage == SettingsPage.Recent, open = layerOpen, position = layerPos, onClose = closeLayer) {
                                        val recent by recentVm.tracks.collectAsState()
                                        RecentlyPlayedScreen(tracks = recent, onBack = closeLayer, onTrackSelected = playTrackWithQueue, nowPlaying = nowPlayingDocked)
                                    }
                                    SlideLayer(composed = SettingsPage.Appearance in composedPages, selected = layerPage == SettingsPage.Appearance, open = layerOpen, position = layerPos, onClose = closeLayer) {
                                        AppearanceScreen(onBack = closeLayer, nowPlaying = nowPlayingDocked)
                                    }
                                    SlideLayer(composed = SettingsPage.Playback in composedPages, selected = layerPage == SettingsPage.Playback, open = layerOpen, position = layerPos, onClose = closeLayer) {
                                        PlaybackSettingsScreen(onBack = closeLayer, nowPlaying = nowPlayingDocked)
                                    }
                                    SlideLayer(composed = SettingsPage.Storage in composedPages, selected = layerPage == SettingsPage.Storage, open = layerOpen, position = layerPos, onClose = closeLayer) {
                                        StorageSettingsScreen(onBack = closeLayer, nowPlaying = nowPlayingDocked)
                                    }
                                    SlideLayer(composed = SettingsPage.Profile in composedPages, selected = layerPage == SettingsPage.Profile, open = layerOpen, position = layerPos, onClose = closeLayer) {
                                        ProfileScreen(onBack = closeLayer, nowPlaying = nowPlayingDocked)
                                    }
                                    SlideLayer(composed = SettingsPage.Feedback in composedPages, selected = layerPage == SettingsPage.Feedback, open = layerOpen, position = layerPos, onClose = closeLayer) {
                                        FeedbackScreen(onBack = closeLayer, nowPlaying = nowPlayingDocked)
                                    }
                                    SlideLayer(composed = SettingsPage.About in composedPages, selected = layerPage == SettingsPage.About, open = layerOpen, position = layerPos, onClose = closeLayer) {
                                        AboutScreen(onBack = closeLayer, nowPlaying = nowPlayingDocked)
                                    }
                                    SlideLayer(composed = createComposed, selected = true, open = createOpen, position = createPos, onClose = closeCreate) {
                                        key(createSession) {
                                            CreatePlaylistScreen(
                                                viewModel = appLibraryVm,
                                                selectedTrack = selectedTrack,
                                                onBack = closeCreate,
                                                active = LocalPageActive.current,
                                            )
                                        }
                                    }
                                    NavHost(
                                        navController = nav,
                                        startDestination = "home",
                                        enterTransition = { GamutMotion.navPushEnter },
                                        exitTransition = { GamutMotion.navPushExit },
                                        popEnterTransition = { GamutMotion.navPopEnter },
                                        popExitTransition = { GamutMotion.navPopExit },
                                    ) {
                                        composable("home") {}
                                        composable(
                                            "settings",
                                            enterTransition = { EnterTransition.None },
                                            exitTransition = { ExitTransition.None },
                                            popEnterTransition = { EnterTransition.None },
                                            popExitTransition = { ExitTransition.None },
                                        ) {}
                                        composable("local") {
                                            val localVm = hiltViewModel<LocalMusicViewModel>()
                                            Settled {
                                                LocalMusicScreen(
                                                    libraryVm = appLibraryVm,
                                                    onBack = { nav.popBackStack() },
                                                    onTrackSelected = playTrackWithQueue,
                                                    nowPlayingIsrc = loadedTrack?.isrc,
                                                    viewModel = localVm,
                                                    nowPlaying = nowPlayingDocked,
                                                )
                                            }
                                        }
                                        composable("favorites") {
                                            val favorites by appLibraryVm.favorites.collectAsState()
                                            val downloadsVm = hiltViewModel<PlaylistDownloadsViewModel>()
                                            Settled {
                                                FavoritesScreen(
                                                    tracks = favorites,
                                                    onBack = { nav.popBackStack() },
                                                    onTrackSelected = playTrackWithQueue,
                                                    onShuffle = { list ->
                                                        player.setShuffle(true)
                                                        list.randomOrNull()?.let { playTrackWithQueue(it, list) }
                                                    },
                                                    onRemove = { appLibraryVm.toggleFavorite(it) },
                                                    onReorder = { appLibraryVm.setFavoriteOrder(it) },
                                                    onTrackArtist = { t -> openArtist(t.artist) },
                                                    nowPlayingIsrc = loadedTrack?.isrc,
                                                    downloads = downloadsVm,
                                                    nowPlaying = nowPlayingDocked,
                                                )
                                            }
                                        }
                                        composable("playlist/{id}") { entry ->
                                            val playlistVm = hiltViewModel<com.gamut.feature.library.PlaylistViewModel>()
                                            val downloadsVm = hiltViewModel<PlaylistDownloadsViewModel>()
                                            val storedPlaylistTracks by playlistVm.tracks.collectAsState()
                                            val existingLocal by localAvailabilityVm.existing.collectAsState()
                                            val playlistTracks = remember(storedPlaylistTracks, existingLocal) { storedPlaylistTracks.withoutMissingLocal(existingLocal) }
                                            val allPlaylists by appLibraryVm.playlists.collectAsState()
                                            val favoriteIsrcs by appLibraryVm.favoriteIsrcs.collectAsState()
                                            val id = entry.arguments?.getString("id").orEmpty()
                                            Settled {
                                                PlaylistScreen(
                                                    playlistId = id,
                                                    title = allPlaylists.firstOrNull { it.id.toString() == id }?.name ?: "Playlist",
                                                    onBack = { nav.popBackStack() },
                                                    onPlay = { list ->
                                                        player.setShuffle(false)
                                                        list.firstOrNull()?.let { playTrackWithQueue(it, list) }
                                                    },
                                                    onShuffle = { list ->
                                                        player.setShuffle(true)
                                                        list.randomOrNull()?.let { playTrackWithQueue(it, list) }
                                                    },
                                                    onTrackSelected = { track -> playTrackWithQueue(track, playlistTracks) },
                                                    onAddSongs = {
                                                        openSearchTick++
                                                        nav.popBackStack("home", false)
                                                    },
                                                    nowPlayingIsrc = loadedTrack?.isrc,
                                                    favoriteIsrcs = favoriteIsrcs,
                                                    existingLocalIsrcs = existingLocal,
                                                    onToggleFavorite = appLibraryVm::toggleFavorite,
                                                    onTrackArtist = { t -> openArtist(t.artist) },
                                                    viewModel = playlistVm,
                                                    downloads = downloadsVm,
                                                    nowPlaying = nowPlayingDocked,
                                                )
                                            }
                                        }
                                        composable("artist/{artistId}") {
                                            val vm = hiltViewModel<ArtistViewModel>()
                                            Settled {
                                                ArtistRoute(
                                                    viewModel = vm,
                                                    libraryVm = appLibraryVm,
                                                    onOpenArtist = openArtist,
                                                    onBack = { nav.popBackStack() },
                                                    onTrackSelected = playTrackWithQueue,
                                                    onAlbumSelected = { item -> nav.navigate(albumRoute(item)) },
                                                    nowPlayingIsrc = loadedTrack?.isrc,
                                                    nowPlaying = nowPlayingDocked,
                                                )
                                            }
                                        }
                                        composable("artist-real/{artistId}") {
                                            val vm = hiltViewModel<ArtistViewModel>()
                                            Settled {
                                                ArtistRoute(
                                                    viewModel = vm,
                                                    libraryVm = appLibraryVm,
                                                    onOpenArtist = openArtist,
                                                    onBack = { nav.popBackStack() },
                                                    onTrackSelected = playTrackWithQueue,
                                                    onAlbumSelected = { item -> nav.navigate(albumRoute(item)) },
                                                    nowPlayingIsrc = loadedTrack?.isrc,
                                                    nowPlaying = nowPlayingDocked,
                                                )
                                            }
                                        }
                                        composable("artist-name/{artistName}") {
                                            val vm = hiltViewModel<ArtistViewModel>()
                                            Settled {
                                                ArtistRoute(
                                                    viewModel = vm,
                                                    libraryVm = appLibraryVm,
                                                    onOpenArtist = openArtist,
                                                    onBack = { nav.popBackStack() },
                                                    onTrackSelected = playTrackWithQueue,
                                                    onAlbumSelected = { item -> nav.navigate(albumRoute(item)) },
                                                    nowPlayingIsrc = loadedTrack?.isrc,
                                                    nowPlaying = nowPlayingDocked,
                                                )
                                            }
                                        }
                                        composable(
                                            "album/{id}?title={title}&subtitle={subtitle}&art={art}",
                                            arguments = listOf("title", "subtitle", "art").map { navArgument(it) { defaultValue = "" } },
                                        ) {
                                            val vm = hiltViewModel<AlbumViewModel>()
                                            Settled {
                                                AlbumRoute(
                                                    viewModel = vm,
                                                    libraryVm = appLibraryVm,
                                                    onOpenArtist = openArtist,
                                                    onBack = { nav.popBackStack() },
                                                    onTrackSelected = playTrackWithQueue,
                                                    nowPlayingIsrc = loadedTrack?.isrc,
                                                    nowPlaying = nowPlayingDocked,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            PlayerLayer(composed = playerComposed, open = playerOpen, position = playerPos) { active ->
                                val libraryVm = appLibraryVm
                                val favoriteIsrcs by libraryVm.favoriteIsrcs.collectAsState()
                                val playlists by libraryVm.playlists.collectAsState()
                                val tracksByPlaylist by libraryVm.tracksByPlaylist.collectAsState()
                                val playlistInfo by libraryVm.playlistInfo.collectAsState()
                                PlayerScreen(
                                    player,
                                    onBack = closePlayer,
                                    active = active,
                                    favoriteIsrcs = favoriteIsrcs,
                                    onToggleFavorite = { t ->
                                        libraryVm.toggleFavorite(t)
                                        if (t.isrc in favoriteIsrcs) notifier.info("Removed from Favorites") else notifier.success("Added to Favorites")
                                    },
                                    playlists = playlists.map { PlaylistPickerItem(id = it.id.toString(), name = it.name, coverUrl = it.coverUri ?: playlistInfo[it.id]?.coverUrl) },
                                    playlistIdsWithTrack = loadedTrack?.let { playlistIdsContaining(tracksByPlaylist, it) } ?: emptySet(),
                                    onAddToPlaylist = { t, playlistId ->
                                        playlists.firstOrNull { it.id.toString() == playlistId }?.let { libraryVm.addTrack(it.id, t) }
                                    },
                                    onRemoveFromPlaylist = { t, playlistId ->
                                        playlists.firstOrNull { it.id.toString() == playlistId }?.let { libraryVm.removeTrack(it.id, t) }
                                    },
                                    onGoToArtist = { t ->
                                        closePlayer()
                                        nav.navigate("artist-name/${Uri.encode(t.artist)}")
                                    },
                                )
                            }
                            BackHandler(enabled = !homeShowing && !playerOpen && !layerOpen && !createOpen) { nav.popBackStack() }
                            BackHandler(enabled = layerOpen && currentRoute == "settings" && !playerOpen) { closeLayer() }
                            BackHandler(enabled = createOpen && !playerOpen) { closeCreate() }
                            if (shazamState !is ShazamState.Idle) {
                                val shazamLevel by shazamVm.level.collectAsState()
                                val favoriteIsrcs by appLibraryVm.favoriteIsrcs.collectAsState()
                                val libraryPlaylists by appLibraryVm.playlists.collectAsState()
                                val playlistInfo by appLibraryVm.playlistInfo.collectAsState()
                                val tracksByPlaylist by appLibraryVm.tracksByPlaylist.collectAsState()
                                val playlists = remember(libraryPlaylists, playlistInfo, tracksByPlaylist) {
                                    libraryPlaylists.map { TrackPlaylistOption(id = it.id.toString(), name = it.name, coverUrl = it.coverUri ?: playlistInfo[it.id]?.coverUrl, trackIsrcs = tracksByPlaylist[it.id].orEmpty().mapTo(HashSet()) { t -> t.isrc }) }
                                }
                                CoverTheme(palette) {
                                    ShazamSheet(
                                        state = shazamState,
                                        level = shazamLevel,
                                        favoriteIsrcs = favoriteIsrcs,
                                        playlists = playlists,
                                        onDismiss = shazamVm::dismiss,
                                        onRetry = onShazam,
                                        onOpenSettings = {
                                            context.startActivity(
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                            )
                                        },
                                        onPlay = { track ->
                                            shazamVm.dismiss()
                                            playTrackWithQueue(track, emptyList())
                                        },
                                        onToggleFavorite = appLibraryVm::toggleFavorite,
                                        onAddToQueue = { track -> player.addToQueue(track) },
                                        onAddToPlaylist = { track, id -> appLibraryVm.addTrack(id.toLong(), track) },
                                        onTrackArtist = { track ->
                                            shazamVm.dismiss()
                                            openArtist(track.artist)
                                        },
                                    )
                                }
                            }
                            val supportSafe = !showLoading && coldStartQuiet && !isPlayingNow && !playerOpen && !createOpen &&
                                shazamState is ShazamState.Idle && !showConnect
                            CoverTheme(palette) { SupportSheetHost(vm = supportVm, safeMoment = supportSafe) }
                            CoverTheme(if (playerOpen || createOpen) neutralPalette else palette) { NoticeHost() }
                            if (showConnect) {
                                CoverTheme(palette) {
                                    ConnectSheet(accent = MaterialTheme.colorScheme.primary, onDismiss = { showConnect = false })
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                showLoading,
                exit = fadeOut(tween(450, easing = GamutMotion.Standard)) +
                    scaleOut(tween(450, easing = GamutMotion.Standard), targetScale = 1.05f),
            ) {
                GamutLoadingScreen(dataReady = dataReady, onFinished = { showLoading = false })
            }
        }
    }
}

@Composable
private fun NowPlayingStrip(player: MusicPlayer, docked: Boolean, onOpen: () -> Unit, onDevicesClick: () -> Unit) {
    val outputs = rememberOutputDevices()
    val state by player.playbackState.collectAsState()
    val trackState by remember { derivedStateOf { (state as? PlaybackState.Playing)?.track ?: (state as? PlaybackState.Paused)?.track } }
    val track: Track? = trackState
    val isPlaying by remember { derivedStateOf { state is PlaybackState.Playing } }
    val isLoading by remember { derivedStateOf { state is PlaybackState.Loading } }
    val loadProgress by player.loadProgress.collectAsState()
    val streamDuration by player.streamDurationMs.collectAsState()
    val position = (state as? PlaybackState.Playing)?.positionMs ?: (state as? PlaybackState.Paused)?.positionMs ?: 0L
    var last by remember { mutableStateOf(track) }
    if (track != null && track != last) last = track
    val shown = track ?: last
    val progress by animateFloatAsState(
        targetValue = if (shown == null) 0f else (position.toFloat() / (if (streamDuration > 0L) streamDuration else shown.durationMs).coerceAtLeast(1L)).coerceIn(0f, 1f),
        animationSpec = tween(500, easing = LinearEasing),
        label = "strip-progress",
    )
    AnimatedVisibility(
        visible = track != null || (isLoading && last != null),
        enter = expandVertically(tween(280)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(220)) + fadeOut(tween(160)),
    ) {
        if (shown != null) {
            MiniPlayerStrip(
                title = shown.title,
                artist = shown.artist,
                artUrl = remember(shown.albumArtUrl) { artFor(shown.albumArtUrl, 200) },
                isPlaying = isPlaying,
                isLoading = isLoading,
                loadProgress = loadProgress,
                progress = progress,
                onOpen = onOpen,
                onTogglePlay = { if (isPlaying) player.pause() else player.resume() },
                onNext = player::skipNext,
                onPrevious = player::skipPrevious,
                onDevicesClick = onDevicesClick,
                devicesActive = outputs.any { it.isActive && it.isBluetooth },
                dockedToEdge = docked,
            )
        }
    }
}
private fun artFor(url: String?, size: Int) = if (url != null && (url.startsWith("file:") || url.startsWith("content:"))) url else squareArtUrl(url, size)

private fun albumRoute(item: DiscoverItem) = "album/${Uri.encode(item.id)}?title=${Uri.encode(item.title)}&subtitle=${Uri.encode(item.subtitle)}&art=${Uri.encode(item.imageUrl)}"

private const val TAB_SWITCH_MS = GamutMotion.TAB_MS
private const val TAB_FLING_VELOCITY = 700f

@Composable
private fun AnimatedVisibilityScope.Settled(content: @Composable () -> Unit) {
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(transition.currentState) { if (transition.currentState == EnterExitState.Visible) settled = true }
    LaunchedEffect(Unit) {
        delay(700)
        settled = true
    }
    CompositionLocalProvider(LocalNavSettled provides settled) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTabs(
    active: Boolean,
    homeAlpha: State<Float>,
    showBar: Boolean,
    searchVm: SearchViewModel,
    libraryVm: LibraryViewModel,
    resolvingIsrc: String?,
    selectedTrack: Track?,
    nowPlayingIsrc: String?,
    onTrackSelected: (Track, List<Track>) -> Unit,
    onArtistSelected: (Long) -> Unit,
    onAlbumSelected: (DiscoverItem) -> Unit,
    onTrackArtist: (Track) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenLocal: () -> Unit,
    openSearchTick: Int,
    openLibraryTick: Int,
    onProfile: () -> Unit,
    onShazam: () -> Unit,
    nowPlaying: @Composable () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val followVm = hiltViewModel<com.gamut.core.sync.FollowViewModel>()
    val followedEntities by followVm.artists.collectAsState()
    val followedArtists = remember(followedEntities) {
        followedEntities.orEmpty().map { com.gamut.feature.library.LibraryArtist(it.artistKey, it.name, it.imageUrl) }
    }
    val accountVm = hiltViewModel<AccountViewModel>()
    val supportVm = hiltViewModel<SupportViewModel>()
    val profileName by accountVm.displayName.collectAsState()
    val profileId by accountVm.userId.collectAsState()
    val avatar by accountVm.avatar.collectAsState()
    val favoriteIsrcs by libraryVm.favoriteIsrcs.collectAsState()
    val libraryPlaylists by libraryVm.playlists.collectAsState()
    val playlistInfo by libraryVm.playlistInfo.collectAsState()
    val tracksByPlaylist by libraryVm.tracksByPlaylist.collectAsState()
    val playlistOptions = remember(libraryPlaylists, playlistInfo, tracksByPlaylist) {
        libraryPlaylists.map { TrackPlaylistOption(id = it.id.toString(), name = it.name, coverUrl = it.coverUri ?: playlistInfo[it.id]?.coverUrl, trackIsrcs = tracksByPlaylist[it.id].orEmpty().mapTo(HashSet()) { t -> t.isrc }) }
    }
    var focusSearchTick by remember { mutableIntStateOf(0) }
    val position = remember { Animatable(tab.toFloat()) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val palette = LocalCoverPalette.current
    val shownTab by remember { derivedStateOf { if (position.value > 0.5f) 1 else 0 } }
    val barComposed by remember { derivedStateOf { homeAlpha.value > 0f } }

    fun goTo(target: Int) {
        tab = target
        scope.launch { position.animateTo(target.toFloat(), tween(TAB_SWITCH_MS, easing = GamutMotion.Standard)) }
    }

    LaunchedEffect(openSearchTick) {
        if (openSearchTick > 0) {
            goTo(0)
            focusSearchTick++
        }
    }
    LaunchedEffect(openLibraryTick) { if (openLibraryTick > 0) goTo(1) }
    BackHandler(enabled = active && tab == 1) { goTo(0) }
    if (active) TransparentStatusBar(darkIcons = palette.isLight)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val shown = homeAlpha.value
                translationX = -(1f - shown) * size.width
                if (shown <= 0f) alpha = 0f
            }
            .then(if (active) Modifier else Modifier.consumeTouches().clearAndSetSemantics {})
            .focusLayer(active),
    ) {
        FocusLive(active) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        enabled = active,
                        state = rememberDraggableState { dx ->
                            scope.launch { position.snapTo((position.value - dx / widthPx).coerceIn(0f, 1f)) }
                        },
                        onDragStarted = { position.stop() },
                        onDragStopped = { velocity ->
                            goTo(
                                when {
                                    velocity < -TAB_FLING_VELOCITY -> 1
                                    velocity > TAB_FLING_VELOCITY -> 0
                                    position.value > 0.5f -> 1
                                    else -> 0
                                },
                            )
                        },
                    ),
            ) {
                TabLayer(page = 0, position = { position.value }, active = shownTab == 0) {
                    SearchScreen(
                        viewModel = searchVm,
                        resolvingIsrc = resolvingIsrc,
                        onTrackSelected = onTrackSelected,
                        onArtistSelected = onArtistSelected,
                        onAlbumSelected = onAlbumSelected,
                        onShazam = onShazam,
                        onProfile = onProfile,
                        profileName = profileName,
                        profileSeed = profileId,
                        profileImage = avatar,
                        profileModifier = Modifier.supportNudgeCorona(),
                        headerExtra = { SupportHeartButton { supportVm.store.requestDonateSheet() } },
                        nowPlayingIsrc = nowPlayingIsrc,
                        favoriteIsrcs = favoriteIsrcs,
                        onToggleFavorite = libraryVm::toggleFavorite,
                        playlists = playlistOptions,
                        onAddToPlaylist = { track, playlistId ->
                            libraryPlaylists.firstOrNull { it.id.toString() == playlistId }?.let { libraryVm.addTrack(it.id, track) }
                        },
                        onTrackArtist = onTrackArtist,
                        barHeight = barHeight,
                        focusSearchTick = focusSearchTick,
                    )
                }
                TabLayer(page = 1, position = { position.value }, active = shownTab == 1) {
                    LibraryScreen(
                        artists = followedArtists,
                        artistsLoading = followedEntities == null,
                        onOpenArtist = { key ->
                            key.removePrefix("deezer:").toLongOrNull()?.let(onArtistSelected)
                        },
                        viewModel = libraryVm,
                        selectedTrack = selectedTrack,
                        onOpenPlaylist = onOpenPlaylist,
                        onCreatePlaylist = onCreatePlaylist,
                        onOpenLocal = onOpenLocal,
                        onOpenSearch = {
                            goTo(0)
                            focusSearchTick++
                        },
                        barHeight = barHeight,
                    )
                }
            }
            if (showBar && barComposed) {
                CoverTheme(palette) {
                    GamutBottomBar(
                        items = listOf(
                            BottomBarItem("Discover", PhosphorIcons.Bold.MagnifyingGlass, selected = shownTab == 0, onClick = if (shownTab == 0) null else ({ goTo(0) })),
                            BottomBarItem("Library", PhosphorIcons.Bold.BookmarkSimple, selected = shownTab == 1, onClick = if (shownTab == 1) null else ({ goTo(1) })),
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                        nowPlaying = nowPlaying,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsLayer(
    composed: Boolean,
    active: Boolean,
    childOpen: Boolean,
    x: Animatable<Float, AnimationVector1D>,
    subPagePos: Animatable<Float, AnimationVector1D>,
    content: @Composable () -> Unit,
) {
    if (!composed) return
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = (x.value - subPagePos.value) * size.width
                if (x.value >= 1f) alpha = 0f
            }
            .then(if (active) Modifier else Modifier.clearAndSetSemantics {})
            .focusLayer(active && !childOpen),
    ) { FocusLive(active && !childOpen) { CompositionLocalProvider(LocalPageActive provides active) { content() } } }
}

private enum class SettingsPage { Recent, Appearance, Playback, Storage, Profile, Feedback, About }

@Composable
private fun SlideLayer(
    composed: Boolean,
    selected: Boolean,
    open: Boolean,
    position: Animatable<Float, AnimationVector1D>,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!composed) return
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableFloatStateOf(1f) }
    val live = selected && open
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                val rel = if (selected) 1f - position.value else 1f
                translationX = rel * size.width
                if (rel >= 1f) alpha = 0f
            }
            .then(if (live) Modifier else Modifier.clearAndSetSemantics {})
            .focusLayer(live)
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = live,
                state = rememberDraggableState { dx ->
                    scope.launch { position.snapTo((position.value - dx / widthPx).coerceIn(0f, 1f)) }
                },
                onDragStarted = { position.stop() },
                onDragStopped = { velocity ->
                    val close = velocity > TAB_FLING_VELOCITY || (velocity > -TAB_FLING_VELOCITY && position.value < 0.5f)
                    if (close) onClose() else position.animateTo(1f, tween(TAB_SWITCH_MS, easing = GamutMotion.Standard))
                },
            ),
    ) {
        FocusLive(live) { CompositionLocalProvider(LocalPageActive provides live) { content() } }
    }
}

@Composable
private fun PlayerLayer(
    composed: Boolean,
    open: Boolean,
    position: Animatable<Float, AnimationVector1D>,
    content: @Composable (active: Boolean) -> Unit,
) {
    if (!composed) return
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val rel = 1f - position.value
                translationY = rel * size.height
                if (rel >= 1f) alpha = 0f
            }
            .focusLayer(open)
            .then(
                if (open) {
                    Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                } else {
                    Modifier.clearAndSetSemantics {}
                },
            ),
    ) { FocusLive(open) { content(open) } }
}

@Composable
private fun TabLayer(page: Int, position: () -> Float, active: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val rel = page - position()
                translationX = rel * size.width
                if (abs(rel) >= 1f) alpha = 0f
            }
            .then(if (active) Modifier else Modifier.clearAndSetSemantics {})
            .focusLayer(active),
    ) { FocusLive(active) { content() } }
}

private fun Modifier.consumeTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

@Composable
private fun ArtistRoute(
    viewModel: ArtistViewModel,
    libraryVm: LibraryViewModel,
    onBack: () -> Unit,
    onTrackSelected: (Track, List<Track>) -> Unit,
    onAlbumSelected: (DiscoverItem) -> Unit,
    onOpenArtist: (String) -> Unit,
    nowPlayingIsrc: String?,
    nowPlaying: @Composable () -> Unit,
) {
    val favoriteIsrcs by libraryVm.favoriteIsrcs.collectAsState()
    val libraryPlaylists by libraryVm.playlists.collectAsState()
    val playlistInfo by libraryVm.playlistInfo.collectAsState()
    val tracksByPlaylist by libraryVm.tracksByPlaylist.collectAsState()
    val playlistOptions = remember(libraryPlaylists, playlistInfo, tracksByPlaylist) {
        libraryPlaylists.map { TrackPlaylistOption(id = it.id.toString(), name = it.name, coverUrl = it.coverUri ?: playlistInfo[it.id]?.coverUrl, trackIsrcs = tracksByPlaylist[it.id].orEmpty().mapTo(HashSet()) { t -> t.isrc }) }
    }
    val followVm = hiltViewModel<com.gamut.core.sync.FollowViewModel>()
    val followedKeys by followVm.followedKeys.collectAsState()
    DiscoverArtistScreen(
        followedKeys = followedKeys,
        onToggleFollow = followVm::toggle,
        viewModel = viewModel,
        onBack = onBack,
        onTrackSelected = onTrackSelected,
        onAlbumSelected = onAlbumSelected,
        onSimilarArtist = onOpenArtist,
        onTrackArtist = { t -> onOpenArtist(t.artist) },
        favoriteIsrcs = favoriteIsrcs,
        onToggleFavorite = libraryVm::toggleFavorite,
        playlists = playlistOptions,
        onAddToPlaylist = { track, playlistId ->
            libraryPlaylists.firstOrNull { it.id.toString() == playlistId }?.let { libraryVm.addTrack(it.id, track) }
        },
        nowPlayingIsrc = nowPlayingIsrc,
        nowPlaying = nowPlaying,
    )
}

@Composable
private fun AlbumRoute(
    viewModel: AlbumViewModel,
    libraryVm: LibraryViewModel,
    onBack: () -> Unit,
    onTrackSelected: (Track, List<Track>) -> Unit,
    onOpenArtist: (String) -> Unit,
    nowPlayingIsrc: String?,
    nowPlaying: @Composable () -> Unit,
) {
    val favoriteIsrcs by libraryVm.favoriteIsrcs.collectAsState()
    val libraryPlaylists by libraryVm.playlists.collectAsState()
    val playlistInfo by libraryVm.playlistInfo.collectAsState()
    val tracksByPlaylist by libraryVm.tracksByPlaylist.collectAsState()
    val playlistOptions = remember(libraryPlaylists, playlistInfo, tracksByPlaylist) {
        libraryPlaylists.map { TrackPlaylistOption(id = it.id.toString(), name = it.name, coverUrl = it.coverUri ?: playlistInfo[it.id]?.coverUrl, trackIsrcs = tracksByPlaylist[it.id].orEmpty().mapTo(HashSet()) { t -> t.isrc }) }
    }
    AlbumScreen(
        viewModel = viewModel,
        onBack = onBack,
        onTrackSelected = onTrackSelected,
        onTrackArtist = { t -> onOpenArtist(t.artist) },
        favoriteIsrcs = favoriteIsrcs,
        onToggleFavorite = libraryVm::toggleFavorite,
        playlists = playlistOptions,
        onAddToPlaylist = { track, playlistId ->
            libraryPlaylists.firstOrNull { it.id.toString() == playlistId }?.let { libraryVm.addTrack(it.id, track) }
        },
        nowPlayingIsrc = nowPlayingIsrc,
        nowPlaying = nowPlaying,
    )
}