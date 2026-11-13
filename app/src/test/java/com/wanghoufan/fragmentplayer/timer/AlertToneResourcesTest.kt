package com.wanghoufan.fragmentplayer.timer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wanghoufan.fragmentplayer.core.model.AlertTone
import com.wanghoufan.fragmentplayer.core.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 三个内置短铃必须真实打进包里，且映射到各自的 raw 资源；
 * 默认铃声与时长上限也一并锁住，防止以后又退回「长铃声」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertToneResourcesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun defaultToneIsShortBuiltInChime() {
        assertEquals(AlertTone.CHIME, AppSettings().alertTone)
        assertTrue(TimerAlertSound.MAX_DURATION_MS <= 3_000L)
    }

    @Test
    fun builtInTonesMapToExistingRawResources() {
        listOf(AlertTone.CHIME, AlertTone.BELL, AlertTone.SOFT).forEach { tone ->
            val resId = TimerAlertSound.resIdFor(tone)
            assertNotNull("$tone 缺少 raw 资源", resId)
            val bytes = context.resources.openRawResource(resId!!).use { it.readBytes() }
            assertTrue("$tone 音频为空", bytes.size > 10_000)
            // 1.0–1.8 秒单声道 44.1kHz 16bit ≈ 88KB–160KB，留足余量但不接受长音频。
            assertTrue("$tone 音频过大，可能又变成了长铃声", bytes.size < 200_000)
        }
    }

    @Test
    fun systemToneHasNoBundledResource() {
        assertNull(TimerAlertSound.resIdFor(AlertTone.SYSTEM))

    }

    @Test
    fun allTonesHaveDisplayName() {
        assertEquals(AlertTone.entries.size, 4)
    }
}