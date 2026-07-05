package app.unlocklearn.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.unlocklearn.data.db.entity.BankEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(banks: List<BankEntity>)

    @Query("SELECT * FROM banks ORDER BY importedAt ASC")
    fun getAll(): Flow<List<BankEntity>>

    @Query("SELECT * FROM banks WHERE enabled = 1 ORDER BY importedAt ASC")
    fun getEnabled(): Flow<List<BankEntity>>

    @Query("SELECT * FROM banks WHERE enabled = 1 ORDER BY importedAt ASC")
    suspend fun getEnabledNow(): List<BankEntity>

    @Query("UPDATE banks SET enabled = :enabled WHERE bankId = :bankId")
    suspend fun setEnabled(bankId: String, enabled: Boolean)

    @Query("SELECT COUNT(*) FROM banks")
    suspend fun count(): Int
}
