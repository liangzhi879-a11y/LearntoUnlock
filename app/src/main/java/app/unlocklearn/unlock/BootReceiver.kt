package app.unlocklearn.unlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.unlocklearn.pref.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机自启接收器。开机完成后按配置启动监听前台 Service。
 *
 * - 锁屏模式默认开启 → 启动 UnlockWatchService（保活 + 通知）
 * - 娱乐 App 模式若开启 → 启动 AppWatchService
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i(TAG, "Boot completed, starting watch services")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = AppSettings(context).snapshot()
                if (settings.unlockMode) {
                    UnlockWatchService.start(context)
                }
                if (settings.appMode) {
                    AppWatchService.start(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动 Service 失败", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
