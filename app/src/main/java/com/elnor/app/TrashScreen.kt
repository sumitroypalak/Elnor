@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ----------------------------- Trash screen --------------------------------

/**
 * The page the "Trash" menu item opens. It lists the notes that were deleted. A note can be
 * restored (it goes back to the notes list exactly as it was) or deleted for good, and
 * "Empty trash" deletes all of them for good. Notes in the Trash are not shown on the home
 * screen or in the search.
 *
 * Changes are sent through [ApplicationScope], like saves in the editor, so leaving this page
 * at the same moment cannot cancel them half-way.
 */
@Composable
internal fun TrashScreen(
    repository: NoteRepository,
    onBack: () -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.background
    // null until the first list is read, so "Trash is empty" does not flash on the screen
    val trashed by repository.trashedNotes.collectAsState<List<Note>, List<Note>?>(initial = null)
    // rememberSaveable: the dialogs stay open if the screen is recreated (for example rotation)
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    val notes = trashed
    // The time chosen in Settings > Trash (30 days if never changed)
    val context = LocalContext.current
    val trashDays = remember { loadTrashRetention(context).days }

    // Opening the Trash also removes the notes that have been here for the chosen time. The list
    // updates by itself when they are gone.
    LaunchedEffect(Unit) {
        repository.purgeExpiredTrashInBackground()
    }

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = { Text("Trash", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!notes.isNullOrEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) {
                            Text("Empty trash", color = MaterialTheme.colorScheme.error)
                        }
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
                Text("\uD83D\uDDD1\uFE0F", fontSize = 54.sp, lineHeight = 76.sp)
                Spacer(Modifier.height(14.dp))
                Text(
                    "Trash is empty",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Deleted notes will appear here.",
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
                        "Notes are deleted for good $trashDays days after they were " +
                            "moved here. Tap a note to read all of it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                }
                items(notes, key = { it.id }) { note ->
                    TrashNoteCard(
                        note = note,
                        onRestore = {
                            ApplicationScope.scope.launch { repository.restore(note.id) }
                        },
                        onDeleteForever = { pendingDeleteId = note.id }
                    )
                }
            }
        }
    }

    val deleteId = pendingDeleteId
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete forever?", fontWeight = FontWeight.Bold) },
            text = { Text("This note will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteId = null
                        ApplicationScope.scope.launch { repository.deletePermanently(deleteId) }
                    }
                ) {
                    Text("Delete forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty trash?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Every note in the Trash will be permanently deleted. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEmpty = false
                        ApplicationScope.scope.launch { repository.emptyTrash() }
                    }
                ) {
                    Text("Empty trash", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * One note in the Trash. Tapping the card shows the whole note (it is cut to a few lines
 * otherwise), so the user can read it before restoring it or deleting it for good.
 */
@Composable
private fun TrashNoteCard(
    note: Note,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
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
                getListTitle(note),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
            val preview = MarkdownFormat.plainText(getPreviewBody(note))
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
                "Deleted " + formatNoteDate(note.deletedAt ?: note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDeleteForever) {
                    Text("Delete forever", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onRestore) {
                    Text("Restore")
                }
            }
        }
    }
}

/** Same date style as the notes list, e.g. "12 Oct 2026 \u00B7 08:35 AM". */
internal fun formatNoteDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy \u00B7 hh:mm a", Locale.getDefault()).format(Date(millis))
