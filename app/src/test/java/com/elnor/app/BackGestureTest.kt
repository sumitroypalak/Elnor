package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The progress of a Back gesture (predictive back). The screens that follow the finger only read
 * [BackGesture.active] and [BackGesture.progress], so these must be right.
 */
class BackGestureTest {

    @Test
    fun startsQuiet() {
        val gesture = BackGesture()
        assertFalse(gesture.active)
        assertEquals(0f, gesture.progress, 0f)
    }

    @Test
    fun update_makesItActive_andKeepsTheProgress() {
        val gesture = BackGesture()
        gesture.update(0.3f)
        assertTrue(gesture.active)
        assertEquals(0.3f, gesture.progress, 0f)
        gesture.update(0.8f)
        assertEquals(0.8f, gesture.progress, 0f)
    }

    @Test
    fun end_stopsTheGesture_butKeepsWhereTheFingerWas() {
        // The transition goes on from there, so the screen does not jump back first
        val gesture = BackGesture()
        gesture.update(0.6f)
        gesture.end()
        assertFalse(gesture.active)
        assertEquals(0.6f, gesture.progress, 0f)
    }

    @Test
    fun cancel_stopsTheGesture_andGoesBackToTheStart() {
        val gesture = BackGesture()
        gesture.update(0.6f)
        gesture.cancel()
        assertFalse(gesture.active)
        assertEquals(0f, gesture.progress, 0f)
    }
}
