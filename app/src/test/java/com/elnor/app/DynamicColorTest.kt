package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dynamic colour: which phones can do it, and when the app really uses it. */
class DynamicColorTest {

    @Test
    fun theFirstSupportedVersionIsAndroid12() {
        assertEquals(31, DYNAMIC_COLOR_MIN_SDK)
    }

    @Test
    fun androidBefore12CannotDoIt() {
        // the app's minSdk is 26 (Android 8)
        for (sdk in listOf(26, 28, 29, 30)) {
            assertFalse("API $sdk", supportsDynamicColor(sdk))
        }
    }

    @Test
    fun android12AndNewerCanDoIt() {
        for (sdk in listOf(31, 32, 33, 34, 35, 36)) {
            assertTrue("API $sdk", supportsDynamicColor(sdk))
        }
    }

    @Test
    fun activeNeedsTheSwitchAndAPhoneThatCanDoIt() {
        assertTrue(dynamicColorActive(enabled = true, sdkInt = 34))
        assertFalse(dynamicColorActive(enabled = false, sdkInt = 34))
        assertFalse(dynamicColorActive(enabled = false, sdkInt = 30))
    }

    @Test
    fun aSavedOnOnAPhoneThatCannotIsIgnored() {
        assertFalse(dynamicColorActive(enabled = true, sdkInt = 30))
        assertFalse(dynamicColorActive(enabled = true, sdkInt = 26))
    }
}
