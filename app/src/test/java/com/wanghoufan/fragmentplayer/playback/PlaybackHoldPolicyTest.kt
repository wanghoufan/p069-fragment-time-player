package com.wanghoufan.fragmentplayer.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 覆盖锁屏续播的保活判据：播放开始必须推进到「持锁 + 前台」，
 * 暂停、空闲、结束必须释放，且 wakelock 的 acquire / release 严格成对。
 */
class PlaybackHoldPolicyTest {

    @Test
    fun holdsWakeLockWhenPlayingFromReady() {
        assertTrue(
            PlaybackHoldPolicy.shouldHoldWakeLock(
                playWhenReady = true,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun holdsWakeLockWhileBufferingWithPlayIntent() {
        assertTrue(
            PlaybackHoldPolicy.shouldHoldWakeLock(
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING
            )
        )
    }

    @Test
    fun keepsHoldingWhileOffloadSleepIsReported() {
        // Media3 的 ExoPlayer 在音频 offload 睡眠时会释放它自己的 wakelock，
        // 这正是锁屏后音频被系统挂起的窗口，应用级锁必须继续持有。
        assertTrue(
            PlaybackHoldPolicy.shouldHoldWakeLock(
                playWhenReady = true,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun releasesWakeLockWhenPaused() {
        assertFalse(
            PlaybackHoldPolicy.shouldHoldWakeLock(
                playWhenReady = false,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun releasesWakeLockWhenIdleOrEnded() {
        assertFalse(
            PlaybackHoldPolicy.shouldHoldWakeLock(
                playWhenReady = true,
                playbackState = Player.STATE_IDLE
            )
        )
        assertFalse(
            PlaybackHoldPolicy.shouldHoldWakeLock(
                playWhenReady = true,
                playbackState = Player.STATE_ENDED
            )
        )
    }

    @Test
    fun expectsForegroundWhilePlaybackOngoing() {
        assertTrue(
            PlaybackHoldPolicy.shouldRunForeground(
                playWhenReady = true,
                playbackState = Player.STATE_READY
            )
        )
        assertTrue(
            PlaybackHoldPolicy.shouldRunForeground(
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING
            )
        )
    }

    @Test
    fun doesNotExpectForegroundWhenPausedIdleOrEnded() {
        assertFalse(
            PlaybackHoldPolicy.shouldRunForeground(
                playWhenReady = false,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            PlaybackHoldPolicy.shouldRunForeground(
                playWhenReady = false,
                playbackState = Player.STATE_IDLE
            )
        )
        assertFalse(
            PlaybackHoldPolicy.shouldRunForeground(
                playWhenReady = true,
                playbackState = Player.STATE_ENDED
            )
        )
    }

    @Test
    fun wakeLockIsAcquiredOnceForRepeatedPlayingUpdates() {
        var acquires = 0
        var releases = 0
        val controller = PlaybackWakeLockController(
            acquireLock = { acquires++ },
            releaseLock = { releases++ }
        )

        controller.update(shouldHold = true)
        controller.update(shouldHold = true)
        controller.update(shouldHold = true)

        assertTrue(controller.isHeld)
        assertEquals(1, acquires)
        assertEquals(0, releases)
    }

    @Test
    fun pauseReleasesWakeLockExactlyOnce() {
        var acquires = 0
        var releases = 0
        val controller = PlaybackWakeLockController(
            acquireLock = { acquires++ },
            releaseLock = { releases++ }
        )

        controller.update(shouldHold = true)
        controller.update(shouldHold = false)
        controller.update(shouldHold = false)

        assertFalse(controller.isHeld)
        assertEquals(1, acquires)
        assertEquals(1, releases)
    }

    @Test
    fun destroyReleasesOnlyHeldLock() {
        var releases = 0
        val controller = PlaybackWakeLockController(
            acquireLock = {},
            releaseLock = { releases++ }
        )

        controller.release()
        assertEquals(0, releases)

        controller.update(shouldHold = true)
        controller.release()
        controller.release()
        assertEquals(1, releases)
        assertFalse(controller.isHeld)
    }
}