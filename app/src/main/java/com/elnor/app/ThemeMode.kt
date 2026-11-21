package com.elnor.app

/**
 * The four choices in Settings > Appearance.
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
    DARK("dark"),

    /**
     * Always dark, with a pure black page (#000000) instead of the dark blue-grey one. On an
     * AMOLED screen black pixels are switched off, which saves battery. It is the dark theme in
     * every other way (the same text, accent and card colours, and the same dark-theme looks).
     */
    AMOLED("amoled");

    /** True if the dark theme must be used now. [systemDark] = the phone is in dark mode right now. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
        AMOLED -> true
    }

    /** True for the pure black theme. It is always a dark theme too ([isDark] is true). */
    val isAmoled: Boolean get() = this == AMOLED

    companion object {
        /**
         * Turns saved values back into a mode.
         *
         * @param stored the value saved by the theme selector (null if nothing was saved)
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
