package com.elnor.app

// The rules of the Collections tab, with no Android code, so they are tested on the computer.
// A collection is a folder with a name. A note or a task is in at most one collection (or none).
// Which collection it is in is stored on the note / task itself (collectionId).

/** What is wrong with a collection name, if anything. */
enum class CollectionNameProblem { BLANK, TOO_LONG, DUPLICATE }

object CollectionNames {

    const val MAX_LENGTH = 40

    private val SPACES = Regex("""\s+""")

    /** The name as it is stored: no spaces at the edges, one space between words. */
    fun clean(name: String): String = name.trim().replace(SPACES, " ")

    /** What two names are compared by: the same rules as the search (case, Bengali spellings). */
    fun key(name: String): String = SearchText.normalize(clean(name))

    /**
     * Is [name] fine as the name of a collection? null means yes. A name is a duplicate when another
     * collection ([exceptId] is the one that is being renamed, which does not count) has the same
     * name, capital letters and the two ways of typing a Bengali letter not counted.
     */
    fun problem(name: String, existing: List<NoteCollection>, exceptId: Long? = null): CollectionNameProblem? {
        val clean = clean(name)
        if (clean.isEmpty()) return CollectionNameProblem.BLANK
        if (clean.length > MAX_LENGTH) return CollectionNameProblem.TOO_LONG
        val key = key(clean)
        if (existing.any { it.id != exceptId && key(it.name) == key }) return CollectionNameProblem.DUPLICATE
        return null
    }

    /** The sentence under the name field for a [problem]. */
    fun message(problem: CollectionNameProblem): String = when (problem) {
        CollectionNameProblem.BLANK -> "Give the collection a name."
        CollectionNameProblem.TOO_LONG -> "Use at most $MAX_LENGTH characters."
        CollectionNameProblem.DUPLICATE -> "A collection with this name already exists."
    }
}

/** A to Z by name (case does not matter); the older one first when two names look the same. */
fun sortCollections(collections: List<NoteCollection>): List<NoteCollection> =
    collections.sortedWith(
        compareBy<NoteCollection> { CollectionNames.key(it.name) }.thenBy { it.createdAt }.thenBy { it.id }
    )

/** How many notes and tasks a collection has. */
data class CollectionCounts(val notes: Int, val tasks: Int) {
    val isEmpty: Boolean get() = notes == 0 && tasks == 0
}

/**
 * The counts of every collection that has something in it. [notes] are the notes to count (the
 * home list: not the Archive, not the Trash), [tasks] all tasks, finished ones too.
 */
fun countByCollection(notes: List<Note>, tasks: List<Task>): Map<Long, CollectionCounts> {
    val noteCounts = HashMap<Long, Int>()
    for (note in notes) note.collectionId?.let { noteCounts[it] = (noteCounts[it] ?: 0) + 1 }
    val taskCounts = HashMap<Long, Int>()
    for (task in tasks) task.collectionId?.let { taskCounts[it] = (taskCounts[it] ?: 0) + 1 }
    return (noteCounts.keys + taskCounts.keys).associateWith {
        CollectionCounts(noteCounts[it] ?: 0, taskCounts[it] ?: 0)
    }
}

/** "Empty", "1 note", "3 notes · 2 tasks". [counts] null means nothing in it. */
fun collectionCountsLabel(counts: CollectionCounts?): String {
    if (counts == null || counts.isEmpty) return "Empty"
    val parts = ArrayList<String>(2)
    if (counts.notes > 0) parts.add(if (counts.notes == 1) "1 note" else "${counts.notes} notes")
    if (counts.tasks > 0) parts.add(if (counts.tasks == 1) "1 task" else "${counts.tasks} tasks")
    return parts.joinToString(" · ")
}

/** The notes of one collection, the most recently changed first (pinned ones do not jump ahead). */
fun notesInCollection(notes: List<Note>, collectionId: Long): List<Note> =
    notes.filter { it.collectionId == collectionId }.sortedByDescending { it.updatedAt }

/** The tasks of one collection in the order of the Task tab (see [sortTasks]). */
fun tasksInCollection(tasks: List<Task>, collectionId: Long): List<Task> =
    sortTasks(tasks.filter { it.collectionId == collectionId })

/** The name of the collection with [id], or null (no collection, or one that is gone). */
fun collectionName(collections: List<NoteCollection>, id: Long?): String? =
    id?.let { wanted -> collections.firstOrNull { it.id == wanted }?.name }

