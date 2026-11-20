@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.elnor.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
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
 *  - Many notes at once: touch and hold a note, then tap more notes. The bar at the top then has
 *    "Unarchive" and a delete button for all selected notes, and "Select all" in its three-dot
 *    menu. Back or the X ends the selection. "Unarchive all" in the normal bar brings back every
 *    note (it is only shown while the search box is empty).
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
    // These live here, above the animation, so they are the same for the list and for the editor
    // while one slides in and the other goes out.
    // null until the first list is read, so "Archive is empty" does not flash on the screen
    val archived by repository.archivedNotes.collectAsState<List<Note>, List<Note>?>(initial = null)
    var query by rememberSaveable { mutableStateOf("") }
    // The notes chosen by touch and hold (empty: not selecting)
    var selectedIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    // The note that is open in the editor (by id). Kept when the screen is recreated.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    // The "moved to Trash" message with Undo. The host and the scope are here, above the animation,
    // so the message survives the editor sliding out after a delete in the editor.
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()

    // The editor rises a little and fades in over the list, and fades away when it closes
    AnimatedContent(
        targetState = editingId,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        transitionSpec = { Motion.page(opening = targetState != null) },
        label = "archiveEditor"
    ) { shownId ->
        ArchiveContent(
            repository = repository,
            editorViewModel = editorViewModel,
            notes = archived,
            openId = shownId,
            // A page that is already sliding out must not answer Back a second time
            leaving = transition.targetState == EnterExitState.PostExit,
            query = query,
            onQueryChange = { query = it },
            selectedIds = selectedIds,
            onSelectedChange = { selectedIds = it },
            onOpenNote = {
                selectedIds = emptySet()
                editingId = it
            },
            onCloseEditor = { editingId = null },
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            uiScope = uiScope
        )
    }
}

/** The list of archived notes ([openId] null), or the editor of one note (the animation switches). */
@Composable
private fun ArchiveContent(
    repository: NoteRepository,
    editorViewModel: NoteEditorViewModel,
    notes: List<Note>?,
    openId: Long?,
    leaving: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedIds: Set<Long>,
    onSelectedChange: (Set<Long>) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCloseEditor: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    uiScope: CoroutineScope
) {
    val bgColor = MaterialTheme.colorScheme.background
    // Light vibrations (follow Settings > Haptic feedback)
    val haptic = rememberHaptic()

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
                LaunchedEffect(openId) { onCloseEditor() }
                Box(Modifier.fillMaxSize().background(bgColor))
            }

            else -> {
                EditorScreen(
                    note = editing,
                    viewModel = editorViewModel,
                    onClose = onCloseEditor,
                    // The editor already moved the note to the Trash. The list shows the message.
                    onNoteDeleted = { deleted ->
                        uiScope.launch {
                            showDeleteUndo(snackbarHostState, repository, listOf(deleted))
                        }
                    }
                )
                // Registered after the editor, so it is asked first: while the page slides out,
                // Back must not save and close it a second time
                BackHandler(enabled = leaving) { }
            }
        }
        return
    }

    // Archived notes that match the search (all of them while the search box is empty)
    val shown = remember(notes, query) {
        notes?.filter { SearchText.matches(it.title, it.body, query) }
    }
    // The selected notes that are on the screen now. A selected note that the search hides, or
    // that left the Archive, is not counted and no button works on it.
    val selection = remember(shown, selectedIds) {
        shown?.filter { it.id in selectedIds } ?: emptyList()
    }
    val selecting = selection.isNotEmpty()
    // Back ends the selection first (and not while the page is already sliding out)
    BackHandler(enabled = selecting && !leaving) { onSelectedChange(emptySet()) }

    // The count of the bar that is fading out stays what it was, so it does not show "0"
    val lastSelectedCount = remember { intArrayOf(0) }
    if (selecting) lastSelectedCount[0] = selection.size

    Scaffold(
        containerColor = bgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Crossfade(
                targetState = selecting,
                animationSpec = Motion.short(),
                label = "archiveTopBar"
            ) { selectingNow ->
                if (selectingNow) {
                    BulkSelectionTopBar(
                        count = lastSelectedCount[0],
                        containerColor = bgColor,
                        onClose = { onSelectedChange(emptySet()) },
                        onSelectAll = { onSelectedChange(shown.orEmpty().map { it.id }.toSet()) }
                    ) {
                        TextButton(
                            onClick = {
                                val ids = selection.map { it.id }
                                onSelectedChange(emptySet())
                                haptic(HapticKind.CONFIRM)
                                ApplicationScope.scope.launch { repository.unarchiveMany(ids) }
                                showPlainMessage(
                                    uiScope,
                                    snackbarHostState,
                                    unarchivedMessage(ids.size)
                                )
                            }
                        ) {
                            Text("Unarchive")
                        }
                        // No question: to the Trash at once, with Undo
                        IconButton(
                            onClick = {
                                val chosen = selection
                                onSelectedChange(emptySet())
                                haptic(HapticKind.CONFIRM)
                                deleteNotesWithUndo(uiScope, snackbarHostState, repository, chosen)
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete selected notes",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
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
                        actions = {
                            // Only while the search box is empty: "all" then really means all notes
                            if (!notes.isNullOrEmpty() && query.isBlank()) {
                                TextButton(
                                    onClick = {
                                        val ids = notes.map { it.id }
                                        haptic(HapticKind.CONFIRM)
                                        ApplicationScope.scope.launch { repository.unarchiveMany(ids) }
                                        showPlainMessage(
                                            uiScope,
                                            snackbarHostState,
                                            unarchivedMessage(ids.size)
                                        )
                                    }
                                ) {
                                    Text("Unarchive all")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .centeredMaxWidth(ReadingMaxWidth)
                .padding(padding)
        ) {
            // The search box is only useful when there is something to search
            if (!notes.isNullOrEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    placeholder = { Text("Search archived notes...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    // The clear button is only there while there is text
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { onQueryChange("") }) {
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
                        item(key = "archive-header") {
                            Text(
                                "Archived notes are kept here and stay out of your list. " +
                                    "Tap a note to open and edit it. Touch and hold a note " +
                                    "to select many.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                    items(shown, key = { it.id }) { note ->
                        ArchivedNoteCard(
                            // A note that is unarchived or deleted fades out, the others close the gap
                            modifier = Modifier.animateItem(),
                            note = note,
                            selectionMode = selecting,
                            selected = selecting && note.id in selectedIds,
                            // While selecting, a tap selects or unselects instead of opening
                            onClick = {
                                if (selecting) {
                                    haptic(HapticKind.TICK)
                                    onSelectedChange(toggleSelection(selectedIds, note.id))
                                } else {
                                    onOpenNote(note.id)
                                }
                            },
                            onLongClick = {
                                haptic(HapticKind.LONG_PRESS)
                                onSelectedChange(selectedIds + note.id)
                            },
                            onUnarchive = {
                                ApplicationScope.scope.launch {
                                    repository.unarchiveMany(listOf(note.id))
                                }
                            },
                            // No question: to the Trash at once, with Undo
                            onDelete = {
                                deleteNotesWithUndo(uiScope, snackbarHostState, repository, listOf(note))
                            }
                        )
                    }
                }
            }
        }
    }
}

/** A centred message in the rest of the screen (empty Archive, nothing found). */
@Composable
private fun ColumnScope.EmptyMessage(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .fadeInOnAppear(),
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

/**
 * One archived note. Tapping the card calls [onClick] (open it, or select it while notes are being
 * selected); touch and hold calls [onLongClick]. While selecting ([selectionMode]) the Delete /
 * Unarchive buttons of the card are hidden. A [selected] card has a border and a check mark.
 */
@Composable
private fun ArchivedNoteCard(
    note: Note,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.extraLarge

    // Not Card(onClick = ...): that one has no long press. The clip makes the press ripple round.
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
                if (selectionMode) {
                    Spacer(Modifier.height(12.dp))
                } else {
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
