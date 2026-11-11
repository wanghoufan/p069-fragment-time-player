package com.wanghoufan.fragmentplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wanghoufan.fragmentplayer.MainActivity
import com.wanghoufan.fragmentplayer.R

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var suppressionRecoveryCount = 0

    private val wakeLockController = PlaybackWakeLockController(
        acquireLock = {
            wakeLock?.let { lock ->
                if (!lock.isHeld) lock.acquire()
            }
        },
        releaseLock = {
            wakeLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
        }
    )

    override fun onCreate() {
        super.onCreate()

        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { setReferenceCounted(false) }
        Log.i(TAG, "service created wakeLockAvailable=${wakeLock != null}")

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // 媒体通知与到点提醒必须分渠道，避免系统把两类声音合并或静音。
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(MEDIA_CHANNEL_ID)
            .setChannelName(R.string.playback_channel_name)
            .build()
        notificationProvider.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        setMediaNotificationProvider(notificationProvider)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        player = exoPlayer
        exoPlayer.addListener(holdListener)
        session = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Media3 只在「控制器已连接且在播放」时把服务推进前台；这里把它与实际播放意图对齐记录，
     * 锁屏后若出现「播放中但未进前台」就是保活退化的直接证据。
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired)
        val exoPlayer = player
        val expected = exoPlayer != null &&
            PlaybackHoldPolicy.shouldRunForeground(exoPlayer.playWhenReady, exoPlayer.playbackState)
        Log.i(TAG, "notification update foregroundRequired=$startInForegroundRequired expectForeground=$expected")
        if (expected && !startInForegroundRequired) {
            Log.w(TAG, "playback ongoing but media notification was not promoted to foreground")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "task removed playbackOngoing=$isPlaybackOngoing")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i(TAG, "service destroyed")
        mainHandler.removeCallbacksAndMessages(null)
        player?.removeListener(holdListener)
        wakeLockController.release()
        wakeLock = null
        session?.release()
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }

    /**
     * ExoPlayer 的播放意图是 playWhenReady，一旦为 true 就必须有应用级锁兜底：
     * 锁屏/灭屏期间由它保证 CPU 与音频线程不被系统挂起。
     */
    private val holdListener = object : Player.Listener {

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updateHoldState()
            Log.i(TAG, "playWhenReady=$playWhenReady reason=${playWhenReadyReason(reason)}")
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG) {
                scheduleSuppressionRecovery()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) suppressionRecoveryCount = 0
            updateHoldState()
            Log.i(TAG, "isPlaying=$isPlaying")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateHoldState()
            Log.i(TAG, "playbackState=${playbackStateName(playbackState)}")
        }
    }

    private fun updateHoldState() {
        val exoPlayer = player ?: return
        val shouldHold = PlaybackHoldPolicy.shouldHoldWakeLock(
            exoPlayer.playWhenReady,
            exoPlayer.playbackState
        )
        if (wakeLockController.isHeld == shouldHold) return
        wakeLockController.update(shouldHold)
        Log.i(TAG, "partial wake lock ${if (shouldHold) "acquired" else "released"}")
    }

    /**
     * 系统短暂占用音频焦点（锁屏音效、系统提示音等）会让 ExoPlayer 进入抑制状态；
     * 抑制超过系统阈值后 ExoPlayer 会以 SUPPRESSED_TOO_LONG 自行暂停。这不是用户意图，
     * 也不属于到点收尾（那条走 USER_REQUEST），因此在抑制结束后恢复一次播放。
     */
    private fun scheduleSuppressionRecovery() {
        if (suppressionRecoveryCount >= MAX_SUPPRESSION_RECOVERY) return
        mainHandler.removeCallbacks(suppressionRecoveryTask)
        mainHandler.postDelayed(suppressionRecoveryTask, SUPPRESSION_RECOVERY_DELAY_MS)
    }

    private val suppressionRecoveryTask = Runnable {
        val exoPlayer = player ?: return@Runnable
        if (exoPlayer.playWhenReady) return@Runnable
        if (exoPlayer.playbackState == Player.STATE_IDLE ||
            exoPlayer.playbackState == Player.STATE_ENDED
        ) {
            return@Runnable
        }
        suppressionRecoveryCount++
        Log.i(TAG, "resume after suppressed-too-long attempt=$suppressionRecoveryCount")
        exoPlayer.play()
    }

    private fun playWhenReadyReason(reason: Int): String = when (reason) {
        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
        Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
        Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
        Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG -> "SUPPRESSED_TOO_LONG"
        else -> reason.toString()
    }

    private fun playbackStateName(playbackState: Int): String = when (playbackState) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> playbackState.toString()
    }

    companion object {
        const val MEDIA_CHANNEL_ID = "media_playback"

        private const val TAG = "PlaybackService"
        private const val WAKE_LOCK_TAG = "FragmentTime:Playback"

        /** 覆盖一段最长 3 分钟的单次计时，抑制恢复最多重试 3 次，避免无限抢占焦点。 */
        private const val MAX_SUPPRESSION_RECOVERY = 3
        private const val SUPPRESSION_RECOVERY_DELAY_MS = 2000L
    }
}