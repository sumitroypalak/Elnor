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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * A save that does not work (storage full, database error) must not be silent: the editor is
 * told ([NoteEditorViewModel.saveFailed]), and if the FINAL save fails after the page closed, the
 * text is kept in [NoteEditorViewModel.lostNote] so it can still be copied.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorSaveFailureTest {

    private val delayMs = 1_000L

    private fun TestScope.settle() {
        advanceTimeBy(delayMs * 3)
        runCurrent()
    }

    /** A storage that can be switched to "full": insert and update then throw. */
    private class FlakyRepository : NoteRepository {
        var failing = false
        val inserted = mutableListOf<Note>()
        val updated = mutableListOf<Note>()

        override val notes: StateFlow<List<Note>> = MutableStateFlow(emptyList())
        override val trashedNotes: Flow<List<Note>> = flowOf(emptyList())
        override fun search(query: String): Flow<List<Note>> = flowOf(emptyList())

        override suspend fun insert(note: Note): Long {
            if (failing) throw IOException("No space left on device")
            inserted.add(note)
            return 100L + inserted.size
        }

        override suspend fun update(note: Note) {
            if (failing) throw IOException("No space left on device")
            updated.add(note)
        }

        override suspend fun moveToTrash(id: Long) = Unit
        override suspend fun setPinned(id: Long, pinned: Boolean) = Unit
        override suspend fun restore(id: Long) = Unit
        override suspend fun deletePermanently(id: Long) = Unit
        override suspend fun emptyTrash() = Unit
        override suspend fun exportAll(): List<Note> = emptyList()
        override suspend fun importNotes(notes: List<Note>) = ImportSummary(0, 0)
        override suspend fun purgeExpiredTrash(): Int = 0
    }

    private fun TestScope.viewModel(repo: NoteRepository) =
        NoteEditorViewModel(repo, backgroundScope, autoSaveDelayMs = delayMs, timerScope = backgroundScope)

    @Test
    fun failedPauseSave_setsSaveFailed_andALaterSaveClearsIt() = runTest {
        val repo = FlakyRepository().apply { failing = true }
        val vm = viewModel(repo)
        vm.open(null)

        vm.onBodyChange("hello")
        settle()
        assertTrue(vm.saveFailed.value)
        assertTrue(repo.inserted.isEmpty())

        repo.failing = false
        vm.onBodyChange("hello again")
        settle()
        assertFalse(vm.saveFailed.value)
        assertEquals("hello again", repo.inserted.single().body)
    }

    @Test
    fun retrySave_savesTheSameTextWithoutTyping() = runTest {
        val repo = FlakyRepository().apply { failing = true }
        val vm = viewModel(repo)
        vm.open(null)
        vm.onBodyChange("keep me")
        settle()
        assertTrue(vm.saveFailed.value)

        repo.failing = false
        vm.retrySave()
        runCurrent()

        assertFalse(vm.saveFailed.value)
        assertEquals("keep me", repo.inserted.single().body)
    }

    @Test
    fun failedFinalSave_afterBack_keepsTheTextInLostNote() = runTest {
        val repo = FlakyRepository().apply { failing = true }
        val vm = viewModel(repo)
        vm.open(null)
        vm.onTitleChange("Shopping")
        vm.onBodyChange("milk, eggs")

        vm.onBack()
        runCurrent()

        assertEquals("Shopping\n\nmilk, eggs", vm.lostNote.value)
        assertFalse(vm.saveFailed.value) // the page is closed, so the editor has nothing to show
    }

    @Test
    fun clearLostNote_forgetsIt() = runTest {
        val repo = FlakyRepository().apply { failing = true }
        val vm = viewModel(repo)
        vm.open(null)
        vm.onBodyChange("text")
        vm.onBack()
        runCurrent()
        assertEquals("text", vm.lostNote.value)

        vm.clearLostNote()
        assertNull(vm.lostNote.value)
    }

    @Test
    fun finalSaveThatWorks_afterAFailedPauseSave_reportsNothing() = runTest {
        val repo = FlakyRepository().apply { failing = true }
        val vm = viewModel(repo)
        vm.open(null)
        vm.onBodyChange("draft")
        settle()
        assertTrue(vm.saveFailed.value)

        repo.failing = false
        vm.onBack()
        runCurrent()

        assertNull(vm.lostNote.value)
        assertFalse(vm.saveFailed.value)
        assertEquals("draft", repo.inserted.single().body)
    }

    @Test
    fun newPage_startsWithoutAnOldError() = runTest {
        val repo = FlakyRepository().apply { failing = true }
        val vm = viewModel(repo)
        vm.open(null)
        vm.onBodyChange("x")
        settle()
        assertTrue(vm.saveFailed.value)
        vm.onBack()
        runCurrent()

        vm.open(null)
        assertFalse(vm.saveFailed.value)
    }
}
