package com.gamut.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamut.R
import com.gamut.core.designsystem.AdaptiveLayout
import com.gamut.core.designsystem.CoverTheme
import com.gamut.core.designsystem.GamutBackdrop
import com.gamut.core.designsystem.GamutDisplayFont
import com.gamut.core.designsystem.GamutMotion
import com.gamut.core.designsystem.GamutTheme
import com.gamut.core.designsystem.LocalAppearance
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.TransparentStatusBar
import com.gamut.core.designsystem.appNeutralPalette
import com.gamut.core.designsystem.focusRing
import com.gamut.core.designsystem.ink
import com.gamut.settings.AppearanceViewModel
import kotlinx.coroutines.delay

@Composable
fun OnboardingGate(vm: WelcomeViewModel = hiltViewModel(), app: @Composable () -> Unit) {
    val rawGate by vm.gate.collectAsState()
    var appShown by remember { mutableStateOf(false) }
    if (rawGate == Gate.Ready) appShown = true
    val gate = if (rawGate == Gate.Loading && appShown) Gate.Ready else rawGate
    Crossfade(targetState = gate, animationSpec = tween(GamutMotion.CROSSFADE_MS, easing = GamutMotion.Standard), label = "onboarding-gate") { g ->
        when (g) {
            Gate.Ready -> app()
            Gate.Welcome -> AppearanceRoot { WelcomeScreen(vm) }
            Gate.Loading -> AppearanceRoot { Box(Modifier.fillMaxSize().background(LocalCoverPalette.current.base)) }
        }
    }
}

@Composable
private fun AppearanceRoot(content: @Composable () -> Unit) {
    val appearance by hiltViewModel<AppearanceViewModel>().settings.collectAsState()
    val palette = appNeutralPalette(appearance)
    TransparentStatusBar(darkIcons = palette.isLight)
    CompositionLocalProvider(LocalAppearance provides appearance) {
        GamutTheme { CoverTheme(palette) { content() } }
    }
}

private val WideBreakpoint = AdaptiveLayout.WideBreakpoint

private val ActionsWidth = 440.dp

private val WideContentMax = 1120.dp

private val HeroMaxWide = 380.dp
private val HeroMaxCompact = 240.dp
private val HeroMin = 104.dp

private val ShortWindow = 520.dp

@Composable
fun WelcomeScreen(
    vm: WelcomeViewModel,
    logo: @Composable () -> Unit = {
        val res = if (LocalCoverPalette.current.isLight) {
            R.drawable.ic_gamut_stacked_black
        } else {
            R.drawable.ic_gamut_stacked_white
        }
        Image(
            painterResource(res),
            contentDescription = "Gamut",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    },
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val palette = LocalCoverPalette.current

    GamutBackdrop {
        BoxWithConstraints(Modifier.fillMaxSize().background(palette.base)) {
            BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
                val wide = maxWidth >= WideBreakpoint
                val windowWidth: Dp = maxWidth
                val windowHeight: Dp = maxHeight
                val short = windowHeight < ShortWindow

                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (wide) {
                        val sidePadding = 40.dp
                        val gap = 56.dp
                        val paneWidth = (minOf(windowWidth, WideContentMax) - sidePadding * 2 - gap) / 2f
                        val heroSize = maxOf(minOf(HeroMaxWide, windowHeight - 96.dp, paneWidth), HeroMin)
                        Row(
                            Modifier
                                .widthIn(max = WideContentMax)
                                .fillMaxWidth()
                                .heightIn(min = windowHeight)
                                .padding(horizontal = sidePadding, vertical = if (short) 16.dp else 32.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Hero(heroSize, logo) }
                            Column(Modifier.weight(1f).widthIn(max = ActionsWidth)) {
                                WelcomeCopy(
                                    headlineSize = when {
                                        short -> 32.sp
                                        windowWidth >= 1000.dp -> 54.sp
                                        else -> 46.sp
                                    },
                                    centered = false,
                                    compactCopy = short,
                                )
                                Spacer(Modifier.height(if (short) 20.dp else 32.dp))
                                WelcomeActions(vm, ui, context, centered = false, showFootnote = !short)
                            }
                        }
                    } else {
                        val heroSize = maxOf(minOf(windowHeight * 0.27f, windowWidth * 0.62f, HeroMaxCompact), HeroMin)
                        Column(
                            Modifier
                                .widthIn(max = 480.dp)
                                .fillMaxWidth()
                                .heightIn(min = windowHeight)
                                .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Spacer(Modifier.height(1.dp))
                            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) { Hero(heroSize, logo) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                WelcomeCopy(
                                    headlineSize = if (windowWidth < 360.dp || short) 34.sp else 40.sp,
                                    centered = true,
                                    compactCopy = short,
                                )
                                Spacer(Modifier.height(if (short) 20.dp else 28.dp))
                                WelcomeActions(vm, ui, context, centered = true, showFootnote = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberReveal(delayMs: Int): Animatable<Float, *> {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(550, easing = GamutMotion.Standard))
    }
    return a
}

private fun Modifier.slideUpReveal(p: Animatable<Float, *>): Modifier = graphicsLayer {
    alpha = p.value
    translationY = (1f - p.value) * 28.dp.toPx()
}

@Composable
private fun Hero(size: Dp, logo: @Composable () -> Unit) {
    val reveal = rememberReveal(0)
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                alpha = reveal.value
                val s = 0.88f + 0.12f * reveal.value
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center,
    ) { logo() }
}

@Composable
private fun WelcomeCopy(headlineSize: TextUnit, centered: Boolean, compactCopy: Boolean) {
    val headline = rememberReveal(150)
    val body = rememberReveal(260)
    val align = if (centered) TextAlign.Center else TextAlign.Start

    Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            "Music without limits.",
            style = MaterialTheme.typography.displaySmall.copy(
                fontFamily = GamutDisplayFont,
                fontSize = headlineSize,
                lineHeight = headlineSize * 1.06f,
                letterSpacing = (headlineSize.value * -0.035f).sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = align,
            ),
            color = ink(),
            modifier = Modifier.slideUpReveal(headline),
        )
        if (!compactCopy) {
            Text(
                "Stream, download and sync across all your devices. Free and open source.",
                style = MaterialTheme.typography.bodyLarge,
                color = ink(0.6f),
                textAlign = align,
                modifier = Modifier.padding(top = 14.dp).slideUpReveal(body),
            )
        }
    }
}

@Composable
private fun WelcomeActions(
    vm: WelcomeViewModel,
    ui: WelcomeUi,
    context: android.content.Context,
    centered: Boolean,
    showFootnote: Boolean,
) {
    val reveal = rememberReveal(470)
    val align = if (centered) TextAlign.Center else TextAlign.Start

    var showSpinner by remember { mutableStateOf(false) }
    LaunchedEffect(ui.busy) {
        if (ui.busy) {
            delay(180)
            showSpinner = true
        } else {
            showSpinner = false
        }
    }

    val remote = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (remote) {
            delay(900)
            runCatching { primaryFocus.requestFocus() }
        }
    }

    Column(Modifier.slideUpReveal(reveal)) {
        Button(
            onClick = { vm.signIn(context) },
            enabled = !ui.busy,
            modifier = Modifier.fillMaxWidth().height(56.dp).focusRing(CircleShape).focusRequester(primaryFocus),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF1F1F1F),
                disabledContainerColor = Color.White,
                disabledContentColor = Color(0xFF1F1F1F),
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    Crossfade(targetState = showSpinner, animationSpec = tween(GamutMotion.CONTENT_MS), label = "google-busy") { spinning ->
                        if (spinning) {
                            CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = 2.dp, color = Color(0xFF1F1F1F))
                        } else {
                            Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text("Continue with Google", fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = vm::continueLocally,
            enabled = !ui.busy,
            modifier = Modifier.fillMaxWidth().height(56.dp).focusRing(CircleShape),
            shape = CircleShape,
            border = BorderStroke(1.dp, ink(0.25f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ink()),
        ) { Text("Continue locally", fontWeight = FontWeight.Medium) }

        if (showFootnote) {
            Text(
                "Local mode stays on this device and backs up daily.",
                style = MaterialTheme.typography.bodySmall,
                color = ink(0.45f),
                textAlign = align,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }

        var lastError by remember { mutableStateOf<String?>(null) }
        ui.error?.let { lastError = it }
        AnimatedVisibility(visible = ui.error != null, enter = GamutMotion.expandIn, exit = GamutMotion.collapseOut) {
            Text(
                lastError.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = align,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}
