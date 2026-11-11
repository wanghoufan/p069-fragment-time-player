package com.wanghoufan.fragmentplayer.core.timer

import com.wanghoufan.fragmentplayer.core.model.Preset
import com.wanghoufan.fragmentplayer.core.model.TimerResult
import com.wanghoufan.fragmentplayer.core.model.TimerUiState
import kotlinx.coroutines.flow.StateFlow

interface TimerControllerApi {
    val state: StateFlow<TimerUiState>

    /** 手动结束已停止播放并结算、等待用户决定是否记入历史的结果。 */
    val pendingFinish: StateFlow<TimerResult?>

    /** 最近一次被系统中断的提示；界面订阅它，首帧即可拿到，消费后置空。 */
    val interruptedNotice: StateFlow<String?>

    fun peekInterruptedNotice(): String?

    fun consumeInterruptedNotice()

    suspend fun start(preset: Preset, countdownOnly: Boolean)

    fun cancelStart()

    suspend fun pause()

    suspend fun resume()

    /** 立即停止播放并按实际用时结算，随后由 pendingFinish 通知界面询问是否记入历史。 */
    suspend fun finishEarly()

    /** 用户在手动结束后做出选择：记入则写会话与断点，不记入则本轮完全不落盘。 */
    suspend fun commitPendingFinish(saveHistory: Boolean)

    suspend fun clearResult()

    suspend fun handleAlarm(sessionId: String)
}