package com.elnor.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The "Undo" and "Redo" icons of the note page: an arrow that turns back, drawn with a line.
 * The core icon set that the app uses has no such icons, and the extended set is very large, so
 * these two small icons are drawn here. Redo is Undo turned around (left to right).
 */
val UndoIcon: ImageVector by lazy { turnBackArrow(name = "Undo", mirrored = false) }

val RedoIcon: ImageVector by lazy { turnBackArrow(name = "Redo", mirrored = true) }

private fun turnBackArrow(name: String, mirrored: Boolean): ImageVector {
    // x of a point, turned around the middle of the 24 x 24 icon for Redo
    fun x(value: Float) = if (mirrored) 24f - value else value

    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // the arrow head
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(x(8f), 4f)
            lineTo(x(4f), 8f)
            lineTo(x(8f), 12f)
        }
        // the line that goes out, turns around and comes back
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(x(4f), 8f)
            lineTo(x(14f), 8f)
            curveTo(x(18f), 8f, x(20f), 10.5f, x(20f), 14f)
            curveTo(x(20f), 17.5f, x(18f), 20f, x(14f), 20f)
            lineTo(x(8f), 20f)
        }
    }.build()
}
