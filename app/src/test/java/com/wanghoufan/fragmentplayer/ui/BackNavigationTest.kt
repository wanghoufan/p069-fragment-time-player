package com.wanghoufan.fragmentplayer.ui

import com.wanghoufan.fragmentplayer.core.model.TimerPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class BackNavigationTest {

    private val timerTab = 0
    private val settingsTab = 2

    @Test
    fun `non timer tab returns to timer tab first`() {
        assertEquals(
            BackAction.SWITCH_TO_TIMER,
            resolveBackAction(settingsTab, timerTab, TimerPhase.IDLE)
        )
        assertEquals(
            BackAction.SWITCH_TO_TIMER,
            resolveBackAction(settingsTab, timerTab, TimerPhase.RUNNING)
        )
    }

    @Test
    fun `finished page clears result instead of exiting`() {
        assertEquals(
            BackAction.CLEAR_RESULT,
            resolveBackAction(timerTab, timerTab, TimerPhase.FINISHED)
        )
    }

    @Test
    fun `timer home falls back to exit guard`() {
        listOf(TimerPhase.IDLE, TimerPhase.PREPARING, TimerPhase.RUNNING, TimerPhase.PAUSED)
            .forEach { phase ->
                assertEquals(
                    "phase=$phase",
                    BackAction.EXIT_GUARD,
                    resolveBackAction(timerTab, timerTab, phase)
                )
            }
    }
}