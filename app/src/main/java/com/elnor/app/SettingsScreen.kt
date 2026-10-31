@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ----------------------------- Settings screen -----------------------------

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    trashRetention: TrashRetention,
    onTrashRetentionChange: (TrashRetention) -> Unit,
    appLockEnabled: Boolean,
    onOpenAppLock: () -> Unit,
    onOpenBackup: () -> Unit,
    onBack: () -> Unit,
    fontSize: FontSizeChoice,
    onFontSizeChange: (FontSizeChoice) -> Unit,
    dynamicColorSupported: Boolean,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val bgColor = MaterialTheme.colorScheme.background
    // rememberSaveable: the dialog stays open if the screen is recreated (rotation, phone theme change)
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showTrashDialog by rememberSaveable { mutableStateOf(false) }
    // A shorter time deletes notes at once, so it is only applied after the user confirms. Saved as
    // the stored text (null = no question open), because an enum cannot be saved as it is.
    var pendingShorterStorage by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingShorter = pendingShorterStorage?.let { stored ->
        TrashRetention.entries.firstOrNull { it.storageValue == stored }
    }
    var showFontSizeDialog by rememberSaveable { mutableStateOf(false) }

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

                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

                // Second row of the same card: the size of all text
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { showFontSizeDialog = true }
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        "Font size",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        fontSize.label(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }

                // Third row: the colours of the wallpaper. Only phones with Android 12 or newer can
                // do it, so the others do not see a switch that would do nothing.
                if (dynamicColorSupported) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // The whole row is the switch (better for screen readers and for thumbs)
                            .toggleable(
                                value = dynamicColor,
                                onValueChange = onDynamicColorChange,
                                role = Role.Switch
                            )
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Dynamic color",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Use the colors of your wallpaper",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        // onCheckedChange = null: the row above is the button
                        Switch(checked = dynamicColor, onCheckedChange = null)
                    }
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

            // ---- Trash ----
            SectionLabel("Trash")
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
                        .clickable(role = Role.Button) { showTrashDialog = true }
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        "Keep deleted notes for",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        trashRetention.label(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }

            if (showTrashDialog) {
                TrashRetentionDialog(
                    current = trashRetention,
                    onSelect = { choice ->
                        showTrashDialog = false
                        when {
                            choice == trashRetention -> Unit
                            // Shorter: notes that are already older than that go for good, so ask first
                            choice.isShorterThan(trashRetention) -> pendingShorterStorage = choice.storageValue
                            else -> onTrashRetentionChange(choice)
                        }
                    },
                    onDismiss = { showTrashDialog = false }
                )
            }

            if (pendingShorter != null) {
                AlertDialog(
                    onDismissRequest = { pendingShorterStorage = null },
                    title = { Text("Keep deleted notes for ${pendingShorter.label()}?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "Notes that have been in the Trash for more than ${pendingShorter.days} days " +
                                "will be deleted for good now. This cannot be undone."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingShorterStorage = null
                            onTrashRetentionChange(pendingShorter) // saves, then cleans the Trash, see ElnorRoot
                        }) {
                            Text("Change", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingShorterStorage = null }) { Text("Cancel") }
                    }
                )
            }

            if (showFontSizeDialog) {
                FontSizeDialog(
                    current = fontSize,
                    onSelect = { choice ->
                        showFontSizeDialog = false
                        if (choice != fontSize) onFontSizeChange(choice) // applies and saves, see ElnorRoot
                    },
                    onDismiss = { showFontSizeDialog = false }
                )
            }

            // ---- Data ----
            SectionLabel("Data")
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
                        .clickable(role = Role.Button) { onOpenBackup() }
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        "Backup & restore",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Save your notes to a file, or add them back",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
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

/**
 * "Keep deleted notes for": 7, 30 or 90 days as radio buttons, the current one is checked.
 * Tapping a row selects it at once (the caller applies it, or asks first when it is shorter, and
 * closes the dialog). Tapping outside, Back or Cancel closes it without a change.
 */
@Composable
private fun TrashRetentionDialog(
    current: TrashRetention,
    onSelect: (TrashRetention) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keep deleted notes for", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                for (choice in TrashRetention.entries) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = choice == current,
                                onClick = { onSelect(choice) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // onClick = null: the whole row is the button (better for screen readers)
                        RadioButton(selected = choice == current, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            choice.label(),
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

/**
 * "Font size": Small / Medium / Large as radio buttons, the current one is checked. Each name is
 * shown in its own size, so the choice can be seen before it is made. Tapping a row selects it at
 * once (the caller applies it and closes the dialog), like the theme dialog.
 */
@Composable
private fun FontSizeDialog(
    current: FontSizeChoice,
    onSelect: (FontSizeChoice) -> Unit,
    onDismiss: () -> Unit
) {
    // The dialog itself is drawn in the size that is chosen now, so a row's own size is its scale
    // divided by the current scale, times the text size of the dialog
    val base = MaterialTheme.typography.bodyLarge
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Font size", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                for (choice in FontSizeChoice.entries) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = choice == current,
                                onClick = { onSelect(choice) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // onClick = null: the whole row is the button (better for screen readers)
                        RadioButton(selected = choice == current, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        val factor = choice.scale / current.scale
                        Text(
                            "${choice.label()} · আমার নোট",
                            style = base.copy(
                                fontSize = base.fontSize * factor,
                                lineHeight = base.lineHeight * factor
                            ),
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
