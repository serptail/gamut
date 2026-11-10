package com.gamut.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamut.core.designsystem.ArtImage
import com.gamut.core.designsystem.LocalArtistPhotoResolver
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale
import com.gamut.core.designsystem.squareArtUrl
import com.gamut.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.NumberFormat
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StatsViewModel @Inject constructor(private val store: PlayStatsStore) : ViewModel() {
    private val period = MutableStateFlow(StatsPeriod.ALL)
    val selected: StateFlow<StatsPeriod> = period.asStateFlow()

    val summary: StateFlow<StatsSummary> = combine(store.snapshot, period) { data, p ->
        summarize(data, p, System.currentTimeMillis())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsSummary())

    fun record(track: Track) = store.record(track)

    fun select(p: StatsPeriod) {
        period.value = p
    }
    fun reset() = store.clear()
}

private const val COLLAPSED_SONGS = 5
private const val COLLAPSED_ARTISTS = 4

@Composable
internal fun StatsTab(vm: StatsViewModel) {
    val summary by vm.summary.collectAsState()
    val period by vm.selected.collectAsState()
    var showAllSongs by rememberSaveable { mutableStateOf(false) }
    var showAllArtists by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp)) {
        item(key = "periods", contentType = "periods") {
            PeriodSwitch(period, vm::select, Modifier.padding(top = 4.dp))
        }
        if (summary.totalPlays == 0) {
            item(key = "empty", contentType = "empty") {
                Text(
                    if (period == StatsPeriod.ALL) {
                        "Play a few songs and your listening stats will show up here."
                    } else {
                        "Nothing played in this period yet."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = ink(0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 64.dp),
                )
            }
        } else {
            item(key = "summary", contentType = "summary") { SummaryStrip(summary) }

            if (summary.topArtists.isNotEmpty()) {
                item(key = "artists-title", contentType = "heading") { SectionTitle("Top artists") }
                item(key = "artists-grid", contentType = "artists-grid") {
                    ArtistGrid(summary.topArtists, showAllArtists) { showAllArtists = !showAllArtists }
                }
            }

            if (summary.topSongs.isNotEmpty()) {
                item(key = "songs-title", contentType = "heading") { SectionTitle("Top songs") }
                val maxSong = summary.topSongs.first().plays.coerceAtLeast(1)
                val songs = if (showAllSongs) summary.topSongs else summary.topSongs.take(COLLAPSED_SONGS)
                itemsIndexed(
                    songs,
                    key = { _, s -> "song-${s.song.isrc}" },
                    contentType = { _, _ -> "song" },
                ) { index, s ->
                    RankRow(
                        rank = index + 1,
                        title = s.song.title,
                        subtitle = s.song.artist,
                        plays = s.plays,
                        fraction = s.plays / maxSong.toFloat(),
                        leading = {
                            ArtImage(
                                url = remember(s.song.artUrl) { squareArtUrl(s.song.artUrl, 200) },
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                            )
                        },
                    )
                }
                if (summary.topSongs.size > COLLAPSED_SONGS) {
                    item(key = "songs-more", contentType = "more") {
                        Text(
                            if (showAllSongs) "Show less" else "Show more",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                                .pressScale(onClick = { showAllSongs = !showAllSongs }, scaleDown = 0.96f)
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }

            item(key = "reset", contentType = "reset") {
                Text(
                    "Reset statistics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink(0.4f),
                    modifier = Modifier.padding(top = 28.dp)
                        .pressScale(onClick = { confirmReset = true }, scaleDown = 0.96f)
                        .padding(vertical = 8.dp),
                )
            }
        }
        item(contentType = "bottom-space") { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
    }

    if (confirmReset) {
        GamutDialog(
            title = "Reset statistics?",
            body = "Your play counts are cleared. Favorites, playlists and the Recent list are not affected.",
            confirmLabel = "Reset",
            destructive = true,
            onDismiss = { confirmReset = false },
            onConfirm = {
                confirmReset = false
                showAllSongs = false
                showAllArtists = false
                vm.reset()
            },
        ) { }
    }
}

@Composable
private fun PeriodSwitch(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        StatsPeriod.values().forEach { p ->
            val on = p == selected
            val color by animateColorAsState(if (on) ink() else ink(0.4f), tween(250), label = "stats-period")
            val line by animateColorAsState(if (on) accent else Color.Transparent, tween(250), label = "stats-period-line")
            Column(
                Modifier.pressScale(onClick = { onSelect(p) }, scaleDown = 0.96f).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(p.label, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
                Box(Modifier.padding(top = 6.dp).width(22.dp).height(2.dp).clip(CircleShape).background(line))
            }
        }
    }
}

@Composable
private fun SummaryStrip(summary: StatsSummary) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp)
            .clip(RoundedCornerShape(18.dp)).background(ink(0.05f)).padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Figure(summary.totalPlays, if (summary.totalPlays == 1) "Play" else "Plays", Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(30.dp).background(ink(0.1f)))
        Figure(summary.uniqueSongs, if (summary.uniqueSongs == 1) "Song" else "Songs", Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(30.dp).background(ink(0.1f)))
        Figure(summary.uniqueArtists, if (summary.uniqueArtists == 1) "Artist" else "Artists", Modifier.weight(1f))
    }
}

@Composable
private fun Figure(value: Int, label: String, modifier: Modifier = Modifier) {
    var target by remember { mutableStateOf(0) }
    LaunchedEffect(value) { target = value }
    val shown by animateIntAsState(target, tween(700, easing = FastOutSlowInEasing), label = "stats-figure")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            NumberFormat.getIntegerInstance().format(shown),
            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
            color = ink(),
            maxLines = 1,
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ink(0.55f), modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = ink(0.55f),
        modifier = Modifier.padding(top = 30.dp, bottom = 4.dp),
    )
}

@Composable
private fun RankRow(
    rank: Int,
    title: String,
    subtitle: String?,
    plays: Int,
    fraction: Float,
    leading: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = if (rank == 1) MaterialTheme.colorScheme.primary else ink(0.4f),
            maxLines = 1,
            modifier = Modifier.width(22.dp),
        )
        leading()
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ink(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ShareBar(fraction, Modifier.padding(top = 7.dp))
        }
        Text(
            NumberFormat.getIntegerInstance().format(plays),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = ink(0.85f),
            maxLines = 1,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

@Composable
private fun ShareBar(fraction: Float, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val progress = remember { Animatable(0f) }
    val target = fraction.coerceIn(0.03f, 1f)
    LaunchedEffect(target) { progress.animateTo(target, tween(650, easing = FastOutSlowInEasing)) }
    Box(modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(ink(0.08f))) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    scaleX = progress.value
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .background(accent.copy(alpha = 0.8f), CircleShape),
        )
    }
}

@Composable
private fun ArtistGrid(artists: List<RankedArtist>, expanded: Boolean, onToggle: () -> Unit) {
    val hasMore = artists.size > COLLAPSED_ARTISTS
    val shown = if (expanded || !hasMore) artists else artists.take(COLLAPSED_ARTISTS)
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        shown.chunked(2).forEach { ArtistPairRow(it) }
        if (hasMore && !expanded) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().fadeOutBottom()) {
                    ArtistPairRow(artists.drop(COLLAPSED_ARTISTS).take(2), Modifier.padding(bottom = 44.dp))
                }
                MoreToggle("Show more", onToggle, Modifier.align(Alignment.BottomCenter))
            }
        } else if (hasMore) {
            MoreToggle("Show less", onToggle, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun MoreToggle(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.pressScale(onClick = onClick, scaleDown = 0.96f).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ArtistPairRow(pair: List<RankedArtist>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        pair.forEach { ArtistCell(it, Modifier.weight(1f)) }
        if (pair.size < 2) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ArtistCell(artist: RankedArtist, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ArtistCircle(artist, 52.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(artist.name, style = MaterialTheme.typography.bodyLarge, color = ink(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (artist.plays == 1) "1 play" else "${NumberFormat.getIntegerInstance().format(artist.plays)} plays",
                style = MaterialTheme.typography.bodyMedium,
                color = ink(0.55f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun Modifier.fadeOutBottom(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(colorStops = arrayOf(0f to Color.Black, 1f to Color.Transparent)),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun ArtistCircle(artist: RankedArtist, size: Dp) {
    val photo = rememberArtistPhoto(artist.name, artist.album)
    Box(Modifier.size(size).clip(CircleShape).background(ink(0.1f)), contentAlignment = Alignment.Center) {
        if (photo != null) {
            ArtImage(url = photo, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                artist.name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = ink(0.55f),
            )
        }
    }
}

@Composable
private fun rememberArtistPhoto(name: String, album: String?): String? {
    val resolver = LocalArtistPhotoResolver.current
    var url by remember(name) { mutableStateOf<String?>(null) }
    LaunchedEffect(name, album, resolver) {
        url = resolver?.invoke(name, album) ?: leadArtist(name)?.let { resolver?.invoke(it, album) }
    }
    return url
}

private fun leadArtist(name: String): String? = name.split(" & ", " feat. ", " ft. ", " x ").first().trim().takeIf { it.isNotEmpty() && !it.equals(name.trim(), ignoreCase = true) }
