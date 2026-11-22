package com.elnor.app

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi

// -------------------- Add the "New note" tile with one tap ----------------------
//
// The "New note" tile of the Quick Settings panel (see NewNoteTileService) is not in the panel by
// itself: the user has to swipe down twice, tap the pencil and drag it in. Android 13 (API 33) and
// newer can ask for it from the app: the system shows its own question "Add tile?" and adds it on
// "Add". Settings > Quick note has the button for it. Older phones keep the manual way.

/** The first Android version that lets an app ask to add its tile (Android 13). */
internal const val QUICK_TILE_REQUEST_MIN_SDK = 33

/** True when this phone ([sdkInt]) can show the system question to add the tile. */
internal fun canRequestTile(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt >= QUICK_TILE_REQUEST_MIN_SDK

/** What the system answered to "add the tile?". */
internal enum class TileAddOutcome { ADDED, ALREADY_ADDED, NOT_ADDED, FAILED }

/** The system's result code, in the words of the app. Anything unknown is [TileAddOutcome.FAILED]. */
internal fun tileAddOutcome(result: Int): TileAddOutcome = when (result) {
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> TileAddOutcome.ADDED
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> TileAddOutcome.ALREADY_ADDED
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> TileAddOutcome.NOT_ADDED
    else -> TileAddOutcome.FAILED
}

/** The short message for an outcome, or null when nothing needs to be said (the user said no). */
internal fun tileOutcomeMessage(outcome: TileAddOutcome): String? = when (outcome) {
    TileAddOutcome.ADDED -> "\"New note\" was added to Quick Settings"
    TileAddOutcome.ALREADY_ADDED -> "\"New note\" is already in Quick Settings"
    TileAddOutcome.NOT_ADDED -> null
    TileAddOutcome.FAILED ->
        "Could not add it here. Swipe down twice, tap the pencil and drag \"New note\" in"
}

/**
 * Shows the system question to add the "New note" tile, and a short message with the answer. Does
 * nothing on a phone older than Android 13 (the button is not shown there).
 */
internal fun requestAddNewNoteTile(context: Context) {
    if (!canRequestTile()) return
    requestOnAndroid13(context)
}

@RequiresApi(QUICK_TILE_REQUEST_MIN_SDK)
private fun requestOnAndroid13(context: Context) {
    val manager = context.getSystemService(StatusBarManager::class.java)
    if (manager == null) {
        toast(context, tileOutcomeMessage(TileAddOutcome.FAILED))
        return
    }
    manager.requestAddTileService(
        ComponentName(context, NewNoteTileService::class.java),
        context.getString(R.string.tile_new_note),
        Icon.createWithResource(context, R.drawable.ic_tile_new_note),
        context.mainExecutor
    ) { result -> toast(context, tileOutcomeMessage(tileAddOutcome(result))) }
}

private fun toast(context: Context, message: String?) {
    if (message != null) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
