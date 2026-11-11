package com.wanghoufan.fragmentplayer

import android.os.SystemClock

internal class ExitBackGuard(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val onFirstBack: () -> Unit,
    private val onExit: () -> Unit
) {
    private var lastBackAt: Long? = null

    fun onBackPressed() {
        val now = elapsedRealtime()
        val previous = lastBackAt
        if (previous != null && now - previous <= EXIT_WINDOW_MILLIS) {
            lastBackAt = null
            onExit()
        } else {
            lastBackAt = now
            onFirstBack()
        }
    }

    companion object {
        const val EXIT_WINDOW_MILLIS = 2_000L
    }
}