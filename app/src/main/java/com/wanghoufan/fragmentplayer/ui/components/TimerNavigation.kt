package com.wanghoufan.fragmentplayer.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 计时页「去课程页导入」的跨 Tab 跳转口子。
 * 由根组件提供实现，计时页本身保持无参签名。
 */
val LocalNavigateToCourses = staticCompositionLocalOf<() -> Unit> { {} }