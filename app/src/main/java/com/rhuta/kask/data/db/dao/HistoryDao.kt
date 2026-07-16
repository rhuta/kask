package com.rhuta.kask.data.db.dao

import androidx.room.*
import com.rhuta.kask.data.db.entities.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("""
        SELECT * FROM history
        WHERE inputPreview LIKE '%' || :query || '%'
           OR outputPreview LIKE '%' || :query || '%'
           OR taskAction LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    fun search(query: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun getById(id: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntity)

    @Delete
    suspend fun delete(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("""
        DELETE FROM history
        WHERE createdAt < :cutoffMs
    """)
    suspend fun deleteOlderThan(cutoffMs: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAll()
}
