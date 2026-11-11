package com.wanghoufan.fragmentplayer.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalSortTest {

    @Test
    fun numbersCompareByValue() {
        assertTrue(NaturalSort.compare("2.mp3", "10.mp3") < 0)
        assertTrue(NaturalSort.compare("10.mp3", "2.mp3") > 0)
        assertTrue(NaturalSort.compare("第02节.mp4", "第10节.mp4") < 0)
    }

    @Test
    fun chapterPrefixOrdersNumerically() {
        val input = listOf("第10节 课程.mp4", "第2节 课程.mp4", "第1节 课程.mp4")
        assertEquals(
            listOf("第1节 课程.mp4", "第2节 课程.mp4", "第10节 课程.mp4"),
            input.sortedWith(NaturalSort)
        )
    }

    @Test
    fun pathsSortByNumberSegmentsAndStayStable() {
        val input = listOf("第10章/b.mp4", "第2章/a.mp4", "第2章/10.mp4", "第2章/2.mp4")
        assertEquals(
            listOf("第2章/2.mp4", "第2章/10.mp4", "第2章/a.mp4", "第10章/b.mp4"),
            input.sortedWith(NaturalSort)
        )
        assertEquals(listOf("第2章/2.mp4"), listOf("第2章/2.mp4").sortedWith(NaturalSort))
    }

    @Test
    fun equalNumericValueFallsBackToPlainStringCompare() {
        assertNotEquals(0, NaturalSort.compare("02.mp3", "2.mp3"))
        assertTrue(NaturalSort.compare("02.mp3", "2.mp3") < 0)
    }

    @Test
    fun comparatorSignIsSymmetricAndIdenticalValuesAreEqual() {
        val pairs = listOf(
            "2" to "10",
            "第2章/a.mp4" to "第10章/a.mp4",
            "abc" to "abc",
            "a" to "a2"
        )
        pairs.forEach { (a, b) ->
            val forward = NaturalSort.compare(a, b).coerceIn(-1, 1)
            val backward = NaturalSort.compare(b, a).coerceIn(-1, 1)
            assertEquals(0, forward + backward)
        }
        assertEquals(0, NaturalSort.compare("abc", "abc"))
    }
}