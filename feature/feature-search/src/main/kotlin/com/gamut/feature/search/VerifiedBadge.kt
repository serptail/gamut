package com.gamut.feature.search

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.SealCheck

internal const val VERIFIED_MIN_FOLLOWERS = 20_000L

internal fun isVerifiedArtist(followers: Long?): Boolean = followers != null && followers >= VERIFIED_MIN_FOLLOWERS

@Composable
internal fun VerifiedBadge(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Icon(
        PhosphorIcons.Fill.SealCheck,
        contentDescription = "Verified artist",
        tint = tint,
        modifier = modifier.size(size),
    )
}
