package com.wanghoufan.fragmentplayer.core.timer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「时光流速」只压缩倒计时的墙钟时间：目标 3 分钟在 6× 下实际 30 秒走完。
 */
class TimeFlowMathTest {

    private val threeMinutes = 3 * 60 * 1000L

    @Test
    fun optionsAreOneTwoSixTen() {
        assertEquals(listOf(1, 2, 6, 10), TimeFlowMath.OPTIONS)
        TimeFlowMath.OPTIONS.forEach { assertEquals(it, TimeFlowMath.sanitize(it)) }
    }

    @Test
    fun normalFlowKeepsWallClockTarget() {
        assertEquals(180_000L, TimeFlowMath.realTargetMs(threeMinutes, 1))
        assertEquals(1_000L + 180_000L, TimeFlowMath.deadlineAt(1_000L, threeMinutes, 1))
        assertEquals(15_000L, TimeFlowMath.displayRemainingMs(threeMinutes, 15_000L, 1))
    }

    @Test
    fun twoTimesFinishesInNinetySeconds() {
        assertEquals(90_000L, TimeFlowMath.realTargetMs(threeMinutes, 2))
        assertEquals(1_000L + 90_000L, TimeFlowMath.deadlineAt(1_000L, threeMinutes, 2))
        assertEquals(30_000L, TimeFlowMath.displayRemainingMs(threeMinutes, 15_000L, 2))
    }

    @Test
    fun sixTimesFinishesInThirtySeconds() {
        assertEquals(30_000L, TimeFlowMath.realTargetMs(threeMinutes, 6))
        assertEquals(1_000L + 30_000L, TimeFlowMath.deadlineAt(1_000L, threeMinutes, 6))
        // 真实只剩 15 秒时，界面上还应显示 1 分 30 秒。
        assertEquals(90_000L, TimeFlowMath.displayRemainingMs(threeMinutes, 15_000L, 6))
        assertEquals(90_000L, TimeFlowMath.virtualElapsedMs(threeMinutes, 15_000L, 6))
    }

    @Test
    fun tenTimesFinishesInEighteenSeconds() {
        assertEquals(18_000L, TimeFlowMath.realTargetMs(threeMinutes, 10))
        assertEquals(1_000L + 18_000L, TimeFlowMath.deadlineAt(1_000L, threeMinutes, 10))
        assertEquals(50_000L, TimeFlowMath.displayRemainingMs(threeMinutes, 5_000L, 10))
        assertEquals(0L, TimeFlowMath.displayRemainingMs(threeMinutes, 0L, 10))
    }

    @Test
    fun displayNeverExceedsTargetAndRoundsUpWallClock() {
        // 不能被倍率整除时向上取整，宁可晚一点到点也不提前。
        assertEquals(16_667L, TimeFlowMath.realTargetMs(100_000L, 6))
        assertEquals(100_000L, TimeFlowMath.displayRemainingMs(100_000L, 16_667L, 6))
        assertEquals(0L, TimeFlowMath.virtualElapsedMs(100_000L, 16_667L, 6))
        assertEquals(100_000L, TimeFlowMath.virtualElapsedMs(100_000L, 0L, 6))
    }

    @Test
    fun unknownMultiplierFallsBackToNormal() {
        assertEquals(1, TimeFlowMath.sanitize(0))
        assertEquals(1, TimeFlowMath.sanitize(3))
        assertEquals(1, TimeFlowMath.sanitize(12))
        assertEquals(6, TimeFlowMath.sanitize(6))
        assertEquals(10, TimeFlowMath.sanitize(10))
        assertEquals(180_000L, TimeFlowMath.realTargetMs(threeMinutes, 3))
        assertEquals(180_000L, TimeFlowMath.realTargetMs(threeMinutes, 12))
    }
}
