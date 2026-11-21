package com.elnor.app

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure black (AMOLED) theme, the fourth choice of Settings > Appearance > Theme.
 */
class AmoledThemeTest {

    private val dark = elnorColorScheme(dark = true)
    private val amoled = elnorColorScheme(dark = true, amoled = true)

    /**
     * ColorScheme has no equals (two schemes with the same colours are still two objects), so
     * assertEquals on them always fails. Its toString lists every colour, so the text is compared.
     */
    private fun assertSameColours(expected: ColorScheme, actual: ColorScheme) {
        assertEquals(expected.toString(), actual.toString())
    }

    // ---- ThemeMode ----

    @Test
    fun amoled_isAlwaysDark_whateverThePhoneUses() {
        assertTrue(ThemeMode.AMOLED.isDark(systemDark = false))
        assertTrue(ThemeMode.AMOLED.isDark(systemDark = true))
    }

    @Test
    fun onlyAmoled_isAmoled() {
        assertTrue(ThemeMode.AMOLED.isAmoled)
        for (mode in listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)) {
            assertFalse("$mode must not be pure black", mode.isAmoled)
        }
    }

    @Test
    fun amoled_isSavedAndReadBack() {
        assertEquals("amoled", ThemeMode.AMOLED.storageValue)
        assertEquals(ThemeMode.AMOLED, ThemeMode.fromStorage("amoled", legacyDark = null))
    }

    @Test
    fun amoled_savedValueWinsOverTheOldSwitch() {
        assertEquals(ThemeMode.AMOLED, ThemeMode.fromStorage("amoled", legacyDark = false))
        assertEquals(ThemeMode.AMOLED, ThemeMode.fromStorage("amoled", legacyDark = true))
    }

    @Test
    fun theOtherChoices_keepTheirSavedValues() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("system", legacyDark = null))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorage("light", legacyDark = null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("dark", legacyDark = null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("unknown", legacyDark = null))
    }

    @Test
    fun storageValues_areAllDifferent() {
        val values = ThemeMode.entries.map { it.storageValue }
        assertEquals(values.size, values.toSet().size)
        assertEquals(4, values.size)
    }

    // ---- Colours ----

    @Test
    fun page_isPureBlack() {
        assertEquals(Color(0xFF000000), amoled.background)
        assertEquals(Color(0xFF000000), amoled.surfaceDim)
        assertEquals(Color(0xFF000000), amoled.surfaceContainerLowest)
    }

    @Test
    fun cards_standOutFromTheBlackPage() {
        assertNotEquals(amoled.background, amoled.surface)
        assertEquals(dark.surface, amoled.surface) // cards, search field, bottom bar are unchanged
    }

    @Test
    fun textAndAccents_areTheSameAsInTheDarkTheme() {
        assertEquals(dark.onBackground, amoled.onBackground)
        assertEquals(dark.onSurface, amoled.onSurface)
        assertEquals(dark.primary, amoled.primary)
        assertEquals(dark.primaryContainer, amoled.primaryContainer)
        assertEquals(dark.secondaryContainer, amoled.secondaryContainer)
        assertEquals(dark.error, amoled.error)
    }

    @Test
    fun theNormalDarkTheme_isNotChanged() {
        assertEquals(Color(0xFF121318), dark.background)
        assertSameColours(dark, elnorColorScheme(dark = true, amoled = false))
    }

    @Test
    fun lightTheme_ignoresAmoled() {
        assertSameColours(elnorColorScheme(dark = false), elnorColorScheme(dark = false, amoled = true))
    }

    @Test
    fun withoutContext_theElnorAmoledColoursAreUsed() {
        // No context (like a phone that cannot do wallpaper colours): the Elnor colours
        val scheme = elnorColorScheme(dark = true, context = null, dynamic = true, amoled = true)
        assertSameColours(amoled, scheme)
        assertSameColours(
            dark,
            elnorColorScheme(dark = true, context = null, dynamic = true, amoled = false)
        )
    }
}
