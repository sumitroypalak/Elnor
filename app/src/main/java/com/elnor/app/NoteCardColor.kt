package com.elnor.app

/**
 * Which of the [colorCount] card colors a note gets. It depends only on the note's id, so a note
 * keeps its color when other notes are added, pinned, deleted or searched. (Before, the color came
 * from the position in the list, so every card changed color when the list changed.)
 * floorMod: never negative, whatever the id is.
 */
fun cardColorIndex(noteId: Long, colorCount: Int): Int =
    Math.floorMod(noteId, colorCount.toLong()).toInt()
