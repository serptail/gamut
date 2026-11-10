package com.gamut.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamut.core.model.Track
import com.gamut.feature.resolver.PrefetchCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

private data class FeedTail(
    val sections: List<FeedSection> = emptyList(),
    val loading: Boolean = false,
    val exhausted: Boolean = false,
    val failed: Boolean = false,
)

private const val FEED_TAIL_ORDER_BASE = 100

private const val PREFETCH_LIMIT = 5
private const val MIN_QUERY_LENGTH = 2
private const val DISCOVER_ERROR = "Discover is unavailable. Check your connection and try again."

private val DISCOVER_RETRY_DELAYS_MS = longArrayOf(4_000L, 12_000L, 30_000L)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
    private val prefetch: PrefetchCoordinator,
    private val discoverRepository: DiscoverRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private val _discover = MutableStateFlow(DiscoverState())

    private val _tail = MutableStateFlow(FeedTail())
    private var moreJob: Job? = null
    private val consumedFeedIds = HashSet<String>()

    val discover: StateFlow<DiscoverState> = combine(_discover, _tail) { base, tail ->
        if (base.tabs.isEmpty()) {
            base
        } else {
            base.copy(
                feed = base.feed + tail.sections,
                feedLoadingMore = tail.loading,
                feedExhausted = tail.exhausted,
                feedFailed = tail.failed,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DiscoverState())

    private val artistSignal = MutableStateFlow<ArtistSignal?>(null)

    private val rawArtistHit = query
        .debounce(300)
        .distinctUntilChanged()
        .mapLatest { value ->
            if (value.trim().length < MIN_QUERY_LENGTH) {
                null
            } else {
                try {
                    discoverRepository.artistHit(value)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
        }

    val artistHit: StateFlow<DiscoverItem?> = combine(rawArtistHit, artistSignal, _state) { hit, signal, ui ->
        hit?.takeIf { SearchRanking.showArtistCard(ui.query, it.title, signal, ui.tracks) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var discoverJob: Job? = null

    private fun loadDiscover() {
        discoverJob?.cancel()
        discoverJob = viewModelScope.launch {
            var autoRetries = 0
            while (true) {
                try {
                    discoverRepository.discoverFlow().collect { _discover.value = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                val now = _discover.value
                if (now.tabs.isEmpty() && (now.isLoading || now.error == null)) {
                    _discover.value = DiscoverState(isLoading = false, error = DISCOVER_ERROR)
                }
                if (_discover.value.tabs.isNotEmpty() || autoRetries >= DISCOVER_RETRY_DELAYS_MS.size) break
                delay(DISCOVER_RETRY_DELAYS_MS[autoRetries++])
                _discover.value = DiscoverState()
            }
        }
    }

    fun refreshDiscover() {
        discoverRepository.clearCache()
        moreJob?.cancel()
        consumedFeedIds.clear()
        _tail.value = FeedTail()
        _discover.update { if (it.tabs.isEmpty()) DiscoverState() else it }
        loadDiscover()
    }

    fun loadMoreFeed() {
        val tail = _tail.value
        if (tail.loading || tail.exhausted || _discover.value.tabs.isEmpty()) return
        moreJob = viewModelScope.launch {
            _tail.update { it.copy(loading = true, failed = false) }
            val result = try {
                discoverRepository.moreFeed(consumedFeedIds.toSet(), FEED_TAIL_ORDER_BASE + _tail.value.sections.size)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (result == null) {
                _tail.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            consumedFeedIds += result.consumed
            _tail.update {
                it.copy(
                    sections = it.sections + listOfNotNull(result.section),
                    loading = false,
                    exhausted = result.exhausted,
                    failed = result.failed,
                )
            }
        }
    }

    suspend fun artistIdFor(item: DiscoverItem): Long? = try {
        discoverRepository.artistIdFor(item)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    init {
        loadDiscover()
        viewModelScope.launch {
            query.debounce(250).flatMapLatest { value ->
                if (value.trim().length < MIN_QUERY_LENGTH) {
                    flowOf(SearchResult(emptyList(), true) to false)
                } else {
                    repository.search(value)
                        .map { it to false }
                        .catch { emit(SearchResult(emptyList(), true) to true) }
                }
            }.collect { (result, failed) ->
                val stillSearching = query.value.trim().length >= MIN_QUERY_LENGTH
                val tracks = if (stillSearching) result.tracks else emptyList()
                artistSignal.value = if (stillSearching) result.artist else null
                _state.update {
                    it.copy(
                        tracks = tracks,
                        isLoading = stillSearching && !result.isFinal,
                        error = if (failed && stillSearching) "Search failed. Check your connection and try again." else null,
                    )
                }
                if (result.isFinal && stillSearching && tracks.isNotEmpty()) {
                    prefetch.prefetch(tracks.take(PREFETCH_LIMIT))
                }
            }
        }
    }

    fun updateQuery(value: String) {
        val changed = query.value != value
        query.value = value
        if (value.isBlank()) artistSignal.value = null
        val searching = value.trim().length >= MIN_QUERY_LENGTH
        _state.update {
            if (value.isBlank()) {
                SearchUiState()
            } else {
                it.copy(query = value, error = null, isLoading = if (searching) changed || it.isLoading else false)
            }
        }
    }
}
