package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingTextTest {

    @Test
    fun textOnly_becomesTheBody_withoutTitle() {
        assertEquals(IncomingNote("", "hello"), buildIncomingNote(null, "  hello \n"))
    }

    @Test
    fun subject_becomesTheTitle() {
        assertEquals(
            IncomingNote("Page title", "https://example.com"),
            buildIncomingNote(" Page title ", "https://example.com")
        )
    }

    @Test
    fun subjectSameAsText_isNotSavedTwice() {
        assertEquals(IncomingNote("", "same"), buildIncomingNote("same", "same"))
    }

    @Test
    fun nothingOrBlank_givesNull() {
        assertNull(buildIncomingNote(null, null))
        assertNull(buildIncomingNote("  ", "\n \t"))
    }

    @Test
    fun subjectWithoutText_keepsTheTitle() {
        assertEquals(IncomingNote("Only title", ""), buildIncomingNote("Only title", null))
    }

    @Test
    fun bengaliAndLineBreaksInsideTheTextStay() {
        assertEquals(
            IncomingNote("", "আমার নোট\nদ্বিতীয় লাইন"),
            buildIncomingNote(null, "আমার নোট\nদ্বিতীয় লাইন")
        )
    }
}
