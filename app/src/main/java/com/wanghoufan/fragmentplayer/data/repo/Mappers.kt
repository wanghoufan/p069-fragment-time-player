package com.wanghoufan.fragmentplayer.data.repo

import com.wanghoufan.fragmentplayer.core.model.Checkpoint
import com.wanghoufan.fragmentplayer.core.model.Course
import com.wanghoufan.fragmentplayer.core.model.CourseStatus
import com.wanghoufan.fragmentplayer.core.model.MediaType
import com.wanghoufan.fragmentplayer.core.model.Preset
import com.wanghoufan.fragmentplayer.core.model.TimerSessionRecord
import com.wanghoufan.fragmentplayer.data.db.CheckpointEntity
import com.wanghoufan.fragmentplayer.data.db.CourseEntity
import com.wanghoufan.fragmentplayer.data.db.PresetEntity
import com.wanghoufan.fragmentplayer.data.db.TimerSessionEntity

internal fun PresetEntity.toModel(): Preset = Preset(
    id = id,
    name = name,
    durationSec = durationSec,
    sortOrder = sortOrder
)

internal fun Preset.toEntity(): PresetEntity = PresetEntity(
    id = id,
    name = name,
    durationSec = durationSec,
    sortOrder = sortOrder
)

internal fun CourseEntity.toModel(): Course = Course(
    id = id,
    uri = uri,
    displayName = displayName,
    relativePath = relativePath,
    durationMs = durationMs,
    type = runCatching { MediaType.valueOf(type) }.getOrDefault(MediaType.AUDIO),
    hasAudio = hasAudio,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    fingerprint = fingerprint,
    status = runCatching { CourseStatus.valueOf(status) }.getOrDefault(CourseStatus.READY),
    sortIndex = sortIndex,
    note = note
)

internal fun Course.toEntity(): CourseEntity = CourseEntity(
    id = id,
    uri = uri,
    displayName = displayName,
    relativePath = relativePath,
    durationMs = durationMs,
    type = type.name,
    hasAudio = hasAudio,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    fingerprint = fingerprint,
    status = status.name,
    sortIndex = sortIndex,
    note = note
)

internal fun CheckpointEntity.toModel(): Checkpoint = Checkpoint(
    courseId = courseId,
    positionMs = positionMs,
    updatedAt = updatedAt
)

internal fun Checkpoint.toEntity(): CheckpointEntity = CheckpointEntity(
    courseId = courseId,
    positionMs = positionMs,
    updatedAt = updatedAt
)

internal fun TimerSessionEntity.toModel(): TimerSessionRecord = TimerSessionRecord(
    id = id,
    presetId = presetId,
    presetName = presetName,
    targetSec = targetSec,
    listenedSec = listenedSec,
    completed = completed,
    startedAt = startedAt,
    endedAt = endedAt
)

internal fun TimerSessionRecord.toEntity(): TimerSessionEntity = TimerSessionEntity(
    id = id,
    presetId = presetId,
    presetName = presetName,
    targetSec = targetSec,
    listenedSec = listenedSec,
    completed = completed,
    startedAt = startedAt,
    endedAt = endedAt
)