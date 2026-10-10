package com.drafto.app

import android.icu.text.BreakIterator
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test: android.icu.* is not available in a plain local JVM unit test,
 * so this must run on a device or emulator. Run it on an old API level (26) AND a
 * new one (34+), because ICU splits some Bengali conjuncts only on old versions.
 *
 * The expected values for English text are exact. For Bengali and emoji, the number
 * of clusters depends on the ICU version of the device, so those tests check rules
 * that must be true on every version (see assertValidTruncation).
 */
@RunWith(AndroidJUnit4::class)
class TruncateTitleTest {

    // ---- Test data (written as \u escapes so no editor can change them) ----
    private val ellipsis = "\u2026"

    /** 👨‍👩‍👧 : three emoji joined with ZWJ */
    private val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"

    /** 🇧🇩 : two regional indicators */
    private val flag = "\uD83C\uDDE7\uD83C\uDDE9"

    /** 👍🏽 : thumbs up + medium skin tone */
    private val thumbMedium = "\uD83D\uDC4D\uD83C\uDFFD"

    /** 😀 : one emoji, two UTF-16 chars (surrogate pair) */
    private val grin = "\uD83D\uDE00"

    private val ksha = "\u0995\u09CD\u09B7" // ক্ষ
    private val ddha = "\u09A6\u09CD\u09A7" // দ্ধ
    private val rya = "\u09B0\u200D\u09CD\u09AF" // র‍্য (with ZWJ)

    /** আজ সন্ধ্যায় শিক্ষক পরীক্ষার ফল জানাবেন এবং বিদ্যালয়ের উদ্যোগে মিটিং হবে */
    private val bengaliSentence =
        "\u0986\u099C \u09B8\u09A8\u09CD\u09A7\u09CD\u09AF\u09BE\u09DF " +
            "\u09B6\u09BF\u0995\u09CD\u09B7\u0995 " +
            "\u09AA\u09B0\u09C0\u0995\u09CD\u09B7\u09BE\u09B0 \u09AB\u09B2 " +
            "\u099C\u09BE\u09A8\u09BE\u09AC\u09C7\u09A8 \u098F\u09AC\u0982 " +
            "\u09AC\u09BF\u09A6\u09CD\u09AF\u09BE\u09B2\u09DF\u09C7\u09B0 " +
            "\u0989\u09A6\u09CD\u09AF\u09CB\u0997\u09C7 " +
            "\u09AE\u09BF\u099F\u09BF\u0982 \u09B9\u09AC\u09C7"

    private val englishSentence = "The quick brown fox jumps over the lazy dog and keeps running far away"

    private val emojiSentence =
        "Party $grin with $family family $flag and $thumbMedium friends $grin$grin done"

    private val bengaliWithJoiners =
        "$rya \u0995\u09CD\u09B7\u09AE\u09BE \u09AC\u09C1\u09A6\u09CD\u09A7\u09BF " +
            (ksha + ddha + rya).repeat(6)

    private val zwnjText = "x".repeat(40) + "\u200C" + "y".repeat(10)

    private val sweepSamples = listOf(
        englishSentence,
        bengaliSentence,
        emojiSentence,
        bengaliWithJoiners,
        zwnjText
    )

    // ---- Helpers ----

    private fun graphemeCount(text: String): Int {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        var count = 0
        iterator.first()
        while (iterator.next() != BreakIterator.DONE) count++
        return count
    }

    private fun isBoundary(text: String, index: Int): Boolean {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        return iterator.isBoundary(index)
    }

    /**
     * Rules that must be true for every truncated result on every Android version.
     */
    private fun assertValidTruncation(original: String, result: String, max: Int) {
        val label = "max=$max result=[$result]"
        assertTrue("ends with ellipsis: $label", result.endsWith(ellipsis))

        val head = result.dropLast(1)
        assertTrue("is a prefix of the original: $label", original.startsWith(head))
        assertTrue("at most $max clusters: $label", graphemeCount(head) <= max)
        assertEquals("no trailing whitespace: $label", head.trimEnd(), head)
        assertTrue("cut is on a cluster boundary: $label", isBoundary(original, head.length))

        if (head.isNotEmpty()) {
            val last = head.codePointBefore(head.length)
            assertFalse("does not end with hasanta: $label", last == 0x09CD)
            assertFalse("does not end with ZWJ: $label", last == 0x200D)
            assertFalse("does not end with ZWNJ: $label", last == 0x200C)
        }

        for (i in head.indices) {
            val c = head[i]
            if (Character.isHighSurrogate(c)) {
                assertTrue(
                    "high surrogate has its pair: $label",
                    i + 1 < head.length && Character.isLowSurrogate(head[i + 1])
                )
            }
            if (Character.isLowSurrogate(c)) {
                assertTrue(
                    "low surrogate has its pair: $label",
                    i > 0 && Character.isHighSurrogate(head[i - 1])
                )
            }
        }
    }

    /** If any part of [sequence] is in [result], the whole [sequence] must be there. */
    private fun assertWholeOrAbsent(result: String, sequence: String, firstPartOfIt: String) {
        assertTrue(
            "sequence must not be split: [$result]",
            !result.contains(firstPartOfIt) || result.contains(sequence)
        )
    }

    // ---- English ----

    @Test
    fun shortTextIsReturnedUnchanged() {
        assertEquals("Hello", truncateTitle("Hello"))
    }

    @Test
    fun emptyStringReturnsEmptyString() {
        assertEquals("", truncateTitle(""))
    }

    @Test
    fun textExactlyAtLimitIsUnchanged() {
        val withSpaces = "abcdefg ".repeat(4) + "abc" // 35 characters
        assertEquals(35, withSpaces.length)
        assertEquals(withSpaces, truncateTitle(withSpaces, 35))
        assertEquals("a".repeat(35), truncateTitle("a".repeat(35), 35))
    }

    @Test
    fun oneClusterOverTheLimitIsCut() {
        assertEquals("a".repeat(35) + ellipsis, truncateTitle("a".repeat(36), 35))
    }

    @Test
    fun longWordWithoutSpacesIsCutAtTheLimit() {
        assertEquals("a".repeat(35) + ellipsis, truncateTitle("a".repeat(50)))
    }

    @Test
    fun cutInsideAWordGoesBackToTheEndOfThePreviousWord() {
        assertEquals(
            "Hello wonderful$ellipsis",
            truncateTitle("Hello wonderful world of Kotlin", 20)
        )
    }

    @Test
    fun defaultLimitIs35AndCutsAtWordEnd() {
        assertEquals(
            "The quick brown fox jumps over the$ellipsis",
            truncateTitle("The quick brown fox jumps over the lazy dog")
        )
    }

    @Test
    fun doesNotGoBackWhenTheCutIsAlreadyAtTheEndOfAWord() {
        // The 10 kept clusters are "aaa bbbbbb" and the next one is a space.
        assertEquals("aaa bbbbbb$ellipsis", truncateTitle("aaa bbbbbb ccc", 10))
    }

    @Test
    fun trailingSpacesAreRemovedBeforeTheEllipsis() {
        assertEquals("aaaa$ellipsis", truncateTitle("aaaa     bbbbbbbbbb", 8))
    }

    @Test
    fun spaceOutsideTheLastTenClustersIsIgnored() {
        // The only space is the 3rd character, which is not in the last 10 of the 20 kept.
        assertEquals(
            "ab cdefghijklmnopqrs$ellipsis",
            truncateTitle("ab cdefghijklmnopqrstuvwxyz", 20)
        )
    }

    @Test
    fun ifOnlySpacesComeBeforeTheWordCutThePlainCutIsUsed() {
        assertEquals(" bbbb$ellipsis", truncateTitle(" " + "b".repeat(40), 5))
    }

    // ---- Emoji ----

    @Test
    fun familyEmojiIsNeverSplit() {
        val text = "a".repeat(34) + family + "bcdefghij"
        val result = truncateTitle(text, 35)
        assertValidTruncation(text, result, 35)
        assertTrue(result.startsWith("a".repeat(34)))
        assertWholeOrAbsent(result, family, "\uD83D\uDC69")

        // The family would be cut in the middle if clusters were counted per emoji.
        val text2 = "a".repeat(33) + family + "bcdefghij"
        val result2 = truncateTitle(text2, 35)
        assertValidTruncation(text2, result2, 35)
        assertWholeOrAbsent(result2, family, "\uD83D\uDC69")
    }

    @Test
    fun flagIsNeverSplit() {
        val text = "a".repeat(34) + flag + flag
        val result = truncateTitle(text, 35)
        assertValidTruncation(text, result, 35)
        assertTrue(result.startsWith("a".repeat(34)))
        assertWholeOrAbsent(result, flag, "\uD83C\uDDE7")

        // A flag after the limit is removed completely.
        assertEquals(
            "a".repeat(35) + ellipsis,
            truncateTitle("a".repeat(35) + flag + "x", 35)
        )
    }

    @Test
    fun skinToneEmojiIsNeverSplit() {
        val text = "a".repeat(34) + thumbMedium + "bcd"
        val result = truncateTitle(text, 35)
        assertValidTruncation(text, result, 35)
        assertTrue(result.startsWith("a".repeat(34)))
        assertWholeOrAbsent(result, thumbMedium, "\uD83D\uDC4D")
        assertTrue(!result.contains("\uD83C\uDFFD") || result.contains(thumbMedium))
    }

    @Test
    fun surrogatePairsAreNeverSplit() {
        val text = "a".repeat(34) + grin + grin
        val result = truncateTitle(text, 35)
        assertValidTruncation(text, result, 35)
        assertTrue(result.startsWith("a".repeat(34)))
    }

    // ---- Bengali ----

    @Test
    fun bengaliConjunctIsNeverCutInTheMiddle() {
        for (conjunct in listOf(ksha, ddha, rya)) {
            // The conjunct starts at cluster 35, exactly where the cut happens.
            val text = "a".repeat(34) + conjunct + "\u09AE\u09BE\u09A8"
            val result = truncateTitle(text, 35)
            assertValidTruncation(text, result, 35)
            assertTrue(
                "the 34 letters before the conjunct stay: [$result]",
                result.startsWith("a".repeat(34))
            )
        }
    }

    @Test
    fun bengaliSentenceIsCutAtTheEndOfAWord() {
        val result = truncateTitle(bengaliSentence, 35)
        assertValidTruncation(bengaliSentence, result, 35)

        val head = result.dropLast(1)
        assertTrue(head.length < bengaliSentence.length)
        assertTrue(
            "the next character in the original is a space: [$result]",
            bengaliSentence[head.length].isWhitespace()
        )
    }

    // ---- Every limit, every sample ----

    @Test
    fun everyLimitGivesAValidResultForEverySample() {
        for (sample in sweepSamples) {
            val count = graphemeCount(sample)

            // Exactly at the limit: unchanged.
            assertEquals(sample, truncateTitle(sample, count))

            // One below: must be shortened.
            assertTrue(truncateTitle(sample, count - 1).endsWith(ellipsis))

            for (max in 1 until count) {
                assertValidTruncation(sample, truncateTitle(sample, max), max)
            }
        }
    }
}
