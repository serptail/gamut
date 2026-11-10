package com.gamut.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.CaretRight
import com.adamglin.phosphoricons.bold.ClockCounterClockwise
import com.adamglin.phosphoricons.bold.HardDrives
import com.adamglin.phosphoricons.bold.Headphones
import com.adamglin.phosphoricons.bold.Heart
import com.adamglin.phosphoricons.bold.Info
import com.adamglin.phosphoricons.bold.Palette
import com.adamglin.phosphoricons.bold.Playlist
import com.adamglin.phosphoricons.bold.Question
import com.adamglin.phosphoricons.bold.User
import com.gamut.account.AccountViewModel
import com.gamut.core.designsystem.LocalNotifier
import com.gamut.core.designsystem.ProfileAvatar
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class UpdateCheckStatus { Found, UpToDate, Failed, Unavailable }

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLikedSongs: () -> Unit,
    onPlaylists: () -> Unit,
    onRecentlyPlayed: () -> Unit,
    onProfile: () -> Unit,
    onAppearance: () -> Unit,
    onPlaybackAudio: () -> Unit,
    onStorage: () -> Unit,
    onFeedback: () -> Unit,
    onAbout: () -> Unit,
    nowPlaying: @Composable () -> Unit,
    onCheckForUpdate: suspend () -> UpdateCheckStatus = { UpdateCheckStatus.Unavailable },
    vm: AccountViewModel = hiltViewModel(),
) {
    val notifier = LocalNotifier.current
    val soon: () -> Unit = { notifier.info("Coming soon") }
    val userId by vm.userId.collectAsState()
    val name by vm.displayName.collectAsState()
    val email by vm.email.collectAsState()
    val avatar by vm.avatar.collectAsState()
    val supportVm: SupportViewModel = hiltViewModel()
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val active = LocalPageActive.current
    LaunchedEffect(active) {
        if (active) {
            delay(600)
            supportVm.store.clearNudge()
        }
    }

    val checkForUpdate: () -> Unit = {
        if (!checking) {
            checking = true
            scope.launch {
                val status = try {
                    onCheckForUpdate()
                } finally {
                    checking = false
                }
                when (status) {
                    UpdateCheckStatus.UpToDate -> notifier.info("You\u2019re on the latest version")
                    UpdateCheckStatus.Failed -> notifier.info("Couldn\u2019t check for updates. Try again later.")
                    UpdateCheckStatus.Unavailable -> notifier.info("Updates aren\u2019t available in this build")
                    UpdateCheckStatus.Found -> Unit
                }
            }
        }
    }

    GamutPage(
        title = "Settings",
        onBack = onBack,
        nowPlaying = nowPlaying,
        actions = { UpdateCheckButton(checking = checking, onClick = checkForUpdate) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).pressScale(onClick = onProfile, scaleDown = 0.985f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(name = name, seed = userId, size = 60.dp, guestIcon = PhosphorIcons.Bold.User, image = avatar)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(email ?: "View profile", style = MaterialTheme.typography.bodyMedium, color = ink(0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(PhosphorIcons.Bold.CaretRight, null, tint = ink(0.45f), modifier = Modifier.size(18.dp))
        }

        GroupLabel("Your music")
        Cell("Liked Songs", icon = PhosphorIcons.Bold.Heart, onClick = onLikedSongs)
        Cell("Your Playlists", icon = PhosphorIcons.Bold.Playlist, onClick = onPlaylists)
        Cell("Recently Played", icon = PhosphorIcons.Bold.ClockCounterClockwise, onClick = onRecentlyPlayed)

        GroupLabel("Preferences")
        Cell("Appearance", icon = PhosphorIcons.Bold.Palette, subtitle = "Theme, colors and glass", onClick = onAppearance)
        Cell("Playback & Audio", icon = PhosphorIcons.Bold.Headphones, subtitle = "Quality, equalizer, fades and more", onClick = onPlaybackAudio)
        Cell("Storage & Downloads", icon = PhosphorIcons.Bold.HardDrives, subtitle = "Cache and offline songs", onClick = onStorage)

        GroupLabel("Support")
        Cell("Help & Feedback", icon = PhosphorIcons.Bold.Question, subtitle = "Report a bug or share an idea", onClick = onFeedback)
        Cell("About Gamut", icon = PhosphorIcons.Bold.Info, subtitle = "Version, license and credits", onClick = onAbout)

        SupportRow()
    }
}

@Composable
private fun UpdateCheckButton(checking: Boolean, onClick: () -> Unit) {
    Text(
        if (checking) "Checking\u2026" else "Check for updates",
        style = MaterialTheme.typography.labelLarge,
        color = ink(if (checking) 0.55f else 0.85f),
        maxLines = 1,
        modifier = Modifier
            .pressScale(onClick = onClick, scaleDown = 0.95f)
            .clip(CircleShape)
            .background(ink(0.07f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}