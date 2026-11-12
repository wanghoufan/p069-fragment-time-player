package com.wanghoufan.fragmentplayer.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.wanghoufan.fragmentplayer.MainActivity
import com.wanghoufan.fragmentplayer.R
import java.util.Locale

/**
 * 到点提醒与常驻计时通知统一在这里构造；两类通知使用独立渠道。
 * alertPosted 是跨路径去重占位，由 synchronized + commit 保证同一次到点只提醒一次。
 */
object TimerNotifications {

    /**
     * 到点提醒渠道固定静音：铃声已改由 [TimerAlertSound] 用 MediaPlayer 走 USAGE_ALARM 独立播放。
     * 渠道声音创建后不可修改，所以换新 id，并清掉旧的带铃声渠道，避免两路声音叠加。
     */
    const val CHANNEL_ALERT = "timer_alert_v2"
    private const val CHANNEL_ALERT_LEGACY = "timer_alert"
    const val CHANNEL_RUNNING = "timer_running"

    const val ALERT_NOTIFICATION_ID = 6901
    const val RUNNING_NOTIFICATION_ID = 6900

    private const val ALARM_REQUEST_CODE = 6902
    private const val OPEN_APP_REQUEST_CODE = 6903
    private val ALERT_VIBRATION = longArrayOf(0L, 500L, 300L, 500L, 300L, 500L)

    private val alertLock = Any()

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val alert = NotificationChannel(
            CHANNEL_ALERT,
            context.getString(R.string.timer_channel_alert_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.timer_channel_alert_description)
            // 声音与震动都由应用按用户开关单独执行，渠道自身静音，避免叠音、双震。
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val running = NotificationChannel(
            CHANNEL_RUNNING,
            context.getString(R.string.timer_channel_running_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.timer_channel_running_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        runCatching {
            manager.createNotificationChannel(alert)
            manager.createNotificationChannel(running)
            manager.deleteNotificationChannel(CHANNEL_ALERT_LEGACY)
        }
    }

    fun claimAlert(context: Context): Boolean = synchronized(alertLock) {
        if (TimerSessionStore.isAlertPosted(context)) return@synchronized false
        TimerSessionStore.markAlertPosted(context)
        true
    }

    fun isAlertPosted(context: Context): Boolean = TimerSessionStore.isAlertPosted(context)

    fun resetAlertPosted(context: Context) = TimerSessionStore.resetAlertPosted(context)

    fun postAlert(
        context: Context,
        presetName: String,
        targetSec: Int,
        sound: Boolean,
        vibration: Boolean
    ) {
        ensureChannels(context)
        val manager = runCatching {
            context.getSystemService(NotificationManager::class.java)
        }.getOrNull() ?: return
        val title = context.getString(R.string.timer_alert_title)
        val text = context.getString(
            R.string.timer_alert_text,
            presetName.ifBlank { context.getString(R.string.app_name) },
            formatClock(targetSec.toLong())
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            // 声音已与通知解耦：通知恒定静音，关闭铃声时仍然发通知，不能让用户完全收不到提醒。
            .setSilent(true)
            .build()
        runCatching { manager.notify(ALERT_NOTIFICATION_ID, notification) }

        val behavior = AlertPolicy.resolve(alertSound = sound, vibration = vibration)
        if (behavior.playRingtone) TimerAlertSound.play(context)
        if (behavior.vibrate) vibrate(context)
    }

    fun cancelAlert(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(ALERT_NOTIFICATION_ID)
        }
        TimerAlertSound.stop()
    }

    fun buildRunning(
        context: Context,
        presetName: String,
        remainingMs: Long
    ): Notification {
        ensureChannels(context)
        val title = presetName.ifBlank { context.getString(R.string.app_name) }
        return NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.timer_running_text))
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(System.currentTimeMillis() + remainingMs.coerceAtLeast(0L))
            .setShowWhen(true)
            .build()
    }

    fun buildAlarmProcessing(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.timer_alert_title))
            .setContentText(context.getString(R.string.timer_alert_processing_text))
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun scheduleAlarm(context: Context, sessionId: String, remainingMs: Long) {
        if (remainingMs <= 0L) return
        val alarmManager = runCatching {
            context.getSystemService(AlarmManager::class.java)
        }.getOrNull() ?: return
        val pending = alarmPendingIntent(context, sessionId, PendingIntent.FLAG_UPDATE_CURRENT)
            ?: return
        val triggerAt = System.currentTimeMillis() + remainingMs
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = runCatching {
            context.getSystemService(AlarmManager::class.java)
        }.getOrNull() ?: return
        val pending = alarmPendingIntent(context, null, PendingIntent.FLAG_NO_CREATE) ?: return
        runCatching { alarmManager.cancel(pending) }
        runCatching { pending.cancel() }
    }

    fun isExactAlarmAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = runCatching {
            context.getSystemService(AlarmManager::class.java)
        }.getOrNull() ?: return false
        return runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
    }

    fun formatClock(totalSec: Long): String {
        val safe = totalSec.coerceAtLeast(0L)
        return String.format(Locale.getDefault(), "%02d:%02d", safe / 60L, safe % 60L)
    }

    private fun alarmPendingIntent(
        context: Context,
        sessionId: String?,
        flags: Int
    ): PendingIntent? {
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_TIMER_ALARM
            if (sessionId != null) {
                putExtra(TimerAlarmReceiver.EXTRA_SESSION_ID, sessionId)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun vibrate(context: Context) {
        val vibrator = runCatching { context.getSystemService(Vibrator::class.java) }.getOrNull() ?: return
        runCatching {
            vibrator.vibrate(VibrationEffect.createWaveform(ALERT_VIBRATION, -1))
        }
    }
}