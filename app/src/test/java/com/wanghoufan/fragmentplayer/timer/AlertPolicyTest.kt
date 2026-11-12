package com.wanghoufan.fragmentplayer.timer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 铃声与震动各自听自己的开关，通知始终发、始终静音（声音交给 MediaPlayer）。
 */
class AlertPolicyTest {

    @Test
    fun bothEnabledRingsAndVibrates() {
        val behavior = AlertPolicy.resolve(alertSound = true, vibration = true)

        assertTrue(behavior.playRingtone)
        assertTrue(behavior.vibrate)
        assertFalse(behavior.notificationSound)
    }

    @Test
    fun soundOnlySkipsVibration() {
        val behavior = AlertPolicy.resolve(alertSound = true, vibration = false)

        assertTrue(behavior.playRingtone)
        assertFalse(behavior.vibrate)
        assertFalse(behavior.notificationSound)
    }

    @Test
    fun vibrationOnlySkipsRingtone() {
        val behavior = AlertPolicy.resolve(alertSound = false, vibration = true)

        assertFalse(behavior.playRingtone)
        assertTrue(behavior.vibrate)
        assertFalse(behavior.notificationSound)
    }

    @Test
    fun bothDisabledKeepsSilentNotification() {
        val behavior = AlertPolicy.resolve(alertSound = false, vibration = false)

        assertFalse(behavior.playRingtone)
        assertFalse(behavior.vibrate)
        assertFalse(behavior.notificationSound)
    }
}
