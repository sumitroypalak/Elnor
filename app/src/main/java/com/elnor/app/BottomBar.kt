@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ----------------------------- Bottom bar ----------------------------------

/** The three tabs of the home screen, in the order they appear in the bottom bar. */
internal enum class HomeTab(val label: String, val icon: ImageVector) {
    NOTE("Note", Icons.Default.Edit),
    TASK("Task", Icons.Default.CheckCircle),
    COLLECTION("Collection", Icons.AutoMirrored.Filled.List)
}

/**
 * The motion of the whole bottom bar: quick, and it settles without a visible bounce.
 * Every tab has one animated number from 0 (not selected) to 1 (selected). The highlight, the
 * width of the tab, and the icon and label fades are all calculated from those numbers, so they
 * always move together.
 */
private val BottomBarSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 800f)

/**
 * The colours of the bottom bar. Every colour comes from the theme (with or without the wallpaper
 * colours), and each one is paired with the colour of what is drawn on it, so the icons and names
 * stay clear in both themes:
 *  - the bar and the tabs on it: the card colour with the normal text colour (white bar in the
 *    light theme, dark bar in the dark theme)
 *  - the highlight of the selected tab: a strong blue (the light theme) or the deep blue container
 *    (the dark theme), with its own text colour
 *  - the "New note" button: the accent colour, so the main action stands out in both themes
 */
internal data class BottomBarColors(
    val bar: Color,
    val onBar: Color,
    val highlight: Color,
    val onHighlight: Color,
    val newNote: Color,
    val onNewNote: Color
)

internal fun bottomBarColors(scheme: ColorScheme, dark: Boolean) = BottomBarColors(
    bar = scheme.surface,
    onBar = scheme.onSurface,
    highlight = if (dark) scheme.primaryContainer else scheme.primary,
    onHighlight = if (dark) scheme.onSurface else scheme.onPrimary,
    newNote = scheme.primary,
    onNewNote = scheme.onPrimary
)

/**
 * A pill with the three tabs and, next to it, a separate round "New note" button.
 * One highlight slides to the selected tab. Only the selected tab shows its icon, so all three
 * names fit on a small phone.
 * The pill follows the theme (white in the light theme, dark in the dark theme, see
 * [bottomBarColors]). The "New note" button is in the accent colour in both.
 */
@Composable
internal fun ElnorBottomBar(
    selected: HomeTab,
    dark: Boolean,
    onSelect: (HomeTab) -> Unit,
    onNewNote: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val colors = bottomBarColors(scheme, dark)

    // 1 for the selected tab, 0 for the others. They are read only while measuring and drawing,
    // so the bar does not recompose on every frame of the animation.
    val progress = HomeTab.entries.map { tab ->
        animateFloatAsState(
            targetValue = if (tab == selected) 1f else 0f,
            animationSpec = BottomBarSpring,
            label = "tabProgress"
        )
    }

    // The "New note" button gets a little smaller while it is pressed and springs back
    val newNoteSource = remember { MutableInteractionSource() }
    val newNotePressed by newNoteSource.collectIsPressedAsState()
    val newNoteScale = animateFloatAsState(
        targetValue = if (newNotePressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "newNoteScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = CircleShape,
            color = colors.bar,
            shadowElevation = 6.dp,
            // A white bar on the light page needs a thin edge besides the shadow
            border = if (dark) null else BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            SlidingTabRow(
                progress = progress,
                highlight = colors.highlight,
                modifier = Modifier
                    .padding(6.dp)
                    .selectableGroup()
            ) {
                for ((index, tab) in HomeTab.entries.withIndex()) {
                    BottomBarTab(
                        tab = tab,
                        selected = tab == selected,
                        progress = progress[index],
                        onBar = colors.onBar,
                        onHighlight = colors.onHighlight,
                        onClick = { onSelect(tab) }
                    )
                }
            }
        }

        Surface(
            onClick = onNewNote,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = newNoteScale.value
                    scaleY = newNoteScale.value
                },
            shape = CircleShape,
            color = colors.newNote,
            shadowElevation = 6.dp,
            interactionSource = newNoteSource
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = "New note", tint = colors.onNewNote)
            }
        }
    }
}

/**
 * Lays the tabs out in a row with equal gaps, and draws one highlight behind them.
 * The highlight is the average of the tab boxes, weighted by [progress]. When the selection
 * changes, the weight moves from the old tab to the new one, so the highlight slides and
 * changes its width at the same time as the tabs move. It is placed in the same layout pass
 * as the tabs, so it never lags behind them.
 *
 * The first child is the highlight. The tabs come after it, so they are drawn on top.
 */
@Composable
private fun SlidingTabRow(
    progress: List<State<Float>>,
    highlight: Color,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(highlight)
            )
            tabs()
        }
    ) { measurables, constraints ->
        val tabPlaceables = measurables
            .drop(1)
            .map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val height = tabPlaceables.maxOf { it.height }
        val tabsWidth = tabPlaceables.sumOf { it.width }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else tabsWidth
        val gap = if (tabPlaceables.size > 1) {
            ((width - tabsWidth) / (tabPlaceables.size - 1)).coerceAtLeast(0)
        } else {
            0
        }

        val xs = IntArray(tabPlaceables.size)
        var nextX = 0
        tabPlaceables.forEachIndexed { i, placeable ->
            xs[i] = nextX
            nextX += placeable.width + gap
        }

        var weightSum = 0f
        var highlightX = 0f
        var highlightWidth = 0f
        tabPlaceables.forEachIndexed { i, placeable ->
            val weight = progress[i].value.coerceIn(0f, 1f)
            weightSum += weight
            highlightX += weight * xs[i]
            highlightWidth += weight * placeable.width
        }
        if (weightSum > 0.001f) {
            highlightX /= weightSum
            highlightWidth /= weightSum
        }
        val highlightPlaceable =
            measurables[0].measure(Constraints.fixed(highlightWidth.roundToInt(), height))

        layout(width, height) {
            highlightPlaceable.place(highlightX.roundToInt(), 0)
            tabPlaceables.forEachIndexed { i, placeable -> placeable.place(xs[i], 0) }
        }
    }
}

/**
 * One tab. While it is being selected, the space for its icon opens up, the icon slides in from
 * the left and fades in, and the label gets brighter. All of it follows [progress].
 * The selected tab sits on the highlight, so its icon and name use [onHighlight]; the others sit on
 * the bar and use [onBar]. The change of colour is animated, so it goes along with the highlight.
 */
@Composable
private fun BottomBarTab(
    tab: HomeTab,
    selected: Boolean,
    progress: State<Float>,
    onBar: Color,
    onHighlight: Color,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) onHighlight else onBar,
        animationSpec = Motion.short(),
        label = "tabContentColor"
    )
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(CircleShape)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The width of this box grows from 0 to the icon's width. The icon itself stays on the
        // right edge of the box, so it travels in from the left while the box opens.
        Box(
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val width = (placeable.width * progress.value.coerceIn(0f, 1f)).roundToInt()
                layout(width, placeable.height) {
                    placeable.place(width - placeable.width, 0)
                }
            }
        ) {
            Icon(
                tab.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(20.dp)
                    .graphicsLayer {
                        val p = progress.value.coerceIn(0f, 1f)
                        alpha = p
                        scaleX = 0.6f + 0.4f * p
                        scaleY = 0.6f + 0.4f * p
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
            )
        }
        Text(
            tab.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                alpha = 0.72f + 0.28f * progress.value.coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
        )
    }
}

/** Temporary screen for a tab that has no feature yet. Same look as the empty states. */
@Composable
internal fun HomeTabPlaceholder(
    title: String,
    subtitle: String,
    padding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp)
            .padding(bottom = 80.dp), // centred above the floating bar
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
        )
    }
}
