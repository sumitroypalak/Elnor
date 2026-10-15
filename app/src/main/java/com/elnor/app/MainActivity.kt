@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// ✏️ DEVELOPER INFO — change these 4 lines to your own details
// ---------------------------------------------------------------------------
private const val DEVELOPER_NAME = "Sumit Roy"
private const val DEVELOPER_ROLE = "Email: sumitroypalak@gmail.com"
private const val DEVELOPER_GITHUB = "https://github.com/sumitroypalak"
private const val APP_VERSION = "1.0"

/**
 * [deletedAt] is null for a normal note. A note in the Trash has the time it was deleted.
 * It comes last and has a default, so Note(id, title, body, updatedAt) still works everywhere.
 */
data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

private const val UNTITLED_NOTE = "Untitled note"

/**
 * Name shown in the notes list. Calculated every time the list is drawn
 * and never saved, so it follows the text when the note is edited.
 *
 * 1. User's Title, if it has any text
 * 2. otherwise the first line of the body that has text
 * 3. otherwise "Untitled note"
 */
fun getDisplayTitle(note: Note): String {
    val title = note.title.trim()
    if (title.isNotEmpty()) return title

    val firstLine = note.body
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }

    return firstLine ?: UNTITLED_NOTE
}

/**
 * Preview text shown under the name. When the first line is already used
 * as the name, it is skipped here so it does not appear twice.
 */
fun getPreviewBody(note: Note): String {
    if (note.title.isNotBlank()) return note.body.trim()

    val lines = note.body.lines()
    val firstIndex = lines.indexOfFirst { it.isNotBlank() }
    if (firstIndex == -1) return ""

    return lines.drop(firstIndex + 1).joinToString("\n").trim()
}

// ----------------------------- Settings storage ----------------------------

private const val SETTINGS_FILE = "elnor_settings"
private const val KEY_THEME_MODE = "theme_mode"

/** Key of the old two-state switch (true = dark). Only read, so an earlier choice is kept. */
private const val KEY_LEGACY_DARK_MODE = "dark_mode"

/** Reads the saved theme. Nothing saved means SYSTEM. */
private fun loadThemeMode(context: Context): ThemeMode {
    val prefs = context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
    val legacyDark =
        if (prefs.contains(KEY_LEGACY_DARK_MODE)) prefs.getBoolean(KEY_LEGACY_DARK_MODE, false) else null
    return ThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, null), legacyDark)
}

private fun saveThemeMode(context: Context, mode: ThemeMode) {
    context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_THEME_MODE, mode.storageValue)
        .remove(KEY_LEGACY_DARK_MODE) // the new value replaces the old switch
        .apply()
}

// ----------------------------- Activity & root -----------------------------

// FragmentActivity (a ComponentActivity too) is needed by the system unlock dialog of the app lock
class MainActivity : FragmentActivity() {

    // One editor ViewModel for the whole activity. It survives screen rotation.
    private val editorViewModel: NoteEditorViewModel by viewModels {
        NoteEditorViewModel.factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Read the saved theme before anything is drawn, and paint the window (colour and
        // status/navigation bar icons) at once. So the first frame never shows the wrong theme.
        val initialThemeMode = loadThemeMode(applicationContext)
        val systemDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        applyWindowTheme(window, window.decorView, initialThemeMode.isDark(systemDark))

        // Reads the app lock settings. A cold start with the lock on starts locked.
        AppLock.init(applicationContext)

        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    // Auto-save when the app goes to the background, and note when it left
                    Lifecycle.Event.ON_STOP -> {
                        editorViewModel.onStop()
                        AppLock.onAppStopped()
                    }
                    // Back on screen: lock if it was away longer than "Automatically lock"
                    Lifecycle.Event.ON_START -> AppLock.onAppStarted()
                    // Ask the phone to unlock as soon as the locked app is visible
                    Lifecycle.Event.ON_RESUME -> showUnlockPrompt(automatic = true)
                    else -> Unit
                }
            }
        )

        val repository = RoomNoteRepository.get(applicationContext)
        // Notes that have been in the Trash for 30 days are deleted for good when the app starts
        repository.purgeExpiredTrashInBackground()
        setContent {
            ElnorRoot(
                repository = repository,
                editorViewModel = editorViewModel,
                initialThemeMode = initialThemeMode,
                onUnlockClick = { showUnlockPrompt(automatic = false) }
            )
        }
    }

    /**
     * Opens the phone's unlock dialog when the app is locked. [automatic] is true when the app
     * itself opens it (not the Unlock button): after the user cancelled it, it does not pop up
     * again by itself until the app has been away once more.
     */
    private fun showUnlockPrompt(automatic: Boolean) {
        if (!AppLock.locked.value || AppLock.promptShowing) return
        if (automatic && !AppLock.autoPromptAllowed) return

        if (!AppLockAuth.isAvailable(this)) {
            // The phone's screen lock was removed after the app lock was turned on. Nothing is
            // left to ask for, so the lock is turned off instead of locking the user out for good.
            AppLock.setEnabled(applicationContext, false)
            return
        }

        AppLock.promptShowing = true
        AppLockAuth.authenticate(
            activity = this,
            title = "Unlock Elnor",
            onSuccess = {
                AppLock.promptShowing = false
                AppLock.unlock()
            },
            onError = { userCancelled ->
                AppLock.promptShowing = false
                if (userCancelled) AppLock.autoPromptAllowed = false
            }
        )
    }
}

/** The screens the user can open from the home screen. */
private enum class AppScreen { HOME, SETTINGS, TRASH, APP_LOCK }

@Composable
fun ElnorRoot(
    repository: NoteRepository,
    editorViewModel: NoteEditorViewModel,
    initialThemeMode: ThemeMode,
    onUnlockClick: () -> Unit = {}
) {
    val context = LocalContext.current
    // Changes by itself when the phone switches between light and dark
    val systemDark = isSystemInDarkTheme()
    var themeMode by remember { mutableStateOf(initialThemeMode) }
    val darkTheme = themeMode.isDark(systemDark)
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    val locked by AppLock.locked.collectAsState()
    val appLockEnabled by AppLock.enabled.collectAsState()
    val focusManager = LocalFocusManager.current

    // The keyboard of an open note must not stay up over the lock screen
    LaunchedEffect(locked) {
        if (locked) focusManager.clearFocus(force = true)
    }

    ElnorTheme(darkTheme = darkTheme) {
        Box(Modifier.fillMaxSize()) {
            // The screens stay composed while locked (an open note is still there after unlocking),
            // but they are hidden from touch and from screen readers by the lock screen above.
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (locked) Modifier.clearAndSetSemantics { } else Modifier)
            ) {
                when (screen) {
                    AppScreen.SETTINGS -> {
                        BackHandler { screen = AppScreen.HOME }
                        SettingsScreen(
                            themeMode = themeMode,
                            onThemeModeChange = {
                                themeMode = it
                                saveThemeMode(context, it)
                            },
                            appLockEnabled = appLockEnabled,
                            onOpenAppLock = { screen = AppScreen.APP_LOCK },
                            onBack = { screen = AppScreen.HOME }
                        )
                    }

                    AppScreen.APP_LOCK -> {
                        BackHandler { screen = AppScreen.SETTINGS }
                        AppLockScreen(onBack = { screen = AppScreen.SETTINGS })
                    }

                    AppScreen.TRASH -> {
                        BackHandler { screen = AppScreen.HOME }
                        TrashScreen(
                            repository = repository,
                            onBack = { screen = AppScreen.HOME }
                        )
                    }

                    AppScreen.HOME -> {
                        ElnorApp(
                            dark = darkTheme,
                            repository = repository,
                            editorViewModel = editorViewModel,
                            onOpenSettings = { screen = AppScreen.SETTINGS },
                            onOpenTrash = { screen = AppScreen.TRASH }
                        )
                    }
                }
            }

            if (locked) LockedScreen(onUnlock = onUnlockClick)
        }
    }
}

/** The only place where the app's light and dark colours are defined. */
fun elnorColorScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = Color(0xFFB9B5FF),
        primaryContainer = Color(0xFF433AA8),
        onPrimaryContainer = Color(0xFFE4E0FF),
        background = Color(0xFF10111B),
        surface = Color(0xFF1C1D2B)
    )
} else {
    lightColorScheme(
        primary = Color(0xFF6257D9),
        primaryContainer = Color(0xFFE4E0FF),
        onPrimaryContainer = Color(0xFF1B1464),
        background = Color(0xFFF5F5FC),
        surface = Color(0xFFFFFFFF)
    )
}

/**
 * Makes the window itself match the theme: the colour behind the app (seen behind the keyboard
 * and while the screen changes) and the icons of the status and navigation bars.
 * Used at launch (MainActivity) and every time the theme changes (ElnorTheme).
 */
private fun applyWindowTheme(window: Window, view: View, dark: Boolean) {
    window.setBackgroundDrawable(ColorDrawable(elnorColorScheme(dark).background.toArgb()))
    val controller = WindowCompat.getInsetsController(window, view)
    controller.isAppearanceLightStatusBars = !dark
    controller.isAppearanceLightNavigationBars = !dark
}

@Composable
fun ElnorTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = elnorColorScheme(darkTheme)

    // Large, rounded shapes in the Material 3 Expressive style
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp)
    )

    // Keep the window and the status/navigation bar icons in step with the in-app theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) applyWindowTheme(window, view, darkTheme)
        }
    }

    MaterialTheme(
        colorScheme = colors,
        shapes = shapes,
        typography = ElnorTypography,
        content = content
    )
}

// ----------------------------- Notes screen --------------------------------

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
    LaunchedEffect(search) {
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

// ----------------------------- Bottom bar ----------------------------------

/** The three tabs of the home screen, in the order they appear in the bottom bar. */
private enum class HomeTab(val label: String, val icon: ImageVector) {
    NOTE("Note", Icons.Default.Edit),
    TASK("Task", Icons.Default.CheckCircle),
    COLLECTION("Collection", Icons.AutoMirrored.Filled.List)
}

/**
 * The motion of the whole bottom bar: quick, and it settles without a visible bounce.
 * Every tab has one animated number from 0 (not selected) to 1 (selected). The highlight, the
 * width of the tab, and the icon and label fades are all calculated from those numbers, so they
 * always move together.
 */
private val BottomBarSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 800f)

/**
 * A dark pill with the three tabs and, next to it, a separate round "New note" button.
 * One highlight slides to the selected tab. Only the selected tab shows its icon, so all three
 * names fit on a small phone.
 * Both parts are dark in the light and in the dark theme.
 */
@Composable
private fun ElnorBottomBar(
    selected: HomeTab,
    dark: Boolean,
    onSelect: (HomeTab) -> Unit,
    onNewNote: () -> Unit
) {
    val barColor = if (dark) MaterialTheme.colorScheme.surface else Color(0xFF24243A)
    val highlight =
        if (dark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    val onBar = Color(0xFFE4E0FF)

    // 1 for the selected tab, 0 for the others. They are read only while measuring and drawing,
    // so the bar does not recompose on every frame of the animation.
    val progress = HomeTab.entries.map { tab ->
        animateFloatAsState(
            targetValue = if (tab == selected) 1f else 0f,
            animationSpec = BottomBarSpring,
            label = "tabProgress"
        )
    }

    // The "New note" button gets a little smaller while it is pressed and springs back
    val newNoteSource = remember { MutableInteractionSource() }
    val newNotePressed by newNoteSource.collectIsPressedAsState()
    val newNoteScale = animateFloatAsState(
        targetValue = if (newNotePressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "newNoteScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = CircleShape,
            color = barColor,
            shadowElevation = 6.dp
        ) {
            SlidingTabRow(
                progress = progress,
                highlight = highlight,
                modifier = Modifier
                    .padding(6.dp)
                    .selectableGroup()
            ) {
                for ((index, tab) in HomeTab.entries.withIndex()) {
                    BottomBarTab(
                        tab = tab,
                        selected = tab == selected,
                        progress = progress[index],
                        onBar = onBar,
                        onClick = { onSelect(tab) }
                    )
                }
            }
        }

        Surface(
            onClick = onNewNote,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = newNoteScale.value
                    scaleY = newNoteScale.value
                },
            shape = CircleShape,
            color = barColor,
            shadowElevation = 6.dp,
            interactionSource = newNoteSource
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = "New note", tint = onBar)
            }
        }
    }
}

/**
 * Lays the tabs out in a row with equal gaps, and draws one highlight behind them.
 * The highlight is the average of the tab boxes, weighted by [progress]. When the selection
 * changes, the weight moves from the old tab to the new one, so the highlight slides and
 * changes its width at the same time as the tabs move. It is placed in the same layout pass
 * as the tabs, so it never lags behind them.
 *
 * The first child is the highlight. The tabs come after it, so they are drawn on top.
 */
@Composable
private fun SlidingTabRow(
    progress: List<State<Float>>,
    highlight: Color,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(highlight)
            )
            tabs()
        }
    ) { measurables, constraints ->
        val tabPlaceables = measurables
            .drop(1)
            .map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val height = tabPlaceables.maxOf { it.height }
        val tabsWidth = tabPlaceables.sumOf { it.width }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else tabsWidth
        val gap = if (tabPlaceables.size > 1) {
            ((width - tabsWidth) / (tabPlaceables.size - 1)).coerceAtLeast(0)
        } else {
            0
        }

        val xs = IntArray(tabPlaceables.size)
        var nextX = 0
        tabPlaceables.forEachIndexed { i, placeable ->
            xs[i] = nextX
            nextX += placeable.width + gap
        }

        var weightSum = 0f
        var highlightX = 0f
        var highlightWidth = 0f
        tabPlaceables.forEachIndexed { i, placeable ->
            val weight = progress[i].value.coerceIn(0f, 1f)
            weightSum += weight
            highlightX += weight * xs[i]
            highlightWidth += weight * placeable.width
        }
        if (weightSum > 0.001f) {
            highlightX /= weightSum
            highlightWidth /= weightSum
        }
        val highlightPlaceable =
            measurables[0].measure(Constraints.fixed(highlightWidth.roundToInt(), height))

        layout(width, height) {
            highlightPlaceable.place(highlightX.roundToInt(), 0)
            tabPlaceables.forEachIndexed { i, placeable -> placeable.place(xs[i], 0) }
        }
    }
}

/**
 * One tab. While it is being selected, the space for its icon opens up, the icon slides in from
 * the left and fades in, and the label gets brighter. All of it follows [progress].
 */
@Composable
private fun BottomBarTab(
    tab: HomeTab,
    selected: Boolean,
    progress: State<Float>,
    onBar: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(CircleShape)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The width of this box grows from 0 to the icon's width. The icon itself stays on the
        // right edge of the box, so it travels in from the left while the box opens.
        Box(
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val width = (placeable.width * progress.value.coerceIn(0f, 1f)).roundToInt()
                layout(width, placeable.height) {
                    placeable.place(width - placeable.width, 0)
                }
            }
        ) {
            Icon(
                tab.icon,
                contentDescription = null,
                tint = onBar,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(20.dp)
                    .graphicsLayer {
                        val p = progress.value.coerceIn(0f, 1f)
                        alpha = p
                        scaleX = 0.6f + 0.4f * p
                        scaleY = 0.6f + 0.4f * p
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
            )
        }
        Text(
            tab.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = onBar,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                alpha = 0.72f + 0.28f * progress.value.coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
        )
    }
}

/** Temporary screen for a tab that has no feature yet. Same look as the empty states. */
@Composable
private fun HomeTabPlaceholder(
    emoji: String,
    title: String,
    subtitle: String,
    padding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp)
            .padding(bottom = 80.dp), // centred above the floating bar
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 54.sp, lineHeight = 76.sp)
        Spacer(Modifier.height(14.dp))
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

// ----------------------------- Side menu -----------------------------------

/**
 * The side menu with Settings and Trash. The menu button is at the top right, so the menu also
 * comes in from the right (the end of the screen). ModalNavigationDrawer always opens from the
 * start side, so its layout direction is turned around, and turned back for everything inside.
 * Tapping outside the menu, swiping it away or pressing Back closes it.
 */
@Composable
private fun EndDrawer(
    drawerState: DrawerState,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit,
    content: @Composable () -> Unit
) {
    val direction = LocalLayoutDirection.current
    val flipped = if (direction == LayoutDirection.Ltr) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides flipped) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    ModalDrawerSheet(
                        // rounded on the side that faces the screen, like the rest of the app
                        drawerShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            "Elnor",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp)
                        )
                        NavigationDrawerItem(
                            label = { Text("Settings") },
                            selected = false,
                            onClick = onOpenSettings,
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                        NavigationDrawerItem(
                            label = { Text("Trash") },
                            selected = false,
                            onClick = onOpenTrash,
                            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            },
            content = {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    content()
                }
            }
        )
    }
}

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
private fun TrashScreen(
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

    // Opening the Trash also removes the notes that have been here for 30 days. The list
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
                        "Notes are deleted for good $TRASH_RETENTION_DAYS days after they were " +
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
private fun formatNoteDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy \u00B7 hh:mm a", Locale.getDefault()).format(Date(millis))

// ----------------------------- Note card -----------------------------------

/**
 * One entry of the three-dot menu of a note. To add Pin, Archive and so on later, pass one more
 * entry in the list of [NoteCard]. Nothing else has to change.
 */
data class NoteMenuAction(
    val label: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * One note in the list. Also used by FontAuditPreview.
 * [menuActions] are the entries of the three-dot menu in the top right corner of the card.
 * With no entries (the default) the menu button is not shown.
 */
@Composable
fun NoteCard(
    note: Note,
    cardColor: Color,
    dark: Boolean,
    onClick: () -> Unit,
    menuActions: List<NoteMenuAction> = emptyList()
) {
    val hasMenu = menuActions.isNotEmpty()

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        // fillMaxWidth: the Box is always as wide as the card, not only as wide as the text.
        // Then the menu button below (TopEnd) stays at the same place for every title length.
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    getDisplayTitle(note),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (dark) MaterialTheme.colorScheme.onSurface
                    else Color(0xFF24243A),
                    // keeps the title out from under the menu button
                    modifier = if (hasMenu) Modifier.padding(end = 36.dp) else Modifier,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val preview = getPreviewBody(note)
                if (preview.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        else Color(0xFF454559),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    formatNoteDate(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    else Color(0xFF626278)
                )
            }

            if (hasMenu) {
                NoteOverflowMenu(
                    actions = menuActions,
                    dark = dark,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 4.dp)
                )
            }
        }
    }
}

/** The three-dot button of a note and the popup menu it opens. Every entry of [actions] is one item. */
@Composable
private fun NoteOverflowMenu(
    actions: List<NoteMenuAction>,
    dark: Boolean,
    modifier: Modifier = Modifier
) {
    // Every note card has its own state, so each menu opens and closes on its own
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                else Color(0xFF454559)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for (action in actions) {
                DropdownMenuItem(
                    text = {
                        Text(
                            action.label,
                            color = if (action.isDestructive) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        expanded = false
                        action.onClick()
                    }
                )
            }
        }
    }
}

/**
 * "Delete note?" The note goes to the Trash, where it can be restored, so this is the same
 * question in the editor and in the notes list.
 */
@Composable
private fun DeleteNoteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete note?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "This note will be moved to Trash. You can restore it within " +
                    "$TRASH_RETENTION_DAYS days. After that it is deleted for good."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ----------------------------- Editor screen -------------------------------

@Composable
fun EditorScreen(
    note: Note?,
    viewModel: NoteEditorViewModel,
    onClose: () -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.background
    val noteKey = note?.id ?: -1L

    var title by rememberSaveable(noteKey) { mutableStateOf(note?.title ?: "") }
    var bodyValue by rememberSaveable(noteKey, stateSaver = TextFieldValue.Saver) {
        val text = note?.body ?: ""
        // cursor starts at the end of the existing text
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val bodyFocus = remember { FocusRequester() }

    // The delete button is only shown when the note exists in storage
    val canDelete by viewModel.hasSavedNote.collectAsState()

    // Start the editing session (nothing happens if it is already open, e.g. after rotation),
    // then give the ViewModel the text that is on screen.
    LaunchedEffect(noteKey) {
        viewModel.open(note)
        viewModel.onTitleChange(title)
        viewModel.onBodyChange(bodyValue.text)
    }

    // Open the keyboard as soon as the page opens
    LaunchedEffect(Unit) {
        runCatching { bodyFocus.requestFocus() }
    }

    // Back arrow and the phone's Back button / gesture: the ViewModel saves, we close the page.
    // (There is no Save button. The app going to the background is saved by MainActivity.)
    fun saveAndClose() {
        viewModel.onBack()
        onClose()
    }

    BackHandler { saveAndClose() }

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent
    )

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { saveAndClose() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Save and go back"
                        )
                    }
                },
                actions = {
                    if (canDelete) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete note",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
                .consumeWindowInsets(padding)
                .imePadding() // keeps the text above the keyboard
                .padding(horizontal = 8.dp)
        ) {
            // Title (optional)
            TextField(
                value = title,
                onValueChange = {
                    title = it
                    viewModel.onTitleChange(it) // only remembered, not saved
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                placeholder = {
                    Text(
                        "Title",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { bodyFocus.requestFocus() }
                ),
                colors = fieldColors
            )

            // Body: fills the rest of the screen
            TextField(
                value = bodyValue,
                onValueChange = {
                    bodyValue = it
                    viewModel.onBodyChange(it.text) // only remembered, not saved
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(bodyFocus),
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = {
                    Text(
                        "Start writing...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                    )
                },
                colors = fieldColors
            )
        }
    }

    if (confirmDelete) {
        DeleteNoteDialog(
            onConfirm = {
                confirmDelete = false
                viewModel.deleteNote()
                onClose()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

// ----------------------------- Settings screen -----------------------------

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    appLockEnabled: Boolean,
    onOpenAppLock: () -> Unit,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val bgColor = MaterialTheme.colorScheme.background
    // rememberSaveable: the dialog stays open if the screen is recreated (rotation, phone theme change)
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // ---- Appearance ----
            SectionLabel("Appearance")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                // One row: the title, and the current choice below it. Tap to change.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { showThemeDialog = true }
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        "Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        themeMode.label(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }

            if (showThemeDialog) {
                ThemeDialog(
                    current = themeMode,
                    onSelect = { mode ->
                        showThemeDialog = false
                        if (mode != themeMode) onThemeModeChange(mode) // applies and saves, see ElnorRoot
                    },
                    onDismiss = { showThemeDialog = false }
                )
            }

            // ---- Privacy ----
            SectionLabel("Privacy")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onOpenAppLock() }
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        "App lock",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (appLockEnabled) "On" else "Off",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }

            // ---- About developer ----
            SectionLabel("About developer")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            DEVELOPER_NAME.first().uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        DEVELOPER_NAME,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        DEVELOPER_ROLE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Elnor is a simple, beautiful note-taking app built with Kotlin and Jetpack Compose.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(Modifier.height(18.dp))
                    FilledTonalButton(
                        onClick = { uriHandler.openUri(DEVELOPER_GITHUB) }
                    ) {
                        Text("View on GitHub")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Elnor v$APP_VERSION",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The text of each theme, in the Settings row and in the dialog. */
private fun ThemeMode.label(): String = when (this) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.SYSTEM -> "System default"
}

private val themeChoices = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)

/**
 * "Choose theme": three radio buttons, the current one is checked.
 * Tapping a row selects it at once (the caller applies it and closes the dialog).
 * Tapping outside, the Back button or Cancel closes it without a change.
 */
@Composable
private fun ThemeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose theme", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                for (mode in themeChoices) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = mode == current,
                                onClick = { onSelect(mode) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // onClick = null: the whole row is the button (better for screen readers)
                        RadioButton(selected = mode == current, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            mode.label(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
    )
}
