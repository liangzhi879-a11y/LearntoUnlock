package app.unlocklearn.unlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import app.unlocklearn.overlay.OverlayActivity
import app.unlocklearn.pref.AppSettings
import app.unlocklearn.pref.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 娱乐 App 监听前台 Service。
 *
 * 通过 UsageStatsManager 轮询当前前台应用包名，检测到打开白名单 App 时
 * 按概率触发答题覆盖层。
 *
 * 权限：需用户在系统设置授权「使用情况访问权限」
 * （android.permission.PACKAGE_USAGE_STATS，特殊权限非运行时授权）。
 *
 * 防抖：同一 App 在 [COOLDOWN_MS] 内只触发一次，避免用户切回时反复弹窗。
 */
class AppWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        startWatching()
        Log.i(TAG, "AppWatchService created, watching foreground apps")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 已在 onCreate 启动监听，这里只需保持前台
        return START_STICKY
    }

    override fun onDestroy() {
        watchJob?.cancel()
        scope.cancel()
        super.onDestroy()
        Log.i(TAG, "AppWatchService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startWatching() {
        watchJob = scope.launch {
            while (true) {
                if (!hasUsageStatsPermission()) {
                    Log.w(TAG, "无使用情况访问权限，等待用户授权…")
                    delay(POLL_INTERVAL_MS * 3)
                    continue
                }
                val settings = AppSettings(this@AppWatchService).snapshot()
                if (!settings.appMode) {
                    // 模式关闭，低频待命
                    delay(POLL_INTERVAL_MS * 3)
                    continue
                }
                val pkg = getForegroundPackage()
                if (pkg != null && EntertainmentApps.isEntertainment(pkg)) {
                    if (shouldTrigger(pkg, settings.probability)) {
                        Log.i(TAG, "检测到娱乐 App: $pkg，触发答题")
                        triggerOverlay(pkg)
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** 概率判定 + 冷却时间防抖 */
    private suspend fun shouldTrigger(pkg: String, probability: Float): Boolean {
        val now = System.currentTimeMillis()
        val lastTrigger = getLastTriggerTime(pkg)
        if (now - lastTrigger < COOLDOWN_MS) return false
        if (Math.random() >= probability) return false
        saveTriggerTime(pkg, now)
        return true
    }

    private fun getForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // 查询最近 10 秒内的使用记录，取最后一条即当前前台
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            now - 10_000,
            now
        ) ?: return null
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun triggerOverlay(pkg: String) {
        val intent = Intent(this, OverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(EXTRA_SOURCE, SOURCE_APP)
            putExtra(EXTRA_PACKAGE, pkg)
        }
        startActivity(intent)
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "娱乐 App 监听运行中",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "检测打开娱乐 App 时触发答题"
                    setShowBadge(false)
                }
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("UnlockLearn")
            .setContentText("娱乐 App 守护已启用")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    // —— 冷却时间记录（写入 DataStore，每个包名独立 key）——

    private suspend fun getLastTriggerTime(pkg: String): Long {
        val prefs = baseContext.dataStore.data.first()
        return prefs[longPreferencesKey("last_trigger_$pkg")] ?: 0L
    }

    private suspend fun saveTriggerTime(pkg: String, time: Long) {
        baseContext.dataStore.edit { prefs ->
            prefs[longPreferencesKey("last_trigger_$pkg")] = time
        }
    }

    companion object {
        private const val TAG = "AppWatchService"
        private const val CHANNEL_ID = "app_watch"
        private const val NOTIFICATION_ID = 1002
        private const val POLL_INTERVAL_MS = 2000L
        private const val COOLDOWN_MS = 5 * 60 * 1000L  // 5 分钟冷却

        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_PACKAGE = "extra_package"
        const val SOURCE_APP = "app"
        const val SOURCE_UNLOCK = "unlock"

        fun start(context: Context) {
            val intent = Intent(context, AppWatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AppWatchService::class.java))
        }
    }
}
