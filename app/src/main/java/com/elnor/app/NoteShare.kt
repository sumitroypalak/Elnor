package com.elnor.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * The subject for the apps that have one (an e-mail, for example): the title of the note.
 * Null when the note has no title, so no empty subject is sent.
 */
fun shareSubjectOf(title: String): String? = title.trim().ifEmpty { null }

/**
 * Opens Android's own "Share" sheet with the note as plain text, so the user can send it to any
 * app that takes text (WhatsApp, e-mail, messages, another notes app ...). The text is the same
 * as for "Copy": the title, an empty line, then the text. Nothing is saved or changed.
 *
 * A failure (for example a note that is far too long to hand to another app) is reported with a
 * message and never crashes the app.
 */
fun shareNote(context: Context, title: String, body: String) {
    val text = noteTextForCopy(title, body)
    if (text.isEmpty()) return

    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        shareSubjectOf(title)?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    val chooser = Intent.createChooser(send, "Share note").apply {
        // Only needed when the context is not an Activity
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val started = runCatching { context.startActivity(chooser) }.isSuccess
    if (!started) {
        Toast.makeText(context.applicationContext, "Could not share the note", Toast.LENGTH_SHORT).show()
    }
}
