package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Archive in the backup file: archivedAt is written, read again, and optional in old files. */
class BackupArchiveTest {

    private fun success(text: String) = BackupFormat.fromJson(text) as BackupParseResult.Success

    @Test
    fun roundTrip_keepsArchivedAt_alsoTogetherWithAPin() {
        val notes = listOf(
            Note(0L, "Normal", "a", 10L),
            Note(0L, "Archived", "b", 20L, archivedAt = 77L),
            Note(0L, "Archived and pinned", "c", 30L, pinnedAt = 5L, archivedAt = 88L)
        )

        val result = success(BackupFormat.toJson(notes, exportedAt = 1L))

        assertEquals(notes, result.notes)
        assertEquals(0, result.skipped)
    }

    @Test
    fun toJson_writesArchivedAtAsNullForANormalNote() {
        val json = BackupFormat.toJson(listOf(Note(0L, "t", "b", 1L)), 1L)

        assertTrue(json.contains("\"archivedAt\": null"))
    }

    @Test
    fun anOlderFileWithoutArchivedAt_hasNoArchivedNotes() {
        val old = """
            {"app":"elnor","formatVersion":1,"exportedAt":1,"noteCount":1,
             "notes":[{"title":"Old","body":"x","updatedAt":5,"deletedAt":null,"pinnedAt":null}]}
        """.trimIndent()

        val note = success(old).notes.single()

        assertNull(note.archivedAt)
    }

    @Test
    fun importDoesNotAddAnArchivedNoteTwice() {
        val stored = listOf(Note(3L, "Archived", "b", 20L, archivedAt = 77L))
        val incoming = success(BackupFormat.toJson(stored, 1L)).notes

        assertTrue(BackupFormat.selectNew(stored, incoming).isEmpty())
    }
}
