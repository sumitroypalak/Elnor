package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagsTest {

    private fun names(text: String) = TagDetector.find(text).map { it.name }

    private fun shown(text: String) = TagDetector.find(text).map { text.substring(it.start, it.end) }

    private fun note(id: Long, title: String, body: String) = Note(id, title, body, 1L)

    // ---- finding tags ----

    @Test
    fun finds_simple_tags_with_their_positions() {
        val text = "buy #milk and #eggs"
        assertEquals(listOf("milk", "eggs"), names(text))
        assertEquals(listOf("#milk", "#eggs"), shown(text))
    }

    @Test
    fun finds_bengali_tags_with_vowel_signs() {
        assertEquals(listOf("কাজ", "বাজার"), names("আজ #কাজ আর #বাজার"))
        assertEquals(listOf("#কাজ"), shown("#কাজ"))
    }

    @Test
    fun an_underscore_stays_inside_the_tag() {
        assertEquals(listOf("বাজার_তালিকা"), names("#বাজার_তালিকা"))
        assertEquals(listOf("to_do"), names("#to_do"))
    }

    @Test
    fun a_tag_ends_at_a_space_or_a_sign() {
        assertEquals(listOf("work"), names("#work, then rest"))
        assertEquals(listOf("work"), names("(#work)"))
        assertEquals(listOf("work"), names("done #work."))
        assertEquals(listOf("কাজ"), names("#কাজ।"))
    }

    @Test
    fun a_tag_next_to_bold_marks_is_found() {
        assertEquals(listOf("tag"), names("**#tag**"))
    }

    @Test
    fun a_tag_can_start_a_line() {
        assertEquals(listOf("one", "two"), names("#one\n#two"))
    }

    // ---- what is not a tag ----

    @Test
    fun a_heading_is_not_a_tag() {
        assertTrue(names("# Title").isEmpty())
        assertTrue(names("## Smaller").isEmpty())
        assertTrue(names("#").isEmpty())
    }

    @Test
    fun a_hash_after_a_letter_or_another_hash_is_not_a_tag() {
        assertTrue(names("a#b").isEmpty())
        assertTrue(names("##x").isEmpty())
        assertTrue(names("সি#শার্প").isEmpty())
    }

    @Test
    fun numbers_alone_are_not_tags() {
        assertTrue(names("#1").isEmpty())
        assertTrue(names("#123").isEmpty())
        assertTrue(names("#১২৩").isEmpty())
        assertEquals(listOf("1a"), names("#1a"))
    }

    @Test
    fun a_very_long_word_is_not_a_tag() {
        assertTrue(names("#" + "a".repeat(TagDetector.MAX_LENGTH + 1)).isEmpty())
        assertEquals(1, names("#" + "a".repeat(TagDetector.MAX_LENGTH)).size)
    }

    @Test
    fun text_inside_a_link_is_not_a_tag() {
        assertTrue(names("see https://example.com/page#section now").isEmpty())
        assertTrue(names("https://example.com/#top").isEmpty())
        // a tag after the link is still a tag
        assertEquals(listOf("real"), names("https://example.com/#top #real"))
    }

    @Test
    fun text_without_a_hash_gives_nothing() {
        assertTrue(TagDetector.find("").isEmpty())
        assertTrue(TagDetector.find("no tags here").isEmpty())
    }

    // ---- keys ----

    @Test
    fun upper_and_lower_case_are_the_same_tag() {
        assertEquals(setOf("work"), Tags.keysIn("#Work and #work and #WORK"))
    }

    @Test
    fun the_two_ways_of_typing_a_bengali_letter_are_the_same_tag() {
        val oneCharacter = "#য়" // য় as one character
        val twoCharacters = "#য়" // য + ় as two
        assertEquals(Tags.keysIn(oneCharacter), Tags.keysIn(twoCharacters))
        assertEquals(1, Tags.keysIn(oneCharacter).size)
    }

    @Test
    fun the_title_counts_too() {
        val n = note(1, "#work meeting", "nothing")
        assertEquals(setOf("work"), Tags.keysOf(n))
        assertTrue(Tags.has(n, "work"))
        assertFalse(Tags.has(n, "home"))
    }

    // ---- the list of tags ----

    @Test
    fun summarize_counts_a_note_once_per_tag() {
        val notes = listOf(
            note(1, "", "#a #a #a #b"),
            note(2, "", "#a"),
            note(3, "#b", "text")
        )
        assertEquals(listOf(TagCount("a", 2), TagCount("b", 2)), Tags.summarize(notes))
    }

    @Test
    fun summarize_puts_the_most_used_tag_first_then_orders_by_key() {
        val notes = listOf(
            note(1, "", "#zeta #alpha"),
            note(2, "", "#zeta"),
            note(3, "", "#beta #alpha"),
            note(4, "", "#zeta")
        )
        assertEquals(
            listOf(TagCount("zeta", 3), TagCount("alpha", 2), TagCount("beta", 1)),
            Tags.summarize(notes)
        )
    }

    @Test
    fun the_preview_finds_tags_in_the_text_without_the_marks() {
        val block = MarkdownPreview.parse("**x** #কাজ")[0]
        assertEquals("x #কাজ", block.text)
        val tag = block.tags.single()
        assertEquals("#কাজ", block.text.substring(tag.start, tag.end))
        assertTrue(MarkdownPreview.parse("# Heading only")[0].tags.isEmpty())
    }

    @Test
    fun summarize_of_notes_without_tags_is_empty() {
        assertTrue(Tags.summarize(emptyList()).isEmpty())
        assertTrue(Tags.summarize(listOf(note(1, "x", "# heading only"))).isEmpty())
    }
}
