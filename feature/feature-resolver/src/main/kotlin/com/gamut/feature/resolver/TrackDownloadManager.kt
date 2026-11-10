package com.gamut.feature.resolver

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import com.gamut.core.database.DownloadEntity
import com.gamut.core.database.DownloadIndex
import com.gamut.core.database.TrackDao
import com.gamut.core.database.toEntity
import com.gamut.core.model.Track
import com.gamut.core.player.StreamQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface DownloadState {
    data object Queued : DownloadState
    data class Active(val progress: Float) : DownloadState
    data object Done : DownloadState
    data class Failed(val message: String) : DownloadState
}

data class DownloadBatch(val done: Int = 0, val total: Int = 0, val current: String? = null, val waitingForWifi: Boolean = false)

data class DownloadSummary(val succeeded: Int, val failed: Int, val title: String?, val label: String?)

@Singleton
class TrackDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolveTrackSource: ResolveTrackSourceUseCase,
    private val audioSources: AudioSourceRepository,
    private val newPipe: NewPipeYoutubeSource,
    @Named("youtube") private val client: OkHttpClient,
    private val index: DownloadIndex,
    private val trackDao: TrackDao,
    private val storageSettings: StorageSettings,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val dir = File(context.filesDir, "downloads")

    val downloadsDir: File get() = dir

    private val transient = MutableStateFlow<Map<String, DownloadState>>(emptyMap())

    val states: StateFlow<Map<String, DownloadState>> = combine(transient, index.downloads) { live, saved ->
        buildMap {
            saved.keys.forEach { put(it, DownloadState.Done) }
            putAll(live)
        }
    }.stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    private val batchState = MutableStateFlow(DownloadBatch())
    val batch: StateFlow<DownloadBatch> = batchState

    private val summaryEvents = MutableSharedFlow<DownloadSummary>(extraBufferCapacity = 4)
    val summaries: SharedFlow<DownloadSummary> = summaryEvents
    private val summaryLock = Any()
    private var okCount = 0
    private var failCount = 0
    private var firstTitle: String? = null
    private var runLabel: String? = null

    private val queue = Channel<Track>(Channel.UNLIMITED)
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var running: Pair<String, Job>? = null
    private val failedTracks = ConcurrentHashMap<String, Track>()

    init {
        appScope.launch(Dispatchers.IO) {
            dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
        }
        appScope.launch {
            for (track in queue) {
                awaitNetworkAllowed(track.isrc)
                if (cancelled.remove(track.isrc)) continue
                val job = launch { process(track) }
                running = track.isrc to job
                job.join()
                running = null
            }
        }
    }

    fun download(tracks: List<Track>, label: String? = null) {
        val fresh = tracks.distinctBy { it.isrc }.filter { track ->
            val live = transient.value[track.isrc]
            !index.downloads.value.containsKey(track.isrc) && (live == null || live is DownloadState.Failed)
        }
        if (fresh.isEmpty()) return
        synchronized(summaryLock) {
            runLabel = if (batchState.value.total == 0 || runLabel == label) label else null
        }
        fresh.forEach { failedTracks.remove(it.isrc) }
        transient.update { it + fresh.associate { track -> track.isrc to DownloadState.Queued } }
        batchState.update { it.copy(total = it.total + fresh.size) }
        fresh.forEach { queue.trySend(it) }
        runCatching { context.startForegroundService(Intent(context, DownloadService::class.java)) }
    }

    fun cancel(isrcs: Collection<String>) {
        val ids = isrcs.toSet()
        val waiting = ids.filter { transient.value[it] is DownloadState.Queued }
        waiting.forEach { cancelled.add(it) }
        transient.update { current -> current.filterKeys { it !in waiting.toSet() } }
        repeat(waiting.size) { finishOne() }
        running?.takeIf { it.first in ids }?.second?.cancel()
    }

    fun cancelAll() {
        cancel(transient.value.filterValues { it is DownloadState.Queued || it is DownloadState.Active }.keys.toList())
    }

    fun retryFailed() {
        val again = failedTracks.values.toList()
        if (again.isNotEmpty()) download(again)
    }

    fun clearFailed() {
        failedTracks.clear()
        transient.update { current -> current.filterValues { it !is DownloadState.Failed } }
    }

    suspend fun remove(isrcs: Collection<String>) = withContext(Dispatchers.IO) {
        isrcs.forEach { failedTracks.remove(it) }
        cancel(isrcs)
        isrcs.forEach { id -> index.remove(id)?.let { File(it).delete() } }
        val ids = isrcs.toSet()
        transient.update { current -> current.filterKeys { it !in ids } }
    }

    private suspend fun process(track: Track) {
        val id = track.isrc
        batchState.update { it.copy(current = track.title) }
        setState(id, DownloadState.Active(0f))
        try {
            val file = fetch(track)
            saveMetadata(track)
            index.save(DownloadEntity(id, file.absolutePath, System.currentTimeMillis(), file.length()))
            setState(id, DownloadState.Done)
            recordResult(track.title, ok = true)
        } catch (e: CancellationException) {
            transient.update { it - id }
            throw e
        } catch (e: Exception) {
            failedTracks[id] = track
            setState(id, DownloadState.Failed(e.message ?: "Download failed"))
            recordResult(track.title, ok = false)
        } finally {
            finishOne()
        }
    }

    private suspend fun fetch(track: Track): File = withContext(Dispatchers.IO) {
        val id = track.isrc
        dir.mkdirs()
        if (dir.usableSpace < MIN_FREE_BYTES) throw IOException("Not enough storage")
        val videoId = resolveTrackSource(track).getOrThrow().youtubeVideoId
        val base = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val part = File(dir, "$base.part").also { it.delete() }

        var url = bestUrl(videoId, refresh = false)
        var stream = streamKey(url)
        var attempts = 0
        var lastEmit = 0L
        while (true) {
            try {
                pump(url, part) { done, all ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastEmit >= PROGRESS_EVERY_MS || done >= all) {
                        lastEmit = now
                        setState(id, DownloadState.Active(done.toFloat() / all))
                    }
                }
                break
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                if (++attempts > MAX_ATTEMPTS) throw e
                delay(500L * attempts)
                url = bestUrl(videoId, refresh = true)
                if (streamKey(url) != stream) {
                    part.delete()
                    stream = streamKey(url)
                }
            }
        }
        if (part.length() < MIN_VALID_BYTES) throw IOException("Download incomplete")
        val target = File(dir, "$base.${extensionOf(url)}")
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IOException("Couldn't save the file")
        target
    }

    private suspend fun bestUrl(videoId: String, refresh: Boolean): String {
        val best = storageSettings.current.downloadQuality == DownloadQuality.BEST
        if (best) newPipe.bestAudioUrl(videoId).getOrNull()?.let { return it }
        return audioSources.audioUrl(
            videoId,
            forceRefresh = refresh,
            quality = if (best) StreamQuality.HIGH else StreamQuality.NORMAL,
        ).getOrThrow()
    }

    private suspend fun pump(url: String, part: File, onProgress: (Long, Long) -> Unit) {
        var offset = part.length()
        var total = -1L
        FileOutputStream(part, true).use { out ->
            val buffer = ByteArray(32 * 1024)
            while (total < 0 || offset < total) {
                currentCoroutineContext().ensureActive()
                val request = Request.Builder().url(url).header("Range", "bytes=$offset-${offset + CHUNK_BYTES - 1}").build()
                val call = client.newCall(request)
                val hook = currentCoroutineContext().job.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
                try {
                    call.execute().use { response ->
                        if (response.code == 416) {
                            total = offset
                            return@use
                        }
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        val body = response.body ?: throw IOException("Empty response")
                        val ranged = response.code == 206
                        if (!ranged && offset > 0) {
                            out.channel.truncate(0)
                            offset = 0
                        }
                        if (ranged) {
                            response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()?.let { total = it }
                        } else if (body.contentLength() > 0) {
                            total = body.contentLength()
                        }
                        var chunk = 0L
                        body.byteStream().use { input ->
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                offset += n
                                chunk += n
                                if (total > 0) onProgress(offset, total)
                                currentCoroutineContext().ensureActive()
                            }
                        }
                        if (total < 0 && (!ranged || chunk < CHUNK_BYTES)) total = offset
                        if (chunk == 0L && total < 0) throw IOException("Empty response")
                    }
                } finally {
                    hook.dispose()
                }
            }
        }
    }

    private suspend fun saveMetadata(track: Track) {
        try {
            trackDao.upsert(track.toEntity())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { }
    }

    private suspend fun awaitNetworkAllowed(isrc: String) {
        var waited = false
        while (!networkAllowed() && !cancelled.contains(isrc)) {
            if (!waited) {
                waited = true
                batchState.update { it.copy(waitingForWifi = true) }
            }
            delay(2_000)
        }
        if (waited) batchState.update { it.copy(waitingForWifi = false) }
    }

    private fun networkAllowed(): Boolean {
        if (!storageSettings.current.wifiOnly) return true
        return try {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
            if (cm.activeNetwork == null) return false
            !cm.isActiveNetworkMetered
        } catch (_: SecurityException) {
            true
        }
    }

    private fun setState(id: String, state: DownloadState) = transient.update { it + (id to state) }

    private fun finishOne() {
        var finished = false
        batchState.update { b ->
            val done = b.done + 1
            finished = done >= b.total
            if (finished) DownloadBatch() else b.copy(done = done)
        }
        if (finished) emitSummary()
    }

    private fun recordResult(title: String, ok: Boolean) = synchronized(summaryLock) {
        if (ok) {
            okCount++
            if (firstTitle == null) firstTitle = title
        } else {
            failCount++
        }
    }

    private fun emitSummary() {
        val summary = synchronized(summaryLock) {
            DownloadSummary(okCount, failCount, firstTitle, runLabel).also {
                okCount = 0
                failCount = 0
                firstTitle = null
                runLabel = null
            }
        }
        if (summary.succeeded + summary.failed > 0) summaryEvents.tryEmit(summary)
    }

    private fun streamKey(url: String) = Uri.parse(url).getQueryParameter("itag").orEmpty()

    private fun extensionOf(url: String): String {
        val mime = Uri.parse(url).getQueryParameter("mime").orEmpty()
        return if ("webm" in mime) "webm" else "m4a"
    }

    private companion object {
        const val CHUNK_BYTES = 1L shl 20
        const val MAX_ATTEMPTS = 3
        const val PROGRESS_EVERY_MS = 120L
        const val MIN_FREE_BYTES = 100L * 1024 * 1024
        const val MIN_VALID_BYTES = 32L * 1024
    }
}
