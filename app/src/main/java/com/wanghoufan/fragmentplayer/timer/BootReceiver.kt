package com.wanghoufan.fragmentplayer.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.model.TimerPhase

/**
 * 重启后不假装计时仍在继续：取消遗留闹钟、清掉会话，并把真实经过时间写成中断提示。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
            val snapshot = TimerSessionStore.read(context)
            TimerNotifications.cancelAlarm(context)
            when (snapshot.phase) {
                TimerPhase.RUNNING, TimerPhase.PAUSED, TimerPhase.PREPARING -> {
                    if (!snapshot.alertPosted && snapshot.targetSec > 0) {
                        val remaining = if (snapshot.phase == TimerPhase.PAUSED) {
                            snapshot.remainingMs
                        } else {
                            (snapshot.deadlineWall - System.currentTimeMillis()).coerceAtLeast(0L)
                        }
                        val elapsedSec = ((snapshot.targetSec * 1000L - remaining).coerceAtLeast(0L) / 1000L)
                        TimerSessionStore.writeNotice(
                            context,
                            context.getString(
                                R.string.timer_interrupted_boot,
                                TimerNotifications.formatClock(elapsedSec)
                            )
                        )
                    }
                    TimerSessionStore.clearSession(context)
                }

                TimerPhase.FINISHED -> TimerSessionStore.clearSession(context)
                TimerPhase.IDLE -> Unit
            }
        } catch (_: Throwable) {
        }
    }
}