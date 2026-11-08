@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
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

/**
 * The dark theme: a blue, slightly cool grey look (Material 3 blue palette). The values marked
 * "measured" were read from a screenshot of the app that the owner wants as the default look.
 * The others are the matching steps of the same palette, so no colour is left to the Material
 * default, which is purple and would show up in dialogs, menus and the date picker.
 *
 * "surface" is the colour of cards, the search field and the bottom bar. It is one step lighter
 * than the page ("background"), the same way the wallpaper colours are arranged (see
 * [wallpaperColorScheme]), so the cards stand out without a shadow.
 */
private fun elnorDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFFB0C6FF), // measured: "1 note", sort and grid icons
    onPrimary = Color(0xFF002D6E),
    primaryContainer = Color(0xFF00419C), // measured: the selected tab of the bottom bar
    onPrimaryContainer = Color(0xFFD9E2FF),
    inversePrimary = Color(0xFF0058CA), // the "Undo" of a message at the bottom
    secondary = Color(0xFFC0C6DC),
    onSecondary = Color(0xFF2A3042),
    secondaryContainer = Color(0xFF404659), // measured: the menu button
    onSecondaryContainer = Color(0xFFDCE2F9), // measured: the icon on the menu button
    tertiary = Color(0xFFE2BBDB),
    onTertiary = Color(0xFF43273F),
    tertiaryContainer = Color(0xFF5B3D56),
    onTertiaryContainer = Color(0xFFFFD7F8),
    background = Color(0xFF121318), // measured: the page
    onBackground = Color(0xFFE2E2E9), // measured: "Elnor", titles
    surface = Color(0xFF1E1F25), // measured: cards, search field, bottom bar
    onSurface = Color(0xFFE2E2E9), // measured
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceTint = Color(0xFFB0C6FF),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    surfaceDim = Color(0xFF121318),
    surfaceBright = Color(0xFF38393F),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF1A1B20),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A)
)

/**
 * The light theme: the same blue palette as [elnorDarkColorScheme], in its light steps. Unlike the
 * dark theme, these values were NOT measured from a screenshot: they are the light counterparts of
 * the measured dark colours (for example the light "secondaryContainer" #DCE2F9 is the "on" colour
 * of the same container in the dark theme), so both themes belong together.
 *
 * Like before, the page ("background") is a soft tint and the cards ("surface") are white, so the
 * cards stand out without a shadow.
 */
private fun elnorLightColorScheme(): ColorScheme = lightColorScheme(
    primary = Color(0xFF0058CA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001945),
    inversePrimary = Color(0xFFB0C6FF), // the "Undo" of a message at the bottom
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF151B2C),
    tertiary = Color(0xFF735470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F8),
    onTertiaryContainer = Color(0xFF2B122A),
    background = Color(0xFFF4F3FA), // the soft tint of the page
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFFFFFF), // cards, search field
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceTint = Color(0xFF0058CA),
    inverseSurface = Color(0xFF2F3036), // the dark bottom bar and the message at the bottom
    inverseOnSurface = Color(0xFFF1F0F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC4C6D0),
    surfaceDim = Color(0xFFDAD9E0),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EE),
    surfaceContainerHighest = Color(0xFFE2E2E9)
)

/** The only place where the app's light and dark colours are defined. */
fun elnorColorScheme(dark: Boolean): ColorScheme =
    if (dark) elnorDarkColorScheme() else elnorLightColorScheme()

/**
 * The colours the app really uses: the colours of the wallpaper when [dynamic] is on and this phone
 * can do it ([supportsDynamicColor]), the Elnor colours in every other case. It never fails: a phone
 * that cannot (or a [context] of null) gets the Elnor colours.
 */
fun elnorColorScheme(dark: Boolean, context: Context?, dynamic: Boolean): ColorScheme =
    if (context != null && dynamicColorActive(dynamic, Build.VERSION.SDK_INT)) {
        wallpaperColorScheme(context, dark)
    } else {
        elnorColorScheme(dark)
    }

/**
 * Tells the screens that the wallpaper colours are in use. A few parts of Elnor have their own
 * fixed colours (the pastel note cards, the dark bottom bar). With the wallpaper colours they take
 * their colours from the theme instead, so they do not clash with it.
 */
val LocalDynamicColor = staticCompositionLocalOf { false }

/**
 * Makes the window itself match the theme: the colour behind the app (seen behind the keyboard
 * and while the screen changes) and the icons of the status and navigation bars.
 * Used at launch (MainActivity) and every time the theme changes (ElnorTheme).
 * [background] is the page colour of the theme in use; it is the Elnor one when nothing is given.
 */
internal fun applyWindowTheme(
    window: Window,
    view: View,
    dark: Boolean,
    background: Color = elnorColorScheme(dark).background
) {
    window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
    val controller = WindowCompat.getInsetsController(window, view)
    controller.isAppearanceLightStatusBars = !dark
    controller.isAppearanceLightNavigationBars = !dark
}

/**
 * [fontScale] is the factor of the chosen font size ([FontSizeChoice.scale], Settings > Appearance
 * > Font size). 1 is the normal size.
 * [dynamicColor] asks for the colours of the wallpaper (Settings > Appearance > Dynamic colour). It
 * only has an effect on Android 12 and newer.
 */
@Composable
fun ElnorTheme(
    darkTheme: Boolean,
    fontScale: Float = 1f,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // Never in the Android Studio preview, which has no wallpaper colours
    val useDynamic = dynamicColor && !LocalView.current.isInEditMode &&
        dynamicColorActive(true, Build.VERSION.SDK_INT)
    // The wallpaper colours are read again when the phone's theme or wallpaper changes (the
    // activity is recreated then), and when the switch or the light/dark mode changes
    val colors = remember(darkTheme, useDynamic, context) {
        elnorColorScheme(darkTheme, context, useDynamic)
    }
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
            if (window != null) applyWindowTheme(window, view, darkTheme, colors.background)
        }
    }

    CompositionLocalProvider(LocalDynamicColor provides useDynamic) {
        MaterialTheme(
            colorScheme = colors,
            shapes = shapes,
            typography = typography,
            content = content
        )
    }
}
