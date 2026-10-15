package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLockTimeoutTest {

    @Test
    fun immediately_locksEvenAfterZeroMilliseconds() {
        assertTrue(shouldLockAfterAway(0L, AutoLockTimeout.IMMEDIATELY))
        assertTrue(shouldLockAfterAway(5_000L, AutoLockTimeout.IMMEDIATELY))
    }

    @Test
    fun oneMinute_locksOnlyFromOneMinute() {
        assertFalse(shouldLockAfterAway(59_999L, AutoLockTimeout.ONE_MINUTE))
        assertTrue(shouldLockAfterAway(60_000L, AutoLockTimeout.ONE_MINUTE))
    }

    @Test
    fun thirtyMinutes_locksOnlyFromThirtyMinutes() {
        assertFalse(shouldLockAfterAway(29L * 60_000L, AutoLockTimeout.THIRTY_MINUTES))
        assertTrue(shouldLockAfterAway(30L * 60_000L, AutoLockTimeout.THIRTY_MINUTES))
    }

    @Test
    fun fromStorage_roundTripsAndDefaultsToImmediately() {
        for (option in AutoLockTimeout.entries) {
            assertEquals(option, AutoLockTimeout.fromStorage(option.storageValue))
        }
        assertEquals(AutoLockTimeout.IMMEDIATELY, AutoLockTimeout.fromStorage(null))
        assertEquals(AutoLockTimeout.IMMEDIATELY, AutoLockTimeout.fromStorage("unknown"))
    }

    @Test
    fun coldStart_respectsTheChosenTime() {
        val left = 1_000_000L
        // away 30 seconds
        assertFalse(shouldLockOnColdStart(left, left + 30_000L, AutoLockTimeout.ONE_MINUTE))
        assertFalse(shouldLockOnColdStart(left, left + 30_000L, AutoLockTimeout.THIRTY_MINUTES))
        assertTrue(shouldLockOnColdStart(left, left + 30_000L, AutoLockTimeout.IMMEDIATELY))
        // away 5 minutes
        assertTrue(shouldLockOnColdStart(left, left + 5L * 60_000L, AutoLockTimeout.ONE_MINUTE))
        assertFalse(shouldLockOnColdStart(left, left + 5L * 60_000L, AutoLockTimeout.THIRTY_MINUTES))
        // away 30 minutes
        assertTrue(shouldLockOnColdStart(left, left + 30L * 60_000L, AutoLockTimeout.THIRTY_MINUTES))
    }

    @Test
    fun coldStart_locksWhenNothingWasSavedOrTheClockWentBack() {
        assertTrue(shouldLockOnColdStart(0L, 5_000_000L, AutoLockTimeout.THIRTY_MINUTES))
        assertTrue(shouldLockOnColdStart(2_000_000L, 1_000_000L, AutoLockTimeout.THIRTY_MINUTES))
    }
}
