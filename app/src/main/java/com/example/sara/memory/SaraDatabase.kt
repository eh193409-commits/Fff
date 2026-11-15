package com.example.sara.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MemoryFactEntity::class,
        CommandLogEntity::class,
        ConversationMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SaraDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: SaraDatabase? = null

        fun getInstance(context: Context): SaraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SaraDatabase::class.java,
                    "sara_ai_memory.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
