package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launcher shortcut and the Quick Settings tile ask for a new note with an intent. These tests
 * check the parts that need no phone: which intents count, and the waiting request.
 */
class QuickNoteTest {

    // ---- Which intents start a new note ----

    @Test
    fun newNoteAction_onAFreshStart_startsANote() {
        assertTrue(QuickNote.shouldStartNewNote(QuickNote.ACTION_NEW_NOTE, isRestored = false))
    }

    @Test
    fun newNoteAction_onARecreatedActivity_doesNot() {
        // Rotation or a restart by the system: the old intent is still there, but it was answered
        assertFalse(QuickNote.shouldStartNewNote(QuickNote.ACTION_NEW_NOTE, isRestored = true))
    }

    @Test
    fun otherActions_doNot() {
        assertFalse(QuickNote.shouldStartNewNote("android.intent.action.MAIN", isRestored = false))
        assertFalse(QuickNote.shouldStartNewNote(null, isRestored = false))
    }

    @Test
    fun actionName_isStable() {
        // It is written in res/xml/shortcuts.xml too: both must stay the same
        assertEquals("com.elnor.app.action.NEW_NOTE", QuickNote.ACTION_NEW_NOTE)
    }

    // ---- The waiting request ----

    @Test
    fun noRequest_atTheStart() {
        val requests = NewNoteRequests()

        assertFalse(requests.pending.value)
        assertFalse(requests.consume())
    }

    @Test
    fun aRequest_isWaiting_untilItIsConsumed() {
        val requests = NewNoteRequests()

        requests.request()

        assertTrue(requests.pending.value)
        assertTrue(requests.consume())
        assertFalse(requests.pending.value)
    }

    @Test
    fun consume_givesTheRequestOnlyOnce() {
        val requests = NewNoteRequests()
        requests.request()

        assertTrue(requests.consume())
        assertFalse(requests.consume())
    }

    @Test
    fun twoRequestsBeforeAnswer_areOneNote() {
        val requests = NewNoteRequests()

        requests.request()
        requests.request()

        assertTrue(requests.consume())
        assertFalse(requests.consume())
    }

    @Test
    fun aNewRequest_afterAnAnsweredOne_isWaitingAgain() {
        val requests = NewNoteRequests()
        requests.request()
        requests.consume()

        requests.request()

        assertTrue(requests.pending.value)
        assertTrue(requests.consume())
    }
}
