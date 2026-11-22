package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NotePrintTest {

    @Test
    fun jobName_isTheTitle_withoutExtension() {
        assertEquals("Shopping list", NotePrint.jobName("Shopping list", "milk"))
    }

    @Test
    fun jobName_usesTheFirstLine_whenThereIsNoTitle() {
        assertEquals("Buy milk", NotePrint.jobName("", "Buy milk\nand eggs"))
    }

    @Test
    fun jobName_keepsBengali() {
        assertEquals("বাজার তালিকা", NotePrint.jobName("বাজার তালিকা", ""))
    }

    @Test
    fun jobName_isNeverEmpty() {
        assertEquals("Untitled note", NotePrint.jobName("", ""))
    }

    @Test
    fun markers_matchTheListKinds() {
        fun m(line: String) = NotePrint.marker(MarkdownFormat.blockOf(line))
        assertEquals("• ", m("- item"))
        assertEquals("3. ", m("3. item"))
        assertEquals("[ ] ", m("- [ ] todo"))
        assertEquals("[x] ", m("- [x] done"))
        assertEquals("", m("# Heading"))
        assertEquals("", m("plain"))
    }
}
