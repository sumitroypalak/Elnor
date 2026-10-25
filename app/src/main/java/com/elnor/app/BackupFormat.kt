package com.elnor.app

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Why a backup file could not be used. [message] is shown to the user as it is.
 */
enum class BackupError(val message: String) {
    NOT_JSON("This file is not a valid Elnor backup."),
    NOT_ELNOR("This file was not made by Elnor."),
    TOO_NEW("This backup was made by a newer version of Elnor. Please update the app first."),
    NO_NOTES("This backup file has no notes in it.")
}

/** The result of reading a backup file. */
sealed interface BackupParseResult {
    /** [skipped] counts the entries in the file that were damaged or empty and were left out. */
    data class Success(val notes: List<Note>, val skipped: Int) : BackupParseResult

    data class Failure(val reason: BackupError) : BackupParseResult
}

/** What an import did. */
data class ImportSummary(val added: Int, val alreadyPresent: Int)

/**
 * The backup file: one readable JSON file with every note (also the ones in the Trash).
 *
 * ```
 * {
 *   "app": "elnor",
 *   "formatVersion": 1,
 *   "exportedAt": 1760000000000,
 *   "noteCount": 2,
 *   "notes": [
 *     { "title": "...", "body": "...", "updatedAt": 1, "deletedAt": null, "pinnedAt": null,
 *       "archivedAt": null }
 *   ]
 * }
 * ```
 *
 * Rules:
 *  - The database id is NOT saved. It means nothing on another phone, and an import always gives
 *    new ids, so an import can never overwrite or replace a note.
 *  - title and body are written exactly as they are stored (Bengali, emoji and line breaks
 *    included). The file is UTF-8.
 *  - An import only ADDS notes. A note that is already in the app (same title, same text and
 *    same updatedAt) is skipped, so importing the same file twice does not make copies.
 *  - A newer file version is refused instead of being half-read. Missing optional fields in an
 *    older or hand-made file are tolerated (a file without "archivedAt" has no archived notes).
 *
 * This file has no Android classes besides org.json, so it is tested with plain unit tests.
 */
object BackupFormat {

    const val APP_ID = "elnor"
    const val FORMAT_VERSION = 1

    /** The note as "the same note" for the import: id is ignored, pin and Trash state too. */
    private data class NoteKey(val title: String, val body: String, val updatedAt: Long)

    private fun Note.key() = NoteKey(title, body, updatedAt)

    fun toJson(notes: List<Note>, exportedAt: Long): String {
        val array = JSONArray()
        for (note in notes) {
            array.put(
                JSONObject().apply {
                    put("title", note.title)
                    put("body", note.body)
                    put("updatedAt", note.updatedAt)
                    put("deletedAt", note.deletedAt ?: JSONObject.NULL)
                    put("pinnedAt", note.pinnedAt ?: JSONObject.NULL)
                    put("archivedAt", note.archivedAt ?: JSONObject.NULL)
                }
            )
        }
        return JSONObject().apply {
            put("app", APP_ID)
            put("formatVersion", FORMAT_VERSION)
            put("exportedAt", exportedAt)
            put("noteCount", notes.size)
            put("notes", array)
        }.toString(2)
    }

    fun fromJson(text: String): BackupParseResult {
        val root = try {
            // Some editors put an invisible byte-order mark at the start of a UTF-8 file
            JSONObject(text.removePrefix("﻿"))
        } catch (_: JSONException) {
            return BackupParseResult.Failure(BackupError.NOT_JSON)
        }

        if (stringOrEmpty(root, "app") != APP_ID) {
            return BackupParseResult.Failure(BackupError.NOT_ELNOR)
        }
        val version = longOrNull(root, "formatVersion") ?: 0L
        if (version > FORMAT_VERSION) return BackupParseResult.Failure(BackupError.TOO_NEW)
        if (version < 1L) return BackupParseResult.Failure(BackupError.NOT_ELNOR)

        val array = root.optJSONArray("notes")
            ?: return BackupParseResult.Failure(BackupError.NO_NOTES)

        val notes = ArrayList<Note>(array.length())
        var skipped = 0
        for (i in 0 until array.length()) {
            val note = readNote(array.optJSONObject(i))
            if (note == null) skipped++ else notes.add(note)
        }
        if (notes.isEmpty() && skipped == 0) return BackupParseResult.Failure(BackupError.NO_NOTES)
        return BackupParseResult.Success(notes, skipped)
    }

    /** Null when the entry is damaged: not an object, no time, or no text at all. */
    private fun readNote(obj: JSONObject?): Note? {
        if (obj == null) return null
        val title = stringOrEmpty(obj, "title")
        val body = stringOrEmpty(obj, "body")
        if (title.isBlank() && body.isBlank()) return null // the app never saves a blank note
        val updatedAt = longOrNull(obj, "updatedAt") ?: return null
        return Note(
            id = 0L,
            title = title,
            body = body,
            updatedAt = updatedAt,
            deletedAt = longOrNull(obj, "deletedAt"),
            pinnedAt = longOrNull(obj, "pinnedAt"),
            archivedAt = longOrNull(obj, "archivedAt")
        )
    }

    // org.json turns a JSON null into the text "null" with optString, so null is checked first.
    private fun stringOrEmpty(obj: JSONObject, name: String): String =
        if (!obj.has(name) || obj.isNull(name)) "" else obj.optString(name, "")

    private fun longOrNull(obj: JSONObject, name: String): Long? {
        if (!obj.has(name) || obj.isNull(name)) return null
        return try {
            obj.getLong(name)
        } catch (_: JSONException) {
            null
        }
    }

    /**
     * The notes of [incoming] that are not in [existing] yet. A note that appears twice in the
     * file is returned once. The order of [incoming] is kept.
     */
    fun selectNew(existing: List<Note>, incoming: List<Note>): List<Note> {
        val seen = HashSet<NoteKey>(existing.size + incoming.size)
        for (note in existing) seen.add(note.key())
        return incoming.filter { seen.add(it.key()) }
    }

    /** Suggested name of the backup file, for example elnor-backup-2026-10-21-1205.json */
    fun fileName(now: Long): String =
        "elnor-backup-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(now)) + ".json"

    private fun noteCount(count: Int) = if (count == 1) "1 note" else "$count notes"

    fun exportMessage(count: Int): String = "Backup saved: ${noteCount(count)}."

    fun importMessage(summary: ImportSummary, skipped: Int): String {
        val parts = ArrayList<String>()
        parts.add(if (summary.added == 0) "No new notes were added." else "Added ${noteCount(summary.added)}.")
        if (summary.alreadyPresent > 0) parts.add("${noteCount(summary.alreadyPresent)} already in Elnor, left unchanged.")
        if (skipped > 0) parts.add("${noteCount(skipped)} in the file could not be read.")
        return parts.joinToString(" ")
    }
}
