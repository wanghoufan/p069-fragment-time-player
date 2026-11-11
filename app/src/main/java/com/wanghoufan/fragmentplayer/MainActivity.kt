package com.wanghoufan.fragmentplayer

import android.Manifest
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