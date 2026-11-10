package com.gamut.core.player

import kotlin.math.pow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ModControl { SPEED, LINK, PITCH, BASS, REVERB_MIX, DECAY, PREDELAY, DAMPING, LOWPASS, WOBBLE, VINYL }

data class ModSettings(
    val speed: Float = 1f,
    val pitchFollowsSpeed: Boolean = true,
    val pitchSemitones: Float = 0f,
    val bassDb: Float = 0f,
    val reverbMix: Float = 0f,
    val reverbDecaySec: Float = 2.5f,
    val reverbPreDelayMs: Float = 20f,
    val reverbDamping: Float = 0.5f,
    val lowPassHz: Float = 20_000f,
    val wobbleCents: Float = 0f,
    val vinylNoise: Float = 0f,
) {
    val pitchRatio: Float
        get() = ((if (pitchFollowsSpeed) speed else 1f) * 2f.pow(pitchSemitones / 12f)).coerceIn(0.25f, 4f)
}

private val TEMPO = setOf(ModControl.SPEED, ModControl.LINK, ModControl.PITCH, ModControl.BASS)
private val SPACE = setOf(ModControl.REVERB_MIX, ModControl.DECAY, ModControl.PREDELAY, ModControl.DAMPING)

enum class ModPreset(val label: String, val controls: Set<ModControl>) {
    ORIGINAL("Original", emptySet()),
    SPED_UP("Sped Up", TEMPO),
    SLOWED("Slowed", TEMPO),
    NIGHTCORE("Nightcore", TEMPO),
    DAYCORE("Daycore", TEMPO),
    SLOWED_REVERB("Slowed + Reverb", TEMPO + SPACE),
    ULTRA_SLOWED("Ultra Slowed", TEMPO + SPACE),
    LO_FI(
        "Lo-Fi",
        setOf(ModControl.SPEED, ModControl.LOWPASS, ModControl.WOBBLE, ModControl.VINYL, ModControl.BASS, ModControl.REVERB_MIX),
    ),
    CUSTOM("Custom", ModControl.entries.toSet()),
    ;

    fun defaults(): ModSettings = when (this) {
        ORIGINAL, CUSTOM -> ModSettings()

        SPED_UP -> ModSettings(speed = 1.15f, bassDb = 1f)

        NIGHTCORE -> ModSettings(speed = 1.30f, bassDb = 2f)

        SLOWED -> ModSettings(speed = 0.85f)

        DAYCORE -> ModSettings(speed = 0.75f, lowPassHz = 12_000f)

        SLOWED_REVERB -> ModSettings(
            speed = 0.80f,
            reverbMix = 0.35f,
            reverbDecaySec = 3f,
            reverbPreDelayMs = 25f,
            reverbDamping = 0.55f,
        )

        ULTRA_SLOWED -> ModSettings(
            speed = 0.60f,
            bassDb = 2f,
            reverbMix = 0.45f,
            reverbDecaySec = 5f,
            reverbPreDelayMs = 40f,
            reverbDamping = 0.6f,
        )

        LO_FI -> ModSettings(
            speed = 0.95f, bassDb = 2f, lowPassHz = 4_500f, wobbleCents = 12f, vinylNoise = 0.25f,
            reverbMix = 0.10f, reverbDecaySec = 0.8f, reverbPreDelayMs = 10f, reverbDamping = 0.7f,
        )
    }
}

object ModController {
    data class State(val preset: ModPreset = ModPreset.ORIGINAL, val settings: ModSettings = ModSettings())

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun select(preset: ModPreset) = _state.update { cur ->
        if (preset == ModPreset.CUSTOM) State(preset, cur.settings) else State(preset, preset.defaults())
    }

    fun edit(transform: (ModSettings) -> ModSettings) = _state.update { it.copy(settings = transform(it.settings)) }

    fun reset() = _state.update { it.copy(settings = it.preset.defaults()) }
}
