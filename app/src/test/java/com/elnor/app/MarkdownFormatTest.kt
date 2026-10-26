package com.elnor.app

import com.elnor.app.MarkdownFormat.Kind
import com.elnor.app.MarkdownFormat.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownFormatTest {

    private fun edit(text: String, start: Int, end: Int) = TextEdit(text, start, end)

    // ---- parse: what is shown styled ----

    @Test
    fun parse_heading() {
        assertEquals(
            listOf(Span(0, 2, Kind.MARKER), Span(2, 7, Kind.HEADING1)),
            MarkdownFormat.parse("# Title")
        )
        assertEquals(
            listOf(Span(0, 3, Kind.MARKER), Span(3, 6, Kind.HEADING2)),
            MarkdownFormat.parse("## Sub")
        )
    }

    @Test
    fun parse_lists() {
        assertEquals(listOf(Span(0, 2, Kind.LIST_MARKER)), MarkdownFormat.parse("- item"))
        assertEquals(listOf(Span(0, 2, Kind.LIST_MARKER)), MarkdownFormat.parse("* item"))
        assertEquals(listOf(Span(2, 4, Kind.LIST_MARKER)), MarkdownFormat.parse("  - item"))
        assertEquals(listOf(Span(0, 3, Kind.LIST_MARKER)), MarkdownFormat.parse("1. one"))
    }

    @Test
    fun parse_bold() {
        assertEquals(
            listOf(Span(2, 4, Kind.MARKER), Span(4, 8, Kind.BOLD), Span(8, 10, Kind.MARKER)),
            MarkdownFormat.parse("a **bold** b")
        )
    }

    @Test
    fun parse_boldInsideAHeading() {
        assertEquals(
            listOf(
                Span(0, 2, Kind.MARKER),
                Span(2, 7, Kind.HEADING1),
                Span(2, 4, Kind.MARKER),
                Span(4, 5, Kind.BOLD),
                Span(5, 7, Kind.MARKER)
            ),
            MarkdownFormat.parse("# **b**")
        )
    }

    @Test
    fun parse_offsetsOfLaterLinesAreInTheWholeText() {
        assertEquals(
            listOf(Span(2, 4, Kind.MARKER), Span(4, 5, Kind.HEADING1)),
            MarkdownFormat.parse("x\n# H")
        )
    }

    @Test
    fun parse_leavesNormalTextAlone() {
        assertTrue(MarkdownFormat.parse("").isEmpty())
        assertTrue(MarkdownFormat.parse("just text\n\nmore text").isEmpty())
        assertTrue(MarkdownFormat.parse("#nospace -notalist 5 * 3 * 2").isEmpty())
        assertTrue(MarkdownFormat.parse("** not bold **").isEmpty())
        assertTrue(MarkdownFormat.parse("****").isEmpty())
        assertTrue(MarkdownFormat.parse("আমার নোট, কোনো ফরম্যাট নেই।").isEmpty())
    }

    @Test
    fun parse_endsWithALineBreak() {
        assertEquals(listOf(Span(0, 2, Kind.LIST_MARKER)), MarkdownFormat.parse("- a\n"))
    }

    // ---- Bold button ----

    @Test
    fun bold_wrapsTheSelection() {
        assertEquals(edit("**hello** world", 2, 7), MarkdownFormat.toggleBold("hello world", 0, 5))
    }

    @Test
    fun bold_takesAwayMarksOutsideTheSelection() {
        assertEquals(edit("hello world", 0, 5), MarkdownFormat.toggleBold("**hello** world", 2, 7))
    }

    @Test
    fun bold_takesAwayMarksInsideTheSelection() {
        assertEquals(edit("hello world", 0, 5), MarkdownFormat.toggleBold("**hello** world", 0, 9))
    }

    @Test
    fun bold_leavesSpacesAtTheEdgesOfTheSelectionOutside() {
        assertEquals(edit("a **hello** b", 4, 9), MarkdownFormat.toggleBold("a hello b", 1, 8))
    }

    @Test
    fun bold_withoutSelection_boldsTheWordAtTheCursor() {
        assertEquals(edit("**hello** world", 2, 7), MarkdownFormat.toggleBold("hello world", 2, 2))
        // and takes the marks away again when the cursor is in a bold word
        assertEquals(edit("hello world", 0, 5), MarkdownFormat.toggleBold("**hello** world", 4, 4))
    }

    @Test
    fun bold_withoutSelection_notOnAWord_putsEmptyMarksWithTheCursorInside() {
        assertEquals(edit("****", 2, 2), MarkdownFormat.toggleBold("", 0, 0))
        assertEquals(edit("a **** b", 4, 4), MarkdownFormat.toggleBold("a  b", 2, 2))
    }

    @Test
    fun bold_worksForBengali() {
        assertEquals(edit("**আমার** নোট", 2, 6), MarkdownFormat.toggleBold("আমার নোট", 0, 4))
    }

    // ---- Heading button ----

    @Test
    fun heading_cyclesPlainToBigToSmallToPlain() {
        assertEquals(edit("# hello", 4, 4), MarkdownFormat.cycleHeading("hello", 2, 2))
        assertEquals(edit("## hello", 5, 5), MarkdownFormat.cycleHeading("# hello", 4, 4))
        assertEquals(edit("hello", 2, 2), MarkdownFormat.cycleHeading("## hello", 5, 5))
    }

    @Test
    fun heading_replacesAListMark() {
        assertEquals(edit("# item", 0, 0), MarkdownFormat.cycleHeading("- item", 0, 0))
    }

    @Test
    fun heading_onlyTheLineOfTheCursor() {
        assertEquals(edit("a\n# b", 5, 5), MarkdownFormat.cycleHeading("a\nb", 3, 3))
    }

    @Test
    fun heading_everySelectedLine_blankLinesStayBlank() {
        assertEquals(edit("# a\n# b", 0, 7), MarkdownFormat.cycleHeading("a\nb", 0, 3))
        assertEquals(edit("# a\n\n# b", 0, 8), MarkdownFormat.cycleHeading("a\n\nb", 0, 4))
    }

    // ---- List buttons ----

    @Test
    fun bullet_addsAndRemovesTheMark() {
        assertEquals(edit("- one\n- two", 0, 11), MarkdownFormat.toggleBullet("one\ntwo", 0, 7))
        assertEquals(edit("one\ntwo", 0, 7), MarkdownFormat.toggleBullet("- one\n- two", 0, 11))
    }

    @Test
    fun bullet_aMixOfLinesGetsBulletsEverywhereWithoutDoubleMarks() {
        assertEquals(edit("- one\n- two", 0, 11), MarkdownFormat.toggleBullet("- one\ntwo", 0, 9))
    }

    @Test
    fun bullet_replacesANumber_andKeepsTheIndent() {
        assertEquals(edit("- a", 0, 0), MarkdownFormat.toggleBullet("1. a", 0, 0))
        assertEquals(edit("  - x", 4, 4), MarkdownFormat.toggleBullet("  x", 2, 2))
    }

    @Test
    fun bullet_aSelectionThatEndsAfterALineBreakDoesNotTakeTheNextLine() {
        assertEquals(edit("- a\nb\n", 0, 3), MarkdownFormat.toggleBullet("a\nb\n", 0, 2))
    }

    @Test
    fun numbered_numbersTheLines_skippingBlankOnes() {
        assertEquals(edit("1. a\n2. b\n3. c", 0, 14), MarkdownFormat.toggleNumbered("a\nb\nc", 0, 5))
        assertEquals(edit("1. a\n\n2. b", 0, 10), MarkdownFormat.toggleNumbered("a\n\nb", 0, 4))
    }

    @Test
    fun numbered_removesTheNumbers_andReplacesBullets() {
        assertEquals(edit("a\nb", 0, 3), MarkdownFormat.toggleNumbered("1. a\n2. b", 0, 9))
        assertEquals(edit("1. a\n2. b", 0, 9), MarkdownFormat.toggleNumbered("- a\n- b", 0, 7))
    }

    @Test
    fun buttons_workOnAnEmptyNote() {
        assertEquals(edit("- ", 2, 2), MarkdownFormat.toggleBullet("", 0, 0))
        assertEquals(edit("1. ", 3, 3), MarkdownFormat.toggleNumbered("", 0, 0))
        assertEquals(edit("# ", 2, 2), MarkdownFormat.cycleHeading("", 0, 0))
    }

    // ---- Enter inside a list ----

    @Test
    fun enter_continuesABulletList() {
        assertEquals(edit("- one\n- ", 8, 8), MarkdownFormat.afterNewline("- one", "- one\n", 6))
        assertEquals(edit("* x\n* ", 6, 6), MarkdownFormat.afterNewline("* x", "* x\n", 4))
    }

    @Test
    fun enter_continuesANumberedList_withTheNextNumber() {
        assertEquals(edit("1. one\n2. ", 10, 10), MarkdownFormat.afterNewline("1. one", "1. one\n", 7))
        assertEquals(edit("9. x\n10. ", 9, 9), MarkdownFormat.afterNewline("9. x", "9. x\n", 5))
    }

    @Test
    fun enter_keepsTheIndent() {
        assertEquals(edit("  - x\n  - ", 10, 10), MarkdownFormat.afterNewline("  - x", "  - x\n", 6))
    }

    @Test
    fun enter_onAnEmptyItemEndsTheList() {
        assertEquals(edit("- one\n", 6, 6), MarkdownFormat.afterNewline("- one\n- ", "- one\n- \n", 9))
    }

    @Test
    fun enter_inTheMiddleOfAnItemSplitsIt() {
        assertEquals(edit("- a\n- b", 6, 6), MarkdownFormat.afterNewline("- ab", "- a\nb", 4))
    }

    @Test
    fun enter_afterOtherLinesWorks() {
        assertEquals(
            edit("intro\n- a\n- ", 12, 12),
            MarkdownFormat.afterNewline("intro\n- a", "intro\n- a\n", 10)
        )
    }

    @Test
    fun enter_outsideAListDoesNothing() {
        assertNull(MarkdownFormat.afterNewline("hello", "hello\n", 6))
        assertNull(MarkdownFormat.afterNewline("# T", "# T\n", 4))
    }

    @Test
    fun otherChangesAreNotTouched() {
        // typing a letter, a paste with a line break, deleting, and two changes at once
        assertNull(MarkdownFormat.afterNewline("- a", "- ab", 4))
        assertNull(MarkdownFormat.afterNewline("- a", "- a\nb\n", 6))
        assertNull(MarkdownFormat.afterNewline("- ab", "- a", 3))
        assertNull(MarkdownFormat.afterNewline("- a", "- b\n", 4))
    }

    // ---- Text without the marks ----

    @Test
    fun plain_removesHeadingMarksAndBoldMarks() {
        assertEquals("Title", MarkdownFormat.plainLine("# Title"))
        assertEquals("Sub", MarkdownFormat.plainLine("## Sub"))
        assertEquals("a b c", MarkdownFormat.plainLine("a **b** c"))
        assertEquals("b", MarkdownFormat.plainLine("# **b**"))
    }

    @Test
    fun plain_drawsBulletsAsDots_andKeepsNumbers() {
        assertEquals("• a", MarkdownFormat.plainLine("- a"))
        assertEquals("• a", MarkdownFormat.plainLine("* a"))
        assertEquals("  • a", MarkdownFormat.plainLine("  - a"))
        assertEquals("1. a", MarkdownFormat.plainLine("1. a"))
    }

    @Test
    fun plain_leavesNormalTextAlone() {
        assertEquals("** x **", MarkdownFormat.plainLine("** x **"))
        assertEquals("5 * 3 = 15", MarkdownFormat.plainLine("5 * 3 = 15"))
        assertEquals("আমার নোট", MarkdownFormat.plainLine("আমার নোট"))
        assertEquals("", MarkdownFormat.plainText(""))
    }

    @Test
    fun plain_everyLineOfAText() {
        assertEquals("A\n• b\nc", MarkdownFormat.plainText("# A\n- b\nc"))
    }

    @Test
    fun sortIgnoresTheFormattingMarksOfATitle() {
        val notes = listOf(
            Note(1, "# Zed", "", 1),
            Note(2, "Apple", "", 2),
            Note(3, "**Mango**", "", 3)
        )
        assertEquals(
            listOf(2L, 3L, 1L),
            sortNotes(notes, NotesSort.TITLE_AZ).map { it.id }
        )
    }
}
