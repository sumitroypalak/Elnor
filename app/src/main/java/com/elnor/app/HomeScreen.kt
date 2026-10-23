@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ----------------------------- Notes screen --------------------------------

/** How long the user must stop typing in the search field before the search runs (milliseconds). */
private const val SEARCH_DEBOUNCE_MS = 200L

@Composable
fun ElnorApp(
    dark: Boolean,
    repository: NoteRepository,
    editorViewModel: NoteEditorViewModel,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit
) {
    // The list follows the repository, so it updates by itself after every auto-save
    val notes by repository.notes.collectAsState()
    var search by remember { mutableStateOf("") }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var homeTab by rememberSaveable { mutableStateOf(HomeTab.NOTE) }
    var editingNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editingNote = notes.firstOrNull { it.id == editingNoteId }
    // The note whose "Delete" was chosen in its three-dot menu (by id, never by list position)
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    val bgColor = MaterialTheme.colorScheme.background
    val cardColors = listOf(
        Color(0xFFE9E5FF),
        Color(0xFFDDF4F0),
        Color(0xFFFFEACD),
        Color(0xFFF9DFEA)
    )
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
    var viewMode by remember { mutableStateOf(loadNotesViewMode(context)) }

    if (editorOpen) {
        EditorScreen(
            note = editingNote,
            viewModel = editorViewModel,
            onClose = { editorOpen = false }
        )
    } else {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val drawerScope = rememberCoroutineScope()
        // Back from Task or Collection returns to Notes (registered first, so the menu below wins)
        BackHandler(enabled = homeTab != HomeTab.NOTE) { homeTab = HomeTab.NOTE }
        // The Back button closes the side menu first
        BackHandler(enabled = drawerState.isOpen) {
            drawerScope.launch { drawerState.close() }
        }

        EndDrawer(
            drawerState = drawerState,
            onOpenSettings = onOpenSettings,
            onOpenTrash = onOpenTrash
        ) {
            Scaffold(
                containerColor = bgColor,
                topBar = {
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
                },
                // The bottom bar floats over the list, in the place of the old "New note" button
                floatingActionButton = {
                    ElnorBottomBar(
                        selected = homeTab,
                        dark = dark,
                        onSelect = { homeTab = it },
                        onNewNote = {
                            homeTab = HomeTab.NOTE
                            editingNoteId = null
                            editorOpen = true
                        }
                    )
                },
                floatingActionButtonPosition = FabPosition.Center
            ) { padding ->
                // Task and Collection are placeholders for now. Only Notes has content.
                if (homeTab != HomeTab.NOTE) {
                    if (homeTab == HomeTab.TASK) {
                        HomeTabPlaceholder(
                            emoji = "✅",
                            title = "Tasks are coming soon",
                            subtitle = "Your to-dos will live here.",
                            padding = padding
                        )
                    } else {
                        HomeTabPlaceholder(
                            emoji = "🗂️",
                            title = "Collections are coming soon",
                            subtitle = "Group your notes and tasks here.",
                            padding = padding
                        )
                    }
                    return@Scaffold
                }
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
                    // the border and the icons turn to the purple accent.
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
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search your notes...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        // The clear button is only there while there is text
                        trailingIcon = if (search.isNotEmpty()) {
                            {
                                IconButton(onClick = { search = "" }) {
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
                            "${notes.size} notes",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        // The button shows the view it switches to
                        IconButton(
                            onClick = {
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

                    if (visibleNotes.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("📝", fontSize = 54.sp, lineHeight = 76.sp)
                            Spacer(Modifier.height(14.dp))
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
                        // One card, used by the list and by the grid
                        val noteItem: @Composable (Note) -> Unit = { note ->
                            val originalIndex = notes.indexOfFirst { it.id == note.id }
                            val cardColor = if (dark) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                cardColors[(originalIndex.coerceAtLeast(0)) % cardColors.size]
                            }

                            NoteCard(
                                note = note,
                                cardColor = cardColor,
                                dark = dark,
                                onClick = {
                                    editingNoteId = note.id
                                    editorOpen = true
                                },
                                menuActions = listOf(
                                    // Pinned notes are listed first. The list follows the repository,
                                    // so it reorders by itself.
                                    NoteMenuAction(
                                        label = if (note.pinnedAt != null) "Unpin" else "Pin",
                                        onClick = {
                                            ApplicationScope.scope.launch {
                                                repository.setPinned(note.id, note.pinnedAt == null)
                                            }
                                        }
                                    ),
                                    NoteMenuAction(
                                        label = "Delete",
                                        isDestructive = true,
                                        onClick = { pendingDeleteId = note.id }
                                    )
                                )
                            )
                        }

                        if (viewMode == NotesViewMode.LIST) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(13.dp),
                                contentPadding = PaddingValues(bottom = 100.dp)
                            ) {
                                items(visibleNotes, key = { it.id }) { note -> noteItem(note) }
                            }
                        } else {
                            // Two columns. Every card is as tall as its text, so they fit together.
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Fixed(2),
                                state = gridState,
                                modifier = Modifier.weight(1f),
                                verticalItemSpacing = 13.dp,
                                horizontalArrangement = Arrangement.spacedBy(13.dp),
                                contentPadding = PaddingValues(bottom = 100.dp)
                            ) {
                                items(visibleNotes, key = { it.id }) { note -> noteItem(note) }
                            }
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
                    // The same deletion as in the editor: the note goes to the Trash. The list
                    // follows the repository, so it updates by itself.
                    ApplicationScope.scope.launch { repository.moveToTrash(deleteId) }
                },
                onDismiss = { pendingDeleteId = null }
            )
        }
    }
}
