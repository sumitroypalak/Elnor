package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteCardColorTest {
    @Test
    fun sameId_alwaysSameColor() {
        assertEquals(cardColorIndex(42L, 4), cardColorIndex(42L, 4))
    }

    @Test
    fun followsIdModuloColorCount() {
        assertEquals(1, cardColorIndex(1L, 4))
        assertEquals(0, cardColorIndex(8L, 4))
        assertEquals(3, cardColorIndex(7L, 4))
    }

    @Test
    fun neighbouringIds_getDifferentColors() {
        assertTrue(cardColorIndex(10L, 4) != cardColorIndex(11L, 4))
    }

    @Test
    fun alwaysInsideTheRange_alsoForHugeAndNegativeIds() {
        for (id in listOf(0L, 1L, Long.MAX_VALUE, -1L, Long.MIN_VALUE)) {
            val index = cardColorIndex(id, 4)
            assertTrue("id $id gave $index", index in 0..3)
        }
    }
}
