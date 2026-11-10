package com.gamut.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.X
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale

private val TagShape = RoundedCornerShape(50)

internal val SuggestedTags = listOf("Road trip", "Chill", "Workout", "Focus", "Late night", "Throwbacks", "Party", "Study")
internal const val MAX_TAGS = 5
internal const val MAX_TAG_LENGTH = 20

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagsEditor(tags: List<String>, onChange: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    var draft by remember { mutableStateOf("") }
    fun add(raw: String) {
        val tag = raw.trim().take(MAX_TAG_LENGTH)
        if (tag.isNotEmpty() && tags.size < MAX_TAGS && tags.none { it.equals(tag, ignoreCase = true) }) onChange(tags + tag)
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag -> TagChip(tag, removable = true) { onChange(tags - tag) } }
            if (tags.size < MAX_TAGS) {
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= MAX_TAG_LENGTH) draft = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelLarge.copy(color = ink()),
                    cursorBrush = SolidColor(ink()),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        add(draft)
                        draft = ""
                    }),
                    decorationBox = { inner ->
                        Row(
                            Modifier.background(ink(0.04f), TagShape).padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (draft.isEmpty()) Text("+ Add tag", style = MaterialTheme.typography.labelLarge, color = ink(0.4f))
                            inner()
                        }
                    },
                )
            }
        }
        val unused = SuggestedTags.filter { s -> tags.none { it.equals(s, ignoreCase = true) } }
        if (tags.size < MAX_TAGS && unused.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                unused.forEach { s -> TagChip(s, removable = false) { add(s) } }
            }
        }
    }
}

@Composable
internal fun TagChip(label: String, removable: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .pressScale(onClick = onClick)
            .background(if (removable) ink(0.20f) else ink(0.08f), TagShape)
            .padding(start = 16.dp, end = if (removable) 10.dp else 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink())
        if (removable) Icon(PhosphorIcons.Bold.X, "Remove tag $label", tint = ink(0.7f), modifier = Modifier.padding(start = 6.dp).size(14.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagsRow(tags: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            Text(
                tag,
                style = MaterialTheme.typography.labelLarge,
                color = ink(0.8f),
                modifier = Modifier.background(ink(0.08f), TagShape).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}
