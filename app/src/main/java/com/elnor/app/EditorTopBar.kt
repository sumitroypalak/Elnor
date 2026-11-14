@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// The top bar of the note page (split out of EditorScreen.kt, nothing changed in how it works).

/**
 * Back arrow, Undo / Redo, Find, Pin and the three-dot menu (Copy, Share, Save as a file, Delete).
 *
 * The bar only draws and reports clicks: the page ([EditorScreen]) owns the text and does the work
 * in [onBack], [onUndo], [onRedo], [onFind], [onTogglePin] and [onDelete]. [title] and [bodyText]
 * are the text that is on the screen now (also if it is not saved yet), used by Copy, Share and
 * Save as a file.
 */
@Composable
internal fun EditorTopBar(
    title: String,
    bodyText: String,
    compactHeight: Boolean,
    bgColor: Color,
    canUndo: Boolean,
    canRedo: Boolean,
    canPin: Boolean,
    pinned: Boolean,
    canDelete: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFind: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    // Save the note as a file: Android's "Save as" screen asks where. The text is taken when the
    // user has chosen the place, so the file has what is on the screen at that moment.
    var moreMenuOpen by remember { mutableStateOf(false) }
    val saveTxtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(NoteExportFormat.TXT.mimeType)
    ) { uri ->
        if (uri != null) saveNoteToUri(context, uri, title, bodyText, NoteExportFormat.TXT)
    }
    val saveMdLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(NoteExportFormat.MD.mimeType)
    ) { uri ->
        if (uri != null) saveNoteToUri(context, uri, title, bodyText, NoteExportFormat.MD)
    }

    TopAppBar(
        title = {},
        // Normal height is 64.dp; a phone held sideways gives the text more room
        expandedHeight = if (compactHeight) 48.dp else 64.dp,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Save and go back"
                )
            }
        },
        actions = {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(UndoIcon, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(RedoIcon, contentDescription = "Redo")
            }
            // Find in note: only when there is some text to look in
            if (bodyText.isNotEmpty()) {
                IconButton(onClick = onFind) {
                    Icon(Icons.Default.Search, contentDescription = "Find in note")
                }
            }
            // Pin: a solid blue pin when the note is pinned, a line pin when it is not
            if (canPin) {
                IconButton(onClick = onTogglePin) {
                    Icon(
                        if (pinned) PinIcon else PinOutlineIcon,
                        contentDescription = if (pinned) "Unpin note" else "Pin note",
                        tint = if (pinned) MaterialTheme.colorScheme.primary
                        else LocalContentColor.current
                    )
                }
            }
            // The title and the text that are on the screen now (also if not saved yet)
            val copyText = noteTextForCopy(title, bodyText)
            // One three-dot menu: Copy, Share, Save as a file, and Delete
            if (copyText.isNotEmpty() || canDelete) {
                Box {
                    IconButton(onClick = { moreMenuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = moreMenuOpen,
                        onDismissRequest = { moreMenuOpen = false }
                    ) {
                        if (copyText.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Copy text") },
                                onClick = {
                                    moreMenuOpen = false
                                    copyTextToClipboard(context, copyText)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    moreMenuOpen = false
                                    AppLock.expectExternalScreen() // the Share sheet is not "leaving" Elnor
                                    shareNote(context, title, bodyText)
                                }
                            )
                            for (format in NoteExportFormat.entries) {
                                DropdownMenuItem(
                                    text = { Text(format.label) },
                                    onClick = {
                                        moreMenuOpen = false
                                        AppLock.expectExternalScreen() // the "Save as" screen is not "leaving" Elnor
                                        val name = NoteExport.fileName(title, bodyText, format)
                                        val launched = runCatching {
                                            when (format) {
                                                NoteExportFormat.TXT -> saveTxtLauncher.launch(name)
                                                NoteExportFormat.MD -> saveMdLauncher.launch(name)
                                            }
                                        }.isSuccess
                                        if (!launched) {
                                            Toast.makeText(
                                                context.applicationContext,
                                                "Could not open the Save screen",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                        }
                        if (copyText.isNotEmpty() && canDelete) HorizontalDivider()
                        if (canDelete) {
                            DropdownMenuItem(
                                text = {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    moreMenuOpen = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
    )
}
