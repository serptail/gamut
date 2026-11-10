package com.gamut.feature.playerui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamut.core.designsystem.CoverPalette
import com.gamut.core.designsystem.GamutMotion
import com.gamut.core.designsystem.GamutSheet
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.focusRing
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.softResize
import com.gamut.core.player.ModControl
import com.gamut.core.player.ModController
import com.gamut.core.player.ModPreset
import com.gamut.core.player.ModSettings
import com.gamut.feature.playerui.components.bounceTap
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModSheet(accent: Color, onDismiss: () -> Unit) {
    val state by ModController.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    GamutSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.softResize().fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 20.dp).padding(bottom = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mod", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = ink())
                    Text(
                        when (state.preset) {
                            ModPreset.ORIGINAL -> "Pick a mod to reshape the sound"
                            ModPreset.CUSTOM -> "Custom mix"
                            else -> "${state.preset.label} \u00B7 ${presetHint(state.preset)}"
                        },
                        fontSize = 13.sp,
                        color = ink(0.55f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (state.preset != ModPreset.ORIGINAL && state.settings != state.preset.defaults()) {
                    Text(
                        "Reset",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(50))
                            .bounceTap(onClick = ModController::reset)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ModPreset.entries.chunked(3).forEach { rowPresets ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowPresets.forEach { preset ->
                            PresetCell(
                                preset = preset,
                                selected = preset == state.preset,
                                accent = accent,
                                modifier = Modifier.weight(1f),
                                onClick = { ModController.select(preset) },
                            )
                        }
                    }
                }
            }

            val controls = ModControl.entries.filter { it in state.preset.controls }
            if (controls.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(ink(0.06f))
                        .border(1.dp, ink(0.06f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    controls.forEach { ControlRow(it, state.settings, accent) }
                }
            }
        }
    }
}

@Composable
private fun PresetCell(preset: ModPreset, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val cellBg by animateColorAsState(if (selected) accent.copy(alpha = 0.20f) else ink(0.06f), tween(GamutMotion.CONTENT_MS), label = "mod-cell-bg")
    val cellEdge by animateColorAsState(if (selected) accent else ink(0.07f), tween(GamutMotion.CONTENT_MS), label = "mod-cell-edge")
    val chipBg by animateColorAsState(if (selected) accent.copy(alpha = 0.28f) else ink(0.07f), tween(GamutMotion.CONTENT_MS), label = "mod-chip-bg")
    Column(
        modifier
            .height(76.dp)
            .clip(shape)
            .background(cellBg)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, cellEdge), shape)
            .bounceTap(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            preset.label,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = ink(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            presetHint(preset),
            fontSize = 10.5.sp,
            color = if (selected) ink() else ink(0.6f),
            maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(chipBg).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun presetHint(preset: ModPreset): String = when (preset) {
    ModPreset.ORIGINAL -> "No effect"
    ModPreset.CUSTOM -> "Everything"
    else -> "%.2fx".format(preset.defaults().speed)
}

private class Spec(
    val label: String,
    val min: Float,
    val max: Float,
    val step: Float,
    val get: (ModSettings) -> Float,
    val set: (ModSettings, Float) -> ModSettings,
    val format: (Float) -> String,
)

private fun specFor(control: ModControl): Spec? = when (control) {
    ModControl.SPEED -> Spec("Speed", 0.5f, 2f, 0.05f, { it.speed }, { s, v -> s.copy(speed = v) }, { "%.2fx".format(it) })

    ModControl.PITCH -> Spec("Pitch", -12f, 12f, 0.5f, { it.pitchSemitones }, { s, v -> s.copy(pitchSemitones = v) }, { "%+.1f st".format(it) })

    ModControl.BASS -> Spec("Bass", -6f, 10f, 0.5f, { it.bassDb }, { s, v -> s.copy(bassDb = v) }, { "%+.1f dB".format(it) })

    ModControl.REVERB_MIX -> Spec("Reverb", 0f, 1f, 0.05f, { it.reverbMix }, { s, v -> s.copy(reverbMix = v) }, { "${(it * 100).roundToInt()}%" })

    ModControl.DECAY -> Spec("Decay", 0.3f, 8f, 0.1f, { it.reverbDecaySec }, { s, v -> s.copy(reverbDecaySec = v) }, { "%.1f s".format(it) })

    ModControl.PREDELAY -> Spec("Pre-delay", 0f, 100f, 5f, { it.reverbPreDelayMs }, { s, v -> s.copy(reverbPreDelayMs = v) }, { "${it.roundToInt()} ms" })

    ModControl.DAMPING -> Spec("Damping", 0f, 1f, 0.05f, { it.reverbDamping }, { s, v -> s.copy(reverbDamping = v) }, { "${(it * 100).roundToInt()}%" })

    ModControl.LOWPASS -> Spec(
        "Low-pass",
        1_500f,
        20_000f,
        500f,
        { it.lowPassHz },
        { s, v -> s.copy(lowPassHz = v) },
        { if (it >= 20_000f) "Off" else "%.1f kHz".format(it / 1000f) },
    )

    ModControl.WOBBLE -> Spec("Tape wobble", 0f, 40f, 1f, { it.wobbleCents }, { s, v -> s.copy(wobbleCents = v) }, { "${it.roundToInt()} ct" })

    ModControl.VINYL -> Spec("Vinyl noise", 0f, 1f, 0.05f, { it.vinylNoise }, { s, v -> s.copy(vinylNoise = v) }, { "${(it * 100).roundToInt()}%" })

    ModControl.LINK -> null
}

@Composable
private fun ControlRow(control: ModControl, settings: ModSettings, accent: Color) {
    if (control == ModControl.LINK) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pitch follows speed", color = ink(0.8f), style = MaterialTheme.typography.bodyMedium)
                Text("Tape style. Off keeps the original key.", fontSize = 11.sp, color = ink(0.5f))
            }
            TogglePill(on = settings.pitchFollowsSpeed, accent = accent) {
                ModController.edit { it.copy(pitchFollowsSpeed = !it.pitchFollowsSpeed) }
            }
        }
        return
    }
    val spec = specFor(control) ?: return
    val value = spec.get(settings).coerceIn(spec.min, spec.max)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row {
            Text(spec.label, color = ink(0.8f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(spec.format(value), color = accent, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        }
        LineSlider(
            value = value,
            min = spec.min,
            max = spec.max,
            step = spec.step,
            accent = accent,
            onChange = { v -> ModController.edit { spec.set(it, v) } },
        )
    }
}

@Composable
private fun TogglePill(on: Boolean, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        if (on) "On" else "Off",
        color = if (on) LocalCoverPalette.current.base else ink(0.75f),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(shape)
            .then(
                if (on) {
                    Modifier.background(accent)
                } else {
                    Modifier.border(1.dp, ink(0.18f), shape)
                },
            )
            .focusRing(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

@Composable
private fun LineSlider(value: Float, min: Float, max: Float, step: Float, accent: Color, onChange: (Float) -> Unit) {
    val onChangeNow by rememberUpdatedState(onChange)
    val trackC = ink(0.16f)
    val tickC = ink(0.3f)
    val dotC = ink()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .focusRing(RoundedCornerShape(12.dp))
            .onKeyEvent { e ->
                val dir = when (e.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> 0
                }
                if (dir == 0) return@onKeyEvent false
                if (e.type == KeyEventType.KeyDown) {
                    val mult = (1 + e.nativeKeyEvent.repeatCount / 4).coerceAtMost(8)
                    onChangeNow((value + dir * step * mult).coerceIn(min, max))
                }
                true
            }
            .focusable()
            .pointerInput(min, max, step) {
                awaitEachGesture {
                    val pad = 6.dp.toPx()
                    fun apply(x: Float) {
                        val f = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                        val snapped = min + ((f * (max - min)) / step).roundToInt() * step
                        onChangeNow(snapped.coerceIn(min, max))
                    }
                    val down = awaitFirstDown()
                    apply(down.position.x)
                    drag(down.id) { change ->
                        change.consume()
                        apply(change.position.x)
                    }
                }
            },
    ) {
        val pad = 6.dp.toPx()
        val cy = size.height / 2f
        val x = pad + (value - min) / (max - min) * (size.width - 2 * pad)
        val stroke = 4.dp.toPx()
        drawLine(trackC, Offset(pad, cy), Offset(size.width - pad, cy), stroke, StrokeCap.Round)
        drawLine(accent, Offset(pad, cy), Offset(x, cy), stroke, StrokeCap.Round)
        drawCircle(accent.copy(alpha = 0.25f), 12.dp.toPx(), Offset(x, cy))
        drawCircle(dotC, 7.dp.toPx(), Offset(x, cy))
    }
}
