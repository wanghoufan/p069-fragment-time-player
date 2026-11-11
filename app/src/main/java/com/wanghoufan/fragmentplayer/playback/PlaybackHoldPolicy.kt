package com.wanghoufan.fragmentplayer.playback

import androidx.media3.common.Player

/**
 * 锁屏续播的保活判据集中在这里，便于单测。
 *
 * 与 Media3 内部策略的差异：ExoPlayer 自己的 wakelock 会在音频 offload 睡眠时主动释放，
 * 而本应用要求「计时结束前一直出声」，因此只要还有播放意图就保持应用级 partial wakelock。
 */
internal object PlaybackHoldPolicy {

    /** 播放意图成立（playWhenReady）且确实在解码/出音时才持锁，暂停与空闲一律释放。 */
    fun shouldHoldWakeLock(playWhenReady: Boolean, playbackState: Int): Boolean =
        playWhenReady &&
            (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY)

    /**
     * 媒体通知必须处于前台：Media3 只有在「已连接的控制器 + 播放中」时才把服务推进前台，
     * 这里的判据用于日志里校验真实前台状态，避免静默退化成一个普通后台服务。
     */
    fun shouldRunForeground(playWhenReady: Boolean, playbackState: Int): Boolean =
        playbackState != Player.STATE_IDLE &&
            playbackState != Player.STATE_ENDED &&
            (playWhenReady || playbackState == Player.STATE_BUFFERING)
}

/**
 * wakelock 只跟随播放状态推进，acquire / release 严格成对且幂等：
 * 重复调用不会叠加引用计数，Service 销毁时也会兜底释放，不存在常驻持锁。
 */
internal class PlaybackWakeLockController(
    private val acquireLock: () -> Unit,
    private val releaseLock: () -> Unit
) {

    var isHeld: Boolean = false
        private set

    fun update(shouldHold: Boolean) {
        if (shouldHold) acquire() else release()
    }

    fun acquire() {
        if (isHeld) return
        acquireLock()
        isHeld = true
    }

    fun release() {
        if (!isHeld) return
        releaseLock()
        isHeld = false
    }
}