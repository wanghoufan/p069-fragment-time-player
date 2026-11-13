package com.wanghoufan.fragmentplayer.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.model.AlertTone
import com.wanghoufan.fragmentplayer.core.model.LanguageMode
import com.wanghoufan.fragmentplayer.core.model.ThemeMode
import com.wanghoufan.fragmentplayer.core.repo.BackupApi
import com.wanghoufan.fragmentplayer.core.repo.BackupSummary
import com.wanghoufan.fragmentplayer.core.repo.SettingsRepository
import com.wanghoufan.fragmentplayer.data.db.AppDatabase
import com.wanghoufan.fragmentplayer.data.db.CheckpointEntity
import com.wanghoufan.fragmentplayer.data.db.CourseEntity
import com.wanghoufan.fragmentplayer.data.db.PresetEntity
import com.wanghoufan.fragmentplayer.data.db.TimerSessionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(
    context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository
) : BackupApi {
    private val appContext = context.applicationContext

    override suspend fun exportTo(uri: String): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val payload = snapshotPayload()
            writeText(uri, BackupCodec.encode(payload))
            Result.success(BackupCodec.summary(payload))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(wrap(error))
        }
    }

    override suspend fun peek(uri: String): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            Result.success(BackupCodec.summary(BackupCodec.decode(readText(uri))))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(wrap(error))
        }
    }

    override suspend fun importFrom(uri: String): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val payload = BackupCodec.decode(readText(uri))
            val snapshotText = BackupCodec.encode(snapshotPayload())
            writeRestoreSnapshot(snapshotText)
            db.withTransaction {
                db.sessionDao().clear()
                db.checkpointDao().clear()
                db.courseDao().clear()
                db.presetDao().clear()
                if (payload.presets.isNotEmpty()) {
                    db.presetDao().insertAll(payload.presets.map { it.toEntity() })
                }
                if (payload.courses.isNotEmpty()) {
                    db.courseDao().insertAll(payload.courses.map { it.toEntity() })
                }
                payload.checkpoint?.let { db.checkpointDao().upsert(it.toEntity()) }
                if (payload.sessions.isNotEmpty()) {
                    db.sessionDao().insertAll(payload.sessions.map { it.toEntity() })
                }
            }
            applySettings(payload.settings)
            Result.success(BackupCodec.summary(payload))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(wrap(error))
        }
    }

    private suspend fun snapshotPayload(): BackupFile = db.withTransaction {
        val current = settings.current()
        val folder = settings.currentFolderUri()
        BackupFile(
            version = BackupCodec.VERSION,
            exportedAt = System.currentTimeMillis(),
            settings = BackupSettingsDto(
                theme = current.theme.name,
                language = current.language.name,
                fontScale = current.fontScale,
                vibration = current.vibration,
                alertSound = current.alertSound,
                alertTone = current.alertTone.name,
                folderUri = folder
            ),
            presets = db.presetDao().all().map { it.toDto() },
            courses = db.courseDao().all().map { it.toDto() },
            checkpoint = db.checkpointDao().get()?.toDto(),
            sessions = db.sessionDao().all().map { it.toDto() }
        )
    }

    private fun readText(uri: String): String {
        val stream = appContext.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw BackupFormatException(appContext.getString(R.string.data_backup_unreadable))
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun writeText(uri: String, text: String) {
        val stream = appContext.contentResolver.openOutputStream(Uri.parse(uri), "wt")
            ?: throw IllegalStateException(appContext.getString(R.string.data_backup_write_failed))
        stream.use {
            it.write(text.toByteArray(Charsets.UTF_8))
            it.flush()
        }
    }

    private fun writeRestoreSnapshot(text: String) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val file = File(appContext.filesDir, "backup-before-restore-$stamp.json")
        try {
            file.writeText(text, Charsets.UTF_8)
        } catch (_: Exception) {
            throw BackupFormatException(appContext.getString(R.string.data_restore_snapshot_failed))
        }
    }

    private suspend fun applySettings(settingsDto: BackupSettingsDto) {
        settings.setTheme(
            runCatching { ThemeMode.valueOf(settingsDto.theme) }.getOrDefault(ThemeMode.SYSTEM)
        )
        settings.setLanguage(
            runCatching { LanguageMode.valueOf(settingsDto.language) }
                .getOrDefault(LanguageMode.FOLLOW_SYSTEM)
        )
        settings.setFontScale(settingsDto.fontScale)
        settings.setVibration(settingsDto.vibration)
        settings.setAlertSound(settingsDto.alertSound)
        settings.setAlertTone(
            runCatching { AlertTone.valueOf(settingsDto.alertTone) }.getOrDefault(AlertTone.CHIME)
        )
        settings.setFolderUri(settingsDto.folderUri?.takeIf { it.isNotBlank() })
    }

    private fun wrap(error: Exception): Throwable = when (error) {
        is BackupFormatException -> error
        else -> IllegalStateException(appContext.getString(R.string.data_backup_failed), error)
    }

    private fun PresetEntity.toDto(): BackupPresetDto = BackupPresetDto(
        id = id,
        name = name,
        durationSec = durationSec,
        sortOrder = sortOrder
    )

    private fun CourseEntity.toDto(): BackupCourseDto = BackupCourseDto(
        id = id,
        uri = uri,
        displayName = displayName,
        relativePath = relativePath,
        durationMs = durationMs,
        type = type,
        hasAudio = hasAudio,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        fingerprint = fingerprint,
        status = status,
        sortIndex = sortIndex,
        note = note
    )

    private fun CheckpointEntity.toDto(): BackupCheckpointDto = BackupCheckpointDto(
        courseId = courseId,
        positionMs = positionMs,
        updatedAt = updatedAt
    )

    private fun TimerSessionEntity.toDto(): BackupSessionDto = BackupSessionDto(
        id = id,
        presetId = presetId,
        presetName = presetName,
        targetSec = targetSec,
        listenedSec = listenedSec,
        completed = completed,
        startedAt = startedAt,
        endedAt = endedAt
    )

    private fun BackupPresetDto.toEntity(): PresetEntity = PresetEntity(
        id = id,
        name = name,
        durationSec = durationSec,
        sortOrder = sortOrder
    )

    private fun BackupCourseDto.toEntity(): CourseEntity = CourseEntity(
        id = id,
        uri = uri,
        displayName = displayName,
        relativePath = relativePath,
        durationMs = durationMs,
        type = type,
        hasAudio = hasAudio,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        fingerprint = fingerprint,
        status = status,
        sortIndex = sortIndex,
        note = note
    )

    private fun BackupCheckpointDto.toEntity(): CheckpointEntity = CheckpointEntity(
        courseId = courseId,
        positionMs = positionMs,
        updatedAt = updatedAt
    )

    private fun BackupSessionDto.toEntity(): TimerSessionEntity = TimerSessionEntity(
        id = id,
        presetId = presetId,
        presetName = presetName,
        targetSec = targetSec,
        listenedSec = listenedSec,
        completed = completed,
        startedAt = startedAt,
        endedAt = endedAt
    )
}