package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotePinMappingTest {

    @Test
    fun pinnedAt_survivesNoteToEntityAndBack() {
        val note = Note(id = 3L, title = "A", body = "B", updatedAt = 10L, pinnedAt = 55L)
        assertEquals(55L, note.toEntity().pinnedAt)
        assertEquals(note, note.toEntity().toNote())
    }

    @Test
    fun aNormalNote_isNotPinned() {
        val note = Note(id = 1L, title = "A", body = "B", updatedAt = 10L)
        assertNull(note.pinnedAt)
        assertNull(note.toEntity().pinnedAt)
        assertNull(note.toEntity().toNote().pinnedAt)
    }

    @Test
    fun theOldFourArgumentConstructorStillWorks() {
        val note = Note(1L, "T", "body", 0L)
        assertNull(note.deletedAt)
        assertNull(note.pinnedAt)
    }
}
