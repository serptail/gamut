package com.gamut.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.Heart
import com.adamglin.phosphoricons.bold.TipJar
import com.adamglin.phosphoricons.fill.Heart as HeartFill
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.LocalNotifier
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale

internal val KoFiCoral = Color(0xFFFF5E5B)

internal val KoFiPink = Color(0xFFFF8FA3)

internal val KoFiCup = Color(0xFFFF7A9B)

fun openKoFi(context: Context, dark: Boolean, toolbarColor: Int, onCopied: () -> Unit) {
    val uri = Uri.parse(SupportConfig.KOFI_URL)

    val tabOpened = runCatching {
        val tab = CustomTabsIntent.Builder()
            .setColorScheme(if (dark) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build())
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setInitialActivityHeightPx((context.resources.displayMetrics.heightPixels * 0.9f).toInt(), CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE)
            .build()
        if (context !is Activity) tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tab.launchUrl(context, uri)
    }.isSuccess
    if (tabOpened) return

    val browserOpened = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (browserOpened) return

    runCatching {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Ko-fi", SupportConfig.KOFI_URL))
    }
    onCopied()
}

@Composable
fun rememberOpenKoFi(): () -> Unit {
    val context = LocalContext.current
    val notifier = LocalNotifier.current
    val dark = !LocalCoverPalette.current.isLight
    val toolbar = MaterialTheme.colorScheme.surface.toArgb()
    return remember(context, notifier, dark, toolbar) {
        { openKoFi(context, dark, toolbar) { notifier.info("No browser found. Link copied instead.") } }
    }
}

@Composable
fun SupportHeartButton(onClick: () -> Unit) {
    val vm: SupportViewModel = hiltViewModel()
    val filled by vm.store.donateOpen.collectAsState()
    val inverse = if (LocalCoverPalette.current.isLight) Color.White else Color.Black
    Box(
        Modifier.size(44.dp).pressScale(onClick = onClick).semantics { contentDescription = "Support Gamut" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(ink(0.9f)), contentAlignment = Alignment.Center) {
            Crossfade(targetState = filled, animationSpec = tween(220), label = "heart") { on ->
                if (on) {
                    Icon(PhosphorIcons.Fill.HeartFill, null, tint = KoFiPink, modifier = Modifier.size(22.dp))
                } else {
                    Icon(PhosphorIcons.Bold.Heart, null, tint = inverse, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
    Spacer(Modifier.width(8.dp))
}

@Composable
fun SupportRow() {
    val open = rememberOpenKoFi()
    val vm: SupportViewModel = hiltViewModel()
    Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .pressScale(onClick = {
                    vm.store.clearNudge()
                    open()
                }, scaleDown = 0.95f)
                .clip(CircleShape)
                .background(ink(0.05f))
                .flowingBorder()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PhosphorIcons.Bold.TipJar, null, tint = KoFiPink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Support Gamut", style = MaterialTheme.typography.labelLarge, color = ink(0.85f), maxLines = 1)
        }
    }
}

@Composable
private fun Modifier.flowingBorder(width: Dp = 1.5.dp): Modifier {
    val live = LocalPageActive.current
    val angle: State<Float> = if (live) {
        rememberInfiniteTransition(label = "flow").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing)),
            label = "angle",
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    val colors = remember { listOf(KoFiCoral, KoFiPink, Color(0xFFFFB38A), KoFiPink, KoFiCoral) }
    return drawWithContent {
        drawContent()
        val deg = angle.value
        val brush = object : ShaderBrush() {
            override fun createShader(size: Size): android.graphics.Shader {
                val shader = SweepGradientShader(Offset(size.width / 2f, size.height / 2f), colors)
                val m = android.graphics.Matrix()
                m.postRotate(deg, size.width / 2f, size.height / 2f)
                shader.setLocalMatrix(m)
                return shader
            }
        }
        val w = width.toPx()
        drawRoundRect(
            brush = brush,
            topLeft = Offset(w / 2f, w / 2f),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(size.height / 2f),
            style = Stroke(width = w),
        )
    }
}

val LocalSupportNudge = compositionLocalOf { false }

@Composable
fun Modifier.supportNudgeCorona(avatarSize: Dp = 34.dp): Modifier {
    val active = LocalSupportNudge.current
    val appear = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "support-corona",
    )
    val breathe: State<Float> = if (active) {
        rememberInfiniteTransition(label = "corona").animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breathe",
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    return drawWithContent {
        val a = appear.value
        if (a <= 0.01f) {
            drawContent()
            return@drawWithContent
        }
        val b = breathe.value
        val c = center
        val r = avatarSize.toPx() / 2f
        val outer = r + 10.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                0f to KoFiCoral.copy(alpha = 0.38f * a * b),
                (r / outer) to KoFiCoral.copy(alpha = 0.34f * a * b),
                0.62f to KoFiPink.copy(alpha = 0.14f * a * b),
                1f to Color.Transparent,
                center = c,
                radius = outer,
            ),
            radius = outer,
            center = c,
        )
        drawContent()
        drawCircle(
            brush = Brush.sweepGradient(
                listOf(KoFiCoral, KoFiPink, Color(0xFFFFB38A), KoFiPink, KoFiCoral),
                center = c,
            ),
            radius = r + 2.dp.toPx(),
            center = c,
            alpha = (0.45f + 0.35f * b) * a,
            style = Stroke(width = 1.6.dp.toPx()),
        )
    }
}

internal fun Path.addHeart(cx: Float, cy: Float, s: Float) {
    moveTo(cx, cy + s * 0.95f)
    cubicTo(cx - s * 1.7f, cy - s * 0.15f, cx - s * 0.95f, cy - s * 1.25f, cx, cy - s * 0.4f)
    cubicTo(cx + s * 0.95f, cy - s * 1.25f, cx + s * 1.7f, cy - s * 0.15f, cx, cy + s * 0.95f)
    close()
}