package com.wanghoufan.fragmentplayer.core.model

enum class MediaType { VIDEO, AUDIO }

enum class CourseStatus { READY, MISSING, UNSUPPORTED }

data class Preset(
    val id: Long = 0L,
    val name: String,
    val durationSec: Int,
    val sortOrder: Int = 0
)

data class Course(
    val id: Long = 0L,
    val uri: String,
    val displayName: String,
    val relativePath: String,
    val durationMs: Long,
    val type: MediaType,
    val hasAudio: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val fingerprint: String,
    val status: CourseStatus = CourseStatus.READY,
    val sortIndex: Int = 0,
    val note: String? = null
)

data class Checkpoint(
    val courseId: Long,
    val positionMs: Long,
    val updatedAt: Long
)

data class TimerSessionRecord(
    val id: Long = 0L,
    val presetId: Long?,
    val presetName: String,
    val targetSec: Int,
    val listenedSec: Long,
    val completed: Boolean,
    val startedAt: Long,
    val endedAt: Long
)

enum class TimerPhase { IDLE, PREPARING, RUNNING, PAUSED, FINISHED }

data class TimerResult(
    val presetName: String,
    val targetSec: Int,
    val listenedSec: Long,
    val completed: Boolean,
    val interrupted: Boolean = false
)

data class TimerUiState(
    val phase: TimerPhase = TimerPhase.IDLE,
    val sessionId: String? = null,
    val presetId: Long? = null,
    val presetName: String = "",
    val targetSec: Int = 0,
    /** 真实墙钟剩余（已按「时光流速」压缩），界面按 timeFlow 换回目标时长显示。 */
    val remainingMs: Long = 0L,
    val countdownOnly: Boolean = false,
    val courseTitle: String? = null,
    val actualMediaMs: Long = 0L,
    val error: String? = null,
    val result: TimerResult? = null,
    /** 本轮的时光流速倍率，开始后不再变化。 */
    val timeFlow: Int = 1
)

data class PlaybackUiState(
    val currentCourseId: Long? = null,
    val currentTitle: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val hasVideo: Boolean = false,
    val error: String? = null
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class LanguageMode { FOLLOW_SYSTEM, ZH_CN, EN }

/**
 * 到点铃声选项：三个内置短音 + 系统默认闹钟。
 * 内置音都只有 1.2–1.6 秒，响一次就够，不循环。
 */
enum class AlertTone { CHIME, BELL, SOFT, SYSTEM }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: LanguageMode = LanguageMode.FOLLOW_SYSTEM,
    val fontScale: Float = 1.15f,
    val vibration: Boolean = true,
    val alertSound: Boolean = true,
    /** 到点铃声：默认用内置「叮咚三连」，避免再依赖机型默认闹钟的长铃声。 */
    val alertTone: AlertTone = AlertTone.CHIME,
    /** 「时光流速（测试用）」倍率，只压缩计时，不改变音频播放速度。 */
    val timeFlow: Int = 1
)