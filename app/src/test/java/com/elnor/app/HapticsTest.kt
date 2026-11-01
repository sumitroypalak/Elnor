package com.elnor.app

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Haptics: which vibration each kind uses, and the on / off switch. No phone is needed. */
class HapticsTest {

    private val android10 = 29
    private val android11 = 30
    private val android15 = 35

    @Test
    fun theFourKindsExist() {
        assertEquals(
            listOf(HapticKind.TICK, HapticKind.CONFIRM, HapticKind.LONG_PRESS, HapticKind.REJECT),
            HapticKind.entries.toList()
        )
    }

    @Test
    fun tickAndLongPressAreTheSameOnEveryAndroid() {
        for (sdk in listOf(26, android10, android11, android15)) {
            assertEquals(HapticFeedbackConstants.CLOCK_TICK, hapticConstantFor(HapticKind.TICK, sdk))
            assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstantFor(HapticKind.LONG_PRESS, sdk))
        }
    }

    @Test
    fun confirmUsesTheNewTickFromAndroid11_andAnOlderOneBefore() {
        assertEquals(HapticFeedbackConstants.CONFIRM, hapticConstantFor(HapticKind.CONFIRM, android11))
        assertEquals(HapticFeedbackConstants.CONFIRM, hapticConstantFor(HapticKind.CONFIRM, android15))
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, hapticConstantFor(HapticKind.CONFIRM, android10))
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, hapticConstantFor(HapticKind.CONFIRM, 26))
    }

    @Test
    fun rejectUsesTheNewTickFromAndroid11_andALongPressBefore() {
        assertEquals(HapticFeedbackConstants.REJECT, hapticConstantFor(HapticKind.REJECT, android11))
        assertEquals(HapticFeedbackConstants.REJECT, hapticConstantFor(HapticKind.REJECT, android15))
        assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstantFor(HapticKind.REJECT, android10))
    }

    @Test
    fun onAndroid11OrNewer_everyKindFeelsDifferent() {
        val constants = HapticKind.entries.map { hapticConstantFor(it, android11) }
        assertEquals(constants.size, constants.toSet().size)
    }

    @Test
    fun confirmAndRejectAreNeverTheSame() {
        for (sdk in listOf(26, android10, android11, android15)) {
            assertNotEquals(
                hapticConstantFor(HapticKind.CONFIRM, sdk),
                hapticConstantFor(HapticKind.REJECT, sdk)
            )
        }
    }

    @Test
    fun switchedOff_nothingIsPerformed() {
        for (kind in HapticKind.entries) {
            assertNull(hapticConstantOrNull(kind, android11, enabled = false))
        }
    }

    @Test
    fun switchedOn_theConstantIsGiven() {
        for (kind in HapticKind.entries) {
            assertEquals(
                hapticConstantFor(kind, android11),
                hapticConstantOrNull(kind, android11, enabled = true)
            )
        }
    }

    @Test
    fun theSettingIsOnByDefault() {
        assertTrue(HapticSettings.enabled)
    }
}
