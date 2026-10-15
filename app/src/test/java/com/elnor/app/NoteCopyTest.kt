package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteCopyTest {

    @Test
    fun titleAndBody_areJoinedByAnEmptyLine() {
        assertEquals("Shopping\n\nmilk\neggs", noteTextForCopy("Shopping", "milk\neggs"))
    }

    @Test
    fun aMissingTitle_givesOnlyTheBody() {
        assertEquals("milk", noteTextForCopy("", "milk"))
        assertEquals("milk", noteTextForCopy("   ", "milk"))
    }

    @Test
    fun aMissingBody_givesOnlyTheTitle() {
        assertEquals("Shopping", noteTextForCopy("Shopping", ""))
        assertEquals("Shopping", noteTextForCopy("Shopping", " \n "))
    }

    @Test
    fun spacesAtTheEnds_areRemoved_butLinesInsideAreKept() {
        assertEquals("T\n\na\n\n  b", noteTextForCopy("  T ", "\n a\n\n  b \n"))
    }

    @Test
    fun nothingWritten_givesAnEmptyText() {
        assertEquals("", noteTextForCopy("", ""))
        assertEquals("", noteTextForCopy("  ", "\n"))
    }
}
