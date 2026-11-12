package com.wanghoufan.fragmentplayer.core.timer

/**
 * 「时光流速（测试用）」倍率换算：把倒计时的墙钟时间按倍率压缩，
 * 音频播放速度不变（只压计时，避免变调）。
 *
 * 引擎与持久化都只记「真实墙钟时间」，界面显示与记录口径再按倍率换回目标时长。
 * 全部是纯函数，非法倍率一律回退到 1×。
 *
 * 倍率 ≠ [NORMAL] 的轮次只用于测试：不写历史、不推进课程进度与断点。
 */
object TimeFlowMath {

    const val NORMAL = 1

    val OPTIONS: List<Int> = listOf(1, 2, 6, 10)

    fun sanitize(multiplier: Int): Int = if (multiplier in OPTIONS) multiplier else NORMAL

    /** 目标时长在给定倍率下真正要走的墙钟时长；向上取整，避免比目标提前到点。 */
    fun realTargetMs(targetMs: Long, multiplier: Int): Long {
        val flow = sanitize(multiplier).toLong()
        val target = targetMs.coerceAtLeast(0L)
        return (target + flow - 1L) / flow
    }

    /** 把真实墙钟剩余换算成界面显示的目标时长（不超过目标时长）。 */
    fun displayRemainingMs(targetMs: Long, realRemainingMs: Long, multiplier: Int): Long {
        val target = targetMs.coerceAtLeast(0L)
        val shown = realRemainingMs.coerceAtLeast(0L) * sanitize(multiplier)
        return shown.coerceAtMost(target)
    }

    /** 已走过的目标时长：按压缩前口径回算，用于手动结束与中断提示。 */
    fun virtualElapsedMs(targetMs: Long, realRemainingMs: Long, multiplier: Int): Long {
        val target = targetMs.coerceAtLeast(0L)
        val flow = sanitize(multiplier).toLong()
        val realElapsed = (realTargetMs(target, flow.toInt()) - realRemainingMs.coerceAtLeast(0L))
            .coerceAtLeast(0L)
        return (realElapsed * flow).coerceIn(0L, target)
    }

    /** 到点的墙钟时间点；倍率在开始这一刻固定，运行中不再变。 */
    fun deadlineAt(startWallMs: Long, targetMs: Long, multiplier: Int): Long =
        startWallMs + realTargetMs(targetMs, multiplier)
}
