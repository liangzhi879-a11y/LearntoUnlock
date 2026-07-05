package app.unlocklearn.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.unlocklearn.data.db.entity.ItemEntity

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ItemEntity>)

    @Query("SELECT * FROM items WHERE bankId = :bankId")
    suspend fun getByBank(bankId: String): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :itemId")
    suspend fun getById(itemId: String): ItemEntity?

    @Query("SELECT COUNT(*) FROM items WHERE bankId = :bankId")
    suspend fun countByBank(bankId: String): Int

    @Query("SELECT id FROM items WHERE bankId = :bankId")
    suspend fun getIdsByBank(bankId: String): List<String>
}
