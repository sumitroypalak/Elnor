package com.elnor.app

import android.content.Context
import android.content.SharedPreferences
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
 * How long after Elnor itself opened another screen (the Share sheet, the file picker of the
 * backup) coming back does not lock the app, even with "Immediately". Longer than that, the
 * normal rule applies again, so a phone that was left in the picker does not stay open for good.
 */
const val EXTERNAL_SCREEN_GRACE_MS = 2L * 60_000L

/**
 * Should the app be locked when it comes back to the screen?
 *
 * [externalScreenAgeMillis] is how long ago Elnor opened its own Share sheet or file picker (see
 * [AppLock.expectExternalScreen]), or null if it did not. Coming back from such a screen within
 * [EXTERNAL_SCREEN_GRACE_MS] does not lock: the user never really left Elnor. In every other
 * case the usual "Automatically lock" rule decides.
 */
fun shouldLockOnReturn(
    awayMillis: Long,
    timeout: AutoLockTimeout,
    externalScreenAgeMillis: Long?
): Boolean {
    if (externalScreenAgeMillis != null && externalScreenAgeMillis in 0L..EXTERNAL_SCREEN_GRACE_MS) {
        return false
    }
    return shouldLockAfterAway(awayMillis, timeout)
}

/**
 * Should the app be locked when the process starts from nothing (the system closed it, or the
 * user swiped it away)? [leftAtMillis] is the wall-clock time saved when the app left the screen.
 *
 * It locks when nothing was saved or the app was still on screen (0: it was closed while open, or
 * while it was already locked), when the clock went backwards (a negative time away cannot be
 * trusted), and when the app was away at least as long as [timeout].
 */
fun shouldLockOnColdStart(leftAtMillis: Long, nowMillis: Long, timeout: AutoLockTimeout): Boolean {
    if (leftAtMillis <= 0L) return true
    val away = nowMillis - leftAtMillis
    if (away < 0L) return true
    return shouldLockAfterAway(away, timeout)
}

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

    /**
     * Wall-clock time (milliseconds since 1970) when the app left the screen. It is saved on the
     * phone because the process can be closed while the app is in the background, and the
     * "Automatically lock" time must still count then. 0 means: on screen, or locked.
     */
    private const val KEY_LEFT_AT = "app_lock_left_at"

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
    private var prefs: SharedPreferences? = null

    /** SystemClock.elapsedRealtime() when the app left the screen. 0 = it did not leave. */
    private var leftAt = 0L

    /**
     * SystemClock.elapsedRealtime() when Elnor opened its own Share sheet or file picker, or 0.
     * It is only kept in memory: if the process is closed meanwhile, the next start follows the
     * normal rules (locked), see [shouldLockOnColdStart].
     */
    private var externalScreenAt = 0L

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
        this.prefs = prefs
        _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
        _timeout.value = AutoLockTimeout.fromStorage(prefs.getString(KEY_TIMEOUT, null))
        // A start from nothing is locked only if the app was away at least as long as the chosen
        // "Automatically lock" time. (Before, it was always locked, so only "Immediately" worked.)
        _locked.value = _enabled.value && shouldLockOnColdStart(
            leftAtMillis = prefs.getLong(KEY_LEFT_AT, 0L),
            nowMillis = System.currentTimeMillis(),
            timeout = _timeout.value
        )
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

    /**
     * Call right before Elnor opens another screen itself (the Share sheet, the backup file
     * picker). The app then goes to the background for a moment; coming back from that screen
     * must not ask for the fingerprint again, also not with "Immediately".
     * The exception is in [shouldLockOnReturn].
     */
    fun expectExternalScreen() {
        externalScreenAt = SystemClock.elapsedRealtime()
    }

    /** The app left the screen (Lifecycle ON_STOP). */
    fun onAppStopped() {
        leftAt = SystemClock.elapsedRealtime()
        autoPromptAllowed = true
        // Saved for the case that the process is closed while the app is away. If the app is
        // locked right now, 0 is saved: it must stay locked, whatever the time.
        prefs?.edit()?.putLong(KEY_LEFT_AT, if (_locked.value) 0L else System.currentTimeMillis())?.apply()
    }

    /** The app came back (Lifecycle ON_START). Locks it if it was away long enough. */
    fun onAppStarted() {
        // On screen again: if the process is closed from now on, the next start must be locked
        prefs?.edit()?.putLong(KEY_LEFT_AT, 0L)?.apply()
        // Used up by this return, whatever happens next
        val externalAt = externalScreenAt
        externalScreenAt = 0L
        val left = leftAt
        if (left == 0L) return
        leftAt = 0L
        if (!_enabled.value || _locked.value) return
        val now = SystemClock.elapsedRealtime()
        val externalAge = if (externalAt == 0L) null else now - externalAt
        if (shouldLockOnReturn(now - left, _timeout.value, externalAge)) _locked.value = true
    }

    fun unlock() {
        leftAt = 0L
        _locked.value = false
    }
}
