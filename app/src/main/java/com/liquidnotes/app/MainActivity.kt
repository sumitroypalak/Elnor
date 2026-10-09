@file:OptIn(ExperimentalMaterial3Api::class)

package com.drafto.app

import android.app.Activity
import android.content.Context
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

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

// ----------------------------- Notes storage -------------------------------

private fun loadNotes(context: Context): List<Note> {
    return try {
        val raw = context.getSharedPreferences(
            "drafto_notes", Context.MODE_PRIVATE
        ).getString("notes", "[]") ?: "[]"

        val array = JSONArray(raw)
        List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            Note(
                id = obj.getLong("id"),
                title = obj.getString("title"),
                body = obj.getString("body"),
                updatedAt = obj.getLong("updatedAt")
            )
        }.sortedByDescending { it.updatedAt }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveNotes(context: Context, notes: List<Note>) {
    val array = JSONArray()
    notes.forEach { note ->
        array.put(JSONObject().apply {
            put("id", note.id)
            put("title", note.title)
            put("body", note.body)
            put("updatedAt", note.updatedAt)
        })
    }
    context.getSharedPreferences(
        "drafto_notes", Context.MODE_PRIVATE
    ).edit().putString("notes", array.toString()).apply()
}

// ----------------------------- Settings storage ----------------------------

/** null = user never chose, so follow the phone's system theme. */
private fun loadDarkOverride(context: Context): Boolean? {
    val prefs = context.getSharedPreferences("drafto_settings", Context.MODE_PRIVATE)
    return if (prefs.contains("dark_mode")) prefs.getBoolean("dark_mode", false) else null
}

private fun saveDarkMode(context: Context, dark: Boolean) {
    context.getSharedPreferences("drafto_settings", Context.MODE_PRIVATE)
        .edit().putBoolean("dark_mode", dark).apply()
}

// ----------------------------- Activity & root -----------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DraftoRoot()
        }
    }
}

@Composable
fun DraftoRoot() {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    var darkOverride by remember { mutableStateOf(loadDarkOverride(context)) }
    val darkTheme = darkOverride ?: systemDark
    var showSettings by rememberSaveable { mutableStateOf(false) }

    DraftoTheme(darkTheme = darkTheme) {
        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(
                darkTheme = darkTheme,
                onDarkThemeChange = {
                    darkOverride = it
                    saveDarkMode(context, it)
                },
                onBack = { showSettings = false }
            )
        } else {
            DraftoApp(
                dark = darkTheme,
                onOpenSettings = { showSettings = true }
            )
        }
    }
}

@Composable
fun DraftoTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
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

    // Large, rounded shapes in the Material 3 Expressive style
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp)
    )

    // Keep status/navigation bar icons readable with the in-app theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        shapes = shapes,
        content = content
    )
}

// ----------------------------- Notes screen --------------------------------

@Composable
fun DraftoApp(dark: Boolean, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(loadNotes(context)) }
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
    val visibleNotes = notes.filter {
        it.title.contains(search, true) ||
            it.body.contains(search, true)
    }

    val listState = rememberLazyListState()
    // FAB shrinks to an icon while the list is scrolled
    val fabExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun updateNotes(updated: List<Note>) {
        notes = updated.sortedByDescending { it.updatedAt }
        saveNotes(context, notes)
    }

    if (editorOpen) {
        EditorScreen(
            note = editingNote,
            onSave = { newTitle, newBody ->
                val now = System.currentTimeMillis()
                val id = editingNote?.id ?: now
                val saved = Note(
                    id = id,
                    title = newTitle,
                    body = newBody,
                    updatedAt = now
                )
                val newList = if (editingNote == null) {
                    notes + saved
                } else {
                    notes.map { if (it.id == id) saved else it }
                }
                updateNotes(newList)
            },
            onDelete = {
                editingNote?.let { current ->
                    updateNotes(notes.filter { it.id != current.id })
                }
                editorOpen = false
            },
            onClose = { editorOpen = false }
        )
    } else {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = bgColor,
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text("Drafto", fontWeight = FontWeight.Bold)
                    },
                    actions = {
                        FilledTonalIconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                        Text("📝", fontSize = 54.sp)
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

                            Card(
                                onClick = {
                                    editingNoteId = note.id
                                    editorOpen = true
                                },
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
                    }
                }
            }
        }
    }
}

// ----------------------------- Editor screen -------------------------------

@Composable
fun EditorScreen(
    note: Note?,
    onSave: (title: String, body: String) -> Unit,
    onDelete: () -> Unit,
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

    // Open the keyboard as soon as the page opens
    LaunchedEffect(Unit) {
        runCatching { bodyFocus.requestFocus() }
    }

    fun saveAndClose() {
        val t = title.trim()
        val b = bodyValue.text.trim()
        val hasContent = t.isNotEmpty() || b.isNotEmpty()
        val changed = note == null || t != note.title || b != note.body
        if (hasContent && changed) onSave(t, b)
        onClose()
    }

    // Phone's Back button / gesture also saves
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
                    if (note != null) {
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
                onValueChange = { title = it },
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
                onValueChange = { bodyValue = it },
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
                        onDelete()
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
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val bgColor = MaterialTheme.colorScheme.background

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Dark mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (darkTheme) "Dark theme is on" else "Light theme is on",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = onDarkThemeChange
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
