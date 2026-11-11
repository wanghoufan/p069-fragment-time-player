package com.wanghoufan.fragmentplayer.ui

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.model.LanguageMode

@StringRes
fun LanguageMode.labelRes(): Int = when (this) {
    LanguageMode.FOLLOW_SYSTEM -> R.string.seu_language_system
    LanguageMode.ZH_CN -> R.string.seu_language_zh_cn
    LanguageMode.EN -> R.string.seu_language_en
}

fun LanguageMode.languageTag(): String? = when (this) {
    LanguageMode.FOLLOW_SYSTEM -> null
    LanguageMode.ZH_CN -> "zh-CN"
    LanguageMode.EN -> "en"
}

fun LanguageMode.toLocaleListCompat(): LocaleListCompat {
    val tag = languageTag()
    return if (tag == null) {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(tag)
    }
}

fun applyLanguage(mode: LanguageMode) {
    AppCompatDelegate.setApplicationLocales(mode.toLocaleListCompat())
}