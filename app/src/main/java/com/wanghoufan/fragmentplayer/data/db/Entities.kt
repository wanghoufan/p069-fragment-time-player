package com.wanghoufan.fragmentplayer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val durationSec: Int,
    val sortOrder: Int
)

@Entity(
    tableName = "courses",
    indices = [Index(value = ["uri"]), Index(value = ["fingerprint"])]
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
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

@Entity(tableName = "checkpoints")
data class CheckpointEntity(
    @PrimaryKey val courseId: Long,
    val positionMs: Long,
    val updatedAt: Long
)

@Entity(tableName = "timer_sessions")
data class TimerSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val presetId: Long?,
    val presetName: String,
    val targetSec: Int,
    val listenedSec: Long,
    val completed: Boolean,
    val startedAt: Long,
    val endedAt: Long
)