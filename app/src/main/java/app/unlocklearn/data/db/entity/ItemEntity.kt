package app.unlocklearn.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 题目表。payload 以 JSON string 整体存储，读取时反序列化为 sealed Payload。
 *
 * 设计理由：不同题型字段差异大，拆字段会产生大量 nullable 列；
 * 整体存 JSON 通用、扩展性好，新增题型无需改表结构。
 */
@Entity(
    tableName = "items",
    indices = [Index("bankId")]
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val bankId: String,
    /** ItemType.jsonName */
    val type: String,
    val difficulty: Int?,
    /** JSON array string，如 ["verb","high_freq"] */
    val tags: String,
    /** 完整 payload 的 JSON string */
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis()
)
