package app.unlocklearn.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.unlocklearn.data.db.entity.SchedulingStateEntity

@Dao
interface SchedulingDao {
    @Upsert
    suspend fun upsert(state: SchedulingStateEntity)

    @Query("SELECT * FROM scheduling_state WHERE bankId IN (:bankIds)")
    suspend fun getByBanks(bankIds: List<String>): List<SchedulingStateEntity>

    @Query("SELECT * FROM scheduling_state WHERE itemId = :itemId")
    suspend fun getByItem(itemId: String): SchedulingStateEntity?

    @Query("SELECT itemId FROM scheduling_state WHERE bankId = :bankId")
    suspend fun getItemIdsByBank(bankId: String): List<String>
}
