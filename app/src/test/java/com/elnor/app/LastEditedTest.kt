package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastEditedTest {

    private val fakeFormat: (Long) -> String = { "time-$it" }

    @Test
    fun aSavedNote_showsItsTime() {
        assertEquals("Last edited time-1234", lastEditedLabel(1234L, fakeFormat))
    }

    @Test
    fun aNoteThatWasNeverSaved_showsNothing() {
        assertNull(lastEditedLabel(null, fakeFormat))
    }

    @Test
    fun aMissingTime_showsNothing() {
        assertNull(lastEditedLabel(0L, fakeFormat))
        assertNull(lastEditedLabel(-5L, fakeFormat))
    }
}
