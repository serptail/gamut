package com.gamut.core.player

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BandType { LOW_SHELF, PEAK, HIGH_SHELF }

data class EqBand(val type: BandType, val freqHz: Float, val gainDb: Float, val q: Float)

enum class Crossfeed(val label: String, val hint: String, val amount: Float) {
    OFF("Off", "The recording as it is", 0f),
    LIGHT("Light", "A gentle touch, keeps a wide stage", 0.12f),
    MEDIUM("Medium", "Closest to listening on speakers", 0.22f),
    STRONG("Strong", "For hard-panned older stereo mixes", 0.32f),
}

internal val DefaultBandFreqs = floatArrayOf(60f, 130f, 250f, 500f, 1_000f, 2_000f, 3_500f, 6_000f, 10_000f, 14_000f)

internal fun defaultBands(gains: FloatArray? = null): List<EqBand> = List(DefaultBandFreqs.size) { i ->
    val edge = i == 0 || i == DefaultBandFreqs.lastIndex
    EqBand(
        type = when (i) {
            0 -> BandType.LOW_SHELF
            DefaultBandFreqs.lastIndex -> BandType.HIGH_SHELF
            else -> BandType.PEAK
        },
        freqHz = DefaultBandFreqs[i],
        gainDb = gains?.getOrNull(i) ?: 0f,
        q = if (edge) 0.71f else 1.2f,
    )
}

class SoundSettings private constructor(context: Context) {
    data class State(
        val eqEnabled: Boolean = false,
        val preset: String = FLAT,
        val bands: List<EqBand> = defaultBands(),
        val autoHeadroom: Boolean = true,
        val limiter: Boolean = true,
        val dither: Boolean = true,
        val crossfeed: Crossfeed = Crossfeed.OFF,
        val widthPercent: Int = 100,
    )

    private val prefs = context.applicationContext.getSharedPreferences("sound_engine", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state.asStateFlow()

    @Synchronized
    private fun edit(transform: (State) -> State) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        save(next)
    }

    fun setEqEnabled(on: Boolean) = edit { it.copy(eqEnabled = on) }

    fun setBandGain(index: Int, gainDb: Float) = edit { s -> s.withBand(index) { it.copy(gainDb = gainDb.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB)) } }

    fun setBandFreq(index: Int, hz: Float) = edit { s -> s.withBand(index) { it.copy(freqHz = hz.coerceIn(20f, 20_000f)) } }

    fun setBandQ(index: Int, q: Float) = edit { s -> s.withBand(index) { it.copy(q = q.coerceIn(0.2f, 10f)) } }

    fun applyPreset(name: String) {
        val gains = PRESETS[name] ?: return
        edit { it.copy(preset = name, bands = defaultBands(gains)) }
    }

    fun setAutoHeadroom(on: Boolean) = edit { it.copy(autoHeadroom = on) }
    fun setLimiter(on: Boolean) = edit { it.copy(limiter = on) }
    fun setDither(on: Boolean) = edit { it.copy(dither = on) }
    fun setCrossfeed(level: Crossfeed) = edit { it.copy(crossfeed = level) }
    fun setWidth(percent: Int) = edit { it.copy(widthPercent = percent.coerceIn(0, 200)) }

    fun resetEqualizer() = edit { it.copy(preset = FLAT, bands = defaultBands(), widthPercent = 100) }

    fun reset() = edit { State() }

    private fun State.withBand(index: Int, change: (EqBand) -> EqBand): State {
        if (index !in bands.indices) return this
        return copy(bands = bands.toMutableList().also { it[index] = change(it[index]) }, preset = CUSTOM)
    }

    private fun save(s: State) {
        prefs.edit()
            .putBoolean(K_EQ, s.eqEnabled)
            .putString(K_PRESET, s.preset)
            .putString(K_BANDS, s.bands.joinToString(";") { "${it.type.name},${it.freqHz},${it.gainDb},${it.q}" })
            .putBoolean(K_HEADROOM, s.autoHeadroom)
            .putBoolean(K_LIMITER, s.limiter)
            .putBoolean(K_DITHER, s.dither)
            .putString(K_XFEED, s.crossfeed.name)
            .putInt(K_WIDTH, s.widthPercent)
            .apply()
    }

    private fun load(): State {
        val bands = prefs.getString(K_BANDS, null)?.split(';')?.mapNotNull(::parseBand)
            ?.takeIf { it.size == DefaultBandFreqs.size } ?: defaultBands()
        return State(
            eqEnabled = prefs.getBoolean(K_EQ, false),
            preset = prefs.getString(K_PRESET, FLAT) ?: FLAT,
            bands = bands,
            autoHeadroom = prefs.getBoolean(K_HEADROOM, true),
            limiter = prefs.getBoolean(K_LIMITER, true),
            dither = prefs.getBoolean(K_DITHER, true),
            crossfeed = Crossfeed.entries.firstOrNull { it.name == prefs.getString(K_XFEED, null) } ?: Crossfeed.OFF,
            widthPercent = prefs.getInt(K_WIDTH, 100).coerceIn(0, 200),
        )
    }

    private fun parseBand(text: String): EqBand? {
        val p = text.split(',')
        if (p.size != 4) return null
        val type = BandType.entries.firstOrNull { it.name == p[0] } ?: return null
        return EqBand(type, p[1].toFloatOrNull() ?: return null, p[2].toFloatOrNull() ?: return null, p[3].toFloatOrNull() ?: return null)
    }

    companion object {
        const val FLAT = "Flat"
        const val CUSTOM = "Custom"
        const val MAX_GAIN_DB = 12f

        private const val K_EQ = "eq_enabled"
        private const val K_PRESET = "preset"
        private const val K_BANDS = "bands"
        private const val K_HEADROOM = "auto_headroom"
        private const val K_LIMITER = "limiter"
        private const val K_DITHER = "dither"
        private const val K_XFEED = "crossfeed"
        private const val K_WIDTH = "width"

        private val PRESETS: Map<String, FloatArray> = linkedMapOf(
            FLAT to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Bass Booster" to floatArrayOf(6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Bass Reducer" to floatArrayOf(-6f, -5f, -3f, -1f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Treble Booster" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 2.5f, 4f, 5f, 5.5f),
            "Vocal Booster" to floatArrayOf(-3f, -2f, -1f, 0f, 1.5f, 3f, 3.5f, 2f, 0f, -1f),
            "Rock" to floatArrayOf(5f, 4f, 2.5f, -0.5f, -1.5f, 0.5f, 2.5f, 3.5f, 4.5f, 4.5f),
            "Pop" to floatArrayOf(-1.5f, -0.5f, 1.5f, 3f, 3.5f, 2.5f, 0.5f, -0.5f, -1f, -1.5f),
            "Hip-Hop" to floatArrayOf(5.5f, 5f, 3.5f, 1.5f, -0.5f, -0.5f, 1f, 0.5f, 1.5f, 2.5f),
            "Electronic" to floatArrayOf(5f, 4f, 1.5f, 0f, -1.5f, 1f, 0f, 1.5f, 4f, 4.5f),
            "Jazz" to floatArrayOf(3f, 2.5f, 1.5f, 2f, -1.5f, -1.5f, 0f, 1.5f, 2.5f, 3.5f),
            "Classical" to floatArrayOf(4f, 3f, 2f, 1.5f, -0.5f, -0.5f, 0f, 2f, 3f, 4f),
            "Loudness" to floatArrayOf(6f, 5f, 2.5f, 0f, -1f, -0.5f, 0.5f, 2f, 4.5f, 5f),
        )

        val presetNames: List<String> = PRESETS.keys.toList()

        @Volatile private var instance: SoundSettings? = null

        fun get(context: Context): SoundSettings = instance ?: synchronized(this) {
            instance ?: SoundSettings(context.applicationContext).also { instance = it }
        }
    }
}

fun SoundSettings.State.toParams() = SoundParams(
    bands = if (eqEnabled) bands else emptyList(),
    autoHeadroom = autoHeadroom,
    limiter = limiter,
    dither = dither,
    crossfeed = crossfeed.amount,
    width = widthPercent / 100f,
)
