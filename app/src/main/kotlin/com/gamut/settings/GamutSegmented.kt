package com.gamut.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale

@Composable
fun <T> GamutSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    val outer = RoundedCornerShape(16.dp)
    val inner = RoundedCornerShape(13.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(outer)
            .background(ink(0.06f))
            .border(1.dp, ink(0.12f), outer)
            .padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val fill by animateColorAsState(if (isSelected) ink(0.16f) else ink(0f), tween(180), label = "seg-fill")
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(inner)
                    .background(fill)
                    .pressScale(onClick = { if (!isSelected) onSelect(value) }, scaleDown = 0.97f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = ink(if (isSelected) 0.95f else 0.6f),
                )
            }
        }
    }
}
