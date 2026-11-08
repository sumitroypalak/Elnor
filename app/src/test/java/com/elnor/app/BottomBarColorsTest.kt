package com.elnor.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The colours of the bottom bar follow the theme: a white bar in the light theme, a dark bar in the
 * dark theme, and the "New note" button in the accent colour in both. Each colour is checked next to
 * the colour that is drawn on it, so the icons and names stay readable.
 */
class BottomBarColorsTest {

    private val light = elnorColorScheme(dark = false)
    private val dark = elnorColorScheme(dark = true)

    @Test
    fun lightTheme_hasAWhiteBar_withDarkText() {
        val colors = bottomBarColors(light, dark = false)
        assertEquals(Color(0xFFFFFFFF), colors.bar)
        assertEquals(Color(0xFF1A1B20), colors.onBar)
    }

    @Test
    fun lightTheme_selectedTabIsStrongBlue_withWhiteText() {
        val colors = bottomBarColors(light, dark = false)
        assertEquals(Color(0xFF0058CA), colors.highlight)
        assertEquals(Color(0xFFFFFFFF), colors.onHighlight)
    }

    @Test
    fun lightTheme_newNoteButtonIsBlue_withWhitePlus() {
        val colors = bottomBarColors(light, dark = false)
        assertEquals(Color(0xFF0058CA), colors.newNote)
        assertEquals(Color(0xFFFFFFFF), colors.onNewNote)
    }

    @Test
    fun darkTheme_keepsTheMeasuredBarAndSelectedTab() {
        // These two were read from the owner's screenshot and must not change
        val colors = bottomBarColors(dark, dark = true)
        assertEquals(Color(0xFF1E1F25), colors.bar)
        assertEquals(Color(0xFF00419C), colors.highlight)
        assertEquals(Color(0xFFE2E2E9), colors.onBar)
        assertEquals(Color(0xFFE2E2E9), colors.onHighlight)
    }

    @Test
    fun darkTheme_newNoteButtonIsLightBlue_withDarkBluePlus() {
        val colors = bottomBarColors(dark, dark = true)
        assertEquals(Color(0xFFB0C6FF), colors.newNote)
        assertEquals(Color(0xFF002D6E), colors.onNewNote)
    }

    @Test
    fun theNewNoteButtonStandsOutFromTheBar_inBothThemes() {
        assertNotEquals(
            bottomBarColors(light, dark = false).bar,
            bottomBarColors(light, dark = false).newNote
        )
        assertNotEquals(
            bottomBarColors(dark, dark = true).bar,
            bottomBarColors(dark, dark = true).newNote
        )
    }

    @Test
    fun textIsNeverTheSameColourAsWhatItSitsOn() {
        for ((scheme, isDark) in listOf(light to false, dark to true)) {
            val colors = bottomBarColors(scheme, isDark)
            assertNotEquals("bar, dark=$isDark", colors.bar, colors.onBar)
            assertNotEquals("highlight, dark=$isDark", colors.highlight, colors.onHighlight)
            assertNotEquals("new note, dark=$isDark", colors.newNote, colors.onNewNote)
        }
    }
}
