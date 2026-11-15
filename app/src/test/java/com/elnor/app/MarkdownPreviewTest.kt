package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPreviewTest {

    private fun kinds(text: String) = MarkdownPreview.parse(text).map { it.kind }

    private fun texts(text: String) = MarkdownPreview.parse(text).map { it.text }

    // ---- nothing to show ----

    @Test
    fun empty_and_blank_text_give_no_blocks() {
        assertTrue(MarkdownPreview.parse("").isEmpty())
        assertTrue(MarkdownPreview.parse("  \n \n\t").isEmpty())
    }

    // ---- headings ----

    @Test
    fun headings_lose_their_marks() {
        val blocks = MarkdownPreview.parse("# Big\n## Small\n### Smaller")
        assertEquals(
            listOf(PreviewKind.HEADING1, PreviewKind.HEADING2, PreviewKind.HEADING2),
            blocks.map { it.kind }
        )
        assertEquals(listOf("Big", "Small", "Smaller"), blocks.map { it.text })
    }

    @Test
    fun a_heading_without_text_is_an_empty_heading() {
        val blocks = MarkdownPreview.parse("# ")
        assertEquals(listOf(PreviewKind.HEADING1), blocks.map { it.kind })
        assertEquals("", blocks[0].text)
    }

    @Test
    fun a_hash_without_a_space_is_plain_text() {
        assertEquals(listOf(PreviewKind.PARAGRAPH), kinds("#hashtag"))
        assertEquals(listOf("#hashtag"), texts("#hashtag"))
    }

    // ---- lists ----

    @Test
    fun bullet_items_get_a_bullet_label() {
        val blocks = MarkdownPreview.parse("- one\n* two")
        assertEquals(listOf(PreviewKind.BULLET, PreviewKind.BULLET), blocks.map { it.kind })
        assertEquals(listOf("one", "two"), blocks.map { it.text })
        assertEquals(listOf("•", "•"), blocks.map { it.label })
        assertEquals(listOf(0, 0), blocks.map { it.level })
    }

    @Test
    fun numbered_items_keep_the_number_that_was_typed() {
        val blocks = MarkdownPreview.parse("1. one\n3. three\n10. ten")
        assertEquals(listOf("1.", "3.", "10."), blocks.map { it.label })
        assertEquals(listOf("one", "three", "ten"), blocks.map { it.text })
        assertTrue(blocks.all { it.kind == PreviewKind.NUMBERED })
    }

    @Test
    fun spaces_before_the_mark_make_a_deeper_level() {
        val blocks = MarkdownPreview.parse("- a\n  - b\n    - c")
        assertEquals(listOf(0, 1, 2), blocks.map { it.level })
        assertEquals(listOf("•", "◦", "•"), blocks.map { it.label })
    }

    @Test
    fun a_tab_counts_as_two_levels_and_the_level_has_a_limit() {
        assertEquals(2, MarkdownPreview.parse("\t- a")[0].level)
        assertEquals(MarkdownPreview.MAX_LEVEL, MarkdownPreview.parse(" ".repeat(40) + "- a")[0].level)
    }

    @Test
    fun an_empty_list_item_is_still_an_item() {
        val blocks = MarkdownPreview.parse("- ")
        assertEquals(listOf(PreviewKind.BULLET), blocks.map { it.kind })
        assertEquals("", blocks[0].text)
    }

    @Test
    fun bold_text_at_the_start_of_a_line_is_not_a_bullet() {
        assertEquals(listOf(PreviewKind.PARAGRAPH), kinds("**bold** text"))
    }

    // ---- bold ----

    @Test
    fun bold_words_become_bold_runs_without_the_stars() {
        val block = MarkdownPreview.parse("a **b** c")[0]
        assertEquals(
            listOf(PreviewRun("a ", false), PreviewRun("b", true), PreviewRun(" c", false)),
            block.runs
        )
        assertEquals("a b c", block.text)
    }

    @Test
    fun stars_with_spaces_inside_are_not_bold() {
        val block = MarkdownPreview.parse("** not bold **")[0]
        assertEquals(listOf(PreviewRun("** not bold **", false)), block.runs)
    }

    @Test
    fun bold_works_inside_headings_and_list_items() {
        assertEquals(listOf(PreviewRun("Big ", false), PreviewRun("one", true)), MarkdownPreview.parse("# Big **one**")[0].runs)
        assertEquals(listOf(PreviewRun("x", true)), MarkdownPreview.parse("- **x**")[0].runs)
    }

    // ---- blank lines ----

    @Test
    fun blank_lines_are_one_small_gap_and_not_at_the_edges() {
        assertEquals(
            listOf(PreviewKind.PARAGRAPH, PreviewKind.BLANK, PreviewKind.PARAGRAPH),
            kinds("\n\na\n\n\n\nb\n\n")
        )
    }

    @Test
    fun windows_line_breaks_leave_no_stray_characters() {
        assertEquals(listOf("a", "b"), texts("a\r\nb"))
    }

    // ---- links ----

    @Test
    fun links_are_found_in_the_text_without_marks() {
        val block = MarkdownPreview.parse("see https://example.com now")[0]
        assertEquals(1, block.links.size)
        val link = block.links[0]
        assertEquals(LinkType.WEB, link.type)
        assertEquals("https://example.com", block.text.substring(link.start, link.end))
    }

    @Test
    fun link_positions_follow_the_text_after_the_stars_are_removed() {
        val block = MarkdownPreview.parse("**hi** https://example.com")[0]
        assertEquals("hi https://example.com", block.text)
        val link = block.links.single()
        assertEquals("https://example.com", block.text.substring(link.start, link.end))
    }

    // ---- the helpers of MarkdownFormat the preview uses ----

    @Test
    fun blockOf_follows_the_same_rules_as_parse() {
        assertEquals(
            MarkdownFormat.Block(MarkdownFormat.BlockKind.HEADING1, 0, null, "Title"),
            MarkdownFormat.blockOf("# Title")
        )
        assertEquals(
            MarkdownFormat.Block(MarkdownFormat.BlockKind.BULLET, 2, null, "item"),
            MarkdownFormat.blockOf("  - item")
        )
        assertEquals(
            MarkdownFormat.Block(MarkdownFormat.BlockKind.NUMBERED, 0, "12", "twelve"),
            MarkdownFormat.blockOf("12. twelve")
        )
        assertEquals(
            MarkdownFormat.Block(MarkdownFormat.BlockKind.PARAGRAPH, 0, null, "just text"),
            MarkdownFormat.blockOf("just text")
        )
    }

    @Test
    fun boldRuns_keeps_every_letter_except_the_stars() {
        assertEquals(
            listOf("x " to false, "y" to true, " z " to false, "w" to true),
            MarkdownFormat.boldRuns("x **y** z **w**")
        )
        assertEquals(listOf("plain" to false), MarkdownFormat.boldRuns("plain"))
        assertTrue(MarkdownFormat.boldRuns("").isEmpty())
    }

    @Test
    fun bengali_text_is_kept_as_it_is() {
        val block = MarkdownPreview.parse("# শিরোনাম\n- **আজকের** কাজ")
        assertEquals(listOf("শিরোনাম", "আজকের কাজ"), block.map { it.text })
    }
}
