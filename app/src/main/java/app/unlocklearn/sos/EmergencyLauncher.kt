package app.unlocklearn.sos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * 应急呼叫。
 *
 * - [dial]：跳拨号盘，无需任何权限，最安全
 * - [call]：直接拨出，需 CALL_PHONE 权限
 *
 * 覆盖层右上角常驻按钮调用 [dial]，
 * 即使用户未授 CALL_PHONE 权限也能应急。
 */
object EmergencyLauncher {

    /** 默认急救号码 */
    const val DEFAULT_NUMBER = "120"

    /**
     * 跳转系统拨号盘，号码已预填。无需权限。
     */
    fun dial(context: Context, phone: String = DEFAULT_NUMBER) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phone")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * 直接拨出。需 CALL_PHONE 权限，否则会抛 SecurityException。
     * 调用前应检查权限，无权限时回退到 [dial]。
     */
    fun call(context: Context, phone: String = DEFAULT_NUMBER) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phone")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            // 无权限或拨号失败，回退到拨号盘
            dial(context, phone)
        }
    }

    /**
     * 检查应用是否有 SYSTEM_ALERT_WINDOW 权限（覆盖层必需）。
     */
    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /**
     * 跳转到系统的"显示在其他应用上层"设置页。
     */
    fun requestOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(intent) }
    }
}
