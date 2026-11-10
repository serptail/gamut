package com.gamut.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Check
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale

@Composable
fun SwitchCell(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Cell(
        title = title,
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        subtitle = subtitle,
        onClick = if (enabled) ({ onChange(!checked) }) else null,
        trailing = {
            Spacer(Modifier.width(12.dp))
            GamutSwitch(checked) { if (enabled) onChange(it) }
        },
    )
}

@Composable
fun GamutSlider(
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    onFinished: (() -> Unit)? = null,
) {
    PillSlider(
        value = value,
        onChange = onChange,
        range = range,
        modifier = modifier,
        enabled = enabled,
        steps = steps,
        onFinished = onFinished,
    )
}

@Composable
fun SliderCell(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    steps: Int = 0,
) {
    Column(modifier.fillMaxWidth().padding(top = 10.dp).alpha(if (enabled) 1f else 0.4f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = ink(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = ink(0.55f), maxLines = 1)
        }
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ink(0.55f), modifier = Modifier.padding(top = 2.dp))
        }
        GamutSlider(value, onChange, range, enabled = enabled, steps = steps)
    }
}

data class Choice<T>(val value: T, val label: String, val hint: String? = null)

@Composable
fun <T> GamutChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
) {
    GamutDialog(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = "Close",
        onConfirm = onDismiss,
        dismissLabel = null,
        body = note,
    ) {
        val close = LocalDialogDismiss.current
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            choices.forEach { choice ->
                Row(
                    Modifier.fillMaxWidth()
                        .pressScale(onClick = {
                            onSelect(choice.value)
                            close()
                        }, scaleDown = 0.98f)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(choice.label, style = MaterialTheme.typography.titleMedium, color = ink())
                        if (choice.hint != null) {
                            Text(choice.hint, style = MaterialTheme.typography.bodyMedium, color = ink(0.55f))
                        }
                    }
                    if (choice.value == selected) {
                        Icon(PhosphorIcons.Bold.Check, null, tint = ink(), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
