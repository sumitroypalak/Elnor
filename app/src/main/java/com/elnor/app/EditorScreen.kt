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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
    var confirmLeaveUnsaved by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val bodyFocus = remember { FocusRequester() }
    val context = LocalContext.current

    // "Find in note": the bar above the title, the word, and which found place is the chosen one.
    // It only looks at the text, it never changes the note.
    var findOpen by rememberSaveable { mutableStateOf(false) }
    var findQuery by rememberSaveable { mutableStateOf("") }
    var findCurrent by rememberSaveable { mutableStateOf(0) }
    val findFocus = remember { FocusRequester() }
    LaunchedEffect(findOpen) {
        if (findOpen) runCatching { findFocus.requestFocus() }
    }

    // Word and character count of the whole note. The first value is calculated at once, then it
    // is updated a moment after the typing stops, off the main thread, so a long note stays smooth.
    var stats by remember { mutableStateOf(noteStats(title, MarkdownFormat.withoutMarks(bodyValue.text))) }
    LaunchedEffect(title, bodyValue.text) {
        val currentTitle = title
        val currentBody = bodyValue.text
        delay(150)
        // The marks of the formatting (** # - 1.) are not words and not characters of the text
        stats = withContext(Dispatchers.Default) {
            noteStats(currentTitle, MarkdownFormat.withoutMarks(currentBody))
        }
    }

    // Links in the text (web, e-mail, phone). They are shown in the link colour. While the cursor is
    // inside one and the text field has the focus, a button above the keyboard opens it.
    val links = remember(bodyValue.text) { LinkDetector.find(bodyValue.text) }
    val linkColor = MaterialTheme.colorScheme.primary
    // Formatting (bold, headings, lists) is written as Markdown in the text itself. The editor shows
    // it styled and keeps the marks visible but dimmed.
    val formatSpans = remember(bodyValue.text) { MarkdownFormat.parse(bodyValue.text) }
    val markerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
    // The places of the searched word in the text (none while Find is closed)
    val findMatches = remember(bodyValue.text, findOpen, findQuery) {
        if (findOpen) FindInNote.find(bodyValue.text, findQuery) else emptyList()
    }
    // The chosen place. Kept inside the list, because the text can get shorter while typing.
    val findIndex = if (findMatches.isEmpty()) -1 else findCurrent.coerceIn(0, findMatches.lastIndex)
    val textStyling = remember(links, formatSpans, linkColor, markerColor, findMatches, findIndex) {
        NoteTextTransformation(links, formatSpans, linkColor, markerColor, findMatches, findIndex)
    }
    var bodyFocused by remember { mutableStateOf(false) }
    val cursor = bodyValue.selection
    val activeLink = if (bodyFocused && cursor.collapsed) {
        LinkDetector.linkAtCursor(links, cursor.start)
    } else {
        null
    }

    // A formatting button: [change] gets the text and the selection and returns the new text. The
    // page shows it and the ViewModel remembers it (saved after a typing pause, like typing).
    fun applyFormat(change: (String, Int, Int) -> TextEdit) {
        val selection = bodyValue.selection
        val result = change(
            bodyValue.text,
            minOf(selection.start, selection.end),
            maxOf(selection.start, selection.end)
        )
        bodyValue = TextFieldValue(result.text, TextRange(result.selStart, result.selEnd))
        viewModel.onBodyChange(result.text, result.selStart, result.selEnd)
    }

    // Puts the cursor at the start of a found place. The text field scrolls to its cursor when it
    // has the focus, so the focus goes to the text (the Find field is tapped again to change the word).
    fun jumpToMatch(index: Int) {
        val match = findMatches.getOrNull(index) ?: return
        findCurrent = index
        val text = bodyValue.text
        bodyValue = TextFieldValue(text, TextRange(match.start.coerceIn(0, text.length)))
        runCatching { bodyFocus.requestFocus() }
    }

    // Next / Previous. The first press shows the chosen place (the first one after typing the word),
    // the next presses move on.
    fun findStep(forward: Boolean) {
        if (findIndex < 0) return
        val atChosen = bodyValue.selection.collapsed && bodyValue.selection.start == findMatches[findIndex].start
        jumpToMatch(if (atChosen) FindInNote.step(findIndex, findMatches.size, forward) else findIndex)
    }

    fun closeFind() {
        findOpen = false
        findQuery = ""
        findCurrent = 0
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

    // The last save did not work (for example the storage is full). The text is still here.
    val saveFailed by viewModel.saveFailed.collectAsState()
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            snackbarHostState.showSnackbar(
                message = "Could not save your note. Your text is still here.",
                duration = SnackbarDuration.Long
            )
        }
    }

    // Back arrow and the phone's Back button / gesture: the ViewModel saves, we close the page.
    // (There is no Save button. The app going to the background is saved by MainActivity.)
    // If the last save failed, the user is asked first: leaving now may lose the latest text.
    fun saveAndClose() {
        if (saveFailed) {
            confirmLeaveUnsaved = true
            return
        }
        viewModel.onBack()
        onClose()
    }

    BackHandler { saveAndClose() }
    // Registered after the one above, so it wins: Back closes the Find bar first
    BackHandler(enabled = findOpen) { closeFind() }

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
        // imePadding: the message must stay above the keyboard
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.imePadding()) },
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
                    // Find in note: only when there is some text to look in
                    if (bodyValue.text.isNotEmpty()) {
                        IconButton(onClick = { findOpen = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Find in note")
                        }
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
            // Find in note (opened by the search button at the top)
            if (findOpen) {
                FindBar(
                    query = findQuery,
                    onQueryChange = {
                        findQuery = it
                        findCurrent = 0
                    },
                    counter = FindInNote.counterLabel(findQuery.isBlank(), findIndex, findMatches.size),
                    hasMatches = findMatches.isNotEmpty(),
                    onPrevious = { findStep(forward = false) },
                    onNext = { findStep(forward = true) },
                    onClose = { closeFind() },
                    focusRequester = findFocus
                )
            }

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
                    // Enter inside a list starts the next item (or ends the list on an empty item)
                    val listEdit = if (it.selection.collapsed) {
                        MarkdownFormat.afterNewline(bodyValue.text, it.text, it.selection.start)
                    } else {
                        null
                    }
                    val next = if (listEdit != null) {
                        TextFieldValue(listEdit.text, TextRange(listEdit.selStart, listEdit.selEnd))
                    } else {
                        it
                    }
                    bodyValue = next
                    viewModel.onBodyChange(next.text, next.selection.start, next.selection.end) // saved after a typing pause
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(bodyFocus)
                    .onFocusChanged { bodyFocused = it.isFocused },
                visualTransformation = textStyling,
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

            // Stays on the screen as long as the last save has not worked
            if (saveFailed) {
                Text(
                    "Not saved yet. Elnor will try again when you type or leave.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            }

            // Bold, heading and list buttons for the text (not for the title). The button for the
            // link the cursor is in (Call / Email / Open) shares the row, so it takes no extra height.
            if (bodyFocused) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    FormatToolbar(
                        onBold = { applyFormat(MarkdownFormat::toggleBold) },
                        onHeading = { applyFormat(MarkdownFormat::cycleHeading) },
                        onBullet = { applyFormat(MarkdownFormat::toggleBullet) },
                        onNumbered = { applyFormat(MarkdownFormat::toggleNumbered) }
                    )
                    if (activeLink != null) {
                        Spacer(Modifier.width(8.dp))
                        LinkActionChip(
                            link = activeLink,
                            onClick = {
                                AppLock.expectExternalScreen() // the dialer / browser is not "leaving" Elnor
                                openLink(context, activeLink)
                            },
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

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

    if (confirmLeaveUnsaved) {
        AlertDialog(
            onDismissRequest = {
                confirmLeaveUnsaved = false
                viewModel.retrySave()
            },
            title = { Text("Note not saved", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Elnor could not save this note. If you leave now, your latest changes " +
                        "may be lost. Use the copy button to keep the text first."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeaveUnsaved = false
                        viewModel.onBack() // one last try
                        onClose()
                    }
                ) {
                    Text("Leave anyway", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmLeaveUnsaved = false
                        viewModel.retrySave()
                    }
                ) {
                    Text("Keep editing")
                }
            }
        )
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
