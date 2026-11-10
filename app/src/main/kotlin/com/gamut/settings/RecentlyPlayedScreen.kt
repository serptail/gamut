package com.gamut.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamut.core.designsystem.ArtImage
import com.gamut.core.designsystem.GamutMotion
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale
import com.gamut.core.designsystem.squareArtUrl
import com.gamut.core.model.Track

private enum class RecentTab(val label: String) { RECENT("Recent"), STATS("Statistics") }

@Composable
fun RecentlyPlayedScreen(
    tracks: List<Track>,
    onBack: () -> Unit,
    onTrackSelected: (Track, List<Track>) -> Unit,
    nowPlaying: @Composable () -> Unit,
    statsViewModel: StatsViewModel = hiltViewModel(),
) {
    val rows = remember(tracks) { tracks.distinctBy { it.isrc } }
    val select by rememberUpdatedState(onTrackSelected)
    val pick = remember { { track: Track, list: List<Track> -> select(track, list) } }
    var tab by rememberSaveable { mutableStateOf(RecentTab.RECENT) }

    SettingsScaffold(title = "Recently played", onBack = onBack, nowPlaying = nowPlaying) {
        Column(Modifier.fillMaxSize()) {
            TabSwitch(tab, { tab = it }, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    RecentTab.RECENT -> RecentList(rows, pick)
                    RecentTab.STATS -> StatsTab(statsViewModel)
                }
            }
        }
    }
}

@Composable
private fun TabSwitch(selected: RecentTab, onSelect: (RecentTab) -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RecentTab.values().forEach { t ->
            val on = t == selected
            val bg by animateColorAsState(if (on) accent.copy(alpha = 0.22f) else ink(0.07f), tween(250), label = "recent-tab")
            Box(
                Modifier.weight(1f).pressScale(onClick = { onSelect(t) }, scaleDown = 0.97f)
                    .clip(RoundedCornerShape(50)).background(bg).padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t.label, style = MaterialTheme.typography.titleMedium, color = if (on) ink() else ink(0.6f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun RecentList(rows: List<Track>, onPick: (Track, List<Track>) -> Unit) {
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Songs you play will show up here.", style = MaterialTheme.typography.bodyLarge, color = ink(0.5f))
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp)) {
            itemsIndexed(
                items = rows,
                key = { _, t -> t.isrc },
                contentType = { _, _ -> "recent-row" },
            ) { index, track ->
                val onClick = remember(track, rows) { { onPick(track, rows) } }
                Column(
                    Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = GamutMotion.itemPlacement,
                        fadeOutSpec = null,
                    ),
                ) {
                    RecentRow(track, onClick)
                    if (index < rows.lastIndex) SettingsDivider()
                }
            }
            item(contentType = "bottom-space") { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
        }
    }
}

@Composable
private fun RecentRow(track: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressScale(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        ArtImage(
            url = remember(track.albumArtUrl) { squareArtUrl(track.albumArtUrl, 200) },
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = ink(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
