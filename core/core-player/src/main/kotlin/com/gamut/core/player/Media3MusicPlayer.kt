package com.gamut.core.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.gamut.core.model.PlaybackState
import com.gamut.core.model.QueueState
import com.gamut.core.model.RepeatMode
import com.gamut.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
@Singleton
class Media3MusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: PlaybackSettings,
) : MusicPlayer {
    private val cacheFactory: CacheDataSource.Factory by lazy {
        val cache = SimpleCache(
            File(context.cacheDir, "audio"),
            LeastRecentlyUsedCacheEvictor(CACHE_MAX_BYTES),
            StandaloneDatabaseProvider(context),
        )
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private val sound = SoundSettings.get(context)

    private val musicAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private class Slot(val mod: ModAudioProcessor, val engine: SoundEngineProcessor, val exo: ExoPlayer)

    private var activeSlot: Slot? = null
    private var standbySlot: Slot? = null
    private var outgoingSlot: Slot? = null

    private val active: Slot get() = activeSlot ?: createPrimary()
    private val player: ExoPlayer get() = active.exo

    private fun createPrimary(): Slot {
        val slot = newSlot(handleFocus = true)
        activeSlot = slot
        startSettingsObservers()
        return slot
    }

    private fun allSlots(): List<Slot> = listOfNotNull(activeSlot, standbySlot, outgoingSlot)

    private fun newSlot(handleFocus: Boolean): Slot {
        val mod = ModAudioProcessor()
        val engine = SoundEngineProcessor()
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(mod, engine))
                .build()
        }
        val exo = ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DataSource.Factory {
                        SchemeRoutingDataSource(local = DefaultDataSource.Factory(context).createDataSource(), remote = cacheFactory.createDataSource())
                    },
                ),
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(30_000, 90_000, BUFFER_FOR_PLAYBACK_MS, 3_000)
                    .build(),
            )
            .setAudioAttributes(musicAttributes, handleFocus)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                    .build()
            }
        return Slot(mod, engine, exo).also { slot ->
            attachPlayerListener(slot)
            applyMod(slot, ModController.state.value.settings)
            applyPlaybackSettings(slot, settings.state.value)
            engine.params = sound.state.value.toParams()
        }
    }

    private fun startSettingsObservers() {
        scope.launch { ModController.state.collect { st -> allSlots().forEach { applyMod(it, st.settings) } } }
        scope.launch {
            settings.state.collect { st ->
                allSlots().forEach { applyPlaybackSettings(it, st) }
                onCrossfadeChanged()
            }
        }
        scope.launch {
            sound.state.collect { st ->
                val params = st.toParams()
                allSlots().forEach { it.engine.params = params }
            }
        }
    }

    override fun warmUp() {
        player.playbackState
    }

    internal val sessionPlayer: ExoPlayer get() = player

    internal val mediaSessionPlayer: Player get() = queueForwardingPlayer(player)

    internal var onSessionPlayerChanged: ((Player) -> Unit)? = null

    private var externalStartPending = false

    internal fun prepareExternalQueue(tracks: List<Track>, startIndex: Int) {
        setQueue(tracks, startIndex)
        externalStartPending = true
    }

    private fun startPreparedQueue() {
        if (!externalStartPending) return
        externalStartPending = false
        skipTo(queueFlow.value.currentIndex)
    }

    private fun queueForwardingPlayer(exo: ExoPlayer): Player = object : ForwardingPlayer(exo) {
        private val queueCommands = intArrayOf(
            COMMAND_SEEK_TO_NEXT,
            COMMAND_SEEK_TO_PREVIOUS,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )

        override fun getAvailableCommands(): Player.Commands {
            val builder = super.getAvailableCommands().buildUpon()
            queueCommands.forEach { builder.add(it) }
            return builder.build()
        }

        override fun isCommandAvailable(command: Int): Boolean = command in queueCommands || super.isCommandAvailable(command)

        override fun hasNextMediaItem(): Boolean = true
        override fun hasPreviousMediaItem(): Boolean = true
        override fun seekToNext() = skipNext()
        override fun seekToNextMediaItem() = skipNext()
        override fun seekToPrevious() = skipPrevious()
        override fun seekToPreviousMediaItem() = skipPrevious()
        override fun setMediaItems(mediaItems: MutableList<MediaItem>) = startPreparedQueue()
        override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) = startPreparedQueue()
        override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) = startPreparedQueue()
        override fun setMediaItem(mediaItem: MediaItem) = startPreparedQueue()
        override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) = startPreparedQueue()
        override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) = startPreparedQueue()
        override fun addMediaItem(mediaItem: MediaItem) {}
        override fun addMediaItem(index: Int, mediaItem: MediaItem) {}
        override fun addMediaItems(mediaItems: MutableList<MediaItem>) {}
        override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {}
        override fun play() = resume()
        override fun pause() = this@Media3MusicPlayer.pause()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    private val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val playbackState: StateFlow<PlaybackState> = state

    private val shuffle = MutableStateFlow(false)
    override val shuffleEnabled: StateFlow<Boolean> = shuffle

    private val repeat = MutableStateFlow(RepeatMode.OFF)
    override val repeatMode: StateFlow<RepeatMode> = repeat

    private val queueFlow = MutableStateFlow(QueueState())
    override val queue: StateFlow<QueueState> = queueFlow

    private val progress = MutableStateFlow(0f)
    override val loadProgress: StateFlow<Float> = progress
    private var bufferBase = RESOLVE_END
    private var progressJob: Job? = null

    private val items = mutableListOf<Track>()
    private var order = mutableListOf<Int>()
    private var pos = -1

    private var currentTrack: Track? = null
    private var loadedIsrc: String? = null
    private var positionJob: Job? = null
    private var loadJob: Job? = null
    private var resolver: TrackSourceResolver? = null

    private var transitioning = false
        set(value) {
            field = value
            updateTransitionLock(value)
            updateAwaiting()
        }

    private val transitionLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Gamut:transition").apply { setReferenceCounted(false) }
    private fun updateTransitionLock(on: Boolean) {
        runCatching {
            if (on) {
                transitionLock.acquire(TRANSITION_LOCK_MS)
            } else if (transitionLock.isHeld) {
                transitionLock.release()
            }
        }
    }

    internal val keepForeground: Boolean get() = transitioning || loadJob?.isActive == true

    private val streamDuration = MutableStateFlow(0L)
    override val streamDurationMs: StateFlow<Long> = streamDuration

    private fun publishStreamDuration() {
        val d = player.duration
        val ok = d != C.TIME_UNSET && d > 0 && player.currentMediaItem?.mediaId == currentTrack?.isrc
        streamDuration.value = if (ok) d else 0L
    }

    private val awaiting = MutableStateFlow(false)
    override val awaitingAudio: StateFlow<Boolean> = awaiting

    private val loadingFlow = MutableStateFlow(false)
    override val loadingTrack: StateFlow<Boolean> = loadingFlow

    private val sourceUrls = object : LinkedHashMap<String, String>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 6
    }
    override fun currentPositionMs(): Long {
        val track = currentTrack ?: return -1L
        if (activeSlot == null || transitioning || state.value is PlaybackState.Loading) return -1L
        if (player.currentMediaItem?.mediaId != track.isrc) return -1L
        return player.currentPosition.coerceAtLeast(0L)
    }

    override fun sourceUrlFor(isrc: String): String? = synchronized(sourceUrls) { sourceUrls[isrc] }

    private fun updateAwaiting() {
        awaiting.value = transitioning || state.value is PlaybackState.Loading ||
            (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        loadingFlow.value = transitioning || state.value is PlaybackState.Loading
    }
    private var prefetchJob: Job? = null
    private var prefetchedFor: String? = null
    private var recoveringIsrc: String? = null
    private var recoveries = 0

    private fun attachPlayerListener(slot: Slot) {
        slot.exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (slot !== activeSlot) return
                if (isPlaying) {
                    startPositionUpdates()
                    startCrossfadeWatcher()
                } else {
                    positionJob?.cancel()
                    crossfadeJob?.cancel()
                }
                val track = currentTrack ?: return
                if (!isPlaying && (transitioning || state.value is PlaybackState.Loading)) return
                if (isPlaying) {
                    if (prefetchedFor != track.isrc) {
                        prefetchedFor = track.isrc
                        schedulePrefetch(PREFETCH_DELAY_MS)
                    }
                }
                state.value = if (isPlaying) {
                    PlaybackState.Playing(player.currentPosition, track)
                } else {
                    PlaybackState.Paused(player.currentPosition, track)
                }
                if (isPlaying) transitioning = false
                updateAwaiting()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (slot === activeSlot) updateAwaiting()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (slot !== activeSlot) return
                updateAwaiting()
                when (playbackState) {
                    Player.STATE_ENDED -> onTrackEnded()

                    Player.STATE_READY -> {
                        learnDuration()
                        publishStreamDuration()
                        val track = currentTrack
                        if (!player.playWhenReady && track != null) {
                            val loadedThis = loadedIsrc == track.isrc && player.currentMediaItem?.mediaId == track.isrc
                            if (state.value is PlaybackState.Loading || (transitioning && loadedThis)) {
                                transitioning = false
                                state.value = PlaybackState.Paused(player.currentPosition, track)
                            }
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (slot !== activeSlot) return
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    publishStreamDuration()
                    return
                }
                val id = mediaItem?.mediaId ?: return
                if (id != currentTrack?.isrc) {
                    val newPos = positionOfTransition(id) ?: return
                    val track = trackAt(newPos) ?: return
                    pos = newPos
                    currentTrack = track
                    loadedIsrc = track.isrc
                    publishQueue()
                    state.value = if (player.isPlaying) {
                        PlaybackState.Playing(player.currentPosition, track)
                    } else {
                        PlaybackState.Paused(player.currentPosition, track)
                    }
                }
                streamDuration.value = 0L
                learnDuration()
                publishStreamDuration()
                if (player.isPlaying) startPositionUpdates()
                while (player.currentMediaItemIndex > 0) player.removeMediaItem(0)
                prefetchedFor = id
                schedulePrefetch(PREFETCH_DELAY_MS)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (slot !== activeSlot) return
                val failing = player.currentMediaItem?.mediaId?.let { queuedTrack(it) } ?: currentTrack
                if (failing != null && error.errorCode / 1000 == 2 && recover(failing, player.currentPosition)) return
                transitioning = false
                state.value = PlaybackState.Error("Playback could not start")
            }
        })
    }

    private fun applyMod(slot: Slot, s: ModSettings) {
        val exo = slot.exo
        exo.playbackParameters = PlaybackParameters(s.speed, s.pitchRatio)
        slot.engine.pitchRatio = s.pitchRatio
        slot.mod.apply {
            reverbMix = s.reverbMix
            reverbDecaySec = s.reverbDecaySec * s.speed
            reverbPreDelayMs = s.reverbPreDelayMs * s.speed
            reverbDamping = s.reverbDamping
            lowPassHz = if (s.lowPassHz >= 20_000f) 20_000f else s.lowPassHz / s.pitchRatio
            vinyl = s.vinylNoise
            bassDb = s.bassDb
            wobbleCents = s.wobbleCents
        }
    }

    private var fadeMs = 0
    private var fadeJob: Job? = null

    private fun applyPlaybackSettings(slot: Slot, s: PlaybackSettings.State) {
        fadeMs = s.fadeMs
        crossfadeMs = s.crossfadeMs
        slot.exo.setSkipSilenceEnabled(s.skipSilence)
        slot.exo.setHandleAudioBecomingNoisy(s.pauseOnUnplug)
        slot.mod.mono = s.mono
        slot.mod.balance = s.balance
    }

    private fun resetVolume() {
        fadeJob?.cancel()
        abortCrossfade()
        disarm()
        player.volume = 1f
    }

    private suspend fun rampVolume(from: Float, to: Float, ms: Int) {
        val steps = (ms / 20).coerceAtLeast(1)
        for (i in 1..steps) {
            player.volume = from + (to - from) * i / steps
            delay(20L)
        }
    }

    private fun pausePlayer() {
        fadeJob?.cancel()
        abortCrossfade(keepVolume = true)
        if (fadeMs <= 0 || !player.isPlaying) {
            player.volume = 1f
            player.pause()
            return
        }
        val from = player.volume
        fadeJob = scope.launch {
            rampVolume(from, 0f, fadeMs)
            player.pause()
            player.volume = 1f
        }
    }

    private fun resumePlayer() {
        fadeJob?.cancel()
        if (fadeMs <= 0) {
            player.volume = 1f
            player.play()
            return
        }
        val from = if (player.isPlaying) player.volume else 0f
        player.volume = from
        player.play()
        fadeJob = scope.launch { rampVolume(from, 1f, fadeMs) }
    }

    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>? = null

    private fun ensureSessionService() {
        if (controllerFuture != null) return
        runCatching {
            val token = androidx.media3.session.SessionToken(context, ComponentName(context, GamutMediaSessionService::class.java))
            val future = androidx.media3.session.MediaController.Builder(context, token)
                .setListener(object : androidx.media3.session.MediaController.Listener {
                    override fun onDisconnected(controller: androidx.media3.session.MediaController) {
                        controllerFuture = null
                    }
                })
                .buildAsync()
            controllerFuture = future
            future.addListener(
                { if (runCatching { future.get() }.isFailure && controllerFuture === future) controllerFuture = null },
                androidx.core.content.ContextCompat.getMainExecutor(context),
            )
        }
    }

    override fun play(track: Track, sourceUrl: String) {
        ensureSessionService()
        loadJob?.cancel()
        positionJob?.cancel()
        syncQueuePositionTo(track)
        beginLoad(startAt = 0f, bufferFrom = 0f)
        startMedia(track, sourceUrl)
    }

    override fun pause() {
        val resolving = loadJob?.isActive == true
        if (resolving) loadJob?.cancel()
        if (resolving || transitioning) {
            transitioning = false
            currentTrack?.let { state.value = PlaybackState.Paused(0L, it) }
        }
        pausePlayer()
        updateAwaiting()
    }

    override fun resume() {
        val track = currentTrack ?: return
        ensureSessionService()
        if (loadedIsrc == track.isrc) resumePlayer() else loadAtPos(pos)
    }

    override fun seekTo(positionMs: Long) {
        if (!transitioning) player.seekTo(positionMs)
    }

    override fun skipNext() {
        val target = nextPos() ?: return
        val track = trackAt(target) ?: return
        if (crossfadeActive() && !transitioning && player.isPlaying && target == peekNextPos() &&
            armedIsrc == track.isrc && standbyReady() && beginCrossfade(minOf(crossfadeMs, MANUAL_CROSSFADE_MS))
        ) {
            return
        }
        val preloaded = !transitioning &&
            target == peekNextPos() &&
            player.currentMediaItem?.mediaId == currentTrack?.isrc &&
            attachedNextId() == track.isrc
        if (preloaded) {
            pos = target
            currentTrack = track
            loadedIsrc = track.isrc
            streamDuration.value = 0L
            publishQueue()
            state.value = PlaybackState.Playing(0L, track)
            player.seekToNextMediaItem()
            resetVolume()
            player.play()
            publishStreamDuration()
        } else {
            loadAtPos(target)
        }
    }

    override fun skipPrevious() {
        if (!transitioning && player.currentPosition > RESTART_THRESHOLD_MS) {
            player.seekTo(0L)
            return
        }
        val previous = prevPos()
        if (previous != null) {
            loadAtPos(previous)
        } else if (!transitioning) {
            player.seekTo(0L)
        }
    }

    override fun setQueue(tracks: List<Track>, startIndex: Int) {
        items.clear()
        items.addAll(tracks)
        rebuildOrder(if (tracks.isEmpty()) -1 else startIndex.coerceIn(0, tracks.lastIndex))
        publishQueue()
        queueChanged()
    }

    override fun setShuffle(enabled: Boolean) {
        if (shuffle.value == enabled) return
        shuffle.value = enabled
        rebuildOrder(order.getOrNull(pos) ?: -1)
        publishQueue()
        queueChanged()
    }

    override fun setRepeatMode(mode: RepeatMode) {
        repeat.value = mode
        player.repeatMode = if (mode == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        queueChanged()
    }

    override fun skipTo(index: Int) {
        if (index in order.indices) loadAtPos(index)
    }

    override fun addToQueue(track: Track, playNext: Boolean) {
        items.add(track)
        val at = if (playNext) (pos + 1).coerceAtMost(order.size) else order.size
        order.add(at, items.lastIndex)
        publishQueue()
        queueChanged()
    }

    override fun removeFromQueue(index: Int) {
        if (index !in order.indices || index == pos) return
        val itemIndex = order.removeAt(index)
        items.removeAt(itemIndex)
        for (i in order.indices) if (order[i] > itemIndex) order[i] = order[i] - 1
        if (index < pos) pos--
        publishQueue()
        queueChanged()
    }

    override fun setSourceResolver(resolver: TrackSourceResolver?) {
        this.resolver = resolver
    }

    private fun trackAt(p: Int): Track? = order.getOrNull(p)?.let { items.getOrNull(it) }

    private fun queuedTrack(isrc: String): Track? = items.firstOrNull { it.isrc == isrc }

    private fun peekNextPos(): Int? {
        if (order.isEmpty()) return null
        val next = pos + 1
        if (next < order.size) return next
        if (repeat.value != RepeatMode.ALL) return null
        if (shuffle.value && order.size > 1) return null
        return 0
    }

    private fun nextPos(): Int? {
        peekNextPos()?.let { return it }
        if (order.isEmpty() || repeat.value != RepeatMode.ALL) return null
        val current = order.getOrNull(pos)
        order = order.shuffled().toMutableList()
        if (order.size > 1 && order[0] == current) order.add(order.removeAt(0))
        return 0
    }

    private fun prevPos(): Int? = when {
        pos > 0 -> pos - 1
        repeat.value == RepeatMode.ALL && order.isNotEmpty() -> order.lastIndex
        else -> null
    }

    private fun positionOfTransition(isrc: String): Int? {
        val expected = peekNextPos()
        if (expected != null && trackAt(expected)?.isrc == isrc) return expected
        return order.indexOfFirst { items[it].isrc == isrc }.takeIf { it >= 0 }
    }

    private fun onTrackEnded() {
        if (crossfadeActive() && standbyReady() && beginCrossfade(0)) return
        val next = nextPos()
        if (next != null) {
            loadAtPos(next)
        } else {
            player.seekTo(0L)
            player.pause()
            currentTrack?.let { state.value = PlaybackState.Paused(0L, it) }
        }
    }

    private fun queueChanged() {
        val i = player.currentMediaItemIndex
        if (player.mediaItemCount > i + 1) {
            val wanted = peekNextPos()?.let { trackAt(it)?.isrc }
            if (player.getMediaItemAt(i + 1).mediaId != wanted) player.removeMediaItem(i + 1)
        }
        val nextIsrc = peekNextPos()?.let { trackAt(it)?.isrc }
        if (armedIsrc != null && (armedIsrc != nextIsrc || !crossfadeActive())) disarm()
        if (currentTrack != null) schedulePrefetch(QUEUE_EDIT_PREFETCH_DELAY_MS)
    }

    private fun attachedNextId(): String? {
        val i = player.currentMediaItemIndex
        return if (player.mediaItemCount > i + 1) player.getMediaItemAt(i + 1).mediaId else null
    }

    private fun loadAtPos(p: Int, startPositionMs: Long = 0L, forceRefresh: Boolean = false) {
        val track = trackAt(p) ?: return
        loadJob?.cancel()
        positionJob?.cancel()
        beginLoad(startAt = RESOLVE_START, bufferFrom = RESOLVE_END)
        pos = p
        currentTrack = track
        publishQueue()
        streamDuration.value = 0L
        transitioning = true
        state.value = PlaybackState.Playing(startPositionMs, track)
        resetVolume()
        player.pause()

        if (resolver == null) {
            transitioning = false
            state.value = PlaybackState.Paused(0L, track)
            return
        }
        loadJob = scope.launch {
            val url = resolveUrl(track, forceRefresh) { done ->
                raiseProgress(RESOLVE_START + (RESOLVE_END - RESOLVE_START) * done.coerceIn(0f, 1f))
            }
            if (url == null) {
                transitioning = false
                state.value = PlaybackState.Paused(0L, track)
            } else {
                raiseProgress(RESOLVE_END)
                startMedia(track, url, soft = true, startPositionMs = startPositionMs)
            }
        }
    }

    private suspend fun resolveUrl(track: Track, forceRefresh: Boolean, onProgress: (Float) -> Unit = {}): String? {
        val active = resolver ?: return null
        repeat(RESOLVE_ATTEMPTS) { attempt ->
            val url = try {
                withContext(Dispatchers.Default) { active.resolve(track, forceRefresh || attempt > 0, onProgress) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (url != null) return url
            if (attempt < RESOLVE_ATTEMPTS - 1) delay(800L * (attempt + 1))
        }
        return null
    }

    private fun startMedia(track: Track, url: String, soft: Boolean = false, startPositionMs: Long = 0L) {
        currentTrack = track
        loadedIsrc = track.isrc
        streamDuration.value = 0L
        if (!soft) {
            transitioning = false
            state.value = PlaybackState.Loading
        }
        resetVolume()
        player.setMediaItem(buildMediaItem(track, url), startPositionMs)
        player.prepare()
        player.play()
        trackBuffering()
        updateAwaiting()
    }

    private fun recover(track: Track, resumeAtMs: Long): Boolean {
        if (resolver == null) return false
        if (recoveringIsrc != track.isrc) {
            recoveringIsrc = track.isrc
            recoveries = 0
        }
        if (recoveries >= MAX_RECOVERIES) return false
        recoveries++
        if (trackAt(pos)?.isrc != track.isrc) {
            val p = order.indexOfFirst { items[it].isrc == track.isrc }
            if (p < 0) return false
            pos = p
        }
        loadAtPos(pos, startPositionMs = resumeAtMs, forceRefresh = true)
        return true
    }

    private fun beginLoad(startAt: Float, bufferFrom: Float) {
        progressJob?.cancel()
        bufferBase = bufferFrom
        progress.value = startAt
    }

    private fun raiseProgress(value: Float) {
        if (value > progress.value) progress.value = value.coerceAtMost(1f)
    }

    private fun trackBuffering() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && (state.value is PlaybackState.Loading || transitioning)) {
                val ahead = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
                raiseProgress(bufferBase + (1f - bufferBase) * (ahead.toFloat() / BUFFER_FOR_PLAYBACK_MS).coerceIn(0f, 1f))
                delay(100)
            }
            if (isActive) raiseProgress(1f)
        }
    }

    private var crossfadeMs = 0
    private var armedIsrc: String? = null
    private var armedPos = -1
    private var armTried: String? = null
    private var armingJob: Job? = null
    private var crossfadeJob: Job? = null
    private var rampJob: Job? = null

    private fun crossfadeActive() = crossfadeMs > 0 && repeat.value != RepeatMode.ONE
    private fun crossfading() = outgoingSlot != null
    private fun standbyReady() = armedIsrc != null && standbySlot?.exo?.playbackState == Player.STATE_READY

    private fun onCrossfadeChanged() {
        if (!crossfadeActive()) {
            crossfadeJob?.cancel()
            if (armedIsrc != null || armingJob?.isActive == true) disarm()
        } else if (activeSlot?.exo?.isPlaying == true) {
            startCrossfadeWatcher()
        }
    }

    private fun startCrossfadeWatcher() {
        if (!crossfadeActive() || crossfadeJob?.isActive == true) return
        crossfadeJob = scope.launch { while (isActive) delay(watchCrossfade()) }
    }

    private fun watchCrossfade(): Long {
        val track = currentTrack
        val exo = player
        if (!crossfadeActive() || track == null || transitioning || crossfading() || !exo.isPlaying ||
            exo.currentMediaItem?.mediaId != track.isrc
        ) {
            return WATCH_IDLE_MS
        }
        val duration = exo.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return WATCH_IDLE_MS
        val nextPos = peekNextPos() ?: return WATCH_IDLE_MS
        val speed = exo.playbackParameters.speed.coerceAtLeast(0.1f)
        val remaining = ((duration - exo.currentPosition) / speed).toLong()
        val fade = minOf(crossfadeMs.toLong(), (duration / speed).toLong() / 2)
        val armAt = fade + ARM_LEAD_MS
        if (remaining > armAt) return (remaining - armAt).coerceIn(250L, 2_000L)
        arm(nextPos, track)
        if (remaining > fade) return (remaining - fade).coerceIn(20L, 250L)
        if (standbyReady()) {
            beginCrossfade(minOf(fade, remaining).toInt())
            return WATCH_IDLE_MS
        }
        return if (remaining > 300L) 100L else WATCH_IDLE_MS
    }

    private fun arm(nextPos: Int, current: Track) {
        val next = trackAt(nextPos) ?: return
        val pair = "${current.isrc}>${next.isrc}"
        if (armedIsrc == next.isrc || armTried == pair) return
        armTried = pair
        armingJob?.cancel()
        armingJob = scope.launch {
            val url = resolveUrl(next, forceRefresh = false) ?: return@launch
            if (currentTrack?.isrc != current.isrc || peekNextPos()?.let { trackAt(it)?.isrc } != next.isrc) return@launch
            val slot = standbySlot ?: newSlot(handleFocus = false).also { standbySlot = it }
            val i = player.currentMediaItemIndex
            if (player.mediaItemCount > i + 1) player.removeMediaItem(i + 1)
            slot.exo.volume = 0f
            slot.exo.playWhenReady = false
            slot.exo.setMediaItem(buildMediaItem(next, url))
            slot.exo.prepare()
            armedIsrc = next.isrc
            armedPos = nextPos
        }
    }

    private fun disarm() {
        armingJob?.cancel()
        armedIsrc = null
        armedPos = -1
        armTried = null
        standbySlot?.exo?.let {
            runCatching {
                it.stop()
                it.clearMediaItems()
            }
        }
    }

    private fun beginCrossfade(fadeMs: Int): Boolean {
        val incoming = standbySlot ?: return false
        val outgoing = activeSlot ?: return false
        val isrc = armedIsrc ?: return false
        val target = armedPos
        val track = trackAt(target)?.takeIf { it.isrc == isrc } ?: return false
        if (target != peekNextPos() || incoming.exo.playbackState != Player.STATE_READY) return false

        positionJob?.cancel()
        fadeJob?.cancel()
        rampJob?.cancel()
        pos = target
        currentTrack = track
        loadedIsrc = track.isrc
        streamDuration.value = 0L
        publishQueue()
        armedIsrc = null
        armedPos = -1
        armTried = null
        standbySlot = null
        outgoingSlot?.let { retire(it) }
        outgoingSlot = outgoing
        activeSlot = incoming

        outgoing.exo.setAudioAttributes(musicAttributes, false)
        incoming.exo.setAudioAttributes(musicAttributes, true)
        incoming.exo.volume = if (fadeMs > 0) 0f else 1f
        incoming.exo.play()
        state.value = PlaybackState.Playing(0L, track)
        learnDuration()
        publishStreamDuration()
        updateAwaiting()
        onSessionPlayerChanged?.invoke(queueForwardingPlayer(incoming.exo))
        prefetchedFor = track.isrc
        schedulePrefetch(PREFETCH_DELAY_MS)

        if (fadeMs <= 0) {
            finishCrossfade()
            return true
        }
        rampJob = scope.launch {
            val started = SystemClock.elapsedRealtime()
            while (isActive) {
                val t = ((SystemClock.elapsedRealtime() - started).toFloat() / fadeMs).coerceIn(0f, 1f)
                incoming.exo.volume = sin(t * HALF_PI)
                outgoing.exo.volume = cos(t * HALF_PI)
                if (t >= 1f) break
                delay(20L)
            }
            finishCrossfade()
        }
        return true
    }

    private fun finishCrossfade() {
        outgoingSlot?.let { retire(it) }
        outgoingSlot = null
        activeSlot?.exo?.volume = 1f
    }

    private fun abortCrossfade(keepVolume: Boolean = false) {
        rampJob?.cancel()
        val out = outgoingSlot ?: return
        retire(out)
        outgoingSlot = null
        if (!keepVolume) activeSlot?.exo?.volume = 1f
    }

    private fun retire(slot: Slot) {
        runCatching {
            slot.exo.pause()
            slot.exo.stop()
            slot.exo.clearMediaItems()
            slot.exo.volume = 1f
        }
        if (standbySlot == null) standbySlot = slot else runCatching { slot.exo.release() }
    }

    private fun schedulePrefetch(delayMs: Long) {
        prefetchJob?.cancel()
        if (resolver == null || currentTrack == null) return
        prefetchJob = scope.launch {
            delay(delayMs)
            if (!prefetchAllowed()) return@launch
            val upcoming = upcomingTracks(if (isCharging()) 2 else 1)
            upcoming.forEachIndexed { index, track ->
                val url = resolveUrl(track, forceRefresh = false) ?: return@forEachIndexed
                if (index == 0) attachNext(track, url)
                warmCache(url)
            }
        }
    }

    private fun upcomingTracks(count: Int): List<Track> {
        val result = mutableListOf<Track>()
        for (k in 1..count) {
            val at = pos + k
            val itemIndex = when {
                at < order.size -> order[at]
                repeat.value == RepeatMode.ALL && !shuffle.value && order.isNotEmpty() -> order[at % order.size]
                else -> null
            } ?: break
            val track = items.getOrNull(itemIndex) ?: break
            if (track.isrc != currentTrack?.isrc && result.none { it.isrc == track.isrc }) result += track
        }
        return result
    }

    private fun attachNext(track: Track, url: String) {
        if (crossfadeActive()) return
        if (transitioning || player.currentMediaItem?.mediaId != currentTrack?.isrc) return
        val wanted = peekNextPos()?.let { trackAt(it) } ?: return
        if (wanted.isrc != track.isrc) return
        val i = player.currentMediaItemIndex
        if (player.mediaItemCount > i + 1) {
            if (player.getMediaItemAt(i + 1).mediaId == track.isrc) return
            player.removeMediaItem(i + 1)
        }
        player.addMediaItem(buildMediaItem(track, url))
    }

    private suspend fun warmCache(url: String) = withContext(Dispatchers.IO) {
        if (url.startsWith("file:")) return@withContext
        try {
            val spec = DataSpec.Builder()
                .setUri(url)
                .setKey(cacheKeyFor(url))
                .setPosition(0)
                .setLength(WARM_BYTES)
                .build()
            val writer = CacheWriter(cacheFactory.createDataSource(), spec, null, null)
            val onCancel = currentCoroutineContext().job.invokeOnCompletion { cause -> if (cause != null) writer.cancel() }
            try {
                writer.cache()
            } finally {
                onCancel.dispose()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private fun prefetchAllowed(): Boolean {
        val s = settings.state.value
        if (!s.preload) return false
        if (s.preloadUnmeteredOnly && !settings.onUnmeteredNetwork()) return false
        if (powerManager.isPowerSaveMode) return false
        if (Build.VERSION.SDK_INT >= 29 && powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) return false
        val (percent, charging) = batteryState()
        return charging || percent > LOW_BATTERY_PERCENT
    }

    private fun isCharging(): Boolean = batteryState().second

    private fun batteryState(): Pair<Int, Boolean> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 100
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return percent to charging
    }

    private fun learnDuration() {
        val track = currentTrack ?: return
        if (track.durationMs > 0 || player.currentMediaItem?.mediaId != track.isrc) return
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val fixed = track.copy(durationMs = duration)
        currentTrack = fixed
        val i = items.indexOfFirst { it.isrc == track.isrc }
        if (i >= 0) items[i] = fixed
        publishQueue()
        state.value = when (val s = state.value) {
            is PlaybackState.Playing -> s.copy(track = fixed)
            is PlaybackState.Paused -> s.copy(track = fixed)
            else -> s
        }
    }

    private fun cacheKeyFor(url: String): String? {
        val uri = Uri.parse(url)
        val id = uri.getQueryParameter("id") ?: return null
        val itag = uri.getQueryParameter("itag") ?: return null
        val length = uri.getQueryParameter("clen") ?: return null
        return "yt:$id:$itag:$length"
    }

    private fun buildMediaItem(track: Track, url: String): MediaItem = MediaItem.Builder()
        .also { synchronized(sourceUrls) { sourceUrls[track.isrc] = url } }
        .setUri(url)
        .setMediaId(track.isrc)
        .setCustomCacheKey(cacheKeyFor(url))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(track.albumArtUrl?.let(Uri::parse))
                .build(),
        )
        .build()

    private fun syncQueuePositionTo(track: Track) {
        val itemAtPos = trackAt(pos)
        if (itemAtPos?.isrc == track.isrc) return
        val itemIndex = items.indexOfFirst { it.isrc == track.isrc }
        val newPos = if (itemIndex >= 0) order.indexOf(itemIndex) else -1
        if (newPos >= 0) {
            pos = newPos
        } else {
            items.clear()
            items.add(track)
            rebuildOrder(0)
        }
        publishQueue()
    }

    private fun rebuildOrder(currentItem: Int) {
        val indices = items.indices.toMutableList()
        if (shuffle.value) {
            indices.remove(currentItem)
            indices.shuffle()
            if (currentItem >= 0) indices.add(0, currentItem)
            order = indices
            pos = if (currentItem >= 0) 0 else -1
        } else {
            order = indices
            pos = currentItem
        }
    }

    private fun publishQueue() {
        queueFlow.value = QueueState(order.map { items[it] }, pos)
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val track = currentTrack
                if (track != null && !transitioning && state.value !is PlaybackState.Loading &&
                    player.isPlaying && player.currentMediaItem?.mediaId == track.isrc
                ) {
                    if (streamDuration.value == 0L) {
                        learnDuration()
                        publishStreamDuration()
                    }
                    state.value = PlaybackState.Playing(player.currentPosition, track)
                }
                delay(if (powerManager.isInteractive) 500L else 2_000L)
            }
        }
    }

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
        const val RESOLVE_START = 0.05f
        const val RESOLVE_END = 0.7f

        const val BUFFER_FOR_PLAYBACK_MS = 1_500

        const val CACHE_MAX_BYTES = 200L * 1024 * 1024

        const val WARM_BYTES = 256L * 1024

        const val PREFETCH_DELAY_MS = 2_000L
        const val QUEUE_EDIT_PREFETCH_DELAY_MS = 500L

        const val LOW_BATTERY_PERCENT = 15

        const val MAX_RECOVERIES = 3

        const val RESOLVE_ATTEMPTS = 3
        const val TRANSITION_LOCK_MS = 90_000L

        const val ARM_LEAD_MS = 6_000L

        const val WATCH_IDLE_MS = 500L

        const val MANUAL_CROSSFADE_MS = 1_000

        const val HALF_PI = 1.5707964f
    }
}

@UnstableApi
private class SchemeRoutingDataSource(private val local: DataSource, private val remote: DataSource) : DataSource {
    private var active: DataSource = remote

    override fun addTransferListener(transferListener: TransferListener) {
        local.addTransferListener(transferListener)
        remote.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        active = if (dataSpec.uri.scheme == "file") local else remote
        return active.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active.read(buffer, offset, length)
    override fun getUri(): Uri? = active.uri
    override fun getResponseHeaders(): Map<String, List<String>> = active.responseHeaders
    override fun close() = active.close()
}
