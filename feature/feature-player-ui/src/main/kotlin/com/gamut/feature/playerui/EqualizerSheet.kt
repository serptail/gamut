package com.gamut.feature.playerui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamut.core.designsystem.CoverPalette
import com.gamut.core.designsystem.GamutMotion
import com.gamut.core.designsystem.GamutSheet
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.ink
import com.gamut.core.player.EqualizerController
import java.util.Locale
import kotlin.math.roundToInt

private const val StepMb = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EqualizerSheet(controller: EqualizerController, accent: Color, onDismiss: () -> Unit) {
    val s by controller.state.collectAsState()
    GamutSheet(
        onDismiss = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(32.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ink(0.2f)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Equalizer", style = MaterialTheme.typography.titleLarge, color = ink())
                    if (s.available) {
                        Text(
                            if (s.enabled) s.preset else "Off",
                            fontSize = 13.sp,
                            color = ink(0.55f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (s.available) {
                    EnableButton(enabled = s.enabled, accent = accent) { controller.setEnabled(!s.enabled) }
                }
            }

            if (!s.available) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "The equalizer connects to the audio output once a song is playing. Start a track and open this again.",
                    color = ink(0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                return@Column
            }

            Spacer(Modifier.height(16.dp))
            val presets = if (s.preset == EqualizerController.CUSTOM) {
                listOf(EqualizerController.CUSTOM) + EqualizerController.presetNames
            } else {
                EqualizerController.presetNames
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { name -> PresetChip(name, selected = s.preset == name, accent = accent) { controller.applyPreset(name) } }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .alpha(if (s.enabled) 1f else 0.4f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(ink(0.06f))
                    .padding(horizontal = 6.dp, vertical = 14.dp),
            ) {
                s.bandLevelsMb.forEachIndexed { i, mb ->
                    BandSlider(
                        levelMb = mb,
                        minMb = s.minLevelMb,
                        maxMb = s.maxLevelMb,
                        label = freqLabel(s.bandFrequenciesHz.getOrElse(i) { 0 }),
                        accent = accent,
                        enabled = s.enabled,
                        onChange = { controller.setBandLevel(i, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (s.bassBoostSupported || s.virtualizerSupported) {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (s.enabled) 1f else 0.4f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(ink(0.06f))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (s.bassBoostSupported) EffectSlider("Bass boost", s.bassBoost, accent, s.enabled, controller::setBassBoost)
                    if (s.virtualizerSupported) EffectSlider("Surround", s.virtualizer, accent, s.enabled, controller::setVirtualizer)
                }
            }
        }
    }
}

@Composable
private fun EnableButton(enabled: Boolean, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        if (enabled) "Disable" else "Enable",
        color = if (enabled) ink(0.75f) else LocalCoverPalette.current.base,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(shape)
            .then(
                if (enabled) {
                    Modifier.border(1.dp, ink(0.18f), shape)
                } else {
                    Modifier.background(accent)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

@Composable
private fun PresetChip(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val chipText by animateColorAsState(if (selected) accent else ink(0.6f), tween(GamutMotion.CONTENT_MS), label = "chip-text")
    val chipBg by animateColorAsState(if (selected) accent.copy(alpha = 0.18f) else ink(0.07f), tween(GamutMotion.CONTENT_MS), label = "chip-bg")
    Text(
        text,
        color = chipText,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(chipBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun BandSlider(
    levelMb: Int,
    minMb: Int,
    maxMb: Int,
    label: String,
    accent: Color,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChangeNow by rememberUpdatedState(onChange)
    val trackC = ink(0.16f)
    val zeroC = ink(0.14f)
    val dotC = ink()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            dbLabel(levelMb),
            color = if (levelMb != 0) accent else ink(0.45f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true),
        )
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(minMb, maxMb, enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val pad = 10.dp.toPx()
                        fun apply(y: Float) {
                            val f = 1f - ((y - pad) / (size.height - 2 * pad)).coerceIn(0f, 1f)
                            val mb = ((minMb + f * (maxMb - minMb)) / StepMb).roundToInt() * StepMb
                            onChangeNow(mb.coerceIn(minMb, maxMb))
                        }
                        val down = awaitFirstDown()
                        apply(down.position.y)
                        drag(down.id) { change ->
                            change.consume()
                            apply(change.position.y)
                        }
                    }
                },
        ) {
            val x = size.width / 2f
            val pad = 10.dp.toPx()
            val top = pad
            val bottom = size.height - pad
            fun yFor(mb: Int) = bottom - (mb - minMb).toFloat() / (maxMb - minMb) * (bottom - top)
            val stroke = 3.dp.toPx()
            val zeroY = yFor(0)
            val y = yFor(levelMb)
            drawLine(zeroC, Offset(0f, zeroY), Offset(size.width, zeroY), 1.dp.toPx())
            drawLine(trackC, Offset(x, top), Offset(x, bottom), stroke, StrokeCap.Round)
            drawLine(accent, Offset(x, zeroY), Offset(x, y), stroke, StrokeCap.Round)
            if (levelMb != 0) drawCircle(accent.copy(alpha = 0.25f), 11.dp.toPx(), Offset(x, y))
            drawCircle(dotC, 6.dp.toPx(), Offset(x, y))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            color = ink(0.5f),
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true),
        )
    }
}

@Composable
private fun EffectSlider(title: String, value: Int, accent: Color, enabled: Boolean, onChange: (Int) -> Unit) {
    Column {
        Row {
            Text(title, color = ink(0.85f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text("${value / 10}%", color = if (value > 0) accent else ink(0.5f), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        }
        LineSlider(value, 1000, accent, enabled, onChange)
    }
}

@Composable
private fun LineSlider(value: Int, max: Int, accent: Color, enabled: Boolean, onChange: (Int) -> Unit) {
    val onChangeNow by rememberUpdatedState(onChange)
    val trackC = ink(0.16f)
    val dotC = ink()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(max, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val pad = 8.dp.toPx()
                    fun apply(x: Float) {
                        val f = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                        onChangeNow((f * max).roundToInt())
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
        val pad = 8.dp.toPx()
        val cy = size.height / 2f
        val x = pad + value.toFloat() / max * (size.width - 2 * pad)
        val stroke = 4.dp.toPx()
        drawLine(trackC, Offset(pad, cy), Offset(size.width - pad, cy), stroke, StrokeCap.Round)
        drawLine(accent, Offset(pad, cy), Offset(x, cy), stroke, StrokeCap.Round)
        drawCircle(accent.copy(alpha = 0.25f), 12.dp.toPx(), Offset(x, cy))
        drawCircle(dotC, 7.dp.toPx(), Offset(x, cy))
    }
}

private fun dbLabel(mb: Int): String = if (mb == 0) "0" else String.format(Locale.US, "%+.1f", mb / 100f).removeSuffix(".0")

private fun freqLabel(hz: Int): String = if (hz >= 1000) String.format(Locale.US, "%.1fk", hz / 1000f).replace(".0k", "k") else "$hz"
