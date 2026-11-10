package com.gamut.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val GamutBottomBarTabsHeight = 54.dp

data class BottomBarItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

@Composable
fun GamutBottomBar(
    items: List<BottomBarItem>,
    modifier: Modifier = Modifier,
    nowPlaying: @Composable () -> Unit = {},
) {
    val hairline = ink(0.08f)
    Column(modifier.fillMaxWidth()) {
        nowPlaying()
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(GamutBottomBarTabsHeight)
                    .drawBehind {
                        val y = 0.5.dp.toPx()
                        drawLine(hairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(Modifier.widthIn(max = AdaptiveLayout.BottomBarTabsMaxWidth).fillMaxWidth().fillMaxHeight()) {
                    items.forEach { item -> BottomBarTab(item, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun BottomBarTab(item: BottomBarItem, modifier: Modifier) {
    val tint by animateColorAsState(if (item.selected) ink() else ink(0.45f), tween(200), label = "bar-tint")
    val marker by animateFloatAsState(if (item.selected) 1f else 0f, tween(200), label = "bar-marker")
    Box(
        modifier
            .fillMaxHeight()
            .then(if (item.onClick != null) Modifier.pressScale(onClick = item.onClick, scaleDown = 0.92f) else Modifier),
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(width = 22.dp, height = 2.dp)
                .background(ink(marker), RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)),
        )
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(item.icon, null, tint = tint, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(2.dp))
            Text(item.label, color = tint, style = MaterialTheme.typography.labelMedium, fontSize = 10.5.sp)
        }
    }
}
