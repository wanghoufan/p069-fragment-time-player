package com.wanghoufan.fragmentplayer.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper

/**
 * 到点铃声：不依赖通知渠道，直接用 MediaPlayer 走 USAGE_ALARM 播放系统默认闹钟铃声。
 *
 * 通知音量被调成 0 时通知渠道往往发不出声，这条路径仍然按闹钟音量出声。
 * 播放上限 [MAX_DURATION_MS]，用户点通知或回到应用时由调用方 [stop]。
 * 没有默认铃声或播放失败时静默放弃，只保留通知与震动，不抛异常。
 */
object TimerAlertSound {

    const val MAX_DURATION_MS = 15_000L

    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private val stopTask = Runnable { stop() }

    fun play(context: Context) {
        val appContext = context.applicationContext
        val uri = defaultAlarmUri() ?: return
        synchronized(lock) {
            releaseLocked()
            val created = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(appContext, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
            if (created == null) {
                // 无默认铃声或播放失败：降级为仅通知 + 震动。
                releaseLocked()
                return
            }
            player = created
            created.setOnCompletionListener { stop() }
            created.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            handler.removeCallbacks(stopTask)
            handler.postDelayed(stopTask, MAX_DURATION_MS)
        }
    }

    fun stop() {
        synchronized(lock) {
            handler.removeCallbacks(stopTask)
            releaseLocked()
        }
    }

    fun isPlaying(): Boolean = synchronized(lock) { player != null }

    /** 必须与 [play] 成对：置空引用后 stop/reset/release，重复调用无副作用。 */
    private fun releaseLocked() {
        val current = player ?: return
        player = null
        runCatching { if (current.isPlaying) current.stop() }
        runCatching { current.reset() }
        runCatching { current.release() }
    }

    private fun defaultAlarmUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
}
