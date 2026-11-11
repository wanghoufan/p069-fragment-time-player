package com.wanghoufan.fragmentplayer.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {

    private class FakeClock(var nowMs: Long = 1_000L)

    private fun engine(clock: FakeClock): TimerEngine = TimerEngine(nowMs = { clock.nowMs })

    @Test
    fun startCountsDownFromTarget() {
        val clock = FakeClock()
        val engine = engine(clock)

        engine.start("session-1", 120_000L, 5_000L)

        assertEquals(TimerState.RUNNING, engine.state)
        assertEquals("session-1", engine.sessionId)
        assertEquals(120_000L, engine.remainingMs())
        assertEquals(0L, engine.elapsedMs())
        assertEquals(5_000L, engine.startedWallMs)

        clock.nowMs += 30_000L

        assertEquals(90_000L, engine.remainingMs())
        assertEquals(30_000L, engine.elapsedMs())
    }

    @Test
    fun pauseFreezesRemainingTime() {
        val clock = FakeClock()
        val engine = engine(clock)
        engine.start("session-1", 60_000L)
        clock.nowMs += 15_000L

        engine.pause()

        assertEquals(TimerState.PAUSED, engine.state)
        assertEquals(45_000L, engine.remainingMs())

        clock.nowMs += 120_000L

        assertEquals(45_000L, engine.remainingMs())
        assertEquals(15_000L, engine.elapsedMs())
    }

    @Test
    fun resumeRebasesDeadlineOnMonotonicClock() {
        val clock = FakeClock()
        val engine = engine(clock)
        engine.start("session-1", 60_000L)
        clock.nowMs += 20_000L
        engine.pause()
        clock.nowMs += 10_000L

        engine.resume()

        assertEquals(TimerState.RUNNING, engine.state)
        assertEquals(40_000L, engine.remainingMs())
        assertEquals(20_000L, engine.elapsedMs())

        clock.nowMs += 40_000L

        assertEquals(0L, engine.remainingMs())
        assertEquals(60_000L, engine.elapsedMs())
        assertTrue(engine.isFinished())
    }

    @Test
    fun finishedOnlyWhenRemainingReachesZero() {
        val clock = FakeClock()
        val engine = engine(clock)
        engine.start("session-1", 10_000L)

        clock.nowMs += 9_999L
        assertFalse(engine.isFinished())

        clock.nowMs += 1L
        assertTrue(engine.isFinished())
        assertEquals(0L, engine.remainingMs())
    }

    @Test
    fun finishEarlyKeepsRealElapsedAndStopsClock() {
        val clock = FakeClock()
        val engine = engine(clock)
        engine.start("session-1", 180_000L)
        clock.nowMs += 42_000L

        engine.finish()

        assertEquals(TimerState.FINISHED, engine.state)
        assertEquals(0L, engine.remainingMs())
        assertEquals(42_000L, engine.elapsedMs())

        clock.nowMs += 100_000L

        assertEquals(0L, engine.remainingMs())
        assertEquals(42_000L, engine.elapsedMs())
    }

    @Test
    fun startResetsPreviousSession() {
        val clock = FakeClock()
        val engine = engine(clock)
        engine.start("session-1", 60_000L)
        clock.nowMs += 10_000L

        engine.start("session-2", 30_000L, 9_000L)

        assertEquals(TimerState.RUNNING, engine.state)
        assertEquals("session-2", engine.sessionId)
        assertEquals(30_000L, engine.remainingMs())
        assertEquals(0L, engine.elapsedMs())
        assertEquals(9_000L, engine.startedWallMs)
    }

    @Test
    fun pauseAndResumeDoNothingInWrongState() {
        val clock = FakeClock()
        val engine = engine(clock)

        engine.pause()
        engine.resume()

        assertEquals(TimerState.IDLE, engine.state)
        assertEquals(0L, engine.remainingMs())
        assertEquals(0L, engine.elapsedMs())
    }
}