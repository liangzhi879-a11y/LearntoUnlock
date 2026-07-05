package app.unlocklearn.unlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import app.unlocklearn.overlay.OverlayActivity
import app.unlocklearn.pref.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 解锁监听前台 Service。
 *
 * 作用：
 * 1. 保活进程，使进程优先级提升，避免国产 ROM 杀后台导致收不到广播
 * 2. **动态注册** ACTION_USER_PRESENT / ACTION_SCREEN_ON 接收器
 *    （Android 8.0+ 对静态注册的隐式广播有限制，动态注册 + 前台 Service 才可靠）
 * 3. 内存缓存当前配置（解锁瞬间同步读取，避免 goAsync 触发后台启动 Activity 限制）
 * 4. 展示常驻通知，告知用户"解锁学习"正在运行
 *
 * 注意：本 Service 不做任何隐藏行为，通知明确可见，符合合规要求。
 */
class UnlockWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var configJob: Job? = null

    /** 内存缓存当前配置，解锁瞬间同步读取，避免协程延迟 */
    @Volatile
    private var cachedUnlockMode: Boolean = true

    @Volatile
    private var cachedProbability: Float = AppSettings.DEFAULT_PROBABILITY

    /** 动态注册的解锁接收器 */
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> {
                    Log.i(TAG, "USER_PRESENT 收到（动态注册）")
                    handleUnlock(context)
                }
                Intent.ACTION_SCREEN_ON -> {
                    // 部分 ROM 不发 USER_PRESENT，用 SCREEN_ON 兜底（但 SCREEN_ON 在锁屏界面也会触发，需配合 KEYGUARD 判断）
                    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                    if (!km.isKeyguardLocked) {
                        Log.i(TAG, "SCREEN_ON 且已解锁，兜底触发")
                        handleUnlock(context)
                    }
                }
            }
        }
    }

    /** 同步处理解锁事件：读内存配置 → 概率判定 → 启动 OverlayActivity */
    private fun handleUnlock(context: Context) {
        if (!cachedUnlockMode) {
            Log.i(TAG, "锁屏模式未启用，跳过")
            return
        }
        if (Math.random() >= cachedProbability) {
            Log.i(TAG, "概率未命中 (${cachedProbability})，跳过")
            return
        }
        Log.i(TAG, "触发答题覆盖层（概率 ${cachedProbability}）")
        val overlayIntent = Intent(context, OverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(AppWatchService.EXTRA_SOURCE, AppWatchService.SOURCE_UNLOCK)
        }
        runCatching { context.startActivity(overlayIntent) }
            .onFailure { Log.e(TAG, "启动 OverlayActivity 失败", it) }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerUnlockReceiver()
        startConfigRefresh()
        Log.i(TAG, "Service created, 已动态注册解锁监听")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 已在 onCreate 完成注册，这里只需保持前台
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlockReceiver) }
        configJob?.cancel()
        scope.cancel()
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 动态注册 USER_PRESENT + SCREEN_ON */
    private fun registerUnlockReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        // RECEIVER_NOT_EXPORTED：Android 13+ 动态注册需指定导出标志；系统广播仍可收到
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(unlockReceiver, filter)
        }
    }

    /** 后台定期刷新配置到内存缓存 */
    private fun startConfigRefresh() {
        configJob = scope.launch {
            while (true) {
                runCatching {
                    val snapshot = AppSettings(this@UnlockWatchService).snapshot()
                    cachedUnlockMode = snapshot.unlockMode
                    cachedProbability = snapshot.probability
                }.onFailure { Log.w(TAG, "刷新配置失败", it) }
                delay(CONFIG_REFRESH_MS)
            }
        }
    }

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
        private const val CONFIG_REFRESH_MS = 5000L

        fun start(context: Context) {
            val intent = Intent(context, UnlockWatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UnlockWatchService::class.java))
        }
    }
}
