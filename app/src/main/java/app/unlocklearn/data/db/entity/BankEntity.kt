package app.unlocklearn.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 题库元数据表。每个 JSON 题库对应一行。
 */
@Entity(tableName = "banks")
data class BankEntity(
    @PrimaryKey val bankId: String,
    val subject: String,
    val category: String,
    val name: String,
    val nameEn: String?,
    val cefrLevel: String?,
    val totalItems: Int,
    val source: String?,
    val license: String?,
    val updatedAt: String,
    /** 用户是否启用此题库参与解锁答题 */
    val enabled: Boolean = false,
    val importedAt: Long = System.currentTimeMillis()
)
