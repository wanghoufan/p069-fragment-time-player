package com.wanghoufan.fragmentplayer.timer

/** 一次到点提醒要做的事：铃声与震动相互独立，通知永远发。 */
data class AlertBehavior(
    val playRingtone: Boolean,
    val vibrate: Boolean,
    val notificationSound: Boolean
)

/**
 * 到点提醒的判定：开关只决定「做什么」，不决定是否通知。
 *
 * 铃声由 [TimerAlertSound] 独立走 USAGE_ALARM 播放，不依赖通知渠道；
 * 通知渠道因此固定静音，避免系统通知音量可用时出现叠音。
 */
object AlertPolicy {

    fun resolve(alertSound: Boolean, vibration: Boolean): AlertBehavior = AlertBehavior(
        playRingtone = alertSound,
        vibrate = vibration,
        notificationSound = false
    )
}
