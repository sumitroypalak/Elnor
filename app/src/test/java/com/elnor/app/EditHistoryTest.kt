package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {

    private var time = 0L
    private fun history(initial: EditSnapshot = EditSnapshot("", ""), maxSteps: Int = 100) =
        EditHistory(initial, now = { time }, maxSteps = maxSteps)

    /** Types [text] one letter at a time into the body, [gap] milliseconds apart. */
    private fun EditHistory.type(from: String, text: String, title: String = "", gap: Long = 50L): String {
        var body = from
        for (ch in text) {
            body += ch
            time += gap
            record(EditSnapshot(title, body))
        }
        return body
    }

    @Test
    fun aNewHistory_hasNothingToUndoOrRedo() {
        val h = history()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertNull(h.undo())
        assertNull(h.redo())
    }

    @Test
    fun lettersTypedOneAfterTheOther_areOneStep() {
        val h = history()
        h.type("", "hello")
        assertTrue(h.canUndo)
        assertEquals(EditSnapshot("", ""), h.undo())
        assertFalse(h.canUndo)
    }

    @Test
    fun aPause_startsANewStep() {
        val h = history()
        val first = h.type("", "abc")
        time += 5_000L
        h.type(first, "def")
        assertEquals("abc", h.undo()?.body)
        assertEquals("", h.undo()?.body)
        assertNull(h.undo())
    }

    @Test
    fun aSpace_endsTheStep_soUndoTakesBackOneWordAtATime() {
        val h = history()
        h.type("", "hello world")
        assertEquals("hello ", h.undo()?.body)
        assertEquals("", h.undo()?.body)
        assertNull(h.undo())
    }

    @Test
    fun typingThenDeleting_areDifferentSteps() {
        val h = history()
        val typed = h.type("", "abc")
        time += 50L
        h.record(EditSnapshot("", typed.dropLast(1)))
        assertEquals("abc", h.undo()?.body)
        assertEquals("", h.undo()?.body)
    }

    @Test
    fun deletedLettersOneAfterTheOther_areOneStep() {
        val h = history(EditSnapshot("", "abcd"))
        time += 50L; h.record(EditSnapshot("", "abc"))
        time += 50L; h.record(EditSnapshot("", "ab"))
        time += 50L; h.record(EditSnapshot("", "a"))
        assertEquals("abcd", h.undo()?.body)
        assertFalse(h.canUndo)
    }

    @Test
    fun aPasteIsAlwaysAStepOfItsOwn() {
        val h = history(EditSnapshot("", "a"))
        time += 10L; h.record(EditSnapshot("", "a" + "paste1"))
        time += 10L; h.record(EditSnapshot("", "apaste1" + "paste2"))
        assertEquals("apaste1", h.undo()?.body)
        assertEquals("a", h.undo()?.body)
    }

    @Test
    fun theTitleAndTheText_areSeparateSteps() {
        val h = history()
        time += 10L; h.record(EditSnapshot("T", ""))
        time += 10L; h.record(EditSnapshot("T", "b"))
        assertEquals(EditSnapshot("T", ""), h.undo())
        assertEquals(EditSnapshot("", ""), h.undo())
    }

    @Test
    fun redo_bringsTheStepBack_andANewChangeClearsIt() {
        val h = history()
        h.type("", "abc")
        h.undo()
        assertTrue(h.canRedo)
        assertEquals("abc", h.redo()?.body)
        assertFalse(h.canRedo)

        h.undo()
        assertTrue(h.canRedo)
        time += 10L
        h.record(EditSnapshot("", "x"))
        assertFalse(h.canRedo)
        assertNull(h.redo())
    }

    @Test
    fun undoAndRedoGoBackAndForthThroughSeveralSteps() {
        val h = history()
        val a = h.type("", "one ")
        val b = h.type(a, "two ")
        h.type(b, "three")
        assertEquals("one two ", h.undo()?.body)
        assertEquals("one ", h.undo()?.body)
        assertEquals("", h.undo()?.body)
        assertEquals("one ", h.redo()?.body)
        assertEquals("one two ", h.redo()?.body)
        assertEquals("one two three", h.redo()?.body)
        assertNull(h.redo())
    }

    @Test
    fun movingOnlyTheCursor_isNotAStep_butUndoGivesBackThatCursor() {
        val h = history(EditSnapshot("", "hello", 5, 5))
        h.record(EditSnapshot("", "hello", 2, 2)) // the cursor moved
        assertFalse(h.canUndo)

        time += 10L
        h.record(EditSnapshot("", "heXllo", 3, 3)) // typed a letter at the cursor
        assertEquals(EditSnapshot("", "hello", 2, 2), h.undo())
    }

    @Test
    fun onlyTheNewestStepsAreKept() {
        val h = history(maxSteps = 3)
        var body = ""
        repeat(6) { i ->
            time += 10_000L // a pause, so every letter is its own step
            body += ('a' + i)
            h.record(EditSnapshot("", body))
        }
        var undone = 0
        while (h.undo() != null) undone++
        assertEquals(3, undone)
    }
}
