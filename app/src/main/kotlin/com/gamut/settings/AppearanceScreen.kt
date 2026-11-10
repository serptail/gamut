package com.gamut.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.gamut.core.designsystem.AcrylicHost
import com.gamut.core.designsystem.AcrylicPill
import com.gamut.core.designsystem.AppColorPresets
import com.gamut.core.designsystem.AppThemeMode
import com.gamut.core.designsystem.AppearanceLevel
import com.gamut.core.designsystem.AppearanceSettings
import com.gamut.core.designsystem.FlowStyle
import com.gamut.core.designsystem.FlowingBackground
import com.gamut.core.designsystem.GamutMotion
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.acrylicSource
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale
import com.gamut.core.designsystem.softResize
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AppearanceViewModel @Inject constructor(private val store: AppearanceStore) : ViewModel() {
    val settings: StateFlow<AppearanceSettings> = store.settings
    fun update(transform: (AppearanceSettings) -> AppearanceSettings) = store.update(transform)
    fun reset() = store.update { AppearanceSettings() }
}

@Composable
fun AppearanceScreen(onBack: () -> Unit, nowPlaying: @Composable () -> Unit, vm: AppearanceViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val showDark = s.resolvedDark(systemDark) ?: !LocalCoverPalette.current.isLight

    GamutPage(title = "Appearance", onBack = onBack, nowPlaying = nowPlaying) {
        Spacer(Modifier.height(8.dp))
        Preview()

        SoftHeader("Theme")
        SoftCard {
            val modes = buildList {
                if (s.adaptive) add(Choice(AppThemeMode.Auto, "Auto"))
                add(Choice(AppThemeMode.System, "System"))
                add(Choice(AppThemeMode.Light, "Light"))
                add(Choice(AppThemeMode.Dark, "Dark"))
            }
            OptionBlock(
                caption = when (s.effectiveMode) {
                    AppThemeMode.Auto -> "Each album decides: light covers give a light app, dark covers a dark one."
                    AppThemeMode.System -> "Follows your phone's light / dark setting."
                    AppThemeMode.Light -> "Light everywhere, including sheets and pop-ups."
                    AppThemeMode.Dark -> "Dark everywhere, including sheets and pop-ups."
                },
            ) {
                SegmentedControl(options = modes, selected = s.effectiveMode, onSelect = { m -> vm.update { it.copy(mode = m) } })
            }
        }

        SoftHeader("Background")
        SoftCard(Modifier.softResize()) {
            SoftSwitchRow(
                "Adaptive background",
                checked = s.adaptive,
                onChange = { v -> vm.update { it.copy(adaptive = v) } },
                subtitle = "Colors flow from the album you're playing",
            )
            AnimatedVisibility(s.adaptive, enter = GamutMotion.expandIn, exit = GamutMotion.collapseOut) {
                Column {
                    SoftDivider()
                    OptionBlock(title = "Style") {
                        SegmentedControl(
                            options = listOf(
                                Choice(FlowStyle.Flow, "Flow"),
                                Choice(FlowStyle.Mercury, "Mercury"),
                                Choice(FlowStyle.Metaballs, "Blobs"),
                                Choice(FlowStyle.Dithering, "Dither"),
                            ),
                            selected = s.flowStyle,
                            onSelect = { v -> vm.update { it.copy(flowStyle = v) } },
                        )
                    }
                    SoftDivider()
                    SoftSwitchRow(
                        "Animate",
                        checked = s.flowAnimated,
                        onChange = { v -> vm.update { it.copy(flowAnimated = v) } },
                        subtitle = "Slowly drifting color",
                    )
                    AnimatedVisibility(s.flowAnimated, enter = GamutMotion.expandIn, exit = GamutMotion.collapseOut) {
                        Column {
                            SoftDivider()
                            OptionBlock(title = "Speed") {
                                SegmentedControl(levels("Slow", "Normal", "Fast"), s.flowSpeed, onSelect = { v -> vm.update { it.copy(flowSpeed = v) } })
                            }
                            SoftDivider()
                            SoftSwitchRow(
                                "Freeze when paused",
                                checked = s.freezeWhenPaused,
                                onChange = { v -> vm.update { it.copy(freezeWhenPaused = v) } },
                                subtitle = "Stops the motion while nothing is playing",
                            )
                        }
                    }
                    SoftDivider()
                    OptionBlock(title = "Intensity") {
                        SegmentedControl(levels("Calm", "Balanced", "Vivid"), s.flowIntensity, onSelect = { v -> vm.update { it.copy(flowIntensity = v) } })
                    }
                }
            }
            AnimatedVisibility(!s.adaptive, enter = GamutMotion.expandIn, exit = GamutMotion.collapseOut) {
                Column {
                    SoftDivider()
                    OptionBlock(title = "Color", caption = "Fixed colors in light and dark. They don't change with the music.") {
                        PresetGrid(selectedId = s.preset, dark = showDark) { id -> vm.update { it.copy(preset = id) } }
                    }
                }
            }
        }

        SoftHeader("Glass")
        SoftCard(Modifier.softResize()) {
            SoftSwitchRow(
                "Frosted glass",
                checked = s.glass,
                onChange = { v -> vm.update { it.copy(glass = v) } },
                subtitle = "Blur behind the mini player, bars and buttons. Off is lighter on battery.",
            )
            AnimatedVisibility(s.glass, enter = GamutMotion.expandIn, exit = GamutMotion.collapseOut) {
                Column {
                    SoftDivider()
                    OptionBlock(title = "Density") {
                        SegmentedControl(levels("Light", "Balanced", "Dense"), s.glassDensity, onSelect = { v -> vm.update { it.copy(glassDensity = v) } })
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.fillMaxWidth().pressScale(onClick = vm::reset)
                .clip(RoundedCornerShape(20.dp)).background(GamutDestructive.copy(alpha = 0.12f)).padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Reset to defaults", style = MaterialTheme.typography.titleMedium, color = GamutDestructive)
        }
    }
}

private fun levels(low: String, mid: String, high: String) = listOf(
    Choice(AppearanceLevel.Low, low),
    Choice(AppearanceLevel.Medium, mid),
    Choice(AppearanceLevel.High, high),
)

@Composable
private fun OptionBlock(title: String? = null, caption: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ink(), modifier = Modifier.padding(bottom = 12.dp))
        }
        content()
        if (caption != null) {
            Box(Modifier.softResize()) {
                Crossfade(targetState = caption, animationSpec = tween(GamutMotion.CONTENT_MS), label = "option-caption") { c ->
                    Text(c, style = MaterialTheme.typography.bodyMedium, color = ink(0.55f), modifier = Modifier.padding(top = 10.dp, start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun Preview() {
    val palette = LocalCoverPalette.current
    val active = LocalPageActive.current
    Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(26.dp)).border(1.dp, ink(0.12f), RoundedCornerShape(26.dp))) {
        AcrylicHost(Modifier.fillMaxSize()) {
            FlowingBackground(palette = palette, isPlaying = active, visible = active, modifier = Modifier.fillMaxSize().acrylicSource())
            AcrylicPill(Modifier.align(Alignment.Center).padding(horizontal = 28.dp)) {
                Text("Now playing", style = MaterialTheme.typography.titleMedium, color = ink())
            }
        }
    }
}

@Composable
private fun PresetGrid(selectedId: String, dark: Boolean, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppColorPresets.all.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { p ->
                    val pal = if (dark) p.dark else p.light
                    val selected = p.id == selectedId
                    val ringWidth by animateDpAsState(if (selected) 2.dp else 1.dp, tween(GamutMotion.CONTENT_MS), label = "preset-ring-width")
                    val ringColor by animateColorAsState(if (selected) ink(0.95f) else ink(0.2f), tween(GamutMotion.CONTENT_MS), label = "preset-ring-color")
                    val labelColor by animateColorAsState(ink(if (selected) 0.95f else 0.6f), tween(GamutMotion.CONTENT_MS), label = "preset-label")
                    Column(Modifier.weight(1f).pressScale(onClick = { onPick(p.id) }, scaleDown = 0.95f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(pal.base)
                                .border(ringWidth, ringColor, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size(22.dp).background(pal.accent, CircleShape)) }
                        Text(p.name, style = MaterialTheme.typography.labelMedium, color = labelColor, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
