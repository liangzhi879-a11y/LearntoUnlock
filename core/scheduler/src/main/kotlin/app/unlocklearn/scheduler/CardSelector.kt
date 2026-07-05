package app.unlocklearn.scheduler

/**
 * 选牌策略。解锁触发答题时调用 [selectCard]。
 *
 * 优先级（符合间隔重复学习法）：
 * 1. 到期复习（REVIEW 且 dueAt <= now）—— 最高优先，错过会遗忘
 * 2. 学习中卡片（LEARNING / RELEARNING）—— 短间隔巩固
 * 3. 新卡（NEW）—— 按每日新卡上限补充
 * 4. 兜底：已掌握卡随机抽查
 *
 * 每次解锁只出 1 题，避免打扰过重。
 */
class CardSelector(
    private val newCardsPerDay: Int = 20,
    private val randomSeed: Long? = null
) {

    private val rng: java.util.Random = randomSeed?.let { java.util.Random(it) } ?: java.util.Random()

    /**
     * @param states 当前题库下所有卡片的调度状态
     * @param now 当前时间戳
     * @param newLearnedToday 今日已学新卡数
     * @return 选中的卡片，若无可用返回 null
     */
    fun selectCard(
        states: List<SchedulingState>,
        now: Long = System.currentTimeMillis(),
        newLearnedToday: Int = 0
    ): SchedulingState? {
        if (states.isEmpty()) return null

        // 1. 到期复习
        val dueReviews = states.filter {
            it.status == CardStatus.REVIEW && it.dueAt <= now
        }
        if (dueReviews.isNotEmpty()) {
            // 取最过期的一张（dueAt 最小）
            return dueReviews.minByOrNull { it.dueAt }
        }

        // 2. 学习中卡片（含重学）
        val learning = states.filter {
            it.status in setOf(CardStatus.LEARNING, CardStatus.RELEARNING) && it.dueAt <= now
        }
        if (learning.isNotEmpty()) {
            return learning.minByOrNull { it.dueAt }
        }

        // 3. 新卡（受每日上限约束）
        if (newLearnedToday < newCardsPerDay) {
            val newCards = states.filter { it.status == CardStatus.NEW }
            if (newCards.isNotEmpty()) {
                // 简单顺序取第一张，避免随机导致部分卡片长期不学
                return newCards.first()
            }
        }

        // 4. 兜底：从已掌握卡里随机抽查 1 张（防止"无卡可学"导致解锁无答题）
        val mastered = states.filter { it.status == CardStatus.REVIEW && it.repetitions >= 3 }
        if (mastered.isNotEmpty()) {
            return mastered[rng.nextInt(mastered.size)]
        }

        return null
    }

    /**
     * 判断当前是否有任何卡片可学（用于解锁监听器决定是否触发答题）。
     */
    fun hasAvailableCard(
        states: List<SchedulingState>,
        now: Long = System.currentTimeMillis(),
        newLearnedToday: Int = 0
    ): Boolean = selectCard(states, now, newLearnedToday) != null
}
