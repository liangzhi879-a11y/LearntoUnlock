package app.unlocklearn

import android.app.Application
import android.util.Log
import app.unlocklearn.data.QuizRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口。在 onCreate 启动后台协程，幂等导入内置题库。
 *
 * 使用 SupervisorJob + IO 调度器，导入失败不影响应用启动；
 * BankImporter 自身幂等（banks 表非空时跳过）。
 */
class UnlockLearnApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { QuizRepository(this@UnlockLearnApp).ensureImported() }
                .onFailure { Log.e(TAG, "题库导入失败", it) }
                .onSuccess { Log.i(TAG, "题库导入完成") }
        }
    }

    companion object {
        private const val TAG = "UnlockLearnApp"
    }
}
