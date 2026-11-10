package com.gamut.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale
import com.gamut.core.designsystem.softResize
import com.gamut.core.player.BandType
import com.gamut.core.player.Crossfeed
import com.gamut.core.player.EqBand
import com.gamut.core.player.PlaybackSettings
import com.gamut.core.player.SoundSettings
import com.gamut.core.player.StreamQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

@HiltViewModel
class PlaybackSettingsViewModel @Inject constructor(
    val settings: PlaybackSettings,
    @ApplicationContext context: Context,
) : ViewModel() {
    val sound: SoundSettings = SoundSettings.get(context)
}

private enum class PlaybackDialog { QUALITY, PRESET, BANDS, RESET }

private val FadeChoices = listOf(
    Choice(0, "Off"),
    Choice(300, "Short", "0.3 seconds"),
    Choice(600, "Medium", "0.6 seconds"),
    Choice(1_000, "Long", "1 second"),
)

private fun hzLabel(hz: Int) = if (hz >= 1_000) String.format(Locale.US, "%.1f kHz", hz / 1_000f).replace(".0 ", " ") else "$hz Hz"

private fun hzShort(hz: Int) = if (hz >= 1_000) String.format(Locale.US, "%.1fk", hz / 1_000f).replace(".0k", "k") else "$hz"

private fun dbLabel(mb: Int) = String.format(Locale.US, "%+.1f dB", mb / 100f)

private val LnMinHz = ln(20f)
private val LnMaxHz = ln(20_000f)

@Composable
private fun BandEditorDialog(bands: List<EqBand>, sound: SoundSettings, onDismiss: () -> Unit) {
    GamutDialog(
        title = "Frequency and Q",
        body = "Move each band along the spectrum and set how wide it is. A lower Q is wider.",
        confirmLabel = "Done",
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        dismissLabel = null,
    ) {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            bands.forEachIndexed { i, b ->
                Text("Band ${i + 1}", style = MaterialTheme.typography.titleMedium, color = ink(), modifier = Modifier.padding(top = 14.dp))
                Column(Modifier.padding(top = 4.dp)) {
                    SliderCell(
                        title = "Frequency",
                        valueLabel = hzLabel(b.freqHz.roundToInt()),
                        value = ln(b.freqHz),
                        range = LnMinHz..LnMaxHz,
                        onChange = { sound.setBandFreq(i, exp(it).roundToInt().toFloat()) },
                    )
                    SliderCell(
                        title = if (b.type == BandType.PEAK) "Q" else "Steepness",
                        valueLabel = String.format(Locale.US, "%.2f", b.q),
                        value = b.q,
                        range = 0.3f..8f,
                        onChange = { sound.setBandQ(i, (it * 20).roundToInt() / 20f) },
                    )
                }
            }
        }
    }
}

private fun balanceLabel(b: Float): String {
    val pct = (abs(b) * 100).roundToInt()
    return when {
        pct == 0 -> "Centre"
        b < 0 -> "Left $pct%"
        else -> "Right $pct%"
    }
}

@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    nowPlaying: @Composable () -> Unit,
    vm: PlaybackSettingsViewModel = hiltViewModel(),
) {
    val s by vm.settings.state.collectAsState()
    val snd by vm.sound.state.collectAsState()
    var dialog by remember { mutableStateOf<PlaybackDialog?>(null) }
    var activeBand by remember { mutableStateOf<Int?>(null) }
    val settings = vm.settings
    val sound = vm.sound

    GamutPage(title = "Playback & Audio", onBack = onBack, nowPlaying = nowPlaying) {
        SoftHeader("Streaming")
        SoftCard {
            SoftValueRow(
                "Streaming quality",
                value = s.streamQuality.label,
                subtitle = s.streamQuality.hint,
                onClick = { dialog = PlaybackDialog.QUALITY },
            )
            SoftDivider()
            SoftSwitchRow(
                "Preload next track",
                checked = s.preload,
                onChange = { v -> settings.update { it.copy(preload = v) } },
                subtitle = "Gapless transitions and instant skips. Uses a little extra data.",
            )
            SoftDivider()
            SoftSwitchRow(
                "Preload on Wi-Fi only",
                checked = s.preloadUnmeteredOnly,
                enabled = s.preload,
                onChange = { v -> settings.update { it.copy(preloadUnmeteredOnly = v) } },
                subtitle = "On mobile data the next track loads when you get to it.",
            )
        }

        SoftHeader("Playback")
        SoftCard {
            SoftSliderRow(
                title = "Crossfade",
                valueLabel = if (s.crossfadeMs == 0) "Off" else "${s.crossfadeMs / 1000} s",
                value = s.crossfadeMs / 1000f,
                range = 0f..12f,
                steps = 11,
                onChange = { v -> settings.update { it.copy(crossfadeMs = v.roundToInt() * 1000) } },
                subtitle = "Songs overlap as one ends and the next begins. Off keeps them gapless.",
            )
            SoftDivider()
            Column(Modifier.softResize().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("Fade on pause and resume", style = MaterialTheme.typography.titleMedium, color = ink())
                Spacer(Modifier.height(12.dp))
                SegmentedControl(
                    options = FadeChoices,
                    selected = s.fadeMs,
                    onSelect = { ms -> settings.update { it.copy(fadeMs = ms) } },
                )
                FadeChoices.firstOrNull { it.value == s.fadeMs }?.hint?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = ink(0.55f), modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
            }
            SoftDivider()
            SoftSwitchRow(
                "Skip silence",
                checked = s.skipSilence,
                onChange = { v -> settings.update { it.copy(skipSilence = v) } },
                subtitle = "Cut long silent gaps inside tracks, like in live recordings.",
            )
            SoftDivider()
            SoftSwitchRow(
                "Pause when headphones disconnect",
                checked = s.pauseOnUnplug,
                onChange = { v -> settings.update { it.copy(pauseOnUnplug = v) } },
            )
        }

        SoftHeader("Equalizer")
        SoftCard {
            SoftSwitchRow(
                "10-band parametric EQ",
                checked = snd.eqEnabled,
                onChange = sound::setEqEnabled,
                subtitle = "64-bit filters, tuned band by band.",
            )
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                val band = activeBand?.let { snd.bands.getOrNull(it) }
                Text(
                    if (band != null) "${hzLabel(band.freqHz.roundToInt())}  ·  ${dbLabel((band.gainDb * 100).roundToInt())}" else snd.preset,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink(0.6f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                EqCurve(snd.bands, SoundSettings.MAX_GAIN_DB, snd.eqEnabled, LnMinHz, LnMaxHz)
                Spacer(Modifier.height(12.dp))
                EqBandSliders(
                    bands = snd.bands,
                    maxDb = SoundSettings.MAX_GAIN_DB,
                    enabled = snd.eqEnabled,
                    labelFor = { hzShort(it.freqHz.roundToInt()) },
                    onGain = { i, g -> sound.setBandGain(i, g) },
                    onActive = { activeBand = it },
                )
            }
            SoftDivider()
            SoftValueRow("Preset", value = snd.preset, onClick = { dialog = PlaybackDialog.PRESET })
            SoftDivider()
            SoftValueRow("Frequency and Q", subtitle = "Move and reshape each band", onClick = { dialog = PlaybackDialog.BANDS })
        }

        SoftHeader("Sound quality")
        SoftCard {
            SoftSwitchRow(
                "Automatic headroom",
                checked = snd.autoHeadroom,
                onChange = sound::setAutoHeadroom,
                subtitle = "Lowers the level by exactly the EQ's biggest boost, so boosted bands cannot clip.",
            )
            SoftDivider()
            SoftSwitchRow(
                "Soft limiter",
                checked = snd.limiter,
                onChange = sound::setLimiter,
                subtitle = "Catches peaks just below full scale before they distort.",
            )
            SoftDivider()
            SoftSwitchRow(
                "Dither",
                checked = snd.dither,
                onChange = sound::setDither,
                subtitle = "Adds a whisper of triangular noise when the sound is processed, so quiet detail is not rounded away.",
            )
            SoftDivider()
            Column(Modifier.softResize().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("Headphone crossfeed", style = MaterialTheme.typography.titleMedium, color = ink())
                Text(
                    "Lets a little of the lows cross over between ears, like speakers do.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink(0.55f),
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(12.dp))
                SegmentedControl(Crossfeed.entries.map { Choice(it, it.label, it.hint) }, snd.crossfeed, sound::setCrossfeed)
                if (snd.crossfeed != Crossfeed.OFF) {
                    Text(snd.crossfeed.hint, style = MaterialTheme.typography.bodyMedium, color = ink(0.55f), modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
            }
            SoftDivider()
            SoftSliderRow(
                title = "Stereo width",
                valueLabel = "${snd.widthPercent}%",
                value = snd.widthPercent.toFloat(),
                range = 0f..200f,
                onChange = { v -> sound.setWidth(if (abs(v - 100f) < 4f) 100 else v.roundToInt()) },
                subtitle = "0% is mono, 100% is the recording as it is.",
                centered = true,
            )
        }

        SoftHeader("Output")
        SoftCard {
            SoftSwitchRow(
                "Mono audio",
                checked = s.mono,
                onChange = { v -> settings.update { it.copy(mono = v) } },
                subtitle = "Play both channels as one. Helps with a single earbud or hearing differences.",
            )
            SoftDivider()
            SoftSliderRow(
                title = "Balance",
                valueLabel = balanceLabel(s.balance),
                value = s.balance,
                range = -1f..1f,
                onChange = { v -> settings.update { it.copy(balance = if (abs(v) < 0.04f) 0f else v) } },
                centered = true,
            )
        }

        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.fillMaxWidth().pressScale(onClick = { dialog = PlaybackDialog.RESET })
                .clip(RoundedCornerShape(20.dp)).background(GamutDestructive.copy(alpha = 0.12f)).padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Reset audio settings", style = MaterialTheme.typography.titleMedium, color = GamutDestructive)
        }
    }

    when (dialog) {
        PlaybackDialog.QUALITY -> GamutChoiceDialog(
            title = "Streaming quality",
            choices = StreamQuality.entries.map { Choice(it, it.label, it.hint) },
            selected = s.streamQuality,
            onSelect = { q -> settings.update { it.copy(streamQuality = q) } },
            onDismiss = { dialog = null },
            note = "Applies from the next track you play. Downloads have their own quality setting in Storage & Downloads.",
        )

        PlaybackDialog.PRESET -> GamutChoiceDialog(
            title = "Equalizer preset",
            choices = buildList {
                addAll(SoundSettings.presetNames.map { Choice(it, it) })
                if (snd.preset == SoundSettings.CUSTOM) add(Choice(SoundSettings.CUSTOM, "Custom"))
            },
            selected = snd.preset,
            onSelect = sound::applyPreset,
            onDismiss = { dialog = null },
        )

        PlaybackDialog.BANDS -> BandEditorDialog(snd.bands, sound) { dialog = null }

        PlaybackDialog.RESET -> GamutDialog(
            title = "Reset audio settings?",
            body = "Streaming, playback, output and sound-quality options go back to their defaults, and the equalizer returns to Flat.",
            confirmLabel = "Reset",
            destructive = true,
            onDismiss = { dialog = null },
            onConfirm = {
                settings.reset()
                sound.reset()
                dialog = null
            },
        )

        null -> Unit
    }
}
