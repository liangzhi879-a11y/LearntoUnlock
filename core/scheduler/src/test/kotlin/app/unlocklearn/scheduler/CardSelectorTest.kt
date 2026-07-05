package app.unlocklearn.scheduler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 选牌策略测试。
 *
 * 覆盖优先级链：
 * 1. 到期复习优先于新卡
 * 2. 学习中卡片优先于新卡
 * 3. 新卡受每日上限约束
 * 4. 兜底抽查已掌握卡
 * 5. 空列表返回 null
 */
class CardSelectorTest {

    private val now = 1_700_000_000_000L
    private val dayMs = 86_400_000L
    private val selector = CardSelector(newCardsPerDay = 3, randomSeed = 42L)

    @Test
    fun `due review card takes priority over new card`() {
        val newCard = SM2.newState("new_1", "bank_1", now)
        val dueCard = SM2.newState("due_1", "bank_1", now).copy(
            status = CardStatus.REVIEW,
            repetitions = 3,
            intervalDays = 7,
            dueAt = now - 1000 // 已过期
        )

        val picked = selector.selectCard(listOf(newCard, dueCard), now)
        assertNotNull(picked)
        assertEquals("due_1", picked.itemId)
    }

    @Test
    fun `learning card takes priority over new card`() {
        val newCard = SM2.newState("new_1", "bank_1", now)
        val learningCard = SM2.newState("learn_1", "bank_1", now).copy(
            status = CardStatus.LEARNING,
            repetitions = 1,
            dueAt = now - 5000
        )

        val picked = selector.selectCard(listOf(newCard, learningCard), now)
        assertNotNull(picked)
        assertEquals("learn_1", picked.itemId)
    }

    @Test
    fun `most overdue review card is picked first`() {
        val cardA = SM2.newState("a", "bank_1", now).copy(status = CardStatus.REVIEW, dueAt = now - 1000)
        val cardB = SM2.newState("b", "bank_1", now).copy(status = CardStatus.REVIEW, dueAt = now - 10000)
        val cardC = SM2.newState("c", "bank_1", now).copy(status = CardStatus.REVIEW, dueAt = now - 5000)

        val picked = selector.selectCard(listOf(cardA, cardB, cardC), now)
        assertNotNull(picked)
        assertEquals("b", picked.itemId) // 最过期
    }

    @Test
    fun `new card picked when no review or learning cards due`() {
        val newCard = SM2.newState("new_1", "bank_1", now)
        val futureReview = SM2.newState("r_1", "bank_1", now).copy(
            status = CardStatus.REVIEW,
            dueAt = now + dayMs // 未到期
        )

        val picked = selector.selectCard(listOf(newCard, futureReview), now, newLearnedToday = 0)
        assertNotNull(picked)
        assertEquals("new_1", picked.itemId)
    }

    @Test
    fun `new cards blocked when daily limit reached`() {
        val newCard = SM2.newState("new_1", "bank_1", now)
        val picked = selector.selectCard(listOf(newCard), now, newLearnedToday = 3)
        // 每日上限 3，已学 3，新卡被阻塞，且无兜底卡 -> null
        assertNull(picked)
    }

    @Test
    fun `fallback random pick from mastered cards when nothing else available`() {
        val mastered = SM2.newState("m_1", "bank_1", now).copy(
            status = CardStatus.REVIEW,
            repetitions = 5,
            dueAt = now + dayMs // 未到期
        )
        // 无到期复习、无学习中、无新卡（上限已达）
        val picked = selector.selectCard(listOf(mastered), now, newLearnedToday = 99)
        assertNotNull(picked)
        assertEquals("m_1", picked.itemId)
    }

    @Test
    fun `empty state list returns null`() {
        assertNull(selector.selectCard(emptyList(), now))
    }

    @Test
    fun `hasAvailableCard reflects selectCard result`() {
        assertTrue(selector.hasAvailableCard(emptyList(), now) == false)
        val newCard = SM2.newState("n", "bank_1", now)
        assertTrue(selector.hasAvailableCard(listOf(newCard), now, newLearnedToday = 0) == true)
    }
}
