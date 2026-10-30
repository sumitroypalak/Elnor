package com.elnor.app

import android.content.Context

/**
 * The three choices of Settings > Appearance > Font size. It changes the size of all text in the
 * app (notes, lists, settings), which helps when reading Bengali on a small screen.
 *
 * [scale] is what every text size is multiplied by: [MEDIUM] is 1, the size the app always had.
 * It comes on top of the phone's own font size setting, it does not replace it.
 *
 * [storageValue] is what is saved on the phone: a fixed text, not the enum name, so renaming an
 * entry later cannot break saved settings.
 */
enum class FontSizeChoice(val storageValue: String, val scale: Float) {
    SMALL("small", 0.9f),
    MEDIUM("medium", 1.0f),
    LARGE("large", 1.2f);

    /** The text in Settings and in the dialog. */
    fun label(): String = when (this) {
        SMALL -> "Small"
        MEDIUM -> "Medium"
        LARGE -> "Large"
    }

    companion object {
        val DEFAULT = MEDIUM

        /** Turns the saved value back into a choice. Nothing saved, or anything unknown, gives [DEFAULT]. */
        fun fromStorage(stored: String?): FontSizeChoice =
            entries.firstOrNull { it.storageValue == stored } ?: DEFAULT
    }
}

private const val FONT_SETTINGS_FILE = "elnor_settings"
private const val KEY_FONT_SIZE = "font_size"

internal fun loadFontSizeChoice(context: Context): FontSizeChoice =
    FontSizeChoice.fromStorage(
        context.applicationContext
            .getSharedPreferences(FONT_SETTINGS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_FONT_SIZE, null)
    )

internal fun saveFontSizeChoice(context: Context, value: FontSizeChoice) {
    context.applicationContext
        .getSharedPreferences(FONT_SETTINGS_FILE, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_FONT_SIZE, value.storageValue)
        .apply()
}
