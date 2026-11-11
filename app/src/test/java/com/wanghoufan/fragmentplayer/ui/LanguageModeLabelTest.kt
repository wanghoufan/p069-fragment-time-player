package com.wanghoufan.fragmentplayer.ui

import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.model.LanguageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageModeLabelTest {

    @Test
    fun everyLanguageModeMapsToItsOwnLabel() {
        assertEquals(R.string.seu_language_system, LanguageMode.FOLLOW_SYSTEM.labelRes())
        assertEquals(R.string.seu_language_zh_cn, LanguageMode.ZH_CN.labelRes())
        assertEquals(R.string.seu_language_en, LanguageMode.EN.labelRes())

        val labels = LanguageMode.entries.map { it.labelRes() }.toSet()
        assertEquals(LanguageMode.entries.size, labels.size)
    }

    @Test
    fun followSystemHasNoLanguageTag() {
        assertNull(LanguageMode.FOLLOW_SYSTEM.languageTag())
        assertEquals("zh-CN", LanguageMode.ZH_CN.languageTag())
        assertEquals("en", LanguageMode.EN.languageTag())
    }
}