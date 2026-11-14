@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

// The "Notes" tab of the home screen: the search field, the "Your notes" line, and the list / grid
// of notes (split out of HomeScreen.kt, nothing changed in how it works).

/** What the notes tab shows below the "Your notes" line. */
internal enum class NotesPane { LOADING, EMPTY, NOTES }

/**
 * The whole content of the Notes tab. It only draws. The home screen ([ElnorApp]) owns the search
 * text, the selection, the sort order and the view mode, and does the work in the callbacks.
 *
 * [noteItem] draws one card ([NoteListItem]); the list and the grid both use it.
 * [gridColumns] is how many columns the grid has (see gridColumnCount).
 */
@Composable
internal fun NotesTabContent(
    padding: PaddingValues,
    bgColor: Color,
    compactHeight: Boolean,
    search: String,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    onSearchSubmit: () -> Unit,
    showRecentSearches: Boolean,
    recentSearches: List<String>,
    onPickRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    shownNotes: List<Note>,
    notesLoaded: Boolean,
    sort: NotesSort,
    onSortChange: (NotesSort) -> Unit,
    viewMode: NotesViewMode,
    onToggleViewMode: () -> Unit,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    showRail: Boolean,
    gridColumns: Int,
    noteItem: @Composable (Note, Modifier) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .centeredMaxWidth(HomeMaxWidth)
            .padding(padding)
            .padding(horizontal = 20.dp)
    ) {
        // No tagline: the header is kept compact, so more notes fit on the screen. A phone
        // held sideways has little height, so it gets no gap under the bar at all.
        if (!compactHeight) {
            Spacer(Modifier.height(4.dp))
        }

        NotesSearchBar(
            search = search,
            onSearchChange = onSearchChange,
            onClear = onClearSearch,
            onFocusChange = onSearchFocusChange,
            onSubmit = onSearchSubmit,
            showRecent = showRecentSearches,
            recentSearches = recentSearches,
            onPickRecent = onPickRecent,
            onRemoveRecent = onRemoveRecent,
            onClearRecents = onClearRecents
        )

        Spacer(Modifier.height(12.dp))

        NotesHeaderRow(
            count = shownNotes.size,
            sort = sort,
            onSortChange = onSortChange,
            viewMode = viewMode,
            onToggleViewMode = onToggleViewMode
        )

        Spacer(Modifier.height(8.dp))

        NotesBody(
            search = search,
            shownNotes = shownNotes,
            notesLoaded = notesLoaded,
            viewMode = viewMode,
            listState = listState,
            gridState = gridState,
            showRail = showRail,
            gridColumns = gridColumns,
            noteItem = noteItem
        )
    }
}

/**
 * The search field and, under it, the panel of recent searches. [showRecent] is true while the
 * field is focused and empty and there is something to show.
 */
@Composable
private fun ColumnScope.NotesSearchBar(
    search: String,
    onSearchChange: (String) -> Unit,
    onClear: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    showRecent: Boolean,
    recentSearches: List<String>,
    onPickRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit
) {
    val focusManager = LocalFocusManager.current

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
        onValueChange = onSearchChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state -> onFocusChange(state.isFocused) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onSubmit()
            focusManager.clearFocus()
        }),
        placeholder = { Text("Search your notes...") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        // The clear button is only there while there is text
        trailingIcon = if (search.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
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
        visible = showRecent,
        enter = expandVertically(Motion.short()) + fadeIn(Motion.short()),
        exit = shrinkVertically(Motion.short()) + fadeOut(Motion.short())
    ) {
        Column {
            Spacer(Modifier.height(8.dp))
            RecentSearchesPanel(
                items = recentSearches,
                onPick = { picked ->
                    onPickRecent(picked)
                    focusManager.clearFocus()
                },
                onRemove = onRemoveRecent,
                onClearAll = onClearRecents
            )
        }
    }
}

/** "Your notes", the number of notes shown, the sort button and the list / grid button. */
@Composable
private fun NotesHeaderRow(
    count: Int,
    sort: NotesSort,
    onSortChange: (NotesSort) -> Unit,
    viewMode: NotesViewMode,
    onToggleViewMode: () -> Unit
) {
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
            notesCountLabel(count),
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
                            onSortChange(option)
                        }
                    )
                }
            }
        }
        // The button shows the view it switches to
        IconButton(onClick = onToggleViewMode) {
            Icon(
                if (viewMode == NotesViewMode.LIST) GridViewIcon
                else Icons.AutoMirrored.Filled.List,
                contentDescription = if (viewMode == NotesViewMode.LIST)
                    "Switch to grid view" else "Switch to list view",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * What fills the rest of the screen: the loading circle, the empty message or the notes. They fade
 * into each other.
 */
@Composable
private fun ColumnScope.NotesBody(
    search: String,
    shownNotes: List<Note>,
    notesLoaded: Boolean,
    viewMode: NotesViewMode,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    showRail: Boolean,
    gridColumns: Int,
    noteItem: @Composable (Note, Modifier) -> Unit
) {
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
                        contentPadding = PaddingValues(bottom = if (showRail) 24.dp else 100.dp)
                    ) {
                        items(shownNotes, key = { it.id }) { note ->
                            noteItem(note, Modifier.animateItem())
                        }
                    }
                } else {
                    // Two columns on a phone, more on wider windows. Every card is as tall as its text,
                    // so they fit together.
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(gridColumns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        verticalItemSpacing = 13.dp,
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                        contentPadding = PaddingValues(bottom = if (showRail) 24.dp else 100.dp)
                    ) {
                        items(shownNotes, key = { it.id }) { note ->
                            noteItem(note, Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }
}
