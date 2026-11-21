@file:OptIn(ExperimentalTransitionApi::class)

package com.elnor.app

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

// ---------------------------- Predictive back ------------------------------
//
// Android 14 and newer: while the user swipes Back, the screen that is closing follows the finger
// and the screen behind it is already visible, so the swipe can be stopped half-way. The screen
// changes of Elnor are AnimatedContent transitions (see Motion.kt). Here they are driven by the
// finger: the transition is "seeked" to the progress of the gesture, and when the gesture is
// finished the transition runs on to the end (or back to the start when the user changed their
// mind). The Back button of the phone (no gesture) just plays the transition as before.

/**
 * How far a back gesture has gone. The screen that handles Back ([PredictiveBackHandler]) writes it,
 * the screen change that must follow the finger ([rememberBackTransition]) reads it.
 *  - [active]: a gesture is in progress, [progress] is 0 (its start) to 1 (its end)
 *  - [rightEdge]: the swipe started at the right edge of the screen (the page then moves left)
 *  - [dy]: how far the finger went up (negative) or down (positive), in pixels, since the start
 *  - [end]: the gesture was finished and Back was done. [progress] and [dy] stay, so the closing
 *    animation goes on from where the finger was
 *  - [cancel]: the user let go without going back
 *  - [settle]: the animation is over, ready for the next one
 */
@Stable
class BackGesture {
    var progress by mutableFloatStateOf(0f)
        private set
    var active by mutableStateOf(false)
        private set
    var rightEdge by mutableStateOf(false)
        private set
    var dy by mutableFloatStateOf(0f)
        private set

    private var startY = 0f

    fun update(progress: Float, rightEdge: Boolean = false, touchY: Float = 0f) {
        if (!active) startY = touchY
        this.progress = progress
        this.rightEdge = rightEdge
        dy = touchY - startY
        active = true
    }

    fun end() {
        active = false
    }

    fun cancel() {
        progress = 0f
        dy = 0f
        active = false
    }

    fun settle() {
        progress = 0f
        dy = 0f
    }
}

/**
 * The transition between [target] states for an AnimatedContent (use it as
 * `transition.AnimatedContent { ... }`). It plays the normal animation when [target] changes, and
 * follows the finger while [gesture] is active and [canGoBack] is true: then the screen goes
 * towards [backTarget].
 *
 * The one that handles Back must call [BackGesture.end] AFTER it changed [target] (in the same
 * step), so the transition runs on from where the finger left it and does not jump back first.
 */
@Composable
internal fun <T> rememberBackTransition(
    target: T,
    backTarget: T,
    canGoBack: Boolean,
    gesture: BackGesture,
    label: String
): Transition<T> {
    val seekState = remember { SeekableTransitionState(target) }
    val transition = rememberTransition(seekState, label)
    val currentBackTarget by rememberUpdatedState(backTarget)
    val currentCanGoBack by rememberUpdatedState(canGoBack)

    // A normal change of [target], the end of a gesture (go on to the end) and a cancelled gesture
    // (go back to the start) are all "animate to [target]"
    LaunchedEffect(target, gesture.active) {
        if (!gesture.active && (seekState.currentState != target || seekState.targetState != target)) {
            seekState.animateTo(target)
            gesture.settle()
        }
    }

    // While the finger moves: show the screen at the progress of the gesture
    LaunchedEffect(gesture) {
        snapshotFlow { gesture.active to gesture.progress }.collect { (active, progress) ->
            if (active && currentCanGoBack) seekState.seekTo(progress, currentBackTarget)
        }
    }
    return transition
}

/**
 * Draws the page that closes because of Back like the photo viewer of Google Photos: while the finger
 * moves, the page gets smaller, follows the finger (to the side it was pulled to, and up or down),
 * gets round corners, and at the end it fades away. The screen behind it is already there, a
 * little smaller at first and growing to its full size.
 *
 * Put it around the content of an AnimatedContent whose transition is [Motion.backPreview] when
 * [leavingByBack] or [enteringByBack] is true. For every other change it does nothing.
 * [leavingByBack]: this content is the page that closes. [enteringByBack]: it is the one behind.
 *
 * All numbers come from the progress of the transition (0 to 1), which is the progress of the
 * gesture while the finger moves and plays by itself when Back was a button or the gesture ended.
 */
@Composable
internal fun AnimatedContentScope.BackPreviewLayer(
    leavingByBack: Boolean,
    enteringByBack: Boolean,
    gesture: BackGesture,
    content: @Composable () -> Unit
) {
    val linear: Transition.Segment<EnterExitState>.() -> androidx.compose.animation.core.FiniteAnimationSpec<Float> =
        { tween(durationMillis = Motion.STANDARD_MS, easing = LinearEasing) }
    // 0 to 1 while this content leaves, 0 to 1 while it arrives
    val leave by transition.animateFloat(transitionSpec = linear, label = "backLeave") {
        if (it == EnterExitState.PostExit) 1f else 0f
    }
    val arrive by transition.animateFloat(transitionSpec = linear, label = "backArrive") {
        if (it == EnterExitState.PreEnter) 0f else 1f
    }

    val modifier = when {
        leavingByBack -> Modifier.graphicsLayer {
            val p = leave
            val scale = 1f - 0.15f * p
            scaleX = scale
            scaleY = scale
            // To the side the page was pulled to, and up or down with the finger (not too far)
            val side = if (gesture.rightEdge) -1f else 1f
            translationX = side * 0.06f * size.width * p
            translationY = (gesture.dy * 0.5f).coerceIn(-0.2f * size.height, 0.2f * size.height)
            // Round corners come in at once, like the corners of the window
            val corner = 36.dp.toPx() * (p * 6f).coerceAtMost(1f)
            shape = RoundedCornerShape(corner)
            clip = true
            // It stays solid while the finger moves, and fades away at the end
            alpha = 1f - ((p - 0.65f) / 0.35f).coerceIn(0f, 1f)
        }
        enteringByBack -> Modifier.graphicsLayer {
            val scale = 0.94f + 0.06f * arrive
            scaleX = scale
            scaleY = scale
        }
        else -> Modifier
    }
    Box(Modifier.fillMaxSize().then(modifier)) { content() }
}
