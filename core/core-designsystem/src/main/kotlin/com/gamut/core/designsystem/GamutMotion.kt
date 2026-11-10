package com.gamut.core.designsystem

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

object GamutMotion {
    const val TAB_MS = 180
    const val EXIT_MS = 75
    const val ENTER_MS = 160

    const val ENTER_DELAY_MS = EXIT_MS

    const val SLIDE_MS = ENTER_DELAY_MS + ENTER_MS

    const val POP_SLIDE_MS = EXIT_MS

    const val PLAYER_IN_MS = 260
    const val PLAYER_OUT_MS = 200

    const val PLAYER_LAYER_IN_MS = 240
    const val PLAYER_LAYER_OUT_MS = 180
    const val EXPAND_MS = 180
    const val CONTENT_MS = 150
    const val CROSSFADE_MS = 220

    const val SLIDE_FRACTION = 10

    const val HOME_SHIFT = 0.08f

    val Standard = FastOutSlowInEasing
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    val PlayerClose = FastOutLinearInEasing

    val pushEnter: EnterTransition =
        fadeIn(tween(ENTER_MS, delayMillis = ENTER_DELAY_MS, easing = Standard)) +
            slideInHorizontally(tween(SLIDE_MS, easing = Standard)) { it / SLIDE_FRACTION }
    val pushExit: ExitTransition =
        fadeOut(tween(EXIT_MS, easing = FastOutLinearInEasing)) +
            slideOutHorizontally(tween(POP_SLIDE_MS, easing = FastOutLinearInEasing)) { -it / SLIDE_FRACTION }
    val popEnter: EnterTransition =
        fadeIn(tween(ENTER_MS, delayMillis = ENTER_DELAY_MS, easing = Standard)) +
            slideInHorizontally(tween(SLIDE_MS, easing = Standard)) { -it / SLIDE_FRACTION }
    val popExit: ExitTransition =
        fadeOut(tween(EXIT_MS, easing = FastOutLinearInEasing)) +
            slideOutHorizontally(tween(POP_SLIDE_MS, easing = FastOutLinearInEasing)) { it / SLIDE_FRACTION }

    const val NAV_MS = TAB_MS
    val navPushEnter: EnterTransition = slideInHorizontally(tween(NAV_MS, easing = Standard)) { it }
    val navPushExit: ExitTransition = slideOutHorizontally(tween(NAV_MS, easing = Standard)) { -it }
    val navPopEnter: EnterTransition = slideInHorizontally(tween(NAV_MS, easing = Standard)) { -it }
    val navPopExit: ExitTransition = slideOutHorizontally(tween(NAV_MS, easing = Standard)) { it }

    val playerEnter: EnterTransition =
        slideInVertically(tween(PLAYER_IN_MS, easing = EmphasizedDecelerate)) { it }
    val playerExit: ExitTransition =
        slideOutVertically(tween(PLAYER_OUT_MS, easing = EmphasizedAccelerate)) { it }

    val expandIn: EnterTransition =
        fadeIn(tween(EXPAND_MS, delayMillis = 20, easing = Standard)) +
            expandVertically(tween(EXPAND_MS, easing = Standard), expandFrom = Alignment.Top)
    val collapseOut: ExitTransition =
        fadeOut(tween(120)) +
            shrinkVertically(tween(EXPAND_MS, easing = Standard), shrinkTowards = Alignment.Top)

    val itemPlacement: FiniteAnimationSpec<IntOffset> = tween(EXPAND_MS, easing = Standard)
}

fun Modifier.softResize(): Modifier = animateContentSize(tween(GamutMotion.EXPAND_MS, easing = GamutMotion.Standard))

val LocalNavSettled = compositionLocalOf { true }
