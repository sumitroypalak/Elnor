package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** The message shown with "Undo" after a task is checked off. */
class TaskUndoTest {

    @Test
    fun messageHasTheTaskTitle() {
        assertEquals("\"Buy milk\" completed", taskCompletedMessage("Buy milk"))
    }

    @Test
    fun spacesAroundTheTitleAreRemoved() {
        assertEquals("\"Buy milk\" completed", taskCompletedMessage("  Buy milk \n"))
    }

    @Test
    fun blankTitleGivesAPlainMessage() {
        assertEquals("Task completed", taskCompletedMessage(""))
        assertEquals("Task completed", taskCompletedMessage("   "))
    }

    @Test
    fun bengaliTitleIsKeptAsItIs() {
        assertEquals("\"দুধ কিনতে হবে\" completed", taskCompletedMessage("দুধ কিনতে হবে"))
    }
}
