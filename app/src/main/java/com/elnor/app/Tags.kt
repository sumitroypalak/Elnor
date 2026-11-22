package com.elnor.app

/**
 * One tag found in a text. [start] (included) and [end] (not included) are positions in the text and
 * cover the "#" too. [name] is the word after the "#", as it was typed.
 */
data class TagSpan(val start: Int, val end: Int, val name: String)

/**
 * Finds tags like #কাজ or #shopping in a text. A tag is plain text of the note: nothing is stored,
 * the tags are calculated from the text every time, so they always follow what the user typed.
 *
 * Rules (careful on purpose, a wrong tag is worse than a missed one):
 *  - A tag is "#" followed by letters, digits, marks and "_" (Bengali works, also the vowel signs).
 *    It ends at the first space or other sign: "#work," is the tag "work".
 *  - It needs at least one letter, so "#1" or "#123" (a rank, a number) is not a tag.
 *  - The "#" must not follow a letter, a digit or another "#": "a#b" and "##x" are not tags, and
 *    "# Title" (a heading, with a space) is not a tag either.
 *  - Text inside a web link, an e-mail address or a phone number is never a tag
 *    ("https://example.com/#top" is a link, not a tag).
 *  - A tag longer than [MAX_LENGTH] characters is not a tag.
 *
 * It has no Android code, so it is tested with plain unit tests.
 */
object TagDetector {

    const val MAX_LENGTH = 50

    // ‌ and ‍ (zero width joiners) are part of some Bengali words
    private val TAG = Regex("""(?<![\p{L}\p{M}\p{N}_#])#([\p{L}\p{M}\p{N}_‌‍]+)""")

    /** All tags of [text], in the order they appear. They never overlap a link. */
    fun find(text: String): List<TagSpan> {
        if (text.length < 2 || !text.contains('#')) return emptyList()
        val links = LinkDetector.find(text)
        val result = ArrayList<TagSpan>()
        for (match in TAG.findAll(text)) {
            val name = match.groupValues[1]
            if (name.length > MAX_LENGTH || name.none { it.isLetter() }) continue
            val start = match.range.first
            val end = match.range.last + 1
            if (links.any { start < it.end && end > it.start }) continue
            result.add(TagSpan(start, end, name))
        }
        return result
    }
}

/** A tag of the notes list: its [key] (see [Tags.keyOf]) and in how many notes it is. */
data class TagCount(val key: String, val count: Int)

/**
 * The tags of notes, for the tag buttons of the home screen. Tags are compared by their [keyOf]:
 * #Work and #work are the same tag, and so are the two ways of typing the Bengali য়.
 */
object Tags {

    /** The tag as it is compared and shown on the buttons: normalized and in lower case. */
    fun keyOf(name: String): String = SearchText.normalize(name)

    /** The keys of all tags in [text], each one once. */
    fun keysIn(text: String): Set<String> =
        TagDetector.find(text).mapTo(LinkedHashSet()) { keyOf(it.name) }

    /** The keys of the tags of a note. The title counts too. */
    fun keysOf(note: Note): Set<String> = keysIn(note.title + "\n" + note.body)

    /** True when [note] has the tag with this [key]. */
    fun has(note: Note, key: String): Boolean = key in keysOf(note)

    /**
     * Every tag that is in [notes], with the number of notes it is in (a note counts once for a
     * tag, however often it is typed). The tag that is in the most notes comes first; the same
     * count is ordered by the key.
     */
    fun summarize(notes: List<Note>): List<TagCount> {
        val counts = HashMap<String, Int>()
        for (note in notes) {
            for (key in keysOf(note)) counts[key] = (counts[key] ?: 0) + 1
        }
        return sortedCounts(counts)
    }

    /** The tag that is in the most notes comes first; the same count is ordered by the key. */
    internal fun sortedCounts(counts: Map<String, Int>): List<TagCount> =
        counts.map { TagCount(it.key, it.value) }
            .sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.key })
}

/**
 * Remembers the tags of every note it has seen, so the (slow) search for tags in the text is only
 * done again for a note whose title or text really changed. Finding tags runs a regular expression
 * and the link detection over the whole text, which is too much to repeat for every note after
 * every auto-save, and it must never run on the main thread for a long list of notes.
 *
 * A remembered answer is only used while the title and the text are the same as when it was
 * calculated, so the cache can never give the tags of an older text. It is safe to use from more
 * than one thread at once (the screen asks from the main thread, the summary is made in the
 * background). It has no Android code, so it is tested with plain unit tests.
 */
class TagKeyCache {

    private class Entry(val title: String, val body: String, val keys: Set<String>)

    private val entries = java.util.concurrent.ConcurrentHashMap<Long, Entry>()

    /** Same answer as [Tags.keysOf], but a note whose text did not change is not searched again. */
    fun keysOf(note: Note): Set<String> {
        val cached = entries[note.id]
        if (cached != null && cached.title == note.title && cached.body == note.body) return cached.keys
        val keys = Tags.keysOf(note)
        entries[note.id] = Entry(note.title, note.body, keys)
        return keys
    }

    /** Same answer as [Tags.has]. */
    fun has(note: Note, key: String): Boolean = key in keysOf(note)

    /**
     * Same answer as [Tags.summarize]. [isActive] is asked before every note: when it turns false
     * (the work is not needed any more) the loop stops at once and the result must be thrown away.
     * When all notes were read, notes that no longer exist are forgotten, so the cache does not
     * keep the text of deleted notes.
     */
    fun summarize(notes: List<Note>, isActive: () -> Boolean = { true }): List<TagCount> {
        val counts = HashMap<String, Int>()
        for (note in notes) {
            if (!isActive()) return emptyList()
            for (key in keysOf(note)) counts[key] = (counts[key] ?: 0) + 1
        }
        entries.keys.retainAll(notes.mapTo(HashSet()) { it.id })
        return Tags.sortedCounts(counts)
    }
}
