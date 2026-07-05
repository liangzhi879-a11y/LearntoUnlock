package app.unlocklearn.unlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import app.unlocklearn.overlay.OverlayActivity
import app.unlocklearn.pref.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 用户解锁事件接收器。
 *
 * 监听 ACTION_USER_PRESENT（用户正确解锁后系统广播），
 * 按配置的概率决定是否触发覆盖答题层。
 *
 * 仅当「锁屏解答模式」开启时生效。
 *
 * 这是合规方案：不拦截解锁，仅在解锁后覆盖。
 */
class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        Log.i(TAG, "User present (unlocked)")

        // BroadcastReceiver.onReceive 不可直接挂起，用 goAsync 桥接协程
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = AppSettings(context).snapshot()
                if (!settings.unlockMode) {
                    Log.i(TAG, "锁屏模式未启用，跳过")
                    return@launch
                }
                if (Math.random() < settings.probability) {
                    Log.i(TAG, "触发答题（概率 ${settings.probability}）")
                    val overlayIntent = Intent(context, OverlayActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        )
                        putExtra(AppWatchService.EXTRA_SOURCE, AppWatchService.SOURCE_UNLOCK)
                    }
                    context.startActivity(overlayIntent)
                } else {
                    Log.i(TAG, "概率未命中，跳过")
                }
            } catch (e: Exception) {
                Log.e(TAG, "解锁触发判定失败", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "UnlockReceiver"
    }
}
