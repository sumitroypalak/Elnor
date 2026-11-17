package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteLinksTest {

    private fun names(text: String) = NoteLinks.find(text).map { it.name }

    private fun note(id: Long, title: String, body: String = "", updatedAt: Long = id, archived: Boolean = false) =
        Note(id, title, body, updatedAt, archivedAt = if (archived) 1L else null)

    // ---- finding links ----

    @Test
    fun finds_a_link_with_its_position() {
        val text = "see [[Shopping list]] now"
        val link = NoteLinks.find(text).single()
        assertEquals("Shopping list", link.name)
        assertEquals("[[Shopping list]]", text.substring(link.start, link.end))
    }

    @Test
    fun finds_several_links_and_bengali_names() {
        assertEquals(listOf("বাজার", "Work plan"), names("[[বাজার]] এবং [[Work plan]]"))
    }

    @Test
    fun spaces_at_the_edges_and_doubled_spaces_do_not_matter() {
        assertEquals(listOf("a b"), names("[[  a   b ]]"))
    }

    @Test
    fun not_a_link() {
        assertTrue(names("[single] brackets").isEmpty())
        assertTrue(names("[[]]").isEmpty())
        assertTrue(names("[[   ]]").isEmpty())
        assertTrue(names("[[broken\nname]]").isEmpty())
        assertTrue(names("[[never closed").isEmpty())
        assertTrue(names("").isEmpty())
        assertTrue(names("[[" + "a".repeat(NoteLinks.MAX_NAME_LENGTH + 1) + "]]").isEmpty())
        assertEquals(1, names("[[" + "a".repeat(NoteLinks.MAX_NAME_LENGTH) + "]]").size)
    }

    @Test
    fun an_extra_bracket_does_not_break_the_link() {
        assertEquals(listOf("a"), names("[[[a]]]"))
    }

    @Test
    fun a_checkbox_is_not_a_link_but_a_link_can_be_in_a_checklist_item() {
        assertTrue(names("- [ ] milk\n- [x] eggs").isEmpty())
        assertEquals(listOf("Recipes"), names("- [ ] cook from [[Recipes]]"))
    }

    @Test
    fun linkAt_finds_the_link_around_the_cursor() {
        val links = NoteLinks.find("ab [[x]] cd")
        assertEquals("x", NoteLinks.linkAt(links, 5)?.name)
        assertEquals("x", NoteLinks.linkAt(links, 3)?.name) // right in front of it
        assertEquals("x", NoteLinks.linkAt(links, 8)?.name) // right behind it
        assertNull(NoteLinks.linkAt(links, 1))
        assertNull(NoteLinks.linkAt(links, 10))
    }

    // ---- the name of a note ----

    @Test
    fun the_name_is_the_title_or_the_first_line() {
        assertEquals("Plan", NoteLinks.nameOf(note(1, "Plan", "text")))
        assertEquals("Plan", NoteLinks.nameOf(note(1, "", "# Plan\nmore")))
        assertEquals("Plan", NoteLinks.nameOf(note(1, "", "\n\n**Plan**\nmore")))
    }

    @Test
    fun brackets_are_left_out_of_a_name() {
        assertEquals("Plan v2", NoteLinks.nameOf(note(1, "Plan [v2]")))
    }

    @Test
    fun an_empty_note_or_a_very_long_name_cannot_be_linked() {
        assertNull(NoteLinks.nameOf(note(1, "", "")))
        assertNull(NoteLinks.nameOf(note(1, "  ", " \n ")))
        assertNull(NoteLinks.nameOf(note(1, "x".repeat(NoteLinks.MAX_NAME_LENGTH + 1))))
        assertNull(NoteLinks.nameOf(note(1, "[[]]")))
    }

    // ---- finding the note ----

    @Test
    fun resolve_finds_a_note_by_its_name() {
        val notes = listOf(note(1, "Shopping"), note(2, "Work"))
        assertEquals(2L, NoteLinks.resolve(notes, "Work")?.id)
        assertNull(NoteLinks.resolve(notes, "Home"))
        assertNull(NoteLinks.resolve(notes, "   "))
    }

    @Test
    fun resolve_ignores_capital_letters_and_extra_spaces() {
        val notes = listOf(note(1, "Shopping List"))
        assertEquals(1L, NoteLinks.resolve(notes, "shopping   LIST")?.id)
    }

    @Test
    fun resolve_works_with_the_two_ways_of_typing_a_bengali_letter() {
        val oneCharacter = "য়" // য় as one character
        val twoCharacters = "য়" // য + ় as two
        assertEquals(1L, NoteLinks.resolve(listOf(note(1, oneCharacter)), twoCharacters)?.id)
    }

    @Test
    fun resolve_finds_a_note_named_by_its_first_line() {
        assertEquals(1L, NoteLinks.resolve(listOf(note(1, "", "# Ideas\nx")), "Ideas")?.id)
    }

    @Test
    fun resolve_prefers_the_same_capitals_then_a_live_note_then_the_newest() {
        val exact = note(1, "Plan", updatedAt = 1)
        val lower = note(2, "plan", updatedAt = 9)
        assertEquals(1L, NoteLinks.resolve(listOf(lower, exact), "Plan")?.id)

        val archived = note(3, "Plan", updatedAt = 9, archived = true)
        assertEquals(1L, NoteLinks.resolve(listOf(archived, exact), "Plan")?.id)

        val newer = note(4, "Plan", updatedAt = 5)
        assertEquals(4L, NoteLinks.resolve(listOf(exact, newer), "Plan")?.id)
    }

    @Test
    fun resolve_finds_an_archived_note_when_nothing_else_has_the_name() {
        assertEquals(3L, NoteLinks.resolve(listOf(note(3, "Old", archived = true)), "Old")?.id)
    }

    // ---- the list to choose from ----

    @Test
    fun suggestions_are_the_linkable_notes_newest_first() {
        val notes = listOf(note(1, "A", updatedAt = 1), note(2, "B", updatedAt = 3), note(3, "", "", updatedAt = 9))
        assertEquals(listOf(2L, 1L), NoteLinks.suggestions(notes, "", excludeId = null).map { it.id })
    }

    @Test
    fun suggestions_leave_out_the_open_note_and_filter_by_the_query() {
        val notes = listOf(note(1, "Shopping"), note(2, "Shop talk"), note(3, "Work"))
        assertEquals(listOf(2L), NoteLinks.suggestions(notes, "SHOP", excludeId = 1L).map { it.id })
        assertTrue(NoteLinks.suggestions(notes, "zzz", excludeId = null).isEmpty())
    }

    @Test
    fun suggestions_respect_the_limit() {
        val notes = (1L..10L).map { note(it, "N$it") }
        assertEquals(3, NoteLinks.suggestions(notes, "", null, limit = 3).size)
    }

    // ---- writing a link ----

    @Test
    fun insertLink_goes_to_the_cursor() {
        assertEquals(TextEdit("ab[[X]]cd", 7, 7), NoteLinks.insertLink("abcd", 2, 2, "X"))
    }

    @Test
    fun insertLink_replaces_the_selection() {
        assertEquals(TextEdit("a[[X]]d", 6, 6), NoteLinks.insertLink("abcd", 3, 1, "X"))
    }

    @Test
    fun a_link_written_by_insertLink_is_found_again() {
        val note = note(1, "My plan")
        val edit = NoteLinks.insertLink("", 0, 0, NoteLinks.nameOf(note)!!)
        assertEquals(1L, NoteLinks.resolve(listOf(note), NoteLinks.find(edit.text).single().name)?.id)
    }

    // ---- the preview ----

    @Test
    fun the_preview_finds_links_in_the_text_without_the_marks() {
        val block = MarkdownPreview.parse("**see** [[Plan]]")[0]
        val link = block.noteLinks.single()
        assertEquals("Plan", link.name)
        assertEquals("[[Plan]]", block.text.substring(link.start, link.end))
        assertTrue(MarkdownPreview.parse("- [ ] [[Plan]]")[0].noteLinks.size == 1)
        assertTrue(MarkdownPreview.parse("plain")[0].noteLinks.isEmpty())
    }
}
