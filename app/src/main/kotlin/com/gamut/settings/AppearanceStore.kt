package com.gamut.settings

import android.content.Context
import com.gamut.core.designsystem.AppThemeMode
import com.gamut.core.designsystem.AppearanceLevel
import com.gamut.core.designsystem.AppearanceSettings
import com.gamut.core.designsystem.FlowStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class AppearanceStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("gamut_appearance", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppearanceSettings> = _settings

    fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        save(next)
    }

    private inline fun <reified E : Enum<E>> enum(key: String, default: E): E = runCatching { enumValueOf<E>(prefs.getString(key, null) ?: return default) }.getOrDefault(default)

    private fun load(): AppearanceSettings {
        val d = AppearanceSettings()
        return AppearanceSettings(
            mode = enum("mode", d.mode),
            adaptive = prefs.getBoolean("adaptive", d.adaptive),
            preset = prefs.getString("preset", d.preset) ?: d.preset,
            flowStyle = enum("flow_style", d.flowStyle),
            flowAnimated = prefs.getBoolean("flow_animated", d.flowAnimated),
            flowIntensity = enum("flow_intensity", d.flowIntensity),
            flowSpeed = enum("flow_speed", d.flowSpeed),
            freezeWhenPaused = prefs.getBoolean("freeze_paused", d.freezeWhenPaused),
            glass = prefs.getBoolean("glass", d.glass),
            glassDensity = enum("glass_density", d.glassDensity),
        )
    }

    private fun save(s: AppearanceSettings) {
        prefs.edit()
            .putString("mode", s.mode.name)
            .putBoolean("adaptive", s.adaptive)
            .putString("preset", s.preset)
            .putString("flow_style", s.flowStyle.name)
            .putBoolean("flow_animated", s.flowAnimated)
            .putString("flow_intensity", s.flowIntensity.name)
            .putString("flow_speed", s.flowSpeed.name)
            .putBoolean("freeze_paused", s.freezeWhenPaused)
            .putBoolean("glass", s.glass)
            .putString("glass_density", s.glassDensity.name)
            .apply()
    }
}
