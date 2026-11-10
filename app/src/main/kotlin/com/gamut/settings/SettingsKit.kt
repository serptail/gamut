package com.gamut.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.CaretLeft
import com.adamglin.phosphoricons.bold.CaretRight
import com.adamglin.phosphoricons.bold.Check
import com.gamut.core.designsystem.CoverTheme
import com.gamut.core.designsystem.GamutMotion
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.TransparentStatusBar
import com.gamut.core.designsystem.ink
import com.gamut.core.designsystem.pressScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val GamutDestructive = Color(0xFFFF6B6B)

val GamutHeading: TextStyle @Composable get() = MaterialTheme.typography.headlineMedium

val LocalPageActive = androidx.compose.runtime.compositionLocalOf { true }

@Composable
fun GamutPage(
    title: String,
    onBack: () -> Unit,
    nowPlaying: @Composable () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalCoverPalette.current
    if (LocalPageActive.current) TransparentStatusBar(darkIcons = palette.isLight)
    CoverTheme(palette) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).pressScale(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(PhosphorIcons.Bold.CaretLeft, "Back", tint = ink(0.85f), modifier = Modifier.size(24.dp))
                }
                if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleLarge, color = ink(), modifier = Modifier.padding(start = 4.dp))
                if (actions != null) {
                    Spacer(Modifier.weight(1f))
                    actions()
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                content()
                Spacer(Modifier.height(32.dp))
            }
            nowPlaying()
        }
    }
}

@Composable
fun GroupLabel(text: String) {
    Text(text, style = GamutHeading, color = ink(), modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), content = content)
}

@Composable
fun CellDivider(inset: Boolean = true) = Unit

@Composable
fun Cell(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    destructive: Boolean = false,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tone = if (destructive) GamutDestructive else ink()
    val tap = if (onClick != null) Modifier.pressScale(onClick = onClick, scaleDown = 0.985f) else Modifier
    Row(modifier.fillMaxWidth().then(tap).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = if (destructive) GamutDestructive else ink(0.8f), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor ?: ink(0.55f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null && chevron && !destructive) {
            Icon(PhosphorIcons.Bold.CaretRight, null, tint = ink(0.45f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun Tile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tone = if (destructive) GamutDestructive else ink()
    Box(
        modifier.aspectRatio(1f).pressScale(onClick = onClick, scaleDown = 0.97f)
            .clip(RoundedCornerShape(26.dp)).background(ink(0.07f)).padding(18.dp),
    ) {
        Icon(icon, null, tint = if (destructive) GamutDestructive else ink(0.85f), modifier = Modifier.size(28.dp).align(Alignment.TopStart))
        if (badge != null) Box(Modifier.align(Alignment.TopEnd)) { badge() }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ink(0.55f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun GamutButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true) {
    val onInk = if (LocalCoverPalette.current.isLight) Color.White else Color.Black
    Box(
        modifier.pressScale(onClick = onClick).clip(RoundedCornerShape(16.dp))
            .background(if (filled) ink(0.95f) else ink(0.12f)).padding(horizontal = 20.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (filled) onInk else ink(), maxLines = 1)
    }
}

@Composable
fun GamutSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val onInk = if (LocalCoverPalette.current.isLight) Color.White else Color.Black
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switch")
    val track by animateColorAsState(if (checked) ink(0.95f) else ink(0.18f), tween(GamutMotion.CONTENT_MS), label = "switch-track")
    val thumb by animateColorAsState(if (checked) onInk else ink(0.7f), tween(GamutMotion.CONTENT_MS), label = "switch-thumb")
    Box(
        Modifier.pressScale(onClick = { onChange(!checked) }).size(width = 48.dp, height = 28.dp).clip(CircleShape)
            .background(track).padding(3.dp),
    ) {
        Box(Modifier.offset(x = x).size(22.dp).background(thumb, CircleShape))
    }
}

@Composable
fun GamutCheck(checked: Boolean, label: String, onChange: (Boolean) -> Unit, warn: Boolean = false) {
    val onInk = if (LocalCoverPalette.current.isLight) Color.White else Color.Black
    val shape = RoundedCornerShape(7.dp)
    Row(
        Modifier.fillMaxWidth().pressScale(onClick = { onChange(!checked) }, scaleDown = 0.98f).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fill by animateColorAsState(if (checked) ink(0.95f) else ink(0.95f).copy(alpha = 0f), tween(GamutMotion.CONTENT_MS), label = "check-fill")
        val edge by animateColorAsState(if (checked) ink(0.3f).copy(alpha = 0f) else ink(0.3f), tween(GamutMotion.CONTENT_MS), label = "check-edge")
        Box(
            Modifier
                .size(22.dp)
                .clip(shape)
                .background(fill, shape)
                .border(1.5.dp, edge, shape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = checked,
                modifier = Modifier,
                enter = fadeIn(tween(120)) +
                    scaleIn(tween(160), initialScale = 0.6f),
                exit = fadeOut(tween(100)),
                label = "check-visibility",
            ) {
                Icon(
                    imageVector = PhosphorIcons.Bold.Check,
                    contentDescription = null,
                    tint = onInk,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (warn && checked) GamutDestructive else ink(0.85f),
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
fun GamutField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ink(0.07f)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = ink(0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink()),
                cursorBrush = SolidColor(ink()),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailing != null) trailing()
    }
}

val LocalDialogDismiss = staticCompositionLocalOf<() -> Unit> { {} }

private const val DIALOG_EXIT_MS = 150

@Composable
fun GamutDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
    body: String? = null,
    dismissLabel: String? = "Cancel",
    confirmCloses: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val palette = LocalCoverPalette.current
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val finish: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            closing = true
            state.targetState = false
            scope.launch {
                delay(DIALOG_EXIT_MS + 20L)
                action()
            }
        }
    }
    val dismiss: () -> Unit = { finish(onDismiss) }
    val confirm: () -> Unit = { if (confirmCloses) finish(onConfirm) else onConfirm() }

    Dialog(onDismissRequest = dismiss) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        AnimatedVisibility(
            visibleState = state,
            enter = fadeIn(tween(180)) + scaleIn(tween(GamutMotion.CONTENT_MS + 40, easing = GamutMotion.Standard), initialScale = 0.92f),
            exit = fadeOut(tween(DIALOG_EXIT_MS - 20)) + scaleOut(tween(DIALOG_EXIT_MS, easing = GamutMotion.Standard), targetScale = 0.97f),
        ) {
            val dim by transition.animateFloat(label = "dialog-dim", transitionSpec = { tween(GamutMotion.CONTENT_MS) }) {
                if (it == EnterExitState.Visible) 0.5f else 0f
            }
            SideEffect { window?.setDimAmount(dim) }
            CompositionLocalProvider(LocalDialogDismiss provides dismiss) {
                CoverTheme(palette) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(palette.base).padding(24.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = ink())
                        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = ink(0.6f), modifier = Modifier.padding(top = 10.dp))
                        if (content != null) Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
                        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.End) {
                            if (dismissLabel != null) {
                                Text(
                                    dismissLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ink(),
                                    modifier = Modifier.pressScale(onClick = dismiss).padding(horizontal = 16.dp, vertical = 10.dp),
                                )
                            }
                            Text(
                                confirmLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (destructive) GamutDestructive else ink(),
                                modifier = Modifier.pressScale(onClick = confirm).padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}