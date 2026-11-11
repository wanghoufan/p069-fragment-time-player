package com.wanghoufan.fragmentplayer.core.playback

import androidx.media3.common.Player
import com.wanghoufan.fragmentplayer.core.model.Course
import com.wanghoufan.fragmentplayer.core.model.PlaybackUiState
import kotlinx.coroutines.flow.StateFlow

interface PlaybackEngineApi {
    val state: StateFlow<PlaybackUiState>
    val queue: StateFlow<List<Course>>
    val connected: StateFlow<Boolean>

    fun connect()

    fun player(): Player?

    suspend fun playCourse(course: Course, positionMs: Long): Result<Unit>

    suspend fun playQueue(courses: List<Course>, startIndex: Int, positionMs: Long): Result<Unit>

    fun play()

    fun pause()

    /** 只暂停，不把当前位置写成断点；用于「结束这一轮但不记入」的场景。 */
    fun pauseWithoutCheckpoint()

    fun seekTo(positionMs: Long)

    fun next()

    fun previous()

    fun setSpeed(speed: Float)

    suspend fun saveCheckpointNow()

    suspend fun waitForPlaybackStarted(timeoutMs: Long = 6000L): Boolean
}