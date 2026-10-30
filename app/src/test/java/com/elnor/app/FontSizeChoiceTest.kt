package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FontSizeChoice: the Small / Medium / Large choice of Settings > Appearance > Font size. */
class FontSizeChoiceTest {

    @Test
    fun choicesAreSmallMediumLarge_inThisOrder() {
        assertEquals(
            listOf(FontSizeChoice.SMALL, FontSizeChoice.MEDIUM, FontSizeChoice.LARGE),
            FontSizeChoice.entries.toList()
        )
    }

    @Test
    fun mediumIsTheDefault_andKeepsTheSizeTheAppAlwaysHad() {
        assertEquals(FontSizeChoice.MEDIUM, FontSizeChoice.DEFAULT)
        assertEquals(1.0f, FontSizeChoice.MEDIUM.scale, 0f)
    }

    @Test
    fun scalesGetBigger_andStayInASaneRange() {
        val scales = FontSizeChoice.entries.map { it.scale }
        assertEquals(scales.sorted(), scales)
        assertTrue(scales.first() < 1f)
        assertTrue(scales.last() > 1f)
        // big enough to notice, small enough that buttons and chips still fit
        assertTrue(scales.all { it in 0.8f..1.3f })
    }

    @Test
    fun savedValuesComeBackAsTheSameChoice() {
        for (choice in FontSizeChoice.entries) {
            assertEquals(choice, FontSizeChoice.fromStorage(choice.storageValue))
        }
    }

    @Test
    fun nothingSavedOrUnknownGivesTheDefault() {
        assertEquals(FontSizeChoice.DEFAULT, FontSizeChoice.fromStorage(null))
        assertEquals(FontSizeChoice.DEFAULT, FontSizeChoice.fromStorage(""))
        assertEquals(FontSizeChoice.DEFAULT, FontSizeChoice.fromStorage("huge"))
        // the enum name is not what is saved
        assertEquals(FontSizeChoice.DEFAULT, FontSizeChoice.fromStorage("LARGE"))
    }

    @Test
    fun labels() {
        assertEquals("Small", FontSizeChoice.SMALL.label())
        assertEquals("Medium", FontSizeChoice.MEDIUM.label())
        assertEquals("Large", FontSizeChoice.LARGE.label())
    }

    @Test
    fun storageValuesAreDifferent() {
        val values = FontSizeChoice.entries.map { it.storageValue }
        assertEquals(values.size, values.toSet().size)
    }
}
