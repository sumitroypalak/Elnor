package com.elnor.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONException

/**
 * The "recent searches" of the Home search field: the last words the user searched for, newest
 * first, so a search can be repeated with one tap.
 *
 * Rules:
 *  - At most [MAX_ITEMS] entries. A new one pushes the oldest out.
 *  - A search is saved with its spaces tidied (no spaces at the start and the end, many spaces
 *    become one). Searches shorter than [MIN_LENGTH] letters are not saved (they are mostly
 *    half-typed words).
 *  - The same search is never listed twice. "Plan" and "plan" are the same search (and so are the
 *    two ways of typing য়), because the comparison uses [SearchText.normalize], the same
 *    normalization the real search uses. Searching it again moves it to the top, and it keeps the
 *    spelling of the latest time.
 *
 * This object has no Android classes besides org.json, so it is tested with plain unit tests.
 * Reading and writing the phone's storage is at the end of this file.
 */
object RecentSearches {

    const val MAX_ITEMS = 8
    const val MIN_LENGTH = 2

    /** Spaces at the start and the end removed, every run of spaces / line breaks becomes one space. */
    fun clean(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    /** The list after [query] was searched. [current] is returned unchanged when the query is not worth saving. */
    fun add(current: List<String>, query: String): List<String> {
        val cleaned = clean(query)
        if (cleaned.length < MIN_LENGTH) return current
        val key = SearchText.normalize(cleaned)
        val others = current.filter { SearchText.normalize(it) != key }
        return (listOf(cleaned) + others).take(MAX_ITEMS)
    }

    /** The list without [item] (compared like [add] does). */
    fun remove(current: List<String>, item: String): List<String> {
        val key = SearchText.normalize(clean(item))
        return current.filter { SearchText.normalize(it) != key }
    }

    /** What is saved on the phone: a JSON array of text. */
    fun toJson(items: List<String>): String = JSONArray(items).toString()

    /**
     * Reads what [toJson] wrote. Nothing saved, or damaged data, gives an empty list: it is only a
     * convenience, so it must never stop the app. Entries that are blank or repeated are dropped.
     */
    fun fromJson(stored: String?): List<String> {
        if (stored.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(stored)
            val result = ArrayList<String>()
            val seen = HashSet<String>()
            for (i in 0 until array.length()) {
                val cleaned = clean(array.optString(i, ""))
                if (cleaned.length < MIN_LENGTH) continue
                if (seen.add(SearchText.normalize(cleaned))) result.add(cleaned)
            }
            result.take(MAX_ITEMS)
        } catch (_: JSONException) {
            emptyList()
        }
    }
}

// Same file as the other small settings (font size, theme)
private const val RECENT_SETTINGS_FILE = "elnor_settings"
private const val KEY_RECENT_SEARCHES = "recent_searches"

internal fun loadRecentSearches(context: Context): List<String> =
    RecentSearches.fromJson(
        context.applicationContext
            .getSharedPreferences(RECENT_SETTINGS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_SEARCHES, null)
    )

internal fun saveRecentSearches(context: Context, items: List<String>) {
    context.applicationContext
        .getSharedPreferences(RECENT_SETTINGS_FILE, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_RECENT_SEARCHES, RecentSearches.toJson(items))
        .apply()
}
