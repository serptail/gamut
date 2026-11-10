package com.gamut.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.CaretLeft
import com.adamglin.phosphoricons.bold.CaretRight
import com.gamut.core.designsystem.CoverTheme
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.TransparentStatusBar
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale

@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    nowPlaying: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    val palette = LocalCoverPalette.current
    if (LocalPageActive.current) TransparentStatusBar(darkIcons = palette.isLight)
    CoverTheme(palette) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).pressScale(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(PhosphorIcons.Bold.CaretLeft, "Back", tint = ink(0.85f), modifier = Modifier.size(24.dp))
                }
                if (title.isNotEmpty()) {
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = ink(), modifier = Modifier.padding(start = 4.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { body() }
            nowPlaying()
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = ink(0.5f),
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

@Composable
fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(ink(0.08f)))
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else ink()
    val clickable = if (onClick != null) modifier.fillMaxWidth().pressScale(onClick = onClick) else modifier.fillMaxWidth()
    Row(clickable.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ink(0.55f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
        if (showChevron) Icon(PhosphorIcons.Bold.CaretRight, null, tint = ink(0.5f), modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SettingsButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false) {
    Box(
        modifier
            .pressScale(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(ink(0.1f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (destructive) MaterialTheme.colorScheme.error else ink())
    }
}
