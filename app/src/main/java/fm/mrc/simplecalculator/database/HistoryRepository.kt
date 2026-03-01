package fm.mrc.simplecalculator.database

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val allHistory: Flow<List<HistoryItem>> = historyDao.getAllHistory()

    suspend fun insert(historyItem: HistoryItem) {
        historyDao.insert(historyItem)
    }

    fun searchHistory(query: String): Flow<List<HistoryItem>> {
        return historyDao.searchHistory(query)
    }

    suspend fun deleteAll() {
        historyDao.deleteAll()
    }
}
