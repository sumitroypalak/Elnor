package com.elnor.app

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * The "New note" tile of the Quick Settings panel (swipe down twice, edit, add "New note").
 * One tap closes the panel and opens an empty note (see [QuickNote]).
 *
 * The tile is only a button: it has no on / off state, so it always shows as available.
 * If the phone is locked, the system asks to unlock first ([unlockAndRun]). The App lock of Elnor
 * is separate and still covers the notes after that.
 */
class NewNoteTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun { openNewNote() }
    }

    private fun openNewNote() {
        val intent = QuickNote.newNoteIntent(this)
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14 and newer: the panel is closed with a PendingIntent
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
