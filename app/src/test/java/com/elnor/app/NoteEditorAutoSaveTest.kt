package com.elnor.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the "save after a typing pause" of [NoteEditorViewModel] with a fake storage and a
 * virtual clock, so no real waiting happens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorAutoSaveTest {

    private val delayMs = 1_000L

    /** Remembers what the ViewModel asked the storage to do. */
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

        override suspend fun update(note: Note) {
            updated.add(note)
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

    @Test
    fun newNote_isSavedAfterThePause_notBefore() = runTest {
        val repo = FakeRepository()
        val vm = NoteEditorViewModel(repo, backgroundScope, autoSaveDelayMs = delayMs, timerScope = backgroundScope)
        vm.open(null)

        vm.onBodyChange("hello")
        advanceTimeBy(delayMs - 1)
        runCurrent()
        assertTrue("saved too early", repo.inserted.isEmpty())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, repo.inserted.size)
        assertEquals("hello", repo.inserted[0].body)
    }

    @Test
    fun newNote_usesTheShorterFirstSaveDelay_butExistingNoteUsesTheNormalOne() = runTest {
        val repo = FakeRepository()
        val vm = NoteEditorViewModel(
            repo, backgroundScope,
            autoSaveDelayMs = delayMs, timerScope = backgroundScope, firstSaveDelayMs = 200L
        )
        vm.open(null)

        vm.onBodyChange("hello")
        advanceTimeBy(201)
        runCurrent()
        assertEquals("the first save should use the short delay", 1, repo.inserted.size)

        // The note now has an id: the next save waits for the normal pause
        vm.onBodyChange("hello world")
        advanceTimeBy(delayMs - 1)
        runCurrent()
        assertTrue("an update must wait for the normal pause", repo.updated.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, repo.updated.size)
    }

    @Test
    fun typingAgain_restartsTheWait_andSavesTheLatestText() = runTest {
        val repo = FakeRepository()
        val vm = NoteEditorViewModel(repo, backgroundScope, autoSaveDelayMs = delayMs, timerScope = backgroundScope)
        vm.open(null)

        vm.onBodyChange("hel")
        advanceTimeBy(700)
        vm.onBodyChange("hello")
        advanceTimeBy(700) // 1400 ms since the first key, but only 700 since the last one
        runCurrent()
        assertTrue("the first timer should have been cancelled", repo.inserted.isEmpty())

        advanceTimeBy(400)
        runCurrent()
        assertEquals(1, repo.inserted.size)
        assertEquals("hello", repo.inserted[0].body)
    }

    @Test
    fun secondPause_updatesTheSameNote_noSecondCopy() = runTest {
        val repo = FakeRepository()
        val vm = NoteEditorViewModel(repo, backgroundScope, autoSaveDelayMs = delayMs, timerScope = backgroundScope)
        vm.open(null)

        vm.onBodyChange("one")
        advanceTimeBy(delayMs + 1)
        runCurrent()
        vm.onBodyChange("one two")
        advanceTimeBy(delayMs + 1)
        runCurrent()

        assertEquals(1, repo.inserted.size)
        assertEquals(1, repo.updated.size)
        assertEquals(100L, repo.updated[0].id)
        assertEquals("one two", repo.updated[0].body)
    }

    @Test
    fun unchangedExistingNote_isNotWritten() = runTest {
        val repo = FakeRepository()
        val vm = NoteEditorViewModel(repo, backgroundScope, autoSaveDelayMs = delayMs, timerScope = backgroundScope)
        vm.open(Note(id = 5L, title = "Title", body = "Body", updatedAt = 1L))

        // The editor screen gives the text on screen to the ViewModel when it opens
        vm.onTitleChange("Title")
        vm.onBodyChange("Body")
        advanceUntilIdle()

        assertTrue(repo.updated.isEmpty())
        assertTrue(repo.inserted.isEmpty())
    }

    @Test
    fun emptiedNote_staysOutOfTheTrash_afterAPause_butGoesThereOnBack() = runTest {
        val repo = FakeRepository()
        val vm = NoteEditorViewModel(repo, backgroundScope, autoSaveDelayMs = delayMs, timerScope = backgroundScope)
        vm.open(Note(id = 5L, title = "", body = "Some text", updatedAt = 1L))

        vm.onBodyChange("")
        advanceTimeBy(delayMs + 1)
        runCurrent()
        assertTrue("a pause must not trash the note", repo.trashed.isEmpty())

        vm.onBack()
        runCurrent()
        assertEquals(listOf(5L), repo.trashed)
    }

    @Test
    fun onBack_cancelsTheWaitingTimer_soThereIsOnlyOneSave() = runTest {
        val repo = FakeRepository()
        val vm = NoteEditorViewModel(repo, backgroundScope, autoSaveDelayMs = delayMs, timerScope = backgroundScope)
        vm.open(null)

        vm.onBodyChange("hello")
        vm.onBack()
        advanceUntilIdle()

        assertEquals(1, repo.inserted.size)
    }

    @Test
    fun deleteNote_cancelsTheWaitingTimer() = runTest {
        val repo = FakeRepository()
        val vm = NoteEditorViewModel(repo, backgroundScope, autoSaveDelayMs = delayMs, timerScope = backgroundScope)
        vm.open(Note(id = 5L, title = "T", body = "B", updatedAt = 1L))

        vm.onBodyChange("B changed")
        vm.deleteNote()
        advanceUntilIdle()

        assertEquals(listOf(5L), repo.trashed)
        assertTrue("the note must not be saved again after delete", repo.updated.isEmpty())
    }

    @Test
    fun undo_alsoSchedulesASave() = runTest {
        val repo = FakeRepository()
        val vm = NoteEditorViewModel(repo, backgroundScope, autoSaveDelayMs = delayMs, timerScope = backgroundScope)
        vm.open(Note(id = 5L, title = "T", body = "Start", updatedAt = 1L))

        vm.onBodyChange("Start and more")
        advanceTimeBy(delayMs + 1)
        runCurrent()
        assertEquals("Start and more", repo.updated.last().body)

        vm.undo()
        advanceTimeBy(delayMs + 1)
        runCurrent()
        assertEquals("Start", repo.updated.last().body)
    }
}
