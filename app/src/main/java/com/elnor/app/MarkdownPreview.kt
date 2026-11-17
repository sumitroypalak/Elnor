package com.elnor.app

/** A piece of the text of a preview line. [bold] pieces are shown in bold letters. */
data class PreviewRun(val text: String, val bold: Boolean)

enum class PreviewKind { BLANK, PARAGRAPH, HEADING1, HEADING2, BULLET, NUMBERED, CHECKLIST }

/**
 * One line of the preview, ready to be drawn.
 *
 * [level] is how deep a list item sits (0 for the first level). [label] is what is drawn before a
 * list item: a bullet sign or the number as it was typed ("3."); empty for every other kind.
 * [runs] is the text without the formatting marks. [links] are the web links, e-mail addresses and
 * phone numbers of that text, and [tags] its tags (#work); their positions are positions in [text]
 * (the runs put together).
 *
 * For a checklist item [checked] tells if the box is ticked, and [lineIndex] is the number of its
 * line in the note (counted from 0), which [MarkdownFormat.toggleCheck] needs to tick it. It is -1
 * for the blocks that are not checklist items.
 */
data class PreviewBlock(
    val kind: PreviewKind,
    val level: Int,
    val label: String,
    val runs: List<PreviewRun>,
    val links: List<LinkSpan>,
    val tags: List<TagSpan> = emptyList(),
    val checked: Boolean = false,
    val lineIndex: Int = -1,
    /** Links to other notes ([[Name]]) in [text]; positions are positions in [text]. */
    val noteLinks: List<NoteLinkSpan> = emptyList()
) {
    /** The text of the line without any mark. */
    val text: String get() = runs.joinToString("") { it.text }
}

/**
 * The reading view of a note: the same text as in the editor, with the formatting marks (** # - 1.)
 * taken away and the lines grouped as headings, list items and paragraphs. Nothing is stored and the
 * note is never changed: the preview is calculated from the text every time it is shown.
 *
 * Rules:
 *  - A line that starts with "#" is a big heading, "##" (or more) a smaller one.
 *  - A line that starts with "- " or "* " is a bullet item, "1. " a numbered item. Spaces in front
 *    of the mark make the item sit one level deeper for every two spaces (at most [MAX_LEVEL]).
 *  - A line that starts with "- [ ] " is a checklist item with an empty box, "- [x] " one with a
 *    ticked box.
 *  - **bold** words are bold, the ** are not shown.
 *  - Blank lines: one blank line stays as a small gap, more than one in a row count as one, and
 *    blank lines at the start and the end of the note are left out.
 *
 * It has no Android code, so it is tested with plain unit tests.
 */
object MarkdownPreview {

    const val MAX_LEVEL = 4

    private val BLANK = PreviewBlock(PreviewKind.BLANK, 0, "", emptyList(), emptyList())

    fun parse(text: String): List<PreviewBlock> {
        if (text.isBlank()) return emptyList()
        val blocks = ArrayList<PreviewBlock>()
        for ((lineIndex, rawLine) in text.split('\n').withIndex()) {
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) {
                // A gap only between two lines, and never two gaps in a row
                if (blocks.isNotEmpty() && blocks.last().kind != PreviewKind.BLANK) blocks.add(BLANK)
                continue
            }
            blocks.add(toBlock(line, lineIndex))
        }
        // The gap after the last line is not needed
        while (blocks.isNotEmpty() && blocks.last().kind == PreviewKind.BLANK) {
            blocks.removeAt(blocks.lastIndex)
        }
        return blocks
    }

    private fun toBlock(line: String, lineIndex: Int): PreviewBlock {
        val block = MarkdownFormat.blockOf(line)
        val runs = MarkdownFormat.boldRuns(block.content.trim()).map { PreviewRun(it.first, it.second) }
        val plain = runs.joinToString("") { it.text }
        val links = LinkDetector.find(plain)
        val tags = TagDetector.find(plain)
        val noteLinks = NoteLinks.find(plain)
        val level = (block.indent / 2).coerceAtMost(MAX_LEVEL)
        return when (block.kind) {
            MarkdownFormat.BlockKind.HEADING1 ->
                PreviewBlock(PreviewKind.HEADING1, 0, "", runs, links, tags, noteLinks = noteLinks)
            MarkdownFormat.BlockKind.HEADING2 ->
                PreviewBlock(PreviewKind.HEADING2, 0, "", runs, links, tags, noteLinks = noteLinks)
            MarkdownFormat.BlockKind.BULLET ->
                PreviewBlock(
                    PreviewKind.BULLET, level, if (level % 2 == 0) "•" else "◦", runs, links, tags,
                    noteLinks = noteLinks
                )
            MarkdownFormat.BlockKind.NUMBERED ->
                PreviewBlock(
                    PreviewKind.NUMBERED, level, block.number + ".", runs, links, tags,
                    noteLinks = noteLinks
                )
            MarkdownFormat.BlockKind.CHECKLIST ->
                PreviewBlock(
                    PreviewKind.CHECKLIST, level, "", runs, links, tags, block.checked, lineIndex, noteLinks
                )
            MarkdownFormat.BlockKind.PARAGRAPH ->
                PreviewBlock(PreviewKind.PARAGRAPH, 0, "", runs, links, tags, noteLinks = noteLinks)
        }
    }
}
