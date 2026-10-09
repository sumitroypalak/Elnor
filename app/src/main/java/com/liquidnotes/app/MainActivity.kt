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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFFB9B5FF),
            background = Color(0xFF10111B),
            surface = Color(0xFF1C1D2B)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6257D9),
            background = Color(0xFFF5F5FC),
            surface = Color(0xFFFFFFFF)
        )
    }

    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun DraftoApp() {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(loadNotes(context)) }
    var search by remember { mutableStateOf("") }
    var editorOpen by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }

    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val background = MaterialTheme.colorScheme.background
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

    fun updateNotes(updated: List<Note>) {
        notes = updated.sortedByDescending { it.updatedAt }
        saveNotes(context, notes)
    }

    Scaffold(
        containerColor = background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingNote = null
                    editorOpen = true
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text("+", fontSize = 32.sp)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "YOUR SPACE",
                        fontSize = 11.sp,
                        letterSpacing = 3.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Drafto",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✎", fontSize = 25.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Your thoughts, beautifully organized.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search your notes...") },
                leadingIcon = { Text("⌕", fontSize = 25.sp) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Your notes",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${notes.size} notes",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
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
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (search.isBlank())
                            "Tap + to capture your first thought."
                        else "Try searching with another word.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                }
            } else {
                LazyColumn(
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
                            shape = RoundedCornerShape(23.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = cardColor
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 0.dp
                            )
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text(
                                    note.title.ifBlank { "Untitled note" },
                                    fontSize = 18.sp,
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
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp,
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
                                    fontSize = 11.sp,
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
                TextButton(
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
