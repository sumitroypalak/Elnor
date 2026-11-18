package com.elnor.app

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Where collections are stored. The Collections screen only knows this interface, so tests can use
 * a fake DAO and the storage can change without touching the screen.
 */
interface CollectionRepository {
    /** Every collection, in no particular order (use [sortCollections]). Updates by itself. */
    val collections: Flow<List<NoteCollection>>

    /**
     * Makes a new collection and returns its id. Returns null (nothing is stored) when the name is
     * not fine, see [CollectionNames.problem].
     */
    suspend fun create(name: String): Long?

    /** Gives the collection a new name. Returns false (nothing changes) when the name is not fine. */
    suspend fun rename(id: Long, name: String): Boolean

    /** Deletes the collection. Its notes and tasks stay, in no collection. */
    suspend fun delete(id: Long)
}

/** Room (SQLite) storage for collections. [now] is the clock for the time of creation. */
class RoomCollectionRepository(
    private val dao: CollectionDao,
    private val now: () -> Long = { System.currentTimeMillis() }
) : CollectionRepository {

    override val collections: Flow<List<NoteCollection>> = dao.observeAll()
        .map { list -> list.map { it.toCollection() } }

    override suspend fun create(name: String): Long? {
        val existing = dao.getAll().map { it.toCollection() }
        if (CollectionNames.problem(name, existing) != null) return null
        return dao.insert(CollectionEntity(name = CollectionNames.clean(name), createdAt = now()))
    }

    override suspend fun rename(id: Long, name: String): Boolean {
        val existing = dao.getAll().map { it.toCollection() }
        if (existing.none { it.id == id }) return false
        if (CollectionNames.problem(name, existing, exceptId = id) != null) return false
        dao.rename(id, CollectionNames.clean(name))
        return true
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }

    companion object {
        @Volatile
        private var instance: RoomCollectionRepository? = null

        fun get(context: Context): RoomCollectionRepository {
            return instance ?: synchronized(this) {
                instance ?: RoomCollectionRepository(
                    ElnorDatabase.get(context.applicationContext).collectionDao()
                ).also { instance = it }
            }
        }
    }
}
