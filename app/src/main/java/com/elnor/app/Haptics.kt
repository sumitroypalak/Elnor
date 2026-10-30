package com.elnor.app

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * A light tick when a task is checked off. It uses the view's own haptic feedback, so it needs no
 * permission and follows the phone's "touch feedback" setting: nothing happens when the user has
 * turned vibration feedback off. Android 11 and newer have a "confirm" tick made for this; older
 * phones get the plain key tick.
 */
fun performTaskDoneHaptic(view: View) {
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }
    view.performHapticFeedback(type)
}
