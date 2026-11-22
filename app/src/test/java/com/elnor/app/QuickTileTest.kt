package com.elnor.app

import android.app.StatusBarManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adding the "New note" tile from Settings (Android 13 and newer): which phones get the button, and
 * how the answer of the system is turned into a message.
 */
class QuickTileTest {

    @Test
    fun onlyAndroid13AndNewer_canAskToAddTheTile() {
        assertFalse(canRequestTile(sdkInt = 26))
        assertFalse(canRequestTile(sdkInt = 32))
        assertTrue(canRequestTile(sdkInt = 33))
        assertTrue(canRequestTile(sdkInt = 36))
    }

    @Test
    fun theSystemAnswers_becomeOutcomes() {
        assertEquals(
            TileAddOutcome.ADDED,
            tileAddOutcome(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED)
        )
        assertEquals(
            TileAddOutcome.ALREADY_ADDED,
            tileAddOutcome(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)
        )
        assertEquals(
            TileAddOutcome.NOT_ADDED,
            tileAddOutcome(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED)
        )
    }

    @Test
    fun anyOtherAnswer_isAFailure() {
        assertEquals(TileAddOutcome.FAILED, tileAddOutcome(-1))
        assertEquals(TileAddOutcome.FAILED, tileAddOutcome(1000))
    }

    @Test
    fun theUserSayingNo_getsNoMessage() {
        assertNull(tileOutcomeMessage(TileAddOutcome.NOT_ADDED))
    }

    @Test
    fun theOtherOutcomes_getAMessage() {
        assertNotNull(tileOutcomeMessage(TileAddOutcome.ADDED))
        assertNotNull(tileOutcomeMessage(TileAddOutcome.ALREADY_ADDED))
        assertNotNull(tileOutcomeMessage(TileAddOutcome.FAILED))
    }
}
