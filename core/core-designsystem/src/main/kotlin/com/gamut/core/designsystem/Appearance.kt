package com.gamut.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeMode { Auto, System, Light, Dark }

enum class AppearanceLevel { Low, Medium, High }

@Immutable
data class AppearanceSettings(
    val mode: AppThemeMode = AppThemeMode.Auto,
    val adaptive: Boolean = true,
    val preset: String = "graphite",
    val flowStyle: FlowStyle = FlowStyle.Flow,
    val flowAnimated: Boolean = true,
    val flowIntensity: AppearanceLevel = AppearanceLevel.Medium,
    val flowSpeed: AppearanceLevel = AppearanceLevel.Medium,
    val freezeWhenPaused: Boolean = false,
    val glass: Boolean = true,
    val glassDensity: AppearanceLevel = AppearanceLevel.Medium,
) {
    val effectiveMode: AppThemeMode get() = if (mode == AppThemeMode.Auto && !adaptive) AppThemeMode.System else mode

    fun resolvedDark(systemDark: Boolean): Boolean? = when (effectiveMode) {
        AppThemeMode.Auto -> null
        AppThemeMode.System -> systemDark
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }

    fun staticPalette(dark: Boolean): CoverPalette = AppColorPresets.byId(preset).let { if (dark) it.dark else it.light }

    fun neutralPalette(systemDark: Boolean): CoverPalette {
        val dark = resolvedDark(systemDark)
        return when {
            !adaptive -> staticPalette(dark ?: systemDark)
            dark == false -> CoverPalette.NeutralLight
            else -> CoverPalette.Neutral
        }
    }
}

val LocalAppearance = staticCompositionLocalOf { AppearanceSettings() }

val AppearanceLevel.flowStrength: Float get() = when (this) {
    AppearanceLevel.Low -> 0.65f
    AppearanceLevel.Medium -> 1f
    AppearanceLevel.High -> 1.25f
}
val AppearanceLevel.flowSpeed: Float get() = when (this) {
    AppearanceLevel.Low -> 0.5f
    AppearanceLevel.Medium -> 1f
    AppearanceLevel.High -> 1.7f
}
val AppearanceLevel.blurScale: Float get() = when (this) {
    AppearanceLevel.Low -> 0.7f
    AppearanceLevel.Medium -> 1f
    AppearanceLevel.High -> 1.35f
}
val AppearanceLevel.tintScale: Float get() = when (this) {
    AppearanceLevel.Low -> 0.8f
    AppearanceLevel.Medium -> 1f
    AppearanceLevel.High -> 1.3f
}

@Immutable
class AppColorPreset(val id: String, val name: String, val dark: CoverPalette, val light: CoverPalette)

private fun solid(base: Long, accent: Long, light: Boolean): CoverPalette {
    val b = Color(base)
    return CoverPalette(
        primary = b,
        secondary = b,
        tertiary = b,
        accent = Color(accent),
        base = b,
        onBase = if (light) Color(0xFF141414) else Color.White,
        isLight = light,
        flows = false,
    )
}

private fun preset(id: String, name: String, darkBase: Long, darkAccent: Long, lightBase: Long, lightAccent: Long) = AppColorPreset(id, name, solid(darkBase, darkAccent, false), solid(lightBase, lightAccent, true))

object AppColorPresets {
    val all = listOf(
        preset("graphite", "Graphite", 0xFF0B0B0D, 0xFFFFFFFF, 0xFFF4F4F6, 0xFF141414),
        preset("rose", "Rose", 0xFF1E1016, 0xFFFF8FB1, 0xFFFFF1F5, 0xFFC2185B),
        preset("ocean", "Ocean", 0xFF0D1620, 0xFF7DB9FF, 0xFFEFF6FF, 0xFF1565C0),
        preset("forest", "Forest", 0xFF0E1A14, 0xFF6FD6A0, 0xFFF0F9F3, 0xFF2E7D4F),
        preset("crimson", "Crimson", 0xFF1F0E10, 0xFFFF7A7A, 0xFFFFF1F1, 0xFFC62828),
        preset("violet", "Violet", 0xFF15121F, 0xFFB8A6F5, 0xFFF5F2FF, 0xFF5E4BC2),
        preset("amber", "Amber", 0xFF1F1608, 0xFFFFC266, 0xFFFFF7E8, 0xFFB26A00),
        preset("teal", "Teal", 0xFF0C1A1B, 0xFF5FD4D6, 0xFFEEF9F9, 0xFF00767A),
    )

    fun byId(id: String): AppColorPreset = all.firstOrNull { it.id == id } ?: all[0]
}

@Composable
fun rememberAppPalette(artUrl: String?, settings: AppearanceSettings = LocalAppearance.current): CoverPalette {
    val systemDark = isSystemInDarkTheme()
    val dark = settings.resolvedDark(systemDark)
    val cover = rememberCoverPalette(if (settings.adaptive) artUrl else null, forceLight = dark?.not())
    return if (settings.adaptive) cover else settings.staticPalette(dark ?: systemDark)
}

@Composable
fun appNeutralPalette(settings: AppearanceSettings = LocalAppearance.current): CoverPalette = settings.neutralPalette(isSystemInDarkTheme())
