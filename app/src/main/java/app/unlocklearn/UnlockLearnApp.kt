package app.unlocklearn

import android.app.Application
import android.util.Log
import app.unlocklearn.data.QuizRepository
import app.unlocklearn.pref.AppSettings
import app.unlocklearn.unlock.AppWatchService
import app.unlocklearn.unlock.UnlockWatchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口。
 * 1. 幂等导入内置题库
 * 2. 按配置启动监听前台 Service
 */
class UnlockLearnApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { QuizRepository(this@UnlockLearnApp).ensureImported() }
                .onFailure { Log.e(TAG, "题库导入失败", it) }
                .onSuccess { Log.i(TAG, "题库导入完成") }

            runCatching {
                val settings = AppSettings(this@UnlockLearnApp).snapshot()
                if (settings.unlockMode) UnlockWatchService.start(this@UnlockLearnApp)
                if (settings.appMode) AppWatchService.start(this@UnlockLearnApp)
            }.onFailure { Log.e(TAG, "Service 启动失败", it) }
        }
    }

    companion object {
        private const val TAG = "UnlockLearnApp"
    }
}
