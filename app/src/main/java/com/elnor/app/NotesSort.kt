package com.elnor.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * How the notes of the home screen are ordered. Pinned notes are always first (the most recently
 * pinned on top); this decides the order of the notes inside each of the two groups.
 * [storageValue] is what is saved on the phone. It is a fixed text, not the enum name.
 */
enum class NotesSort(val storageValue: String, val label: String) {
    EDITED_NEWEST("edited_newest", "Last edited: newest first"),
    EDITED_OLDEST("edited_oldest", "Last edited: oldest first"),
    TITLE_AZ("title_az", "Title: A to Z"),
    TITLE_ZA("title_za", "Title: Z to A");

    companion object {
        /** Anything unknown (or nothing saved) is EDITED_NEWEST, the way the app always sorted. */
        fun fromStorage(stored: String?): NotesSort =
            entries.firstOrNull { it.storageValue == stored } ?: EDITED_NEWEST
    }
}

/**
 * [notes] in the order of [sort], pinned notes first. The title is the name shown in the list
 * ([getDisplayTitle]), compared without regard to upper/lower case. Notes that are equal for the
 * chosen order stay in a fixed order (the most recently changed first, then by id), so the list
 * never jumps around.
 */
fun sortNotes(notes: List<Note>, sort: NotesSort): List<Note> {
    val titleOrder = Comparator<Note> { a, b ->
        // Without the formatting marks: "# Apple" is sorted as "Apple"
        String.CASE_INSENSITIVE_ORDER.compare(
            MarkdownFormat.plainLine(getDisplayTitle(a)),
            MarkdownFormat.plainLine(getDisplayTitle(b))
        )
    }
    val within: Comparator<Note> = when (sort) {
        NotesSort.EDITED_NEWEST -> compareByDescending { it.updatedAt }
        NotesSort.EDITED_OLDEST -> compareBy { it.updatedAt }
        NotesSort.TITLE_AZ -> titleOrder
        NotesSort.TITLE_ZA -> titleOrder.reversed()
    }
    return notes.sortedWith(
        // pinned first: a note without pinnedAt (null) comes after every pinned one
        compareBy<Note> { it.pinnedAt == null }
            .thenByDescending { it.pinnedAt ?: 0L }
            .then(within)
            .thenByDescending { it.updatedAt }
            .thenBy { it.id }
    )
}

private const val SORT_SETTINGS_FILE = "elnor_settings"
private const val KEY_NOTES_SORT = "notes_sort"

fun loadNotesSort(context: Context): NotesSort =
    NotesSort.fromStorage(
        context.applicationContext
            .getSharedPreferences(SORT_SETTINGS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_NOTES_SORT, null)
    )

fun saveNotesSort(context: Context, sort: NotesSort) {
    context.applicationContext
        .getSharedPreferences(SORT_SETTINGS_FILE, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_NOTES_SORT, sort.storageValue)
        .apply()
}

/** Three lines that get shorter, for the sort button. The core icon set has no sort icon. */
val SortIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Sort",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 6f); horizontalLineTo(21f); verticalLineTo(8f); horizontalLineTo(3f); close()
            moveTo(3f, 11f); horizontalLineTo(15f); verticalLineTo(13f); horizontalLineTo(3f); close()
            moveTo(3f, 16f); horizontalLineTo(9f); verticalLineTo(18f); horizontalLineTo(3f); close()
        }
    }.build()
}

/** A box with a lid, for "Archive" in the side menu (outlined, like the other menu icons). */
val ArchiveIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Archive",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f
        ) {
            // the lid
            moveTo(3f, 4f); horizontalLineTo(21f); verticalLineTo(8f); horizontalLineTo(3f); close()
            // the box under it
            moveTo(4f, 8f); verticalLineTo(20f); horizontalLineTo(20f); verticalLineTo(8f)
            // the handle
            moveTo(10f, 12f); horizontalLineTo(14f)
        }
    }.build()
}
