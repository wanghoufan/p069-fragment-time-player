package com.wanghoufan.fragmentplayer

import android.app.Application
import android.util.Log
import com.wanghoufan.fragmentplayer.data.backup.BackupManager
import com.wanghoufan.fragmentplayer.data.db.AppDatabase
import com.wanghoufan.fragmentplayer.data.repo.CheckpointRepositoryImpl
import com.wanghoufan.fragmentplayer.data.repo.CourseRepositoryImpl
import com.wanghoufan.fragmentplayer.data.repo.PresetRepositoryImpl
import com.wanghoufan.fragmentplayer.data.repo.SessionRepositoryImpl
import com.wanghoufan.fragmentplayer.data.settings.SettingsRepositoryImpl
import com.wanghoufan.fragmentplayer.importer.CourseImporterImpl
import com.wanghoufan.fragmentplayer.playback.PlaybackEngine
import com.wanghoufan.fragmentplayer.timer.TimerController
import com.wanghoufan.fragmentplayer.timer.TimerNotifications
import com.wanghoufan.fragmentplayer.ui.applyLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FragmentTimeApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 依赖图按首次访问构建，避免拖慢冷启动与后台启动耗时。
    val graph: AppGraph by lazy { buildGraph() }

    override fun onCreate() {
        super.onCreate()
        applySavedLanguage()
        TimerNotifications.ensureChannels(this)
        graph.timer
        appScope.launch {
            runCatching { graph.presets.ensureDefaults() }
                .onFailure { Log.w(TAG, "ensureDefaults failed", it) }
        }
    }

    private fun applySavedLanguage() {
        val saved = runBlocking { SettingsRepositoryImpl(this@FragmentTimeApp).current().language }
        applyLanguage(saved)
    }

    private fun buildGraph(): AppGraph {
        val db = AppDatabase.create(this)
        val presets = PresetRepositoryImpl(db)
        val courses = CourseRepositoryImpl(db)
        val checkpoints = CheckpointRepositoryImpl(db)
        val sessions = SessionRepositoryImpl(db)
        val settings = SettingsRepositoryImpl(this)
        val backups = BackupManager(this, db, settings)
        val importer = CourseImporterImpl(this, courses, checkpoints, settings)
        val playback = PlaybackEngine(this, courses, checkpoints)
        val timer = TimerController(
            this,
            presets,
            courses,
            checkpoints,
            sessions,
            settings,
            playback
        )
        return AppGraph(
            presets = presets,
            courses = courses,
            checkpoints = checkpoints,
            sessions = sessions,
            settings = settings,
            backups = backups,
            importer = importer,
            playback = playback,
            timer = timer
        )
    }

    private companion object {
        const val TAG = "FragmentTimeApp"
    }
}