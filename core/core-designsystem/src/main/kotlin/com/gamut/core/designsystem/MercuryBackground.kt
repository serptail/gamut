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

enum class FlowStyle { Flow, Mercury, Metaballs, Dithering }

private const val MercuryDistortion = 0.8f

private const val MercurySwirl = 0.35f

private const val MercurySpeed = 0.6f

private const val MercuryBaseWeight = 0.9f

private const val MercuryAgsl = """
uniform float2 resolution;
uniform float time;
uniform float3 c1;
uniform float3 c2;
uniform float3 c3;
uniform float3 base;
uniform float strength;
uniform float distortion;
uniform float swirl;
uniform float baseWeight;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float2 getPosition(float i, float t) {
    float a = i * 0.37;
    float b = 0.6 + fract(i / 3.0) * 0.9;
    float c = 0.8 + fract((i + 1.0) / 4.0);
    float x = sin(t * b + a);
    float y = cos(t * c + a * 1.5);
    return 0.5 + 0.5 * float2(x, y);
}

void accumulate(inout float3 color, inout float totalWeight, float3 col, float i, float2 uv, float t, float mul) {
    float dist = length(uv - getPosition(i, t));
    dist = pow(max(dist, 0.0001), 3.5);
    float weight = mul / (dist + 0.001);
    color += col * weight;
    totalWeight += weight;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float t = 0.5 * (time + 41.5);
    float radius = smoothstep(0.0, 1.0, length(uv - 0.5));
    float center = 1.0 - radius;
    for (int k = 1; k <= 2; k++) {
        float i = float(k);
        uv.x += distortion * center / i * sin(t + i * 0.4 * smoothstep(0.0, 1.0, uv.y))
                * cos(0.2 * t + i * 2.4 * smoothstep(0.0, 1.0, uv.y));
        uv.y += distortion * center / i * cos(t + i * 2.0 * smoothstep(0.0, 1.0, uv.x));
    }

    float2 rotated = uv - 0.5;
    float angle = -3.0 * swirl * radius;
    float ca = cos(angle);
    float sa = sin(angle);
    rotated = float2(rotated.x * ca - rotated.y * sa, rotated.x * sa + rotated.y * ca) + 0.5;
    float3 color = float3(0.0);
    float totalWeight = 0.0;
    accumulate(color, totalWeight, c1, 0.0, rotated, t, 1.0);
    accumulate(color, totalWeight, c2, 1.0, rotated, t, 1.0);
    accumulate(color, totalWeight, c3, 2.0, rotated, t, 1.0);
    accumulate(color, totalWeight, base, 3.0, rotated, t, baseWeight);
    accumulate(color, totalWeight, base, 4.0, rotated, t, baseWeight);
    color /= max(totalWeight, 0.0001);
    float3 outCol = mix(base, color, clamp(strength, 0.0, 1.0));
    outCol += (hash(fragCoord) - 0.5) * 0.004;
    return half4(half3(clamp(outCol, 0.0, 1.0)), 1.0);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun MercuryBackground(
    base: Color,
    c1: Color,
    c2: Color,
    c3: Color,
    strength: Float,
    isLight: Boolean,
    clock: FloatState,
    modifier: Modifier,
) {
    val shader = remember { RuntimeShader(MercuryAgsl) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier.fillMaxSize()) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", clock.floatValue * MercurySpeed)
        shader.setFloatUniform("c1", c1.red, c1.green, c1.blue)
        shader.setFloatUniform("c2", c2.red, c2.green, c2.blue)
        shader.setFloatUniform("c3", c3.red, c3.green, c3.blue)
        shader.setFloatUniform("base", base.red, base.green, base.blue)
        shader.setFloatUniform("strength", strength)
        shader.setFloatUniform("distortion", MercuryDistortion)
        shader.setFloatUniform("swirl", MercurySwirl)
        shader.setFloatUniform("baseWeight", MercuryBaseWeight)
        drawRect(brush)
    }
}
