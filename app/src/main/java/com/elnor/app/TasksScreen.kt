@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.elnor.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate

// ----------------------------- Task tab ------------------------------------

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * The Task tab: a list of to-dos with a check box, an optional deadline and the filter
 * All / Active / Completed.
 *
 * [addDialogOpen] is controlled by the home screen, because the round "+" button of the bottom bar
 * lives there. Changes are sent through [ApplicationScope], like saves in the editor, so leaving
 * the tab cannot cancel them.
 *
 * [snackbarHostState] belongs to the home screen's Scaffold, which shows the message with "Undo"
 * that follows checking a task off.
 */
@Composable
fun TasksScreen(
    repository: TaskRepository,
    padding: PaddingValues,
    addDialogOpen: Boolean,
    onAddDialogDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val view = LocalView.current
    val uiScope = rememberCoroutineScope()

    // null until the database has answered, so "No tasks yet" never flashes
    val loaded by repository.tasks.collectAsState<List<Task>, List<Task>?>(initial = null)
    val tasks = loaded
    var filter by rememberSaveable { mutableStateOf(TaskFilter.ALL) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()

    val allTasks = tasks ?: emptyList()
    val shown = remember(allTasks, filter) { sortTasks(filterTasks(allTasks, filter)) }
    val completedCount = allTasks.count { it.isCompleted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp)
    ) {
        Text(
            if (tasks == null) "" else taskCountLabel(allTasks),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
        )
        Spacer(Modifier.height(12.dp))

        // ---- Filter ----
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (option in TaskFilter.entries) {
                val count = filterTasks(allTasks, option).size
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text("${option.label} · $count") }
                )
            }
        }

        if (completedCount > 0 && filter != TaskFilter.ACTIVE) {
            TextButton(onClick = { confirmClear = true }) {
                Text("Clear completed")
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        // ---- List ----
        when {
            tasks == null -> Unit
            shown.isEmpty() -> EmptyTasks(filter = filter, hasAnyTask = allTasks.isNotEmpty())
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // room for the floating bottom bar
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(shown, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        today = today,
                        onToggle = { done ->
                            ApplicationScope.scope.launch { repository.setCompleted(task.id, done) }
                            if (done) {
                                // A light tick, and a message that takes the check back (a task
                                // checked by mistake). Unchecking a task needs neither.
                                performTaskDoneHaptic(view)
                                uiScope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = taskCompletedMessage(truncateTitle(task.title, 30)),
                                        actionLabel = "Undo",
                                        withDismissAction = true,
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        ApplicationScope.scope.launch {
                                            repository.setCompleted(task.id, false)
                                        }
                                    }
                                }
                            }
                        },
                        onClick = { editingId = task.id }
                    )
                }
            }
        }
    }

    // ---- Dialogs ----
    if (addDialogOpen) {
        TaskEditDialog(
            initial = null,
            today = today,
            onSave = { title, due ->
                ApplicationScope.scope.launch { repository.add(title, due) }
                onAddDialogDismiss()
            },
            onDelete = null,
            onDismiss = onAddDialogDismiss
        )
    }

    // Look the task up by id, so a task that is gone (deleted elsewhere) just closes the dialog
    val editing = allTasks.firstOrNull { it.id == editingId }
    if (editing != null) {
        TaskEditDialog(
            initial = editing,
            today = today,
            onSave = { title, due ->
                ApplicationScope.scope.launch { repository.update(editing.id, title, due) }
                editingId = null
            },
            onDelete = {
                editingId = null
                deleteId = editing.id
            },
            onDismiss = { editingId = null }
        )
    }

    val toDelete = allTasks.firstOrNull { it.id == deleteId }
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete this task?") },
            text = { Text("\"${toDelete.title}\" will be deleted for good.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteId = null
                    ApplicationScope.scope.launch { repository.delete(toDelete.id) }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear completed tasks?") },
            text = {
                Text(
                    if (completedCount == 1) "1 completed task will be deleted for good."
                    else "$completedCount completed tasks will be deleted for good."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    ApplicationScope.scope.launch { repository.clearCompleted() }
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptyTasks(filter: TaskFilter, hasAnyTask: Boolean) {
    val (title, subtitle) = when {
        !hasAnyTask -> "No tasks yet" to "Tap + to add your first task."
        filter == TaskFilter.ACTIVE -> "Nothing left to do" to "Every task is done."
        else -> "No completed tasks" to "Tasks you check off appear here."
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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

@Composable
private fun TaskRow(
    task: Task,
    today: LocalDate,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = task.isCompleted, onCheckedChange = onToggle)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (task.isCompleted) onSurface.copy(alpha = 0.5f) else onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                )
                val label = deadlineLabel(task, today)
                if (label != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = when (taskUrgency(task, today)) {
                            TaskUrgency.OVERDUE -> MaterialTheme.colorScheme.error
                            TaskUrgency.TODAY -> MaterialTheme.colorScheme.primary
                            else -> onSurface.copy(alpha = 0.6f)
                        }
                    )
                }
            }
        }
    }
}

/**
 * New task ([initial] is null) or edit of an existing one. [onDelete] is null for a new task.
 * The typed text and the chosen deadline survive a screen rotation.
 */
@Composable
private fun TaskEditDialog(
    initial: Task?,
    today: LocalDate,
    onSave: (title: String, dueDate: Long?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var dueDate by rememberSaveable { mutableStateOf(initial?.dueDate) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val canSave = title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New task" else "Edit task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("What needs to be done?") },
                    maxLines = 3
                )

                // ---- Deadline ----
                Text(
                    "Deadline",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val todayDay = today.toEpochDay()
                    FilterChip(
                        selected = dueDate == todayDay,
                        onClick = { dueDate = todayDay },
                        label = { Text("Today") }
                    )
                    FilterChip(
                        selected = dueDate == todayDay + 1,
                        onClick = { dueDate = todayDay + 1 },
                        label = { Text("Tomorrow") }
                    )
                    val customDate = dueDate != null && dueDate != todayDay && dueDate != todayDay + 1
                    FilterChip(
                        selected = customDate,
                        onClick = { showPicker = true },
                        label = {
                            Text(if (customDate) formatDueDate(dueDate!!, today) else "Pick date")
                        }
                    )
                }
                if (dueDate != null) {
                    TextButton(onClick = { dueDate = null }) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove deadline")
                    }
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Delete task") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = { onSave(title, dueDate) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showPicker) {
        // The date picker works with the UTC midnight of the chosen day, which is exactly
        // epochDay * 24h, so the day number comes out the same in every time zone.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (dueDate ?: today.toEpochDay()) * MILLIS_PER_DAY
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { dueDate = Math.floorDiv(it, MILLIS_PER_DAY) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
