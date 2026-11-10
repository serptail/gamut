package com.gamut.feature.playerui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Equalizer
import com.adamglin.phosphoricons.bold.Heart
import com.adamglin.phosphoricons.bold.ListPlus
import com.adamglin.phosphoricons.bold.MicrophoneStage
import com.adamglin.phosphoricons.bold.ShareNetwork
import com.adamglin.phosphoricons.bold.Timer
import com.adamglin.phosphoricons.bold.UserCircle
import com.gamut.core.designsystem.GamutSheet
import com.gamut.core.designsystem.ink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerOptionsSheet(
    isFavorite: Boolean,
    lyricsOn: Boolean,
    eqActive: Boolean,
    sleepActive: Boolean,
    onShare: () -> Unit,
    onToggleLyrics: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onGoToArtist: () -> Unit,
    onEqualizer: () -> Unit,
    onSleepTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    GamutSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        ) {
            Text(
                "Options",
                style = MaterialTheme.typography.titleLarge,
                color = ink(),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            OptionRow(PhosphorIcons.Bold.ShareNetwork, "Share", null, onShare)
            OptionRow(PhosphorIcons.Bold.MicrophoneStage, "Lyrics", if (lyricsOn) "On" else "Off", onToggleLyrics)
            OptionRow(
                PhosphorIcons.Bold.Heart,
                if (isFavorite) "Remove from favorites" else "Add to favorites",
                null,
                onToggleFavorite,
            )
            OptionRow(PhosphorIcons.Bold.ListPlus, "Add to playlist", null, onAddToPlaylist)
            OptionRow(PhosphorIcons.Bold.UserCircle, "Go to artist", null, onGoToArtist)
            OptionRow(PhosphorIcons.Bold.Timer, "Sleep timer", if (sleepActive) "On" else "Off", onSleepTimer)
            OptionRow(PhosphorIcons.Bold.Equalizer, "Equalizer", if (eqActive) "On" else "Off", onEqualizer)
        }
    }
}

@Composable
private fun OptionRow(icon: ImageVector, label: String, status: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ink(0.85f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = ink(), modifier = Modifier.weight(1f))
        if (status != null) {
            Text(status, style = MaterialTheme.typography.bodyMedium, color = ink(0.5f))
        }
    }
}
