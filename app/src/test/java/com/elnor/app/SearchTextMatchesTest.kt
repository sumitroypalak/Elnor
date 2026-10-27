package com.elnor.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SearchText.matches: the search of notes that are already in memory (the Archive screen). */
class SearchTextMatchesTest {

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(SearchText.matches("Title", "Body", ""))
        assertTrue(SearchText.matches("Title", "Body", "   "))
        assertTrue(SearchText.matches("", "", ""))
    }

    @Test
    fun findsTextInTheTitleAndInTheBody() {
        assertTrue(SearchText.matches("Shopping list", "milk and eggs", "shopping"))
        assertTrue(SearchText.matches("Shopping list", "milk and eggs", "eggs"))
        assertFalse(SearchText.matches("Shopping list", "milk and eggs", "bread"))
    }

    @Test
    fun upperAndLowerCaseDoNotMatter_alsoForNonEnglishLetters() {
        assertTrue(SearchText.matches("MILK", "", "milk"))
        assertTrue(SearchText.matches("", "école", "ÉCOLE"))
    }

    @Test
    fun differentSpellingsOfTheSameBengaliLetterAreTheSame() {
        // য় as one character (U+09DF) and as য + nukta (U+09AF U+09BC)
        assertTrue(SearchText.matches("", "য়", "য়"))
        assertTrue(SearchText.matches("", "য়", "য়"))
    }

    @Test
    fun aWordIsNotFoundAcrossTheTitleAndTheBody() {
        assertFalse(SearchText.matches("ab", "cd", "bc"))
    }

    @Test
    fun aPhraseIsFoundAcrossBoldMarks() {
        assertTrue(SearchText.matches("", "say hello **world**", "hello world"))
        assertTrue(SearchText.matches("", "**hello** world", "hello world"))
        // text that is not bold stays as it is
        assertTrue(SearchText.matches("", "** x **", "** x **"))
    }

    @Test
    fun specialCharactersAreSearchedAsNormalText() {
        assertTrue(SearchText.matches("100% sure", "", "100%"))
        assertTrue(SearchText.matches("a_b", "", "a_b"))
        assertFalse(SearchText.matches("axb", "", "a_b"))
    }
}
