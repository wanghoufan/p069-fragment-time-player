package com.wanghoufan.fragmentplayer.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanghoufan.fragmentplayer.FragmentTimeApp
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.importer.ImportResult
import com.wanghoufan.fragmentplayer.core.importer.ScanProgress
import com.wanghoufan.fragmentplayer.core.model.AppSettings
import com.wanghoufan.fragmentplayer.core.model.Checkpoint
import com.wanghoufan.fragmentplayer.core.model.Course
import com.wanghoufan.fragmentplayer.core.model.LanguageMode
import com.wanghoufan.fragmentplayer.core.model.PlaybackUiState
import com.wanghoufan.fragmentplayer.core.model.ThemeMode
import com.wanghoufan.fragmentplayer.core.repo.BackupSummary
import com.wanghoufan.fragmentplayer.ui.applyLanguage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CoursesSettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as FragmentTimeApp).graph
    private val appContext: Context
        get() = getApplication<Application>()

    val courses: StateFlow<List<Course>> = graph.courses.courses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

    val checkpoint: StateFlow<Checkpoint?> = graph.checkpoints.checkpoint
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), null)

    val playback: StateFlow<PlaybackUiState> = graph.playback.state

    val settings: StateFlow<AppSettings> = graph.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), AppSettings())

    val folderUri: StateFlow<String?> = graph.settings.folderUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), null)

    val scanning: StateFlow<Boolean> = graph.importer.scanning

    val scanProgress: StateFlow<ScanProgress> = graph.importer.progress

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = MESSAGE_BUFFER)
    val messages: SharedFlow<String> = _messages

    fun importFolder(uri: String) {
        runScan { graph.importer.importFolder(uri) }
    }

    fun rescan() {
        runScan { graph.importer.rescan() }
    }

    fun relink(uri: String) {
        runScan { graph.importer.relink(uri) }
    }

    fun notifyNoAudioCourse() {
        postMessage(appContext.getString(R.string.mcu_no_audio_notice))
    }

    fun playCourse(course: Course, fromStart: Boolean) {
        viewModelScope.launch {
            val positionMs = if (fromStart) {
                0L
            } else {
                val last = graph.checkpoints.get()
                if (last != null && last.courseId == course.id) last.positionMs else 0L
            }
            graph.playback.playCourse(course, positionMs).onFailure { error ->
                postMessage(error.message ?: appContext.getString(R.string.mcu_play_failed))
            }
        }
    }

    fun setStartPoint(courseId: Long, positionMs: Long) {
        viewModelScope.launch {
            graph.checkpoints.save(courseId, positionMs.coerceAtLeast(0L))
            postMessage(appContext.getString(R.string.mcu_start_point_saved))
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { graph.settings.setTheme(mode) }
    }

    fun setLanguage(mode: LanguageMode) {
        viewModelScope.launch {
            graph.settings.setLanguage(mode)
            applyLanguage(mode)
        }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { graph.settings.setFontScale(scale) }
    }

    fun setVibration(enabled: Boolean) {
        viewModelScope.launch { graph.settings.setVibration(enabled) }
    }

    fun setAlertSound(enabled: Boolean) {
        viewModelScope.launch { graph.settings.setAlertSound(enabled) }
    }

    fun exportTo(uri: String) {
        viewModelScope.launch {
            graph.backups.exportTo(uri).fold(
                onSuccess = { postMessage(appContext.getString(R.string.seu_backup_exported)) },
                onFailure = { postMessage(it.message ?: appContext.getString(R.string.seu_backup_failed)) }
            )
        }
    }

    fun peek(uri: String, callback: (BackupSummary?) -> Unit) {
        viewModelScope.launch {
            val result = graph.backups.peek(uri)
            val summary = result.getOrNull()
            if (summary == null) {
                postMessage(result.exceptionOrNull()?.message ?: appContext.getString(R.string.seu_backup_unreadable))
            }
            callback(summary)
        }
    }

    fun importFrom(uri: String) {
        viewModelScope.launch {
            graph.backups.importFrom(uri).fold(
                onSuccess = {
                    applyLanguage(graph.settings.current().language)
                    postMessage(appContext.getString(R.string.seu_backup_imported))
                },
                onFailure = { postMessage(it.message ?: appContext.getString(R.string.seu_backup_failed)) }
            )
        }
    }

    private fun runScan(action: suspend () -> ImportResult) {
        if (graph.importer.scanning.value) {
            postMessage(appContext.getString(R.string.mcu_scan_busy))
            return
        }
        viewModelScope.launch {
            val result = action()
            postMessage(
                result.message ?: if (result.ok) {
                    appContext.getString(R.string.mcu_scan_done, result.total)
                } else {
                    appContext.getString(R.string.mcu_scan_failed)
                }
            )
        }
    }

    private fun postMessage(text: String) {
        if (text.isNotBlank()) _messages.tryEmit(text)
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        const val MESSAGE_BUFFER = 8
    }
}