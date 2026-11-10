package com.gamut.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun NowPlayingBars(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "now-playing")
    val periods = listOf(420, 560, 480)
    val levels = periods.mapIndexed { i, ms ->
        t.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Reverse, initialStartOffset = StartOffset(i * 90)),
            label = "bar-$i",
        )
    }
    Row(
        modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        levels.forEach { level ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(level.value)
                    .background(color, RoundedCornerShape(1.5.dp)),
            )
        }
    }
}
