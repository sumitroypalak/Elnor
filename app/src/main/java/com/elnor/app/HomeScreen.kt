@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ----------------------------- Notes screen --------------------------------
//
// This file holds the state and the logic of the home screen. The parts that only draw are in their
// own files: HomeTopBar.kt (top bar and the bar of the selection), HomeNotesTab.kt (search field,
// "Your notes" line, list and grid), HomeNoteItem.kt (one note card and its menu) and
// HomeLayout.kt (rail, one-page and two-pane layout).

/** How long the user must stop typing in the search field before the search runs (milliseconds). */
private const val SEARCH_DEBOUNCE_MS = 200L

@Composable
fun ElnorApp(
    dark: Boolean,
    repository: NoteRepository,
    taskRepository: TaskRepository,
    editorViewModel: NoteEditorViewModel,
    onOpenSettings: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenTrash: () -> Unit
) {
    // The list follows the repository, so it updates by itself after every auto-save
    val notes by repository.notes.collectAsState()
    val notesLoaded by repository.notesLoaded.collectAsState()
    var search by remember { mutableStateOf("") }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var homeTab by rememberSaveable { mutableStateOf(HomeTab.NOTE) }
    // The "new task" dialog of the Task tab (opened by the round "+" button of the bottom bar)
    var newTaskOpen by rememberSaveable { mutableStateOf(false) }
    var editingNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Wide window: the list stays on the left and the editor opens on the right
    val windowClass = LocalWindowClass.current
    val twoPane = isTwoPane(windowClass)
    // Tablet or open foldable: the tabs and "+" are a rail on the left, not the bottom bar
    val showRail = showsNavigationRail(windowClass)
    val railWidthDp = if (showRail) NavigationRailWidth.value.toInt() else 0
    // Counts every opened page, so each note (or new note) gets a fresh editor
    var editorSession by rememberSaveable { mutableStateOf(0) }
    // An archived note can be opened too (it is a search result), so the editor looks in both
    // lists. null until the Archive is read, so such a note is not mistaken for a new one.
    val archivedNotes by repository.archivedNotes.collectAsState<List<Note>, List<Note>?>(initial = null)
    val everyNote = remember(notes, archivedNotes) { notes + (archivedNotes ?: emptyList()) }
    val editingNote = everyNote.firstOrNull { it.id == editingNoteId }

    val bgColor = MaterialTheme.colorScheme.background
    // The four pastel colours of the cards in the light theme. With the wallpaper colours (dynamic
    // colour) the cards take light colours of the theme instead, so they match the wallpaper. All of
    // them are light (tone 90 to 92), so the dark text of the cards can be read on any wallpaper.
    val scheme = MaterialTheme.colorScheme
    val cardColors = if (LocalDynamicColor.current) {
        listOf(
            scheme.primaryContainer,
            scheme.secondaryContainer,
            scheme.tertiaryContainer,
            scheme.surfaceContainerHigh
        )
    } else {
        listOf(
            Color(0xFFD9E2FF), // soft blue (was lavender)
            Color(0xFFDDF4F0),
            Color(0xFFFFEACD),
            Color(0xFFF9DFEA)
        )
    }
    // Search runs in the database, on the normalized searchText column.
    // The old list stays on screen until the new result arrives (no flicker while typing).
    var visibleNotes by remember { mutableStateOf(notes) }
    // Debounce: every new letter restarts this effect (the old run is cancelled), so the database
    // is asked only after the user stopped typing for SEARCH_DEBOUNCE_MS. An empty search (start,
    // or the clear button) is answered at once.
    LaunchedEffect(search) {
        if (search.isNotBlank()) delay(SEARCH_DEBOUNCE_MS)
        repository.search(search).collect { visibleNotes = it }
    }

    val listState = rememberLazyListState()
    val gridState = rememberLazyStaggeredGridState()

    // List (one note per row) or grid (two columns). Remembered on the phone.
    val context = LocalContext.current
    // Light vibrations: haptic(HapticKind.TICK) and so on (follows Settings > Haptic feedback)
    val haptic = rememberHaptic()
    var viewMode by remember { mutableStateOf(loadNotesViewMode(context)) }

    // Recent searches: listed under the search field while it is focused and empty
    var recentSearches by remember { mutableStateOf(loadRecentSearches(context)) }
    var searchFocused by remember { mutableStateOf(false) }
    // Saves a search the user really made (not every letter typed)
    fun commitSearch(text: String) {
        val updated = RecentSearches.add(recentSearches, text)
        if (updated != recentSearches) {
            recentSearches = updated
            saveRecentSearches(context, updated)
        }
    }

    // A note whose final save failed after its page closed (for example the storage is full).
    // Its text is not on any screen any more, so it can still be copied from this message.
    val snackbarHostState = remember { SnackbarHostState() }
    val lostNote by editorViewModel.lostNote.collectAsState()
    LaunchedEffect(lostNote) {
        val text = lostNote ?: return@LaunchedEffect
        haptic(HapticKind.REJECT) // a note was lost: more than a message deserves
        val result = snackbarHostState.showSnackbar(
            message = "Your last note could not be saved.",
            actionLabel = "Copy text",
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite
        )
        if (result == SnackbarResult.ActionPerformed) copyTextToClipboard(context, text)
        editorViewModel.clearLostNote()
    }

    // ---- Sort order ----
    var sort by remember { mutableStateOf(loadNotesSort(context)) }
    // Pinned notes first, then the order the user chose. The database gives the notes newest
    // first; this puts them in the chosen order.
    val shownNotes = remember(visibleNotes, sort) { sortNotes(visibleNotes, sort) }

    // ---- Selecting notes (touch and hold a note) ----
    // The ids of the selected notes (by id, never by list position). Kept when the screen is
    // recreated (for example rotation). No selected note = the normal screen.
    var selectedIds by rememberSaveable(
        stateSaver = listSaver<Set<Long>, Long>(save = { it.toList() }, restore = { it.toSet() })
    ) { mutableStateOf<Set<Long>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()
    val uiScope = rememberCoroutineScope()

    // Delete has no question: the notes go to the Trash at once and the message "moved to Trash"
    // offers Undo (see DeleteUndo.kt). [notes] are taken before the delete, so Undo can give them
    // back exactly as they were (also pinned).
    fun deleteNotes(notes: List<Note>) {
        if (notes.isEmpty()) return
        haptic(HapticKind.CONFIRM) // once, also for many notes
        deleteNotesWithUndo(uiScope, snackbarHostState, repository, notes)
    }

    // A selected note that is gone (deleted somewhere else) is not selected any more. Only checked
    // when the notes are really loaded, so a selection restored after rotation is not lost.
    LaunchedEffect(everyNote, notesLoaded, archivedNotes) {
        if (notesLoaded && archivedNotes != null && everyNote.isNotEmpty() && selectedIds.isNotEmpty()) {
            val existing = everyNote.mapTo(HashSet()) { it.id }
            val kept = selectedIds.filterTo(HashSet()) { it in existing }
            if (kept.size != selectedIds.size) selectedIds = kept
        }
    }

    fun toggleSelected(id: Long) {
        haptic(HapticKind.TICK) // one more note chosen (or taken out)
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    // The notes leave the list at once (the list follows the repository). The message offers Undo,
    // which brings them back exactly as they were (also pinned, if they were pinned).
    fun archiveNotes(ids: List<Long>) {
        if (ids.isEmpty()) return
        haptic(HapticKind.CONFIRM) // once, also for many notes
        ApplicationScope.scope.launch { repository.archiveMany(ids) }
        uiScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = if (ids.size == 1) "Note archived" else "${ids.size} notes archived",
                actionLabel = "Undo",
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                ApplicationScope.scope.launch { repository.unarchiveMany(ids) }
            }
        }
    }

    // Opens a note (or a new note for null). With the editor already open (two-pane), the note
    // that is open is saved and closed first, so the next one can start.
    fun openNote(id: Long?) {
        if (editorOpen) {
            if (id != null && id == editingNoteId) return // already open
            editorViewModel.onBack()
        }
        editorSession++
        editingNoteId = id
        editorOpen = true
    }

    // The tabs and the round "+" button, used by the bottom bar and by the rail
    fun selectHomeTab(tab: HomeTab) {
        if (tab != homeTab) haptic(HapticKind.TICK)
        homeTab = tab
    }

    fun onNewNoteClick() {
        haptic(HapticKind.TICK)
        if (homeTab == HomeTab.TASK) {
            // On the Task tab the "+" button adds a task, not a note
            newTaskOpen = true
        } else {
            homeTab = HomeTab.NOTE
            openNote(null)
        }
    }

    // "New note" from the launcher shortcut or the Quick Settings tile (see QuickNote). Always a
    // note, also when the Task tab was open. A note that is open is saved first (openNote does it).
    val newNoteWaiting by QuickNote.requests.pending.collectAsState()
    LaunchedEffect(newNoteWaiting) {
        if (newNoteWaiting && QuickNote.requests.consume()) {
            selectedIds = emptySet() // an action of the selection bar must not reach a closed page
            homeTab = HomeTab.NOTE
            openNote(null)
        }
    }

    // Two-pane: the note that is open can disappear from the list (deleted there). Its page
    // is then closed, and the right side shows "Select a note" again.
    LaunchedEffect(twoPane, editorOpen, editingNoteId, everyNote, notesLoaded, archivedNotes) {
        val id = editingNoteId
        if (twoPane && editorOpen && id != null && notesLoaded && archivedNotes != null &&
            everyNote.none { it.id == id }
        ) {
            editorViewModel.onBack()
            editorOpen = false
        }
    }

    // One note card, used by the list and by the grid
    val noteItem: @Composable (Note, Modifier) -> Unit = { note, itemModifier ->
        NoteListItem(
            note = note,
            modifier = itemModifier,
            dark = dark,
            cardColors = cardColors,
            selected = note.id in selectedIds,
            // No three-dot menu while selecting: the bar at the top has the actions
            menuActions = if (selectionMode) {
                emptyList()
            } else {
                noteMenuActions(
                    note = note,
                    haptic = haptic,
                    repository = repository,
                    onArchive = { archiveNotes(listOf(note.id)) },
                    onDelete = { deleteNotes(listOf(note)) }
                )
            },
            onClick = {
                if (selectionMode) {
                    toggleSelected(note.id)
                } else {
                    // Opening a result shows the search was useful: remember it
                    commitSearch(search)
                    openNote(note.id)
                }
            },
            // Touch and hold: start selecting (or select one more note)
            onLongClick = {
                if (selectionMode) {
                    toggleSelected(note.id)
                } else {
                    haptic(HapticKind.LONG_PRESS) // the selection starts
                    selectedIds = setOf(note.id)
                }
            }
        )
    }

    // The editor: the page of one note (or a new note)
    val editorPane: @Composable () -> Unit = {
        key(editorSession) {
            if (editingNoteId != null && (!notesLoaded || (editingNote == null && archivedNotes == null))) {
                // The page came back after the app was closed: wait for the notes, so the note that
                // was open is found and not mistaken for a new one
                Box(Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                EditorScreen(
                    note = editingNote,
                    viewModel = editorViewModel,
                    onClose = { editorOpen = false },
                    // The editor already moved the note to the Trash. The list shows the message.
                    onNoteDeleted = { deleted ->
                        uiScope.launch { showDeleteUndo(snackbarHostState, repository, listOf(deleted)) }
                    }
                )
            }
        }
    }

    // The note list with its bars, tabs and side menu
    val listPane: @Composable () -> Unit = {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val drawerScope = rememberCoroutineScope()
        // Back from Task or Collection returns to Notes (registered first, so the menu below wins)
        BackHandler(enabled = homeTab != HomeTab.NOTE) { homeTab = HomeTab.NOTE }
        // The Back button closes the side menu first
        BackHandler(enabled = drawerState.isOpen) {
            drawerScope.launch { drawerState.close() }
        }
        // While notes are selected, Back only ends the selection (registered last, so it wins)
        BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

        EndDrawer(
            drawerState = drawerState,
            onOpenSettings = onOpenSettings,
            onOpenArchive = onOpenArchive,
            onOpenTrash = onOpenTrash
        ) {
            Scaffold(
                containerColor = bgColor,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    HomeTopBar(
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        everyNote = everyNote,
                        shownNotes = shownNotes,
                        bgColor = bgColor,
                        repository = repository,
                        onMenuClick = { drawerScope.launch { drawerState.open() } },
                        onClearSelection = { selectedIds = emptySet() },
                        onSelectAll = { selectedIds = it },
                        onArchiveNotes = { archiveNotes(it) },
                        onDeleteNotes = { deleteNotes(it) }
                    )
                },
                // The bottom bar floats over the list, in the place of the old "New note" button
                floatingActionButton = {
                    // No "new note" button while notes are selected
                    if (!selectionMode && !showRail) ElnorBottomBar(
                        selected = homeTab,
                        dark = dark,
                        onSelect = { selectHomeTab(it) },
                        onNewNote = { onNewNoteClick() }
                    )
                },
                floatingActionButtonPosition = FabPosition.Center
            ) { padding ->
                // The three tabs fade into each other (no slide: they are on the same level).
                // Collection is a placeholder for now. Notes and Tasks have content.
                Crossfade(
                    targetState = homeTab,
                    animationSpec = Motion.short(),
                    label = "homeTab"
                ) { tab ->
                    if (tab != HomeTab.NOTE) {
                        if (tab == HomeTab.TASK) {
                            TasksScreen(
                                repository = taskRepository,
                                padding = padding,
                                addDialogOpen = newTaskOpen,
                                onAddDialogDismiss = { newTaskOpen = false },
                                snackbarHostState = snackbarHostState
                            )
                        } else {
                            HomeTabPlaceholder(
                                title = "Collections are coming soon",
                                subtitle = "Group your notes and tasks here.",
                                padding = padding
                            )
                        }
                    } else {
                        NotesTabContent(
                            padding = padding,
                            bgColor = bgColor,
                            compactHeight = windowClass.compactHeight,
                            search = search,
                            // A new search ends the selection, so an action never reaches a note that is not on the screen
                            onSearchChange = {
                                if (it != search) selectedIds = emptySet()
                                search = it
                            },
                            onClearSearch = {
                                search = ""
                                selectedIds = emptySet()
                            },
                            onSearchFocusChange = { focused ->
                                // Leaving the field with a search typed counts as a search
                                if (searchFocused && !focused) commitSearch(search)
                                searchFocused = focused
                            },
                            onSearchSubmit = { commitSearch(search) },
                            showRecentSearches = searchFocused && search.isEmpty() && recentSearches.isNotEmpty(),
                            recentSearches = recentSearches,
                            onPickRecent = { picked ->
                                search = picked
                                selectedIds = emptySet()
                            },
                            onRemoveRecent = { item ->
                                recentSearches = RecentSearches.remove(recentSearches, item)
                                saveRecentSearches(context, recentSearches)
                            },
                            onClearRecents = {
                                recentSearches = emptyList()
                                saveRecentSearches(context, emptyList())
                            },
                            shownNotes = shownNotes,
                            notesLoaded = notesLoaded,
                            sort = sort,
                            onSortChange = { option ->
                                if (option != sort) haptic(HapticKind.TICK)
                                sort = option
                                saveNotesSort(context, option)
                            },
                            viewMode = viewMode,
                            onToggleViewMode = {
                                haptic(HapticKind.TICK)
                                viewMode = viewMode.toggled()
                                saveNotesViewMode(context, viewMode)
                            },
                            listState = listState,
                            gridState = gridState,
                            showRail = showRail,
                            // In two-pane the list is only TwoPaneListWidth wide
                            gridColumns = gridColumnCount(
                                if (twoPane) TwoPaneListWidth.value.toInt()
                                else windowClass.widthDp - railWidthDp
                            ),
                            noteItem = noteItem
                        )
                    }
                }
            }
        }
    }

    HomeLayout(
        bgColor = bgColor,
        dark = dark,
        showRail = showRail,
        twoPane = twoPane,
        editorOpen = editorOpen,
        homeTab = homeTab,
        onSelectTab = { selectHomeTab(it) },
        onNewNote = { onNewNoteClick() },
        listPane = listPane,
        editorPane = editorPane
    )
}
