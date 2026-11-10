package com.gamut.update

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gamut.core.designsystem.CoverTheme
import com.gamut.core.designsystem.GamutTheme
import com.gamut.core.designsystem.LocalAppearance
import com.gamut.core.designsystem.appNeutralPalette
import com.gamut.core.designsystem.ink
import com.gamut.settings.GamutButton
import com.gamut.settings.GamutDestructive
import com.gamut.settings.GamutHeading
import kotlinx.coroutines.delay

@Composable
fun UpdateGate(vm: AppUpdateViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val ui by vm.state.collectAsState()

    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) vm.onStart() }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    if (!ui.checking && (ui.required == null || ui.skipped)) {
        content()
        return
    }

    val palette = appNeutralPalette(LocalAppearance.current)
    GamutTheme(dark = !palette.isLight) {
        CoverTheme(palette) {
            Box(Modifier.fillMaxSize().background(palette.base).systemBarsPadding(), contentAlignment = Alignment.Center) {
                val info = ui.required
                if (info == null) {
                    var showSpinner by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(500)
                        showSpinner = true
                    }
                    if (showSpinner) CircularProgressIndicator(Modifier.size(28.dp), color = ink(0.6f), strokeWidth = 2.dp)
                } else {
                    UpdateRequired(ui, info, onUpdate = vm::startUpdate, onSkip = vm::skip, onCancel = vm::cancelUpdate)
                }
            }
        }
    }
}

@Composable
private fun UpdateRequired(ui: UpdateUiState, info: UpdateInfo, onUpdate: () -> Unit, onSkip: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
        Text(if (ui.optional) "Update available" else "Update required", style = GamutHeading, color = ink())
        Text(
            if (ui.optional) {
                "Gamut ${info.versionName} is out. Update now to get the latest fixes and improvements."
            } else {
                "Gamut ${info.versionName} is out. YouTube changes all the time, so every update has to be installed to keep playback working."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = ink(0.7f),
            modifier = Modifier.padding(top = 12.dp),
        )
        if (info.notes.isNotBlank()) ReleaseNotes(info.notes.take(400))
        Spacer(Modifier.height(28.dp))

        when (ui.phase) {
            UpdatePhase.DOWNLOADING -> UpdateProgress(ui.progress, if (ui.waiting) "Waiting for connection\u2026" else "Downloading")

            UpdatePhase.INSTALLING -> UpdateProgress(1f, "Installing")

            UpdatePhase.NEEDS_PERMISSION -> InfoBlock(
                "Android needs your OK before Gamut can install updates. Allow it on the next screen, then come back.",
                color = ink(0.7f),
            )

            else -> Unit
        }
        if (ui.error != null) InfoBlock(ui.error, color = GamutDestructive)

        GamutButton(
            label = when (ui.phase) {
                UpdatePhase.READY -> "Update now"
                UpdatePhase.NEEDS_PERMISSION -> "Allow installs"
                UpdatePhase.DOWNLOADING -> "Downloading..."
                UpdatePhase.INSTALLING -> "Installing..."
                UpdatePhase.FAILED -> "Try again"
            },
            onClick = onUpdate,
            modifier = Modifier.fillMaxWidth(),
        )

        if (ui.phase == UpdatePhase.DOWNLOADING || ui.phase == UpdatePhase.INSTALLING) {
            Text(
                "Cancel",
                style = MaterialTheme.typography.bodyMedium,
                color = ink(0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onCancel).padding(top = 8.dp).padding(vertical = 8.dp),
            )
        }
        Text(
            "Skip for now",
            style = MaterialTheme.typography.bodySmall,
            color = ink(0.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSkip).padding(top = 4.dp, bottom = 12.dp).padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun ReleaseNotes(notes: String) {
    Column(
        Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ink(0.07f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            "WHAT'S NEW",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = ink(0.45f),
        )
        Text(notes, style = MaterialTheme.typography.bodyMedium, color = ink(0.8f), modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun InfoBlock(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ink(0.07f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun UpdateProgress(progress: Float, label: String) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = tween(250), label = "update-progress")
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = ink(0.7f))
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = ink(0.7f))
        }
        Box(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(ink(0.14f)),
        ) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().clip(CircleShape).background(ink(0.95f)))
        }
    }
}