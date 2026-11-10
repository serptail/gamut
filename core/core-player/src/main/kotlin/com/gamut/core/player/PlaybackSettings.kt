package com.gamut.core.player

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StreamQuality(val label: String, val hint: String) {
    AUTO("Auto", "High on Wi-Fi, Normal on mobile data"),
    LOW("Data saver", "Smallest files, about 48 kbps"),
    NORMAL("Normal", "AAC, about 128 kbps"),
    HIGH("High", "Best stream available, up to about 160 kbps Opus"),
}

@Singleton
class PlaybackSettings @Inject constructor(@ApplicationContext private val context: Context) {
    data class State(
        val streamQuality: StreamQuality = StreamQuality.AUTO,
        val preload: Boolean = true,
        val preloadUnmeteredOnly: Boolean = false,
        val fadeMs: Int = 0,
        val crossfadeMs: Int = 0,
        val skipSilence: Boolean = false,
        val pauseOnUnplug: Boolean = true,
        val mono: Boolean = false,
        val balance: Float = 0f,
    )

    private val prefs = context.applicationContext.getSharedPreferences("playback_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state.asStateFlow()

    @Synchronized
    fun update(transform: (State) -> State) {
        val next = transform(_state.value).let {
            it.copy(
                fadeMs = it.fadeMs.coerceIn(0, 2_000),
                crossfadeMs = it.crossfadeMs.coerceIn(0, 12_000),
                balance = it.balance.coerceIn(-1f, 1f),
            )
        }
        if (next == _state.value) return
        _state.value = next
        save(next)
    }

    fun reset() = update { State() }

    fun effectiveQuality(): StreamQuality = when (val q = _state.value.streamQuality) {
        StreamQuality.AUTO -> if (onUnmeteredNetwork()) StreamQuality.HIGH else StreamQuality.NORMAL
        else -> q
    }

    fun onUnmeteredNetwork(): Boolean = runCatching {
        !context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
    }.getOrDefault(false)

    private fun load() = State(
        streamQuality = StreamQuality.entries.firstOrNull { it.name == prefs.getString(K_QUALITY, null) } ?: StreamQuality.AUTO,
        preload = prefs.getBoolean(K_PRELOAD, true),
        preloadUnmeteredOnly = prefs.getBoolean(K_PRELOAD_WIFI, false),
        fadeMs = prefs.getInt(K_FADE, 0),
        crossfadeMs = prefs.getInt(K_CROSSFADE, 0),
        skipSilence = prefs.getBoolean(K_SKIP_SILENCE, false),
        pauseOnUnplug = prefs.getBoolean(K_UNPLUG, true),
        mono = prefs.getBoolean(K_MONO, false),
        balance = prefs.getFloat(K_BALANCE, 0f),
    )

    private fun save(s: State) {
        prefs.edit()
            .putString(K_QUALITY, s.streamQuality.name)
            .putBoolean(K_PRELOAD, s.preload)
            .putBoolean(K_PRELOAD_WIFI, s.preloadUnmeteredOnly)
            .putInt(K_FADE, s.fadeMs)
            .putInt(K_CROSSFADE, s.crossfadeMs)
            .putBoolean(K_SKIP_SILENCE, s.skipSilence)
            .putBoolean(K_UNPLUG, s.pauseOnUnplug)
            .putBoolean(K_MONO, s.mono)
            .putFloat(K_BALANCE, s.balance)
            .apply()
    }

    private companion object {
        const val K_QUALITY = "quality"
        const val K_PRELOAD = "preload"
        const val K_PRELOAD_WIFI = "preload_unmetered_only"
        const val K_FADE = "fade_ms"
        const val K_CROSSFADE = "crossfade_ms"
        const val K_SKIP_SILENCE = "skip_silence"
        const val K_UNPLUG = "pause_on_unplug"
        const val K_MONO = "mono"
        const val K_BALANCE = "balance"
    }
}
