package fm.mrc.simplecalculator

import android.content.Context
import fm.mrc.simplecalculator.database.AppDatabase
import fm.mrc.simplecalculator.database.HistoryItem
import fm.mrc.simplecalculator.database.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HistoryManager {
    private const val HISTORY_KEY = "calculator_history"
    private const val PREFS_NAME = "CalculatorPrefs"
    private const val SEPARATOR = "|||"

    private var repository: HistoryRepository? = null

    fun getRepository(context: Context): HistoryRepository {
        return repository ?: synchronized(this) {
            val dao = AppDatabase.getDatabase(context).historyDao()
            val repo = HistoryRepository(dao)
            repository = repo
            repo
        }
    }

    suspend fun checkAndMigrate(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyString = prefs.getString(HISTORY_KEY, null)
        if (historyString != null) {
            val repo = getRepository(context)
            val items = historyString.split(SEPARATOR).filter { it.isNotEmpty() }
            items.forEach { item ->
                val parts = item.split(";", limit = 2)
                val timestamp = parts.getOrNull(0)?.toLongOrNull() ?: System.currentTimeMillis()
                val calculation = parts.getOrNull(1) ?: item
                repo.insert(HistoryItem(timestamp = timestamp, calculation = calculation))
            }
            prefs.edit().remove(HISTORY_KEY).apply()
        }
    }

    suspend fun saveHistory(context: Context, calculation: String) = withContext(Dispatchers.IO) {
        val repo = getRepository(context)
        repo.insert(HistoryItem(timestamp = System.currentTimeMillis(), calculation = calculation))
    }

    suspend fun loadHistory(context: Context): List<String> = withContext(Dispatchers.IO) {
        // This is now handled by Flow in ViewModel/State, but keeping for compatibility
        emptyList()
    }

    suspend fun clearHistory(context: Context) = withContext(Dispatchers.IO) {
        val repo = getRepository(context)
        repo.deleteAll()
    }
}
