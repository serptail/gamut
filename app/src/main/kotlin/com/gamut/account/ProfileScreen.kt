package com.gamut.account

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.ArrowSquareDown
import com.adamglin.phosphoricons.bold.ArrowSquareUp
import com.adamglin.phosphoricons.bold.ArrowsClockwise
import com.adamglin.phosphoricons.bold.CalendarCheck
import com.adamglin.phosphoricons.bold.Camera
import com.adamglin.phosphoricons.bold.ClockClockwise
import com.adamglin.phosphoricons.bold.CloudCheck
import com.adamglin.phosphoricons.bold.CloudSlash
import com.adamglin.phosphoricons.bold.FloppyDisk
import com.adamglin.phosphoricons.bold.PencilSimple
import com.adamglin.phosphoricons.bold.Plugs
import com.adamglin.phosphoricons.bold.Trash
import com.adamglin.phosphoricons.bold.User
import com.gamut.R
import com.gamut.core.designsystem.LocalNotifier
import com.gamut.core.designsystem.ProfileAvatar
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale
import com.gamut.core.sync.ImportReport
import com.gamut.core.sync.SyncConfig
import com.gamut.core.sync.SyncStatus
import com.gamut.settings.GamutCheck
import com.gamut.settings.GamutDestructive
import com.gamut.settings.GamutDialog
import com.gamut.settings.GamutField
import com.gamut.settings.GamutHeading
import com.gamut.settings.GamutPage
import com.gamut.settings.PillSwitch
import com.gamut.settings.SoftCard
import com.gamut.settings.SoftHeader
import kotlin.math.ceil
import kotlinx.coroutines.delay

@Composable
fun ProfileScreen(onBack: () -> Unit, nowPlaying: @Composable () -> Unit, vm: AccountViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val notifier = LocalNotifier.current
    val userId by vm.userId.collectAsState()
    val name by vm.displayName.collectAsState()
    val customName by vm.customName.collectAsState()
    val email by vm.email.collectAsState()
    val avatar by vm.avatar.collectAsState()
    val status by vm.status.collectAsState()
    val lastSynced by vm.lastSyncedMs.collectAsState()
    val pending by vm.pendingCount.collectAsState()
    val autoBackup by vm.autoBackup.collectAsState()
    val backups by vm.backups.collectAsState()
    val importSummary by vm.importSummary.collectAsState()
    val restorePrompt by vm.restorePrompt.collectAsState()

    var editName by rememberSaveable { mutableStateOf(false) }
    var draft by remember(customName) { mutableStateOf(customName) }
    var disconnect by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    var allDevices by rememberSaveable { mutableStateOf(false) }
    var clearLocal by rememberSaveable { mutableStateOf(false) }

    var exportDialog by rememberSaveable { mutableStateOf(false) }
    var exportPassword by rememberSaveable { mutableStateOf("") }
    var exportConfirm by rememberSaveable { mutableStateOf("") }
    var exportMismatch by rememberSaveable { mutableStateOf(false) }

    var pendingImport by rememberSaveable { mutableStateOf<Uri?>(null) }
    var importPassword by rememberSaveable { mutableStateOf("") }
    var importReplace by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect {
            when (it) {
                is Notice.Success -> notifier.success(it.text)
                is Notice.Info -> notifier.info(it.text)
                is Notice.Error -> notifier.error(it.text)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.export(context.contentResolver, it, exportPassword) }
        exportPassword = ""
        exportConfirm = ""
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importPassword = ""
            importReplace = false
            pendingImport = uri
        }
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setAvatar(context.contentResolver, it) }
    }
    val pickPhoto = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val openEditName = {
        draft = customName
        editName = true
    }

    GamutPage(title = "", onBack = onBack, nowPlaying = nowPlaying) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.pressScale(onClick = pickPhoto)) {
                ProfileAvatar(name = name, seed = userId, size = 92.dp, guestIcon = PhosphorIcons.Bold.User, image = avatar, shape = CircleShape)
            }
            Column(Modifier.weight(1f).padding(start = 18.dp)) {
                Text(
                    name,
                    style = GamutHeading,
                    color = ink(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.pressScale(onClick = openEditName, scaleDown = 0.98f),
                )
                Text(
                    email ?: "Using Gamut locally",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink(0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(
                    Modifier.padding(top = 10.dp).clip(RoundedCornerShape(50)).background(ink(0.08f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (userId != null) PhosphorIcons.Bold.CloudCheck else PhosphorIcons.Bold.CloudSlash,
                        null,
                        tint = ink(0.7f),
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        if (userId != null) "Signed in" else "Local only",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink(0.7f),
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconPill(PhosphorIcons.Bold.PencilSimple, "Edit name", openEditName, Modifier.weight(1f))
            IconPill(PhosphorIcons.Bold.Camera, if (avatar != null) "Change photo" else "Add photo", pickPhoto, Modifier.weight(1f))
            if (avatar != null) {
                Box(
                    Modifier.size(44.dp).pressScale(onClick = { vm.removeAvatar() }).clip(CircleShape).background(ink(0.1f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(PhosphorIcons.Bold.Trash, "Remove photo", tint = ink(0.85f), modifier = Modifier.size(20.dp)) }
            }
        }

        if (SyncConfig.enabled) {
            SoftHeader("Sync")
            SoftCard {
                if (userId == null) {
                    IconRow(
                        icon = null,
                        painter = painterResource(R.drawable.ic_google),
                        title = "Continue with Google",
                        subtitle = "Sync and back up your library across devices.",
                        onClick = { vm.signIn(context) },
                    )
                } else {
                    SyncRow(status = status, lastSynced = lastSynced, pending = pending, onClick = vm::syncNow)
                }
            }
        }

        SoftHeader("Backup")
        SoftCard {
            IconRow(
                PhosphorIcons.Bold.ArrowSquareDown,
                "Export",
                subtitle = "Save to a file",
                onClick = {
                    exportPassword = ""
                    exportConfirm = ""
                    exportMismatch = false
                    exportDialog = true
                },
            )
            IconDivider()
            IconRow(
                PhosphorIcons.Bold.ArrowSquareUp,
                "Import",
                subtitle = "Merge or replace",
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            )
            IconDivider()
            IconRow(PhosphorIcons.Bold.FloppyDisk, "Back up now", subtitle = "On this device", onClick = vm::backupNow)
            IconDivider()
            IconRow(
                PhosphorIcons.Bold.CalendarCheck,
                "Daily backup",
                subtitle = "Last 10 days, offline",
                onClick = { vm.setAutoBackup(!autoBackup) },
                trailing = { PillSwitch(autoBackup) },
            )
        }

        if (backups.isNotEmpty()) {
            SoftHeader("Restore")
            SoftCard {
                backups.forEachIndexed { i, b ->
                    if (i > 0) IconDivider()
                    IconRow(
                        PhosphorIcons.Bold.ClockClockwise,
                        DateUtils.formatDateTime(context, b.modifiedMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH),
                        subtitle = "Tap to restore",
                        onClick = { vm.requestRestore(b) },
                    )
                }
            }
        }

        if (SyncConfig.enabled && userId != null) {
            SoftHeader("Account")
            SoftCard {
                IconRow(
                    PhosphorIcons.Bold.Plugs,
                    "Disconnect Google",
                    onClick = {
                        allDevices = false
                        clearLocal = false
                        disconnect = true
                    },
                )
                IconDivider()
                IconRow(
                    PhosphorIcons.Bold.Trash,
                    "Delete account",
                    destructive = true,
                    onClick = {
                        clearLocal = false
                        delete = true
                    },
                )
            }
        }
    }

    if (exportDialog) {
        GamutDialog(
            title = "Export backup",
            confirmLabel = "Choose location",
            confirmCloses = false,
            onDismiss = { exportDialog = false },
            onConfirm = {
                if (exportPassword.isNotEmpty() && exportPassword != exportConfirm) {
                    exportMismatch = true
                } else {
                    exportDialog = false
                    exportLauncher.launch("gamut-backup.json")
                }
            },
        ) {
            GamutField(value = exportPassword, onChange = {
                exportPassword = it
                exportMismatch = false
            }, placeholder = "Password (optional)", password = true)
            if (exportPassword.isNotEmpty()) {
                GamutField(
                    value = exportConfirm,
                    onChange = {
                        exportConfirm = it
                        exportMismatch = false
                    },
                    placeholder = "Repeat password",
                    password = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                if (exportMismatch) "Passwords don\u2019t match." else "A password encrypts the file. You\u2019ll need the same one to import it.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (exportMismatch) GamutDestructive else ink(0.5f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    pendingImport?.let { uri ->
        GamutDialog(
            title = "Import backup",
            body = "Enter the password if the file is encrypted, then choose how to bring it in.",
            confirmLabel = if (importReplace) "Replace & import" else "Import",
            destructive = importReplace,
            onDismiss = {
                pendingImport = null
                importPassword = ""
            },
            onConfirm = {
                pendingImport = null
                vm.import(context.contentResolver, uri, importPassword, importReplace)
                importPassword = ""
            },
        ) {
            GamutField(value = importPassword, onChange = { importPassword = it }, placeholder = "Password (if encrypted)", password = true)
            Column(Modifier.padding(top = 10.dp)) {
                ChoiceRow(!importReplace, "Merge", "Add to what you have. Nothing is deleted.", { importReplace = false })
                ChoiceRow(importReplace, "Replace", "Delete your current favorites and playlists first.", { importReplace = true }, warn = true)
            }
        }
    }

    restorePrompt?.let { p ->
        GamutDialog(
            title = "Restore this backup?",
            body = "Backup from " + DateUtils.formatDateTime(
                context,
                p.file.modifiedMs,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
            ) + ". It only adds what is missing; nothing you have now is removed.",
            confirmLabel = "Restore",
            onDismiss = vm::cancelRestore,
            onConfirm = vm::confirmRestore,
        ) {
            ReportRows(p.preview, done = false)
        }
    }

    importSummary?.let { s ->
        GamutDialog(
            title = if (s.report.totalAdded == 0) "Nothing new to add" else "Import complete",
            body = when {
                s.report.totalAdded == 0 -> "Everything in the backup was already in your library."
                s.replaced -> "Your previous library was replaced."
                else -> "The backup was merged into your library."
            },
            confirmLabel = "Done",
            dismissLabel = null,
            onDismiss = vm::dismissImportSummary,
            onConfirm = vm::dismissImportSummary,
        ) {
            ReportRows(s.report, done = true)
        }
    }

    if (editName) {
        GamutDialog(
            title = "Display name",
            confirmLabel = "Save",
            onDismiss = { editName = false },
            onConfirm = {
                editName = false
                vm.setName(draft)
            },
        ) {
            GamutField(value = draft, onChange = { draft = it.take(40) }, placeholder = "Your Google name")
            Text(
                "Leave empty to use the name from your Google account.",
                style = MaterialTheme.typography.bodyMedium,
                color = ink(0.5f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (disconnect) {
        GamutDialog(
            title = "Disconnect Google?",
            body = "Your library stays on this device unless you clear it.",
            confirmLabel = "Disconnect",
            destructive = clearLocal,
            onDismiss = { disconnect = false },
            onConfirm = {
                disconnect = false
                vm.signOut(allDevices, clearLocal)
            },
        ) {
            GamutCheck(allDevices, "Also sign out of other devices", { allDevices = it })
            GamutCheck(clearLocal, "Clear library on this device", { clearLocal = it }, warn = true)
            if (clearLocal && pending > 0) {
                Text(
                    "$pending change(s) haven\u2019t synced and will be lost.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GamutDestructive,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
    if (delete) {
        GamutDialog(
            title = "Delete account?",
            body = "This permanently deletes your cloud library. Export a backup first.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { delete = false },
            onConfirm = {
                delete = false
                vm.deleteAccount(clearLocal)
            },
        ) {
            GamutCheck(clearLocal, "Also clear data on this device", { clearLocal = it }, warn = true)
        }
    }
}

@Composable
private fun IconRow(
    icon: ImageVector?,
    title: String,
    onClick: () -> Unit,
    painter: Painter? = null,
    subtitle: String? = null,
    destructive: Boolean = false,
    subtitleColor: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tone = if (destructive) GamutDestructive else ink()
    Row(
        Modifier.fillMaxWidth().pressScale(onClick = onClick, scaleDown = 0.99f).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            when {
                painter != null -> Icon(painter, null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
                icon != null -> Icon(icon, null, tint = if (destructive) GamutDestructive else ink(0.85f), modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor ?: ink(0.55f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun IconDivider() {
    Box(Modifier.padding(start = 76.dp).fillMaxWidth().height(1.dp).background(ink(0.06f)))
}

@Composable
private fun IconPill(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.pressScale(onClick = onClick).clip(RoundedCornerShape(50)).background(ink(0.1f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink(0.85f), modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit, warn: Boolean = false) {
    val dot = if (warn) GamutDestructive else ink(0.95f)
    Row(Modifier.fillMaxWidth().pressScale(onClick = onClick, scaleDown = 0.98f).padding(vertical = 8.dp)) {
        Box(
            Modifier.padding(top = 2.dp).size(22.dp).border(1.5.dp, if (selected) dot else ink(0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(12.dp).background(dot, CircleShape)) }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (warn && selected) GamutDestructive else ink())
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ink(0.55f))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ink(0.7f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = ink())
    }
}

@Composable
private fun ReportRows(r: ImportReport, done: Boolean) {
    val added = if (done) "added" else "to add"
    if (r.replaced) {
        if (r.removedFavorites > 0) SummaryRow("Favorites removed first", "${r.removedFavorites}")
        if (r.removedPlaylists > 0) SummaryRow("Playlists removed first", "${r.removedPlaylists}")
        if (r.removedFollows > 0) SummaryRow("Artists removed first", "${r.removedFollows}")
    }
    if (r.favoritesAdded > 0) SummaryRow("Favorites $added", "${r.favoritesAdded}")
    if (r.playlistsCreated > 0) SummaryRow(if (done) "Playlists created" else "New playlists", "${r.playlistsCreated}")
    if (r.playlistsMerged > 0) SummaryRow("Merged into existing playlists", "${r.playlistsMerged}")
    if (r.playlistSongsAdded > 0) SummaryRow("Playlist songs $added", "${r.playlistSongsAdded}")
    if (r.followsAdded > 0) SummaryRow("Artists $added", "${r.followsAdded}")
    val already = r.favoritesAlreadyThere + r.playlistSongsAlreadyThere + r.followsAlreadyThere
    if (already > 0) SummaryRow("Already in your library", "$already")
    if (r.totalSkipped > 0) SummaryRow("Skipped (can\u2019t be restored)", "${r.totalSkipped}")
    if (r.duplicatesInFile > 0) SummaryRow("Duplicates in file ignored", "${r.duplicatesInFile}")
    if (done) {
        r.playlists.take(6).forEach { p ->
            Text(
                "${p.name} \u00B7 ${if (p.created) "new" else "merged"} \u00B7 +${p.songsAdded}",
                style = MaterialTheme.typography.bodyMedium,
                color = ink(0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (done && r.playlists.size > 6) Text("and ${r.playlists.size - 6} more playlists", style = MaterialTheme.typography.bodyMedium, color = ink(0.55f))
    r.warnings.take(3).forEach {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = GamutDestructive, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SyncRow(status: SyncStatus, lastSynced: Long?, pending: Int, onClick: () -> Unit) {
    val syncing = status is SyncStatus.Syncing
    val failed = status is SyncStatus.Error
    val offline = status is SyncStatus.Offline

    var justSynced by remember { mutableStateOf(false) }
    var wasSyncing by remember { mutableStateOf(false) }
    LaunchedEffect(status) {
        val finished = wasSyncing && status is SyncStatus.Idle
        wasSyncing = syncing
        justSynced = finished
        if (finished) {
            delay(1600)
            justSynced = false
        }
    }

    val spin = remember { Animatable(0f) }
    LaunchedEffect(syncing) {
        if (syncing) {
            while (true) spin.animateTo(spin.value + 360f, tween(1000, easing = LinearEasing))
        } else {
            spin.animateTo(ceil(spin.value / 360f) * 360f, tween(450, easing = FastOutSlowInEasing))
            spin.snapTo(0f)
        }
    }

    val shake = remember { Animatable(0f) }
    LaunchedEffect(status) {
        if (status is SyncStatus.Error) {
            repeat(3) {
                shake.animateTo(9f, tween(55))
                shake.animateTo(-9f, tween(55))
            }
            shake.animateTo(0f, tween(55))
        }
    }

    val done by animateFloatAsState(if (justSynced) 1f else 0f, tween(450), label = "done")
    val pop by animateFloatAsState(
        if (justSynced) 1.2f else 1f,
        spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium),
        label = "pop",
    )

    val accent = if (failed) GamutDestructive else ink(0.9f)

    val title = when {
        failed -> "Sync failed \u00B7 tap to retry"
        offline -> "Waiting for connection"
        syncing -> "Syncing\u2026"
        justSynced -> "All synced"
        else -> "Sync now"
    }
    val subtitle = when (status) {
        is SyncStatus.Error -> status.message

        SyncStatus.Offline ->
            if (pending > 0) "Offline \u00B7 $pending waiting, will sync when you\u2019re back" else "Offline \u00B7 will sync when you\u2019re back"

        SyncStatus.Syncing -> if (pending > 0) "$pending waiting" else "Keeping your library up to date"

        SyncStatus.Idle -> (lastSynced?.let { "Last synced ${DateUtils.getRelativeTimeSpanString(it)}" } ?: "Not synced yet") +
            if (pending > 0) " \u00B7 $pending waiting" else ""
    }

    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.fillMaxWidth()
            .pressScale(onClick = {
                if (!syncing) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            }, scaleDown = 0.99f),
    ) {
        if (syncing) SyncShimmer(Modifier.matchParentSize())
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).graphicsLayer { translationX = shake.value }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.requiredSize(38.dp)) {
                    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    val inset = stroke.width / 2
                    val box = Size(size.width - stroke.width, size.height - stroke.width)
                    if (syncing) {
                        rotate(-spin.value * 1.5f) {
                            drawArc(
                                Brush.sweepGradient(colorStops = arrayOf(0f to Color.Transparent, 0.75f to accent, 1f to accent)),
                                0f,
                                270f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = box,
                                style = stroke,
                            )
                        }
                    } else if (done > 0f) {
                        drawArc(accent, -90f, 360f * done, useCenter = false, topLeft = Offset(inset, inset), size = box, style = stroke)
                    }
                }
                Crossfade(targetState = justSynced, label = "icon") { checked ->
                    Icon(
                        when {
                            checked -> PhosphorIcons.Bold.CloudCheck
                            offline -> PhosphorIcons.Bold.CloudSlash
                            else -> PhosphorIcons.Bold.ArrowsClockwise
                        },
                        null,
                        tint = if (failed) GamutDestructive else ink(if (offline) 0.5f else 0.85f),
                        modifier = Modifier.size(24.dp).graphicsLayer {
                            rotationZ = if (checked) 0f else spin.value
                            scaleX = if (checked) pop else 1f
                            scaleY = if (checked) pop else 1f
                        },
                    )
                }
                if (pending > 0 && !syncing && !failed && !justSynced) {
                    Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(9.dp).background(ink(0.9f), CircleShape))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Crossfade(targetState = title, label = "title") {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (failed) GamutDestructive else ink(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) GamutDestructive else ink(0.55f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SyncShimmer(modifier: Modifier) {
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "x",
    )
    val tint = ink(0.10f)
    Canvas(modifier) {
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, tint, Color.Transparent),
                startX = size.width * (x - 0.3f),
                endX = size.width * (x + 0.3f),
            ),
        )
    }
}
