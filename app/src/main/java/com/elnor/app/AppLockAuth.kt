package com.elnor.app

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Unlocking uses the phone's own system dialog, nothing of ours:
 *  - fingerprint, face or other biometrics, if the phone has them set up, and
 *  - the phone's screen-lock PIN, pattern or password, as the fallback (the dialog offers it
 *    by itself). A phone whose only lock is a PIN works too.
 *
 * If the phone has no screen lock and no biometrics at all, nothing can be checked. Then the app
 * lock cannot be turned on, and the user is sent to the phone's security settings.
 */
object AppLockAuth {

    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** True when the phone has a screen lock or a biometric that the dialog can ask for. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** Whether the phone can ask the user to unlock right now, and if not, for how long. */
    enum class Availability {
        /** The system dialog can be shown. */
        AVAILABLE,

        /** The phone has no screen lock / biometric (any more). Nothing is left to ask for. */
        PERMANENTLY_UNAVAILABLE,

        /** A passing problem (sensor busy, security update pending ...). The lock must stay on. */
        TEMPORARILY_UNAVAILABLE
    }

    /** Maps the result of canAuthenticate() so a passing error never turns the App lock off. */
    fun availability(context: Context): Availability =
        availabilityFor(BiometricManager.from(context).canAuthenticate(AUTHENTICATORS))

    /** Only "nothing is set up" is permanent. Every other error is treated as temporary. */
    internal fun availabilityFor(canAuthenticateResult: Int): Availability = when (canAuthenticateResult) {
        BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Availability.PERMANENTLY_UNAVAILABLE
        else -> Availability.TEMPORARILY_UNAVAILABLE
    }

    /**
     * Shows the system dialog. [onSuccess] runs only when the user proved who they are.
     * [onError] runs when the dialog was closed or could not be shown; [userCancelled] is true
     * when the user chose to close it.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        onSuccess: () -> Unit,
        onError: (userCancelled: Boolean) -> Unit = {}
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onError(cancelled)
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS) // no negative button when DEVICE_CREDENTIAL is allowed
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}

/** The FragmentActivity behind a Compose context, or null (for example in a preview). */
fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
