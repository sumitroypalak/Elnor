package com.elnor.app

import com.elnor.app.FindInNote.Match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FindInNote: finding a word inside one note, and moving from one match to the next. */
class FindInNoteTest {

    @Test
    fun upperAndLowerCaseDoNotMatter() {
        assertEquals(listOf(Match(0, 5), Match(6, 11)), FindInNote.find("Hello HELLO", "hello"))
        assertEquals(listOf(Match(0, 5), Match(6, 11)), FindInNote.find("ÉCOLE école", "école"))
    }

    @Test
    fun blankQueryOrEmptyTextFindsNothing() {
        assertTrue(FindInNote.find("abc", "").isEmpty())
        assertTrue(FindInNote.find("abc", "   ").isEmpty())
        assertTrue(FindInNote.find("", "a").isEmpty())
        assertTrue(FindInNote.find("hello", "xyz").isEmpty())
    }

    @Test
    fun matchesNeverOverlap() {
        assertEquals(listOf(Match(0, 2), Match(2, 4)), FindInNote.find("aaaa", "aa"))
        assertEquals(listOf(Match(0, 3)), FindInNote.find("aaaa", "aaa"))
    }

    @Test
    fun findsEveryPlace_alsoAcrossLinesAndAtTheEnd() {
        assertEquals(listOf(Match(0, 3), Match(8, 11)), FindInNote.find("one\ntwo\none", "one"))
        assertEquals(listOf(Match(0, 1), Match(4, 5)), FindInNote.find("a b a", "a"))
    }

    @Test
    fun offsetsAreOffsetsOfTheOriginalText_withEmoji() {
        // an emoji is two characters in the text
        val text = "😀 hi 😀 hi"
        assertEquals(listOf(Match(3, 5), Match(9, 11)), FindInNote.find(text, "hi"))
    }

    @Test
    fun bengaliWordsAreFound() {
        assertEquals(
            listOf(Match(0, 4), Match(9, 13)),
            FindInNote.find("আমার নোট আমার", "আমার")
        )
    }

    @Test
    fun bothWaysOfTypingTheSameBengaliLetterAreTheSame() {
        // য় as one character (U+09DF) in the text, as য + ় in the query
        assertEquals(listOf(Match(1, 2)), FindInNote.find("aয়b", "য়"))
        // and the other way round: the match covers both characters of the text
        assertEquals(listOf(Match(1, 3)), FindInNote.find("aয়b", "য়"))
    }

    @Test
    fun aMatchCoversTheWholeVisibleLetter() {
        // the vowel sign cannot be shown apart from its letter
        assertEquals(listOf(Match(0, 2)), FindInNote.find("কি", "ক"))
    }

    @Test
    fun stepGoesRoundAtTheEnds() {
        assertEquals(1, FindInNote.step(current = 0, total = 3, forward = true))
        assertEquals(0, FindInNote.step(current = 2, total = 3, forward = true))
        assertEquals(2, FindInNote.step(current = 0, total = 3, forward = false))
        assertEquals(0, FindInNote.step(current = 1, total = 3, forward = false))
        assertEquals(0, FindInNote.step(current = 0, total = 1, forward = true))
    }

    @Test
    fun stepWithNoMatchesGivesMinusOne() {
        assertEquals(-1, FindInNote.step(current = 0, total = 0, forward = true))
        assertEquals(-1, FindInNote.step(current = 0, total = 0, forward = false))
    }

    @Test
    fun counterLabel() {
        assertEquals("", FindInNote.counterLabel(queryIsBlank = true, current = 0, total = 0))
        assertEquals("No matches", FindInNote.counterLabel(queryIsBlank = false, current = 0, total = 0))
        assertEquals("1/5", FindInNote.counterLabel(queryIsBlank = false, current = 0, total = 5))
        assertEquals("5/5", FindInNote.counterLabel(queryIsBlank = false, current = 4, total = 5))
        // a stale position (the note got shorter) never shows a wrong number
        assertEquals("2/2", FindInNote.counterLabel(queryIsBlank = false, current = 9, total = 2))
    }
}
