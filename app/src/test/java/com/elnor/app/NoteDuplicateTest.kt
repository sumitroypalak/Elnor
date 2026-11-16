package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteDuplicateTest {

    private val original = Note(
        id = 7L,
        title = "Shopping",
        body = "milk\neggs",
        updatedAt = 1_000L,
        deletedAt = null,
        pinnedAt = 500L,
        archivedAt = null
    )

    @Test
    fun copyKeepsTheTextAndAddsTheSuffixToTheTitle() {
        val copy = duplicateOf(original, now = 5_000L)
        assertEquals("Shopping (copy)", copy.title)
        assertEquals("milk\neggs", copy.body)
    }

    @Test
    fun copyGetsTheNewTimeAndNoId() {
        val copy = duplicateOf(original, now = 5_000L)
        assertEquals(5_000L, copy.updatedAt)
        assertEquals(0L, copy.id)
    }

    @Test
    fun copyIsNotPinnedArchivedOrDeleted() {
        val copy = duplicateOf(
            original.copy(archivedAt = 800L, deletedAt = 900L),
            now = 5_000L
        )
        assertNull(copy.pinnedAt)
        assertNull(copy.archivedAt)
        assertNull(copy.deletedAt)
    }

    @Test
    fun aNoteWithoutATitleStaysWithoutATitle() {
        val copy = duplicateOf(original.copy(title = ""), now = 5_000L)
        assertEquals("", copy.title)
    }

    @Test
    fun theOriginalIsNotChanged() {
        duplicateOf(original, now = 5_000L)
        assertEquals("Shopping", original.title)
        assertEquals(500L, original.pinnedAt)
    }
}
