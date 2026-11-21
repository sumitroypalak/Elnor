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

    @Test
    fun update_remembersTheEdge_andHowFarTheFingerWentUpOrDown() {
        val gesture = BackGesture()
        gesture.update(0.1f, rightEdge = true, touchY = 500f) // the start
        assertTrue(gesture.rightEdge)
        assertEquals(0f, gesture.dy, 0f)
        gesture.update(0.4f, rightEdge = true, touchY = 380f) // 120 px up
        assertEquals(-120f, gesture.dy, 0f)
        gesture.update(0.5f, rightEdge = true, touchY = 560f) // 60 px down from the start
        assertEquals(60f, gesture.dy, 0f)
    }

    @Test
    fun aNewGesture_startsFromItsOwnFingerPosition() {
        val gesture = BackGesture()
        gesture.update(0.5f, touchY = 100f)
        gesture.update(0.6f, touchY = 300f)
        gesture.end()
        gesture.update(0.1f, touchY = 900f) // the next gesture
        assertEquals(0f, gesture.dy, 0f)
    }

    @Test
    fun end_keepsTheFingerHeight_untilSettled() {
        val gesture = BackGesture()
        gesture.update(0.5f, touchY = 100f)
        gesture.update(0.7f, touchY = 40f)
        gesture.end()
        assertEquals(-60f, gesture.dy, 0f) // the closing animation goes on from here
        gesture.settle()
        assertEquals(0f, gesture.dy, 0f)
        assertEquals(0f, gesture.progress, 0f)
    }

    @Test
    fun cancel_forgetsTheFingerHeight() {
        val gesture = BackGesture()
        gesture.update(0.5f, touchY = 100f)
        gesture.update(0.7f, touchY = 40f)
        gesture.cancel()
        assertEquals(0f, gesture.dy, 0f)
    }
}
