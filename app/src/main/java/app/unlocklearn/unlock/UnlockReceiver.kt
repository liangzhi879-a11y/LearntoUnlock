package app.unlocklearn.unlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.unlocklearn.overlay.OverlayActivity

/**
 * 用户解锁事件接收器。
 *
 * 监听 ACTION_USER_PRESENT（用户正确解锁后系统广播），
 * 按概率决定是否触发覆盖答题层。
 *
 * 这是合规方案：不拦截解锁，仅在解锁后覆盖。
 */
class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return

        Log.i(TAG, "User present (unlocked)")
        // TODO: 从配置读取触发概率，这里先用固定 50%
        val triggerProbability = 0.5
        if (Math.random() < triggerProbability) {
            Log.i(TAG, "Triggering quiz overlay")
            val overlayIntent = Intent(context, OverlayActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
            context.startActivity(overlayIntent)
        } else {
            Log.i(TAG, "Skipping quiz (probability not met)")
        }
    }

    companion object {
        private const val TAG = "UnlockReceiver"
    }
}
