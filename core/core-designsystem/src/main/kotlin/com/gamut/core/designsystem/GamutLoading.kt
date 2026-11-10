package com.gamut.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object GamutBoot {
    @Volatile var finished: Boolean = false
}

@Composable
fun GamutLogo(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Image(painterResource(R.drawable.gamut_logo), contentDescription = "Gamut", modifier = modifier.size(size))
}

@Composable
fun GamutLoadingScreen(
    dataReady: Boolean,
    onFinished: () -> Unit,
    minDurationMs: Int = 3000,
) {
    val palette = appNeutralPalette()

    val progress = remember { Animatable(0f) }
    var minTimePassed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(minDurationMs.toLong())
        minTimePassed = true
    }
    LaunchedEffect(Unit) { progress.animateTo(0.9f, tween(minDurationMs, easing = FastOutSlowInEasing)) }
    LaunchedEffect(dataReady, minTimePassed) {
        if (dataReady && minTimePassed) {
            progress.animateTo(1f, tween(250))
            delay(150)
            GamutBoot.finished = true
            onFinished()
        }
    }

    val logoScale = remember { Animatable(0.9f) }
    val logoAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(450)) }
        launch { logoScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) }
    }

    Box(
        Modifier.fillMaxSize().background(palette.base).pointerInput(Unit) {},
        contentAlignment = Alignment.Center,
    ) {
        val outline = if (palette.isLight) R.drawable.ic_gamut_mark_black_outline else R.drawable.ic_gamut_mark_white_outline
        val filled = if (palette.isLight) R.drawable.ic_gamut_mark_black else R.drawable.ic_gamut_mark_white
        Box(
            Modifier
                .width(100.dp)
                .aspectRatio(475.8f / 227.06f)
                .graphicsLayer {
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                    alpha = logoAlpha.value
                },
        ) {
            Image(painterResource(outline), contentDescription = "Gamut", modifier = Modifier.fillMaxSize())
            Image(
                painterResource(filled),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().drawWithContent {
                    clipRect(right = size.width * progress.value) { this@drawWithContent.drawContent() }
                },
            )
        }
    }
}
