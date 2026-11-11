package com.wanghoufan.fragmentplayer

import com.wanghoufan.fragmentplayer.core.importer.CourseImporterApi
import com.wanghoufan.fragmentplayer.core.playback.PlaybackEngineApi
import com.wanghoufan.fragmentplayer.core.repo.BackupApi
import com.wanghoufan.fragmentplayer.core.repo.CheckpointRepository
import com.wanghoufan.fragmentplayer.core.repo.CourseRepository
import com.wanghoufan.fragmentplayer.core.repo.PresetRepository
import com.wanghoufan.fragmentplayer.core.repo.SessionRepository
import com.wanghoufan.fragmentplayer.core.repo.SettingsRepository
import com.wanghoufan.fragmentplayer.core.timer.TimerControllerApi

class AppGraph(
    val presets: PresetRepository,
    val courses: CourseRepository,
    val checkpoints: CheckpointRepository,
    val sessions: SessionRepository,
    val settings: SettingsRepository,
    val backups: BackupApi,
    val importer: CourseImporterApi,
    val playback: PlaybackEngineApi,
    val timer: TimerControllerApi
)