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
import com.wanghoufan.fragmentplayer.shortcut.ShortcutStart
import com.wanghoufan.fragmentplayer.timer.TimerController
import com.wanghoufan.fragmentplayer.timer.TimerNotifications
import com.wanghoufan.fragmentplayer.ui.applyLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FragmentTimeApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 依赖图按首次访问构建，避免拖慢冷启动与后台启动耗时。
    val graph: AppGraph by lazy { buildGraph() }

    /** 桌面快捷方式带来的待处理场景；由根界面消费，避免冷启动时丢失。 */
    private val _shortcutStartPresetId = MutableStateFlow<Long?>(null)
    val shortcutStartPresetId: StateFlow<Long?> = _shortcutStartPresetId.asStateFlow()

    fun requestShortcutStart(presetId: Long) {
        _shortcutStartPresetId.value = presetId
    }

    fun consumeShortcutStart() {
        _shortcutStartPresetId.value = null
    }

    override fun onCreate() {
        super.onCreate()
        applySavedLanguage()
        TimerNotifications.ensureChannels(this)
        graph.timer
        appScope.launch {
            runCatching { graph.presets.ensureDefaults() }
                .onFailure { Log.w(TAG, "ensureDefaults failed", it) }
        }
        // 场景增删改都会重新发射；菜单始终与 App 里的场景列表保持一致。
        appScope.launch {
            graph.presets.presets.collect { presets ->
                runCatching { ShortcutStart.refresh(this@FragmentTimeApp, presets) }
                    .onFailure { Log.w(TAG, "refresh shortcuts failed", it) }
            }
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