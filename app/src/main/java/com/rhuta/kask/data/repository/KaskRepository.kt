package com.rhuta.kask.data.repository

import com.rhuta.kask.data.db.dao.HistoryDao
import com.rhuta.kask.data.db.dao.LibraryDao
import com.rhuta.kask.data.db.entities.HistoryEntity
import com.rhuta.kask.data.db.entities.LibraryEntity
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KaskRepository @Inject constructor(
    private val historyDao: HistoryDao,
    private val libraryDao: LibraryDao
) {

    // ---- History --------------------------------------------------------

    fun observeHistory(): Flow<List<HistoryEntity>> = historyDao.observeAll()

    fun searchHistory(query: String): Flow<List<HistoryEntity>> =
        historyDao.search(query)

    suspend fun getHistoryById(id: String): HistoryEntity? =
        historyDao.getById(id)

    suspend fun saveHistory(entry: HistoryEntity) =
        historyDao.insert(entry)

    suspend fun deleteHistory(entry: HistoryEntity) =
        historyDao.delete(entry)

    suspend fun deleteHistoryById(id: String) =
        historyDao.deleteById(id)

    suspend fun clearHistory() =
        historyDao.deleteAll()

    suspend fun clearLibrary() =
        libraryDao.deleteAll()

    /** Prune entries older than [HistoryEntity.HISTORY_RETENTION_DAYS] days. */
    suspend fun pruneOldHistory() {
        val cutoff = System.currentTimeMillis() -
                TimeUnit.DAYS.toMillis(HistoryEntity.HISTORY_RETENTION_DAYS.toLong())
        historyDao.deleteOlderThan(cutoff)
    }

    // ---- Library --------------------------------------------------------

    fun observeLibrary(): Flow<List<LibraryEntity>> = libraryDao.observeAll()

    fun observeFavourites(): Flow<List<LibraryEntity>> = libraryDao.observeFavourites()

    fun observeLibraryByType(type: String): Flow<List<LibraryEntity>> =
        libraryDao.observeByType(type)

    fun searchLibrary(query: String): Flow<List<LibraryEntity>> =
        libraryDao.search(query)

    fun searchAndFilterLibrary(query: String, type: String?): Flow<List<LibraryEntity>> =
        libraryDao.searchAndFilter(query, type)

    suspend fun getLibraryItemById(id: String): LibraryEntity? =
        libraryDao.getById(id)

    suspend fun saveLibraryItem(item: LibraryEntity) =
        libraryDao.insert(item)

    suspend fun updateLibraryItem(item: LibraryEntity) =
        libraryDao.update(item)

    suspend fun deleteLibraryItem(item: LibraryEntity) =
        libraryDao.delete(item)

    suspend fun deleteLibraryItemById(id: String) =
        libraryDao.deleteById(id)

    suspend fun setFavourite(id: String, fav: Boolean) =
        libraryDao.setFavourite(id, fav)

    suspend fun setPinned(id: String, pinned: Boolean) =
        libraryDao.setPinned(id, pinned)

    suspend fun recordLibraryOpen(id: String) =
        libraryDao.updateLastOpened(id)
}
