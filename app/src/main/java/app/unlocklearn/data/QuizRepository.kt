package app.unlocklearn.data

import android.content.Context
import app.unlocklearn.data.db.UnlockLearnDatabase
import app.unlocklearn.data.db.entity.BankEntity
import app.unlocklearn.quizbank.model.Item
import app.unlocklearn.scheduler.AnswerQuality
import app.unlocklearn.scheduler.SchedulingState
import app.unlocklearn.scheduler.SM2
import kotlinx.coroutines.flow.Flow

/**
 * 数据仓库。统一访问题库、题目、调度状态。
 */
class QuizRepository(private val context: Context) {

    private val db = UnlockLearnDatabase.get(context)
    private val bankDao = db.bankDao()
    private val itemDao = db.itemDao()
    private val schedulingDao = db.schedulingDao()

    /** 所有题库（观察用） */
    val allBanks: Flow<List<BankEntity>> = bankDao.getAll()

    /** 首次启动导入题库 */
    suspend fun ensureImported() {
        BankImporter(context).importIfNeeded()
    }

    suspend fun setBankEnabled(bankId: String, enabled: Boolean) {
        bankDao.setEnabled(bankId, enabled)
    }

    suspend fun getEnabledBankIds(): List<String> =
        bankDao.getEnabledNow().map { it.bankId }

    /**
     * 获取所有启用题库的调度状态，供 CardSelector 选牌。
     */
    suspend fun getSchedulingStatesForEnabledBanks(): List<SchedulingState> {
        val bankIds = getEnabledBankIds()
        if (bankIds.isEmpty()) return emptyList()
        return schedulingDao.getByBanks(bankIds).map { EntityMapper.toSchedulingState(it) }
    }

    suspend fun getItem(itemId: String): Item? {
        val entity = itemDao.getById(itemId) ?: return null
        return EntityMapper.toItem(entity)
    }

    /**
     * 记录答题结果，更新 SM-2 调度状态。
     */
    suspend fun recordAnswer(itemId: String, quality: AnswerQuality) {
        val entity = schedulingDao.getByItem(itemId) ?: return
        val state = EntityMapper.toSchedulingState(entity)
        val newState = SM2.schedule(state, quality)
        schedulingDao.upsert(EntityMapper.toEntity(newState))
    }

    /**
     * 选牌并返回对应 Item。供 OverlayViewModel 调用。
     * @return 选中的 Item，若无可用卡片返回 null
     */
    suspend fun selectNextItem(): Item? {
        val states = getSchedulingStatesForEnabledBanks()
        if (states.isEmpty()) return null
        val selector = app.unlocklearn.scheduler.CardSelector()
        val picked = selector.selectCard(states) ?: return null
        return getItem(picked.itemId)
    }
}
