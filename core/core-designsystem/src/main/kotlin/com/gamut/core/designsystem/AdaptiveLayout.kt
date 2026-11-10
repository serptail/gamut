package com.gamut.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AdaptiveLayout {
    val ContentMaxWidth: Dp = 720.dp
    val PlayerMaxWidth: Dp = 560.dp

    val WideBreakpoint: Dp = 720.dp

    val BottomBarTabsMaxWidth: Dp = 480.dp

    val SheetContentMaxWidth: Dp = 720.dp
}

@Composable
fun adaptiveGutter(maxWidth: Dp = AdaptiveLayout.ContentMaxWidth): Dp {
    val width = LocalConfiguration.current.screenWidthDp.dp
    return ((width - maxWidth) / 2).coerceAtLeast(0.dp)
}

@Composable
fun isWideLayout(): Boolean = LocalConfiguration.current.screenWidthDp.dp >= AdaptiveLayout.WideBreakpoint
