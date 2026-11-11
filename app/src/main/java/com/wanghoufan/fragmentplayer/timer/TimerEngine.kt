package com.wanghoufan.fragmentplayer.timer

import android.os.SystemClock

enum class TimerState { IDLE, RUNNING, PAUSED, FINISHED }

/**
 * 纯计时状态机：所有倒计时都用单调时钟，UI 刷新频率不参与时间计算。
 */
class TimerEngine(
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() }
) {

    var state: TimerState = TimerState.IDLE
        private set
    var sessionId: String? = null
        private set
    var targetMs: Long = 0L
        private set
    var deadlineMs: Long = 0L
        private set
    var pausedRemainingMs: Long = 0L
        private set
    var startedWallMs: Long = 0L
        private set
    var finishedElapsedMs: Long = 0L
        private set

    fun start(sessionId: String, targetMs: Long, startedWallMs: Long = System.currentTimeMillis()) {
        this.sessionId = sessionId
        this.targetMs = targetMs.coerceAtLeast(0L)
        this.startedWallMs = startedWallMs
        this.pausedRemainingMs = 0L
        this.finishedElapsedMs = 0L
        this.deadlineMs = nowMs() + this.targetMs
        this.state = TimerState.RUNNING
    }

    fun pause() {
        if (state != TimerState.RUNNING) return
        pausedRemainingMs = remainingMs()
        state = TimerState.PAUSED
    }

    fun resume() {
        if (state != TimerState.PAUSED) return
        deadlineMs = nowMs() + pausedRemainingMs
        state = TimerState.RUNNING
    }

    fun remainingMs(): Long = when (state) {
        TimerState.RUNNING -> (deadlineMs - nowMs()).coerceAtLeast(0L)
        TimerState.PAUSED -> pausedRemainingMs.coerceAtLeast(0L)
        TimerState.FINISHED -> 0L
        TimerState.IDLE -> 0L
    }

    fun elapsedMs(): Long = when (state) {
        TimerState.RUNNING -> (targetMs - remainingMs()).coerceAtLeast(0L)
        TimerState.PAUSED -> (targetMs - pausedRemainingMs).coerceAtLeast(0L)
        TimerState.FINISHED -> finishedElapsedMs
        TimerState.IDLE -> 0L
    }

    fun isFinished(): Boolean =
        state == TimerState.FINISHED || (state == TimerState.RUNNING && remainingMs() <= 0L)

    fun finish() {
        if (state == TimerState.IDLE) return
        if (state != TimerState.FINISHED) {
            finishedElapsedMs = elapsedMs()
            pausedRemainingMs = 0L
            state = TimerState.FINISHED
        }
    }
}