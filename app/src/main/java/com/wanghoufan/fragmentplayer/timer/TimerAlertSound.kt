package com.wanghoufan.fragmentplayer.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.model.AlertTone

/**
 * 到点铃声：不依赖通知渠道，直接用 MediaPlayer 走 USAGE_ALARM 播放。
 *
 * 三个内置音都是 1.2–1.6 秒的短提示音，响一次就结束；系统闹钟选项也限制在
 * [MAX_DURATION_MS] 内，避免以前「一直响十几秒」的体验。
 * 通知音量被调成 0 时这条路径仍按闹钟音量出声。
 * 没有可用音源或播放失败时静默放弃，只保留通知与震动，不抛异常。
 */
object TimerAlertSound {

    /** 所有铃声的最长播放时长：内置音自然收尾，系统铃声到点就截断。 */
    const val MAX_DURATION_MS = 2_500L

    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private val stopTask = Runnable { stop() }

    fun play(context: Context, tone: AlertTone, maxDurationMs: Long = MAX_DURATION_MS): Boolean {
        val appContext = context.applicationContext
        val uri = uriFor(appContext, tone) ?: return false
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
                    // 内置音只响一次；系统铃声可能很长，也不循环。
                    isLooping = false
                    prepare()
                    start()
                }
            }.getOrNull()
            if (created == null) {
                // 音源不可用或播放失败：降级为仅通知 + 震动。
                releaseLocked()
                return false
            }
            player = created
            created.setOnCompletionListener { stop() }
            created.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            handler.removeCallbacks(stopTask)
            handler.postDelayed(stopTask, maxDurationMs.coerceAtLeast(1L))
            return true
        }
    }

    fun stop() {
        synchronized(lock) {
            handler.removeCallbacks(stopTask)
            releaseLocked()
        }
    }

    fun isPlaying(): Boolean = synchronized(lock) { player != null }

    /** 三个内置音各自对应的 raw 资源；系统铃声走系统默认闹钟。 */
    fun resIdFor(tone: AlertTone): Int? = when (tone) {
        AlertTone.CHIME -> R.raw.tone_chime
        AlertTone.BELL -> R.raw.tone_bell
        AlertTone.SOFT -> R.raw.tone_soft
        AlertTone.SYSTEM -> null
    }

    private fun uriFor(context: Context, tone: AlertTone): Uri? {
        val resId = resIdFor(tone)
        if (resId != null) {
            return Uri.parse("android.resource://${context.packageName}/$resId")
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    /** 必须与 [play] 成对：置空引用后 stop/reset/release，重复调用无副作用。 */
    private fun releaseLocked() {
        val current = player ?: return
        player = null
        runCatching { if (current.isPlaying) current.stop() }
        runCatching { current.reset() }
        runCatching { current.release() }
    }
}