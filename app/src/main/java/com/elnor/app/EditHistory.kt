package com.elnor.app

/**
 * What the note page showed at one moment: the title, the text, and where the cursor (or the
 * selection) was in the text.
 */
data class EditSnapshot(
    val title: String,
    val body: String,
    val bodySelStart: Int = body.length,
    val bodySelEnd: Int = bodySelStart
)

/**
 * The Undo / Redo memory of one open note.
 *
 * [record] is called after every change on the page. A change is NOT one step on its own. Keys
 * typed one after the other are joined into one step, so Undo takes back a word (or a run of
 * deleted letters) at a time, not a letter at a time:
 *  - typing joins typing, deleting joins deleting, in the same field (title or text)
 *  - a pause longer than [groupMillis], a space or a line break, or a change of kind or field
 *    starts a new step
 *  - a paste, a replaced selection, a suggestion from the keyboard (more than one letter at once)
 *    is always a step of its own
 *  - only moving the cursor is not a change: no step, but the cursor position is remembered
 *
 * The memory is limited ([maxSteps] steps and about [maxTotalChars] characters in all), the oldest
 * steps are forgotten first. A new change after an Undo removes what could have been redone.
 *
 * It only holds plain values and has no Android code, so it is tested on the computer.
 *
 * @param now clock in milliseconds. Replaced in tests.
 */
class EditHistory(
    initial: EditSnapshot,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val maxSteps: Int = 100,
    private val maxTotalChars: Int = 1_000_000,
    private val groupMillis: Long = 1_000L
) {
    private enum class Kind { TYPE, DELETE, OTHER }
    private enum class Field { TITLE, BODY, BOTH }

    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()
    private var current = initial

    /** Characters kept in both stacks, to keep the memory small for a very long note. */
    private var totalChars = 0

    // The step that is still being typed (null: the next change starts a new step)
    private var groupKind: Kind? = null
    private var groupField: Field? = null
    private var lastChangeAt = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** The page now shows [next]. */
    fun record(next: EditSnapshot) {
        if (next.title == current.title && next.body == current.body) {
            current = next // only the cursor moved
            return
        }

        val time = now()
        val field = fieldOf(next)
        val kind = kindOf(next, field)
        val joinsLastStep = kind != Kind.OTHER &&
            kind == groupKind &&
            field == groupField &&
            time - lastChangeAt <= groupMillis

        if (!joinsLastStep) {
            pushUndo(current)
            clearRedo()
            trim()
        }

        val endsWord = kind == Kind.TYPE && insertedCharIsWhitespace(next, field)
        current = next
        lastChangeAt = time
        groupField = field
        // A space or a line break ends the step: the next typing is a new step (a new word)
        groupKind = if (endsWord) null else kind
    }

    /** Goes one step back. Returns what the page must show now, or null if there is nothing to undo. */
    fun undo(): EditSnapshot? {
        val previous = undoStack.removeLastOrNull() ?: return null
        totalChars -= size(previous)
        redoStack.addLast(current)
        totalChars += size(current)
        current = previous
        groupKind = null
        return current
    }

    /** Goes one step forward again. Returns what the page must show now, or null if there is nothing to redo. */
    fun redo(): EditSnapshot? {
        val next = redoStack.removeLastOrNull() ?: return null
        totalChars -= size(next)
        undoStack.addLast(current)
        totalChars += size(current)
        current = next
        groupKind = null
        return current
    }

    // ---- helpers ----

    private fun fieldOf(next: EditSnapshot): Field {
        val titleChanged = next.title != current.title
        val bodyChanged = next.body != current.body
        return when {
            titleChanged && bodyChanged -> Field.BOTH
            titleChanged -> Field.TITLE
            else -> Field.BODY
        }
    }

    private fun kindOf(next: EditSnapshot, field: Field): Kind {
        if (field == Field.BOTH) return Kind.OTHER
        val change = changeLength(next, field)
        return when (change) {
            1 -> Kind.TYPE
            -1 -> Kind.DELETE
            else -> Kind.OTHER
        }
    }

    /** How many characters longer (or shorter, when negative) the changed field got. */
    private fun changeLength(next: EditSnapshot, field: Field): Int =
        if (field == Field.TITLE) next.title.length - current.title.length
        else next.body.length - current.body.length

    /** For one typed character: is it a space, a tab or a line break? */
    private fun insertedCharIsWhitespace(next: EditSnapshot, field: Field): Boolean {
        val before = if (field == Field.TITLE) current.title else current.body
        val after = if (field == Field.TITLE) next.title else next.body
        var i = 0
        while (i < before.length && i < after.length && before[i] == after[i]) i++
        return after.getOrNull(i)?.isWhitespace() == true
    }

    private fun size(snapshot: EditSnapshot): Int = snapshot.title.length + snapshot.body.length

    private fun pushUndo(snapshot: EditSnapshot) {
        undoStack.addLast(snapshot)
        totalChars += size(snapshot)
    }

    private fun clearRedo() {
        for (snapshot in redoStack) totalChars -= size(snapshot)
        redoStack.clear()
    }

    private fun trim() {
        while (undoStack.size > maxSteps || (totalChars > maxTotalChars && undoStack.size > 1)) {
            totalChars -= size(undoStack.removeFirst())
        }
    }
}
