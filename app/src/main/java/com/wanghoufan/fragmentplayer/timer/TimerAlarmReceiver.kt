package com.wanghoufan.fragmentplayer.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.wanghoufan.fragmentplayer.FragmentTimeApp
import com.wanghoufan.fragmentplayer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 到点闹钟只做三件事：校验 sessionId、原子占位提醒、拉起计时服务收尾。
 * 任何异常都不允许让进程崩溃。
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != ACTION_TIMER_ALARM) return
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
            val snapshot = TimerSessionStore.read(context)
            if (snapshot.sessionId == null || snapshot.sessionId != sessionId) return
            if (!TimerNotifications.claimAlert(context)) return

            val pendingResult = goAsync()
            val workScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            workScope.launch {
                try {
                    val app = context.applicationContext as? FragmentTimeApp
                    val currentSettings = runCatching { app?.graph?.settings?.current() }.getOrNull()
                    TimerNotifications.postAlert(
                        context = context,
                        presetName = snapshot.presetName.ifBlank { context.getString(R.string.app_name) },
                        targetSec = snapshot.targetSec,
                        sound = currentSettings?.alertSound ?: true,
                        vibration = currentSettings?.vibration ?: true
                    )
                    val serviceIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_ALARM
                        putExtra(TimerService.EXTRA_SESSION_ID, sessionId)
                    }
                    runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
                        .onFailure {
                            val timer = runCatching { app?.graph?.timer }.getOrNull()
                            if (timer != null) runCatching { timer.handleAlarm(sessionId) }
                        }
                } catch (_: Throwable) {
                } finally {
                    pendingResult.finish()
                    workScope.cancel()
                }
            }
        } catch (_: Throwable) {
        }
    }

    companion object {
        const val ACTION_TIMER_ALARM = "com.wanghoufan.fragmentplayer.timer.TIMER_ALARM"
        const val EXTRA_SESSION_ID = "session_id"
    }
}