package com.elnor.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray

/**
 * Version 1: table "notes" with id, title, body, updatedAt.
 * Version 2: adds the searchText column (see SearchText).
 * Version 3: adds the deletedAt column (the Trash, see NoteEntity).
 * Version 4: adds the pinnedAt column (pinned notes, see NoteEntity).
 * Version 5: adds the archivedAt column (the Archive, see NoteEntity).
 * Version 6: adds the "tasks" table (the Task tab, see TaskEntity).
 */
@Database(entities = [NoteEntity::class, TaskEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
}

/**
 * The one database of the app. Notes and tasks live in the same file, so both repositories must
 * share one instance (two Room instances on one file would not see each other's changes).
 */
object ElnorDatabase {
    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }
    }

    private fun build(appContext: Context): AppDatabase =
        Room.databaseBuilder(appContext, AppDatabase::class.java, "elnor_notes_room.db")
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6
            )
            .addCallback(LegacyNotesImport(appContext))
            .build()
}

/**
 * Adds the empty "tasks" table. The notes table is not touched. The column definitions must
 * match TaskEntity exactly, or Room refuses to open the database.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "title TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL, " +
                "dueDate INTEGER, " +
                "completedAt INTEGER)"
        )
    }
}

/**
 * Adds archivedAt. It is empty (NULL) for every note that already exists, so no old note is archived.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN archivedAt INTEGER")
    }
}

/**
 * Adds pinnedAt. It is empty (NULL) for every note that already exists, so no old note is pinned.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN pinnedAt INTEGER")
    }
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
