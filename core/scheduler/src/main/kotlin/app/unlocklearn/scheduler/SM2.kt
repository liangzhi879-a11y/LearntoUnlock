package app.unlocklearn.scheduler

import kotlin.math.roundToInt

/**
 * SM-2 间隔重复算法实现。
 *
 * 参考：SuperMemo SM-2 (1987)，Anki 30 年验证。
 * 输入：卡片当前状态 + 答题质量(0-5)
 * 输出：更新后的状态（新间隔、新到期时间、新 EF）
 *
 * 算法核心：
 * 1. quality < 3 → 重置 repetitions=0，interval=1 天
 * 2. quality >= 3 → 递增间隔：1天 → 6天 → interval*EF
 * 3. EF 调整：ef += 0.1 - (5-q)*(0.08 + (5-q)*0.02)，下限 1.3
 *
 * 后续可平滑替换为 FSRS：接口 [schedule] 不变，仅替换实现。
 */
object SM2 {

    /** 一天的毫秒数 */
    private const val DAY_MS = 86_400_000L

    /** 学习中卡片的短间隔（首次答对后 10 分钟再复习，巩固记忆） */
    private const val LEARNING_STEP_MS = 10 * 60_000L

    /** EF 下限，SM-2 规定 */
    private const val EF_MIN = 1.3

    /** EF 初始值 */
    const val EF_INITIAL = 2.5

    /**
     * 根据答题质量推进卡片状态。
     *
     * @param state 卡片当前状态
     * @param quality 答题质量 0-5
     * @param now 当前时间戳，默认系统时间，便于测试注入
     */
    fun schedule(
        state: SchedulingState,
        quality: AnswerQuality,
        now: Long = System.currentTimeMillis()
    ): SchedulingState {
        val q = quality.score.coerceIn(0, 5)
        var ef = state.easeFactor
        var reps = state.repetitions
        var interval: Int
        var status: CardStatus
        var lapses = state.lapses

        if (q < 3) {
            // 答错或跳过：重置
            reps = 0
            interval = 0
            status = if (state.status == CardStatus.NEW) CardStatus.LEARNING else CardStatus.RELEARNING
            if (state.status == CardStatus.REVIEW) lapses += 1
            // 短间隔重学，不走天数间隔
            val due = now + LEARNING_STEP_MS
            return state.copy(
                repetitions = reps,
                intervalDays = interval,
                easeFactor = adjustEf(ef, q),
                lastReviewedAt = now,
                dueAt = due,
                lapses = lapses,
                status = status
            )
        }

        // 答对：递增间隔
        reps += 1
        interval = when (reps) {
            1 -> 1
            2 -> 6
            else -> (state.intervalDays * ef).roundToInt().coerceAtLeast(1)
        }
        status = if (reps <= 1) CardStatus.LEARNING else CardStatus.REVIEW
        val due = now + interval * DAY_MS

        return state.copy(
            repetitions = reps,
            intervalDays = interval,
            easeFactor = adjustEf(ef, q),
            lastReviewedAt = now,
            dueAt = due,
            lapses = lapses,
            status = status
        )
    }

    /**
     * EF 调整公式：ef += 0.1 - (5-q)*(0.08 + (5-q)*0.02)
     * 下限 1.3。
     */
    private fun adjustEf(currentEf: Double, q: Int): Double {
        val delta = 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)
        return (currentEf + delta).coerceAtLeast(EF_MIN)
    }

    /**
     * 初始化一张新卡的状态。
     */
    fun newState(itemId: String, bankId: String, now: Long = System.currentTimeMillis()): SchedulingState =
        SchedulingState(
            itemId = itemId,
            bankId = bankId,
            repetitions = 0,
            intervalDays = 0,
            easeFactor = EF_INITIAL,
            lastReviewedAt = 0L,
            dueAt = now, // 新卡立即可学
            lapses = 0,
            status = CardStatus.NEW,
            createdAt = now
        )
}
