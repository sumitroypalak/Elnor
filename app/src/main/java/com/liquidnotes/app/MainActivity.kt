@file:OptIn(ExperimentalMaterial3Api::class)

package com.drafto.app

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// ✏️ DEVELOPER INFO — change these 4 lines to your own details
// ---------------------------------------------------------------------------
private const val DEVELOPER_NAME = "Sumit Roy"
private const val DEVELOPER_ROLE = "Email: sumitroypalak@gmail.com"
private const val DEVELOPER_GITHUB = "https://github.com/sumitroypalak"
private const val APP_VERSION = "1.0"

data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val updatedAt: Long
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

private const val SETTINGS_FILE = "drafto_settings"
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

class MainActivity : ComponentActivity() {

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

        // Auto-save when the app goes to the background (Lifecycle ON_STOP)
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) editorViewModel.onStop()
            }
        )

        val repository = RoomNoteRepository.get(applicationContext)
        setContent {
            DraftoRoot(
                repository = repository,
                editorViewModel = editorViewModel,
                initialThemeMode = initialThemeMode
            )
        }
    }
}

/** The screens the user can open from the home screen. */
private enum class AppScreen { HOME, SETTINGS, TRASH }

@Composable
fun DraftoRoot(
    repository: NoteRepository,
    editorViewModel: NoteEditorViewModel,
    initialThemeMode: ThemeMode
) {
    val context = LocalContext.current
    // Changes by itself when the phone switches between light and dark
    val systemDark = isSystemInDarkTheme()
    var themeMode by remember { mutableStateOf(initialThemeMode) }
    val darkTheme = themeMode.isDark(systemDark)
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }

    DraftoTheme(darkTheme = darkTheme) {
        when (screen) {
            AppScreen.SETTINGS -> {
                BackHandler { screen = AppScreen.HOME }
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = {
                        themeMode = it
                        saveThemeMode(context, it)
                    },
                    onBack = { screen = AppScreen.HOME }
                )
            }

            AppScreen.TRASH -> {
                BackHandler { screen = AppScreen.HOME }
                TrashPlaceholderScreen(onBack = { screen = AppScreen.HOME })
            }

            AppScreen.HOME -> {
                DraftoApp(
                    dark = darkTheme,
                    repository = repository,
                    editorViewModel = editorViewModel,
                    onOpenSettings = { screen = AppScreen.SETTINGS },
                    onOpenTrash = { screen = AppScreen.TRASH }
                )
            }
        }
    }
}

/** The only place where the app's light and dark colours are defined. */
fun draftoColorScheme(dark: Boolean): ColorScheme = if (dark) {
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
 * Used at launch (MainActivity) and every time the theme changes (DraftoTheme).
 */
private fun applyWindowTheme(window: Window, view: View, dark: Boolean) {
    window.setBackgroundDrawable(ColorDrawable(draftoColorScheme(dark).background.toArgb()))
    val controller = WindowCompat.getInsetsController(window, view)
    controller.isAppearanceLightStatusBars = !dark
    controller.isAppearanceLightNavigationBars = !dark
}

@Composable
fun DraftoTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = draftoColorScheme(darkTheme)

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
        typography = DraftoTypography,
        content = content
    )
}

// ----------------------------- Notes screen --------------------------------

@Composable
fun DraftoApp(
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
    var editingNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editingNote = notes.firstOrNull { it.id == editingNoteId }

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
    // FAB shrinks to an icon while the list is scrolled
    val fabExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (editorOpen) {
        EditorScreen(
            note = editingNote,
            viewModel = editorViewModel,
            onClose = { editorOpen = false }
        )
    } else {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val drawerScope = rememberCoroutineScope()
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
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = bgColor,
                topBar = {
                    LargeTopAppBar(
                        title = {
                            Text("Drafto", fontWeight = FontWeight.Bold)
                        },
                        actions = {
                            // Hamburger button: opens the side menu (Settings, Trash)
                            FilledTonalIconButton(
                                onClick = { drawerScope.launch { drawerState.open() } },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Open menu")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = bgColor,
                            scrolledContainerColor = bgColor
                        ),
                        scrollBehavior = scrollBehavior
                    )
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = {
                            editingNoteId = null
                            editorOpen = true
                        },
                        expanded = fabExpanded,
                        icon = { Icon(Icons.Default.Add, contentDescription = "New note") },
                        text = { Text("New note") },
                        shape = MaterialTheme.shapes.extraLarge
                    )
                }
            ) { padding ->
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

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search your notes...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        shape = CircleShape
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
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(13.dp),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(visibleNotes, key = { it.id }) { note ->
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
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
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
                            "Drafto",
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
 * The page the "Trash" menu item opens. Notes are still deleted for good (there is no Trash
 * system yet), so for now this is only the empty page. It is its own composable, so the real
 * Trash list can replace it later without touching the menu or the navigation.
 */
@Composable
private fun TrashPlaceholderScreen(onBack: () -> Unit) {
    val bgColor = MaterialTheme.colorScheme.background

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        }
    ) { padding ->
        Column(
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
        }
    }
}

// ----------------------------- Note card -----------------------------------

/** One note in the list. Also used by FontAuditPreview. */
@Composable
fun NoteCard(
    note: Note,
    cardColor: Color,
    dark: Boolean,
    onClick: () -> Unit
) {
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
        Column(Modifier.padding(20.dp)) {
            Text(
                getDisplayTitle(note),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (dark) MaterialTheme.colorScheme.onSurface
                else Color(0xFF24243A),
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
                SimpleDateFormat(
                    "dd MMM yyyy · hh:mm a",
                    Locale.getDefault()
                ).format(Date(note.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                else Color(0xFF626278)
            )
        }
    }
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
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete note?", fontWeight = FontWeight.Bold) },
            text = { Text("This note will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteNote()
                        onClose()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ----------------------------- Settings screen -----------------------------

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
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
                        if (mode != themeMode) onThemeModeChange(mode) // applies and saves, see DraftoRoot
                    },
                    onDismiss = { showThemeDialog = false }
                )
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
                        "Drafto is a simple, beautiful note-taking app built with Kotlin and Jetpack Compose.",
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
                "Drafto v$APP_VERSION",
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
