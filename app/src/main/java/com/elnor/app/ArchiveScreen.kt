@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
 * wants to keep but not see on the home screen every day. A note can be unarchived (it goes back
 * to the home list exactly as it was, also pinned if it was pinned) or deleted (it goes to the
 * Trash). Archived notes are not shown on the home screen or in the search.
 *
 * Changes are sent through [ApplicationScope], like saves in the editor, so leaving this page at
 * the same moment cannot cancel them half-way.
 */
@Composable
internal fun ArchiveScreen(
    repository: NoteRepository,
    onBack: () -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.background
    // null until the first list is read, so "Archive is empty" does not flash on the screen
    val archived by repository.archivedNotes.collectAsState<List<Note>, List<Note>?>(initial = null)
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val notes = archived

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
        when {
            // Still reading: show only the background
            notes == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )

            notes.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Archive is empty",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Touch and hold a note on the home screen to select it, then choose Archive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                item {
                    Text(
                        "Archived notes are kept here and stay out of your list and search. " +
                            "Tap a note to read all of it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                }
                items(notes, key = { it.id }) { note ->
                    ArchivedNoteCard(
                        note = note,
                        onUnarchive = {
                            ApplicationScope.scope.launch { repository.unarchiveMany(listOf(note.id)) }
                        },
                        onDelete = { pendingDeleteId = note.id }
                    )
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

/** One archived note. Tapping the card shows the whole note (it is cut to a few lines otherwise). */
@Composable
private fun ArchivedNoteCard(
    note: Note,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable(note.id) { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
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
                getDisplayTitle(note),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
            val preview = getPreviewBody(note)
            if (preview.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
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
