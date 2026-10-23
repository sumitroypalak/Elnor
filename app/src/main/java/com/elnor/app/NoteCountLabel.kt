package com.elnor.app

/** "1 note", "0 notes", "12 notes": the count shown next to "Your notes" on the home screen. */
fun notesCountLabel(count: Int): String = if (count == 1) "1 note" else "$count notes"
