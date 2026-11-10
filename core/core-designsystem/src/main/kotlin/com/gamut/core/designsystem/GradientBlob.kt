package com.gamut.core.designsystem

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

private const val MagentaAtBottom = false

private const val BlobFocalDistance = 0.97f

private const val BlobFalloff = 0.55f

private val BlobColors = listOf(
    Color(0xFFEE22FF),
    Color(0xFFA02CFF),
    Color(0xFF3340FF),
    Color(0xFF0A4CF0),
    Color(0xFF00A8FF),
    Color(0xFF00DCE8),
    Color(0xFF1CF2C0),
)
private val BlobOffsets = floatArrayOf(0f, 0.2f, 0.4f, 0.55f, 0.72f, 0.86f, 1f)

private const val BlobAgsl = """
uniform float2 resolution;
uniform float3 c0;
uniform float3 c1;
uniform float3 c2;
uniform float3 c3;
uniform float3 c4;
uniform float3 c5;
uniform float3 c6;
uniform float focalDistance;
uniform float falloff;
uniform float intensity;
uniform float reverse;

float3 ramp(float u) {
    float3 c = c0;
    c = mix(c, c1, clamp((u - 0.00) / 0.20, 0.0, 1.0));
    c = mix(c, c2, clamp((u - 0.20) / 0.20, 0.0, 1.0));
    c = mix(c, c3, clamp((u - 0.40) / 0.15, 0.0, 1.0));
    c = mix(c, c4, clamp((u - 0.55) / 0.17, 0.0, 1.0));
    c = mix(c, c5, clamp((u - 0.72) / 0.14, 0.0, 1.0));
    c = mix(c, c6, clamp((u - 0.86) / 0.14, 0.0, 1.0));
    return c;
}

half4 main(float2 fc) {
    float2 p = (fc - resolution * 0.5) / (resolution * 0.5);
    float2 F = float2(0.0, -focalDistance);
    float2 dv = p - F;
    float len = length(dv);
    float2 d = dv / max(len, 0.0001);
    float b = dot(F, d);
    float s = -b + sqrt(max(b * b - (dot(F, F) - 1.0), 0.0));
    float t = len / max(s, 0.0001);
    float u = clamp(reverse > 0.5 ? t : 1.0 - t, 0.0, 1.0);
    float3 col = ramp(u);
    float alpha = 1.0 - smoothstep(1.0 - falloff, 1.0, t);
    float n = fract(sin(dot(fc, float2(12.9898, 78.233))) * 43758.5453);
    col += (n - 0.5) / 128.0;
    float a = alpha * intensity;
    return half4(half3(col * a), half(a));
}
"""

@Composable
fun GradientBlob(modifier: Modifier = Modifier, intensity: Float = 1f) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ShaderBlob(modifier, intensity)
    } else {
        GradientBlobFallback(modifier, intensity)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderBlob(modifier: Modifier, intensity: Float) {
    val shader = remember { RuntimeShader(BlobAgsl) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier) {
        shader.setFloatUniform("resolution", size.width, size.height)
        BlobColors.forEachIndexed { i, c -> shader.setFloatUniform("c$i", c.red, c.green, c.blue) }
        shader.setFloatUniform("focalDistance", BlobFocalDistance)
        shader.setFloatUniform("falloff", BlobFalloff)
        shader.setFloatUniform("intensity", intensity)
        shader.setFloatUniform("reverse", if (MagentaAtBottom) 1f else 0f)
        drawRect(brush)
    }
}

@Composable
private fun GradientBlobFallback(modifier: Modifier, intensity: Float) {
    Canvas(modifier) {
        val stops = BlobColors.indices
            .map { i -> (if (MagentaAtBottom) BlobOffsets[i] else 1f - BlobOffsets[i]) to BlobColors[i] }
            .sortedBy { it.first }
            .let { it.dropLast(1) + (it.last().first to it.last().second.copy(alpha = 0f)) }
        drawRect(
            brush = Brush.radialGradient(
                colorStops = stops.toTypedArray(),
                center = Offset(size.width / 2f, 0f),
                radius = size.height * 0.98f,
            ),
            alpha = intensity,
        )
    }
}
