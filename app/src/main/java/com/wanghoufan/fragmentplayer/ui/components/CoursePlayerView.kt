package com.wanghoufan.fragmentplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.wanghoufan.fragmentplayer.R

/**
 * 视频画面容器。播放器本身由播放引擎持有，这里只做绑定，绝不负责释放。
 */
@Composable
fun CoursePlayerView(
    player: Player?,
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    if (player == null || !connected) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.tui_video_waiting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
        },
        onRelease = { view ->
            view.player = null
        }
    )
}