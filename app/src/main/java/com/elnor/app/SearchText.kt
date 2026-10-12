package com.elnor.app

import java.text.Normalizer
import java.util.Locale

/**
 * Helpers for searching notes with SQLite LIKE.
 *
 * Problem: SQLite's LIKE ignores upper/lower case only for English (ASCII) letters, so
 * "ÉCOLE" is not found by "école". It also does not know that the same Bengali letter can be
 * typed in two ways (for example য় as U+09DF or as য + ় ). So every note keeps an extra
 * column, searchText, that is already normalized. The search query goes through the same
 * [normalize], so both sides always look the same.
 */
object SearchText {

    /** Unicode NFC, then lower case (Locale.ROOT, so the phone's language does not matter). */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    /** The value stored in the searchText column: title + "\n" + body, normalized. */
    fun build(title: String, body: String): String = normalize(title + "\n" + body)

    /**
     * Puts a backslash before % _ and \ so they are searched as normal characters.
     * Use it with:  LIKE '%' || :query || '%' ESCAPE '\'
     */
    fun escapeLike(text: String): String {
        val result = StringBuilder(text.length + 4)
        for (ch in text) {
            if (ch == '\\' || ch == '%' || ch == '_') result.append('\\')
            result.append(ch)
        }
        return result.toString()
    }

    /** What the user typed -> the text to give to the DAO query (normalized, then escaped). */
    fun toLikeTerm(query: String): String = escapeLike(normalize(query))
}
