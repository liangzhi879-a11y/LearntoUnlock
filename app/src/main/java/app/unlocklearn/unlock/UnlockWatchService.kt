package app.unlocklearn.unlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * 解锁监听前台 Service。
 *
 * 作用：
 * 1. 保活 UnlockReceiver，防止国产 ROM 杀后台导致收不到 USER_PRESENT
 * 2. 展示常驻通知，告知用户"解锁学习"正在运行
 *
 * 注意：本 Service 不做任何隐藏行为，通知明确可见，符合合规要求。
 */
class UnlockWatchService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.i(TAG, "Foreground service started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "解锁学习运行中",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "保持解锁答题功能运行"
                    setShowBadge(false)
                }
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("UnlockLearn")
            .setContentText("解锁学习已启用")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "UnlockWatchService"
        private const val CHANNEL_ID = "unlock_watch"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, UnlockWatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
