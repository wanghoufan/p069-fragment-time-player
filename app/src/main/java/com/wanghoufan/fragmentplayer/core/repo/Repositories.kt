package com.wanghoufan.fragmentplayer.core.repo

import com.wanghoufan.fragmentplayer.core.model.AppSettings
import com.wanghoufan.fragmentplayer.core.model.Checkpoint
import com.wanghoufan.fragmentplayer.core.model.Course
import com.wanghoufan.fragmentplayer.core.model.LanguageMode
import com.wanghoufan.fragmentplayer.core.model.Preset
import com.wanghoufan.fragmentplayer.core.model.ThemeMode
import com.wanghoufan.fragmentplayer.core.model.TimerSessionRecord
import kotlinx.coroutines.flow.Flow

interface PresetRepository {
    val presets: Flow<List<Preset>>
    suspend fun ensureDefaults()
    suspend fun all(): List<Preset>
    suspend fun get(id: Long): Preset?
    suspend fun add(name: String, durationSec: Int): Long
    suspend fun update(preset: Preset)
    suspend fun delete(id: Long)
}

interface CourseRepository {
    val courses: Flow<List<Course>>
    suspend fun all(): List<Course>
    suspend fun get(id: Long): Course?
    suspend fun insert(course: Course): Long
    suspend fun update(course: Course)
    suspend fun updateAll(courses: List<Course>)
    suspend fun delete(ids: List<Long>)
    suspend fun count(): Int
}

interface CheckpointRepository {
    val checkpoint: Flow<Checkpoint?>
    suspend fun get(): Checkpoint?
    suspend fun save(courseId: Long, positionMs: Long)
    suspend fun clear()
}

interface SessionRepository {
    suspend fun record(record: TimerSessionRecord): Long
    suspend fun recent(limit: Int = 20): List<TimerSessionRecord>
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    val folderUri: Flow<String?>
    suspend fun current(): AppSettings
    suspend fun setTheme(mode: ThemeMode)
    suspend fun setLanguage(mode: LanguageMode)
    suspend fun setFontScale(scale: Float)
    suspend fun setVibration(enabled: Boolean)
    suspend fun setAlertSound(enabled: Boolean)
    suspend fun setTimeFlow(multiplier: Int)
    suspend fun setFolderUri(uri: String?)
    suspend fun currentFolderUri(): String?
}

data class BackupSummary(
    val presets: Int,
    val courses: Int,
    val hasCheckpoint: Boolean,
    val exportedAt: Long
)

interface BackupApi {
    suspend fun exportTo(uri: String): Result<BackupSummary>
    suspend fun peek(uri: String): Result<BackupSummary>
    suspend fun importFrom(uri: String): Result<BackupSummary>
}