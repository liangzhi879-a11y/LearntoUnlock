package app.unlocklearn.scheduler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * SM-2 算法核心测试。
 *
 * 覆盖：
 * - 新卡首次答对：间隔 1 天
 * - 二次答对：间隔 6 天
 * - 三次起：interval * EF
 * - 答错：重置 repetitions，进入 RELEARNING
 * - EF 下限 1.3
 * - 跳过：等同答错
 */
class SM2Test {

    private val fixedNow = 1_700_000_000_000L
    private val dayMs = 86_400_000L

    @Test
    fun `new card first correct answer schedules 1 day`() {
        val state = SM2.newState("item_1", "bank_1", fixedNow)
        val updated = SM2.schedule(state, AnswerQuality.GOOD, fixedNow)

        assertEquals(1, updated.repetitions)
        assertEquals(1, updated.intervalDays)
        assertEquals(fixedNow + dayMs, updated.dueAt)
        assertEquals(CardStatus.LEARNING, updated.status)
    }

    @Test
    fun `second correct answer schedules 6 days`() {
        val state = SM2.newState("item_1", "bank_1", fixedNow).copy(
            repetitions = 1,
            intervalDays = 1,
            status = CardStatus.LEARNING,
            dueAt = fixedNow + dayMs
        )
        val updated = SM2.schedule(state, AnswerQuality.GOOD, fixedNow + dayMs)

        assertEquals(2, updated.repetitions)
        assertEquals(6, updated.intervalDays)
        assertEquals(CardStatus.REVIEW, updated.status)
    }

    @Test
    fun `third correct answer multiplies interval by ease factor`() {
        val state = SM2.newState("item_1", "bank_1", fixedNow).copy(
            repetitions = 2,
            intervalDays = 6,
            easeFactor = 2.5,
            status = CardStatus.REVIEW
        )
        val updated = SM2.schedule(state, AnswerQuality.GOOD, fixedNow)

        assertEquals(3, updated.repetitions)
        // 6 * 2.5 = 15
        assertEquals(15, updated.intervalDays)
        assertEquals(fixedNow + 15 * dayMs, updated.dueAt)
    }

    @Test
    fun `wrong answer resets repetitions and enters relearning`() {
        val state = SM2.newState("item_1", "bank_1", fixedNow).copy(
            repetitions = 5,
            intervalDays = 30,
            status = CardStatus.REVIEW
        )
        val updated = SM2.schedule(state, AnswerQuality.FAILED, fixedNow)

        assertEquals(0, updated.repetitions)
        assertEquals(0, updated.intervalDays)
        assertEquals(CardStatus.RELEARNING, updated.status)
        assertEquals(1, updated.lapses)
    }

    @Test
    fun `skip is treated as failure`() {
        val state = SM2.newState("item_1", "bank_1", fixedNow).copy(
            repetitions = 3,
            intervalDays = 15,
            status = CardStatus.REVIEW
        )
        val updated = SM2.schedule(state, AnswerQuality.SKIPPED, fixedNow)

        assertEquals(0, updated.repetitions)
        assertEquals(1, updated.lapses)
        assertEquals(CardStatus.RELEARNING, updated.status)
    }

    @Test
    fun `ease factor has floor of 1_3`() {
        // 连续多次答错，EF 不应低于 1.3
        var state = SM2.newState("item_1", "bank_1", fixedNow)
        repeat(10) {
            state = SM2.schedule(state, AnswerQuality.FAILED, fixedNow + it * dayMs)
        }
        assertTrue(state.easeFactor >= 1.3, "EF=${state.easeFactor} 不应低于 1.3")
    }

    @Test
    fun `perfect answer increases ease factor`() {
        val state = SM2.newState("item_1", "bank_1", fixedNow).copy(
            repetitions = 1,
            intervalDays = 1,
            easeFactor = 2.5,
            status = CardStatus.LEARNING
        )
        val updated = SM2.schedule(state, AnswerQuality.PERFECT, fixedNow)
        assertTrue(updated.easeFactor > 2.5, "完美答对应提升 EF，实际=${updated.easeFactor}")
    }

    @Test
    fun `fromResult maps binary result to quality`() {
        assertEquals(AnswerQuality.PERFECT, AnswerQuality.fromResult(correct = true, responseMs = 1_000))
        assertEquals(AnswerQuality.GOOD, AnswerQuality.fromResult(correct = true, responseMs = 5_000))
        assertEquals(AnswerQuality.PASS, AnswerQuality.fromResult(correct = true, responseMs = 10_000))
        assertEquals(AnswerQuality.FAILED, AnswerQuality.fromResult(correct = false, responseMs = 1_000))
        assertEquals(AnswerQuality.SKIPPED, AnswerQuality.fromResult(correct = false, responseMs = 0, skipped = true))
    }
}
