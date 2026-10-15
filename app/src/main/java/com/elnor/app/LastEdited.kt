package com.elnor.app

/**
 * The "Last edited" line of the note page, e.g. "Last edited 15 Oct 2026 \u00B7 12:40 PM".
 * [format] turns the time into text (the same date style as the notes list).
 * Returns null when there is nothing to show: a note that was never saved has no time.
 */
fun lastEditedLabel(updatedAt: Long?, format: (Long) -> String): String? {
    if (updatedAt == null || updatedAt <= 0L) return null
    return "Last edited " + format(updatedAt)
}
