package com.gamut.settings

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Heart
import com.gamut.core.designsystem.AdaptiveLayout
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SupportSheetHost(vm: SupportViewModel, safeMoment: Boolean) {
    val store = vm.store
    val pending by store.pending.collectAsState()
    var shown by remember { mutableStateOf<SupportTrigger?>(null) }
    var playsAtShow by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        store.openRequests.collect {
            if (shown == null) {
                playsAtShow = store.plays
                shown = SupportTrigger.Donate
                store.setDonateOpen(true)
            }
        }
    }

    LaunchedEffect(pending, safeMoment) {
        val trigger = pending ?: return@LaunchedEffect
        if (shown != null || !safeMoment || !store.canShowNow()) return@LaunchedEffect
        delay(1_500)
        playsAtShow = store.plays
        store.onSheetShown()
        shown = trigger
    }

    shown?.let { trigger ->
        val open = rememberOpenKoFi()
        SupportSheet(
            trigger = trigger,
            plays = playsAtShow,
            onBuy = {
                store.onSupported()
                open()
            },
            onLater = { if (trigger != SupportTrigger.Donate) store.onLater() },
            onNever = { store.onNeverAgain() },
            onGone = {
                shown = null
                store.setDonateOpen(false)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportSheet(
    trigger: SupportTrigger,
    plays: Int,
    onBuy: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
    onGone: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    var handled by remember { mutableStateOf(false) }
    fun resolve(action: () -> Unit) {
        if (!handled) {
            handled = true
            action()
        }
    }
    fun leave(action: () -> Unit) {
        resolve(action)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onGone() }
    }

    ModalBottomSheet(
        onDismissRequest = {
            resolve(onLater)
            onGone()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = minOf(AdaptiveLayout.SheetContentMaxWidth, 520.dp))
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CoffeeCup()
                Spacer(Modifier.height(8.dp))
                Text(headline(trigger, plays), style = MaterialTheme.typography.titleLarge, color = ink(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(
                    body(trigger),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink(0.7f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier
                        .fillMaxWidth().height(56.dp)
                        .pressScale(onClick = { leave(onBuy) }, scaleDown = 0.97f)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(KoFiPink, KoFiCoral)))
                        .semantics { role = Role.Button },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(PhosphorIcons.Bold.Heart, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Buy me a coffee", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { leave(onLater) }, colors = ButtonDefaults.textButtonColors(contentColor = ink(0.7f))) {
                    Text(if (trigger == SupportTrigger.Donate) "Not now" else "Maybe later")
                }
                if (trigger != SupportTrigger.Donate) {
                    Text(
                        "I already did / don\u2019t ask again",
                        style = MaterialTheme.typography.bodySmall,
                        color = ink(0.45f),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { leave(onNever) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private fun body(trigger: SupportTrigger): String = when (trigger) {
    SupportTrigger.Donate ->
        "Gamut is free, open source and ad-free. Support keeps development going."
    else -> "Gamut will always be free and open source. Every bit of support helps us keep improving it."
}

private fun headline(trigger: SupportTrigger, plays: Int): String = when {
    trigger == SupportTrigger.Donate -> "Support Gamut"
    plays >= 20 -> "You\u2019ve played ${"%,d".format(plays)} songs on Gamut."
    trigger == SupportTrigger.FirstDownload -> "Your first song is saved for offline."
    trigger == SupportTrigger.PlaylistMilestone -> "That playlist is really coming together."
    else -> "Enjoying Gamut?"
}

@Composable
internal fun CoffeeCup(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animate = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }

    val cup = KoFiCup
    val heart = Color.White
    val steam = ink(0.55f)
    val saucer = cup.copy(alpha = 0.35f)

    val phaseState: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "cup").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
            label = "steam",
        )
    } else {
        remember { mutableStateOf(0.3f) }
    }

    val pop = remember { Animatable(if (animate) 0.6f else 1f) }
    LaunchedEffect(Unit) { if (animate) pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }

    Canvas(
        modifier.size(104.dp).graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
            alpha = ((pop.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
        },
    ) {
        val w = size.width
        val h = size.height
        val phase = phaseState.value
        val line = 3.dp.toPx()

        val bodyL = w * 0.23f
        val bodyR = w * 0.73f
        val bodyT = h * 0.46f
        val bodyB = h * 0.84f
        val bw = bodyR - bodyL
        val bh = bodyB - bodyT

        val xs = floatArrayOf(0.26f, 0.5f, 0.74f)
        for (i in 0..2) {
            val p = (phase + i / 3f) % 1f
            val len = h * (0.22f + 0.04f * i)
            val x0 = bodyL + bw * xs[i]
            val y0 = bodyT - h * 0.06f - p * h * 0.10f
            val path = Path()
            for (k in 0..14) {
                val f = k / 14f
                val x = x0 + sin((f * 1.6f + p * 2f) * PI).toFloat() * w * 0.035f
                val y = y0 - f * len
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val a = sin(PI * p).toFloat().coerceIn(0f, 1f)
            drawPath(path, steam.copy(alpha = steam.alpha * a), style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        val hr = bh * 0.30f
        val hc = Offset(bodyR - line * 0.3f, bodyT + bh * 0.42f)
        drawArc(cup, startAngle = -90f, sweepAngle = 180f, useCenter = false, topLeft = Offset(hc.x - hr, hc.y - hr), size = Size(hr * 2, hr * 2), style = Stroke(line * 1.8f, cap = StrokeCap.Round))

        val body = Path().apply {
            moveTo(bodyL, bodyT)
            lineTo(bodyR, bodyT)
            cubicTo(bodyR, bodyT + bh * 0.9f, bodyR - bw * 0.12f, bodyB, bodyR - bw * 0.28f, bodyB)
            lineTo(bodyL + bw * 0.28f, bodyB)
            cubicTo(bodyL + bw * 0.12f, bodyB, bodyL, bodyT + bh * 0.9f, bodyL, bodyT)
            close()
        }
        drawPath(body, cup)

        val beat = if (phase < 0.18f) sin(PI * phase / 0.18f).toFloat() else 0f
        val s = bw * 0.11f * (1f + 0.22f * beat)
        drawPath(Path().apply { addHeart(bodyL + bw / 2f, bodyT + bh * 0.46f, s) }, heart)

        drawRoundRect(saucer, topLeft = Offset(bodyL - bw * 0.14f, bodyB + 1.5.dp.toPx()), size = Size(bw * 1.28f, 4.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()))
    }
}