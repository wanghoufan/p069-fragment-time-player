package com.wanghoufan.fragmentplayer.shortcut

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wanghoufan.fragmentplayer.MainActivity
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.model.Preset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 长按菜单直接管理用户的场景列表：顺序、上限、文案、图标和启动意图都要锁住，
 * 否则改名或新增场景后菜单会与 App 内不一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class ShortcutStartTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val brush = Preset(id = 1L, name = "刷牙", durationSec = 120, sortOrder = 0)
    private val eye = Preset(id = 2L, name = "滴眼药水", durationSec = 180, sortOrder = 1)

    @Test
    fun usesPresetOrderAndCapsAtFour() {
        val presets = (0 until 6).map {
            Preset(id = 10L + it, name = "场景$it", durationSec = 60, sortOrder = it)
        }
        val shortcuts = ShortcutStart.buildShortcuts(context, presets.reversed())
        assertEquals(ShortcutStart.MAX_SHORTCUTS, shortcuts.size)
        assertEquals("场景0", shortcuts.first().shortLabel.toString().removePrefix("开始"))
        assertEquals("场景3", shortcuts.last().shortLabel.toString().removePrefix("开始"))
    }

    @Test
    fun labelsFollowPresetName() {
        val shortcuts = ShortcutStart.buildShortcuts(context, listOf(brush, eye))
        assertEquals("开始刷牙", shortcuts[0].shortLabel.toString())
        // 长标签会被 MIUI 截断成「开始刷牙 · 0…」，所以只保留短标签。
        assertEquals(null, shortcuts[0].longLabel)
        assertEquals("开始滴眼药水", shortcuts[1].shortLabel.toString())
        assertEquals(null, shortcuts[1].longLabel)
    }

    @Test
    fun intentCarriesPresetIdToMainActivity() {
        val intent = ShortcutStart.intentFor(context, brush.id)
        assertEquals(ShortcutStart.ACTION_START_PRESET, intent.action)
        assertEquals(brush.id, intent.getLongExtra(ShortcutStart.EXTRA_PRESET_ID, -1L))
        assertEquals(MainActivity::class.java.name, intent.component?.className)

        val built = ShortcutStart.buildShortcuts(context, listOf(brush)).first().intent
        assertEquals(ShortcutStart.ACTION_START_PRESET, built.action)
        assertEquals(brush.id, built.getLongExtra(ShortcutStart.EXTRA_PRESET_ID, -1L))
    }

    @Test
    fun idsAreStableAcrossRefreshes() {
        val first = ShortcutStart.buildShortcuts(context, listOf(brush)).first().id
        val second = ShortcutStart.buildShortcuts(
            context,
            listOf(brush.copy(name = "早上刷牙"))
        ).first().id
        assertEquals(first, second)
    }

    @Test
    fun iconsMatchTheTwoDefaultPresets() {
        assertEquals(R.drawable.ic_shortcut_brush, ShortcutStart.iconFor("刷牙"))
        assertEquals(R.drawable.ic_shortcut_eye, ShortcutStart.iconFor("滴眼药水"))
        assertEquals(R.drawable.ic_shortcut_timer, ShortcutStart.iconFor("背单词"))
        assertTrue(ShortcutStart.MAX_SHORTCUTS <= 4)
    }
}
