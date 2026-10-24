package com.elnor.app

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Auto-save for the note editor. There is no Save button.
 *
 * The text is only kept in memory while the user types (no save on every key).
 * It is written to the [repository] when:
 *  - the user stops typing for [autoSaveDelayMs] (a short pause, see [scheduleAutoSave]).
 *    Every new change restarts the wait, so a save happens only after a pause, never per key
 *  - [onBack]: the back arrow or the system back was pressed
 *  - [onStop]: the app went to the background (Lifecycle ON_STOP)
 *
 * Rules:
 *  - A new note whose title and body are both blank is never saved.
 *  - A pause-save never moves a note to the Trash. If the user clears the whole text and
 *    pauses, nothing happens yet (they may be about to type something else). Only [onBack] and
 *    [onStop] do that, as before.
 *  - The first save of a new note is an insert. The id that comes back is kept in the
 *    session, so every later save of that note is an update. No second copy is created.
 *  - An existing note that was changed is updated and gets a new updatedAt.
 *    If nothing changed, it is not touched.
 *  - An existing note whose title and body were both cleared is moved to the Trash (it keeps
 *    the text that was last saved, so it can still be restored from there).
 *  - Deleting a note moves it to the Trash. Nothing is removed for good here. Only the Trash
 *    screen can do that.
 *
 * Why saves run in [saveScope] (an application-level scope) and not in viewModelScope:
 * when the user leaves the screen, the ViewModel can be cleared at the same moment the
 * save starts, and viewModelScope would cancel the save half-way. [saveScope] lives as long
 * as the app process, so nobody can cancel it. We also do not need NonCancellable for this.
 * NonCancellable only protects a block that has already started and still ties the work to
 * a dying object. Saves are sent through one queue ([commands]) that a single coroutine
 * reads in order. That keeps them in the order they happened (stop, then back) and makes a
 * duplicate insert impossible. [onCleared] only closes the queue. The coroutine still
 * finishes everything that is already in it.
 *
 * The pause timer runs in [timerScope] (the ViewModel's own scope by default, main thread), so the
 * draft text is only ever read on one thread. It does not write anything itself: after the pause
 * it puts a normal save into the same queue as above. If the ViewModel is cleared first, the timer
 * is cancelled, and that is fine because [onBack] has already queued the final save.
 *
 * A new note that was already saved is remembered in [savedState] (the id the first save gave
 * it). The system can close the whole app process while it is in the background; the page then
 * comes back with the typed text but with a new, empty ViewModel. [open] finds the id in
 * [savedState] and continues with an update, so no second copy of the note is created.
 * The id is removed when the page is closed ([onBack]) or the note is deleted.
 *
 * @param now clock for updatedAt. Replaced in tests.
 * @param autoSaveDelayMs how long the user must stop typing before the text is saved.
 * @param firstSaveDelayMs the same wait, but for the first save of a new note only. A new note
 *   gets its id from that save, and [onStop] has to wait for the id when it is missing. A short
 *   wait here makes sure the id almost always exists long before the app goes to the background,
 *   so [onStop] almost never has to block.
 * @param timerScope scope of the pause timer. Null = viewModelScope. Replaced in tests.
 */
class NoteEditorViewModel(
    private val repository: NoteRepository,
    saveScope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val autoSaveDelayMs: Long = AUTO_SAVE_DELAY_MS,
    private val timerScope: CoroutineScope? = null,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    /** How long to wait before the FIRST save of a new note. Shorter, so the note gets its id early. */
    private val firstSaveDelayMs: Long = autoSaveDelayMs
) : ViewModel() {

    /** One open editor page. [id] is null until a new note was inserted. */
    private class Session(
        @Volatile var id: Long?,
        var savedTitle: String,
        var savedBody: String,
        /** True for a page that began as a new note (also one that continues after a restart). */
        val startedNew: Boolean
    )

    private sealed interface Command {
        /** [fromPause] = true: saved after a typing pause, so a blank text never goes to the Trash. */
        class Save(
            val session: Session,
            val title: String,
            val body: String,
            val fromPause: Boolean = false,
            /** Completed when this save is finished. Only [onStop] waits for it. */
            val done: CompletableDeferred<Unit>? = null
        ) : Command

        class Delete(val session: Session) : Command
    }

    /** The waiting timer of the pause-save. Only used from the main thread. */
    private var autoSaveJob: Job? = null

    private val _hasSavedNote = MutableStateFlow(false)

    /** True when the open note exists in storage (the UI shows the delete button then). */
    val hasSavedNote: StateFlow<Boolean> = _hasSavedNote.asStateFlow()

    private val _lastSavedAt = MutableStateFlow<Long?>(null)

    /**
     * The time of the last save of the open note (the updatedAt it got), or null if it was never
     * saved. The editor shows it as "Last edited". It changes at every save, also for a note that
     * was new when the page was opened.
     */
    val lastSavedAt: StateFlow<Long?> = _lastSavedAt.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)

    /**
     * True while the last save of the open page did not work (for example the phone's storage is
     * full). The text is still on the screen and in memory. It turns false again as soon as a
     * later save works, and when a page is opened or closed. The editor shows a message and asks
     * before the user leaves.
     */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    private val _lostNote = MutableStateFlow<String?>(null)

    /**
     * The text (title, empty line, text) of a note whose FINAL save failed after its page was
     * already closed, so it could not be written and is not on any screen any more. The notes
     * list offers to copy it, then calls [clearLostNote]. Null when there is nothing to report.
     */
    val lostNote: StateFlow<String?> = _lostNote.asStateFlow()

    fun clearLostNote() {
        _lostNote.value = null
    }

    @Volatile
    private var session: Session? = null
    private var draftTitle = ""
    private var draftBody = ""
    private var draftSelStart = 0
    private var draftSelEnd = 0

    // Undo / Redo. It lives here (not in the screen), so it survives rotating the phone.
    // It is only used from the main thread.
    private var history = EditHistory(EditSnapshot("", ""), now)
    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)

    /** True when there is a change to take back (the Undo button is on). */
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    /** True when an undone change can be brought back (the Redo button is on). */
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        saveScope.launch {
            for (command in commands) {
                try {
                    handle(command)
                    // A save that works clears an earlier failure of the page that is open now
                    if (command is Command.Save && session === command.session) {
                        _saveFailed.value = false
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep the queue alive. The next save tries again (the session state is unchanged).
                    Log.e("NoteEditorViewModel", "Saving failed", e)
                    if (command is Command.Save) reportSaveFailure(command)
                } finally {
                    (command as? Command.Save)?.done?.complete(Unit)
                }
            }
        }
    }

    /**
     * Starts an editing session: [note] to edit, or null for a new note.
     * Does nothing if a session is already open (for example after rotating the screen),
     * so the text the user typed is kept.
     */
    fun open(note: Note?) {
        if (session != null) return
        // A new page that finds an id here is the same new note after the app process was closed
        // and started again: it must update that note, not insert a second one.
        val resumedId = if (note == null) savedState.get<Long>(KEY_DRAFT_NOTE_ID) else null
        if (note != null) savedState.remove<Long>(KEY_DRAFT_NOTE_ID)
        session = Session(
            id = note?.id ?: resumedId,
            // For a resumed note the saved text is not known here. "" makes the next non-blank
            // text count as a change, so it is written (an update, never an insert).
            savedTitle = note?.title?.trim() ?: "",
            savedBody = note?.body?.trim() ?: "",
            startedNew = note == null
        )
        draftTitle = note?.title ?: ""
        draftBody = note?.body ?: ""
        draftSelStart = draftBody.length
        draftSelEnd = draftBody.length
        _saveFailed.value = false // a new page starts without an old error
        history = EditHistory(snapshot(), now) // a new page starts with an empty Undo memory
        refreshHistoryFlags()
        _hasSavedNote.value = note != null || resumedId != null
        _lastSavedAt.value = note?.updatedAt
    }

    /** Called while typing. Remembers the text (and for Undo). It is saved after a typing pause. */
    fun onTitleChange(value: String) {
        draftTitle = value
        recordForUndo()
        scheduleAutoSave()
    }

    /**
     * Called while typing. Remembers the text (and for Undo). It is saved after a typing pause.
     * [selStart] and [selEnd] are the cursor (or selection) in the text.
     */
    fun onBodyChange(value: String, selStart: Int = value.length, selEnd: Int = selStart) {
        draftBody = value
        draftSelStart = selStart.coerceIn(0, value.length)
        draftSelEnd = selEnd.coerceIn(0, value.length)
        recordForUndo()
        scheduleAutoSave()
    }

    /**
     * (Re)starts the pause timer. Each change cancels the one before, so the text is saved only
     * when the user has stopped for [autoSaveDelayMs]. The save goes through the normal queue.
     */
    private fun scheduleAutoSave() {
        val current = session ?: return
        autoSaveJob?.cancel()
        autoSaveJob = (timerScope ?: viewModelScope).launch {
            delay(if (current.id == null) firstSaveDelayMs else autoSaveDelayMs)
            // The page may have closed or a new one opened while we waited
            if (session !== current) return@launch
            commands.trySend(Command.Save(current, draftTitle, draftBody, fromPause = true))
        }
    }

    private fun cancelAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    /**
     * Takes back the last change. Returns what the page must show now (title, text and cursor),
     * or null if there is nothing to take back. The remembered text is changed too, so what is
     * saved is what is on the screen.
     */
    fun undo(): EditSnapshot? = applyFromHistory(if (session == null) null else history.undo())

    /** Brings back the change that was taken back. Same result as [undo]. */
    fun redo(): EditSnapshot? = applyFromHistory(if (session == null) null else history.redo())

    private fun snapshot() = EditSnapshot(draftTitle, draftBody, draftSelStart, draftSelEnd)

    private fun recordForUndo() {
        if (session == null) return
        history.record(snapshot())
        refreshHistoryFlags()
    }

    private fun applyFromHistory(result: EditSnapshot?): EditSnapshot? {
        if (result == null) return null
        draftTitle = result.title
        draftBody = result.body
        draftSelStart = result.bodySelStart
        draftSelEnd = result.bodySelEnd
        refreshHistoryFlags()
        scheduleAutoSave() // Undo / Redo changes the text too
        return result
    }

    private fun refreshHistoryFlags() {
        _canUndo.value = history.canUndo
        _canRedo.value = history.canRedo
    }

    /** The page is closing: forget the changes, so the next page never starts with these buttons on. */
    private fun clearHistory() {
        history = EditHistory(EditSnapshot("", ""), now)
        refreshHistoryFlags()
    }

    /**
     * Tries to save the text that is on the page again, without leaving. The editor calls this
     * after a failed save, when the user chooses to keep editing. Like a pause-save, it never
     * moves a note to the Trash.
     */
    fun retrySave() {
        val current = session ?: return
        cancelAutoSave()
        commands.trySend(Command.Save(current, draftTitle, draftBody, fromPause = true))
    }

    /**
     * A save did not work. If its page is still open, the editor is told ([saveFailed]) and the
     * text stays on the screen. If the page is already closed, only a FINAL save (back / app
     * stopped) matters: it is the last chance, so the text is kept in [lostNote] to be copied.
     * A failed pause-save of a closed page is ignored, because the final save that follows it
     * has the newer text.
     */
    private fun reportSaveFailure(command: Command.Save) {
        if (session === command.session) {
            _saveFailed.value = true
            return
        }
        if (command.fromPause) return
        val text = noteTextForCopy(command.title, command.body)
        if (text.isNotEmpty()) _lostNote.value = text
    }

    /** Back arrow or system back: save, and end the session. */
    fun onBack() {
        val current = session ?: return
        session = null
        _saveFailed.value = false // the page is closing; a failure of the final save goes to lostNote
        cancelAutoSave() // this save is the final one
        _lastSavedAt.value = null // the page is closing: the next page must not start with this time
        savedState.remove<Long>(KEY_DRAFT_NOTE_ID) // the page is closed: nothing to continue later
        clearHistory()
        commands.trySend(Command.Save(current, draftTitle, draftBody))
    }

    /**
     * App went to the background: save, and keep the session open.
     *
     * [waitForNewNote]: the system saves the state of the page right after this call returns, and
     * the id of a new note only exists once its first save is done. With true, a new note that is
     * not saved yet is saved first, and this call waits for it (at most [STOP_WAIT_MS], in
     * practice a few milliseconds), so the id is in [savedState] in time. The real app passes true.
     * Tests pass nothing and do not block.
     */
    fun onStop(waitForNewNote: Boolean = false) {
        val current = session ?: return
        cancelAutoSave() // saved right now, no need to wait for the pause
        val mustWait = waitForNewNote && current.id == null &&
            (draftTitle.isNotBlank() || draftBody.isNotBlank())
        val done = if (mustWait) CompletableDeferred<Unit>() else null
        commands.trySend(Command.Save(current, draftTitle, draftBody, done = done))
        if (done != null) {
            runBlocking { withTimeoutOrNull(STOP_WAIT_MS) { done.await() } }
        }
        // Written here, on this thread, so it is surely there when the state is saved
        val id = current.id
        if (session === current && current.startedNew && id != null) {
            savedState[KEY_DRAFT_NOTE_ID] = id
        }
    }

    /** The user chose "Delete note". Moves the note to the Trash if it was saved, and ends the session. */
    fun deleteNote() {
        val current = session ?: return
        session = null
        _saveFailed.value = false
        cancelAutoSave() // a waiting pause-save must not bring the note back
        _lastSavedAt.value = null
        savedState.remove<Long>(KEY_DRAFT_NOTE_ID)
        clearHistory()
        commands.trySend(Command.Delete(current))
    }

    override fun onCleared() {
        // Do not cancel anything. Close the queue: what is already in it is still saved.
        commands.close()
    }

    // ---- Runs one command at a time, in the order they were sent ----

    private suspend fun handle(command: Command) {
        when (command) {
            is Command.Save -> persist(
                command.session,
                command.title,
                command.body,
                trashWhenBlank = !command.fromPause
            )
            is Command.Delete -> {
                val id = command.session.id
                if (id != null) repository.moveToTrash(id)
                command.session.id = null
            }
        }
    }

    private suspend fun persist(
        session: Session,
        rawTitle: String,
        rawBody: String,
        trashWhenBlank: Boolean = true
    ) {
        val title = rawTitle.trim()
        val body = rawBody.trim()
        val id = session.id

        // Nothing written: a blank note is not worth saving ...
        if (title.isBlank() && body.isBlank()) {
            // A pause-save leaves an emptied note alone (the user may still be typing)
            if (id != null && trashWhenBlank) {
                // ... and an existing note that was emptied leaves the list. It goes to the
                // Trash with the text that was last saved, so nothing is lost for good.
                repository.moveToTrash(id)
                session.id = null
                session.savedTitle = ""
                session.savedBody = ""
                publishHasSavedNote(session, false)
                publishLastSavedAt(session, null)
                publishDraftNoteId(session, null)
            }
            return
        }

        if (id == null) {
            // First save of a new note. Keep the id so the next save is an update.
            val savedAt = now()
            val newId = repository.insert(Note(id = 0L, title = title, body = body, updatedAt = savedAt))
            session.id = newId
            session.savedTitle = title
            session.savedBody = body
            publishHasSavedNote(session, true)
            publishLastSavedAt(session, savedAt)
            publishDraftNoteId(session, newId)
            return
        }

        if (title == session.savedTitle && body == session.savedBody) return // nothing changed

        val savedAt = now()
        repository.update(Note(id = id, title = title, body = body, updatedAt = savedAt))
        session.savedTitle = title
        session.savedBody = body
        publishLastSavedAt(session, savedAt)
    }

    /** Only the page that is open now may change the flag (an older, closed page must not). */
    private fun publishHasSavedNote(from: Session, value: Boolean) {
        if (session === from) _hasSavedNote.value = value
    }

    /** Same rule as [publishHasSavedNote]: only the page that is open now may change the time. */
    private fun publishLastSavedAt(from: Session, value: Long?) {
        if (session === from) _lastSavedAt.value = value
    }

    /**
     * Remembers the id of a new note in [savedState] (or forgets it for null). The saves run on a
     * background thread, so the write is done on the main thread. Only the page that is open now
     * may do it, so a page that was closed meanwhile cannot leave an id behind.
     */
    private fun publishDraftNoteId(from: Session, id: Long?) {
        if (!from.startedNew || session !== from) return
        (timerScope ?: viewModelScope).launch {
            if (session !== from) return@launch
            if (id == null) savedState.remove<Long>(KEY_DRAFT_NOTE_ID) else savedState[KEY_DRAFT_NOTE_ID] = id
        }
    }

    companion object {
        /** Key in the saved state: the id of a new note that was saved while its page is open. */
        const val KEY_DRAFT_NOTE_ID = "draft_note_id"

        /** The longest [onStop] waits for the first save of a new note (milliseconds). */
        const val STOP_WAIT_MS = 500L

        /** Wait before the first save of a new note (milliseconds), see [firstSaveDelayMs]. */
        const val FIRST_SAVE_DELAY_MS = 600L

        /** How long the user must stop typing before the text is saved (milliseconds). */
        const val AUTO_SAVE_DELAY_MS = 2_000L

        /** Creates the ViewModel with the real storage (Room) and the application-level scope. */
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return NoteEditorViewModel(
                        repository = RoomNoteRepository.get(appContext),
                        saveScope = ApplicationScope.scope,
                        // Survives the system closing the app process (see the class comment)
                        savedState = extras.createSavedStateHandle(),
                        firstSaveDelayMs = FIRST_SAVE_DELAY_MS
                    ) as T
                }
            }
        }
    }
}
