package com.elnor.app

/**
 * The three choices in Settings > Appearance.
 *
 * [storageValue] is what is saved on the phone. It is a fixed text, not the enum name, so
 * renaming an entry later cannot break saved settings.
 */
enum class ThemeMode(val storageValue: String) {
    /** Follow the phone's light/dark setting, also when it changes while the app is open. */
    SYSTEM("system"),

    /** Always light, whatever the phone uses. */
    LIGHT("light"),

    /** Always dark, whatever the phone uses. */
    DARK("dark");

    /** True if the dark theme must be used now. [systemDark] = the phone is in dark mode right now. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /**
         * Turns saved values back into a mode.
         *
         * @param stored the value saved by the 3-option selector (null if nothing was saved)
         * @param legacyDark the old 2-state switch: true = dark, false = light, null = never used.
         *        It is only used when [stored] is missing, so people who already chose Light or
         *        Dark with the old switch keep their choice after the update.
         *
         * Anything unknown ends as SYSTEM, which is also the default for a new install.
         */
        fun fromStorage(stored: String?, legacyDark: Boolean?): ThemeMode {
            val saved = ThemeMode.entries.firstOrNull { it.storageValue == stored }
            if (saved != null) return saved
            return when (legacyDark) {
                true -> DARK
                false -> LIGHT
                null -> SYSTEM
            }
        }
    }
}
