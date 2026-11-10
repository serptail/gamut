package com.gamut.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager

@Composable
fun Modifier.focusLayer(live: Boolean): Modifier {
    val focusManager = LocalFocusManager.current
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(live) { if (!live && hasFocus) focusManager.clearFocus(force = true) }
    return this
        .onFocusChanged { hasFocus = it.hasFocus }
        .focusProperties { if (!live) canFocus = false }
}

val LocalFocusLive = compositionLocalOf { true }

@Composable
fun FocusLive(live: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFocusLive provides (LocalFocusLive.current && live), content = content)
}
