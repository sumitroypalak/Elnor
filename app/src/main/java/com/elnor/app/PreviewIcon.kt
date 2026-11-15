package com.elnor.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The "Preview" button of the note page: an eye. Drawn here because the icon set of the app
 * (material-icons-core) has no eye. Tinted by Icon(), same on every phone.
 */
val PreviewIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Preview",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // The outline of the eye
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(2f, 12f)
            curveTo(4.5f, 7.5f, 8f, 5.5f, 12f, 5.5f)
            curveTo(16f, 5.5f, 19.5f, 7.5f, 22f, 12f)
            curveTo(19.5f, 16.5f, 16f, 18.5f, 12f, 18.5f)
            curveTo(8f, 18.5f, 4.5f, 16.5f, 2f, 12f)
            close()
        }
        // The pupil
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 8.75f)
            curveTo(13.79f, 8.75f, 15.25f, 10.21f, 15.25f, 12f)
            curveTo(15.25f, 13.79f, 13.79f, 15.25f, 12f, 15.25f)
            curveTo(10.21f, 15.25f, 8.75f, 13.79f, 8.75f, 12f)
            curveTo(8.75f, 10.21f, 10.21f, 8.75f, 12f, 8.75f)
            close()
        }
    }.build()
}
