@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// ✏️ DEVELOPER INFO — change these 4 lines to your own details
// ---------------------------------------------------------------------------
internal const val DEVELOPER_NAME = "Sumit Roy"
internal const val DEVELOPER_ROLE = "Email: sumitroypalak@gmail.com"
internal const val DEVELOPER_GITHUB = "https://github.com/sumitroypalak"
internal const val APP_VERSION = "1.0"
// ----------------------------- Activity & root -----------------------------

// FragmentActivity (a ComponentActivity too) is needed by the system unlock dialog of the app lock
class MainActivity : FragmentActivity() {

    // One editor ViewModel for the whole activity. It survives screen rotation.
    private val editorViewModel: NoteEditorViewModel by viewModels {
        NoteEditorViewModel.factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Read the saved theme before anything is drawn, and paint the window (colour and
        // status/navigation bar icons) at once. So the first frame never shows the wrong theme.
        val initialThemeMode = loadThemeMode(applicationContext)
        val systemDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        applyWindowTheme(window, window.decorView, initialThemeMode.isDark(systemDark))

        // Reads the app lock settings. A cold start with the lock on starts locked.
        AppLock.init(applicationContext)

        // While the App lock is on, the window is secure: the Recent Apps thumbnail is blank and
        // screenshots / screen recording are blocked. It is set before the first frame, and
        // follows the switch in Settings > App lock at once (the app is not locked yet at the
        // moment it goes to the background, so the lock screen alone cannot hide the note).
        updateSecureWindow(AppLock.enabled.value)
        lifecycleScope.launch {
            AppLock.enabled.collect { updateSecureWindow(it) }
        }

        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    // Auto-save when the app goes to the background, and note when it left
                    Lifecycle.Event.ON_STOP -> {
                        editorViewModel.onStop()
                        AppLock.onAppStopped()
                    }
                    // Back on screen: lock if it was away longer than "Automatically lock"
                    Lifecycle.Event.ON_START -> AppLock.onAppStarted()
                    // Ask the phone to unlock as soon as the locked app is visible
                    Lifecycle.Event.ON_RESUME -> showUnlockPrompt(automatic = true)
                    else -> Unit
                }
            }
        )

        val repository = RoomNoteRepository.get(applicationContext)
        val taskRepository = RoomTaskRepository.get(applicationContext)
        // Notes that have been in the Trash for 30 days are deleted for good when the app starts
        repository.purgeExpiredTrashInBackground()
        setContent {
            ElnorRoot(
                repository = repository,
                taskRepository = taskRepository,
                editorViewModel = editorViewModel,
                initialThemeMode = initialThemeMode,
                onUnlockClick = { showUnlockPrompt(automatic = false) }
            )
        }
    }

    /** FLAG_SECURE on = blank Recent Apps thumbnail and no screenshots. Only while the App lock is on. */
    private fun updateSecureWindow(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /**
     * Opens the phone's unlock dialog when the app is locked. [automatic] is true when the app
     * itself opens it (not the Unlock button): after the user cancelled it, it does not pop up
     * again by itself until the app has been away once more.
     */
    private fun showUnlockPrompt(automatic: Boolean) {
        if (!AppLock.locked.value || AppLock.promptShowing) return
        if (automatic && !AppLock.autoPromptAllowed) return

        when (AppLockAuth.availability(this)) {
            AppLockAuth.Availability.PERMANENTLY_UNAVAILABLE -> {
                // The phone's screen lock was removed after the app lock was turned on. Nothing is
                // left to ask for, so the lock is turned off instead of locking the user out for good.
                AppLock.setUnlockTemporarilyUnavailable(false)
                AppLock.setEnabled(applicationContext, false)
                return
            }
            AppLockAuth.Availability.TEMPORARILY_UNAVAILABLE -> {
                // A passing problem (for example the sensor is busy). The lock must NOT be turned
                // off: the app stays locked and the lock screen asks the user to try again later.
                AppLock.setUnlockTemporarilyUnavailable(true)
                return
            }
            AppLockAuth.Availability.AVAILABLE -> AppLock.setUnlockTemporarilyUnavailable(false)
        }

        AppLock.promptShowing = true
        AppLockAuth.authenticate(
            activity = this,
            title = "Unlock Elnor",
            onSuccess = {
                AppLock.promptShowing = false
                AppLock.unlock()
            },
            onError = { userCancelled ->
                AppLock.promptShowing = false
                if (userCancelled) AppLock.autoPromptAllowed = false
            }
        )
    }
}

/** The screens the user can open from the home screen. */
private enum class AppScreen { HOME, SETTINGS, TRASH, ARCHIVE, APP_LOCK, BACKUP }

@Composable
fun ElnorRoot(
    repository: NoteRepository,
    taskRepository: TaskRepository,
    editorViewModel: NoteEditorViewModel,
    initialThemeMode: ThemeMode,
    onUnlockClick: () -> Unit = {}
) {
    val context = LocalContext.current
    // Changes by itself when the phone switches between light and dark
    val systemDark = isSystemInDarkTheme()
    var themeMode by remember { mutableStateOf(initialThemeMode) }
    val darkTheme = themeMode.isDark(systemDark)
    // The size of all text (Settings > Appearance > Font size)
    var fontSize by remember { mutableStateOf(loadFontSizeChoice(context)) }
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    val locked by AppLock.locked.collectAsState()
    val appLockEnabled by AppLock.enabled.collectAsState()
    val focusManager = LocalFocusManager.current

    // The keyboard of an open note must not stay up over the lock screen
    LaunchedEffect(locked) {
        if (locked) focusManager.clearFocus(force = true)
    }

    ElnorTheme(darkTheme = darkTheme, fontScale = fontSize.scale) {
        Box(Modifier.fillMaxSize()) {
            // The screens stay composed while locked (an open note is still there after unlocking),
            // but they are hidden from touch and from screen readers by the lock screen above.
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (locked) Modifier.clearAndSetSemantics { } else Modifier)
            ) {
                when (screen) {
                    AppScreen.SETTINGS -> {
                        BackHandler { screen = AppScreen.HOME }
                        SettingsScreen(
                            themeMode = themeMode,
                            onThemeModeChange = {
                                themeMode = it
                                saveThemeMode(context, it)
                            },
                            appLockEnabled = appLockEnabled,
                            onOpenAppLock = { screen = AppScreen.APP_LOCK },
                            onOpenBackup = { screen = AppScreen.BACKUP },
                            onBack = { screen = AppScreen.HOME },
                            fontSize = fontSize,
                            onFontSizeChange = {
                                fontSize = it
                                saveFontSizeChoice(context, it)
                            }
                        )
                    }

                    AppScreen.BACKUP -> {
                        BackHandler { screen = AppScreen.SETTINGS }
                        BackupScreen(
                            repository = repository,
                            onBack = { screen = AppScreen.SETTINGS }
                        )
                    }

                    AppScreen.APP_LOCK -> {
                        BackHandler { screen = AppScreen.SETTINGS }
                        AppLockScreen(onBack = { screen = AppScreen.SETTINGS })
                    }

                    AppScreen.ARCHIVE -> {
                        BackHandler { screen = AppScreen.HOME }
                        ArchiveScreen(
                            repository = repository,
                            editorViewModel = editorViewModel,
                            onBack = { screen = AppScreen.HOME }
                        )
                    }

                    AppScreen.TRASH -> {
                        BackHandler { screen = AppScreen.HOME }
                        TrashScreen(
                            repository = repository,
                            onBack = { screen = AppScreen.HOME }
                        )
                    }

                    AppScreen.HOME -> {
                        ElnorApp(
                            dark = darkTheme,
                            repository = repository,
                            taskRepository = taskRepository,
                            editorViewModel = editorViewModel,
                            onOpenSettings = { screen = AppScreen.SETTINGS },
                            onOpenArchive = { screen = AppScreen.ARCHIVE },
                            onOpenTrash = { screen = AppScreen.TRASH }
                        )
                    }
                }
            }

            if (locked) LockedScreen(onUnlock = onUnlockClick)
        }
    }
}
