package com.elnor.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NoteEditorViewModel.onStop] blocks the main thread while it waits for the id of a new note.
 * That wait must be short and must never hang, even if the storage is very slow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorStopWaitTest {

    /** insert() waits until [release] is completed, like a very slow disk. */
    private class SlowInsertRepository : NoteRepository {
        val release = CompletableDeferred<Unit>()
        val inserted = mutableListOf<Note>()

        override val notes: StateFlow<List<Note>> = MutableStateFlow(emptyList())
        override val trashedNotes: Flow<List<Note>> = flowOf(emptyList())
        override fun search(query: String): Flow<List<Note>> = flowOf(emptyList())

        override suspend fun insert(note: Note): Long {
            release.await()
            inserted.add(note)
            return 42L
        }

        override suspend fun update(note: Note) = Unit
        override suspend fun moveToTrash(id: Long) = Unit
        override suspend fun setPinned(id: Long, pinned: Boolean) = Unit
        override suspend fun restore(id: Long) = Unit
        override suspend fun deletePermanently(id: Long) = Unit
        override suspend fun emptyTrash() = Unit
        override suspend fun exportAll(): List<Note> = emptyList()
        override suspend fun importNotes(notes: List<Note>) = ImportSummary(0, 0)
        override suspend fun purgeExpiredTrash(): Int = 0
    }

    @Test
    fun defaultWait_isShort() {
        assertTrue(
            "the main thread must not be blocked for long",
            NoteEditorViewModel.STOP_WAIT_MS <= 150L
        )
    }

    @Test
    fun onStop_returnsAfterTheWait_whenTheFirstSaveIsStillRunning() {
        // A real scope with real time: onStop uses runBlocking + a real timeout.
        val repo = SlowInsertRepository()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        try {
            val vm = NoteEditorViewModel(repo, scope, stopWaitMs = 100L, timerScope = scope)
            vm.open(null)
            vm.onBodyChange("a new note") // the editor starts a pause timer, but we stop first

            val started = System.nanoTime()
            vm.onStop(waitForNewNote = true)
            val tookMs = (System.nanoTime() - started) / 1_000_000

            assertTrue("blocked for $tookMs ms, far more than the limit", tookMs < 1_000)
            assertTrue("the save has not finished, so nothing is inserted yet", repo.inserted.isEmpty())

            // The save still finishes afterwards: the note is not lost, only its id came late.
            repo.release.complete(Unit)
            val deadline = System.currentTimeMillis() + 2_000
            while (repo.inserted.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertEquals("a new note", repo.inserted.single().body)
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }
}
