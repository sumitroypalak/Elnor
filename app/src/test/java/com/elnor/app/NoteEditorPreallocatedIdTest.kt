package com.elnor.app

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A new note chooses its id when its page opens. So onStop never waits for the first save, and a
 * page that comes back after the app process was closed saves under the same id: one copy of the
 * note, whether or not the first save had finished.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorPreallocatedIdTest {

    private val delayMs = 1_000L
    private val key = NoteEditorViewModel.KEY_DRAFT_NOTE_ID

    private fun TestScope.settle() {
        advanceTimeBy(delayMs * 3)
        runCurrent()
    }

    /** Behaves like the real storage: ids are given by the caller, update tells if the note exists. */
    private class IdRepository : NoteRepository {
        val stored = linkedMapOf<Long, Note>()
        val insertedIds = mutableListOf<Long>()
        val trashed = mutableListOf<Long>()

        /** When set, insertWithId waits for it (a very slow disk). */
        var insertGate: CompletableDeferred<Unit>? = null

        override val notes: StateFlow<List<Note>> = MutableStateFlow(emptyList())
        override val trashedNotes: Flow<List<Note>> = flowOf(emptyList())
        override fun search(query: String): Flow<List<Note>> = flowOf(emptyList())

        override suspend fun insert(note: Note): Long = throw AssertionError("must use insertWithId")

        override suspend fun insertWithId(note: Note): Long {
            insertGate?.await()
            check(note.id !in stored) { "id ${note.id} is taken" }
            stored[note.id] = note
            insertedIds.add(note.id)
            return note.id
        }

        override suspend fun update(note: Note) = throw AssertionError("must use updateIfExists")

        override suspend fun updateIfExists(note: Note): Boolean {
            if (note.id !in stored) return false
            stored[note.id] = note
            return true
        }

        override suspend fun moveToTrash(id: Long) {
            trashed.add(id)
        }

        override suspend fun setPinned(id: Long, pinned: Boolean) = Unit
        override suspend fun restore(id: Long) = Unit
        override suspend fun deletePermanently(id: Long) = Unit
        override suspend fun emptyTrash() = Unit
        override suspend fun exportAll(): List<Note> = emptyList()
        override suspend fun importNotes(notes: List<Note>) = ImportSummary(0, 0)
        override suspend fun purgeExpiredTrash(): Int = 0
    }

    private fun TestScope.viewModel(
        repo: IdRepository,
        state: SavedStateHandle,
        ids: Iterator<Long> = listOf(777L, 778L, 779L).iterator()
    ) = NoteEditorViewModel(
        repo, backgroundScope,
        autoSaveDelayMs = delayMs, timerScope = backgroundScope, savedState = state,
        newNoteId = { ids.next() }
    )

    @Test
    fun newPage_hasItsIdInTheSavedState_beforeAnythingIsSaved() = runTest {
        val state = SavedStateHandle()
        val vm = viewModel(IdRepository(), state)
        vm.open(null)

        assertEquals(777L, state.get<Long>(key))
    }

    @Test
    fun firstSave_usesTheIdChosenWhenThePageOpened() = runTest {
        val repo = IdRepository()
        val vm = viewModel(repo, SavedStateHandle())
        vm.open(null)
        vm.onBodyChange("hello")
        settle()

        assertEquals(listOf(777L), repo.insertedIds)
        assertEquals("hello", repo.stored.getValue(777L).body)
    }

    @Test
    fun onStop_doesNotWait_andTheIdIsThereWhileTheFirstSaveIsStillRunning() = runTest {
        val repo = IdRepository().apply { insertGate = CompletableDeferred() }
        val state = SavedStateHandle()
        val vm = viewModel(repo, state)
        vm.open(null)
        vm.onBodyChange("hello")

        vm.onStop() // returns at once, although the insert has not finished (and cannot)
        runCurrent()

        assertEquals(777L, state.get<Long>(key))
        assertTrue("the insert is still waiting", repo.stored.isEmpty())
    }

    @Test
    fun processClosedBeforeTheFirstSaveFinished_pageComesBack_insertsOnceUnderTheSameId() = runTest {
        // First life: the app goes to the background, the insert never finishes, the process dies.
        val repo = IdRepository()
        val state = SavedStateHandle(mapOf(key to 777L))

        // Second life: new ViewModel, same saved state, the text comes back with the page.
        val vm = viewModel(repo, state)
        vm.open(null)
        vm.onBodyChange("hello")
        settle()

        assertEquals("one copy, under the same id", listOf(777L), repo.insertedIds)
        assertEquals("hello", repo.stored.getValue(777L).body)
    }

    @Test
    fun processClosedAfterTheFirstSaveFinished_pageComesBack_updatesTheSameNote() = runTest {
        val repo = IdRepository()
        val first = viewModel(repo, SavedStateHandle())
        first.open(null)
        first.onBodyChange("hello")
        settle()
        assertEquals(listOf(777L), repo.insertedIds)

        val second = viewModel(repo, SavedStateHandle(mapOf(key to 777L)))
        second.open(null)
        second.onBodyChange("hello again")
        settle()

        assertEquals("no second copy", listOf(777L), repo.insertedIds)
        assertEquals("hello again", repo.stored.getValue(777L).body)
    }

    @Test
    fun noteThatWasEmptiedAndTrashed_getsANewId_whenTextIsTypedAgain() = runTest {
        val repo = IdRepository()
        val state = SavedStateHandle()
        val vm = viewModel(repo, state)
        vm.open(null)
        vm.onBodyChange("first")
        settle()
        assertEquals(listOf(777L), repo.insertedIds)

        vm.onBodyChange("")
        vm.onStop() // a final save of an empty text moves the note to the Trash
        settle()
        assertEquals(listOf(777L), repo.trashed)

        vm.onBodyChange("second")
        settle()

        assertEquals("the trashed note's id is not used again", listOf(777L, 778L), repo.insertedIds)
        assertEquals(778L, state.get<Long>(key))
    }

    @Test
    fun randomNoteId_isInTheSafeRange() {
        repeat(1_000) {
            val id = NoteEditorViewModel.randomNoteId()
            assertTrue(id >= (1L shl 41) && id < (1L shl 52))
        }
    }
}
