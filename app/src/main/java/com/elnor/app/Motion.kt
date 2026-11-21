package com.elnor.app

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The motion of the whole app in one place: how long it takes, how it speeds up and slows down,
 * and how far things slide. Every animation takes its numbers from here, so the app moves in one
 * style. The animations follow the phone's "Remove animations" setting by themselves (Compose
 * reads the animator duration scale of the system), so nothing has to be checked in the code.
 */
internal object Motion {
    /** A whole screen, or a big part of it, changes. */
    const val STANDARD_MS = 250

    /** A small part appears or disappears (a bar, a button, a panel). */
    const val SHORT_MS = 180

    /** A slide goes this part of the width (or height) of the screen: 1/10, small and calm. */
    private const val SLIDE_DIVISOR = 10

    val easing = FastOutSlowInEasing

    fun <T> standard(): FiniteAnimationSpec<T> = tween(durationMillis = STANDARD_MS, easing = easing)

    fun <T> short(): FiniteAnimationSpec<T> = tween(durationMillis = SHORT_MS, easing = easing)

    // ---- Screen changes (slide + fade) ----

    /**
     * A screen opens ([forward]) or closes (not [forward]) sideways. Going in, the new screen comes
     * a little from the right while the old one moves a little to the left. Going back, it is the
     * other way round.
     */
    fun sideways(forward: Boolean): ContentTransform {
        val sign = if (forward) 1 else -1
        return (slideInHorizontally(standard()) { sign * it / SLIDE_DIVISOR } + fadeIn(standard()))
            .togetherWith(
                slideOutHorizontally(standard()) { -sign * it / SLIDE_DIVISOR } + fadeOut(standard())
            )
    }

    /**
     * The editor page opens ([opening]) or closes. It rises a little from the bottom and fades in;
     * when it closes it fades out while the page below shows through.
     */
    fun page(opening: Boolean): ContentTransform =
        if (opening) {
            (slideInVertically(standard()) { it / SLIDE_DIVISOR } + fadeIn(standard()))
                .togetherWith(fadeOut(standard()))
        } else {
            fadeIn(standard()).togetherWith(
                slideOutVertically(standard()) { it / SLIDE_DIVISOR } + fadeOut(standard())
            ).apply {
                // The page that closes stays on top while it goes, the page below shows through it
                targetContentZIndex = -1f
            }
        }

    /**
     * A screen or page closes because of Back (the arrow, the button, or the Back gesture). The
     * screen behind it is already there and does not move; the page that closes is drawn by
     * BackPreviewLayer (it shrinks, follows the finger, gets round corners and fades away). The
     * page that closes stays on top while it goes.
     *
     * The exit has to be a real animation: it is what keeps the closing page on the screen until
     * the animation is over. It is almost invisible (alpha 1 to 0.99), BackPreviewLayer does the
     * drawing. It is linear, so the page follows the finger one to one.
     */
    fun backPreview(): ContentTransform =
        EnterTransition.None
            .togetherWith(
                fadeOut(tween(durationMillis = STANDARD_MS, easing = LinearEasing), targetAlpha = 0.99f)
            )
            .apply { targetContentZIndex = -1f }

    // ---- Small parts that come and go ----

    /** Only fades in. For parts that appear in place. */
    val fadeInShort: EnterTransition get() = fadeIn(short())

    val fadeOutShort: ExitTransition get() = fadeOut(short())
}

/**
 * Fades the content in when it first appears, so an empty-state message does not pop in. It uses
 * the same short fade as the rest of the app ([Motion.short]) and follows the phone's "Remove
 * animations" setting.
 */
@Composable
internal fun Modifier.fadeInOnAppear(): Modifier {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, Motion.short()) }
    return graphicsLayer { this.alpha = alpha.value }
}
