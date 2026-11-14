package com.wanghoufan.fragmentplayer.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.model.TimerPhase
import com.wanghoufan.fragmentplayer.ui.components.LocalNavigateToCourses
import com.wanghoufan.fragmentplayer.ui.screens.CoursesScreen
import com.wanghoufan.fragmentplayer.ui.screens.SettingsScreen
import com.wanghoufan.fragmentplayer.ui.screens.TimerScreen
import com.wanghoufan.fragmentplayer.ui.theme.FragmentTimeTheme

private enum class RootTab(
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    TIMER(R.string.nav_timer, Icons.Filled.PlayArrow),
    COURSES(R.string.nav_courses, Icons.Filled.List),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings)
}

@Composable
fun FragmentTimeRoot(rootViewModel: RootViewModel = viewModel()) {
    val settings by rootViewModel.settings.collectAsStateWithLifecycle()
    var interruptedNotice by remember { mutableStateOf(rootViewModel.peekInterruptedNotice()) }

    LaunchedEffect(Unit) {
        var attempts = 0
        while (interruptedNotice == null && attempts < 60) {
            delay(500)
            interruptedNotice = rootViewModel.peekInterruptedNotice()
            attempts++
        }
    }

    FragmentTimeTheme(themeMode = settings.theme, fontScale = settings.fontScale) {
        var selectedTabIndex by rememberSaveable { mutableIntStateOf(RootTab.TIMER.ordinal) }
        val tabs = RootTab.entries

        // 返回键分层：非计时 Tab 先回计时页，完成页回 IDLE，其余交给首页双击退出。
        val timerViewModel: MainViewModel = viewModel()
        val timer by timerViewModel.timer.collectAsStateWithLifecycle()
        BackHandler(
            enabled = selectedTabIndex != RootTab.TIMER.ordinal ||
                timer.phase == TimerPhase.FINISHED
        ) {
            when (resolveBackAction(selectedTabIndex, RootTab.TIMER.ordinal, timer.phase)) {
                BackAction.SWITCH_TO_TIMER -> selectedTabIndex = RootTab.TIMER.ordinal
                BackAction.CLEAR_RESULT -> timerViewModel.clearResult()
                BackAction.EXIT_GUARD -> Unit
            }
        }

        val shortcutRequest by rootViewModel.shortcutStartPresetId.collectAsStateWithLifecycle()
        LaunchedEffect(shortcutRequest) {
            val presetId = shortcutRequest ?: return@LaunchedEffect
            // 快捷方式至少要回到计时页；已在计时时这里不会再重开一轮。
            selectedTabIndex = RootTab.TIMER.ordinal
            rootViewModel.startFromShortcut(presetId)
            rootViewModel.consumeShortcutStart()
        }

        val navigateToCourses: () -> Unit = remember {
            {
                selectedTabIndex = RootTab.COURSES.ordinal
            }
        }

        CompositionLocalProvider(LocalNavigateToCourses provides navigateToCourses) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (tabs[selectedTabIndex]) {
                        RootTab.TIMER -> TimerScreen()
                        RootTab.COURSES -> CoursesScreen()
                        RootTab.SETTINGS -> SettingsScreen()
                    }

                    interruptedNotice?.let { notice ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp),
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 6.dp,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.tui_interrupted_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = notice,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(
                                    onClick = {
                                        rootViewModel.consumeInterruptedNotice()
                                        interruptedNotice = null
                                    }
                                ) {
                                    Text(stringResource(R.string.tui_action_got_it))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}