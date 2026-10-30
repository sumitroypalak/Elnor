package com.elnor.app

/** A new note made from text that another app sent to Elnor. See [buildIncomingNote]. */
data class IncomingNote(val title: String, val body: String)

/**
 * Turns the text another app shares (or the text the user selected in another app) into a note.
 *
 * - [subject] is the optional subject of a share (a browser sends the page title). It becomes the
 *   note's title. Spaces and line breaks at the start and the end are removed, like everywhere else.
 * - [text] becomes the body, trimmed the same way. Nothing inside the text is changed.
 * - When the subject is the same as the text, the title is left empty so the text is not saved twice.
 * - Returns null when there is nothing to save (both are missing or blank).
 *
 * No Android classes here, so this is tested with plain unit tests.
 */
fun buildIncomingNote(subject: CharSequence?, text: CharSequence?): IncomingNote? {
    val body = text?.toString()?.trim().orEmpty()
    var title = subject?.toString()?.trim().orEmpty()
    if (title == body) title = ""
    if (title.isEmpty() && body.isEmpty()) return null
    return IncomingNote(title = title, body = body)
}
