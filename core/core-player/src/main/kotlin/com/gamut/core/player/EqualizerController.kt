package com.gamut.core.player

import android.content.Context
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EqualizerController private constructor(context: Context) {
    data class State(
        val available: Boolean = false,
        val enabled: Boolean = false,
        val preset: String = FLAT,
        val bandFrequenciesHz: List<Int> = emptyList(),
        val bandLevelsMb: List<Int> = emptyList(),
        val minLevelMb: Int = -1200,
        val maxLevelMb: Int = 1200,
        val bassBoostSupported: Boolean = false,
        val bassBoost: Int = 0,
        val virtualizerSupported: Boolean = false,
        val virtualizer: Int = 0,
    )

    private val sound = SoundSettings.get(context)
    private val _state = MutableStateFlow(sound.state.value.toLegacy())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).launch {
            sound.state.collect { _state.value = it.toLegacy() }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun attach(audioSessionId: Int) = Unit

    fun setEnabled(on: Boolean) = sound.setEqEnabled(on)

    fun setBandLevel(index: Int, levelMb: Int) = sound.setBandGain(index, levelMb / 100f)

    fun applyPreset(name: String) = sound.applyPreset(name)

    @Suppress("UNUSED_PARAMETER")
    fun setBassBoost(strength: Int) = Unit

    fun setVirtualizer(strength: Int) = sound.setWidth(100 + strength.coerceIn(0, 1000) / 10)

    fun reset() = sound.resetEqualizer()

    fun release() = Unit

    private fun SoundSettings.State.toLegacy() = State(
        available = true,
        enabled = eqEnabled,
        preset = preset,
        bandFrequenciesHz = bands.map { it.freqHz.roundToInt() },
        bandLevelsMb = bands.map { (it.gainDb * 100f).roundToInt() },
        minLevelMb = (-SoundSettings.MAX_GAIN_DB * 100f).roundToInt(),
        maxLevelMb = (SoundSettings.MAX_GAIN_DB * 100f).roundToInt(),
        bassBoostSupported = false,
        bassBoost = 0,
        virtualizerSupported = true,
        virtualizer = ((widthPercent - 100).coerceAtLeast(0) * 10).coerceAtMost(1000),
    )

    companion object {
        const val FLAT = SoundSettings.FLAT
        const val CUSTOM = SoundSettings.CUSTOM

        val presetNames: List<String> = SoundSettings.presetNames

        @Volatile private var instance: EqualizerController? = null

        fun get(context: Context): EqualizerController = instance ?: synchronized(this) {
            instance ?: EqualizerController(context.applicationContext).also { instance = it }
        }
    }
}
