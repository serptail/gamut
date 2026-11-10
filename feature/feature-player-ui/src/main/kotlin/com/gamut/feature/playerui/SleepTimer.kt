package com.gamut.feature.playerui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gamut.core.designsystem.GamutSheet
import com.gamut.core.designsystem.ink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SleepTimer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val _endsAt = MutableStateFlow<Long?>(null)
    val endsAt: StateFlow<Long?> = _endsAt.asStateFlow()

    fun start(minutes: Int, onFire: () -> Unit) {
        cancel()
        val end = SystemClock.elapsedRealtime() + minutes * 60_000L
        _endsAt.value = end
        job = scope.launch {
            delay((end - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            onFire()
            _endsAt.value = null
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _endsAt.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepTimerSheet(onPick: (minutes: Int) -> Unit, onTurnOff: () -> Unit, onDismiss: () -> Unit) {
    val endsAt by SleepTimer.endsAt.collectAsState()
    GamutSheet(
        onDismiss = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge, color = ink())
            endsAt?.let { end ->
                val left = ((end - SystemClock.elapsedRealtime()) / 60_000L + 1).coerceAtLeast(1)
                Spacer(Modifier.height(4.dp))
                Text("Playback pauses in about $left min", color = ink(0.6f), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            listOf(5, 10, 15, 30, 45, 60).forEach { m ->
                Text(
                    "$m minutes",
                    color = ink(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(m) }.padding(vertical = 14.dp),
                )
            }
            if (endsAt != null) {
                Text(
                    "Turn off timer",
                    color = ink(0.7f),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onTurnOff).padding(vertical = 14.dp),
                )
            }
        }
    }
}
