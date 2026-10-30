@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch

// ----------------------------- Settings storage ----------------------------

private const val SETTINGS_FILE = "elnor_settings"
private const val KEY_THEME_MODE = "theme_mode"

/** Key of the old two-state switch (true = dark). Only read, so an earlier choice is kept. */
private const val KEY_LEGACY_DARK_MODE = "dark_mode"

/** Reads the saved theme. Nothing saved means SYSTEM. */
internal fun loadThemeMode(context: Context): ThemeMode {
    val prefs = context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
    val legacyDark =
        if (prefs.contains(KEY_LEGACY_DARK_MODE)) prefs.getBoolean(KEY_LEGACY_DARK_MODE, false) else null
    return ThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, null), legacyDark)
}

internal fun saveThemeMode(context: Context, mode: ThemeMode) {
    context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_THEME_MODE, mode.storageValue)
        .remove(KEY_LEGACY_DARK_MODE) // the new value replaces the old switch
        .apply()
}

/** The only place where the app's light and dark colours are defined. */
fun elnorColorScheme(dark: Boolean): ColorScheme = if (dark) {
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

/**
 * Makes the window itself match the theme: the colour behind the app (seen behind the keyboard
 * and while the screen changes) and the icons of the status and navigation bars.
 * Used at launch (MainActivity) and every time the theme changes (ElnorTheme).
 */
internal fun applyWindowTheme(window: Window, view: View, dark: Boolean) {
    window.setBackgroundDrawable(ColorDrawable(elnorColorScheme(dark).background.toArgb()))
    val controller = WindowCompat.getInsetsController(window, view)
    controller.isAppearanceLightStatusBars = !dark
    controller.isAppearanceLightNavigationBars = !dark
}

/**
 * [fontScale] is the factor of the chosen font size ([FontSizeChoice.scale], Settings > Appearance
 * > Font size). 1 is the normal size.
 */
@Composable
fun ElnorTheme(
    darkTheme: Boolean,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val colors = elnorColorScheme(darkTheme)
    // Built again only when the font size changes
    val typography = remember(fontScale) { elnorTypography(fontScale) }

    // Large, rounded shapes in the Material 3 Expressive style
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp)
    )

    // Keep the window and the status/navigation bar icons in step with the in-app theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) applyWindowTheme(window, view, darkTheme)
        }
    }

    MaterialTheme(
        colorScheme = colors,
        shapes = shapes,
        typography = typography,
        content = content
    )
}
