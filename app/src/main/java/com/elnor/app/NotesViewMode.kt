package com.elnor.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * How the notes are shown on the home screen: one note per row (LIST) or two columns (GRID).
 * [storageValue] is what is saved on the phone. It is a fixed text, not the enum name.
 */
enum class NotesViewMode(val storageValue: String) {
    LIST("list"),
    GRID("grid");

    /** The other view. The toggle button switches to it. */
    fun toggled(): NotesViewMode = if (this == LIST) GRID else LIST

    companion object {
        /** Anything unknown (or nothing saved) is LIST, the way the app always looked. */
        fun fromStorage(stored: String?): NotesViewMode =
            entries.firstOrNull { it.storageValue == stored } ?: LIST
    }
}

private const val VIEW_MODE_SETTINGS_FILE = "elnor_settings"
private const val KEY_NOTES_VIEW_MODE = "notes_view_mode"

fun loadNotesViewMode(context: Context): NotesViewMode =
    NotesViewMode.fromStorage(
        context.applicationContext
            .getSharedPreferences(VIEW_MODE_SETTINGS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_NOTES_VIEW_MODE, null)
    )

fun saveNotesViewMode(context: Context, mode: NotesViewMode) {
    context.applicationContext
        .getSharedPreferences(VIEW_MODE_SETTINGS_FILE, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_NOTES_VIEW_MODE, mode.storageValue)
        .apply()
}

/**
 * Four squares, for the "switch to grid" button. The core icon set that the app uses has no grid
 * icon, and the extended set is very large, so this one small icon is drawn here.
 */
val GridViewIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GridView",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // top left, top right, bottom left, bottom right
            moveTo(4f, 4f); horizontalLineTo(10f); verticalLineTo(10f); horizontalLineTo(4f); close()
            moveTo(14f, 4f); horizontalLineTo(20f); verticalLineTo(10f); horizontalLineTo(14f); close()
            moveTo(4f, 14f); horizontalLineTo(10f); verticalLineTo(20f); horizontalLineTo(4f); close()
            moveTo(14f, 14f); horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(14f); close()
        }
    }.build()
}
