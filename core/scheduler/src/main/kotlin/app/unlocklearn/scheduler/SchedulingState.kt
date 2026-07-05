package app.unlocklearn.scheduler

/**
 * 单张卡片的调度状态。对应 SM-2 算法所需的全部字段。
 *
 * - repetitions: 连续答对次数，答错清零
 * - intervalDays: 当前复习间隔（天）
 * - easeFactor: 难度系数，初始 2.5，下限 1.3
 * - dueAt: 下次到期时间戳（毫秒）
 * - status: 卡片生命周期阶段
 */
data class SchedulingState(
    val itemId: String,
    val bankId: String,
    val repetitions: Int = 0,
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.5,
    val lastReviewedAt: Long = 0L,
    val dueAt: Long = 0L,
    val lapses: Int = 0,
    val status: CardStatus = CardStatus.NEW,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 卡片生命周期。
 * - NEW: 从未见过
 * - LEARNING: 首次学习中（间隔以分钟/小时计）
 * - REVIEW: 已进入长期间隔复习
 * - RELEARNING: 答错后重新进入学习队列
 * - SUSPENDED: 暂停（用户主动跳过长期、或低频抽查）
 */
enum class CardStatus {
    NEW, LEARNING, REVIEW, RELEARNING, SUSPENDED
}

/**
 * 答题质量评分，SM-2 标准 0-5 分。
 *
 * 解锁场景只有对/错二元结果，需结合响应时间映射：
 * - 5: 一次答对且 < 3 秒，完美回忆
 * - 4: 一次答对，3-8 秒
 * - 3: 一次答对，> 8 秒，勉强回忆
 * - 2: 答错后重试答对
 * - 1: 主动跳过
 * - 0: 答错且放弃
 */
@Suppress("unused")
enum class AnswerQuality(val score: Int) {
    PERFECT(5),
    GOOD(4),
    PASS(3),
    RETRIED(2),
    SKIPPED(1),
    FAILED(0);

    companion object {
        /**
         * 将"对/错 + 响应毫秒"二元结果映射为 SM-2 quality。
         * 仅用于解锁答题场景的简化评分。
         */
        fun fromResult(correct: Boolean, responseMs: Long, skipped: Boolean = false): AnswerQuality {
            if (skipped) return SKIPPED
            return when {
                correct && responseMs < 3_000 -> PERFECT
                correct && responseMs < 8_000 -> GOOD
                correct -> PASS
                else -> FAILED
            }
        }
    }
}
