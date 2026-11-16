@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.elnor.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// ----------------------------- Trash screen --------------------------------

/**
 * The page the "Trash" menu item opens. It lists the notes that were deleted. A note can be
 * restored (it goes back to the notes list exactly as it was) or deleted for good, and
 * "Empty trash" deletes all of them for good. Notes in the Trash are not shown on the home
 * screen or in the search.
 *
 * Many notes at once:
 *  - Touch and hold a note, then tap more notes. The bar at the top then has "Restore" and a
 *    delete button for all selected notes, and "Select all" in its three-dot menu. Back or the X
 *    ends the selection.
 *  - "Restore all" in the normal bar takes every note out of the Trash.
 * Deleting for good always asks first, because that cannot be undone. Restoring does not ask.
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
    var confirmDeleteSelected by rememberSaveable { mutableStateOf(false) }
    // The notes chosen by touch and hold (empty: not selecting)
    var selectedIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    val notes = trashed
    // The time chosen in Settings > Trash (30 days if never changed)
    val context = LocalContext.current
    // Light vibrations (follow Settings > Haptic feedback)
    val haptic = rememberHaptic()
    val trashRetention = remember { loadTrashRetention(context) }
    val trashDays = trashRetention.days
    // Taken once when the page opens: "days left" does not need to change while it is on screen
    val now = remember { System.currentTimeMillis() }
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()

    // Only notes that are still in the Trash count (one can leave it while selected, for example
    // when its time is over)
    val selection = remember(notes, selectedIds) {
        notes?.filter { it.id in selectedIds }?.map { it.id } ?: emptyList()
    }
    val selecting = selection.isNotEmpty()
    BackHandler(enabled = selecting) { selectedIds = emptySet() }

    // Opening the Trash also removes the notes that have been here for the chosen time. The list
    // updates by itself when they are gone.
    LaunchedEffect(Unit) {
        repository.purgeExpiredTrashInBackground()
    }

    Scaffold(
        containerColor = bgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selecting) {
                BulkSelectionTopBar(
                    count = selection.size,
                    containerColor = bgColor,
                    onClose = { selectedIds = emptySet() },
                    onSelectAll = { selectedIds = notes.orEmpty().map { it.id }.toSet() }
                ) {
                    TextButton(
                        onClick = {
                            val ids = selection
                            selectedIds = emptySet()
                            haptic(HapticKind.CONFIRM)
                            ApplicationScope.scope.launch { repository.restoreMany(ids) }
                            showPlainMessage(uiScope, snackbarHostState, restoredMessage(ids.size))
                        }
                    ) {
                        Text("Restore")
                    }
                    IconButton(onClick = { confirmDeleteSelected = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete selected notes forever",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
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
                            // No question: nothing is lost by restoring
                            TextButton(
                                onClick = {
                                    val ids = notes.map { it.id }
                                    haptic(HapticKind.CONFIRM)
                                    ApplicationScope.scope.launch { repository.restoreMany(ids) }
                                    showPlainMessage(
                                        uiScope,
                                        snackbarHostState,
                                        restoredMessage(ids.size)
                                    )
                                }
                            ) {
                                Text("Restore all")
                            }
                            TextButton(onClick = { confirmEmpty = true }) {
                                Text("Empty trash", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
                )
            }
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
                    .centeredMaxWidth(ReadingMaxWidth)
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
                            "moved here. Tap a note to read all of it. Touch and hold " +
                            "a note to select many.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                }
                items(notes, key = { it.id }) { note ->
                    TrashNoteCard(
                        note = note,
                        now = now,
                        retentionMs = trashRetention.millis,
                        selectionMode = selecting,
                        selected = selecting && note.id in selectedIds,
                        onToggleSelect = {
                            haptic(HapticKind.TICK)
                            selectedIds = toggleSelection(selectedIds, note.id)
                        },
                        onLongClick = {
                            haptic(HapticKind.LONG_PRESS)
                            selectedIds = selectedIds + note.id
                        },
                        onRestore = {
                            haptic(HapticKind.CONFIRM)
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
                        haptic(HapticKind.CONFIRM)
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

    // The selected notes, deleted for good (always asks, it cannot be undone)
    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text("Delete forever?", fontWeight = FontWeight.Bold) },
            text = { Text(deleteForeverQuestion(selection.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteSelected = false
                        val ids = selection
                        selectedIds = emptySet()
                        haptic(HapticKind.CONFIRM)
                        ApplicationScope.scope.launch { repository.deletePermanentlyMany(ids) }
                    }
                ) {
                    Text("Delete forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelected = false }) {
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
                        haptic(HapticKind.CONFIRM)
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
 *
 * While notes are being selected ([selectionMode]) a tap selects or unselects the note instead,
 * and the Restore / Delete forever buttons of the card are hidden, so a button is not pressed by
 * mistake. Touch and hold calls [onLongClick]. A [selected] card has a border and a check mark,
 * like on the home screen.
 */
@Composable
private fun TrashNoteCard(
    note: Note,
    now: Long,
    retentionMs: Long,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onLongClick: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    var expanded by rememberSaveable(note.id) { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.extraLarge

    // Not Card(onClick = ...): that one has no long press. The clip makes the press ripple round.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else expanded = !expanded },
                onLongClick = onLongClick
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(start = 20.dp, top = 20.dp, end = 12.dp, bottom = 8.dp)
            ) {
                Text(
                    getListTitle(note),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    // keeps the title out from under the check mark
                    modifier = if (selected) Modifier.padding(end = 32.dp) else Modifier,
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
                // How long the note can still be restored. Red when 3 days or less are left.
                val deletedAt = note.deletedAt ?: note.updatedAt
                val urgent = TrashDaysLeft.isUrgent(deletedAt, now, retentionMs)
                Spacer(Modifier.height(2.dp))
                Text(
                    TrashDaysLeft.label(deletedAt, now, retentionMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (urgent) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (urgent) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                if (selectionMode) {
                    Spacer(Modifier.height(12.dp))
                } else {
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

            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                )
            }
        }
    }
}

/** Same date style as the notes list, e.g. "12 Oct 2026 \u00B7 08:35 AM". */
internal fun formatNoteDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy \u00B7 hh:mm a", Locale.getDefault()).format(Date(millis))
