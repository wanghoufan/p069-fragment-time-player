package com.wanghoufan.fragmentplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class ExitBackGuardTest {

    private var now = 0L
    private var firstBackCount = 0
    private var exitCount = 0

    private fun guard() = ExitBackGuard(
        elapsedRealtime = { now },
        onFirstBack = { firstBackCount++ },
        onExit = { exitCount++ }
    )

    @Test
    fun firstBackOnlyShowsHint() {
        guard().onBackPressed()

        assertEquals(1, firstBackCount)
        assertEquals(0, exitCount)
    }

    @Test
    fun secondBackWithinWindowExits() {
        val guard = guard()
        guard.onBackPressed()

        now = 1_999
        guard.onBackPressed()

        assertEquals(1, firstBackCount)
        assertEquals(1, exitCount)
    }

    @Test
    fun exactlyTwoSecondsStillExits() {
        val guard = guard()
        guard.onBackPressed()

        now = ExitBackGuard.EXIT_WINDOW_MILLIS
        guard.onBackPressed()

        assertEquals(1, exitCount)
    }

    @Test
    fun secondBackAfterWindowRestartsTimer() {
        val guard = guard()
        guard.onBackPressed()

        now = 2_001
        guard.onBackPressed()
        assertEquals(2, firstBackCount)
        assertEquals(0, exitCount)

        now = 4_000
        guard.onBackPressed()
        assertEquals(2, firstBackCount)
        assertEquals(1, exitCount)
    }
}