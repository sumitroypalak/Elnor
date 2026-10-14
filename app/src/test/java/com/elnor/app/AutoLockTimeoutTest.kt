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
}
