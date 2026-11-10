package com.gamut.core.designsystem

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

val LocalAcrylicState = staticCompositionLocalOf<HazeState?> { null }

private var noHazeWarned = false

@Composable
fun AcrylicHost(
    modifier: Modifier = Modifier,
    state: HazeState = remember { HazeState() },
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalAcrylicState provides state) {
        Box(modifier, content = content)
    }
}

@Composable
fun Modifier.acrylicSource(state: HazeState? = LocalAcrylicState.current, zIndex: Float = 0f): Modifier = if (state == null) this else hazeSource(state, zIndex = zIndex)

enum class AcrylicTone { Dark, Light }

@Immutable
data class AcrylicMaterial(
    val blurRadius: Dp,
    val tintAlpha: Float,
    val noise: Float,
    val sheenAlpha: Float,
    val rimAlpha: Float,
    val fallbackAlpha: Float,
)

object AcrylicMaterials {
    val Scrim = AcrylicMaterial(22.dp, 0.28f, 0f, 0f, 0f, 0.40f)

    val UltraThin = AcrylicMaterial(14.dp, 0.10f, 0.015f, 0f, 0f, 0.40f)

    val Thin = AcrylicMaterial(16.dp, 0.14f, 0.025f, 0.10f, 0.40f, 0.55f)

    val Regular = AcrylicMaterial(28.dp, 0.24f, 0.03f, 0.12f, 0.50f, 0.70f)

    val Thick = AcrylicMaterial(40.dp, 0.38f, 0.035f, 0.14f, 0.55f, 0.82f)

    val Ultra = AcrylicMaterial(56.dp, 0.55f, 0.04f, 0.16f, 0.60f, 0.92f)
}

private fun AcrylicMaterial.adapted(s: AppearanceSettings): AcrylicMaterial = copy(
    blurRadius = blurRadius * s.glassDensity.blurScale,
    tintAlpha = (tintAlpha * s.glassDensity.tintScale).coerceAtMost(0.95f),
    fallbackAlpha = if (s.glass) (fallbackAlpha * s.glassDensity.tintScale).coerceAtMost(0.97f) else maxOf(fallbackAlpha, 0.88f),
)

private val DarkBase = Color(0xFF0B0D12)
private val LightBase = Color(0xFFF4F6FA)

private fun AcrylicTone.base(): Color = if (this == AcrylicTone.Dark) DarkBase else LightBase

@Composable
private fun rememberGlassBase(): Color {
    val target = LocalCoverPalette.current.base
    val animated by animateColorAsState(target, tween(1400), label = "acrylic-base")
    return animated
}

@Composable
fun AcrylicSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    material: AcrylicMaterial = AcrylicMaterials.Regular,
    tone: AcrylicTone = AcrylicTone.Dark,
    hazeState: HazeState? = LocalAcrylicState.current,
    content: @Composable BoxScope.() -> Unit,
) {
    val appearance = LocalAppearance.current
    val material = material.adapted(appearance)
    val hazeState = if (appearance.glass) hazeState else null
    if (hazeState == null && appearance.glass) {
        SideEffect {
            if (!noHazeWarned) {
                noHazeWarned = true
                Log.w("Acrylic", "No HazeState found. Wrap the screen in AcrylicHost and put Modifier.acrylicSource() on the layer behind the glass.")
            }
        }
    }
    val base = rememberGlassBase()
    val style = remember(material, base) { material.hazeStyle(base) }
    val highlight = Color.White
    val sheen = remember(material, tone) {
        Brush.linearGradient(
            0.00f to highlight.copy(alpha = material.sheenAlpha),
            0.45f to highlight.copy(alpha = material.sheenAlpha * 0.15f),
            1.00f to Color.Transparent,
        )
    }
    val rim = remember(material, tone) {
        val a = material.rimAlpha
        val rimColor = Color.White
        Brush.linearGradient(
            0.0f to rimColor.copy(alpha = a),
            0.5f to rimColor.copy(alpha = a * 0.10f),
            1.0f to rimColor.copy(alpha = a * 0.40f),
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState, style = style)
                } else {
                    Modifier.background(base.copy(alpha = material.fallbackAlpha))
                },
            )
            .then(if (material.sheenAlpha > 0f) Modifier.background(sheen) else Modifier)
            .then(if (material.rimAlpha > 0f) Modifier.border(0.75.dp, rim, shape) else Modifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun AcrylicCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    material: AcrylicMaterial = AcrylicMaterials.UltraThin,
    tone: AcrylicTone = AcrylicTone.Dark,
    content: @Composable BoxScope.() -> Unit,
) {
    AcrylicSurface(
        modifier = modifier
            .pressScale(onClick = onClick)
            .size(size),
        shape = CircleShape,
        material = material,
        tone = tone,
        content = content,
    )
}

@Composable
fun AcrylicPill(
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    material: AcrylicMaterial = AcrylicMaterials.Regular,
    tone: AcrylicTone = AcrylicTone.Dark,
    content: @Composable RowScope.() -> Unit,
) {
    AcrylicSurface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        material = material,
        tone = tone,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun AcrylicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    material: AcrylicMaterial = AcrylicMaterials.Thick,
    tone: AcrylicTone = AcrylicTone.Dark,
    content: @Composable BoxScope.() -> Unit,
) {
    AcrylicSurface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        material = material,
        tone = tone,
        content = content,
    )
}

private fun AcrylicMaterial.hazeStyle(base: Color) = HazeStyle(
    backgroundColor = base,
    tints = listOf(HazeTint(base.copy(alpha = tintAlpha))),
    blurRadius = blurRadius,
    noiseFactor = noise,
    fallbackTint = HazeTint(base.copy(alpha = fallbackAlpha)),
)

@Composable
fun Modifier.acrylicFill(
    material: AcrylicMaterial = AcrylicMaterials.Regular,
    tone: AcrylicTone = AcrylicTone.Dark,
    state: HazeState? = LocalAcrylicState.current,
    intensity: () -> Float = { 1f },
): Modifier {
    val appearance = LocalAppearance.current
    val material = material.adapted(appearance)
    val base = rememberGlassBase()
    val style = remember(material, base) { material.hazeStyle(base) }
    return if (state != null && appearance.glass) {
        this.hazeEffect(state = state, style = style) { alpha = intensity() }
    } else {
        this.background(base.copy(alpha = material.fallbackAlpha))
    }
}

enum class AcrylicEdge { Top, Bottom }

@Composable
fun AcrylicScrim(
    modifier: Modifier = Modifier,
    edge: AcrylicEdge = AcrylicEdge.Top,
    material: AcrylicMaterial = AcrylicMaterials.Scrim,
    tone: AcrylicTone = AcrylicTone.Dark,
    state: HazeState? = LocalAcrylicState.current,
) {
    val appearance = LocalAppearance.current
    val material = material.adapted(appearance)
    val state = if (appearance.glass) state else null
    val base = rememberGlassBase()
    val style = remember(material, base) { material.hazeStyle(base) }
    val top = edge == AcrylicEdge.Top
    val fade = remember(edge) {
        Brush.verticalGradient(if (top) listOf(Color.Black, Color.Transparent) else listOf(Color.Transparent, Color.Black))
    }
    Box(
        if (state != null) {
            modifier.hazeEffect(state = state, style = style) {
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = if (top) 1f else 0f,
                    endIntensity = if (top) 0f else 1f,
                )
                mask = fade
            }
        } else {
            modifier.background(
                Brush.verticalGradient(
                    if (top) {
                        listOf(base.copy(alpha = material.fallbackAlpha), Color.Transparent)
                    } else {
                        listOf(Color.Transparent, base.copy(alpha = material.fallbackAlpha))
                    },
                ),
            )
        },
    )
}
