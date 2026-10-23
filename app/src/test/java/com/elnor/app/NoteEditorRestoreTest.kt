package com.elnor.app

import androidx.lifecycle.SavedStateHandle
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
import org.junit.Test

/**
 * The system can close the app process while the editor is open. The page comes back with its
 * typed text but with a new ViewModel. These tests check that a new note that was already saved
 * is then updated, and no second copy is inserted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorRestoreTest {

    private val delayMs = 1_000L

    /**
     * Moves the virtual clock and runs everything that is due. advanceUntilIdle() is not used: it
     * ignores the tasks of backgroundScope, and that is where the save queue and the timer run.
     */
    private fun TestScope.settle() {
        advanceTimeBy(delayMs * 3)
        runCurrent()
    }
    private val key = NoteEditorViewModel.KEY_DRAFT_NOTE_ID

    private class FakeRepository : NoteRepository {
        val inserted = mutableListOf<Note>()
        val updated = mutableListOf<Note>()
        val trashed = mutableListOf<Long>()
        private var nextId = 100L

        override val notes: StateFlow<List<Note>> = MutableStateFlow(emptyList())
        override val trashedNotes: Flow<List<Note>> = flowOf(emptyList())
        override fun search(query: String): Flow<List<Note>> = flowOf(emptyList())
        override suspend fun insert(note: Note): Long {
            inserted.add(note)
            return nextId++
        }
        override suspend fun update(note: Note) { updated.add(note) }
        override suspend fun moveToTrash(id: Long) { trashed.add(id) }
        override suspend fun setPinned(id: Long, pinned: Boolean) = Unit
        override suspend fun restore(id: Long) = Unit
        override suspend fun deletePermanently(id: Long) = Unit
        override suspend fun emptyTrash() = Unit
        override suspend fun exportAll(): List<Note> = emptyList()
        override suspend fun importNotes(notes: List<Note>) = ImportSummary(0, 0)
        override suspend fun purgeExpiredTrash(): Int = 0
    }

    private fun TestScope.viewModel(repo: FakeRepository, state: SavedStateHandle) =
        NoteEditorViewModel(
            repo, backgroundScope,
            autoSaveDelayMs = delayMs, timerScope = backgroundScope, savedState = state
        )

    @Test
    fun firstSaveOfANewNote_isRememberedInTheSavedState() = runTest {
        val state = SavedStateHandle()
        val vm = viewModel(FakeRepository(), state)
        vm.open(null)
        vm.onBodyChange("hello")
        advanceTimeBy(delayMs + 1)
        settle()

        assertEquals(100L, state.get<Long>(key))
    }

    @Test
    fun restartedPage_updatesTheSavedNote_insteadOfInsertingAgain() = runTest {
        val repo = FakeRepository()
        val state = SavedStateHandle()
        val first = viewModel(repo, state)
        first.open(null)
        first.onBodyChange("hello")
        advanceTimeBy(delayMs + 1)
        settle()
        assertEquals(1, repo.inserted.size)

        // The process is closed and started again: new ViewModel, same saved state, text restored
        val second = viewModel(repo, SavedStateHandle(mapOf(key to 100L)))
        second.open(null)
        second.onBodyChange("hello again")
        advanceTimeBy(delayMs + 1)
        settle()

        assertEquals("no second copy", 1, repo.inserted.size)
        assertEquals(1, repo.updated.size)
        assertEquals(100L, repo.updated[0].id)
        assertEquals("hello again", repo.updated[0].body)
    }

    @Test
    fun restartedPage_showsTheDeleteButton() = runTest {
        val vm = viewModel(FakeRepository(), SavedStateHandle(mapOf(key to 100L)))
        vm.open(null)
        assertEquals(true, vm.hasSavedNote.value)
    }

    @Test
    fun aNormalNewNote_withNothingRemembered_isInserted() = runTest {
        val repo = FakeRepository()
        val vm = viewModel(repo, SavedStateHandle())
        vm.open(null)
        vm.onBodyChange("fresh")
        advanceTimeBy(delayMs + 1)
        settle()

        assertEquals(1, repo.inserted.size)
        assertEquals(0, repo.updated.size)
    }

    @Test
    fun closingThePage_forgetsTheId() = runTest {
        val state = SavedStateHandle()
        val vm = viewModel(FakeRepository(), state)
        vm.open(null)
        vm.onBodyChange("hello")
        advanceTimeBy(delayMs + 1)
        settle()
        assertEquals(100L, state.get<Long>(key))

        vm.onBack()
        settle()
        assertNull(state.get<Long>(key))
    }

    @Test
    fun deletingTheNote_forgetsTheId() = runTest {
        val state = SavedStateHandle()
        val repo = FakeRepository()
        val vm = viewModel(repo, state)
        vm.open(null)
        vm.onBodyChange("hello")
        advanceTimeBy(delayMs + 1)
        settle()

        vm.deleteNote()
        settle()
        assertNull(state.get<Long>(key))
        assertEquals(listOf(100L), repo.trashed)
    }

    @Test
    fun aPageForAnExistingNote_ignoresAndClearsAStaleId() = runTest {
        val repo = FakeRepository()
        val state = SavedStateHandle(mapOf(key to 100L))
        val vm = viewModel(repo, state)
        vm.open(Note(id = 7L, title = "t", body = "old", updatedAt = 1L))
        vm.onBodyChange("new")
        advanceTimeBy(delayMs + 1)
        settle()

        assertEquals(7L, repo.updated.single().id)
        assertNull(state.get<Long>(key))
    }

    @Test
    fun goingToTheBackground_alsoRemembersTheId() = runTest {
        val state = SavedStateHandle()
        val vm = viewModel(FakeRepository(), state)
        vm.open(null)
        vm.onBodyChange("hello")
        vm.onStop() // saved right away, not after the pause
        settle()
        // The test version does not wait, so the id arrives through the normal publish
        assertEquals(100L, state.get<Long>(key))
    }
}
