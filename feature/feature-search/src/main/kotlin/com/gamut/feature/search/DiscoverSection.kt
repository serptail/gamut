package com.gamut.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.CaretRight
import com.adamglin.phosphoricons.bold.DotsThreeVertical
import com.adamglin.phosphoricons.fill.Play
import com.gamut.core.designsystem.ArtImage
import com.gamut.core.designsystem.NowPlayingBars
import com.gamut.core.designsystem.TrackRowSkeleton
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale
import com.gamut.core.designsystem.squareArtUrl
import com.gamut.core.model.Track

private val NUMBERED_TABS = setOf("Trending", "Billboard")
private const val COLLAPSED_ROWS = 10

internal fun LazyListScope.discoverFeaturedItems(
    state: DiscoverState,
    onTrackClick: (Track, List<Track>) -> Unit,
    nowPlayingIsrc: String?,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (DiscoverItem) -> Unit,
    onRetry: () -> Unit = {},
    onMoreClick: (Track) -> Unit = {},
    onArtistItemClick: (DiscoverItem) -> Unit = {},
) {
    if (state.tabs.isEmpty()) {
        if (state.error != null && !state.isLoading) {
            item(key = "discover-error") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Try again",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        modifier = Modifier.pressScale(onClick = onRetry, scaleDown = 0.92f).padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        } else {
            item(key = "discover-skeleton-hero") {
                Box(
                    Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .aspectRatio(1.35f)
                        .clip(CardShapeLarge)
                        .background(ink(0.06f)),
                )
            }
            items(6, key = { "discover-skeleton-$it" }) { TrackRowSkeleton(Modifier.padding(horizontal = 24.dp)) }
        }
        return
    }

    val trending = state.tabs.first()
    val heroItem = trending.items.firstOrNull { it.track != null }

    if (heroItem != null) {
        item(key = "hero") {
            HeroCard(
                item = heroItem,
                isPlaying = heroItem.track?.isrc == nowPlayingIsrc,
                onClick = { heroItem.track?.let { onTrackClick(it, trending.items.mapNotNull { i -> i.track }) } },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
    }

    feedSectionItems(state.feed.filter { it.slot == FeedSlot.TOP }, onTrackClick, onMoreClick, nowPlayingIsrc, onArtistItemClick, onAlbumClick)

    if (state.artists.isNotEmpty()) {
        item(key = "artists-title") { DiscoverSectionTitle("Artists to know") }
        item(key = "artists-row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(state.artists, key = { it.id }) { artist -> ArtistBubble(artist) { artist.artistId?.let(onArtistClick) } }
            }
        }
    }

    if (state.albums.isNotEmpty()) {
        item(key = "albums-title") { DiscoverSectionTitle("Albums on the rise") }
        item(key = "albums-row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.albums, key = { it.id }) { album -> AlbumCard(album) { onAlbumClick(album) } }
            }
        }
    }

    feedSectionItems(state.feed.filter { it.slot == FeedSlot.MIDDLE }, onTrackClick, onMoreClick, nowPlayingIsrc, onArtistItemClick, onAlbumClick)
}

internal fun LazyListScope.discoverFeedItems(
    state: DiscoverState,
    onTrackClick: (Track, List<Track>) -> Unit,
    onMoreClick: (Track) -> Unit,
    nowPlayingIsrc: String?,
    onArtistItemClick: (DiscoverItem) -> Unit,
    onAlbumClick: (DiscoverItem) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (state.tabs.isEmpty()) return
    val bottom = state.feed.filter { it.slot == FeedSlot.BOTTOM }
    feedSectionItems(bottom, onTrackClick, onMoreClick, nowPlayingIsrc, onArtistItemClick, onAlbumClick)
    if (state.feedEnabled) {
        item(key = "feed-footer") {
            FeedFooter(
                count = bottom.size,
                exhausted = state.feedExhausted,
                failed = state.feedFailed,
                onLoadMore = onLoadMore,
            )
        }
    }
}

internal fun LazyListScope.discoverChartItems(
    state: DiscoverState,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onMoreClick: (Track) -> Unit,
    nowPlayingIsrc: String?,
    onAlbumClick: (DiscoverItem) -> Unit,
    chartExpanded: Boolean = false,
    onToggleChartExpanded: () -> Unit = {},
) {
    if (state.tabs.isEmpty()) return
    val tabIndex = selectedTab.coerceIn(0, state.tabs.lastIndex)
    val tab = state.tabs[tabIndex]
    item(key = "charts-title") { DiscoverSectionTitle("Charts") }
    item(key = "charts-tabs") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.tabs, key = { _, t -> t.label }) { i, t -> Pill(t.label, i == tabIndex) { onSelectTab(i) } }
        }
    }
    item(key = "charts-caption") {
        Text(
            tab.title,
            style = MaterialTheme.typography.labelLarge,
            color = TextTertiary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp),
        )
    }

    val numbered = tab.label in NUMBERED_TABS
    val queue = tab.items.mapNotNull { it.track }
    val shown = if (chartExpanded) tab.items else tab.items.take(COLLAPSED_ROWS)
    itemsIndexed(shown, key = { i, it -> "chart-${tab.label}-$i-${it.id}" }) { index, item ->
        val track = item.track
        Box(Modifier.padding(horizontal = 16.dp)) {
            ChartRow(
                rank = if (numbered) index + 1 else null,
                item = item,
                isPlaying = track != null && track.isrc == nowPlayingIsrc,
                onClick = { if (track != null) onTrackClick(track, queue) else onAlbumClick(item) },
                onMore = track?.let { t -> { onMoreClick(t) } },
            )
        }
    }
    if (tab.items.size > COLLAPSED_ROWS) {
        item(key = "charts-more") {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                Pill(if (chartExpanded) "Show less" else "Show all ${tab.items.size}", false, onToggleChartExpanded)
            }
        }
    }
}

internal fun LazyListScope.discoverItems(
    state: DiscoverState,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onMoreClick: (Track) -> Unit,
    nowPlayingIsrc: String?,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (DiscoverItem) -> Unit,
    chartExpanded: Boolean = false,
    onToggleChartExpanded: () -> Unit = {},
    onRetry: () -> Unit = {},
    onArtistItemClick: (DiscoverItem) -> Unit = {},
    onLoadMore: () -> Unit = {},
) {
    discoverFeaturedItems(state, onTrackClick, nowPlayingIsrc, onArtistClick, onAlbumClick, onRetry, onMoreClick, onArtistItemClick)
    discoverChartItems(state, selectedTab, onSelectTab, onTrackClick, onMoreClick, nowPlayingIsrc, onAlbumClick, chartExpanded, onToggleChartExpanded)
    discoverFeedItems(state, onTrackClick, onMoreClick, nowPlayingIsrc, onArtistItemClick, onAlbumClick, onLoadMore)
}

@Composable
private fun DiscoverSectionTitle(text: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HeroCard(
    item: DiscoverItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "#1 Trending",
    opensAlbum: Boolean = false,
    aspect: Float = 1.35f,
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .shadow(18.dp, CardShapeLarge, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.35f))
            .pressScale(onClick = onClick, scaleDown = 0.98f)
            .clip(CardShapeLarge),
    ) {
        ArtImage(
            url = remember(item.imageUrl) { squareArtUrl(item.imageUrl, 600) },
            modifier = Modifier.fillMaxSize(),
            contentDescription = item.title,
            fallback = { ArtworkFallbackIcon() },
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f)),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 80.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(18.dp).size(44.dp), contentAlignment = Alignment.Center) {
            if (isPlaying) {
                NowPlayingBars(color = Color.White)
            } else if (opensAlbum) {
                Icon(PhosphorIcons.Bold.CaretRight, "Open album", tint = Color.White.copy(alpha = 0.95f), modifier = Modifier.size(28.dp))
            } else {
                Icon(PhosphorIcons.Fill.Play, "Play", tint = Color.White.copy(alpha = 0.95f), modifier = Modifier.size(34.dp))
            }
        }
    }
}

@Composable
private fun ArtistBubble(item: DiscoverItem, onClick: () -> Unit) {
    Column(
        Modifier.width(88.dp).pressScale(onClick = onClick, scaleDown = 0.94f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtImage(
            url = item.imageUrl,
            modifier = Modifier
                .size(88.dp)
                .shadow(8.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.3f))
                .clip(CircleShape),
            contentDescription = item.title,
            fallback = { ArtworkFallbackIcon() },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AlbumCard(item: DiscoverItem, isPlaying: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.width(148.dp).pressScale(onClick = onClick, scaleDown = 0.95f)) {
        ArtImage(
            url = remember(item.imageUrl) { squareArtUrl(item.imageUrl, 400) },
            modifier = Modifier
                .size(148.dp)
                .shadow(10.dp, RoundedCornerShape(14.dp), clip = false, ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.3f))
                .clip(RoundedCornerShape(14.dp)),
            contentDescription = item.title,
            fallback = { ArtworkFallbackIcon() },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium),
            color = if (isPlaying) MaterialTheme.colorScheme.primary else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            item.subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.meta?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChartRow(rank: Int?, item: DiscoverItem, isPlaying: Boolean, onClick: () -> Unit, onMore: (() -> Unit)?) {
    val accent = MaterialTheme.colorScheme.primary
    InteractiveSurface(
        shape = RowShape,
        pressedScale = 0.97f,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) { _ ->
        if (rank != null) {
            Column(Modifier.width(40.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (isPlaying) {
                    NowPlayingBars(color = accent)
                } else {
                    Text(
                        rank.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (rank <= 3) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (rank <= 3) TextPrimary else TextTertiary,
                    )
                }
                TrendMark(item.trend, item.trendDelta, accent)
            }
        }
        ArtImage(
            url = remember(item.imageUrl) { squareArtUrl(item.imageUrl, 256) },
            modifier = Modifier
                .size(52.dp)
                .shadow(6.dp, ThumbShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.20f), spotColor = Color.Black.copy(alpha = 0.30f))
                .clip(ThumbShape),
            fallback = { ArtworkFallbackIcon() },
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal),
                color = if (isPlaying) accent else ink(0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.meta?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onMore != null) {
            RowIconButton(PhosphorIcons.Bold.DotsThreeVertical, "More options", ink(0.6f), onMore)
        } else {
            Icon(PhosphorIcons.Bold.CaretRight, null, tint = ink(0.4f), modifier = Modifier.padding(end = 8.dp).size(18.dp))
        }
    }
}

@Composable
private fun TrendMark(trend: Trend?, delta: Int, accent: Color) {
    val small = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    when (trend) {
        null -> Unit
        Trend.NEW -> Text("NEW", style = small, color = accent)
        Trend.UP -> Text("\u25B2$delta", style = small, color = Color(0xFF3DDC84))
        Trend.DOWN -> Text("\u25BC$delta", style = small, color = Color(0xFFFF6B6B))
        Trend.SAME -> Text("\u2013", style = small, color = TextTertiary)
    }
}

private fun LazyListScope.feedSectionItems(
    sections: List<FeedSection>,
    onTrackClick: (Track, List<Track>) -> Unit,
    onMoreClick: (Track) -> Unit,
    nowPlayingIsrc: String?,
    onArtistItemClick: (DiscoverItem) -> Unit,
    onAlbumClick: (DiscoverItem) -> Unit,
) {
    sections.forEach { section ->
        if (section.items.isEmpty()) return@forEach
        val queue = section.items.mapNotNull { it.track }
        val play: (DiscoverItem) -> Unit = { item -> item.track?.let { onTrackClick(it, queue) } }
        val playing: (DiscoverItem) -> Boolean = { item -> nowPlayingIsrc != null && item.track?.isrc == nowPlayingIsrc }

        if (section.kind == FeedKind.SPOTLIGHT) {
            item(key = "feed-${section.id}") {
                val album = section.items.first()
                HeroCard(
                    item = album,
                    isPlaying = false,
                    onClick = { onAlbumClick(album) },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    label = section.title,
                    opensAlbum = true,
                    aspect = 1.7f,
                )
            }
            return@forEach
        }

        item(key = "feed-${section.id}-title") { DiscoverSectionTitle(section.title, section.subtitle) }
        item(key = "feed-${section.id}-body") {
            when (section.kind) {
                FeedKind.QUICK_PICKS -> QuickPicksGrid(section.items, playing, play)

                FeedKind.TRACK_RAILS -> TrackRails(section.items, playing, play, onMoreClick)

                FeedKind.TRACK_CAROUSEL -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(section.items, key = { it.id }) { song -> AlbumCard(song, isPlaying = playing(song)) { play(song) } }
                }

                FeedKind.ALBUM_CAROUSEL -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(section.items, key = { it.id }) { album -> AlbumCard(album) { onAlbumClick(album) } }
                }

                FeedKind.ARTIST_CAROUSEL -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(section.items, key = { it.id }) { artist -> ArtistBubble(artist) { onArtistItemClick(artist) } }
                }

                FeedKind.SPOTLIGHT -> Unit
            }
        }
    }
}

@Composable
private fun QuickPicksGrid(items: List<DiscoverItem>, isPlaying: (DiscoverItem) -> Boolean, onPlay: (DiscoverItem) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { item ->
                    QuickPickTile(item, isPlaying(item), { onPlay(item) }, Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickPickTile(item: DiscoverItem, isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .height(56.dp)
            .pressScale(onClick = onClick, scaleDown = 0.97f)
            .clip(RoundedCornerShape(12.dp))
            .background(ink(0.07f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(
            url = remember(item.imageUrl) { squareArtUrl(item.imageUrl, 200) },
            modifier = Modifier.size(56.dp),
            contentDescription = item.title,
            fallback = { ArtworkFallbackIcon() },
        )
        Text(
            item.title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium),
            color = if (isPlaying) accent else TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        if (isPlaying) NowPlayingBars(color = accent, modifier = Modifier.padding(end = 10.dp))
    }
}

@Composable
private fun TrackRails(
    items: List<DiscoverItem>,
    isPlaying: (DiscoverItem) -> Boolean,
    onPlay: (DiscoverItem) -> Unit,
    onMore: (Track) -> Unit,
) {
    val pages = remember(items) { items.chunked(3) }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(pages, key = { index, _ -> index }) { _, page ->
            Column(Modifier.fillParentMaxWidth(0.9f)) {
                page.forEach { item ->
                    val track = item.track
                    ChartRow(
                        rank = null,
                        item = item,
                        isPlaying = isPlaying(item),
                        onClick = { onPlay(item) },
                        onMore = track?.let { t -> { onMore(t) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedFooter(count: Int, exhausted: Boolean, failed: Boolean, onLoadMore: () -> Unit) {
    LaunchedEffect(count, exhausted, failed) { if (!exhausted && !failed) onLoadMore() }
    Box(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp).height(56.dp), contentAlignment = Alignment.Center) {
        when {
            failed -> Text(
                "Couldn\u2019t load more. Tap to retry",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier.pressScale(onClick = onLoadMore, scaleDown = 0.92f).padding(horizontal = 16.dp, vertical = 8.dp),
            )

            exhausted -> Text(
                "You\u2019re all caught up \u00B7 pull down for a fresh mix",
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            else -> CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        }
    }
}
