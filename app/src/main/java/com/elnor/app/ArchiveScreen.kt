@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// ----------------------------- Archive screen ------------------------------

/**
 * The page the "Archive" menu item opens. It lists the notes that were archived: notes the user
 * wants to keep but not see on the home screen every day.
 *  - The search box above the list finds archived notes (the home search finds them too, marked
 *    "Archived").
 *  - Tapping a note opens it in the editor, like on the home screen. It stays archived while it
 *    is edited.
 *  - A note can be unarchived (it goes back to the home list exactly as it was, also pinned if it
 *    was pinned) or deleted (it goes to the Trash).
 *
 * Changes are sent through [ApplicationScope], like saves in the editor, so leaving this page at
 * the same moment cannot cancel them half-way. The editor is the same [editorViewModel] as on the
 * home screen (only one of the two screens is open at a time).
 */
@Composable
internal fun ArchiveScreen(
    repository: NoteRepository,
    editorViewModel: NoteEditorViewModel,
    onBack: () -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.background
    // null until the first list is read, so "Archive is empty" does not flash on the screen
    val archived by repository.archivedNotes.collectAsState<List<Note>, List<Note>?>(initial = null)
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    // The note that is open in the editor (by id). Kept when the screen is recreated.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val notes = archived

    val openId = editingId
    if (openId != null) {
        val editing = notes?.firstOrNull { it.id == openId }
        when {
            // The page came back after the app was closed: wait for the notes, so the note that
            // was open is found and not mistaken for a new one
            notes == null -> Box(
                Modifier.fillMaxSize().background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            // The note is gone (deleted or unarchived meanwhile): back to the list
            editing == null -> {
                LaunchedEffect(openId) { editingId = null }
                Box(Modifier.fillMaxSize().background(bgColor))
            }

            else -> EditorScreen(
                note = editing,
                viewModel = editorViewModel,
                onClose = { editingId = null }
            )
        }
        return
    }

    // Archived notes that match the search (all of them while the search box is empty)
    val shown = remember(notes, query) {
        notes?.filter { SearchText.matches(it.title, it.body, query) }
    }

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = { Text("Archive", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // The search box is only useful when there is something to search
            if (!notes.isNullOrEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    placeholder = { Text("Search archived notes...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    // The clear button is only there while there is text
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = CircleShape
                )
            }

            when {
                // Still reading: show only the background
                notes == null || shown == null -> Box(Modifier.weight(1f).fillMaxWidth())

                notes.isEmpty() -> EmptyMessage(
                    title = "Archive is empty",
                    subtitle = "Touch and hold a note on the home screen to select it, " +
                        "then choose Archive."
                )

                shown.isEmpty() -> EmptyMessage(
                    title = "No notes found",
                    subtitle = "Try searching with another word."
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    if (query.isBlank()) {
                        item {
                            Text(
                                "Archived notes are kept here and stay out of your list. " +
                                    "Tap a note to open and edit it.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                            )
                        }
                    }
                    items(shown, key = { it.id }) { note ->
                        ArchivedNoteCard(
                            note = note,
                            onOpen = { editingId = note.id },
                            onUnarchive = {
                                ApplicationScope.scope.launch {
                                    repository.unarchiveMany(listOf(note.id))
                                }
                            },
                            onDelete = { pendingDeleteId = note.id }
                        )
                    }
                }
            }
        }
    }

    val deleteId = pendingDeleteId
    if (deleteId != null) {
        DeleteNoteDialog(
            onConfirm = {
                pendingDeleteId = null
                ApplicationScope.scope.launch { repository.moveToTrashMany(listOf(deleteId)) }
            },
            onDismiss = { pendingDeleteId = null }
        )
    }
}

/** A centred message in the rest of the screen (empty Archive, nothing found). */
@Composable
private fun ColumnScope.EmptyMessage(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
        )
    }
}

/** One archived note. Tapping the card opens it in the editor. */
@Composable
private fun ArchivedNoteCard(
    note: Note,
    onOpen: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Column(
            Modifier.padding(start = 20.dp, top = 20.dp, end = 12.dp, bottom = 8.dp)
        ) {
            Text(
                getListTitle(note),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val preview = MarkdownFormat.plainText(getPreviewBody(note))
            if (preview.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Archived " + formatNoteDate(note.archivedAt ?: note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onUnarchive) {
                    Text("Unarchive")
                }
            }
        }
    }
}
