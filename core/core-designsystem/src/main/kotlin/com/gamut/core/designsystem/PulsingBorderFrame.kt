package com.gamut.core.designsystem

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI

val GoogleBorderColors = listOf(
    Color(0xFF4285F4),
    Color(0xFFEA4335),
    Color(0xFFFBBC05),
    Color(0xFF34A853),
)

private const val PulseLoopMs = 12_000

private const val PulsingBorderAgsl = """
uniform float2 size;
uniform float radius;
uniform float thickness;
uniform float glow;
uniform float time;
uniform float level;
uniform float3 g0;
uniform float3 g1;
uniform float3 g2;
uniform float3 g3;

float sdRoundBox(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float hat(float x, float k) {
    float d = abs(mod(x - k + 2.0, 4.0) - 2.0);
    float w = clamp(1.0 - d, 0.0, 1.0);
    return w * w * (3.0 - 2.0 * w);
}

half4 main(float2 fc) {
    float2 p = fc - size * 0.5;
    float d = sdRoundBox(p, size * 0.5, radius);
    float ang = atan(p.y, p.x);
    float x = fract(ang / 6.2831853 + 0.5 + time / 6.2831853) * 4.0;
    float3 col = g0 * hat(x, 0.0) + g1 * hat(x, 1.0) + g2 * hat(x, 2.0) + g3 * hat(x, 3.0);
    float spots = 0.5 + 0.5 * sin(ang * 2.0 - time * 2.0);
    float breathe = 0.85 + 0.15 * sin(time * 3.0);
    float ring = smoothstep(-1.0, 0.5, d) * (1.0 - smoothstep(thickness - 0.5, thickness + 0.5, d));
    float m = mix(0.55, 1.0, spots) * breathe * level;
    float dOut = max(d - thickness, 0.0);
    float halo = exp(-dOut / (glow * 0.3)) * (1.0 - smoothstep(glow * 0.7, glow, dOut));
    halo *= smoothstep(-1.0, 0.5, d) * (0.25 + 0.75 * spots) * breathe * level * 0.6;
    float a = clamp(ring * m + halo, 0.0, 1.0);
    return half4(half3(col * a), half(a));
}
"""

@Composable
fun PulsingBorderFrame(
    modifier: Modifier = Modifier,
    colors: List<Color> = GoogleBorderColors,
    cornerRadius: Dp = 28.dp,
    thickness: Dp = 2.dp,
    glow: Dp = 14.dp,
    active: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ShaderBorderFrame(modifier, colors, cornerRadius, thickness, glow, active, content)
    } else {
        val shape = RoundedCornerShape(cornerRadius)
        val sweep = Brush.sweepGradient(colors + colors.first())
        Box(modifier.border(thickness, sweep, shape)) { content() }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderBorderFrame(
    modifier: Modifier,
    colors: List<Color>,
    cornerRadius: Dp,
    thickness: Dp,
    glow: Dp,
    active: Boolean,
    content: @Composable () -> Unit,
) {
    val shader = remember { RuntimeShader(PulsingBorderAgsl) }
    val brush = remember(shader) { ShaderBrush(shader) }

    val clock = rememberInfiniteTransition(label = "pulsing-border").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(PulseLoopMs, easing = LinearEasing), RepeatMode.Restart),
        label = "pulsing-border-clock",
    )
    val level by animateFloatAsState(if (active) 1f else 0.25f, tween(300), label = "pulsing-border-level")

    Box(
        modifier.drawBehind {
            val g = glow.toPx()
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("radius", cornerRadius.toPx())
            shader.setFloatUniform("thickness", thickness.toPx())
            shader.setFloatUniform("glow", g)
            shader.setFloatUniform("time", clock.value)
            shader.setFloatUniform("level", level)
            for (i in 0 until 4) {
                val c = colors[i % colors.size]
                shader.setFloatUniform("g$i", c.red, c.green, c.blue)
            }
            drawRect(
                brush,
                topLeft = Offset(-g, -g),
                size = Size(size.width + 2 * g, size.height + 2 * g),
            )
        },
    ) { content() }
}
