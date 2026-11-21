package com.elnor.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

// Android Studio previews of the home screen at the sizes where the layout changes. Open this file
// and look at the Preview pane (or turn on "Interactive mode" to tap a note). Each preview gets its
// window size from widthDp / heightDp, so the same code that runs on a device picks the layout:
//
//   phone portrait      360 x 800    bottom bar, grid of 2
//   phone landscape     800 x 360    rail, grid of 4, no tagline
//   small tablet        700 x 900    rail, grid of 3
//   tablet portrait     800 x 1280   rail, grid of 4
//   foldable, open      841 x 701    rail, list on the left and "Select a note" on the right
//   tablet landscape    1280 x 800   rail, list on the left and "Select a note" on the right
//
// The notes are made up here. Nothing is read from or written to the phone.

private const val DAY_MS = 24L * 60L * 60L * 1000L

private val sampleNotes: List<Note> = run {
    val now = 1_700_000_000_000L
    listOf(
        Note(1, "Groceries", "- milk\n- rice\n- eggs\n- bananas", now - 1 * DAY_MS, pinnedAt = now),
        Note(2, "Meeting notes", "# Plan\nTalk about the release.\n\n**Decide** the date.", now - 2 * DAY_MS),
        // "বাংলা" written with \u escapes, like in FontAuditPreview
        Note(3, "বাংলা", "A note in Bengali and English.", now - 3 * DAY_MS),
        Note(4, "", "A note without a title. Its first line is used instead.", now - 4 * DAY_MS),
        Note(5, "Ideas", "1. A rail for tablets\n2. Two panes\n3. A bigger grid", now - 5 * DAY_MS),
        Note(6, "Books to read", "Short list.", now - 6 * DAY_MS),
        Note(7, "Trip", "Pack the charger, the tickets and a jacket. Check the weather.", now - 7 * DAY_MS),
        Note(8, "Recipe", "Mix, wait, bake. Longer text so this card is taller than the others " +
            "in the grid, and the cards fit together.", now - 8 * DAY_MS)
    )
}

private val sampleTasks: List<Task> = listOf(
    Task(1, "Call the bank", createdAt = 0L),
    Task(2, "Send the report", createdAt = 0L),
    Task(3, "Water the plants", createdAt = 0L, completedAt = 1L)
)

/** A repository with a few made-up notes. Every change is ignored. */
private class PreviewNoteRepository(private val all: List<Note>) : NoteRepository {
    override val notes: StateFlow<List<Note>> = MutableStateFlow(all)
    override val trashedNotes: Flow<List<Note>> = flowOf(emptyList())
    override fun search(query: String): Flow<List<Note>> = flowOf(all)
    override suspend fun insert(note: Note): Long = note.id
    override suspend fun update(note: Note) = Unit
    override suspend fun moveToTrash(id: Long) = Unit
    override suspend fun setPinned(id: Long, pinned: Boolean) = Unit
    override suspend fun restore(id: Long) = Unit
    override suspend fun deletePermanently(id: Long) = Unit
    override suspend fun emptyTrash() = Unit
    override suspend fun exportAll(): List<Note> = all
    override suspend fun importNotes(notes: List<Note>) = ImportSummary(0, 0)
    override suspend fun purgeExpiredTrash(): Int = 0
}

private class PreviewTaskRepository(private val all: List<Task>) : TaskRepository {
    override val tasks: Flow<List<Task>> = flowOf(all)
    override suspend fun add(title: String, dueDate: Long?): Long? = null
    override suspend fun update(id: Long, title: String, dueDate: Long?) = Unit
    override suspend fun setCompleted(id: Long, completed: Boolean) = Unit
    override suspend fun delete(id: Long) = Unit
    override suspend fun clearCompleted(): Int = 0
}

/** Gives the screens the window size of the preview, like ElnorRoot does on a device. */
@Composable
private fun PreviewWindow(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWindowClass provides rememberWindowClass()) {
        ElnorTheme(darkTheme = dark) { content() }
    }
}

@Composable
private fun HomePreview(dark: Boolean = false) {
    val notes = remember { PreviewNoteRepository(sampleNotes) }
    val tasks = remember { PreviewTaskRepository(sampleTasks) }
    val editor = remember {
        NoteEditorViewModel(notes, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
    }
    PreviewWindow(dark) {
        ElnorApp(
            dark = dark,
            repository = notes,
            taskRepository = tasks,
            editorViewModel = editor,
            onOpenSettings = {},
            onOpenArchive = {},
            onOpenTrash = {}
        )
    }
}

@Composable
private fun EditorPreview(dark: Boolean = false) {
    val notes = remember { PreviewNoteRepository(sampleNotes) }
    val editor = remember {
        NoteEditorViewModel(notes, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
    }
    PreviewWindow(dark) {
        EditorScreen(note = sampleNotes[1], viewModel = editor, onClose = {})
    }
}

@Preview(name = "Phone portrait", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun PhonePortraitPreview() = HomePreview()

@Preview(name = "Phone landscape", widthDp = 800, heightDp = 360, showBackground = true)
@Composable
private fun PhoneLandscapePreview() = HomePreview()

@Preview(name = "Small tablet", widthDp = 700, heightDp = 900, showBackground = true)
@Composable
private fun SmallTabletPreview() = HomePreview()

@Preview(name = "Tablet portrait", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun TabletPortraitPreview() = HomePreview()

@Preview(name = "Foldable, open", widthDp = 841, heightDp = 701, showBackground = true)
@Composable
private fun FoldableOpenPreview() = HomePreview()

@Preview(name = "Tablet landscape", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun TabletLandscapePreview() = HomePreview()

@Preview(name = "Tablet landscape - dark", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun TabletLandscapeDarkPreview() = HomePreview(dark = true)

@Preview(name = "Editor - phone landscape", widthDp = 800, heightDp = 360, showBackground = true)
@Composable
private fun EditorPhoneLandscapePreview() = EditorPreview()

@Preview(name = "Editor - tablet", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun EditorTabletPreview() = EditorPreview()
