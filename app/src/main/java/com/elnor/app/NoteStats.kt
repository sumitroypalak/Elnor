package com.elnor.app

import java.text.BreakIterator

/** How much is written in a note. See [noteStats]. */
data class NoteStats(val words: Int, val characters: Int)

/**
 * Number of words: groups of characters that are separated by spaces, tabs or line breaks.
 * It works for Bengali and English alike, because both are written with spaces between words.
 */
fun countWords(text: String): Int {
    var count = 0
    var inWord = false
    for (ch in text) {
        if (ch.isWhitespace()) {
            inWord = false
        } else if (!inWord) {
            inWord = true
            count++
        }
    }
    return count
}

/**
 * Number of characters, as the user sees them: spaces count, line breaks do not. A letter with its
 * vowel sign or a conjunct (like কি or ক্ষ), and an emoji, count once, not once per code point.
 * The count follows the phone's own text rules (java.text.BreakIterator is ICU on Android).
 */
fun countCharacters(text: String): Int {
    val visible = text.filter { it != '\n' && it != '\r' && it != ' ' && it != ' ' }
    if (visible.isEmpty()) return 0

    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(visible)
    var count = 0
    iterator.first()
    while (iterator.next() != BreakIterator.DONE) count++
    return count
}

/**
 * The count for the whole note: the title and the text. They are counted one after the other, so
 * the last word of the title and the first word of the text are never counted as one word.
 */
fun noteStats(title: String, body: String): NoteStats = NoteStats(
    words = countWords(title) + countWords(body),
    characters = countCharacters(title) + countCharacters(body)
)

/** The line under the note, e.g. "12 words · 68 characters" ("1 word", "1 character"). */
fun formatNoteStats(stats: NoteStats): String {
    val words = if (stats.words == 1) "1 word" else "${stats.words} words"
    val characters = if (stats.characters == 1) "1 character" else "${stats.characters} characters"
    return "$words · $characters"
}
