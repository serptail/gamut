package com.gamut.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.MusicNote
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale

object EmptyStateCovers {
    var urls by mutableStateOf<List<String>>(emptyList())
        private set

    fun offer(artUrls: List<String?>) {
        if (urls.size >= 5) return
        urls = (urls + artUrls.filterNotNull().filter { it.isNotBlank() }).distinct().take(5)
    }
}

object EmptyStateArtistCovers {
    var urls by mutableStateOf<List<String>>(emptyList())
        private set

    fun offer(photoUrls: List<String?>) {
        if (urls.size >= 5) return
        urls = (urls + photoUrls.filterNotNull().filter { it.isNotBlank() }).distinct().take(5)
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    buttonLabel: String? = null,
    onButton: () -> Unit = {},
    covers: List<String> = EmptyStateCovers.urls,
    circleCovers: Boolean = false,
) {
    val palette = LocalCoverPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ink(0.07f))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverSpread(covers, circleCovers)
        Spacer(Modifier.height(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = ink(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ink(0.6f), textAlign = TextAlign.Center)
        if (buttonLabel != null) {
            Spacer(Modifier.height(24.dp))
            Text(
                buttonLabel,
                style = MaterialTheme.typography.titleSmall,
                color = if (palette.isLight) Color.White else Color.Black,
                modifier = Modifier
                    .pressScale(onClick = onButton)
                    .background(ink(0.95f), CircleShape)
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun CoverSpread(urls: List<String>, circle: Boolean = false) {
    val slots = listOf(
        Triple(-96, 62, 0.55f),
        Triple(-48, 78, 0.8f),
        Triple(0, 96, 1f),
        Triple(48, 78, 0.8f),
        Triple(96, 62, 0.55f),
    )
    val fillOrder = listOf(2, 1, 3, 0, 4)
    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
        listOf(0, 4, 1, 3, 2).forEach { i ->
            val (dx, size, alpha) = slots[i]
            val url = urls.getOrNull(fillOrder.indexOf(i))
            Box(
                Modifier
                    .offset(x = dx.dp)
                    .size(size.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .clip(if (circle) CircleShape else RoundedCornerShape(8.dp))
                    .background(ink(0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (url != null) {
                    CoverImage(remember(url) { resolveCoverUrl(url, 200) }, Modifier.fillMaxSize())
                } else {
                    Icon(PhosphorIcons.Bold.MusicNote, null, tint = ink(0.35f), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
