@file:OptIn(ExperimentalMaterial3Api::class)

package com.drafto.app

import android.content.Context
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val updatedAt: Long
)

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DraftoTheme {
                DraftoApp()
            }
        }
    }
}

@Composable
fun DraftoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
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

    MaterialTheme(
        colorScheme = colors,
        shapes = shapes,
        content = content
    )
}

@Composable
fun DraftoApp() {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(loadNotes(context)) }
    var search by remember { mutableStateOf("") }
    var editorOpen by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }

    val dark = isSystemInDarkTheme()
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = bgColor,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text("Drafto", fontWeight = FontWeight.Bold)
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(18.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
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
                    editingNote = null
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
                                editingNote = note
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
                                    note.title.ifBlank { "Untitled note" },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (dark) MaterialTheme.colorScheme.onSurface
                                    else Color(0xFF24243A),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (note.body.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        note.body,
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

    if (editorOpen) {
        var title by remember(editingNote?.id) {
            mutableStateOf(editingNote?.title ?: "")
        }
        var body by remember(editingNote?.id) {
            mutableStateOf(editingNote?.body ?: "")
        }

        AlertDialog(
            onDismissRequest = { editorOpen = false },
            title = {
                Text(
                    if (editingNote == null) "New note" else "Edit note",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Write your thoughts...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 130.dp),
                        minLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = title.isNotBlank() || body.isNotBlank(),
                    onClick = {
                        val now = System.currentTimeMillis()
                        val id = editingNote?.id ?: now
                        val updated = Note(
                            id = id,
                            title = title.trim(),
                            body = body.trim(),
                            updatedAt = now
                        )
                        val newList = if (editingNote == null) {
                            notes + updated
                        } else {
                            notes.map { if (it.id == id) updated else it }
                        }
                        updateNotes(newList)
                        editorOpen = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (editingNote != null) {
                        TextButton(
                            onClick = {
                                updateNotes(notes.filter { it.id != editingNote!!.id })
                                editorOpen = false
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { editorOpen = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}
