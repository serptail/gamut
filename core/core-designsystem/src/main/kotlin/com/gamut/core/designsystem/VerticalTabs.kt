package com.gamut.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun VerticalTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 30.dp,
) {
    Column(
        modifier.width(38.dp),
        verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            val color by animateColorAsState(if (active) ink() else ink(0.35f), tween(220), label = "tab-color")
            val dot by animateColorAsState(if (active) ink() else Color.Transparent, tween(220), label = "tab-dot")
            Row(
                Modifier.pressScale(onClick = { onSelect(index) }, scaleDown = 0.94f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(5.dp).background(dot, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.verticalText(),
                )
            }
        }
    }
}

private fun Modifier.verticalText(): Modifier = this
    .layout { measurable, _ ->
        val p = measurable.measure(Constraints())
        layout(p.height, p.width) { p.place((p.height - p.width) / 2, (p.width - p.height) / 2) }
    }
    .rotate(-90f)
