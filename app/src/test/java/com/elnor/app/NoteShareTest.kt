package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteShareTest {

    @Test
    fun theSubject_isTheTitleWithoutSpacesAtTheEnds() {
        assertEquals("Shopping", shareSubjectOf("  Shopping "))
    }

    @Test
    fun noTitle_meansNoSubject() {
        assertNull(shareSubjectOf(""))
        assertNull(shareSubjectOf("   \n"))
    }
}
