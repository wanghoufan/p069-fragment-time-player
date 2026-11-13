package com.wanghoufan.fragmentplayer.shortcut

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.wanghoufan.fragmentplayer.MainActivity
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.model.Preset

/**
 * 桌面长按图标的快捷方式：菜单内容直接来自用户的场景列表，
 * 点一下把 presetId 交给 MainActivity，冷启动也能直接进入计时。
 */
object ShortcutStart {

    const val ACTION_START_PRESET = "com.wanghoufan.fragmentplayer.action.START_PRESET"
    const val EXTRA_PRESET_ID = "com.wanghoufan.fragmentplayer.extra.PRESET_ID"

    /** 启动器一般只展示前 4 个，多出来的没有意义。 */
    const val MAX_SHORTCUTS = 4

    fun intentFor(context: Context, presetId: Long): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_START_PRESET)
            .putExtra(EXTRA_PRESET_ID, presetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun buildShortcuts(context: Context, presets: List<Preset>): List<ShortcutInfoCompat> =
        presets
            .sortedBy { it.sortOrder }
            .take(MAX_SHORTCUTS)
            .map { preset ->
                ShortcutInfoCompat.Builder(context, shortcutId(preset.id))
                    // 只给短标签：部分启动器会优先显示长标签并把它截断成「开始刷牙 · 0…」。
                    .setShortLabel(context.getString(R.string.shortcut_start_label, preset.name))
                    .setIcon(IconCompat.createWithResource(context, iconFor(preset.name)))
                    .setIntent(intentFor(context, preset.id))
                    .setRank(preset.sortOrder)
                    .build()
            }

    /** 场景增删改后调用；空列表就清空菜单，避免删掉的场景还挂在图标上。 */
    fun refresh(context: Context, presets: List<Preset>) {
        val shortcuts = buildShortcuts(context, presets)
        runCatching {
            if (shortcuts.isEmpty()) {
                ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            } else {
                val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
                ShortcutManagerCompat.setDynamicShortcuts(
                    context,
                    shortcuts.take(max.coerceAtLeast(1))
                )
            }
        }
    }

    internal fun shortcutId(presetId: Long): String = "preset-$presetId"

    /** 目前只给两个默认场景配了专属图标，其余场景沿用通用计时图标。 */
    internal fun iconFor(name: String): Int = when {
        name.contains("牙") -> R.drawable.ic_shortcut_brush
        name.contains("眼") -> R.drawable.ic_shortcut_eye
        else -> R.drawable.ic_shortcut_timer
    }
}
