package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkSelectionTest {

    @Test
    fun toggleAddsAnIdThatIsNotSelected() {
        assertEquals(setOf(1L, 2L), toggleSelection(setOf(1L), 2L))
    }

    @Test
    fun toggleRemovesAnIdThatIsSelected() {
        assertEquals(setOf(1L), toggleSelection(setOf(1L, 2L), 2L))
    }

    @Test
    fun toggleTheLastIdEndsTheSelection() {
        assertTrue(toggleSelection(setOf(5L), 5L).isEmpty())
    }

    @Test
    fun restoredMessageUsesSingularAndPlural() {
        assertEquals("Note restored", restoredMessage(1))
        assertEquals("3 notes restored", restoredMessage(3))
    }

    @Test
    fun unarchivedMessageUsesSingularAndPlural() {
        assertEquals("Note unarchived", unarchivedMessage(1))
        assertEquals("4 notes unarchived", unarchivedMessage(4))
    }

    @Test
    fun deleteForeverQuestionUsesSingularAndPlural() {
        assertEquals(
            "This note will be permanently deleted. This cannot be undone.",
            deleteForeverQuestion(1)
        )
        assertEquals(
            "These 2 notes will be permanently deleted. This cannot be undone.",
            deleteForeverQuestion(2)
        )
    }
}
