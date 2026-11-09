package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTest {

    @Test
    fun widthClassBreakpoints() {
        assertEquals(WidthClass.COMPACT, widthClassOf(360))
        assertEquals(WidthClass.COMPACT, widthClassOf(599))
        assertEquals(WidthClass.MEDIUM, widthClassOf(600))
        assertEquals(WidthClass.MEDIUM, widthClassOf(839))
        assertEquals(WidthClass.EXPANDED, widthClassOf(840))
        assertEquals(WidthClass.EXPANDED, widthClassOf(1280))
    }

    @Test
    fun heightClassBreakpoints() {
        assertEquals(HeightClass.COMPACT, heightClassOf(360))
        assertEquals(HeightClass.COMPACT, heightClassOf(479))
        assertEquals(HeightClass.MEDIUM, heightClassOf(480))
        assertEquals(HeightClass.MEDIUM, heightClassOf(899))
        assertEquals(HeightClass.EXPANDED, heightClassOf(900))
    }

    @Test
    fun phoneSidewaysHasCompactHeight() {
        assertTrue(WindowClass(widthDp = 800, heightDp = 360).compactHeight)
        assertFalse(WindowClass(widthDp = 360, heightDp = 800).compactHeight)
        assertFalse(WindowClass(widthDp = 1280, heightDp = 800).compactHeight)
    }

    @Test
    fun phonePortraitKeepsTwoGridColumns() {
        assertEquals(2, gridColumnCount(320))
        assertEquals(2, gridColumnCount(360))
        assertEquals(2, gridColumnCount(411))
        assertEquals(2, gridColumnCount(520))
    }

    @Test
    fun widerWindowsGetMoreColumns() {
        assertEquals(4, gridColumnCount(800)) // phone sideways
        assertEquals(4, gridColumnCount(840))
        assertTrue(gridColumnCount(1280) >= 4)
    }

    @Test
    fun gridColumnsStopGrowingAtTheMaxWidth() {
        // The list is not wider than HomeMaxWidth, so a huge window gets no extra columns
        assertEquals(gridColumnCount(1280), gridColumnCount(2560))
    }

    @Test
    fun gridNeverHasLessThanTwoColumns() {
        assertEquals(2, gridColumnCount(100))
        assertEquals(2, gridColumnCount(0))
    }

    @Test
    fun tabletUsesTwoPanes() {
        assertTrue(isTwoPane(WindowClass(widthDp = 1280, heightDp = 800)))
        assertTrue(isTwoPane(WindowClass(widthDp = 840, heightDp = 600)))
    }

    @Test
    fun phonesAndNarrowWindowsKeepOnePage() {
        assertFalse(isTwoPane(WindowClass(widthDp = 360, heightDp = 800))) // phone portrait
        assertFalse(isTwoPane(WindowClass(widthDp = 700, heightDp = 900))) // small tablet / fold
        assertFalse(isTwoPane(WindowClass(widthDp = 915, heightDp = 412))) // big phone sideways
    }

    @Test
    fun listPaneGridHasTwoColumns() {
        assertEquals(2, gridColumnCount(TwoPaneListWidth.value.toInt()))
    }

    @Test
    fun railIsOnlyForTabletsAndFoldables() {
        assertFalse(showsNavigationRail(WindowClass(widthDp = 360, heightDp = 800))) // phone portrait
        assertFalse(showsNavigationRail(WindowClass(widthDp = 915, heightDp = 412))) // phone sideways
        assertFalse(showsNavigationRail(WindowClass(widthDp = 700, heightDp = 900))) // small window
        assertTrue(showsNavigationRail(WindowClass(widthDp = 841, heightDp = 701)))  // open foldable
        assertTrue(showsNavigationRail(WindowClass(widthDp = 1280, heightDp = 800))) // tablet
    }

    @Test
    fun railAppearsTogetherWithTwoPanes() {
        for ((w, h) in listOf(360 to 800, 915 to 412, 700 to 900, 841 to 701, 1280 to 800, 840 to 479)) {
            val window = WindowClass(widthDp = w, heightDp = h)
            assertEquals("$w x $h", isTwoPane(window), showsNavigationRail(window))
        }
    }
}
