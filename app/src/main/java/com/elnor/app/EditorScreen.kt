@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    val context = LocalContext.current

    // Word and character count of the whole note. The first value is calculated at once, then it
    // is updated a moment after the typing stops, off the main thread, so a long note stays smooth.
    var stats by remember { mutableStateOf(noteStats(title, bodyValue.text)) }
    LaunchedEffect(title, bodyValue.text) {
        val currentTitle = title
        val currentBody = bodyValue.text
        delay(150)
        stats = withContext(Dispatchers.Default) { noteStats(currentTitle, currentBody) }
    }

    // The delete button is only shown when the note exists in storage
    val canDelete by viewModel.hasSavedNote.collectAsState()

    // Undo / Redo. The buttons are on only when there is something to take back / bring back.
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

    // Shows a snapshot from the Undo memory on the page (title, text and cursor). The ViewModel
    // has already changed the remembered text, so the next save is what is on the screen.
    fun showSnapshot(snapshot: EditSnapshot?) {
        if (snapshot == null) return
        title = snapshot.title
        val length = snapshot.body.length
        bodyValue = TextFieldValue(
            snapshot.body,
            TextRange(snapshot.bodySelStart.coerceIn(0, length), snapshot.bodySelEnd.coerceIn(0, length))
        )
    }

    // "Last edited": the time of the last save. The ViewModel updates it at every save (also for a
    // note that was new when the page opened). Until the editing session has started, the time of
    // the note that was opened is used.
    val lastSavedAt by viewModel.lastSavedAt.collectAsState()
    val openedAt = note?.updatedAt
    // remember: the text is not rebuilt on every key that is typed
    val lastEdited = remember(lastSavedAt, openedAt) {
        lastEditedLabel(lastSavedAt ?: openedAt, ::formatNoteDate)
    }

    // Start the editing session (nothing happens if it is already open, e.g. after rotation),
    // then give the ViewModel the text that is on screen.
    LaunchedEffect(noteKey) {
        viewModel.open(note)
        viewModel.onTitleChange(title)
        viewModel.onBodyChange(bodyValue.text, bodyValue.selection.start, bodyValue.selection.end)
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
                    IconButton(onClick = { showSnapshot(viewModel.undo()) }, enabled = canUndo) {
                        Icon(UndoIcon, contentDescription = "Undo")
                    }
                    IconButton(onClick = { showSnapshot(viewModel.redo()) }, enabled = canRedo) {
                        Icon(RedoIcon, contentDescription = "Redo")
                    }
                    // Copy: the title and the text that are on the screen now (also if not saved yet)
                    val copyText = noteTextForCopy(title, bodyValue.text)
                    if (copyText.isNotEmpty()) {
                        IconButton(onClick = { copyTextToClipboard(context, copyText) }) {
                            Icon(CopyIcon, contentDescription = "Copy note text")
                        }
                        // Share: opens Android's Share sheet with the same text as Copy
                        IconButton(onClick = {
                            AppLock.expectExternalScreen() // the Share sheet is not "leaving" Elnor
                            shareNote(context, title, bodyValue.text)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share note")
                        }
                    }
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
                    viewModel.onTitleChange(it) // saved after a typing pause
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

            // When the note was last saved (nothing for a note that was never saved)
            if (lastEdited != null) {
                Text(
                    lastEdited,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            }

            // Body: fills the rest of the screen
            TextField(
                value = bodyValue,
                onValueChange = {
                    bodyValue = it
                    viewModel.onBodyChange(it.text, it.selection.start, it.selection.end) // saved after a typing pause
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

            // Word and character count, just above the keyboard
            Text(
                formatNoteStats(stats),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
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
