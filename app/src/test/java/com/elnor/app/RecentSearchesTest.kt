package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** RecentSearches: the list of recent searches under the Home search field. */
class RecentSearchesTest {

    @Test
    fun newestSearchComesFirst() {
        val list = RecentSearches.add(RecentSearches.add(emptyList(), "apple"), "banana")
        assertEquals(listOf("banana", "apple"), list)
    }

    @Test
    fun spacesAreTidied() {
        assertEquals(listOf("big plan"), RecentSearches.add(emptyList(), "   big    plan \n"))
    }

    @Test
    fun blankAndTooShortSearchesAreNotSaved() {
        val start = listOf("apple")
        assertEquals(start, RecentSearches.add(start, ""))
        assertEquals(start, RecentSearches.add(start, "   "))
        assertEquals(start, RecentSearches.add(start, "a"))
    }

    @Test
    fun twoLetterBengaliWordIsSaved() {
        assertEquals(listOf("কি"), RecentSearches.add(emptyList(), "কি"))
    }

    @Test
    fun sameSearchIsNotListedTwice_caseDoesNotMatter_andMovesToTop() {
        val start = listOf("banana", "Plan", "apple")
        val list = RecentSearches.add(start, "plan")
        assertEquals(listOf("plan", "banana", "apple"), list) // latest spelling, at the top
    }

    @Test
    fun bengaliYaWrittenTwoWaysIsTheSameSearch() {
        val precomposed = "য়" // য় as one character
        val decomposed = "য়" // য + ়
        val list = RecentSearches.add(listOf("x$precomposed"), "x$decomposed")
        assertEquals(1, list.size)
    }

    @Test
    fun listNeverGetsLongerThanTheLimit_theOldestGoesOut() {
        var list = emptyList<String>()
        for (i in 1..(RecentSearches.MAX_ITEMS + 3)) list = RecentSearches.add(list, "word$i")
        assertEquals(RecentSearches.MAX_ITEMS, list.size)
        assertEquals("word${RecentSearches.MAX_ITEMS + 3}", list.first())
        assertTrue("word1" !in list)
    }

    @Test
    fun removeTakesOutOnlyThatEntry() {
        val list = RecentSearches.remove(listOf("cccc", "bbbb", "aaaa"), "BBBB")
        assertEquals(listOf("cccc", "aaaa"), list)
    }

    @Test
    fun removeOfAnUnknownEntryChangesNothing() {
        val start = listOf("apple", "banana")
        assertEquals(start, RecentSearches.remove(start, "cherry"))
    }

    @Test
    fun saveAndReadBack_keepsOrderAndBengaliAndQuotes() {
        val start = listOf("আমার নোট", "say \"hi\"", "plan")
        assertEquals(start, RecentSearches.fromJson(RecentSearches.toJson(start)))
    }

    @Test
    fun nothingSavedOrDamagedDataGivesAnEmptyList() {
        assertEquals(emptyList<String>(), RecentSearches.fromJson(null))
        assertEquals(emptyList<String>(), RecentSearches.fromJson(""))
        assertEquals(emptyList<String>(), RecentSearches.fromJson("not json at all"))
        assertEquals(emptyList<String>(), RecentSearches.fromJson("{\"a\":1}"))
    }

    @Test
    fun readingDropsBlankRepeatedAndTooManyEntries() {
        val stored = "[\"apple\", \"  \", \"Apple\", \"b\", \"cherry\"]"
        assertEquals(listOf("apple", "cherry"), RecentSearches.fromJson(stored))

        val many = (1..20).joinToString(prefix = "[", postfix = "]") { "\"word$it\"" }
        assertEquals(RecentSearches.MAX_ITEMS, RecentSearches.fromJson(many).size)
    }
}
