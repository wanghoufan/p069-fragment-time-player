package com.wanghoufan.fragmentplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.wanghoufan.fragmentplayer.FragmentTimeApp
import com.wanghoufan.fragmentplayer.core.model.AppSettings
import com.wanghoufan.fragmentplayer.core.model.Checkpoint
import com.wanghoufan.fragmentplayer.core.model.Course
import com.wanghoufan.fragmentplayer.core.model.PlaybackUiState
import com.wanghoufan.fragmentplayer.core.model.Preset
import com.wanghoufan.fragmentplayer.core.model.TimerResult
import com.wanghoufan.fragmentplayer.core.model.TimerUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 计时页专用 ViewModel。课程页与设置页各自持有自己的状态，不从这里取。
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as FragmentTimeApp).graph

    val settings: StateFlow<AppSettings> = graph.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), AppSettings())

    val presets: StateFlow<List<Preset>> = graph.presets.presets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

    val courses: StateFlow<List<Course>> = graph.courses.courses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

    val checkpoint: StateFlow<Checkpoint?> = graph.checkpoints.checkpoint
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), null)

    val playback: StateFlow<PlaybackUiState> = graph.playback.state

    val timer: StateFlow<TimerUiState> = graph.timer.state

    /** 手动结束已结算、等待用户选择是否记入历史；非空时界面只弹这一个对话框。 */
    val pendingFinish: StateFlow<TimerResult?> = graph.timer.pendingFinish

    val connected: StateFlow<Boolean> = graph.playback.connected

    private var startJob: Job? = null

    fun player(): Player? = graph.playback.player()

    fun ensurePlaybackConnected() {
        graph.playback.connect()
    }

    fun start(preset: Preset, countdownOnly: Boolean = false) {
        startJob?.cancel()
        startJob = viewModelScope.launch { graph.timer.start(preset, countdownOnly) }
    }

    fun cancelPreparing() {
        // 先取消挂起中的启动协程，再按当前 sessionId 清理，避免旧的启动流程继续推进。
        startJob?.cancel()
        startJob = null
        graph.timer.cancelStart()
    }

    fun pauseTimer() {
        viewModelScope.launch { graph.timer.pause() }
    }

    fun resumeTimer() {
        viewModelScope.launch { graph.timer.resume() }
    }

    fun finishEarly() {
        viewModelScope.launch { graph.timer.finishEarly() }
    }

    fun commitFinish(saveHistory: Boolean) {
        viewModelScope.launch { graph.timer.commitPendingFinish(saveHistory) }
    }

    fun clearResult() {
        viewModelScope.launch { graph.timer.clearResult() }
    }

    fun toggleMediaPlayback() {
        if (graph.playback.state.value.isPlaying) {
            graph.playback.pause()
        } else {
            graph.playback.play()
        }
    }

    fun seekTo(ms: Long) {
        graph.playback.seekTo(ms)
    }

    fun nextCourse() {
        graph.playback.next()
    }

    fun previousCourse() {
        graph.playback.previous()
    }

    fun setSpeed(speed: Float) {
        graph.playback.setSpeed(speed)
    }

    fun addPreset(name: String, durationSec: Int) {
        viewModelScope.launch { graph.presets.add(name, durationSec) }
    }

    fun updatePreset(preset: Preset) {
        viewModelScope.launch { graph.presets.update(preset) }
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch { graph.presets.delete(id) }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}