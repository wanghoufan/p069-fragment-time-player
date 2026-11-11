package com.wanghoufan.fragmentplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PresetEntity::class,
        CourseEntity::class,
        CheckpointEntity::class,
        TimerSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao
    abstract fun courseDao(): CourseDao
    abstract fun checkpointDao(): CheckpointDao
    abstract fun sessionDao(): SessionDao

    companion object {
        private const val DB_NAME = "fragment_time.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun create(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            ).build().also { instance = it }
        }
    }
}