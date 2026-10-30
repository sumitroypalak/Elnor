package com.elnor.app

import android.content.Context

/**
 * How long a deleted note stays in the Trash before it is deleted for good. The three choices of
 * Settings > Trash.
 *
 * [storageValue] is what is saved on the phone: a fixed text, not the enum name, so renaming an
 * entry later cannot break saved settings. [DEFAULT] is 30 days, what the app always did before
 * this was a setting.
 */
enum class TrashRetention(val days: Int, val storageValue: String) {
    DAYS_7(7, "7"),
    DAYS_30(30, "30"),
    DAYS_90(90, "90");

    /** The time in milliseconds. deletedAt is a point in time, so the count goes on while the app is closed. */
    val millis: Long get() = days * 24L * 60L * 60L * 1000L

    /** The text in Settings and in the dialog, for example "30 days". */
    fun label(): String = "$days days"

    /** True when this is a shorter time than [other]. Switching to a shorter time deletes notes. */
    fun isShorterThan(other: TrashRetention): Boolean = days < other.days

    companion object {
        val DEFAULT = DAYS_30

        /** Turns the saved value back into a choice. Nothing saved, or anything unknown, gives [DEFAULT]. */
        fun fromStorage(stored: String?): TrashRetention =
            entries.firstOrNull { it.storageValue == stored } ?: DEFAULT
    }
}

private const val TRASH_SETTINGS_FILE = "elnor_settings"
private const val KEY_TRASH_RETENTION = "trash_retention_days"

fun loadTrashRetention(context: Context): TrashRetention =
    TrashRetention.fromStorage(
        context.applicationContext
            .getSharedPreferences(TRASH_SETTINGS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_TRASH_RETENTION, null)
    )

fun saveTrashRetention(context: Context, value: TrashRetention) {
    context.applicationContext
        .getSharedPreferences(TRASH_SETTINGS_FILE, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_TRASH_RETENTION, value.storageValue)
        .apply()
}
