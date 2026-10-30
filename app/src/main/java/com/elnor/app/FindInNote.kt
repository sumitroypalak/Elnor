package com.elnor.app

import java.text.BreakIterator
import java.util.Arrays

/**
 * "Find in note": finds every place of a word in the text of one note, so the editor can colour
 * them and jump from one to the next.
 *
 * It matches the same way the notes list search does ([SearchText.normalize]): upper and lower
 * case do not matter, and the two ways of typing the same Bengali letter (for example য় as one
 * character or as য + ়) are the same. No Android classes besides java.text, so it is tested with
 * plain unit tests.
 */
object FindInNote {

    /** One place in the text. [start] is the first character, [end] is just after the last one. */
    data class Match(val start: Int, val end: Int)

    /**
     * All places of [query] in [text], from the start to the end, never overlapping ("aa" in "aaaa"
     * is found twice, not three times). A blank [query] finds nothing. The offsets are offsets in
     * [text] exactly as it is, so they can be used to colour the text or to put the cursor.
     *
     * A match covers whole letters as the platform's text rules see them: on a phone, finding "ক"
     * in "কি" colours the whole "কি", because the vowel sign cannot be shown apart from its letter.
     * (An old JDK, like the one in local unit tests, may see "ক" and "ি" as two letters.)
     */
    fun find(text: String, query: String): List<Match> {
        val wanted = SearchText.normalize(query)
        if (wanted.isBlank() || text.isEmpty()) return emptyList()

        // The text is normalized one visible character (grapheme cluster) at a time. Normalizing
        // can make a piece longer or shorter, so we remember where each piece starts in both the
        // normalized text and the original text.
        val normalized = StringBuilder(text.length)
        val normStart = IntArray(text.length + 1)
        val origStart = IntArray(text.length + 1)
        var clusters = 0

        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        var from = iterator.first()
        var to = iterator.next()
        while (to != BreakIterator.DONE) {
            origStart[clusters] = from
            normStart[clusters] = normalized.length
            normalized.append(SearchText.normalize(text.substring(from, to)))
            clusters++
            from = to
            to = iterator.next()
        }
        origStart[clusters] = text.length
        normStart[clusters] = normalized.length

        /** The cluster that holds the character at [normIndex] of the normalized text. */
        fun clusterAt(normIndex: Int): Int {
            val found = Arrays.binarySearch(normStart, 0, clusters, normIndex)
            return if (found >= 0) found else -found - 2
        }

        val result = ArrayList<Match>()
        var searchFrom = 0
        while (true) {
            val at = normalized.indexOf(wanted, searchFrom)
            if (at < 0) break
            searchFrom = at + wanted.length
            val start = origStart[clusterAt(at)]
            val end = origStart[clusterAt(at + wanted.length - 1) + 1]
            // Two matches inside one letter (after normalizing) would overlap: keep the first
            if (result.isNotEmpty() && start < result.last().end) continue
            result.add(Match(start, end))
        }
        return result
    }

    /**
     * The match to show after "next" ([forward] true) or "previous", going round at the ends.
     * [current] is the match shown now. Returns -1 when there is no match at all.
     */
    fun step(current: Int, total: Int, forward: Boolean): Int {
        if (total <= 0) return -1
        val from = current.coerceIn(0, total - 1)
        return if (forward) (from + 1) % total else (from - 1 + total) % total
    }

    /** The text next to the Find field: "2/5", or "No matches". Empty while nothing is searched. */
    fun counterLabel(queryIsBlank: Boolean, current: Int, total: Int): String = when {
        queryIsBlank -> ""
        total == 0 -> "No matches"
        else -> "${current.coerceIn(0, total - 1) + 1}/$total"
    }
}
