package com.wanghoufan.fragmentplayer.data.backup

import com.wanghoufan.fragmentplayer.core.model.AlertTone
import com.wanghoufan.fragmentplayer.core.model.LanguageMode
import com.wanghoufan.fragmentplayer.core.repo.BackupSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupFormatException(message: String) : IllegalArgumentException(message)

@Serializable
data class BackupSettingsDto(
    val theme: String = "SYSTEM",
    val language: String = LanguageMode.FOLLOW_SYSTEM.name,
    val fontScale: Float = 1.15f,
    val vibration: Boolean = true,
    val alertSound: Boolean = true,
    val alertTone: String = AlertTone.CHIME.name,
    val folderUri: String? = null
)

@Serializable
data class BackupPresetDto(
    val id: Long,
    val name: String,
    val durationSec: Int,
    val sortOrder: Int
)

@Serializable
data class BackupCourseDto(
    val id: Long,
    val uri: String,
    val displayName: String,
    val relativePath: String,
    val durationMs: Long,
    val type: String,
    val hasAudio: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val fingerprint: String,
    val status: String,
    val sortIndex: Int,
    val note: String? = null
)

@Serializable
data class BackupCheckpointDto(
    val courseId: Long,
    val positionMs: Long,
    val updatedAt: Long
)

@Serializable
data class BackupSessionDto(
    val id: Long,
    val presetId: Long? = null,
    val presetName: String,
    val targetSec: Int,
    val listenedSec: Long,
    val completed: Boolean,
    val startedAt: Long,
    val endedAt: Long
)

@Serializable
data class BackupFile(
    val version: Int = BackupCodec.VERSION,
    val exportedAt: Long = 0L,
    val settings: BackupSettingsDto = BackupSettingsDto(),
    val presets: List<BackupPresetDto> = emptyList(),
    val courses: List<BackupCourseDto> = emptyList(),
    val checkpoint: BackupCheckpointDto? = null,
    val sessions: List<BackupSessionDto> = emptyList()
)

object BackupCodec {
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val VALID_THEMES = setOf("SYSTEM", "LIGHT", "DARK")
    private val VALID_LANGUAGES = LanguageMode.entries.map { it.name }.toSet()
    private val VALID_TONES = AlertTone.entries.map { it.name }.toSet()

    fun encode(file: BackupFile): String = json.encodeToString(file)

    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString<BackupFile>(text)
        } catch (error: SerializationException) {
            throw BackupFormatException("备份文件格式不正确")
        } catch (error: IllegalArgumentException) {
            throw BackupFormatException("备份文件格式不正确")
        }
        validate(file)
        return normalize(file)
    }

    private fun normalize(file: BackupFile): BackupFile {
        val language = if (file.settings.language in VALID_LANGUAGES) {
            file.settings.language
        } else {
            LanguageMode.FOLLOW_SYSTEM.name
        }
        // 旧备份没有 alertTone 字段或写了未知值，都回退到默认铃声，不让导入失败。
        val tone = if (file.settings.alertTone in VALID_TONES) {
            file.settings.alertTone
        } else {
            AlertTone.CHIME.name
        }
        return file.copy(
            settings = file.settings.copy(language = language, alertTone = tone)
        )
    }

    fun summary(file: BackupFile): BackupSummary = BackupSummary(
        presets = file.presets.size,
        courses = file.courses.size,
        hasCheckpoint = file.checkpoint != null,
        exportedAt = file.exportedAt
    )

    private fun validate(file: BackupFile) {
        if (file.version != VERSION) {
            throw BackupFormatException("备份版本不受支持")
        }
        if (file.settings.theme !in VALID_THEMES) {
            throw BackupFormatException("备份中的主题设置无效")
        }
        if (file.presets.map { it.id }.toSet().size != file.presets.size) {
            throw BackupFormatException("备份中的场景编号重复")
        }
        if (file.courses.map { it.id }.toSet().size != file.courses.size) {
            throw BackupFormatException("备份中的课程编号重复")
        }
        file.courses.forEach { course ->
            if (course.uri.isBlank()) {
                throw BackupFormatException("备份中的课程地址为空")
            }
        }
        file.checkpoint?.let { checkpoint ->
            if (checkpoint.positionMs < 0L) {
                throw BackupFormatException("备份中的播放位置无效")
            }
            val courseIds = file.courses.map { it.id }.toSet()
            if (checkpoint.courseId !in courseIds) {
                throw BackupFormatException("备份中的播放位置找不到对应课程")
            }
        }
    }
}