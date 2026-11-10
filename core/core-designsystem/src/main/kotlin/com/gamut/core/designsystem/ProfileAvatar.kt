package com.gamut.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun ProfileAvatar(
    name: String?,
    seed: String?,
    size: Dp,
    guestIcon: ImageVector,
    modifier: Modifier = Modifier,
    image: Any? = null,
    shape: Shape = CircleShape,
    invertedGuest: Boolean = false,
) {
    if (image != null) {
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape).background(ink(0.1f)),
        )
        return
    }
    if (seed == null && invertedGuest) {
        val disc = ink(0.9f)
        Box(
            modifier.size(size).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.clip(shape).background(disc),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                guestIcon,
                null,
                tint = Color.Black,
                modifier = Modifier.size(size * 0.55f).drawWithContent {
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(Rect(0f, 0f, this.size.width, this.size.height), Paint().apply { blendMode = BlendMode.DstOut })
                        drawContent()
                        canvas.restore()
                    }
                },
            )
        }
        return
    }
    if (seed == null) {
        Box(modifier.size(size).clip(shape).background(ink(0.1f)), contentAlignment = Alignment.Center) {
            Icon(guestIcon, null, tint = ink(0.85f), modifier = Modifier.size(size * 0.55f))
        }
        return
    }
    val color = remember(seed) { Color.hsv(Math.floorMod(seed.hashCode(), 360).toFloat(), 0.45f, 0.80f) }
    val initial = name?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    Box(modifier.size(size).clip(shape).background(color), contentAlignment = Alignment.Center) {
        Text(
            initial,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.42f).sp),
            color = Color(0xFF111111),
        )
    }
}
