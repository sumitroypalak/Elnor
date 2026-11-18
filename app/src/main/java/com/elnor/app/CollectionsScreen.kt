@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ----------------------------- Collections tab ------------------------------

/**
 * The Collections tab: the list of collections (folders), and, when one is opened, the notes and
 * tasks in it. A note or a task is put in a collection from its own menu / dialog (see HomeScreen
 * and TasksScreen); here they can be opened, ticked and taken out again.
 *
 * [collections] is null until the database has answered, so "No collections yet" never flashes.
 * [openId] is the collection that is open (null: the list). The home screen keeps it, so it is still
 * open when the user comes back from a note. [notes] are the notes of the home list (not the
 * Archive, not the Trash), [tasks] all tasks. The creation of a collection (the round "+" button) is
 * also done by the home screen. Every change is sent through the callbacks, which use
 * [ApplicationScope] like the other tabs, so leaving the screen cannot cancel them.
 */
@Composable
internal fun CollectionsScreen(
    collections: List<NoteCollection>?,
    notes: List<Note>,
    tasks: List<Task>,
    padding: PaddingValues,
    openId: Long?,
    onOpenChange: (Long?) -> Unit,
    onOpenNote: (Long) -> Unit,
    onRename: (id: Long, name: String) -> Unit,
    onDelete: (id: Long) -> Unit,
    onRemoveNote: (noteId: Long) -> Unit,
    onToggleTask: (taskId: Long, done: Boolean) -> Unit,
    onRemoveTask: (taskId: Long) -> Unit
) {
    val sorted = remember(collections) { sortCollections(collections ?: emptyList()) }
    val counts = remember(notes, tasks) { countByCollection(notes, tasks) }
    val open = sorted.firstOrNull { it.id == openId }

    // The open collection was deleted (here or somewhere else): back to the list. Only checked when
    // the collections are really loaded, so an open collection that is restored after rotation stays.
    LaunchedEffect(collections, openId) {
        if (collections != null && openId != null && open == null) onOpenChange(null)
    }
    // Back closes the open collection first (it is registered after the one of the home screen, so
    // it is asked first)
    BackHandler(enabled = open != null) { onOpenChange(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .centeredMaxWidth(ReadingMaxWidth)
            .padding(padding)
            .padding(horizontal = 20.dp)
    ) {
        if (open != null) {
            CollectionDetail(
                collection = open,
                counts = counts[open.id],
                allCollections = sorted,
                notes = remember(notes, open.id) { notesInCollection(notes, open.id) },
                tasks = remember(tasks, open.id) { tasksInCollection(tasks, open.id) },
                onBack = { onOpenChange(null) },
                onOpenNote = onOpenNote,
                onRename = onRename,
                onDelete = onDelete,
                onRemoveNote = onRemoveNote,
                onToggleTask = onToggleTask,
                onRemoveTask = onRemoveTask
            )
        } else {
            CollectionList(
                collections = if (collections == null) null else sorted,
                counts = counts,
                onOpen = { onOpenChange(it.id) }
            )
        }
    }
}

@Composable
private fun CollectionList(
    collections: List<NoteCollection>?,
    counts: Map<Long, CollectionCounts>,
    onOpen: (NoteCollection) -> Unit
) {
    Text(
        when {
            collections == null -> ""
            collections.size == 1 -> "1 collection"
            else -> "${collections.size} collections"
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    )
    Spacer(Modifier.height(12.dp))

    when {
        collections == null -> Unit
        collections.isEmpty() -> EmptyCollections()
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // room for the floating bottom bar
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(collections, key = { it.id }) { collection ->
                CollectionCard(
                    modifier = Modifier,
                    name = collection.name,
                    label = collectionCountsLabel(counts[collection.id]),
                    onClick = { onOpen(collection) }
                )
            }
        }
    }
}

@Composable
private fun EmptyCollections() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "No collections yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap + to make a collection. Then use \"Move to collection\" in the menu of a note, or in the dialog of a task.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
        )
    }
}

@Composable
private fun CollectionCard(modifier: Modifier, name: String, label: String, onClick: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
private fun CollectionDetail(
    collection: NoteCollection,
    counts: CollectionCounts?,
    allCollections: List<NoteCollection>,
    notes: List<Note>,
    tasks: List<Task>,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onRename: (id: Long, name: String) -> Unit,
    onDelete: (id: Long) -> Unit,
    onRemoveNote: (noteId: Long) -> Unit,
    onToggleTask: (taskId: Long, done: Boolean) -> Unit,
    onRemoveTask: (taskId: Long) -> Unit
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to collections")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                collection.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                collectionCountsLabel(counts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )
        }
        TextButton(onClick = { renaming = true }) { Text("Rename") }
        TextButton(onClick = { confirmDelete = true }) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
        }
    }
    Spacer(Modifier.height(8.dp))

    if (notes.isEmpty() && tasks.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Nothing in this collection yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Use \"Move to collection\" in the menu of a note, or in the dialog of a task.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            if (notes.isNotEmpty()) {
                item(key = "notes-header") { SectionHeader("Notes") }
                items(notes, key = { "note-${it.id}" }) { note ->
                    ItemCard(onClick = { onOpenNote(note.id) }) {
                        val preview = remember(note) { firstPreviewLine(note) }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                getListTitle(note),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (preview.isNotEmpty()) {
                                Text(
                                    preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        RemoveButton { onRemoveNote(note.id) }
                    }
                }
            }
            if (tasks.isNotEmpty()) {
                item(key = "tasks-header") { SectionHeader("Tasks") }
                items(tasks, key = { "task-${it.id}" }) { task ->
                    ItemCard(onClick = { onToggleTask(task.id, !task.isCompleted) }, startPadding = 0) {
                        Checkbox(checked = task.isCompleted, onCheckedChange = { onToggleTask(task.id, it) })
                        val onSurface = MaterialTheme.colorScheme.onSurface
                        Text(
                            task.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (task.isCompleted) onSurface.copy(alpha = 0.5f) else onSurface,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f)
                        )
                        RemoveButton { onRemoveTask(task.id) }
                    }
                }
            }
        }
    }

    if (renaming) {
        CollectionNameDialog(
            title = "Rename collection",
            confirmLabel = "Save",
            initial = collection.name,
            existing = allCollections,
            exceptId = collection.id,
            onSave = { name ->
                renaming = false
                onRename(collection.id, name)
            },
            onDismiss = { renaming = false }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this collection?") },
            text = {
                Text("\"${collection.name}\" will be deleted. Its notes and tasks are not deleted: they just are not in a collection any more.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(collection.id)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** A card with one row inside: used for the notes and the tasks of a collection. */
@Composable
private fun ItemCard(
    onClick: () -> Unit,
    startPadding: Int = 16,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = startPadding.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Default.Clear,
            contentDescription = "Take out of this collection",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
    }
}

/** The first line of the text of a note that is not its name, without formatting marks. */
private fun firstPreviewLine(note: Note): String =
    MarkdownFormat.plainText(getPreviewBody(note)).lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: ""
