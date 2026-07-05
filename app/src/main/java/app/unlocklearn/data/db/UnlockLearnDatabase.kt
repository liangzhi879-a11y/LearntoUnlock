package app.unlocklearn.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.unlocklearn.data.db.dao.BankDao
import app.unlocklearn.data.db.dao.ItemDao
import app.unlocklearn.data.db.dao.SchedulingDao
import app.unlocklearn.data.db.entity.BankEntity
import app.unlocklearn.data.db.entity.ItemEntity
import app.unlocklearn.data.db.entity.SchedulingStateEntity

@Database(
    entities = [
        BankEntity::class,
        ItemEntity::class,
        SchedulingStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class UnlockLearnDatabase : RoomDatabase() {
    abstract fun bankDao(): BankDao
    abstract fun itemDao(): ItemDao
    abstract fun schedulingDao(): SchedulingDao

    companion object {
        @Volatile
        private var INSTANCE: UnlockLearnDatabase? = null

        fun get(context: Context): UnlockLearnDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UnlockLearnDatabase::class.java,
                    "unlocklearn.db"
                ).build().also { INSTANCE = it }
            }
    }
}
