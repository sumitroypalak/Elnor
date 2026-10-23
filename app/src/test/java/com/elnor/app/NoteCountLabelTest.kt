package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteCountLabelTest {
    @Test
    fun one_isSingular() = assertEquals("1 note", notesCountLabel(1))

    @Test
    fun zero_isPlural() = assertEquals("0 notes", notesCountLabel(0))

    @Test
    fun many_isPlural() {
        assertEquals("2 notes", notesCountLabel(2))
        assertEquals("120 notes", notesCountLabel(120))
    }
}
