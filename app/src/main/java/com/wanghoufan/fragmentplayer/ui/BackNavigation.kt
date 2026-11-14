package com.wanghoufan.fragmentplayer.ui

import com.wanghoufan.fragmentplayer.core.model.TimerPhase

/**
 * 返回键动作分层：非计时 Tab 先回计时 Tab，计时完成页回 IDLE，
 * 其余情况才交给首页的「再按一次退出」。
 */
internal enum class BackAction {
    SWITCH_TO_TIMER,
    CLEAR_RESULT,
    EXIT_GUARD
}

internal fun resolveBackAction(
    selectedTabIndex: Int,
    timerTabIndex: Int,
    phase: TimerPhase
): BackAction = when {
    selectedTabIndex != timerTabIndex -> BackAction.SWITCH_TO_TIMER
    phase == TimerPhase.FINISHED -> BackAction.CLEAR_RESULT
    else -> BackAction.EXIT_GUARD
}