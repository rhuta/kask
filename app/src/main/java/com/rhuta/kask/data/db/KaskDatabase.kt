package com.rhuta.kask.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rhuta.kask.data.db.dao.HistoryDao
import com.rhuta.kask.data.db.dao.LibraryDao
import com.rhuta.kask.data.db.entities.HistoryEntity
import com.rhuta.kask.data.db.entities.LibraryEntity

@Database(
    entities = [HistoryEntity::class, LibraryEntity::class],
    version = 3,
    exportSchema = false
)
abstract class KaskDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun libraryDao(): LibraryDao
}
