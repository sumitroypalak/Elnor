package com.elnor.app

import com.elnor.app.MarkdownFormat.Kind
import com.elnor.app.MarkdownFormat.Span
import com.elnor.app.MarkdownFormat.checklistProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistTest {

    private fun edit(text: String, start: Int, end: Int) = TextEdit(text, start, end)

    // ---- how it is shown in the editor ----

    @Test
    fun the_box_is_part_of_the_list_marker() {
        assertEquals(listOf(Span(0, 6, Kind.LIST_MARKER)), MarkdownFormat.parse("- [ ] milk"))
        assertEquals(listOf(Span(0, 6, Kind.LIST_MARKER)), MarkdownFormat.parse("- [x] milk"))
        assertEquals(listOf(Span(2, 8, Kind.LIST_MARKER)), MarkdownFormat.parse("  * [X] milk"))
    }

    @Test
    fun a_box_without_a_space_after_it_is_a_normal_bullet() {
        assertEquals(listOf(Span(0, 2, Kind.LIST_MARKER)), MarkdownFormat.parse("- [x]milk"))
        assertEquals(listOf(Span(0, 2, Kind.LIST_MARKER)), MarkdownFormat.parse("- [y] milk"))
        assertEquals(listOf(Span(0, 2, Kind.LIST_MARKER)), MarkdownFormat.parse("- []"))
    }

    // ---- the checklist button ----

    @Test
    fun the_button_makes_lines_checklist_items() {
        assertEquals(edit("- [ ] a\n- [ ] b", 0, 15), MarkdownFormat.toggleChecklist("a\nb", 0, 3))
        assertEquals(edit("- [ ] ", 6, 6), MarkdownFormat.toggleChecklist("", 0, 0))
    }

    @Test
    fun the_button_turns_a_bullet_or_number_into_a_box() {
        // the cursor was at the end of the line and stays at the end of it
        assertEquals(edit("- [ ] a", 7, 7), MarkdownFormat.toggleChecklist("- a", 3, 3))
        assertEquals(edit("- [ ] a", 7, 7), MarkdownFormat.toggleChecklist("1. a", 4, 4))
        assertEquals(edit("  - [ ] a", 9, 9), MarkdownFormat.toggleChecklist("  - a", 5, 5))
    }

    @Test
    fun the_button_keeps_ticked_items_and_adds_boxes_to_the_rest() {
        assertEquals(
            edit("- [x] a\n- [ ] b", 0, 15),
            MarkdownFormat.toggleChecklist("- [x] a\nb", 0, 9)
        )
    }

    @Test
    fun the_button_on_a_checklist_takes_it_away() {
        assertEquals(edit("a\nb", 0, 3), MarkdownFormat.toggleChecklist("- [ ] a\n- [x] b", 0, 15))
    }

    @Test
    fun the_button_on_a_heading_keeps_the_words() {
        assertEquals(edit("- [ ] Title", 11, 11), MarkdownFormat.toggleChecklist("# Title", 7, 7))
    }

    @Test
    fun the_bullet_button_on_a_checklist_takes_the_boxes_away() {
        assertEquals(edit("a\nb", 0, 3), MarkdownFormat.toggleBullet("- [ ] a\n- [x] b", 0, 15))
    }

    // ---- ticking ----

    @Test
    fun toggleCheck_ticks_and_unticks_one_line() {
        val text = "title\n- [ ] a\n- [x] b\n- [X] c"
        assertEquals("title\n- [x] a\n- [x] b\n- [X] c", MarkdownFormat.toggleCheck(text, 1))
        assertEquals("title\n- [ ] a\n- [ ] b\n- [X] c", MarkdownFormat.toggleCheck(text, 2))
        assertEquals("title\n- [ ] a\n- [x] b\n- [ ] c", MarkdownFormat.toggleCheck(text, 3))
    }

    @Test
    fun toggleCheck_keeps_the_length_and_the_text_around() {
        val text = "- [ ] buy **milk**, #shop"
        val result = MarkdownFormat.toggleCheck(text, 0)!!
        assertEquals(text.length, result.length)
        assertEquals("- [x] buy **milk**, #shop", result)
        assertEquals(text, MarkdownFormat.toggleCheck(result, 0))
    }

    @Test
    fun toggleCheck_refuses_lines_that_are_not_checklist_items() {
        assertNull(MarkdownFormat.toggleCheck("- a\nb", 0))
        assertNull(MarkdownFormat.toggleCheck("- a\nb", 1))
        assertNull(MarkdownFormat.toggleCheck("- [ ] a", 1)) // there is no line 1
        assertNull(MarkdownFormat.toggleCheck("- [ ] a", -1))
        assertNull(MarkdownFormat.toggleCheck("", 0))
    }

    @Test
    fun toggleCheck_works_on_an_empty_item_and_with_windows_line_ends() {
        assertEquals("- [x]", MarkdownFormat.toggleCheck("- [ ]", 0))
        assertEquals("- [x] a\r\n- [ ] b", MarkdownFormat.toggleCheck("- [ ] a\r\n- [ ] b", 0))
    }

    // ---- Enter ----

    @Test
    fun enter_on_an_item_starts_a_new_empty_box() {
        assertEquals(
            edit("- [ ] one\n- [ ] ", 16, 16),
            MarkdownFormat.afterNewline("- [ ] one", "- [ ] one\n", 10)
        )
    }

    @Test
    fun enter_on_a_ticked_item_starts_an_empty_box_not_a_ticked_one() {
        assertEquals(
            edit("- [x] done\n- [ ] ", 17, 17),
            MarkdownFormat.afterNewline("- [x] done", "- [x] done\n", 11)
        )
    }

    @Test
    fun enter_keeps_the_indent_and_the_star() {
        assertEquals(
            edit("  * [ ] a\n  * [ ] ", 18, 18),
            MarkdownFormat.afterNewline("  * [ ] a", "  * [ ] a\n", 10)
        )
    }

    @Test
    fun enter_on_an_empty_box_ends_the_list() {
        assertEquals(
            edit("- [ ] one\n", 10, 10),
            MarkdownFormat.afterNewline("- [ ] one\n- [ ] ", "- [ ] one\n- [ ] \n", 17)
        )
        assertEquals(
            edit("- [ ] one\n", 10, 10),
            MarkdownFormat.afterNewline("- [ ] one\n- [x]", "- [ ] one\n- [x]\n", 16)
        )
    }

    // ---- the note list and the counts ----

    @Test
    fun the_note_list_shows_empty_and_ticked_boxes() {
        assertEquals("☐ milk", MarkdownFormat.plainLine("- [ ] milk"))
        assertEquals("☑ eggs", MarkdownFormat.plainLine("- [x] eggs"))
        assertEquals("  ☐ **x**".replace("**", ""), MarkdownFormat.plainLine("  - [ ] **x**"))
    }

    @Test
    fun the_word_count_leaves_the_box_out() {
        assertEquals("milk\neggs", MarkdownFormat.withoutMarks("- [ ] milk\n- [x] eggs"))
    }

    @Test
    fun progress_counts_the_items_and_the_ticked_ones() {
        assertEquals(
            MarkdownFormat.ChecklistProgress(2, 4),
            checklistProgress("list\n- [x] a\n- [ ] b\n- [X] c\n- [ ] d\n- plain")
        )
        assertEquals(MarkdownFormat.ChecklistProgress(0, 0), checklistProgress("no list [ ] here"))
        assertEquals(MarkdownFormat.ChecklistProgress(0, 0), checklistProgress(""))
    }

    // ---- the preview ----

    @Test
    fun the_preview_has_checklist_blocks_with_their_line_numbers() {
        val blocks = MarkdownPreview.parse("Shopping\n\n- [ ] **milk**\n- [x] eggs\ntext")
        val items = blocks.filter { it.kind == PreviewKind.CHECKLIST }
        assertEquals(2, items.size)
        assertEquals(listOf("milk", "eggs"), items.map { it.text })
        assertEquals(listOf(false, true), items.map { it.checked })
        // line 0 is "Shopping", line 1 is blank, so the items are lines 2 and 3
        assertEquals(listOf(2, 3), items.map { it.lineIndex })
        assertTrue(blocks.filter { it.kind != PreviewKind.CHECKLIST }.all { it.lineIndex == -1 })
    }

    @Test
    fun the_line_number_of_the_preview_tells_toggleCheck_which_line_to_change() {
        val text = "a\n\n- [ ] one\n- [ ] two"
        val second = MarkdownPreview.parse(text).last { it.kind == PreviewKind.CHECKLIST }
        assertEquals("a\n\n- [ ] one\n- [x] two", MarkdownFormat.toggleCheck(text, second.lineIndex))
    }

    @Test
    fun a_deeper_checklist_item_sits_one_level_deeper() {
        val item = MarkdownPreview.parse("- [ ] a\n  - [ ] b").last()
        assertEquals(PreviewKind.CHECKLIST, item.kind)
        assertEquals(1, item.level)
    }

    @Test
    fun blockOf_reads_the_box() {
        assertEquals(
            MarkdownFormat.Block(MarkdownFormat.BlockKind.CHECKLIST, 0, null, "milk", false),
            MarkdownFormat.blockOf("- [ ] milk")
        )
        assertEquals(
            MarkdownFormat.Block(MarkdownFormat.BlockKind.CHECKLIST, 2, null, "milk", true),
            MarkdownFormat.blockOf("  - [X] milk")
        )
    }
}
