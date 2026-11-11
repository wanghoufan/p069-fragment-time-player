package com.wanghoufan.fragmentplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanghoufan.fragmentplayer.FragmentTimeApp
import com.wanghoufan.fragmentplayer.core.model.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 根级 ViewModel：把设置里的主题与字体缩放接到 Compose 主题，
 * 并持有跨 Tab 的中断提示（任何 Tab 首屏都要能看到）。
 */
class RootViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as FragmentTimeApp).graph

    val settings: StateFlow<AppSettings> = graph.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppSettings())

    fun peekInterruptedNotice(): String? = graph.timer.peekInterruptedNotice()

    fun consumeInterruptedNotice() {
        graph.timer.consumeInterruptedNotice()
    }
}