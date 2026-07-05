package app.unlocklearn.unlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机自启接收器。开机完成后启动解锁监听前台 Service。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed, starting UnlockWatchService")
            UnlockWatchService.start(context)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
