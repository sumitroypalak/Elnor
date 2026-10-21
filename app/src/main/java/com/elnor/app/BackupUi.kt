@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/** A backup file bigger than this is refused, so a wrong file cannot fill the memory. */
private const val MAX_BACKUP_BYTES = 50L * 1024L * 1024L

/**
 * Backup and restore: save all notes into one file that the user places (Downloads, Google Drive,
 * a memory card ...), and add the notes of such a file back.
 *
 * The file is chosen with the system file picker, so Elnor needs no storage permission.
 * The work runs in [ApplicationScope], like saving a note, so leaving this screen cannot stop a
 * file half-way written.
 */
@Composable
fun BackupScreen(
    repository: NoteRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.background
    val uiScope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    // rememberSaveable: the result stays on the screen after rotating the phone
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // the user cancelled
        busy = true
        message = null
        ApplicationScope.scope.launch {
            message = try {
                val count = withContext(Dispatchers.IO) { writeBackup(context, repository, uri) }
                BackupFormat.exportMessage(count)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                "The backup could not be saved. Please try another place."
            } finally {
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        message = null
        ApplicationScope.scope.launch {
            message = try {
                val text = withContext(Dispatchers.IO) { readBackup(context, uri) }
                when (val parsed = BackupFormat.fromJson(text)) {
                    is BackupParseResult.Failure -> parsed.reason.message
                    is BackupParseResult.Success -> {
                        val summary = repository.importNotes(parsed.notes)
                        BackupFormat.importMessage(summary, parsed.skipped)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                "The file could not be read."
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = { Text("Backup & restore", fontWeight = FontWeight.Bold) },
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
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }

            ActionCard(
                title = "Back up your notes",
                description = "Saves all your notes, also the ones in the Trash, into one file. " +
                    "You choose where it goes, for example Downloads or Google Drive.",
                buttonLabel = "Save backup file",
                enabled = !busy,
                onClick = {
                    uiScope.launch {
                        // No empty backup files: there would be nothing to restore from them
                        if (repository.exportAll().isEmpty()) {
                            message = "There are no notes to back up yet."
                        } else {
                            exportLauncher.launch(BackupFormat.fileName(System.currentTimeMillis()))
                        }
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            ActionCard(
                title = "Restore from a backup",
                description = "Adds the notes of a backup file. Notes you already have are never " +
                    "changed or replaced, and a note that is already here is not added twice.",
                buttonLabel = "Choose backup file",
                enabled = !busy,
                onClick = { importLauncher.launch(arrayOf("*/*")) }
            )

            val result = message
            if (result != null) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        result,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "The backup file is not encrypted and the App lock does not protect it. " +
                    "Keep it in a place only you can open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    buttonLabel: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(14.dp))
            FilledTonalButton(onClick = onClick, enabled = enabled) {
                Text(buttonLabel)
            }
        }
    }
}

/** Writes every note to [uri] as UTF-8 and returns how many notes it wrote. */
private suspend fun writeBackup(context: Context, repository: NoteRepository, uri: Uri): Int {
    val notes = repository.exportAll()
    val json = BackupFormat.toJson(notes, System.currentTimeMillis())
    // "wt": an existing file is emptied first, so no old bytes stay at the end of a shorter file
    val stream = context.contentResolver.openOutputStream(uri, "wt")
        ?: throw IOException("Cannot open the file for writing")
    stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    return notes.size
}

/** Reads the whole file at [uri] as UTF-8 text. Throws if it cannot be opened or is too big. */
private fun readBackup(context: Context, uri: Uri): String {
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IOException("Cannot open the file for reading")
    return input.use { stream ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_BACKUP_BYTES) throw IOException("The file is too big")
            out.write(buffer, 0, read)
        }
        String(out.toByteArray(), Charsets.UTF_8)
    }
}
