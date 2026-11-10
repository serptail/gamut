package com.gamut.settings

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.CaretRight
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.bold.DownloadSimple
import com.adamglin.phosphoricons.bold.MusicNote
import com.adamglin.phosphoricons.bold.Trash
import com.gamut.core.designsystem.GamutMotion
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.LocalNotifier
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale
import com.gamut.core.designsystem.softResize
import com.gamut.feature.resolver.DownloadQuality
import com.gamut.feature.resolver.DownloadState
import com.gamut.feature.resolver.TrackDownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SongSort(val label: String) { RECENT("Recent"), SIZE("Size"), TITLE("A\u2013Z") }

private enum class StorageDialog { QUALITY, CACHE_LIMIT }

private sealed interface Confirm {
    data class DeleteSong(val song: DownloadedSong) : Confirm
    data class DeleteSelected(val ids: Set<String>) : Confirm
    data object DeleteAll : Confirm
    data object ClearImages : Confirm
    data object ClearDiscover : Confirm
    data object ClearAllCache : Confirm
    data object CleanLeftovers : Confirm
}

private val SortChoices = SongSort.entries.map { Choice(it, it.label) }
private val CacheLimitChoices = listOf(
    Choice(0, "No limit"),
    Choice(250, "250 MB"),
    Choice(500, "500 MB"),
    Choice(1_000, "1 GB"),
    Choice(2_000, "2 GB"),
)
private const val PAGE = 8

@Composable
fun StorageSettingsScreen(
    onBack: () -> Unit,
    nowPlaying: @Composable () -> Unit,
    vm: StorageViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val active = LocalPageActive.current
    val snap by vm.snapshot.collectAsState()
    val songs by vm.songs.collectAsState()
    val stats by vm.stats.collectAsState()
    val cfg by vm.settings.state.collectAsState()
    val fmt: (Long) -> String = remember(context) { { b: Long -> Formatter.formatShortFileSize(context, b) } }

    var sort by rememberSaveable { mutableStateOf(SongSort.RECENT) }
    var visible by rememberSaveable { mutableIntStateOf(PAGE) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var removing by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var dialog by remember { mutableStateOf<StorageDialog?>(null) }

    LaunchedEffect(active) {
        if (active) {
            vm.onOpened()
            while (true) {
                delay(8_000)
                vm.refreshNow()
            }
        } else {
            selecting = false
            selected = emptySet()
            visible = PAGE
        }
    }
    LaunchedEffect(songs) { removing = removing.filter { id -> songs.any { it.isrc == id } }.toSet() }

    val sorted = remember(songs, sort) {
        when (sort) {
            SongSort.RECENT -> songs.sortedByDescending { it.addedMs }
            SongSort.SIZE -> songs.sortedByDescending { it.bytes }
            SongSort.TITLE -> songs.sortedBy { it.title.lowercase() }
        }
    }

    fun deleteSongs(ids: Collection<String>, announce: String) {
        removing = removing + ids
        scope.launch {
            delay(260)
            vm.delete(ids)
            notifier.success(announce)
        }
    }

    GamutPage(title = "Storage & Downloads", onBack = onBack, nowPlaying = nowPlaying) {
        SoftHeader("Used by Gamut")
        UsageHero(snap, fmt)

        QueueCard(
            manager = vm.downloads,
            onCancelAll = vm.downloads::cancelAll,
            onRetry = vm.downloads::retryFailed,
            onClearFailed = vm.downloads::clearFailed,
            onAllowMobile = { vm.settings.update { it.copy(wifiOnly = false) } },
        )

        SoftHeader("Your downloads")
        SoftCard {
            Column(Modifier.softResize()) {
                if (songs.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (songs.size == 1) "1 song" else "${songs.size} songs",
                                style = MaterialTheme.typography.titleMedium,
                                color = ink(),
                                modifier = Modifier.weight(1f),
                            )
                            if (songs.isNotEmpty()) {
                                TextAction(if (selecting) "Done" else "Select") {
                                    selecting = !selecting
                                    selected = emptySet()
                                }
                            }
                        }
                        if (songs.size > 1) {
                            Spacer(Modifier.height(12.dp))
                            SegmentedControl(options = SortChoices, selected = sort, onSelect = { sort = it })
                        }
                    }
                }
                if (songs.isEmpty()) {
                    EmptyDownloads()
                } else {
                    SoftDivider()
                    AnimatedVisibility(
                        visible = selecting,
                        enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val all = selected.size == songs.size
                            GamutButton(if (all) "Select none" else "Select all", onClick = {
                                selected = if (all) emptySet() else songs.map { it.isrc }.toSet()
                            }, modifier = Modifier.weight(1f), filled = false)
                            DestructiveButton(
                                if (selected.isEmpty()) "Delete" else "Delete (${selected.size})",
                                onClick = { confirm = Confirm.DeleteSelected(selected) },
                                modifier = Modifier.weight(1f),
                                enabled = selected.isNotEmpty(),
                            )
                        }
                    }
                    sorted.take(visible).forEach { song ->
                        androidx.compose.runtime.key(song.isrc) {
                            AnimatedVisibility(
                                visible = song.isrc !in removing,
                                enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                                exit = shrinkVertically(tween(240, easing = GamutMotion.Standard)) + fadeOut(tween(160)),
                            ) {
                                SongRow(
                                    song = song,
                                    detail = fmt(song.bytes) + " \u00B7 " + song.format,
                                    selecting = selecting,
                                    checked = song.isrc in selected,
                                    onToggle = { selected = if (song.isrc in selected) selected - song.isrc else selected + song.isrc },
                                    onDelete = { confirm = Confirm.DeleteSong(song) },
                                )
                            }
                        }
                    }
                    if (sorted.size > visible) {
                        SoftDivider()
                        TextAction(
                            "Show ${minOf(PAGE, sorted.size - visible)} more \u00B7 ${sorted.size - visible} hidden",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            centered = true,
                        ) { visible += PAGE }
                    }
                    SoftDivider()
                    TextAction(
                        "Delete all downloads",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        centered = true,
                        color = GamutDestructive,
                    ) { confirm = Confirm.DeleteAll }
                }
            }
        }

        SoftHeader("Cache")
        SoftCard {
            Column(Modifier.softResize()) {
                CacheRow(
                    "Images & temporary files",
                    fmt(snap?.imageCacheBytes ?: 0L),
                    (snap?.imageCacheBytes ?: 0L) > 0,
                ) { confirm = Confirm.ClearImages }
                SoftDivider()
                CacheRow(
                    "Discover & search",
                    fmt(snap?.discoverCacheBytes ?: 0L),
                    (snap?.discoverCacheBytes ?: 0L) > 0,
                ) { confirm = Confirm.ClearDiscover }
                SoftDivider()
                TextAction(
                    "Clear all cache",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    centered = true,
                    color = GamutDestructive,
                ) { confirm = Confirm.ClearAllCache }
            }
        }

        SoftHeader("Settings")
        SoftCard {
            SoftSwitchRow(
                "Download on Wi-Fi only",
                checked = cfg.wifiOnly,
                onChange = { v -> vm.settings.update { it.copy(wifiOnly = v) } },
            )
            SoftDivider()
            StorageValueRow(
                "Download quality",
                value = cfg.downloadQuality.label,
                onClick = { dialog = StorageDialog.QUALITY },
            )
            SoftDivider()
            StorageValueRow(
                "Cache limit",
                value = limitLabel(cfg.cacheLimitMb),
                onClick = { dialog = StorageDialog.CACHE_LIMIT },
            )
        }

        AnimatedVisibility((snap?.leftoverCount ?: 0) > 0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                SoftHeader("Tidy up")
                SoftCard {
                    StorageValueRow(
                        "Leftover files",
                        value = fmt(snap?.leftoverBytes ?: 0L),
                        onClick = { confirm = Confirm.CleanLeftovers },
                    )
                }
            }
        }
    }

    when (val c = confirm) {
        is Confirm.DeleteSong -> GamutDialog(
            title = "Delete this song?",
            body = "\u201C${c.song.title}\u201D will be removed from this device.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirm = null },
            onConfirm = {
                deleteSongs(listOf(c.song.isrc), "Deleted \u201C${c.song.title}\u201D")
                confirm = null
            },
        )

        is Confirm.DeleteSelected -> GamutDialog(
            title = if (c.ids.size == 1) "Delete 1 song?" else "Delete ${c.ids.size} songs?",
            body = "They will be removed from this device.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirm = null },
            onConfirm = {
                deleteSongs(c.ids, if (c.ids.size == 1) "Deleted 1 song" else "Deleted ${c.ids.size} songs")
                selecting = false
                selected = emptySet()
                confirm = null
            },
        )

        Confirm.DeleteAll -> GamutDialog(
            title = "Delete all downloads?",
            body = "${songs.size} songs will be removed from this device.",
            confirmLabel = "Delete all",
            destructive = true,
            onDismiss = { confirm = null },
            onConfirm = {
                vm.deleteAll()
                selecting = false
                selected = emptySet()
                confirm = null
                notifier.success("All downloads deleted")
            },
        )

        Confirm.ClearImages -> ClearDialog("Clear images & temporary files?", "${fmt(snap?.imageCacheBytes ?: 0L)} will be freed.", { confirm = null }) {
            vm.clear(CacheTarget.IMAGES) { notifier.success(if (it > 0) "Freed ${fmt(it)}" else "Nothing to clear") }
            confirm = null
        }

        Confirm.ClearDiscover -> ClearDialog("Clear Discover & search cache?", "${fmt(snap?.discoverCacheBytes ?: 0L)} will be freed.", { confirm = null }) {
            vm.clear(CacheTarget.DISCOVER) { notifier.success(if (it > 0) "Freed ${fmt(it)}" else "Cache cleared") }
            confirm = null
        }

        Confirm.ClearAllCache -> ClearDialog("Clear all cache?", "${fmt(snap?.cacheBytes ?: 0L)} will be freed.", { confirm = null }) {
            vm.clear(CacheTarget.ALL) { notifier.success(if (it > 0) "Freed ${fmt(it)}" else "Cache was already empty") }
            confirm = null
        }

        Confirm.CleanLeftovers -> ClearDialog("Delete leftover files?", "${fmt(snap?.leftoverBytes ?: 0L)} will be freed.", { confirm = null }) {
            vm.cleanLeftovers { notifier.success(if (it > 0) "Freed ${fmt(it)}" else "Nothing to clean") }
            confirm = null
        }

        null -> Unit
    }

    when (dialog) {
        StorageDialog.QUALITY -> GamutChoiceDialog(
            title = "Download quality",
            choices = DownloadQuality.entries.map { Choice(it, it.label, it.hint) },
            selected = cfg.downloadQuality,
            onSelect = { q -> vm.settings.update { it.copy(downloadQuality = q) } },
            onDismiss = { dialog = null },
            note = "Applies to new downloads. Songs you already downloaded keep the quality they have.",
        )

        StorageDialog.CACHE_LIMIT -> GamutChoiceDialog(
            title = "Cache limit",
            choices = CacheLimitChoices,
            selected = cfg.cacheLimitMb,
            onSelect = { mb ->
                vm.settings.update { it.copy(cacheLimitMb = mb) }
                vm.onOpened()
            },
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

private fun limitLabel(mb: Int) = CacheLimitChoices.firstOrNull { it.value == mb }?.label ?: "$mb MB"

@Composable
private fun ClearDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) = GamutDialog(title = title, body = body, confirmLabel = "Clear", destructive = true, onDismiss = onDismiss, onConfirm = onConfirm)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsageHero(snap: StorageSnapshot?, fmt: (Long) -> String) {
    val spec = tween<Float>(700, easing = GamutMotion.Standard)
    val total = (snap?.totalBytes ?: 0L)
    val downloads by animateFloatAsState((snap?.downloadsBytes ?: 0L).toFloat(), spec, label = "seg-downloads")
    val cache by animateFloatAsState((snap?.cacheBytes ?: 0L).toFloat(), spec, label = "seg-cache")
    val other by animateFloatAsState(((snap?.appDataBytes ?: 0L) + (snap?.leftoverBytes ?: 0L)).toFloat(), spec, label = "seg-other")
    val shownTotal by animateFloatAsState(total.toFloat(), spec, label = "hero-total")
    val accent = MaterialTheme.colorScheme.primary
    val cacheTone = ink(0.55f)
    val otherTone = ink(0.22f)

    SoftCard {
        Column(Modifier.softResize().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                if (snap == null) "\u2026" else fmt(shownTotal.toLong()),
                style = MaterialTheme.typography.headlineLarge,
                color = ink(),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(ink(0.08f)),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val sum = downloads + cache + other
                if (sum > 0f) {
                    if (downloads > 0f) Box(Modifier.weight(downloads / sum).fillMaxHeight().background(accent))
                    if (cache > 0f) Box(Modifier.weight(cache / sum).fillMaxHeight().background(cacheTone))
                    if (other > 0f) Box(Modifier.weight(other / sum).fillMaxHeight().background(otherTone))
                }
            }
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LegendItem(accent, "Downloads", fmt(snap?.downloadsBytes ?: 0L))
                LegendItem(cacheTone, "Cache", fmt(snap?.cacheBytes ?: 0L))
                LegendItem(otherTone, "App data", fmt((snap?.appDataBytes ?: 0L) + (snap?.leftoverBytes ?: 0L)))
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            "$label ($value)",
            style = MaterialTheme.typography.bodyMedium,
            color = ink(0.7f),
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun QueueCard(manager: TrackDownloadManager, onCancelAll: () -> Unit, onRetry: () -> Unit, onClearFailed: () -> Unit, onAllowMobile: () -> Unit) {
    val batch by manager.batch.collectAsState()
    val live by manager.states.collectAsState()
    val failed = live.values.count { it is DownloadState.Failed }
    val progress = live.values.filterIsInstance<DownloadState.Active>().firstOrNull()?.progress ?: 0f
    var shownBatch by remember { mutableStateOf(batch) }
    if (batch.total > 0) shownBatch = batch
    var shownFailed by remember { mutableIntStateOf(failed) }
    if (failed > 0) shownFailed = failed

    AnimatedVisibility(
        visible = batch.total > 0 || failed > 0,
        enter = expandVertically(tween(280)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(220)) + fadeOut(tween(160)),
    ) {
        Column {
            SoftHeader("Download queue")
            SoftCard {
                Column(Modifier.softResize().padding(20.dp)) {
                    if (shownBatch.total > 0) {
                        val fraction = ((shownBatch.done + progress) / shownBatch.total).coerceIn(0f, 1f)
                        val bar by animateFloatAsState(fraction, tween(300), label = "queue-bar")
                        Text(
                            if (shownBatch.waitingForWifi) "Waiting for Wi-Fi" else "Downloading ${minOf(shownBatch.done + 1, shownBatch.total)} of ${shownBatch.total}",
                            style = MaterialTheme.typography.titleMedium,
                            color = ink(),
                        )
                        Text(
                            if (shownBatch.waitingForWifi) "\u201CDownload on Wi-Fi only\u201D is on, so nothing downloads over mobile data." else shownBatch.current.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink(0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Spacer(Modifier.height(14.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(ink(0.08f))) {
                            Box(Modifier.fillMaxWidth(bar.coerceAtLeast(0.02f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary, CircleShape))
                        }
                        Spacer(Modifier.height(14.dp))
                        if (shownBatch.waitingForWifi) {
                            GamutButton("Download on mobile data", onAllowMobile, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                        }
                        GamutButton("Cancel all", onCancelAll, Modifier.fillMaxWidth(), filled = false)
                    }
                    if (shownFailed > 0) {
                        if (shownBatch.total > 0) Spacer(Modifier.height(18.dp))
                        Text(
                            if (shownFailed == 1) "1 download failed" else "$shownFailed downloads failed",
                            style = MaterialTheme.typography.titleMedium,
                            color = GamutDestructive,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GamutButton("Retry", onRetry, Modifier.weight(1f))
                            GamutButton("Dismiss", onClearFailed, Modifier.weight(1f), filled = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(
    song: DownloadedSong,
    detail: String,
    selecting: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val tap = if (selecting) Modifier.pressScale(onClick = onToggle, scaleDown = 0.985f) else Modifier
    Row(
        Modifier.fillMaxWidth().then(tap).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Crossfade(selecting, animationSpec = tween(GamutMotion.CONTENT_MS), label = "row-lead") { pick ->
                if (pick) {
                    SelectDot(checked)
                } else {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(ink(0.08f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(PhosphorIcons.Bold.MusicNote, null, tint = ink(0.7f), modifier = Modifier.size(22.dp)) }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(song.artist, detail).filter { it.isNotBlank() }.joinToString(" \u00B7 "),
                style = MaterialTheme.typography.bodyMedium,
                color = ink(0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(!selecting, enter = fadeIn(tween(160)), exit = fadeOut(tween(100))) {
            Box(Modifier.size(40.dp).pressScale(onClick = onDelete), contentAlignment = Alignment.Center) {
                Icon(PhosphorIcons.Bold.Trash, "Delete", tint = ink(0.6f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SelectDot(checked: Boolean) {
    val onInk = if (LocalCoverPalette.current.isLight) Color.White else Color.Black
    val fill by animateColorAsState(if (checked) ink(0.95f) else ink(0.95f).copy(alpha = 0f), tween(GamutMotion.CONTENT_MS), label = "dot-fill")
    val edge by animateColorAsState(if (checked) ink(0.3f).copy(alpha = 0f) else ink(0.3f), tween(GamutMotion.CONTENT_MS), label = "dot-edge")
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(fill).border(1.5.dp, edge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(checked, enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.6f), exit = fadeOut(tween(100))) {
            Icon(PhosphorIcons.Bold.Check, null, tint = onInk, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun EmptyDownloads() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(PhosphorIcons.Bold.DownloadSimple, null, tint = ink(0.4f), modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(12.dp))
        Text("No downloads yet", style = MaterialTheme.typography.titleMedium, color = ink(0.6f))
    }
}

@Composable
private fun StorageValueRow(title: String, value: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(
        Modifier
            .softResize()
            .fillMaxWidth()
            .pressScale(onClick = onClick, scaleDown = 0.985f)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ink(), modifier = Modifier.weight(1f).padding(end = 12.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = ink(0.6f), maxLines = 1)
            Icon(PhosphorIcons.Bold.CaretRight, null, tint = ink(0.4f), modifier = Modifier.padding(start = 6.dp).size(14.dp))
        }
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ink(0.55f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun CacheRow(title: String, size: String, hasData: Boolean, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ink())
            Text(
                size,
                style = MaterialTheme.typography.bodyMedium,
                color = ink(0.55f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            "Clear",
            style = MaterialTheme.typography.labelLarge,
            color = ink(if (hasData) 0.85f else 0.35f),
            modifier = Modifier
                .alpha(if (hasData) 1f else 0.6f)
                .pressScale(onClick = { if (hasData) onClear() })
                .clip(CircleShape).background(ink(0.08f)).padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun TextAction(
    label: String,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    color: Color = ink(0.85f),
    onClick: () -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = modifier.pressScale(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

@Composable
private fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.alpha(if (enabled) 1f else 0.4f)
            .pressScale(onClick = { if (enabled) onClick() })
            .clip(RoundedCornerShape(20.dp)).background(GamutDestructive.copy(alpha = 0.12f)).padding(vertical = 16.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = GamutDestructive, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
