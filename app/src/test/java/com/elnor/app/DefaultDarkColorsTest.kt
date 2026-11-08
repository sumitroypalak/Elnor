package com.elnor.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The default dark look of the app. The expected values were measured on a screenshot of the app
 * that the owner chose as the look to keep, so a later change of the palette cannot slip in
 * unnoticed.
 */
class DefaultDarkColorsTest {

    private val dark = elnorColorScheme(dark = true)

    @Test
    fun page_andCards_haveTheMeasuredColours() {
        assertEquals(Color(0xFF121318), dark.background)
        assertEquals(Color(0xFF1E1F25), dark.surface) // cards, search field, bottom bar
    }

    @Test
    fun text_hasTheMeasuredColour() {
        assertEquals(Color(0xFFE2E2E9), dark.onBackground)
        assertEquals(Color(0xFFE2E2E9), dark.onSurface)
    }

    @Test
    fun accent_hasTheMeasuredColours() {
        assertEquals(Color(0xFFB0C6FF), dark.primary)
        assertEquals(Color(0xFF00419C), dark.primaryContainer) // selected tab of the bottom bar
        assertEquals(Color(0xFF404659), dark.secondaryContainer) // menu button
        assertEquals(Color(0xFFDCE2F9), dark.onSecondaryContainer)
    }

    @Test
    fun cardsStandOutFromThePage() {
        assertNotEquals(dark.background, dark.surface)
    }

    @Test
    fun theOldPurpleIsGone() {
        val oldPurple = listOf(
            Color(0xFFB9B5FF), Color(0xFF433AA8), Color(0xFF10111B),
            Color(0xFF1C1D2B), Color(0xFFE4E0FF)
        )
        val used = listOf(
            dark.primary, dark.primaryContainer, dark.onPrimaryContainer,
            dark.background, dark.surface
        )
        for (color in used) {
            assertFalse("old colour still used: $color", color in oldPurple)
        }
    }

    @Test
    fun lightTheme_isNotChanged() {
        val light = elnorColorScheme(dark = false)
        assertEquals(Color(0xFFF5F5FC), light.background)
        assertEquals(Color(0xFFFFFFFF), light.surface)
        assertEquals(Color(0xFF6257D9), light.primary)
    }
}
