package com.gamut.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.gamut.core.database.GamutDatabase
import com.gamut.core.database.PendingSyncDao
import com.gamut.core.database.SyncStateDao
import com.gamut.core.database.SyncStateEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data object Offline : SyncStatus
    data class Error(val message: String) : SyncStatus
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    private val engine: SyncEngine,
    private val pending: PendingSyncDao,
    private val state: SyncStateDao,
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status
    private val _lastSyncedMs = MutableStateFlow<Long?>(null)
    val lastSyncedMs: StateFlow<Long?> = _lastSyncedMs

    private val _signedOutRemotely = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signedOutRemotely: SharedFlow<Unit> = _signedOutRemotely.asSharedFlow()

    @Volatile private var offline = false

    private val manual = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val lock = Mutex()
    private var scope: CoroutineScope? = null
    private var failures = 0
    private var retryJob: Job? = null

    fun syncNow() {
        manual.tryEmit(Unit)
    }

    suspend fun <T> withSyncPaused(block: suspend () -> T): T = lock.withLock { block() }

    suspend fun forgetOwner() = withSyncPaused { state.put(SyncStateEntity(SYNC_OWNER_KEY, "")) }

    fun start(scope: CoroutineScope) {
        if (!SyncConfig.enabled) return
        this.scope = scope
        scope.launch(Dispatchers.IO) {
            _lastSyncedMs.value = state.get("last_synced_ms")?.toLongOrNull()
            combine(auth.userId, online()) { user, online -> user to online }
                .distinctUntilChanged()
                .onEach { (user, online) ->
                    offline = user != null && !online
                    if (offline) {
                        if (_status.value !is SyncStatus.Syncing) _status.value = SyncStatus.Offline
                    } else if (_status.value is SyncStatus.Offline) {
                        _status.value = SyncStatus.Idle
                    }
                }
                .map { (user, online) -> user != null && online }
                .distinctUntilChanged()
                .flatMapLatest { active ->
                    if (!active) {
                        emptyFlow()
                    } else {
                        merge(
                            flow {
                                while (true) {
                                    emit(Unit)
                                    delay(PERIOD_MS)
                                }
                            },
                            pending.observeCount().filter { it > 0 }.debounce(2_000).map { },
                            manual,
                        )
                    }
                }
                .conflate()
                .collect { runSync() }
        }
    }

    private suspend fun runSync() = lock.withLock {
        _status.value = SyncStatus.Syncing
        try {
            engine.sync()
            failures = 0
            retryJob?.cancel()
            _lastSyncedMs.value = state.get("last_synced_ms")?.toLongOrNull()
            _status.value = SyncStatus.Idle
        } catch (e: CancellationException) {
            _status.value = if (offline) SyncStatus.Offline else SyncStatus.Idle
            throw e
        } catch (e: SessionEndedException) {
            failures = 0
            _status.value = SyncStatus.Idle
            _signedOutRemotely.tryEmit(Unit)
        } catch (e: Exception) {
            failures++
            _status.value = if (e.isNetworkProblem()) SyncStatus.Offline else SyncStatus.Error(friendly(e))
            scheduleRetry()
        }
    }

    private fun Throwable.isNetworkProblem(depth: Int = 0): Boolean {
        if (depth > 6) return false
        if (this is java.io.IOException || this::class.java.simpleName == "HttpRequestException") return true
        return cause?.isNetworkProblem(depth + 1) == true || suppressed.any { it.isNetworkProblem(depth + 1) }
    }

    private fun friendly(e: Throwable): String {
        val m = e.message.orEmpty()
        return if (m.isBlank() || m.contains("http", ignoreCase = true)) "Couldn\u2019t sync \u00B7 will retry" else m.take(120)
    }

    private fun scheduleRetry() {
        val s = scope ?: return
        retryJob?.cancel()
        val wait = minOf(30_000L shl (failures - 1).coerceIn(0, 5), MAX_BACKOFF_MS)
        retryJob = s.launch {
            delay(wait)
            manual.tryEmit(Unit)
        }
    }

    private fun online() = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        fun hasInternet() = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(hasInternet())
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
            }
            override fun onLost(network: Network) {
                trySend(hasInternet())
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        trySend(hasInternet())
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }

    private companion object {
        const val PERIOD_MS = 15L * 60 * 1000
        const val MAX_BACKOFF_MS = 15L * 60 * 1000
    }
}

class LocalDataCleaner @Inject constructor(private val db: GamutDatabase, private val sync: SyncManager) {
    suspend fun clear() = sync.withSyncPaused { withContext(Dispatchers.IO) { db.clearAllTables() } }
}
