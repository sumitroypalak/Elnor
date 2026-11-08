@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ----------------------------- Notes screen --------------------------------

/** What the notes screen shows below the "Your notes" line. */
private enum class NotesPane { LOADING, EMPTY, NOTES }

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
    val focusManager = LocalFocusManager.current
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

    // The editor page rises a little and fades in over the list; when it closes it fades away
    // and the list shows through. The page colour behind keeps the two from showing through
    // each other.
    AnimatedContent(
        targetState = editorOpen,
        modifier = Modifier.fillMaxSize().background(bgColor),
        transitionSpec = { Motion.page(opening = targetState) },
        label = "homeEditor"
    ) { showEditor ->
    // A page that is already sliding out must not answer Back again (the editor would save and
    // close twice). Registered after the page below, so it is asked first.
    val leaving = transition.targetState == EnterExitState.PostExit
    if (showEditor) {
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
        BackHandler(enabled = leaving) { }
    } else {
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
                  // The normal bar and the bar of the selection fade into each other. The number of
                  // selected notes is remembered, so the bar that is fading out does not show "0".
                  val lastSelectedCount = remember { intArrayOf(0) }
                  if (selectionMode) lastSelectedCount[0] = selectedIds.size
                  Crossfade(
                      targetState = selectionMode,
                      animationSpec = Motion.short(),
                      label = "topBar"
                  ) { selecting ->
                  if (selecting) {
                    // The selected notes. An archived note (a search result) cannot be pinned, and
                    // "Archive" does nothing for it, so those actions only look at the other notes.
                    val selectedNotes = everyNote.filter { it.id in selectedIds }
                    val pinTargets = selectedNotes.filter { it.archivedAt == null }
                    val allArchived = selectedNotes.isNotEmpty() && pinTargets.isEmpty()
                    SelectionTopBar(
                        count = lastSelectedCount[0],
                        canPin = pinTargets.isNotEmpty(),
                        allPinned = pinTargets.isNotEmpty() && pinTargets.all { it.pinnedAt != null },
                        allArchived = allArchived,
                        containerColor = bgColor,
                        onClose = { selectedIds = emptySet() },
                        onTogglePin = {
                            val ids = pinTargets.map { it.id }
                            // all pinned -> unpin them all, otherwise pin them all
                            val pin = !pinTargets.all { it.pinnedAt != null }
                            haptic(HapticKind.CONFIRM)
                            ApplicationScope.scope.launch { repository.setPinnedMany(ids, pin) }
                            selectedIds = emptySet()
                        },
                        onArchive = {
                            selectedIds = emptySet()
                            if (allArchived) {
                                val ids = selectedNotes.map { it.id }
                                haptic(HapticKind.CONFIRM)
                                ApplicationScope.scope.launch { repository.unarchiveMany(ids) }
                            } else {
                                archiveNotes(pinTargets.map { it.id })
                            }
                        },
                        onDelete = {
                            // The notes as they are now (with their pin), then the selection ends
                            val targets = selectedNotes
                            selectedIds = emptySet()
                            deleteNotes(targets)
                        },
                        onSelectAll = {
                            haptic(HapticKind.TICK)
                            selectedIds = shownNotes.mapTo(HashSet()) { it.id }
                        }
                    )
                  } else {
                    // A normal-height bar: the title and the menu button share one line, so there
                    // is no empty space above the title. The 4.dp / 16.dp paddings put the title
                    // and the menu button on the same 20.dp edges as the tagline and the search bar.
                    TopAppBar(
                        title = {
                            Text(
                                "Elnor",
                                modifier = Modifier.padding(start = 4.dp),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        actions = {
                            // Hamburger button: opens the side menu (Settings, Trash)
                            FilledTonalIconButton(
                                onClick = { drawerScope.launch { drawerState.open() } },
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Open menu")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = bgColor,
                            scrolledContainerColor = bgColor
                        )
                    )
                  }
                  }
                },
                // The bottom bar floats over the list, in the place of the old "New note" button
                floatingActionButton = {
                    // No "new note" button while notes are selected
                    if (!selectionMode) ElnorBottomBar(
                        selected = homeTab,
                        dark = dark,
                        onSelect = {
                            if (it != homeTab) haptic(HapticKind.TICK)
                            homeTab = it
                        },
                        onNewNote = {
                            haptic(HapticKind.TICK)
                            if (homeTab == HomeTab.TASK) {
                                // On the Task tab the "+" button adds a task, not a note
                                newTaskOpen = true
                            } else {
                                homeTab = HomeTab.NOTE
                                editingNoteId = null
                                editorOpen = true
                            }
                        }
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bgColor)
                        .padding(padding)
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        "Your thoughts, beautifully organized.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )

                    Spacer(Modifier.height(12.dp))

                    // A soft surface with a thin, low-contrast border. When the field is focused,
                    // the border and the icons turn to the accent colour.
                    val onSurface = MaterialTheme.colorScheme.onSurface
                    val searchColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = onSurface,
                        unfocusedTextColor = onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = onSurface.copy(alpha = 0.12f),
                        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                        unfocusedLeadingIconColor = onSurface.copy(alpha = 0.6f),
                        focusedTrailingIconColor = onSurface.copy(alpha = 0.7f),
                        unfocusedTrailingIconColor = onSurface.copy(alpha = 0.7f),
                        focusedPlaceholderColor = onSurface.copy(alpha = 0.5f),
                        unfocusedPlaceholderColor = onSurface.copy(alpha = 0.5f)
                    )

                    OutlinedTextField(
                        value = search,
                        // A new search ends the selection, so an action never reaches a note that is not on the screen
                        onValueChange = {
                            if (it != search) selectedIds = emptySet()
                            search = it
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                // Leaving the field with a search typed counts as a search
                                if (searchFocused && !state.isFocused) commitSearch(search)
                                searchFocused = state.isFocused
                            },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            commitSearch(search)
                            focusManager.clearFocus()
                        }),
                        placeholder = { Text("Search your notes...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        // The clear button is only there while there is text
                        trailingIcon = if (search.isNotEmpty()) {
                            {
                                IconButton(onClick = {
                                    search = ""
                                    selectedIds = emptySet()
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        } else {
                            null
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        shape = CircleShape,
                        colors = searchColors
                    )

                    // The panel opens downwards and fades in, and closes the same way
                    AnimatedVisibility(
                        visible = searchFocused && search.isEmpty() && recentSearches.isNotEmpty(),
                        enter = expandVertically(Motion.short()) + fadeIn(Motion.short()),
                        exit = shrinkVertically(Motion.short()) + fadeOut(Motion.short())
                    ) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            RecentSearchesPanel(
                                items = recentSearches,
                                onPick = { picked ->
                                    search = picked
                                    selectedIds = emptySet()
                                    focusManager.clearFocus()
                                },
                                onRemove = { item ->
                                    recentSearches = RecentSearches.remove(recentSearches, item)
                                    saveRecentSearches(context, recentSearches)
                                },
                                onClearAll = {
                                    recentSearches = emptyList()
                                    saveRecentSearches(context, emptyList())
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Your notes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            // The notes that are shown now: fewer while a search is active
                            notesCountLabel(shownNotes.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        // Sort order of the notes (pinned notes stay first)
                        var sortMenuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(
                                    SortIcon,
                                    contentDescription = "Sort notes",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuOpen,
                                onDismissRequest = { sortMenuOpen = false }
                            ) {
                                for (option in NotesSort.entries) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                option.label,
                                                fontWeight = if (option == sort) FontWeight.Bold
                                                else FontWeight.Normal
                                            )
                                        },
                                        trailingIcon = {
                                            if (option == sort) {
                                                Icon(Icons.Default.Check, contentDescription = "Selected")
                                            }
                                        },
                                        onClick = {
                                            sortMenuOpen = false
                                            if (option != sort) haptic(HapticKind.TICK)
                                            sort = option
                                            saveNotesSort(context, option)
                                        }
                                    )
                                }
                            }
                        }
                        // The button shows the view it switches to
                        IconButton(
                            onClick = {
                                haptic(HapticKind.TICK)
                                viewMode = viewMode.toggled()
                                saveNotesViewMode(context, viewMode)
                            }
                        ) {
                            Icon(
                                if (viewMode == NotesViewMode.LIST) GridViewIcon
                                else Icons.AutoMirrored.Filled.List,
                                contentDescription = if (viewMode == NotesViewMode.LIST)
                                    "Switch to grid view" else "Switch to list view",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // What fills the rest of the screen: the loading circle, the empty message or
                    // the notes. They fade into each other.
                    val pane = when {
                        !notesLoaded && search.isBlank() -> NotesPane.LOADING
                        shownNotes.isEmpty() -> NotesPane.EMPTY
                        else -> NotesPane.NOTES
                    }
                    Crossfade(
                        targetState = pane,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        animationSpec = Motion.short(),
                        label = "notesPane"
                    ) { shownPane ->
                    if (shownPane == NotesPane.LOADING) {
                        // The database has not answered yet (a split second at start-up)
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (shownPane == NotesPane.EMPTY) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                if (search.isBlank()) "Your space is empty"
                                else "No notes found",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (search.isBlank())
                                    "Tap + to capture your first thought."
                                else "Try searching with another word.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                            )
                        }
                    } else {
                        // One card, used by the list and by the grid. [itemModifier] carries
                        // Modifier.animateItem() from the lazy list / grid, so a note that is added,
                        // removed, pinned or re-sorted fades and slides instead of jumping.
                        val noteItem: @Composable (Note, Modifier) -> Unit = { note, itemModifier ->
                            // The color comes from the note's id, not from its place in the list,
                            // so it stays the same when notes are added, pinned or searched
                            val cardColor = if (dark) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                cardColors[cardColorIndex(note.id, cardColors.size)]
                            }

                            NoteCard(
                                note = note,
                                cardColor = cardColor,
                                dark = dark,
                                modifier = itemModifier,
                                onClick = {
                                    if (selectionMode) {
                                        toggleSelected(note.id)
                                    } else {
                                        // Opening a result shows the search was useful: remember it
                                        commitSearch(search)
                                        editingNoteId = note.id
                                        editorOpen = true
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
                                },
                                selected = note.id in selectedIds,
                                // No three-dot menu while selecting: the bar at the top has the actions
                                menuActions = if (selectionMode) {
                                    emptyList()
                                } else {
                                    val deleteAction = NoteMenuAction(
                                        label = "Delete",
                                        isDestructive = true,
                                        onClick = { deleteNotes(listOf(note)) }
                                    )
                                    if (note.archivedAt != null) {
                                        // An archived note (a search result) goes back to the list
                                        listOf(
                                            NoteMenuAction(
                                                label = "Unarchive",
                                                onClick = {
                                                    haptic(HapticKind.CONFIRM)
                                                    ApplicationScope.scope.launch {
                                                        repository.unarchiveMany(listOf(note.id))
                                                    }
                                                }
                                            ),
                                            deleteAction
                                        )
                                    } else {
                                        listOf(
                                            // Pinned notes are listed first. The list follows the
                                            // repository, so it reorders by itself.
                                            NoteMenuAction(
                                                label = if (note.pinnedAt != null) "Unpin" else "Pin",
                                                onClick = {
                                                    haptic(HapticKind.CONFIRM)
                                                    ApplicationScope.scope.launch {
                                                        repository.setPinned(note.id, note.pinnedAt == null)
                                                    }
                                                }
                                            ),
                                            NoteMenuAction(
                                                label = "Archive",
                                                onClick = { archiveNotes(listOf(note.id)) }
                                            ),
                                            deleteAction
                                        )
                                    }
                                }
                            )
                        }

                        // The list and the grid fade into each other when the view is switched
                        Crossfade(
                            targetState = viewMode,
                            modifier = Modifier.fillMaxSize(),
                            animationSpec = Motion.short(),
                            label = "viewMode"
                        ) { shownMode ->
                        if (shownMode == NotesViewMode.LIST) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(13.dp),
                                contentPadding = PaddingValues(bottom = 100.dp)
                            ) {
                                items(shownNotes, key = { it.id }) { note ->
                                    noteItem(note, Modifier.animateItem())
                                }
                            }
                        } else {
                            // Two columns. Every card is as tall as its text, so they fit together.
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Fixed(2),
                                state = gridState,
                                modifier = Modifier.fillMaxSize(),
                                verticalItemSpacing = 13.dp,
                                horizontalArrangement = Arrangement.spacedBy(13.dp),
                                contentPadding = PaddingValues(bottom = 100.dp)
                            ) {
                                items(shownNotes, key = { it.id }) { note ->
                                    noteItem(note, Modifier.animateItem())
                                }
                            }
                        }
                        } // end of Crossfade (list / grid)
                    }
                    } // end of Crossfade (loading / empty / notes)
                }
                } // end of the Note tab
                } // end of Crossfade (tabs)
            }
        }

    }
    } // end of AnimatedContent (list / editor)
}
