package com.rhuta.kask.data.db.dao

import androidx.room.*
import com.rhuta.kask.data.db.entities.LibraryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query("SELECT * FROM library ORDER BY isPinned DESC, lastOpenedAt DESC")
    fun observeAll(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM library WHERE isFavourite = 1 ORDER BY isPinned DESC, savedAt DESC")
    fun observeFavourites(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM library WHERE contentType = :type ORDER BY isPinned DESC, savedAt DESC")
    fun observeByType(type: String): Flow<List<LibraryEntity>>

    @Query(
        """
        SELECT * FROM library
        WHERE title LIKE '%' || :query || '%'
           OR textContent LIKE '%' || :query || '%'
           OR tags LIKE '%' || :query || '%'
        ORDER BY isPinned DESC, lastOpenedAt DESC
        """,
    )
    fun search(query: String): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM library WHERE id = :id")
    suspend fun getById(id: String): LibraryEntity?

    /**
     * Efficient combined search and filter.
     * @param query Keyword to search for in title, content, or tags.
     * @param type Specific content type to filter by, or null for all types.
     */
    @Query(
        """
        SELECT * FROM library 
        WHERE (:type IS NULL OR contentType = :type)
        AND (
            title LIKE '%' || :query || '%' 
            OR textContent LIKE '%' || :query || '%' 
            OR tags LIKE '%' || :query || '%'
        )
        ORDER BY isPinned DESC, lastOpenedAt DESC
        """,
    )
    fun searchAndFilter(query: String, type: String?): Flow<List<LibraryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: LibraryEntity)

    @Update
    suspend fun update(item: LibraryEntity)

    @Delete
    suspend fun delete(item: LibraryEntity)

    @Query("DELETE FROM library WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE library SET isFavourite = :fav WHERE id = :id")
    suspend fun setFavourite(id: String, fav: Boolean)

    @Query("UPDATE library SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE library SET lastOpenedAt = :ts WHERE id = :id")
    suspend fun updateLastOpened(id: String, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM library")
    suspend fun deleteAll()
}
