package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteStatsTest {

    @Test
    fun words_areSeparatedBySpacesTabsAndLineBreaks() {
        assertEquals(0, countWords(""))
        assertEquals(0, countWords("   \n\t "))
        assertEquals(1, countWords("hello"))
        assertEquals(2, countWords("hello world"))
        assertEquals(4, countWords("  one  two\tthree\nfour \n"))
    }

    @Test
    fun words_workForBengaliText() {
        // "আমি বাংলা লিখি" written with \u escapes so no editor can change it
        val bengali = "আমি বাংলা লিখি"
        assertEquals(3, countWords(bengali))
        assertEquals(5, countWords("$bengali hello\nworld"))
    }

    @Test
    fun words_nonBreakingSpaceSeparatesToo() {
        assertEquals(2, countWords("one two"))
    }

    @Test
    fun characters_countSpacesButNotLineBreaks() {
        assertEquals(0, countCharacters(""))
        assertEquals(0, countCharacters("\n\r\n"))
        assertEquals(5, countCharacters("hello"))
        assertEquals(11, countCharacters("hello world"))
        assertEquals(11, countCharacters("hello\n world\r\n"))
    }

    @Test
    fun characters_anEmojiWithTwoUtf16UnitsCountsOnce() {
        assertEquals(1, countCharacters("😀")) // 😀
        assertEquals(3, countCharacters("a😀b"))
    }

    @Test
    fun noteStats_addsTitleAndBody_andDoesNotJoinTheirWords() {
        val stats = noteStats("Hello", "world again")
        assertEquals(3, stats.words) // not 2: "Hello" and "world" are different words
        assertEquals(5 + 11, stats.characters)
    }

    @Test
    fun noteStats_ofAnEmptyNote_isZero() {
        assertEquals(NoteStats(0, 0), noteStats("", ""))
    }

    @Test
    fun format_usesSingularAndPlural() {
        assertEquals("0 words · 0 characters", formatNoteStats(NoteStats(0, 0)))
        assertEquals("1 word · 1 character", formatNoteStats(NoteStats(1, 1)))
        assertEquals("12 words · 68 characters", formatNoteStats(NoteStats(12, 68)))
    }
}
