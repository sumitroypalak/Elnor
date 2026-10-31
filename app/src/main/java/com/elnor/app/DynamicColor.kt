package com.elnor.app

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

/**
 * Dynamic colour ("Material You"): the colours of the app follow the wallpaper of the phone. It is
 * a switch in Settings > Appearance > Dynamic colour. Only Android 12 (API 31) and newer have the
 * wallpaper colours, so older phones do not see the switch and always use the Elnor colours.
 *
 * It is OFF by default, so an update never changes the look of the app by itself.
 */

/** The first Android version with wallpaper colours (Android 12). */
const val DYNAMIC_COLOR_MIN_SDK = 31

/** True when a phone with this Android version ([sdkInt]) can use dynamic colour. */
fun supportsDynamicColor(sdkInt: Int): Boolean = sdkInt >= DYNAMIC_COLOR_MIN_SDK

/**
 * True when the app must really use the wallpaper colours: the user turned it on ([enabled]) AND
 * the phone can do it. A saved "on" on a phone that cannot (for example the settings file came
 * from another phone) is ignored, so the app never breaks.
 */
fun dynamicColorActive(enabled: Boolean, sdkInt: Int): Boolean =
    enabled && supportsDynamicColor(sdkInt)

/** [supportsDynamicColor] for this phone. */
fun isDynamicColorSupported(): Boolean = supportsDynamicColor(Build.VERSION.SDK_INT)

private const val DYNAMIC_SETTINGS_FILE = "elnor_settings"
private const val KEY_DYNAMIC_COLOR = "dynamic_color"

/** The saved switch. Nothing saved means off. This is the user's choice, see [dynamicColorActive]. */
internal fun loadDynamicColor(context: Context): Boolean =
    context.applicationContext
        .getSharedPreferences(DYNAMIC_SETTINGS_FILE, Context.MODE_PRIVATE)
        .getBoolean(KEY_DYNAMIC_COLOR, false)

internal fun saveDynamicColor(context: Context, value: Boolean) {
    context.applicationContext
        .getSharedPreferences(DYNAMIC_SETTINGS_FILE, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_DYNAMIC_COLOR, value)
        .apply()
}

/**
 * The colours of the wallpaper, arranged the way Elnor needs them. Call it only when
 * [supportsDynamicColor] is true for this phone.
 *
 * The phone's own scheme has the same colour for the page ([ColorScheme.background]) and for the
 * cards on it ([ColorScheme.surface]), so the cards would melt into the page (Elnor draws cards
 * without a shadow). Like the Elnor colours do, the cards get a step of their own:
 *  - light: the page is the soft tint, the cards are the lightest colour (white-ish)
 *  - dark: the page is the darkest colour, the cards are one step lighter
 */
@RequiresApi(DYNAMIC_COLOR_MIN_SDK)
internal fun wallpaperColorScheme(context: Context, dark: Boolean): ColorScheme =
    if (dark) {
        val scheme = dynamicDarkColorScheme(context)
        scheme.copy(surface = scheme.surfaceContainer)
    } else {
        val scheme = dynamicLightColorScheme(context)
        scheme.copy(surface = scheme.surfaceContainerLowest)
    }
