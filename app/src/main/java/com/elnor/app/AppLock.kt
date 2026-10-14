package com.elnor.app

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Automatically lock" choices of the App lock page.
 * [storageValue] is saved on the phone. It is a fixed text, not the enum name.
 */
enum class AutoLockTimeout(val storageValue: String, val millis: Long) {
    IMMEDIATELY("immediately", 0L),
    ONE_MINUTE("1_minute", 60_000L),
    THIRTY_MINUTES("30_minutes", 30L * 60_000L);

    companion object {
        /** Anything unknown (or nothing saved) is IMMEDIATELY, the safest choice. */
        fun fromStorage(stored: String?): AutoLockTimeout =
            entries.firstOrNull { it.storageValue == stored } ?: IMMEDIATELY
    }
}

/** True when the app was away from the screen for at least as long as [timeout]. */
fun shouldLockAfterAway(awayMillis: Long, timeout: AutoLockTimeout): Boolean =
    awayMillis >= timeout.millis

/**
 * The state of the app lock. There is no PIN or password of our own: unlocking always asks the
 * phone itself (fingerprint, face, or the phone's screen-lock PIN / pattern / password).
 * See [AppLockAuth].
 *
 * It lives as long as the app process, so rotating the screen never locks or unlocks the app.
 */
object AppLock {

    private const val SETTINGS_FILE = "elnor_settings"
    private const val KEY_ENABLED = "app_lock_enabled"
    private const val KEY_TIMEOUT = "app_lock_timeout"

    private val _enabled = MutableStateFlow(false)
    private val _timeout = MutableStateFlow(AutoLockTimeout.IMMEDIATELY)
    private val _locked = MutableStateFlow(false)

    /** The "Unlock with biometric" switch. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** The "Automatically lock" choice. */
    val timeout: StateFlow<AutoLockTimeout> = _timeout.asStateFlow()

    /** True while the lock screen must cover the app. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var initialized = false

    /** SystemClock.elapsedRealtime() when the app left the screen. 0 = it did not leave. */
    private var leftAt = 0L

    /** True while the system unlock dialog is on screen, so it is never opened twice. */
    @Volatile
    var promptShowing = false

    /**
     * False after the user cancelled the unlock dialog. The dialog then does not pop up again
     * by itself until the app has been away once more. The Unlock button still works.
     */
    @Volatile
    var autoPromptAllowed = true

    /** Reads the saved settings once per app process. A cold start with the lock on is locked. */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.applicationContext.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
        _timeout.value = AutoLockTimeout.fromStorage(prefs.getString(KEY_TIMEOUT, null))
        _locked.value = _enabled.value
    }

    fun setEnabled(context: Context, value: Boolean) {
        context.applicationContext.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
        // Turning it on happens while the app is open and the user has just proved who they are
        _locked.value = false
        leftAt = 0L
    }

    fun setTimeout(context: Context, value: AutoLockTimeout) {
        context.applicationContext.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_TIMEOUT, value.storageValue).apply()
        _timeout.value = value
    }

    /** The app left the screen (Lifecycle ON_STOP). */
    fun onAppStopped() {
        leftAt = SystemClock.elapsedRealtime()
        autoPromptAllowed = true
    }

    /** The app came back (Lifecycle ON_START). Locks it if it was away long enough. */
    fun onAppStarted() {
        val left = leftAt
        if (left == 0L) return
        leftAt = 0L
        if (!_enabled.value || _locked.value) return
        val away = SystemClock.elapsedRealtime() - left
        if (shouldLockAfterAway(away, _timeout.value)) _locked.value = true
    }

    fun unlock() {
        leftAt = 0L
        _locked.value = false
    }
}
