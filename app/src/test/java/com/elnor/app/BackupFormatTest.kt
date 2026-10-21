package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {

    private fun note(
        title: String = "Title",
        body: String = "Body",
        updatedAt: Long = 1_000L,
        id: Long = 0L,
        deletedAt: Long? = null,
        pinnedAt: Long? = null
    ) = Note(id, title, body, updatedAt, deletedAt, pinnedAt)

    private fun success(text: String) = BackupFormat.fromJson(text) as BackupParseResult.Success

    private fun failure(text: String) =
        (BackupFormat.fromJson(text) as BackupParseResult.Failure).reason

    // ---- Writing and reading again ----

    @Test
    fun roundTrip_keepsEveryField() {
        val notes = listOf(
            note("Shopping", "milk\neggs", 10L),
            note("Pinned", "x", 20L, pinnedAt = 99L),
            note("In the Trash", "y", 30L, deletedAt = 55L)
        )

        val result = success(BackupFormat.toJson(notes, exportedAt = 5L))

        assertEquals(notes, result.notes)
        assertEquals(0, result.skipped)
    }

    @Test
    fun roundTrip_keepsBengaliEmojiQuotesAndLineBreaks() {
        val tricky = note(
            title = "আমার নোট \"quoted\" \\ backslash",
            body = "প্রথম লাইন\n\tTab, emoji 😀, য় = য়, line\r\nbreak"
        )

        val result = success(BackupFormat.toJson(listOf(tricky), 1L))

        assertEquals(listOf(tricky), result.notes)
    }

    @Test
    fun roundTrip_dropsTheDatabaseId() {
        val result = success(BackupFormat.toJson(listOf(note(id = 42L)), 1L))

        assertEquals(0L, result.notes.single().id)
    }

    @Test
    fun toJson_writesHeaderAndNullsForMissingTimes() {
        val json = BackupFormat.toJson(listOf(note()), exportedAt = 7L)

        assertTrue(json.contains("\"app\": \"elnor\""))
        assertTrue(json.contains("\"formatVersion\": 1"))
        assertTrue(json.contains("\"noteCount\": 1"))
        assertTrue(json.contains("\"deletedAt\": null"))
        assertTrue(json.contains("\"pinnedAt\": null"))
    }

    @Test
    fun emptyList_writesAFileThatSaysNoNotes() {
        val json = BackupFormat.toJson(emptyList(), 1L)

        assertEquals(BackupError.NO_NOTES, failure(json))
    }

    // ---- Files that cannot be used ----

    @Test
    fun text_thatIsNotJson_isRefused() {
        assertEquals(BackupError.NOT_JSON, failure("hello, this is not json"))
        assertEquals(BackupError.NOT_JSON, failure(""))
    }

    @Test
    fun json_fromAnotherApp_isRefused() {
        assertEquals(BackupError.NOT_ELNOR, failure("""{"app":"other","formatVersion":1,"notes":[]}"""))
        assertEquals(BackupError.NOT_ELNOR, failure("""{"notes":[]}"""))
    }

    @Test
    fun missingOrZeroVersion_isRefused() {
        assertEquals(BackupError.NOT_ELNOR, failure("""{"app":"elnor","notes":[]}"""))
        assertEquals(BackupError.NOT_ELNOR, failure("""{"app":"elnor","formatVersion":0,"notes":[]}"""))
    }

    @Test
    fun newerVersion_isRefusedInsteadOfHalfRead() {
        val text = """{"app":"elnor","formatVersion":2,"notes":[{"title":"a","body":"b","updatedAt":1}]}"""

        assertEquals(BackupError.TOO_NEW, failure(text))
    }

    @Test
    fun missingNotesList_isRefused() {
        assertEquals(BackupError.NO_NOTES, failure("""{"app":"elnor","formatVersion":1}"""))
        assertEquals(BackupError.NO_NOTES, failure("""{"app":"elnor","formatVersion":1,"notes":[]}"""))
    }

    @Test
    fun byteOrderMarkAtTheStart_isIgnored() {
        val text = "﻿" + BackupFormat.toJson(listOf(note()), 1L)

        assertEquals(1, success(text).notes.size)
    }

    // ---- Damaged entries ----

    @Test
    fun damagedEntries_areSkippedAndCounted_theGoodOnesStay() {
        val text = """
            {"app":"elnor","formatVersion":1,"notes":[
              {"title":"good","body":"ok","updatedAt":5},
              {"title":"no time","body":"x"},
              {"title":"  ","body":"","updatedAt":6},
              "not an object",
              {"title":"time is text","body":"x","updatedAt":"abc"},
              {"title":null,"body":"only body","updatedAt":7}
            ]}
        """.trimIndent()

        val result = success(text)

        assertEquals(listOf("good", ""), result.notes.map { it.title })
        assertEquals("only body", result.notes[1].body) // a JSON null title is empty, not the text "null"
        assertEquals(4, result.skipped)
    }

    @Test
    fun optionalFields_mayBeMissing() {
        val result = success("""{"app":"elnor","formatVersion":1,"notes":[{"title":"a","body":"b","updatedAt":3}]}""")

        val only = result.notes.single()
        assertNull(only.deletedAt)
        assertNull(only.pinnedAt)
    }

    // ---- Adding without making copies ----

    @Test
    fun selectNew_skipsNotesThatAreAlreadyStored() {
        val existing = listOf(note("A", "a", 1L, id = 10L), note("B", "b", 2L, id = 11L))
        val incoming = listOf(note("A", "a", 1L), note("C", "c", 3L))

        val fresh = BackupFormat.selectNew(existing, incoming)

        assertEquals(listOf("C"), fresh.map { it.title })
    }

    @Test
    fun selectNew_ignoresIdPinAndTrashState() {
        val existing = listOf(note("A", "a", 1L, id = 5L, pinnedAt = 9L))
        val incoming = listOf(note("A", "a", 1L, id = 0L, deletedAt = 7L))

        assertTrue(BackupFormat.selectNew(existing, incoming).isEmpty())
    }

    @Test
    fun selectNew_aNoteEditedLater_comesInAsAnotherNote_nothingIsOverwritten() {
        val existing = listOf(note("A", "old text", 1L, id = 5L))
        val incoming = listOf(note("A", "new text", 2L))

        assertEquals(1, BackupFormat.selectNew(existing, incoming).size)
    }

    @Test
    fun selectNew_aNoteTwiceInTheFile_isAddedOnce_andOrderIsKept() {
        val incoming = listOf(note("X", "x", 1L), note("Y", "y", 2L), note("X", "x", 1L))

        val fresh = BackupFormat.selectNew(emptyList(), incoming)

        assertEquals(listOf("X", "Y"), fresh.map { it.title })
    }

    @Test
    fun importingTheSameBackupTwice_addsNothingTheSecondTime() {
        val stored = listOf(note("A", "a", 1L, id = 1L))
        val backup = success(BackupFormat.toJson(stored, 1L)).notes

        assertTrue(BackupFormat.selectNew(stored, backup).isEmpty())
    }

    // ---- File name and messages ----

    @Test
    fun fileName_hasTheDateAndEndsWithJson() {
        val name = BackupFormat.fileName(1_760_000_000_000L)

        assertTrue(name, Regex("""elnor-backup-\d{4}-\d{2}-\d{2}-\d{4}\.json""").matches(name))
    }

    @Test
    fun exportMessage_saysOneNoteAndManyNotes() {
        assertEquals("Backup saved: 1 note.", BackupFormat.exportMessage(1))
        assertEquals("Backup saved: 12 notes.", BackupFormat.exportMessage(12))
    }

    @Test
    fun importMessage_tellsWhatWasAddedWhatWasLeftAndWhatWasBroken() {
        assertEquals(
            "Added 3 notes. 1 note already in Elnor, left unchanged. 2 notes in the file could not be read.",
            BackupFormat.importMessage(ImportSummary(added = 3, alreadyPresent = 1), skipped = 2)
        )
        assertEquals(
            "No new notes were added. 4 notes already in Elnor, left unchanged.",
            BackupFormat.importMessage(ImportSummary(added = 0, alreadyPresent = 4), skipped = 0)
        )
    }
}
