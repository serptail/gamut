package com.gamut.feature.search

import com.gamut.core.network.ShazamClient
import com.gamut.core.network.ShazamResult
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class ShazamSession @Inject constructor(
    private val recorder: MicRecorder,
    private val client: ShazamClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<ShazamState>(ShazamState.Idle)
    val state: StateFlow<ShazamState> = _state.asStateFlow()

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    @Volatile var appVisible: Boolean = false

    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        _level.value = 0f
        _state.value = ShazamState.Listening(searching = false)
        job = scope.launch {
            val outcome = try {
                listen()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                ShazamState.PermissionDenied
            } catch (e: Exception) {
                ShazamState.Failed(e.message ?: "Couldn't use the microphone")
            }
            _level.value = 0f
            _state.value = outcome
        }
    }

    @Synchronized
    fun dismiss() {
        job?.cancel()
        job = null
        _level.value = 0f
        _state.value = ShazamState.Idle
    }

    fun permissionDenied() {
        _state.value = ShazamState.PermissionDenied
    }

    private suspend fun listen(): ShazamState = coroutineScope {
        val pcm = ByteArrayOutputStream()
        var attempt: Deferred<ShazamResult>? = null
        var checkpoint = 0
        var sentBytes = 0
        var outcome: ShazamState? = null

        fun settle(result: ShazamResult): Boolean {
            when (result) {
                is ShazamResult.Match -> outcome = ShazamState.Found(result.track)
                is ShazamResult.Failure -> outcome = ShazamState.Failed(result.message)
                ShazamResult.NoMatch -> return false
            }
            return true
        }

        fun send(): Deferred<ShazamResult> {
            val all = pcm.toByteArray()
            sentBytes = all.size
            val recent = all.copyOfRange(maxOf(0, all.size - MAX_SEND_BYTES), all.size)
            _state.value = ShazamState.Listening(searching = true)
            return async { client.recognize(recent) }
        }

        recorder.record { chunk ->
            pcm.write(chunk)
            _level.value = levelOf(chunk)

            attempt?.takeIf { it.isCompleted }?.let { finished ->
                attempt = null
                _state.value = ShazamState.Listening(searching = false)
                if (settle(finished.await())) return@record false
            }
            val seconds = pcm.size() / MicRecorder.BYTES_PER_SECOND
            if (attempt == null && checkpoint < CHECKPOINT_SECONDS.size && seconds >= CHECKPOINT_SECONDS[checkpoint]) {
                checkpoint++
                attempt = send()
            }
            seconds < MAX_LISTEN_SECONDS
        }
        outcome?.let { return@coroutineScope it }

        _level.value = 0f
        attempt?.let { if (settle(it.await())) return@coroutineScope outcome ?: ShazamState.NotFound }
        if (pcm.size() - sentBytes >= MicRecorder.BYTES_PER_SECOND * 2) {
            _state.value = ShazamState.Listening(searching = true)
            val all = pcm.toByteArray()
            settle(client.recognize(all.copyOfRange(maxOf(0, all.size - MAX_SEND_BYTES), all.size)))
        }
        outcome ?: ShazamState.NotFound
    }

    private companion object {
        val CHECKPOINT_SECONDS = intArrayOf(6, 10, 14)
        const val MAX_LISTEN_SECONDS = 15
        const val MAX_SEND_BYTES = 12 * MicRecorder.BYTES_PER_SECOND
    }
}

private fun levelOf(chunk: ByteArray): Float {
    val samples = chunk.size / 2
    if (samples == 0) return 0f
    var sum = 0.0
    for (i in 0 until samples) {
        val sample = (chunk[2 * i + 1].toInt() shl 8) or (chunk[2 * i].toInt() and 0xFF)
        sum += sample.toDouble() * sample
    }
    return (sqrt(sum / samples) / 32768.0 * 4.0).toFloat().coerceIn(0f, 1f)
}
