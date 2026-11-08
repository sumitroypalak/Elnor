package com.elnor.app

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The tabs of the home screen on the left edge, for a window that is not a phone in portrait
 * (medium and expanded width, see [showsNavigationRail]). It takes the place of the floating
 * bottom bar: the round "+" button is at the top of the rail, the three tabs are under it.
 * It uses the same colours as the bottom bar ([bottomBarColors]), so both follow the theme.
 */
@Composable
internal fun ElnorNavigationRail(
    selected: HomeTab,
    dark: Boolean,
    onSelect: (HomeTab) -> Unit,
    onNewNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = bottomBarColors(MaterialTheme.colorScheme, dark)
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = colors.onBar,
        header = {
            FloatingActionButton(
                onClick = onNewNote,
                containerColor = colors.newNote,
                contentColor = colors.onNewNote
            ) {
                // On the Task tab the "+" button adds a task, not a note
                Icon(
                    Icons.Default.Add,
                    contentDescription = if (selected == HomeTab.TASK) "New task" else "New note"
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    ) {
        for (tab in HomeTab.entries) {
            NavigationRailItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = colors.onHighlight,
                    selectedTextColor = colors.onBar,
                    indicatorColor = colors.highlight,
                    unselectedIconColor = colors.onBar,
                    unselectedTextColor = colors.onBar
                )
            )
        }
    }
}
