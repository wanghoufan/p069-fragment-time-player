package com.wanghoufan.fragmentplayer.timer

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.wanghoufan.fragmentplayer.FragmentTimeApp
import com.wanghoufan.fragmentplayer.core.model.TimerPhase
import com.wanghoufan.fragmentplayer.core.timer.TimerControllerApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 计时前台服务：只在会话进行中显示常驻倒计时通知，状态变化才更新，不每秒 notify。
 * 到点闹钟路径在同一服务里完成收尾，收尾后立即退出前台。
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // 到点收尾独立于通知观察协程：onDestroy（含暂停触发的 stopService）不得打断收尾。
    private val alarmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var foregroundStarted = false
    private var alarmInFlight = false

    override fun onCreate() {
        super.onCreate()
        TimerNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val timer = runCatching {
            (applicationContext as FragmentTimeApp).graph.timer
        }.getOrNull()
        if (timer == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_ALARM) {
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            if (sessionId == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            alarmInFlight = true
            startForegroundNow(TimerNotifications.buildAlarmProcessing(this))
            alarmScope.launch {
                try {
                    withContext(NonCancellable) {
                        runCatching { timer.handleAlarm(sessionId) }
                    }
                } finally {
                    alarmInFlight = false
                    runCatching { stopForegroundNow() }
                    runCatching { stopSelf() }
                    alarmScope.cancel()
                }
            }
            return START_NOT_STICKY
        }

        observe(timer)
        if (timer.state.value.phase == TimerPhase.RUNNING) {
            showRunning(timer)
        } else if (!alarmInFlight) {
            stopForegroundNow()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // 只取消通知观察协程；到点收尾跑在独立的 alarmScope 上，由它自己结束后回收。
        observerJob?.cancel()
        observerJob = null
        scope.cancel()
        if (!alarmInFlight) alarmScope.cancel()
        stopForegroundNow()
        super.onDestroy()
    }

    private fun observe(timer: TimerControllerApi) {
        if (observerJob?.isActive == true) return
        observerJob = scope.launch {
            timer.state
                .map { it.phase to it.sessionId }
                .distinctUntilChanged()
                .collect { (phase, _) ->
                    when (phase) {
                        TimerPhase.RUNNING -> showRunning(timer)
                        else -> if (!alarmInFlight) {
                            stopForegroundNow()
                            stopSelf()
                        }
                    }
                }
        }
    }

    private fun showRunning(timer: TimerControllerApi) {
        val uiState = timer.state.value
        if (uiState.phase != TimerPhase.RUNNING) return
        startForegroundNow(
            TimerNotifications.buildRunning(this, uiState.presetName, uiState.remainingMs)
        )
    }

    private fun startForegroundNow(notification: android.app.Notification) {
        runCatching {
            ServiceCompat.startForeground(
                this,
                TimerNotifications.RUNNING_NOTIFICATION_ID,
                notification,
                foregroundServiceType()
            )
            foregroundStarted = true
        }.onFailure {
            stopSelf()
        }
    }

    private fun stopForegroundNow() {
        if (!foregroundStarted) return
        runCatching { stopForeground(android.app.Service.STOP_FOREGROUND_REMOVE) }
        foregroundStarted = false
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

    companion object {
        const val ACTION_SYNC = "com.wanghoufan.fragmentplayer.timer.ACTION_SYNC"
        const val ACTION_ALARM = "com.wanghoufan.fragmentplayer.timer.ACTION_ALARM"
        const val EXTRA_SESSION_ID = "session_id"
    }
}