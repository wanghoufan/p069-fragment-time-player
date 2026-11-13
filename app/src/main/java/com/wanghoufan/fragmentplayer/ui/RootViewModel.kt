package com.wanghoufan.fragmentplayer.ui

import android.app.Application
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanghoufan.fragmentplayer.FragmentTimeApp
import com.wanghoufan.fragmentplayer.core.model.AppSettings
import com.wanghoufan.fragmentplayer.core.model.CourseStatus
import com.wanghoufan.fragmentplayer.core.model.TimerPhase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 根级 ViewModel：把设置里的主题与字体缩放接到 Compose 主题，
 * 并持有跨 Tab 的中断提示（任何 Tab 首屏都要能看到）。
 */
class RootViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext = app as FragmentTimeApp
    private val graph = appContext.graph

    val settings: StateFlow<AppSettings> = graph.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppSettings())

    /** 桌面快捷方式点进来的场景 id，消费后置空。 */
    val shortcutStartPresetId: StateFlow<Long?> = appContext.shortcutStartPresetId

    fun peekInterruptedNotice(): String? = graph.timer.peekInterruptedNotice()

    fun consumeInterruptedNotice() {
        graph.timer.consumeInterruptedNotice()
    }

    fun consumeShortcutStart() {
        appContext.consumeShortcutStart()
    }

    /**
     * 点快捷方式即开始：已有计时或被中断的结算在等用户选择时不动它，
     * 只是把界面带回计时页；没有课程就自动退到仅倒计时，保证「点一下就开始了」。
     */
    fun startFromShortcut(presetId: Long) {
        viewModelScope.launch {
            runCatching { graph.presets.ensureDefaults() }
            if (graph.timer.pendingFinish.value != null) return@launch
            val phase = graph.timer.state.value.phase
            if (phase != TimerPhase.IDLE && phase != TimerPhase.FINISHED) return@launch
            val preset = runCatching { graph.presets.get(presetId) }.getOrNull() ?: return@launch
            val hasPlayableCourse = runCatching { graph.courses.all() }
                .getOrDefault(emptyList())
                .any { it.status == CourseStatus.READY }
            vibrateShort()
            graph.timer.start(preset, countdownOnly = !hasPlayableCourse)
        }
    }

    private suspend fun vibrateShort() {
        val enabled = runCatching { graph.settings.current().vibration }.getOrDefault(true)
        if (!enabled) return
        runCatching {
            val vibrator = getApplication<Application>().getSystemService(Vibrator::class.java)
                ?: return@runCatching
            vibrator.vibrate(
                VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }
}
