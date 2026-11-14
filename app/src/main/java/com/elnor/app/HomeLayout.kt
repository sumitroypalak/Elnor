package com.elnor.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// How the home screen puts its parts on the screen: the navigation rail, the one-page layout and
// the two-pane layout (split out of HomeScreen.kt, nothing changed in how it works).

/**
 * The rail (tablet / open foldable), then the note list and the editor.
 *  - Wide window ([twoPane]): the list stays on the left, the editor (or "Select a note") on the right.
 *  - Otherwise: one page. The editor rises a little and fades in over the list, and fades away when
 *    it closes.
 * [listPane] and [editorPane] draw the two parts; the home screen owns what is in them.
 */
@Composable
internal fun HomeLayout(
    bgColor: Color,
    dark: Boolean,
    showRail: Boolean,
    twoPane: Boolean,
    editorOpen: Boolean,
    homeTab: HomeTab,
    onSelectTab: (HomeTab) -> Unit,
    onNewNote: () -> Unit,
    listPane: @Composable () -> Unit,
    editorPane: @Composable () -> Unit
) {
    Row(Modifier.fillMaxSize().background(bgColor)) {
        // The tabs and the round "+" button are on the left edge, before the list and the editor
        if (showRail) {
            ElnorNavigationRail(
                selected = homeTab,
                dark = dark,
                onSelect = onSelectTab,
                onNewNote = onNewNote
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                // The rail already keeps clear of a bar on the left edge
                .then(
                    if (showRail) {
                        Modifier.consumeWindowInsets(
                            WindowInsets.systemBars.only(WindowInsetsSides.Start)
                        )
                    } else Modifier
                )
        ) {
            if (twoPane) {
                Row(Modifier.fillMaxSize().background(bgColor)) {
                    Box(Modifier.width(TwoPaneListWidth).fillMaxHeight()) { listPane() }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Box(Modifier.weight(1f).fillMaxHeight().background(bgColor)) {
                        if (editorOpen) {
                            editorPane()
                        } else {
                            HomeTabPlaceholder(
                                title = "Select a note",
                                subtitle = "Or tap + to write a new one.",
                                padding = PaddingValues()
                            )
                        }
                    }
                }
            } else {
                // The editor page rises a little and fades in over the list; when it closes it fades away
                // and the list shows through. The page colour behind keeps the two from showing through
                // each other.
                AnimatedContent(
                    targetState = editorOpen,
                    modifier = Modifier.fillMaxSize().background(bgColor),
                    transitionSpec = { Motion.page(opening = targetState) },
                    label = "homeEditor"
                ) { showEditor ->
                    // A page that is already sliding out must not answer Back again (the editor would save
                    // and close twice). Registered after the page below, so it is asked first.
                    val leaving = transition.targetState == EnterExitState.PostExit
                    if (showEditor) {
                        editorPane()
                        BackHandler(enabled = leaving) { }
                    } else {
                        listPane()
                    }
                }
            }
        }
    }
}
