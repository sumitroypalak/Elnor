package com.elnor.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The text that the "Copy" button of the editor puts on the clipboard: the title, an empty line,
 * then the body. A part that is empty is left out. Spaces at the start and the end of each part
 * are removed, like when a note is saved. Returns "" when there is nothing to copy.
 */
fun noteTextForCopy(title: String, body: String): String {
    val t = title.trim()
    val b = body.trim()
    return when {
        t.isEmpty() -> b
        b.isEmpty() -> t
        else -> "$t\n\n$b"
    }
}

/**
 * Puts [text] on the clipboard. Android 13 and newer shows its own "Copied" message. On older
 * versions a short message is shown here. A failure (for example a text that is far too big for
 * the clipboard) is reported with a message and never crashes the app.
 */
fun copyTextToClipboard(context: Context, text: String) {
    val appContext = context.applicationContext
    val copied = runCatching {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Elnor note", text))
    }.isSuccess

    if (!copied) {
        Toast.makeText(appContext, "Could not copy the note", Toast.LENGTH_SHORT).show()
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(appContext, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Two sheets of paper, for the "Copy" button. The core icon set that the app uses has no copy
 * icon, and the extended set is very large, so this one small icon is drawn here.
 */
val CopyIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Copy",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            // The sheet behind: only its top and left edge show (an L shape)
            moveTo(2f, 1f); lineTo(16f, 1f); lineTo(16f, 3f); lineTo(4f, 3f); lineTo(4f, 17f); lineTo(2f, 17f); close()
            // The sheet in front: an outer rectangle with an inner one cut out (a frame)
            moveTo(6f, 5f); lineTo(21f, 5f); lineTo(21f, 23f); lineTo(6f, 23f); close()
            moveTo(8f, 7f); lineTo(19f, 7f); lineTo(19f, 21f); lineTo(8f, 21f); close()
        }
    }.build()
}
