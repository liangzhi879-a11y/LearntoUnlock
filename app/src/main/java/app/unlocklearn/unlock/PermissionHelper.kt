package app.unlocklearn.unlock

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * 权限工具。
 *
 * - SYSTEM_ALERT_WINDOW：覆盖答题层所需
 * - PACKAGE_USAGE_STATS：娱乐 App 模式监听前台应用所需（特殊权限，需跳系统设置授权）
 *
 * 这两个都是「特殊权限」，不能用 requestPermissions 运行时弹窗，必须跳到系统设置页让用户手动开。
 *
 * 国产 ROM（小米/华为/OPPO/vivo）可能隐藏「使用情况访问」入口，
 * 这里提供多套跳转方案，逐个尝试直至成功。
 */
object PermissionHelper {

    private const val TAG = "PermissionHelper"

    // —— SYSTEM_ALERT_WINDOW ——

    fun canDrawOverlays(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
                .onFailure { Log.w(TAG, "覆盖权限页跳转失败，降级到应用详情", it) }
                .onFailure { openAppDetailSettings(context) }
        }
    }

    // —— PACKAGE_USAGE_STATS ——

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 跳转到「使用情况访问权限」设置页。
     *
     * 依次尝试：
     * 1. 标准 ACTION_USAGE_ACCESS_SETTINGS（AOSP / 原生 Android / 部分国产 ROM）
     * 2. 小米 MIUI 专属入口
     * 3. 华为 EMUI / 鸿蒙专属入口
     * 4. OPPO ColorOS 专属入口
     * 5. vivo OriginOS / FuntouchOS 专属入口
     * 6. 应用详情页（兜底，让用户手动找权限项）
     */
    fun openUsageAccessSettings(context: Context) {
        val intents = buildList {
            // 1. 标准入口
            add(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            // 2. 小米 MIUI：安全中心 -> 使用情况访问
            add(Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            // 3. 华为 EMUI / 鸿蒙：权限管理
            add(Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.permissionmanager.ui.MainActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            // 4. OPPO ColorOS：权限隐私
            add(Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            // 5. vivo：权限管理
            add(Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        for (intent in intents) {
            if (tryStartActivity(context, intent)) {
                Log.i(TAG, "使用情况访问页跳转成功: ${intent.component ?: intent.action}")
                return
            }
        }
        // 6. 全部失败，降级到应用详情页
        Log.w(TAG, "所有使用情况访问入口都不可用，降级到应用详情页")
        openAppDetailSettings(context)
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse { false }
    }

    /** 跳转到本应用的应用详情页（权限 → 使用情况访问，让用户手动开） */
    fun openAppDetailSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * 检测当前 ROM 类型，用于在 UI 上给出针对性引导文案。
     */
    fun detectRom(): RomType {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> RomType.MIUI
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> RomType.EMUI
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> RomType.COLOR_OS
            manufacturer.contains("vivo") -> RomType.ORIGIN_OS
            manufacturer.contains("samsung") -> RomType.ONE_UI
            else -> RomType.AOSP
        }
    }

    enum class RomType(val displayName: String, val usageAccessGuide: String) {
        AOSP("原生 Android", "设置 → 应用 → 特殊应用访问 → 使用情况访问 → UnlockLearn"),
        MIUI("小米 MIUI", "设置 → 隐私保护 → 使用情况访问 → UnlockLearn（或安全中心 → 权限管理）"),
        EMUI("华为 EMUI/鸿蒙", "设置 → 应用 → 权限管理 → 使用情况访问 → UnlockLearn"),
        COLOR_OS("OPPO ColorOS", "设置 → 权限与隐私 → 使用情况访问 → UnlockLearn"),
        ORIGIN_OS("vivo OriginOS", "设置 → 应用与权限 → 权限管理 → 使用情况访问 → UnlockLearn"),
        ONE_UI("三星 OneUI", "设置 → 应用 → 特殊访问 → 使用情况访问 → UnlockLearn")
    }
}
