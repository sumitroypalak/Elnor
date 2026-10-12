package com.elnor.app

import android.icu.text.BreakIterator

private const val ELLIPSIS = "…"

/** How many of the last kept clusters we search for a space (word end). */
private const val WORD_BREAK_WINDOW = 10

private const val BENGALI_HASANTA = 0x09CD
private const val ZWNJ = 0x200C
private const val ZWJ = 0x200D
private const val VARIATION_SELECTOR_15 = 0xFE0E
private const val VARIATION_SELECTOR_16 = 0xFE0F
private const val SKIN_TONE_FIRST = 0x1F3FB
private const val SKIN_TONE_LAST = 0x1F3FF
private const val REGIONAL_INDICATOR_FIRST = 0x1F1E6
private const val REGIONAL_INDICATOR_LAST = 0x1F1FF

/**
 * Shortens [text] to at most [maxGraphemes] user-perceived characters
 * (grapheme clusters) and adds "…" when something was removed.
 *
 * - Counting and cutting use `android.icu.text.BreakIterator` (API 24+), never
 *   `Char` or code point counts, so surrogate pairs, flags, family / skin-tone
 *   emoji and combining marks are not split.
 * - If [text] fits, it is returned unchanged.
 * - If the cut falls inside a word and a space exists among the last
 *   [WORD_BREAK_WINDOW] kept clusters, the text is cut at the end of the
 *   previous word instead. If the cut already falls at the end of a word, we
 *   do not go back.
 * - Older Android versions can split Bengali conjuncts (ক্ষ, দ্ধ ...) into two
 *   clusters. If the cut would leave a hasanta (U+09CD), ZWJ or ZWNJ at the end
 *   (or the next cluster only continues the previous one), we step back one
 *   cluster at a time until the cut is safe.
 * - Trailing whitespace is removed before the ellipsis is added.
 *
 * Note: the result can be [maxGraphemes] + 1 clusters long (the ellipsis).
 */
fun truncateTitle(text: String, maxGraphemes: Int = 35): String {
    require(maxGraphemes > 0) { "maxGraphemes must be greater than 0" }

    // Fast path: the number of clusters can never be bigger than the UTF-16 length.
    if (text.length <= maxGraphemes) return text

    // ends[i] = UTF-16 index where grapheme cluster i ends.
    // We collect only maxGraphemes + 1 of them: that is enough to know if the text is too long.
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val ends = IntArray(maxGraphemes + 1)
    var count = 0
    iterator.first()
    var boundary = iterator.next()
    while (boundary != BreakIterator.DONE && count <= maxGraphemes) {
        ends[count++] = boundary
        boundary = iterator.next()
    }
    if (count <= maxGraphemes) return text // fits in the limit

    val wordKeep = wordBoundaryKeep(text, ends, maxGraphemes)
    var head = headOf(text, ends, wordKeep)
    if (head.isEmpty() && wordKeep != maxGraphemes) {
        // Only spaces were before the word cut, so use the plain cut instead.
        head = headOf(text, ends, maxGraphemes)
    }
    return head + ELLIPSIS
}

/** Number of clusters to keep so that the text ends at the end of a word. */
private fun wordBoundaryKeep(text: String, ends: IntArray, hardKeep: Int): Int {
    // The first removed cluster is a space: the plain cut is already at a word end.
    if (isSpaceCluster(text, ends, hardKeep)) return hardKeep

    val windowStart = maxOf(0, hardKeep - WORD_BREAK_WINDOW)
    for (i in hardKeep - 1 downTo windowStart) {
        if (isSpaceCluster(text, ends, i)) return i // keep clusters before this space
    }
    return hardKeep // no space nearby: cut inside the word
}

/** Text of the first [keepClusters] clusters, moved back until the cut is safe, without trailing spaces. */
private fun headOf(text: String, ends: IntArray, keepClusters: Int): String {
    var keep = keepClusters
    while (keep > 0 && isUnsafeCut(text, ends[keep - 1])) keep--
    val end = if (keep == 0) 0 else ends[keep - 1]
    return text.substring(0, end).trimEnd()
}

private fun isSpaceCluster(text: String, ends: IntArray, index: Int): Boolean {
    val start = if (index == 0) 0 else ends[index - 1]
    val end = ends[index]
    if (end <= start) return false
    for (i in start until end) {
        if (!text[i].isWhitespace()) return false
    }
    return true
}

/**
 * True if cutting [text] at UTF-16 [index] would break a character sequence.
 * ICU on new Android versions never returns such boundaries, but older
 * versions can (for example Bengali conjuncts).
 */
private fun isUnsafeCut(text: String, index: Int): Boolean {
    if (index <= 0 || index >= text.length) return false

    val before = text.codePointBefore(index)
    val after = text.codePointAt(index)

    // The kept text would end with a joiner: the sequence is not finished.
    if (before == BENGALI_HASANTA || before == ZWJ || before == ZWNJ) return true

    // The removed text would start with something that only continues the previous cluster.
    if (after == BENGALI_HASANTA || after == ZWJ || after == ZWNJ ||
        after == VARIATION_SELECTOR_15 || after == VARIATION_SELECTOR_16 ||
        after in SKIN_TONE_FIRST..SKIN_TONE_LAST
    ) {
        return true
    }

    // A flag is two regional indicators. Do not cut between them.
    if (isRegionalIndicator(before) && isRegionalIndicator(after)) {
        var run = 0
        var i = index
        while (i > 0) {
            val cp = text.codePointBefore(i)
            if (!isRegionalIndicator(cp)) break
            run++
            i -= Character.charCount(cp)
        }
        if (run % 2 == 1) return true
    }
    return false
}

private fun isRegionalIndicator(codePoint: Int): Boolean =
    codePoint in REGIONAL_INDICATOR_FIRST..REGIONAL_INDICATOR_LAST

