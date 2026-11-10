package com.gamut.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamut.account.AccountViewModel
import com.gamut.core.designsystem.LocalNotifier
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale
import com.gamut.core.designsystem.softResize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val StarLabels = listOf("Poor", "Not great", "It\u2019s okay", "Good", "Love it")

@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    nowPlaying: @Composable () -> Unit,
    vm: FeedbackViewModel = hiltViewModel(),
    account: AccountViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsState()
    val accEmail by account.email.collectAsState()
    val notifier = LocalNotifier.current
    val focus = LocalFocusManager.current
    val activity = LocalContext.current.findActivity()
    var showDiagnostics by remember { mutableStateOf(false) }

    LaunchedEffect(accEmail) { accEmail?.let { vm.prefill("", it) } }

    LaunchedEffect(ui.status) {
        if (ui.status == FeedbackStatus.SENT) {
            notifier.success("Thanks! Your report was sent")
            vm.acknowledgeSent()
            onBack()
        }
    }

    val sending = ui.status == FeedbackStatus.SENDING
    val canSend = !sending && ui.message.isNotBlank()

    GamutPage(title = "Help & Feedback", onBack = onBack, nowPlaying = nowPlaying) {
        SegmentedControl(
            options = FeedbackType.entries.map { Choice(it, it.label) },
            selected = ui.type,
            onSelect = vm::setType,
        )

        Spacer(Modifier.height(24.dp))
        MessageField(
            value = ui.message,
            onChange = vm::setMessage,
            placeholder = when (ui.type) {
                FeedbackType.BUG -> "What did you do, what happened, and what did you expect?"
                FeedbackType.IDEA -> "Describe the feature and how you\u2019d use it."
                FeedbackType.FEEDBACK -> "Anything you love, or want us to improve."
            },
            words = ui.words,
        )

        Spacer(Modifier.height(28.dp))
        StarRating(rating = ui.rating, onChange = vm::setRating)

        Spacer(Modifier.height(28.dp))
        GamutField(ui.email, vm::setEmail, "Email for a reply (optional)")
        if (ui.emailError) {
            Text(
                "That email address doesn\u2019t look right.",
                style = MaterialTheme.typography.bodyMedium,
                color = GamutDestructive,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        SwitchCell(
            title = "Attach diagnostics",
            checked = ui.attachDiagnostics,
            onChange = vm::setAttachDiagnostics,
            subtitle = "App and device info. No account, email or song names.",
        )
        if (ui.attachDiagnostics) {
            Cell("Preview what\u2019s sent", subtitle = null, onClick = { showDiagnostics = true })
        }

        Column(Modifier.softResize()) {
            if (ui.error != null) {
                Text(
                    ui.error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GamutDestructive,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                )
            }
        }
        Spacer(Modifier.height(if (ui.error != null) 12.dp else 24.dp))
        GamutButton(
            label = if (sending) "Sending\u2026" else "Send",
            onClick = {
                if (canSend && activity != null) {
                    focus.clearFocus()
                    vm.send { activity.solveHCaptcha() }
                }
            },
            modifier = Modifier.fillMaxWidth().alpha(if (canSend) 1f else 0.45f),
        )
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime))
    }

    if (showDiagnostics) DiagnosticsDialog(vm) { showDiagnostics = false }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = ink(0.55f), textAlign = align, modifier = modifier)
}

@Composable
private fun MessageField(value: String, onChange: (String) -> Unit, placeholder: String, words: Int) {
    val nearLimit = words >= FeedbackRules.MAX_WORDS - 20
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ink(0.07f))
            .padding(horizontal = 16.dp, vertical = 14.dp).heightIn(min = 140.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = ink(0.4f))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink()),
            cursorBrush = SolidColor(ink()),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (words > 0) {
        Text(
            "$words / ${FeedbackRules.MAX_WORDS}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (nearLimit) GamutDestructive else ink(0.55f),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 4.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun StarRating(rating: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Rate Gamut", style = MaterialTheme.typography.titleMedium, color = ink(0.9f))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 1..5) Star(filled = i <= rating, onClick = { onChange(i) }, description = "$i star${if (i == 1) "" else "s"}")
        }
        Hint(
            if (rating in 1..5) StarLabels[rating - 1] else "Optional",
            Modifier.fillMaxWidth().padding(top = 6.dp),
            TextAlign.Center,
        )
    }
}

@Composable
private fun Star(filled: Boolean, onClick: () -> Unit, description: String) {
    val fill by animateFloatAsState(
        if (filled) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "star-fill",
    )
    val line = ink(0.35f)
    val solid = ink(0.95f)
    Box(
        Modifier.size(48.dp).pressScale(onClick = onClick, scaleDown = 0.88f).scale(0.88f + 0.12f * fill),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(34.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f + size.height * 0.03f)
            val path = Path()
            val outer = size.minDimension / 2f
            val inner = outer * 0.5f
            for (k in 0 until 10) {
                val r = if (k % 2 == 0) outer else inner
                val a = -PI / 2 + k * PI / 5
                val x = c.x + (r * cos(a)).toFloat()
                val y = c.y + (r * sin(a)).toFloat()
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, solid.copy(alpha = solid.alpha * fill.coerceIn(0f, 1f)))
            drawPath(path, line.copy(alpha = line.alpha * (1f - fill.coerceIn(0f, 1f))), style = Stroke(width = 2.4f * density, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun DiagnosticsDialog(vm: FeedbackViewModel, onDismiss: () -> Unit) {
    val text by produceState("Collecting\u2026") { value = vm.diagnosticsPreview() }
    GamutDialog(
        title = "Diagnostics",
        body = "This is everything that would be attached. It stays on your phone unless you send the report.",
        confirmLabel = "Close",
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        dismissLabel = null,
    ) {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp), color = ink(0.75f))
        }
    }
}
