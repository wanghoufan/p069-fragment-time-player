package com.wanghoufan.fragmentplayer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets ORDER BY sortOrder ASC, id ASC")
    suspend fun all(): List<PresetEntity>

    @Query("SELECT * FROM presets WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): PresetEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM presets")
    suspend fun maxSortOrder(): Int

    @Query("SELECT COUNT(*) FROM presets")
    suspend fun count(): Int

    @Insert
    suspend fun insert(preset: PresetEntity): Long

    @Insert
    suspend fun insertAll(presets: List<PresetEntity>): List<Long>

    @Update
    suspend fun update(preset: PresetEntity)

    @Delete
    suspend fun delete(preset: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM presets")
    suspend fun clear()
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY sortIndex ASC, id ASC")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses ORDER BY sortIndex ASC, id ASC")
    suspend fun all(): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): CourseEntity?

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun count(): Int

    @Insert
    suspend fun insert(course: CourseEntity): Long

    @Insert
    suspend fun insertAll(courses: List<CourseEntity>): List<Long>

    @Update
    suspend fun update(course: CourseEntity)

    @Update
    suspend fun updateAll(courses: List<CourseEntity>)

    @Query("DELETE FROM courses WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM courses")
    suspend fun clear()
}

@Dao
interface CheckpointDao {
    @Query("SELECT * FROM checkpoints ORDER BY updatedAt DESC LIMIT 1")
    fun observe(): Flow<CheckpointEntity?>

    @Query("SELECT * FROM checkpoints ORDER BY updatedAt DESC LIMIT 1")
    suspend fun get(): CheckpointEntity?

    @Upsert
    suspend fun upsert(checkpoint: CheckpointEntity)

    @Query("DELETE FROM checkpoints")
    suspend fun clear()
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM timer_sessions ORDER BY endedAt DESC, id DESC")
    suspend fun all(): List<TimerSessionEntity>

    @Query("SELECT * FROM timer_sessions ORDER BY endedAt DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<TimerSessionEntity>

    @Insert
    suspend fun insert(session: TimerSessionEntity): Long

    @Insert
    suspend fun insertAll(sessions: List<TimerSessionEntity>): List<Long>

    @Query("DELETE FROM timer_sessions")
    suspend fun clear()
}