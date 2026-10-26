@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// ----------------------------- Side menu -----------------------------------

/**
 * The side menu with Settings, Archive and Trash. The menu button is at the top right, so the menu also
 * comes in from the right (the end of the screen). ModalNavigationDrawer always opens from the
 * start side, so its layout direction is turned around, and turned back for everything inside.
 * Tapping outside the menu, swiping it away or pressing Back closes it.
 */
@Composable
internal fun EndDrawer(
    drawerState: DrawerState,
    onOpenSettings: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenTrash: () -> Unit,
    content: @Composable () -> Unit
) {
    val direction = LocalLayoutDirection.current
    val flipped = if (direction == LayoutDirection.Ltr) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides flipped) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    ModalDrawerSheet(
                        // rounded on the side that faces the screen, like the rest of the app
                        drawerShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        drawerTonalElevation = 0.dp // a flat surface, no colour tint
                    ) {
                        // A large app name at the top, then the entries as outlined icon + text rows
                        Text(
                            "Elnor",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 24.dp)
                        )
                        DrawerItem(
                            label = "Settings",
                            icon = Icons.Outlined.Settings,
                            onClick = onOpenSettings
                        )
                        DrawerItem(
                            label = "Archive",
                            icon = ArchiveIcon,
                            onClick = onOpenArchive
                        )
                        DrawerItem(
                            label = "Trash",
                            icon = Icons.Outlined.Delete,
                            onClick = onOpenTrash
                        )
                    }
                }
            },
            content = {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    content()
                }
            }
        )
    }
}

/**
 * One row of the side menu: an outlined icon and the text, in a tall row with fully rounded
 * ends. The row gets a strong, filled highlight (the "pill") while it is pressed.
 */
@Composable
private fun DrawerItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = MaterialTheme.colorScheme

    NavigationDrawerItem(
        label = {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        },
        selected = pressed,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .height(52.dp),
        shape = CircleShape,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = colors.primaryContainer,
            unselectedContainerColor = Color.Transparent,
            selectedIconColor = colors.onPrimaryContainer,
            unselectedIconColor = colors.onSurface,
            selectedTextColor = colors.onPrimaryContainer,
            unselectedTextColor = colors.onSurface
        ),
        interactionSource = interaction
    )
}
