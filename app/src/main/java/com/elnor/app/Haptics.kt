package com.elnor.app

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The kinds of light vibration the app uses. The kind says what happened, so the same kind feels
 * the same everywhere:
 *  - [TICK]: a small touch (a button, a tab, selecting one more note)
 *  - [CONFIRM]: something was done (pinned, archived, deleted, restored, task checked off)
 *  - [LONG_PRESS]: touch and hold was recognised (the selection of notes starts)
 *  - [REJECT]: something did not work (a note could not be saved, a backup failed)
 */
enum class HapticKind { TICK, CONFIRM, LONG_PRESS, REJECT }

/**
 * Settings > Appearance > Haptic feedback. One switch for the whole app, read at the moment a
 * vibration is asked for. On by default. [enabled] is filled from the saved setting when the app
 * starts, and changed by the switch in Settings.
 */
object HapticSettings {
    @Volatile
    var enabled: Boolean = true
}

/**
 * Which Android haptic constant a [kind] uses. [sdkInt] is a parameter (not read here) so this can
 * be tested without a phone. Android 11 (R) added the "confirm" and "reject" ticks made for exactly
 * these two cases; older phones get the closest older ones.
 */
internal fun hapticConstantFor(kind: HapticKind, sdkInt: Int): Int = when (kind) {
    HapticKind.TICK -> HapticFeedbackConstants.CLOCK_TICK
    HapticKind.CONFIRM ->
        if (sdkInt >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY
    HapticKind.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
    HapticKind.REJECT ->
        if (sdkInt >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS
}

/** The constant to perform, or null when nothing should happen (the setting is off). */
internal fun hapticConstantOrNull(kind: HapticKind, sdkInt: Int, enabled: Boolean): Int? =
    if (enabled) hapticConstantFor(kind, sdkInt) else null

/**
 * A light vibration of the given [kind]. It uses the view's own haptic feedback, so it needs no
 * permission and also follows the phone's "touch feedback" setting: nothing happens when the user
 * has turned vibration feedback off there, or the switch in Elnor's Settings.
 */
fun performHaptic(view: View, kind: HapticKind) {
    val constant = hapticConstantOrNull(kind, Build.VERSION.SDK_INT, HapticSettings.enabled) ?: return
    view.performHapticFeedback(constant)
}

/** For code inside a composable: `val haptic = rememberHaptic()` and then `haptic(HapticKind.TICK)`. */
@Composable
fun rememberHaptic(): (HapticKind) -> Unit {
    val view = LocalView.current
    return remember(view) { { kind: HapticKind -> performHaptic(view, kind) } }
}

/** A light tick when a task is checked off (the first haptic of the app, kept under its old name). */
fun performTaskDoneHaptic(view: View) = performHaptic(view, HapticKind.CONFIRM)

private const val HAPTIC_SETTINGS_FILE = "elnor_settings"
private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"

/** On when nothing is saved, so the first start already has the vibrations. */
internal fun loadHapticEnabled(context: Context): Boolean =
    context.applicationContext
        .getSharedPreferences(HAPTIC_SETTINGS_FILE, Context.MODE_PRIVATE)
        .getBoolean(KEY_HAPTIC_FEEDBACK, true)

internal fun saveHapticEnabled(context: Context, value: Boolean) {
    context.applicationContext
        .getSharedPreferences(HAPTIC_SETTINGS_FILE, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_HAPTIC_FEEDBACK, value)
        .apply()
}
