package app.unlocklearn.ui.effect

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import app.unlocklearn.ui.theme.Success
import app.unlocklearn.ui.theme.Orange

/**
 * 答题反馈动效。震动 + 粒子 + 摇晃，全部用 Compose 原生能力，不依赖 Lottie 资源。
 */
object FeedbackEffect {

    /** 答对：轻震 30ms */
    fun vibrateCorrect(context: Context) {
        vibrate(context, durationMs = 30, amplitude = 80)
    }

    /** 答错：双震 pattern（100ms 停 80ms 震 100ms） */
    fun vibrateWrong(context: Context) {
        val vibrator = getVibrator(context) ?: return
        val timings = longArrayOf(0, 100, 80, 100)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    private fun vibrate(context: Context, durationMs: Long, amplitude: Int) {
        val vibrator = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255))
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun getVibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}

/**
 * 答对粒子爆发。16 个粒子从中心向外扩散 + 淡出，800ms。
 */
@Composable
fun CorrectBurst(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Orange
) {
    if (!trigger) return
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(800))
    }
    Canvas(modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val particleCount = 16
        val maxDistance = size.minDimension * 0.4f
        for (i in 0 until particleCount) {
            val angle = (i * 360f / particleCount) * (PI / 180).toFloat()
            val distance = maxDistance * progress.value
            val x = center.x + cos(angle) * distance
            val y = center.y + sin(angle) * distance
            val radius = (10f * (1f - progress.value)).coerceAtLeast(0f)
            drawCircle(
                color = color.copy(alpha = 1f - progress.value),
                radius = radius,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * 答错卡片摇晃。水平方向正弦衰减，400ms。
 */
@Composable
fun ShakeOnTrigger(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger) {
            shake.snapTo(0f)
            shake.animateTo(1f, animationSpec = tween(400))
        }
    }
    val offsetX = (sin(shake.value * PI * 8) * 12 * (1 - shake.value)).toFloat()
    Box(modifier = modifier.offset { IntOffset(offsetX.roundToInt(), 0) }) {
        content()
    }
}
