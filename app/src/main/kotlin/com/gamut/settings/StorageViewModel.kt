package com.gamut.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamut.core.database.DownloadEntity
import com.gamut.core.database.DownloadIndex
import com.gamut.core.model.Track
import com.gamut.feature.resolver.StorageSettings
import com.gamut.feature.resolver.TrackDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadedSong(
    val isrc: String,
    val title: String,
    val artist: String,
    val bytes: Long,
    val addedMs: Long,
    val durationMs: Long,
    val format: String,
)

data class DownloadStats(
    val count: Int = 0,
    val bytes: Long = 0L,
    val playtimeMs: Long = 0L,
    val averageBytes: Long = 0L,
    val largest: DownloadedSong? = null,
    val newestMs: Long = 0L,
    val opus: Int = 0,
    val aac: Int = 0,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class StorageViewModel @Inject constructor(
    private val manager: StorageManager,
    val downloads: TrackDownloadManager,
    val settings: StorageSettings,
    private val index: DownloadIndex,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private val metadata = ConcurrentHashMap<String, Track>()

    val songs: StateFlow<List<DownloadedSong>> = index.downloads
        .mapLatest { rows -> buildSongs(rows) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val stats: StateFlow<DownloadStats> = songs
        .map { computeStats(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DownloadStats())

    val snapshot: StateFlow<StorageSnapshot?> = combine(
        index.downloads,
        refresh,
        downloads.batch.map { it.total == 0 }.distinctUntilChanged(),
    ) { _, _, _ -> }
        .debounce(150)
        .mapLatest { manager.measure() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            delay(5_000)
            manager.trimCacheIfNeeded()
        }
    }

    fun onOpened() {
        viewModelScope.launch {
            manager.repair()
            manager.trimCacheIfNeeded()
            refresh.value++
        }
    }

    fun refreshNow() {
        refresh.value++
    }

    fun delete(isrcs: Collection<String>) {
        val ids = isrcs.toList()
        viewModelScope.launch { downloads.remove(ids) }
    }

    fun deleteAll() {
        viewModelScope.launch {
            downloads.cancelAll()
            downloads.clearFailed()
            downloads.remove(index.downloads.value.keys.toList())
        }
    }

    fun clear(target: CacheTarget, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val freed = manager.clear(target)
            refresh.value++
            onDone(freed)
        }
    }

    fun cleanLeftovers(onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val freed = manager.cleanLeftovers()
            refresh.value++
            onDone(freed)
        }
    }

    private suspend fun buildSongs(rows: Map<String, DownloadEntity>): List<DownloadedSong> {
        val missing = rows.keys.filter { !metadata.containsKey(it) }
        if (missing.isNotEmpty()) metadata.putAll(manager.trackInfo(missing))
        return rows.values.mapNotNull { row ->
            val file = File(row.localFilePath)
            if (!file.exists()) return@mapNotNull null
            val track = metadata[row.isrc]
            DownloadedSong(
                isrc = row.isrc,
                title = track?.title ?: file.nameWithoutExtension,
                artist = track?.artist.orEmpty(),
                bytes = file.length(),
                addedMs = file.lastModified(),
                durationMs = track?.durationMs ?: 0L,
                format = if (file.extension.equals("webm", ignoreCase = true)) "Opus" else "AAC",
            )
        }
    }

    private fun computeStats(list: List<DownloadedSong>): DownloadStats {
        if (list.isEmpty()) return DownloadStats()
        val bytes = list.sumOf { it.bytes }
        val playtime = list.sumOf { if (it.durationMs > 0) it.durationMs else it.bytes * 1_000L / 18_000L }
        return DownloadStats(
            count = list.size,
            bytes = bytes,
            playtimeMs = playtime,
            averageBytes = bytes / list.size,
            largest = list.maxByOrNull { it.bytes },
            newestMs = list.maxOf { it.addedMs },
            opus = list.count { it.format == "Opus" },
            aac = list.count { it.format == "AAC" },
        )
    }
}
