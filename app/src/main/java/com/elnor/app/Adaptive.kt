package com.elnor.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ------------------------- Window size classes -------------------------
// The same breakpoints as Material's WindowSizeClass (width 600 / 840 dp, height 480 / 900 dp),
// but plain Kotlin, so they can be unit-tested and need no extra library.
// The numbers come from the activity's own configuration, so they follow rotation, split
// screen, window resize and foldables.

enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

enum class HeightClass { COMPACT, MEDIUM, EXPANDED }

fun widthClassOf(widthDp: Int): WidthClass = when {
    widthDp < 600 -> WidthClass.COMPACT
    widthDp < 840 -> WidthClass.MEDIUM
    else -> WidthClass.EXPANDED
}

fun heightClassOf(heightDp: Int): HeightClass = when {
    heightDp < 480 -> HeightClass.COMPACT
    heightDp < 900 -> HeightClass.MEDIUM
    else -> HeightClass.EXPANDED
}

/** The size of the window the app is drawn in, in dp. */
data class WindowClass(val widthDp: Int, val heightDp: Int) {
    val width: WidthClass get() = widthClassOf(widthDp)
    val height: HeightClass get() = heightClassOf(heightDp)

    /** A phone held sideways: very little height, so every extra bar costs a lot. */
    val compactHeight: Boolean get() = height == HeightClass.COMPACT
}

/** Reads the size of the window from the activity's configuration. */
@Composable
fun rememberWindowClass(): WindowClass {
    val configuration = LocalConfiguration.current
    return WindowClass(configuration.screenWidthDp, configuration.screenHeightDp)
}

/**
 * The size of the window, worked out once in ElnorRoot ([rememberWindowClass]) and handed to every
 * screen below it. A screen reads it with `LocalWindowClass.current`. The value before a root
 * provides one is a normal phone in portrait, so a preview or a test still draws something sensible.
 */
val LocalWindowClass = compositionLocalOf { WindowClass(widthDp = 360, heightDp = 800) }

// ------------------------- Widths -------------------------

/** The note list / grid never gets wider than this, so cards do not stretch across a tablet. */
val HomeMaxWidth: Dp = 840.dp

/** Text screens (editor, settings, tasks, trash) stay this wide: easy line length to read. */
val ReadingMaxWidth: Dp = 720.dp

/** The note list when it sits left of the editor (two-pane layout). */
val TwoPaneListWidth: Dp = 360.dp

/**
 * Note list and editor side by side: a wide window (tablet, open fold, big window) that is also
 * tall enough. A phone held sideways is wide but short, so it keeps the one-page layout.
 */
fun isTwoPane(window: WindowClass): Boolean =
    window.width == WidthClass.EXPANDED && !window.compactHeight

/** Width of the navigation rail (Material's own size). */
val NavigationRailWidth: Dp = 80.dp

/**
 * Tabs on the left edge (a rail) instead of the floating bottom bar: only where the note list and
 * the editor are side by side (a tablet or an open foldable, see [isTwoPane]). Every phone, also
 * one held sideways, and a small window keep the floating bottom bar.
 */
fun showsNavigationRail(window: WindowClass): Boolean = isTwoPane(window)

/** The floating bottom bar (tabs and "+") on big screens. */
val BottomBarMaxWidth: Dp = 520.dp

/** Home-screen side padding, left and right (dp). */
private const val HOME_SIDE_PADDING_DP = 20

/** A note card in the grid is at least about this wide (dp), spacing included. */
private const val GRID_MIN_CARD_DP = 170

/**
 * How many columns the notes grid has for a window [widthDp] wide. A phone in portrait keeps the
 * two columns it always had. Wider windows get more: landscape phone 4, tablet 4 to 5.
 */
fun gridColumnCount(widthDp: Int): Int {
    val content = minOf(widthDp, HomeMaxWidth.value.toInt()) - 2 * HOME_SIDE_PADDING_DP
    return maxOf(2, content / GRID_MIN_CARD_DP)
}

/** At most [max] wide and centred; on a phone it is simply the full width. */
fun Modifier.centeredMaxWidth(max: Dp): Modifier =
    this.wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = max)
        .fillMaxWidth()

/** At most [max] wide, fills that width. For a row that is already centred by its parent. */
fun Modifier.limitedWidth(max: Dp): Modifier =
    this.widthIn(max = max).fillMaxWidth()
