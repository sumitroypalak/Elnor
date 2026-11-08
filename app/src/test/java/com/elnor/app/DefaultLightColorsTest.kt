package com.elnor.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The default light look of the app: the blue palette of the dark theme in its light steps. These
 * values are not measured from a screenshot (see elnorLightColorScheme), so the test only keeps
 * them from changing by accident and makes sure the old purple is gone.
 */
class DefaultLightColorsTest {

    private val light = elnorColorScheme(dark = false)

    @Test
    fun accent_isBlue() {
        assertEquals(Color(0xFF0058CA), light.primary)
        assertEquals(Color(0xFFD9E2FF), light.primaryContainer)
        assertEquals(Color(0xFF001945), light.onPrimaryContainer)
    }

    @Test
    fun pageIsASoftTint_andCardsAreWhite() {
        assertEquals(Color(0xFFF4F3FA), light.background)
        assertEquals(Color(0xFFFFFFFF), light.surface)
        assertNotEquals(light.background, light.surface)
    }

    @Test
    fun bottomBarPair_isDarkWithLightText() {
        // The bar of the light theme is drawn with these two
        assertEquals(Color(0xFF2F3036), light.inverseSurface)
        assertEquals(Color(0xFFF1F0F7), light.inverseOnSurface)
    }

    @Test
    fun theOldPurpleIsGone() {
        val oldPurple = listOf(
            Color(0xFF6257D9), Color(0xFFE4E0FF), Color(0xFF1B1464), Color(0xFFF5F5FC)
        )
        val used = listOf(
            light.primary, light.primaryContainer, light.onPrimaryContainer, light.background
        )
        for (color in used) {
            assertFalse("old colour still used: $color", color in oldPurple)
        }
    }

    @Test
    fun darkTheme_isTheSameBlueFamily() {
        // The container colours of both themes are the same palette: the "on" colour of one theme is
        // the container colour of the other
        val dark = elnorColorScheme(dark = true)
        assertEquals(light.secondaryContainer, dark.onSecondaryContainer)
        assertEquals(light.primaryContainer, dark.onPrimaryContainer)
        assertEquals(light.inversePrimary, dark.primary)
        assertEquals(dark.inversePrimary, light.primary)
    }
}
