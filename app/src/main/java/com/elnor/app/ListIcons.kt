package com.elnor.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The bullet-list and numbered-list icons of the formatting bar. The core icon set has no
 * list-format icons, so they are drawn here, in the same line style as UndoIcon.
 */
val BulletListIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BulletList",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        for (y in listOf(6f, 12f, 18f)) {
            // the dot: a round line of zero length
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 3.4f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(5f, y)
                lineTo(5.01f, y)
            }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(10f, y)
                lineTo(20f, y)
            }
        }
    }.build()
}

val NumberedListIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "NumberedList",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        for (y in listOf(6f, 12f, 18f)) {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(11f, y)
                lineTo(20f, y)
            }
        }
        // the small numbers 1, 2, 3 on the left
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4.4f, 5.4f); lineTo(5.9f, 4.2f); lineTo(5.9f, 8f)
            moveTo(4f, 10.9f)
            curveTo(4f, 9.5f, 6.9f, 9.5f, 6.9f, 11.1f)
            curveTo(6.9f, 12.3f, 4.2f, 13f, 4f, 14.2f)
            lineTo(7f, 14.2f)
            moveTo(4f, 16.2f); lineTo(6.8f, 16.2f); lineTo(5.2f, 17.7f)
            curveTo(7.2f, 17.7f, 7.4f, 20.7f, 5.4f, 20.7f)
            curveTo(4.6f, 20.7f, 4.1f, 20.5f, 3.9f, 20f)
        }
    }.build()
}
