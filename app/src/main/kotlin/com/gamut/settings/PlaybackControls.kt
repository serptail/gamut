package com.gamut.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.CaretRight
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale
import com.gamut.core.player.EqBand
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(26.dp)
private val BouncySpec = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)

@Composable
fun SoftHeader(text: String) {
    Text(
        text,
        style = GamutHeading.copy(fontSize = 20.sp),
        color = ink(0.9f),
        modifier = Modifier.padding(start = 8.dp, top = 30.dp, bottom = 10.dp),
    )
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(ink(0.06f)), content = content)
}

@Composable
fun SoftDivider() {
    Box(Modifier.padding(start = 20.dp).fillMaxWidth().height(1.dp).background(ink(0.06f)))
}

@Composable
private fun RowText(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ink(0.55f), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun PillSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val onInk = if (LocalCoverPalette.current.isLight) Color.White else Color.Black
    val trackOn = ink(0.95f)
    val trackOff = ink(0.16f)
    val thumbOff = ink(0.7f)
    val track by animateColorAsState(if (checked) trackOn else trackOff, label = "track")
    val thumb by animateColorAsState(if (checked) onInk else thumbOff, label = "thumb")
    val x by animateDpAsState(
        if (checked) 22.dp else 0.dp,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "x",
    )
    Box(modifier.size(width = 52.dp, height = 30.dp).clip(CircleShape).background(track).padding(3.dp)) {
        Box(Modifier.offset(x = x).size(24.dp).background(thumb, CircleShape))
    }
}

@Composable
fun SoftSwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f)
            .then(if (enabled) Modifier.pressScale(onClick = { onChange(!checked) }, scaleDown = 0.99f) else Modifier)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        PillSwitch(checked)
    }
}

@Composable
fun SoftValueRow(title: String, value: String? = null, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressScale(onClick = onClick, scaleDown = 0.99f).padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, subtitle, Modifier.weight(1f))
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = ink(0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(PhosphorIcons.Bold.CaretRight, null, tint = ink(0.4f), modifier = Modifier.size(16.dp))
    }
}

@Composable
fun PillSlider(
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    centered: Boolean = false,
    onFinished: (() -> Unit)? = null,
) {
    val onChangeNow by rememberUpdatedState(onChange)
    val onFinishedNow by rememberUpdatedState(onFinished)
    var pressed by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (pressed) 1.2f else 1f, BouncySpec, label = "grow")
    val trackC = ink(0.12f)
    val fillC = ink(0.95f)
    val tickC = ink(0.35f)
    val span = range.endInclusive - range.start
    val f = ((value - range.start) / span).coerceIn(0f, 1f)

    Canvas(
        modifier.fillMaxWidth().height(28.dp).pointerInput(range, steps, enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val pad = 10.dp.toPx()
                fun apply(x: Float) {
                    var p = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                    if (steps > 0) p = (p * (steps + 1)).roundToInt() / (steps + 1f)
                    onChangeNow(range.start + p * span)
                }
                val down = awaitFirstDown()
                pressed = true
                apply(down.position.x)
                drag(down.id) { change ->
                    change.consume()
                    apply(change.position.x)
                }
                pressed = false
                onFinishedNow?.invoke()
            }
        },
    ) {
        val pad = 10.dp.toPx()
        val th = 3.dp.toPx()
        val cy = size.height / 2f
        val x = pad + f * (size.width - 2 * pad)
        val r = th / 2f
        drawRoundRect(trackC, Offset(pad, cy - r), Size(size.width - 2 * pad, th), CornerRadius(r))
        val startX = if (centered) size.width / 2f else pad
        val l = min(startX, x)
        val rr = max(startX, x)
        if (rr - l > 0.5f) drawRoundRect(fillC, Offset(l, cy - r), Size(rr - l, th), CornerRadius(r))
        if (centered) drawLine(tickC, Offset(size.width / 2f, cy - 5.dp.toPx()), Offset(size.width / 2f, cy + 5.dp.toPx()), 1.dp.toPx(), StrokeCap.Round)
        drawCircle(fillC, 6.5.dp.toPx() * grow, Offset(x, cy))
    }
}

@Composable
fun SoftSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    steps: Int = 0,
    centered: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).padding(horizontal = 20.dp).padding(top = 15.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = ink(0.6f), maxLines = 1)
        }
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ink(0.55f), modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(8.dp))
        PillSlider(value, onChange, range, enabled = enabled, steps = steps, centered = centered)
    }
}

@Composable
fun <T> SegmentedControl(options: List<Choice<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val idx = options.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    BoxWithConstraints(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ink(0.07f)).padding(4.dp)) {
        val segW = maxWidth / options.size
        val x by animateDpAsState(segW * idx, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium), label = "seg")
        Box(Modifier.offset(x = x).width(segW).height(38.dp).clip(RoundedCornerShape(12.dp)).background(ink(0.16f)))
        Row {
            options.forEachIndexed { i, o ->
                Box(
                    Modifier.weight(1f).height(38.dp).pressScale(onClick = { onSelect(o.value) }, scaleDown = 0.95f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        o.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (i == idx) ink() else ink(0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun EqCurve(bands: List<EqBand>, maxDb: Float, enabled: Boolean, lnMin: Float, lnMax: Float, modifier: Modifier = Modifier) {
    val line = ink(0.95f)
    val area = ink(0.12f)
    val grid = ink(0.1f)
    Canvas(modifier.fillMaxWidth().height(96.dp).alpha(if (enabled) 1f else 0.4f)) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        drawLine(grid, Offset(0f, mid), Offset(w, mid), 1.dp.toPx())
        if (bands.isEmpty()) return@Canvas
        val inset = 10.dp.toPx()
        val pts = bands.sortedBy { it.freqHz }.map { b ->
            Offset(
                ((ln(b.freqHz) - lnMin) / (lnMax - lnMin)).coerceIn(0f, 1f) * w,
                mid - (b.gainDb / maxDb).coerceIn(-1f, 1f) * (mid - inset),
            )
        }
        val path = Path().apply {
            moveTo(0f, pts.first().y)
            lineTo(pts.first().x, pts.first().y)
            for (i in 0 until pts.size - 1) {
                val a = pts[i]
                val b = pts[i + 1]
                val mx = (a.x + b.x) / 2f
                cubicTo(mx, a.y, mx, b.y, b.x, b.y)
            }
            lineTo(w, pts.last().y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(w, mid)
            lineTo(0f, mid)
            close()
        }
        drawPath(fill, area)
        drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun EqBandSliders(
    bands: List<EqBand>,
    maxDb: Float,
    enabled: Boolean,
    labelFor: (EqBand) -> String,
    onGain: (Int, Float) -> Unit,
    onActive: (Int?) -> Unit,
) {
    Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f)) {
        bands.forEachIndexed { i, b ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                VerticalBand(b.gainDb, maxDb, enabled, { onGain(i, it) }, { onActive(if (it) i else null) })
                Spacer(Modifier.height(6.dp))
                Text(labelFor(b), color = ink(0.45f), fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun VerticalBand(gain: Float, maxDb: Float, enabled: Boolean, onChange: (Float) -> Unit, onPress: (Boolean) -> Unit) {
    val onChangeNow by rememberUpdatedState(onChange)
    val onPressNow by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (pressed) 1.25f else 1f, BouncySpec, label = "bandGrow")
    val trackC = ink(0.12f)
    val fillC = ink(0.95f)
    val tickC = ink(0.35f)

    Canvas(
        Modifier.width(26.dp).height(170.dp).pointerInput(maxDb, enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val pad = 12.dp.toPx()
                fun apply(y: Float) {
                    val p = 1f - ((y - pad) / (size.height - 2 * pad)).coerceIn(0f, 1f)
                    var db = ((-maxDb + 2 * maxDb * p) * 10).roundToInt() / 10f
                    if (abs(db) < 0.25f) db = 0f
                    onChangeNow(db)
                }
                val down = awaitFirstDown()
                pressed = true
                onPressNow(true)
                apply(down.position.y)
                drag(down.id) { change ->
                    change.consume()
                    apply(change.position.y)
                }
                pressed = false
                onPressNow(false)
            }
        },
    ) {
        val pad = 12.dp.toPx()
        val cx = size.width / 2f
        val top = pad
        val bottom = size.height - pad
        fun yFor(db: Float) = bottom - (db + maxDb) / (2 * maxDb) * (bottom - top)
        val tw = 3.dp.toPx()
        val zeroY = yFor(0f)
        val y = yFor(gain.coerceIn(-maxDb, maxDb))
        drawRoundRect(trackC, Offset(cx - tw / 2, top), Size(tw, bottom - top), CornerRadius(tw / 2))
        val t = min(y, zeroY)
        val len = abs(y - zeroY)
        if (len > 0.5f) drawRoundRect(fillC, Offset(cx - tw / 2, t), Size(tw, len), CornerRadius(tw / 2))
        drawLine(tickC, Offset(cx - 8.dp.toPx(), zeroY), Offset(cx + 8.dp.toPx(), zeroY), 1.dp.toPx())
        drawCircle(fillC, 5.dp.toPx() * grow, Offset(cx, y))
    }
}
