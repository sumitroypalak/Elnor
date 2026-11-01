package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** NoteExport: the file name and the file text when a note is saved as .txt or .md. */
class NoteExportTest {

    private val txt = NoteExportFormat.TXT
    private val md = NoteExportFormat.MD

    // ---- file name ----

    @Test
    fun fileName_usesTheTitle_andTheExtension() {
        assertEquals("Shopping list.txt", NoteExport.fileName("Shopping list", "milk", txt))
        assertEquals("Shopping list.md", NoteExport.fileName("Shopping list", "milk", md))
    }

    @Test
    fun fileName_withoutATitle_usesTheFirstLine_withoutTheFormattingMarks() {
        assertEquals("Plan.md", NoteExport.fileName("", "\n# Plan\nstep one", md))
    }

    @Test
    fun fileName_withNothing_isUntitledNote() {
        assertEquals("Untitled note.txt", NoteExport.fileName("  ", "  \n ", txt))
    }

    @Test
    fun fileName_forbiddenCharactersAndLineBreaksBecomeOneSpace() {
        assertEquals("a b c d.txt", NoteExport.fileName("a/b:c*d", "", txt))
        assertEquals("what is this.txt", NoteExport.fileName("what? is \"this\"", "", txt))
        assertEquals("one two.txt", NoteExport.fileName("one\ttwo", "", txt))
    }

    @Test
    fun fileName_onlyForbiddenCharactersOrDots_fallsBackToNote() {
        assertEquals("note.txt", NoteExport.fileName("???", "", txt))
        assertEquals("note.md", NoteExport.fileName("...", "", md))
    }

    @Test
    fun fileName_trailingDotsAreRemoved() {
        assertEquals("Ideas.txt", NoteExport.fileName("Ideas...", "", txt))
    }

    @Test
    fun fileName_keepsBengaliAndEmoji() {
        assertEquals("আমার নোট.txt", NoteExport.fileName("আমার নোট", "", txt))
        assertEquals("Trip 🌴.md", NoteExport.fileName("Trip 🌴", "", md))
    }

    @Test
    fun fileName_isAtMost80Characters_andNeverCutsAnEmojiInHalf() {
        val long = "a".repeat(200)
        val name = NoteExport.fileName(long, "", txt)
        assertEquals(80 + ".txt".length, name.length)

        // 100 emoji (2 chars each in UTF-16): 80 whole emoji are kept
        val emoji = "😀".repeat(100)
        val emojiName = NoteExport.fileName(emoji, "", txt)
        val base = emojiName.removeSuffix(".txt")
        assertEquals(80, base.codePointCount(0, base.length))
        assertFalse(Character.isHighSurrogate(base.last()))
    }

    // ---- text of the file ----

    @Test
    fun md_isTheNoteAsWritten_withTheTitleAsAHeading() {
        val text = NoteExport.text("Plan", "**Do** this\n- one\n- two", md)
        assertEquals("# Plan\n\n**Do** this\n- one\n- two\n", text)
    }

    @Test
    fun txt_hasNoFormattingMarks_bulletsAreDots() {
        val text = NoteExport.text("Plan", "# Big\n**Do** this\n- one\n1. two", txt)
        assertEquals("Plan\n\nBig\nDo this\n• one\n1. two\n", text)
    }

    @Test
    fun withoutATitle_noHeadingAndNoEmptyLineOnTop() {
        assertEquals("just text\n", NoteExport.text("", "just text", txt))
        assertEquals("just **text**\n", NoteExport.text("  ", "just **text**", md))
    }

    @Test
    fun withoutText_onlyTheTitle() {
        assertEquals("Title\n", NoteExport.text("Title", "", txt))
        assertEquals("# Title\n", NoteExport.text("Title", "  ", md))
    }

    @Test
    fun emptyNote_givesAnEmptyText() {
        assertEquals("", NoteExport.text("", "", txt))
        assertEquals("", NoteExport.text(" ", "\n", md))
    }

    @Test
    fun spacesAtTheStartAndEnd_areRemoved_butTheLinesInsideStay() {
        val text = NoteExport.text("  T  ", "\n\n a\n\n b \n\n", md)
        assertEquals("# T\n\na\n\n b\n", text)
    }

    @Test
    fun bengaliTextStaysTheSame() {
        assertEquals("আমার নোট\n\nআজকের কাজ\n", NoteExport.text("আমার নোট", "আজকের কাজ", txt))
    }

    @Test
    fun fileEndsWithExactlyOneLineBreak() {
        assertTrue(NoteExport.text("a", "b", txt).endsWith("b\n"))
        assertFalse(NoteExport.text("a", "b", txt).endsWith("\n\n"))
    }

    @Test
    fun formatsHaveTheRightExtensionAndType() {
        assertEquals("txt", txt.extension)
        assertEquals("text/plain", txt.mimeType)
        assertEquals("md", md.extension)
        assertEquals("text/markdown", md.mimeType)
    }
}
