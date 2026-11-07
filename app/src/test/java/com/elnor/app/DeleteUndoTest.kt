package com.elnor.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Delete" has no confirm dialog. The notes go to the Trash and a message offers Undo. These tests
 * check the parts that do not need a screen: the message, the default "restore with the pin" of
 * the repository, and the id that the editor gives back when it deletes a note.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteUndoTest {

    private val delayMs = 1_000L

    private fun TestScope.settle() {
        advanceTimeBy(delayMs * 3)
        runCurrent()
    }

    /** Remembers what was asked. [restoreNotes] is NOT overridden: the default of the interface is tested. */
    private class FakeRepository : NoteRepository {
        val trashed = mutableListOf<Long>()
        val restored = mutableListOf<Long>()
        val pinCalls = mutableListOf<Pair<Long, Boolean>>()
        private var nextId = 100L

        override val notes: StateFlow<List<Note>> = MutableStateFlow(emptyList())
        override val trashedNotes: Flow<List<Note>> = flowOf(emptyList())
        override fun search(query: String): Flow<List<Note>> = flowOf(emptyList())
        override suspend fun insert(note: Note): Long = nextId++
        override suspend fun update(note: Note) = Unit
        override suspend fun moveToTrash(id: Long) { trashed.add(id) }
        override suspend fun setPinned(id: Long, pinned: Boolean) { pinCalls.add(id to pinned) }
        override suspend fun restore(id: Long) { restored.add(id) }
        override suspend fun deletePermanently(id: Long) = Unit
        override suspend fun emptyTrash() = Unit
        override suspend fun exportAll(): List<Note> = emptyList()
        override suspend fun importNotes(notes: List<Note>) = ImportSummary(0, 0)
        override suspend fun purgeExpiredTrash(): Int = 0
    }

    private fun note(id: Long, pinnedAt: Long? = null) =
        Note(id = id, title = "T$id", body = "B$id", updatedAt = 1L, pinnedAt = pinnedAt)

    // ---- The message ----

    @Test
    fun message_forOneNote_isSingular() {
        assertEquals("Note moved to Trash", deleteUndoMessage(1))
    }

    @Test
    fun message_forManyNotes_hasTheCount() {
        assertEquals("3 notes moved to Trash", deleteUndoMessage(3))
    }

    // ---- Undo gives the notes back, with their pin ----

    @Test
    fun restoreNotes_restoresEveryNote_andPinsOnlyTheOnesThatWerePinned() = runTest {
        val repo = FakeRepository()

        repo.restoreNotes(listOf(note(1, pinnedAt = 5L), note(2), note(3, pinnedAt = 9L)))

        assertEquals(listOf(1L, 2L, 3L), repo.restored)
        assertEquals(listOf(1L to true, 3L to true), repo.pinCalls)
    }

    @Test
    fun restoreNotes_withNoPinnedNote_doesNotPinAnything() = runTest {
        val repo = FakeRepository()

        repo.restoreNotes(listOf(note(1), note(2)))

        assertEquals(listOf(1L, 2L), repo.restored)
        assertTrue(repo.pinCalls.isEmpty())
    }

    @Test
    fun restoreNotes_withAnEmptyList_doesNothing() = runTest {
        val repo = FakeRepository()

        repo.restoreNotes(emptyList())

        assertTrue(repo.restored.isEmpty())
        assertTrue(repo.pinCalls.isEmpty())
    }

    // ---- The editor tells which note it deleted ----

    @Test
    fun deleteNote_ofASavedNote_returnsItsId_andTrashesIt() = runTest {
        val repo = FakeRepository()
        val vm = NoteEditorViewModel(repo, backgroundScope, autoSaveDelayMs = delayMs, timerScope = backgroundScope)
        vm.open(note(5L))

        val deletedId = vm.deleteNote()
        settle()

        assertEquals(5L, deletedId)
        assertEquals(listOf(5L), repo.trashed)
    }

    @Test
    fun deleteNote_ofANewNoteThatWasAlreadySaved_returnsTheIdItGot() = runTest {
        val repo = FakeRepository()
        val vm = NoteEditorViewModel(repo, backgroundScope, autoSaveDelayMs = delayMs, timerScope = backgroundScope)
        vm.open(null)
        vm.onBodyChange("hello")
        settle() // the first save: the fake storage gives id 100

        val deletedId = vm.deleteNote()
        settle()

        assertEquals(100L, deletedId)
        assertEquals(listOf(100L), repo.trashed)
    }

    @Test
    fun deleteNote_ofANoteThatWasNeverSaved_returnsNull() = runTest {
        val repo = FakeRepository()
        val vm = NoteEditorViewModel(repo, backgroundScope, autoSaveDelayMs = delayMs, timerScope = backgroundScope)
        vm.open(null)

        assertNull(vm.deleteNote())
        settle()

        assertTrue(repo.trashed.isEmpty())
    }

    @Test
    fun deleteNote_withNoPageOpen_returnsNull() = runTest {
        val repo = FakeRepository()
        val vm = NoteEditorViewModel(repo, backgroundScope, autoSaveDelayMs = delayMs, timerScope = backgroundScope)

        assertNull(vm.deleteNote())
    }

    @Test
    fun deleteNote_twice_onlyTheFirstOneReturnsTheId() = runTest {
        val repo = FakeRepository()
        val vm = NoteEditorViewModel(repo, backgroundScope, autoSaveDelayMs = delayMs, timerScope = backgroundScope)
        vm.open(note(5L))

        assertEquals(5L, vm.deleteNote())
        assertNull(vm.deleteNote())
        settle()

        assertEquals(listOf(5L), repo.trashed)
    }
}
