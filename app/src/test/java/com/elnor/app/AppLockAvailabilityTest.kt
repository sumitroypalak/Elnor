package com.elnor.app

import androidx.biometric.BiometricManager
import org.junit.Assert.assertEquals
import org.junit.Test

/** Only "nothing is set up" may turn the App lock off. A passing error must keep it on. */
class AppLockAvailabilityTest {

    @Test
    fun success_isAvailable() {
        assertEquals(
            AppLockAuth.Availability.AVAILABLE,
            AppLockAuth.availabilityFor(BiometricManager.BIOMETRIC_SUCCESS)
        )
    }

    @Test
    fun noneEnrolled_isPermanent() {
        assertEquals(
            AppLockAuth.Availability.PERMANENTLY_UNAVAILABLE,
            AppLockAuth.availabilityFor(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)
        )
    }

    @Test
    fun noHardware_isPermanent() {
        assertEquals(
            AppLockAuth.Availability.PERMANENTLY_UNAVAILABLE,
            AppLockAuth.availabilityFor(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)
        )
    }

    @Test
    fun passingErrors_areTemporary() {
        val passing = listOf(
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
            -12345 // a code this version does not know
        )
        for (code in passing) {
            assertEquals(
                "code $code",
                AppLockAuth.Availability.TEMPORARILY_UNAVAILABLE,
                AppLockAuth.availabilityFor(code)
            )
        }
    }
}
