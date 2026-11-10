package com.gamut.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.gamut.feature.resolver.PlaybackHealth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SourceNoticeViewModel @Inject constructor(health: PlaybackHealth) : ViewModel() {
    val failing: StateFlow<Boolean> = health.failing
}

@Composable
fun SourceNoticeBanner(
    onCheckForUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourceNoticeViewModel = hiltViewModel(),
) {
    val failing by viewModel.failing.collectAsState()
    var dismissed by remember(failing) { mutableStateOf(false) }
    if (!failing || dismissed) return

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Songs can\u2019t be loaded right now", style = MaterialTheme.typography.titleSmall)
            Text(
                "The music source has probably changed. When a fixed version is out you\u2019ll be asked to update; until then, try again later.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { dismissed = true }) { Text("Dismiss") }
                TextButton(onClick = onCheckForUpdate) { Text("Check for update") }
            }
        }
    }
}
