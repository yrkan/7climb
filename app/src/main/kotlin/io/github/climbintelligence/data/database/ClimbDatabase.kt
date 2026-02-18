package io.github.climbintelligence.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ClimbEntity::class, AttemptEntity::class],
    version = 1,
    exportSchema = true
)
abstract class ClimbDatabase : RoomDatabase() {

    abstract fun climbDao(): ClimbDao
    abstract fun attemptDao(): AttemptDao

    companion object {
        @Volatile
        private var INSTANCE: ClimbDatabase? = null

        fun getInstance(context: Context): ClimbDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ClimbDatabase::class.java,
                    "climb_intelligence.db"
                )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
