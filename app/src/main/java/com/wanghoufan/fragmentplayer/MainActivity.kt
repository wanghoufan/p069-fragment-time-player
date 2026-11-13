package com.wanghoufan.fragmentplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.wanghoufan.fragmentplayer.shortcut.ShortcutStart
import com.wanghoufan.fragmentplayer.timer.TimerAlertSound
import com.wanghoufan.fragmentplayer.ui.FragmentTimeRoot

class MainActivity : AppCompatActivity() {

    /**
     * 媒体播放通知是 mediaPlayback 前台服务对用户可见的前提：Android 13+ 未授权时
     * 通知栏看不到正在播放的媒体，锁屏后服务更容易被系统当作普通后台服务回收，
     * 所以启动时补一次请求（设置页仍有手动入口）。
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 重建（旋转/回收后恢复）会原样带回启动 Intent，只有全新启动才消费快捷方式。
        if (savedInstanceState == null) handleShortcutIntent(intent)
        requestPlaybackNotificationPermission()
        setContent {
            val exitBackGuard = remember {
                ExitBackGuard(
                    onFirstBack = {
                        Toast.makeText(
                            this,
                            R.string.back_press_again_to_exit,
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onExit = { finish() }
                )
            }
            BackHandler { exitBackGuard.onBackPressed() }
            FragmentTimeRoot()
        }
    }

    override fun onResume() {
        super.onResume()
        // 用户回到应用（含点通知进入）即认为已经收到提醒，停掉最长 15 秒的到点铃声。
        TimerAlertSound.stop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    /**
     * 桌面快捷方式把 presetId 带进来。清掉 action 是为了让旋转/重建后的
     * onCreate 不会把同一次点击再执行一遍。
     */
    private fun handleShortcutIntent(intent: Intent?) {
        if (intent == null || intent.action != ShortcutStart.ACTION_START_PRESET) return
        val presetId = intent.getLongExtra(ShortcutStart.EXTRA_PRESET_ID, -1L)
        intent.action = null
        if (presetId <= 0L) return
        (application as FragmentTimeApp).requestShortcutStart(presetId)
    }

    private fun requestPlaybackNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        runCatching {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}