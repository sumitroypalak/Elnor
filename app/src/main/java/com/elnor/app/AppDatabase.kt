package com.elnor.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray

/**
 * Version 1: table "notes" with id, title, body, updatedAt.
 * Version 2: adds the searchText column (see SearchText).
 * Version 3: adds the deletedAt column (the Trash, see NoteEntity).
 */
@Database(entities = [NoteEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}

/**
 * Adds deletedAt. It is empty (NULL) for every note that already exists, so all old notes
 * stay normal notes. Nothing else in the table is touched.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN deletedAt INTEGER")
    }
}

/** Adds searchText and fills it for every note that already exists (backfill). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN searchText TEXT NOT NULL DEFAULT ''")
        backfillSearchText(db)
    }
}

/** Calculates searchText from title and body for all rows. */
fun backfillSearchText(db: SupportSQLiteDatabase) {
    // Read everything first, then write, so we do not change the table while a cursor is open.
    val rows = ArrayList<Triple<Long, String, String>>()
    db.query("SELECT id, title, body FROM notes").use { cursor ->
        while (cursor.moveToNext()) {
            rows.add(Triple(cursor.getLong(0), cursor.getString(1), cursor.getString(2)))
        }
    }
    for ((id, title, body) in rows) {
        db.execSQL(
            "UPDATE notes SET searchText = ? WHERE id = ?",
            arrayOf<Any?>(SearchText.build(title, body), id)
        )
    }
}

/**
 * The app used to keep notes in SharedPreferences ("elnor_notes" / "notes", as JSON).
 * When the database file is created for the first time, those notes are copied into it,
 * with searchText filled. The old data is left untouched as a backup.
 */
class LegacyNotesImport(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val notes = readLegacyNotes() ?: return
        for (note in notes) {
            val values = ContentValues().apply {
                put("id", note.id)
                put("title", note.title)
                put("body", note.body)
                put("updatedAt", note.updatedAt)
                put("searchText", SearchText.build(note.title, note.body))
            }
            db.insert("notes", SQLiteDatabase.CONFLICT_REPLACE, values)
        }
    }

    /** Null when there is nothing to import or the old data cannot be read. */
    private fun readLegacyNotes(): List<Note>? {
        return try {
            val raw = context.getSharedPreferences("elnor_notes", Context.MODE_PRIVATE)
                .getString("notes", null) ?: return null
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                Note(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    body = obj.getString("body"),
                    updatedAt = obj.getLong("updatedAt")
                )
            }
        } catch (_: Exception) {
            null // keep the new empty database, the old data is still in SharedPreferences
        }
    }
}
