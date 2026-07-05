package app.unlocklearn.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * 每日跳过计数器。基于 DataStore Preferences。
 *
 * 按日期 key 存储，跨天自动重置。
 * 每日上限 [DAILY_LIMIT] 次，用完后跳过按钮禁用。
 */
class SkipCounter(private val context: Context) {

    suspend fun getRemaining(): Int {
        val prefs = context.dataStore.data.first()
        val today = todayStr()
        val savedDate = prefs[KEY_DATE]
        if (savedDate != today) return DAILY_LIMIT
        val used = prefs[KEY_COUNT] ?: 0
        return (DAILY_LIMIT - used).coerceAtLeast(0)
    }

    /**
     * 消费一次跳过。返回是否成功（剩余次数 > 0 时才成功）。
     */
    suspend fun consumeSkip(): Boolean {
        val remaining = getRemaining()
        if (remaining <= 0) return false
        val today = todayStr()
        context.dataStore.edit { prefs ->
            val used = if (prefs[KEY_DATE] == today) prefs[KEY_COUNT] ?: 0 else 0
            prefs[KEY_DATE] = today
            prefs[KEY_COUNT] = used + 1
        }
        return true
    }

    private fun todayStr(): String = LocalDate.now().toString()

    companion object {
        const val DAILY_LIMIT = 3
        private val KEY_COUNT = intPreferencesKey("skip_count")
        private val KEY_DATE = stringPreferencesKey("skip_date")
    }
}
