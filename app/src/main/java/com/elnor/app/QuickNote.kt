package com.elnor.app

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quick note: a way to start a new note without opening the app first. Two entrances use it:
 *  - the "New note" shortcut of the launcher icon (touch and hold the icon), see res/xml/shortcuts.xml
 *  - the "New note" tile of the Quick Settings panel, see [NewNoteTileService]
 *
 * Both start MainActivity with [QuickNote.ACTION_NEW_NOTE]. The activity turns that into a
 * request ([NewNoteRequests]), and the home screen answers it by opening an empty note.
 */
object QuickNote {
    const val ACTION_NEW_NOTE = "com.elnor.app.action.NEW_NOTE"

    /** The one list of waiting requests of the app. */
    val requests = NewNoteRequests()

    /**
     * True when an incoming intent must start a new note. [isRestored] is true when the activity
     * is only being recreated (rotation, or the system restarted the app): its old intent is
     * still there then, but that request was already answered, so it must not make another note.
     */
    fun shouldStartNewNote(action: String?, isRestored: Boolean): Boolean =
        !isRestored && action == ACTION_NEW_NOTE

    /**
     * The intent of the Quick Settings tile. Together, NEW_TASK, CLEAR_TOP and SINGLE_TOP bring
     * the Elnor that is already open to the front (it gets the intent in onNewIntent) instead of
     * starting a second one.
     */
    fun newNoteIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_NEW_NOTE)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
}

/**
 * "Open a new note" requests that are waiting for the home screen. It is a flag, not a counter:
 * two requests before the screen answers are one new note, not two.
 *
 * The request stays until it is answered, so it also works for a cold start, when the activity
 * gets the intent before the home screen exists. It holds plain values only, so it is tested on
 * the computer.
 */
class NewNoteRequests {
    private val _pending = MutableStateFlow(false)

    /** True while a request waits to be answered. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    /** Takes the request. True if there was one: only the first caller gets true. */
    fun consume(): Boolean = _pending.compareAndSet(expect = true, update = false)
}
