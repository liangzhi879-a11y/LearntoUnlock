package app.unlocklearn.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 卡片调度状态表。对应 [app.unlocklearn.scheduler.SchedulingState]。
 *
 * 每张题目一行，记录 SM-2 算法所需的复习状态。
 * dueAt 建索引：选牌时按到期时间排序查询。
 */
@Entity(
    tableName = "scheduling_state",
    indices = [Index("bankId"), Index("dueAt")]
)
data class SchedulingStateEntity(
    @PrimaryKey val itemId: String,
    val bankId: String,
    val repetitions: Int,
    val intervalDays: Int,
    val easeFactor: Double,
    val lastReviewedAt: Long,
    val dueAt: Long,
    val lapses: Int,
    /** CardStatus.name */
    val status: String,
    val createdAt: Long
)
