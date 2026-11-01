package com.elnor.app

/**
 * "How long until this note is deleted for good", shown on each note in the Trash.
 *
 * The note goes for good when it has been in the Trash for [retentionMs] (see TrashRetention).
 * The days left are counted up: 12 hours left is "1 day", so the text never says "0 days" while
 * the note is still there. When the time is over (the note only waits for the next clean-up,
 * which happens when the app or the Trash opens) it says "today".
 *
 * No Android classes here, so it is tested with plain unit tests. [now] is a parameter for the
 * same reason.
 */
object TrashDaysLeft {

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** Whole days left, counted up. 0 when the time is over or the note is deleted today. */
    fun daysLeft(deletedAt: Long, now: Long, retentionMs: Long): Int {
        // Never more than the chosen time, even if the phone's clock was set back after the delete
        val leftMs = minOf(deletedAt + retentionMs - now, retentionMs)
        if (leftMs <= 0L) return 0
        return ((leftMs + DAY_MS - 1L) / DAY_MS).toInt()
    }

    /** The line under the note, for example "Deleted for good in 12 days". */
    fun label(deletedAt: Long, now: Long, retentionMs: Long): String =
        when (val days = daysLeft(deletedAt, now, retentionMs)) {
            0 -> "Will be deleted for good today"
            1 -> "Deleted for good tomorrow"
            else -> "Deleted for good in $days days"
        }

    /** True when 3 days or less are left: the line is shown in red so the user can still restore the note. */
    fun isUrgent(deletedAt: Long, now: Long, retentionMs: Long): Boolean =
        daysLeft(deletedAt, now, retentionMs) <= 3
}
