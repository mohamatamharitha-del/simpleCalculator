package fm.mrc.simplecalculator

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HistoryManager {
    private const val PREF_NAME = "calculator_history"
    private const val KEY_HISTORY = "history_items"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Loads history from SharedPreferences.
     * Returns a list of strings formatted as "timestamp;calculation".
     * Ordered newest first.
     */
    suspend fun loadHistory(context: Context): List<String> = withContext(Dispatchers.IO) {
        val prefs = getPreferences(context)
        prefs.getStringSet(KEY_HISTORY, emptySet())?.toList()?.sortedByDescending {
            it.split(";").getOrNull(0)?.toLongOrNull() ?: 0L
        } ?: emptyList()
    }

    /**
     * Saves history list to SharedPreferences.
     */
    suspend fun saveHistory(context: Context, history: List<String>) = withContext(Dispatchers.IO) {
        val prefs = getPreferences(context)
        prefs.edit().putStringSet(KEY_HISTORY, history.toSet()).apply()
    }

    /**
     * Clears all history.
     */
    suspend fun clearHistory(context: Context) = withContext(Dispatchers.IO) {
        val prefs = getPreferences(context)
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}
