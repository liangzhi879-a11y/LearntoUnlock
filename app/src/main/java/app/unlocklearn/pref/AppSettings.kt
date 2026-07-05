package app.unlocklearn.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 应用设置。统一存储模式开关、触发概率等。
 *
 * 两个解答模式作为独立开关并存：
 * - 锁屏模式：解锁后概率触发答题
 * - 娱乐 App 模式：打开白名单 App 时概率触发答题
 *
 * 触发概率 [TRIGGER_PROBABILITY] 范围 0.0~1.0，默认 0.5。
 */
class AppSettings(private val context: Context) {

    val unlockModeEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_UNLOCK_MODE] ?: true }

    val appModeEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_APP_MODE] ?: false }

    val triggerProbability: Flow<Float> =
        context.dataStore.data.map { it[KEY_PROBABILITY] ?: DEFAULT_PROBABILITY }

    suspend fun setUnlockModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_UNLOCK_MODE] = enabled }
    }

    suspend fun setAppModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_APP_MODE] = enabled }
    }

    suspend fun setTriggerProbability(prob: Float) {
        context.dataStore.edit { it[KEY_PROBABILITY] = prob.coerceIn(0f, 1f) }
    }

    /** 一次性快照读取，供 BroadcastReceiver 等非协程场景使用 */
    suspend fun snapshot(): Snapshot {
        val prefs = context.dataStore.data.first()
        return Snapshot(
            unlockMode = prefs[KEY_UNLOCK_MODE] ?: true,
            appMode = prefs[KEY_APP_MODE] ?: false,
            probability = prefs[KEY_PROBABILITY] ?: DEFAULT_PROBABILITY
        )
    }

    data class Snapshot(
        val unlockMode: Boolean,
        val appMode: Boolean,
        val probability: Float
    )

    companion object {
        const val DEFAULT_PROBABILITY = 0.5f
        private val KEY_UNLOCK_MODE = booleanPreferencesKey("mode_unlock_enabled")
        private val KEY_APP_MODE = booleanPreferencesKey("mode_app_enabled")
        private val KEY_PROBABILITY = floatPreferencesKey("trigger_probability")
    }
}

/** 顶层 DataStore 委托，整个应用共享一个（SkipCounter 也复用此实例） */
internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "unlocklearn_settings")
