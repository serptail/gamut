package com.gamut.core.designsystem

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

private const val MetaballsCount = 9f

private const val MetaballsSize = 1.0f

private const val MetaballsSpeed = 0.6f

private const val MetaballsEdge = 0.003f

private const val MetaballsAgsl = """
uniform float2 resolution;
uniform float time;
uniform float3 c1;
uniform float3 c2;
uniform float3 c3;
uniform float3 base;
uniform float strength;
uniform float count;
uniform float size;
uniform float edgeSoft;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float hash11(float n) { return fract(sin(n * 127.1) * 43758.5453); }

half4 main(float2 fragCoord) {
    float aspect = resolution.x / resolution.y;
    float2 p = fragCoord / resolution.y;
    float t = time * 0.5;

    float field = 0.0;
    float3 acc = float3(0.0);
    for (int i = 0; i < 10; i++) {
        float fi = float(i);
        if (fi >= count) break;
        float h1 = hash11(fi + 1.0);
        float h2 = hash11(fi + 7.3);
        float h3 = hash11(fi + 13.7);
        float h4 = hash11(fi + 21.1);
        float2 pos = float2(
            aspect * (0.5 + 0.45 * sin(t * (0.25 + 0.50 * h1) + h2 * 6.2832)),
            0.5 + 0.45 * cos(t * (0.20 + 0.45 * h3) + h4 * 6.2832)
        );
        float r = size * (0.07 + 0.07 * h2);
        float falloff = r * 3.2;
        float f = pow(clamp(1.0 - length(p - pos) / falloff, 0.0, 1.0), 3.0);
        float m = mod(fi, 3.0);
        float3 col = m < 0.5 ? c1 : (m < 1.5 ? c2 : c3);
        field += f;
        acc += col * f;
    }

    float s = clamp(strength, 0.0, 1.0);
    float3 ballCol = acc / max(field, 0.0001);
    float shape = smoothstep(0.35 - edgeSoft, 0.35 + edgeSoft, field);
    float3 outCol = mix(base, ballCol, shape * s);
    outCol += (hash(fragCoord) - 0.5) * 0.004;
    return half4(half3(clamp(outCol, 0.0, 1.0)), 1.0);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun MetaballsBackground(
    base: Color,
    c1: Color,
    c2: Color,
    c3: Color,
    strength: Float,
    isLight: Boolean,
    clock: FloatState,
    modifier: Modifier,
) {
    val shader = remember { RuntimeShader(MetaballsAgsl) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier.fillMaxSize()) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", clock.floatValue * MetaballsSpeed)
        shader.setFloatUniform("c1", c1.red, c1.green, c1.blue)
        shader.setFloatUniform("c2", c2.red, c2.green, c2.blue)
        shader.setFloatUniform("c3", c3.red, c3.green, c3.blue)
        shader.setFloatUniform("base", base.red, base.green, base.blue)
        shader.setFloatUniform("strength", strength)
        shader.setFloatUniform("count", MetaballsCount)
        shader.setFloatUniform("size", MetaballsSize)
        shader.setFloatUniform("edgeSoft", MetaballsEdge)
        drawRect(brush)
    }
}
