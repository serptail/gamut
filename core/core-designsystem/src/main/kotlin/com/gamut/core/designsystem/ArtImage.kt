package com.gamut.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.MusicNotes

@Composable
fun ArtImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    @Suppress("UNUSED_PARAMETER") fallback: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context).data(url?.takeIf { it.isNotBlank() }).crossfade(false).build()
    }
    var failed by remember(url) { mutableStateOf(false) }
    val tile = artTileColor()
    Box(modifier.background(tile), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onError = { failed = true },
        )
        if (failed) ArtPlaceholder(tile = tile)
    }
}

@Composable
fun artTileColor(): Color = ink(0.08f).compositeOver(if (ink().luminance() > 0.5f) Color(0xFF0E0E10) else Color(0xFFF4F4F6))

@Composable
fun ArtPlaceholder(
    modifier: Modifier = Modifier.fillMaxSize(),
    iconFraction: Float = 0.42f,
    tile: Color = artTileColor(),
    tint: Color = ink(0.45f),
    iconAlignment: Alignment = Alignment.Center,
) {
    Box(modifier.background(tile), contentAlignment = iconAlignment) {
        Icon(PhosphorIcons.Bold.MusicNotes, contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize(iconFraction))
    }
}
