@file:OptIn(ExperimentalTransitionApi::class)

package com.elnor.app

import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
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
 *  - [end]: the gesture was finished and Back was done
 *  - [cancel]: the user let go without going back
 */
@Stable
class BackGesture {
    var progress by mutableFloatStateOf(0f)
        private set
    var active by mutableStateOf(false)
        private set

    fun update(progress: Float) {
        this.progress = progress
        active = true
    }

    fun end() {
        active = false
    }

    fun cancel() {
        progress = 0f
        active = false
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
