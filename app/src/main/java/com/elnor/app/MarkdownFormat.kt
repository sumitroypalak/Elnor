package com.elnor.app

/** A text with the selection (or the cursor, when [selStart] == [selEnd]) that goes with it. */
data class TextEdit(val text: String, val selStart: Int, val selEnd: Int)

/**
 * Basic formatting for notes, written the Markdown way. A note is still plain text: the
 * formatting is only the characters the user (or the toolbar) typed, so search, backup, share,
 * copy and the database need no change.
 *
 *  - Bold:           **bold**
 *  - Heading:        "# Big heading", "## Smaller heading" (at the start of a line)
 *  - Bullet list:    "- item" or "* item"
 *  - Numbered list:  "1. item"
 *
 * The editor shows the text styled (bold letters, big headings, dimmed marks) but keeps the marks
 * visible, so the user always sees what is stored and can edit it. The note list shows the text
 * without the marks ([plainText]).
 *
 * Everything here works on plain strings and has no Android code, so it is tested on the computer.
 */
object MarkdownFormat {

    // ---- Looking at the text (for showing it styled) ----

    enum class Kind { BOLD, HEADING1, HEADING2, MARKER, LIST_MARKER }

    /** [start] (included) to [end] (not included) of the text is shown in the style [kind]. */
    data class Span(val start: Int, val end: Int, val kind: Kind)

    private val HEADING = Regex("""^(#{1,6})[ \t]+""")
    private val BULLET = Regex("""^([ \t]*)[-*][ \t]+""")
    private val NUMBER = Regex("""^([ \t]*)(\d{1,9})\.[ \t]+""")
    private val BOLD = Regex("""\*\*(?=\S)(.+?)(?<=\S)\*\*""")

    /** All the styled parts of [text], line by line. Offsets are positions in [text]. */
    fun parse(text: String): List<Span> {
        if (text.isEmpty()) return emptyList()
        val spans = ArrayList<Span>()
        var lineStart = 0
        while (lineStart <= text.length) {
            var lineEnd = text.indexOf('\n', lineStart)
            if (lineEnd < 0) lineEnd = text.length
            parseLine(text.substring(lineStart, lineEnd), lineStart, spans)
            lineStart = lineEnd + 1
        }
        return spans
    }

    private fun parseLine(line: String, offset: Int, out: MutableList<Span>) {
        if (line.isEmpty()) return

        val heading = HEADING.find(line)
        if (heading != null) {
            val markLength = heading.value.length
            out.add(Span(offset, offset + markLength, Kind.MARKER))
            if (line.length > markLength) {
                val kind = if (heading.groupValues[1].length == 1) Kind.HEADING1 else Kind.HEADING2
                out.add(Span(offset + markLength, offset + line.length, kind))
            }
        } else {
            val bullet = BULLET.find(line)
            val number = if (bullet == null) NUMBER.find(line) else null
            val list = bullet ?: number
            if (list != null) {
                val indent = list.groupValues[1].length
                out.add(Span(offset + indent, offset + list.value.length, Kind.LIST_MARKER))
            }
        }

        for (match in BOLD.findAll(line)) {
            val first = match.range.first
            val endExclusive = match.range.last + 1
            out.add(Span(offset + first, offset + first + 2, Kind.MARKER))
            out.add(Span(offset + first + 2, offset + endExclusive - 2, Kind.BOLD))
            out.add(Span(offset + endExclusive - 2, offset + endExclusive, Kind.MARKER))
        }
    }

    // ---- The toolbar buttons ----

    /**
     * Bold button. With a selection: puts ** around it, or takes them away when it is already
     * bold. Without a selection: bolds the word at the cursor, or, when the cursor is not on a
     * word, puts **** there with the cursor in the middle, ready to type.
     * [selStart] must not be bigger than [selEnd].
     */
    fun toggleBold(text: String, selStart: Int, selEnd: Int): TextEdit {
        var s = selStart.coerceIn(0, text.length)
        var e = selEnd.coerceIn(s, text.length)

        if (s == e) {
            // Nothing selected: use the word (the non-space characters) around the cursor
            var ws = s
            var we = e
            while (ws > 0 && !text[ws - 1].isWhitespace()) ws--
            while (we < text.length && !text[we].isWhitespace()) we++
            if (ws == we) return insertAtCursor(text, s, "****", 2)
            s = ws
            e = we
        } else {
            // "** word **" would not be bold: leave the spaces at the edges of the selection out
            while (s < e && text[s].isWhitespace()) s++
            while (e > s && text[e - 1].isWhitespace()) e--
            if (s == e) return insertAtCursor(text, s, "****", 2)
        }

        val selected = text.substring(s, e)
        return when {
            // The marks are just outside the selection: take them away
            s >= 2 && e + 2 <= text.length && text.startsWith("**", s - 2) && text.startsWith("**", e) ->
                TextEdit(text.removeRange(e, e + 2).removeRange(s - 2, s), s - 2, e - 2)

            // The marks are inside the selection: take them away
            selected.length >= 4 && selected.startsWith("**") && selected.endsWith("**") ->
                TextEdit(
                    text.substring(0, s) + selected.substring(2, selected.length - 2) + text.substring(e),
                    s,
                    e - 4
                )

            else -> TextEdit(text.substring(0, s) + "**" + selected + "**" + text.substring(e), s + 2, e + 2)
        }
    }

    /** Heading button: the line of the cursor (every selected line) goes plain -> # -> ## -> plain. */
    fun cycleHeading(text: String, selStart: Int, selEnd: Int): TextEdit =
        transformLines(text, selStart, selEnd) { lines ->
            val level = lines.firstOrNull { !skipped(it, lines) }
                ?.let { HEADING.find(it)?.groupValues?.get(1)?.length } ?: 0
            // 0 (plain) -> 1, 1 -> 2, 2 or more -> plain
            val next = when (level) {
                0 -> 1
                1 -> 2
                else -> 0
            }
            lines.map { line ->
                if (skipped(line, lines)) {
                    line
                } else {
                    val content = splitLine(line).content
                    if (next == 0) content else "#".repeat(next) + " " + content
                }
            }
        }

    /** Bullet list button: every selected line gets "- ", or loses it when all of them have it. */
    fun toggleBullet(text: String, selStart: Int, selEnd: Int): TextEdit =
        transformLines(text, selStart, selEnd) { lines ->
            val filled = lines.filter { !skipped(it, lines) }
            val remove = filled.isNotEmpty() && filled.all { BULLET.containsMatchIn(it) }
            lines.map { line ->
                if (skipped(line, lines)) {
                    line
                } else {
                    val parts = splitLine(line)
                    if (remove) parts.indent + parts.content else parts.indent + "- " + parts.content
                }
            }
        }

    /** Numbered list button: the selected lines become 1. 2. 3. ... or lose the numbers. */
    fun toggleNumbered(text: String, selStart: Int, selEnd: Int): TextEdit =
        transformLines(text, selStart, selEnd) { lines ->
            val filled = lines.filter { !skipped(it, lines) }
            val remove = filled.isNotEmpty() && filled.all { NUMBER.containsMatchIn(it) }
            var number = 0
            lines.map { line ->
                if (skipped(line, lines)) {
                    line
                } else {
                    val parts = splitLine(line)
                    if (remove) {
                        parts.indent + parts.content
                    } else {
                        number++
                        parts.indent + "$number. " + parts.content
                    }
                }
            }
        }

    // ---- The Enter key inside a list ----

    /**
     * Called when the text changed from [oldText] to [newText] with the cursor at [cursor]. If the
     * user pressed Enter at a line of a list, returns the text to show instead:
     *  - the next line starts with the next mark ("- " or the next number), or
     *  - when the line had a mark and nothing else, Enter ends the list: the mark is removed.
     * Returns null for every other change (typing, deleting, pasting, Enter outside a list).
     */
    fun afterNewline(oldText: String, newText: String, cursor: Int): TextEdit? {
        if (cursor < 1 || cursor > newText.length) return null
        if (newText.length != oldText.length + 1 || newText[cursor - 1] != '\n') return null
        // It must be exactly one inserted line break, nothing else changed
        if (newText.removeRange(cursor - 1, cursor) != oldText) return null

        val lineStart = newText.lastIndexOf('\n', cursor - 2) + 1
        val line = newText.substring(lineStart, cursor - 1)

        val bullet = BULLET.find(line)
        val number = if (bullet == null) NUMBER.find(line) else null
        val match = bullet ?: number ?: return null

        val rest = line.substring(match.value.length)
        if (rest.isBlank()) {
            // An empty item: Enter ends the list. The mark and the new line break go away.
            return TextEdit(newText.removeRange(lineStart, cursor), lineStart, lineStart)
        }

        val indent = match.groupValues[1]
        val mark = if (bullet != null) {
            val symbol = line.substring(indent.length, indent.length + 1)
            "$indent$symbol "
        } else {
            val next = (match.groupValues[2].toLongOrNull() ?: 0L) + 1
            "$indent$next. "
        }
        val text = newText.substring(0, cursor) + mark + newText.substring(cursor)
        return TextEdit(text, cursor + mark.length, cursor + mark.length)
    }

    // ---- Text without the marks (for the note list) ----

    /**
     * [line] as it is shown in the note list: no "#" before a heading, no ** around bold words,
     * and a bullet mark is drawn as "•". Numbers stay.
     */
    fun plainLine(line: String): String {
        var result = line
        val heading = HEADING.find(result)
        if (heading != null) {
            result = result.substring(heading.value.length)
        } else {
            val bullet = BULLET.find(result)
            if (bullet != null) {
                result = bullet.groupValues[1] + "• " + result.substring(bullet.value.length)
            }
        }
        return BOLD.replace(result) { it.groupValues[1] }
    }

    /** [plainLine] for every line of [text]. */
    fun plainText(text: String): String =
        if (text.isEmpty()) text else text.split('\n').joinToString("\n") { plainLine(it) }

    // ---- helpers ----

    private fun insertAtCursor(text: String, at: Int, insert: String, cursorInside: Int): TextEdit =
        TextEdit(
            text.substring(0, at) + insert + text.substring(at),
            at + cursorInside,
            at + cursorInside
        )

    /**
     * Blank lines between other lines are left alone by the buttons. A single blank line (the
     * cursor is on an empty line, or the note is empty) is not: the button starts the list there.
     */
    private fun skipped(line: String, lines: List<String>): Boolean = line.isBlank() && lines.size > 1

    /** A line split into its leading spaces, and its text without a heading / list mark. */
    private class LineParts(val indent: String, val content: String)

    private fun splitLine(line: String): LineParts {
        HEADING.find(line)?.let { return LineParts("", line.substring(it.value.length)) }
        BULLET.find(line)?.let { return LineParts(it.groupValues[1], line.substring(it.value.length)) }
        NUMBER.find(line)?.let { return LineParts(it.groupValues[1], line.substring(it.value.length)) }
        val indentLength = line.length - line.trimStart(' ', '\t').length
        return LineParts(line.substring(0, indentLength), line.substring(indentLength))
    }

    /**
     * Rewrites the lines that the selection touches with [change] (it gets those lines and returns
     * the same number of new lines). The cursor stays on its line; a selection becomes the whole
     * changed block.
     */
    private fun transformLines(
        text: String,
        selStart: Int,
        selEnd: Int,
        change: (List<String>) -> List<String>
    ): TextEdit {
        val s = selStart.coerceIn(0, text.length)
        val e = selEnd.coerceIn(s, text.length)
        // A selection that ends right after a line break does not include the next line
        val lastPos = if (e > s && text[e - 1] == '\n') e - 1 else e

        val blockStart = text.lastIndexOf('\n', s - 1) + 1
        val blockEndFound = text.indexOf('\n', lastPos)
        val blockEnd = if (blockEndFound < 0) text.length else blockEndFound

        val oldLines = text.substring(blockStart, blockEnd).split('\n')
        val newLines = change(oldLines)
        val newBlock = newLines.joinToString("\n")
        val newText = text.substring(0, blockStart) + newBlock + text.substring(blockEnd)

        if (s != e) return TextEdit(newText, blockStart, blockStart + newBlock.length)

        // The cursor: same place in its line, moved by what changed in front of it
        val lineIndex = text.substring(blockStart, s).count { it == '\n' }
        val oldLine = oldLines[lineIndex]
        val newLine = newLines[lineIndex]
        val newLineStart = blockStart + newLines.take(lineIndex).sumOf { it.length + 1 }
        val column = s - (blockStart + oldLines.take(lineIndex).sumOf { it.length + 1 })
        val moved = (column + newLine.length - oldLine.length).coerceIn(0, newLine.length)
        return TextEdit(newText, newLineStart + moved, newLineStart + moved)
    }
}
