package com.elnor.app

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The "App lock" page (Settings > App lock). Same layout as WhatsApp's:
 *  - "Unlock with biometric" switch with its explanation
 *  - "Automatically lock": Immediately / After 1 minute / After 30 minutes (only while it is on)
 *
 * Turning the lock on or off asks the phone first (biometric or the phone's screen-lock PIN),
 * so nobody else can change it. There is no PIN of our own anywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val bgColor = MaterialTheme.colorScheme.background
    val enabled by AppLock.enabled.collectAsState()
    val timeout by AppLock.timeout.collectAsState()
    var showSetupDialog by rememberSaveable { mutableStateOf(false) }

    fun changeEnabled(target: Boolean) {
        if (!AppLockAuth.isAvailable(context)) {
            // The phone has no screen lock or biometric to ask for. Turning the lock off is
            // always allowed. Turning it on needs the phone to be set up first.
            if (target) showSetupDialog = true else AppLock.setEnabled(context, false)
            return
        }
        if (activity == null) return
        AppLockAuth.authenticate(
            activity = activity,
            title = if (target) "Turn on app lock" else "Turn off app lock",
            onSuccess = { AppLock.setEnabled(context, target) }
        )
    }

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = { Text("App lock") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = enabled,
                        onClick = { changeEnabled(!enabled) },
                        role = Role.Switch
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Unlock with biometric",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "When enabled, you'll need to use fingerprint, face or other unique " +
                            "identifiers to open Elnor.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.width(16.dp))
                // onCheckedChange = null: the whole row is the button (better for screen readers)
                Switch(
                    checked = enabled,
                    onCheckedChange = null,
                    thumbContent = if (enabled) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    }
                )
            }

            if (enabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                Text(
                    "Automatically lock",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
                )
                Column(Modifier.selectableGroup()) {
                    for (option in AutoLockTimeout.entries) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == timeout,
                                    onClick = { AppLock.setTimeout(context, option) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == timeout, onClick = null)
                            Spacer(Modifier.width(24.dp))
                            Text(
                                option.label(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSetupDialog) {
        AlertDialog(
            onDismissRequest = { showSetupDialog = false },
            title = { Text("Set up a screen lock", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "To use app lock, first set up a screen lock (PIN, pattern or password) " +
                        "or a fingerprint or face unlock in your phone's settings."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSetupDialog = false
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                        }
                    }
                ) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { showSetupDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun AutoLockTimeout.label(): String = when (this) {
    AutoLockTimeout.IMMEDIATELY -> "Immediately"
    AutoLockTimeout.ONE_MINUTE -> "After 1 minute"
    AutoLockTimeout.THIRTY_MINUTES -> "After 30 minutes"
}

/**
 * Covers the whole app while it is locked. It is drawn on top of the screens (they stay as they
 * were, so an open note comes back after unlocking) and takes every touch.
 * [onUnlock] asks the phone to check the user again.
 */
@Composable
fun LockedScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    // Back must not reach the screens below. It sends the app to the background instead.
    BackHandler { context.findFragmentActivity()?.moveTaskToBack(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Elnor is locked",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Use your fingerprint, face or phone PIN to open Elnor.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(28.dp))
            androidx.compose.material3.Button(onClick = onUnlock) {
                Text("Unlock")
            }
        }
    }
}
