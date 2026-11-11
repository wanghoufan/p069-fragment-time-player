package com.wanghoufan.fragmentplayer.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.model.Course
import com.wanghoufan.fragmentplayer.core.model.CourseStatus
import com.wanghoufan.fragmentplayer.core.model.MediaType
import com.wanghoufan.fragmentplayer.core.model.PlaybackUiState
import com.wanghoufan.fragmentplayer.core.playback.PlaybackEngineApi
import com.wanghoufan.fragmentplayer.core.repo.CheckpointRepository
import com.wanghoufan.fragmentplayer.core.repo.CourseRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 播放引擎只负责媒体与断点，不接触倒计时；媒体暂停与计时暂停是两个独立动作。
 */
class PlaybackEngine(
    context: Context,
    private val courses: CourseRepository,
    private val checkpoints: CheckpointRepository
) : PlaybackEngineApi {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackUiState())
    override val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<Course>>(emptyList())
    override val queue: StateFlow<List<Course>> = _queue.asStateFlow()

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val connectLock = Any()
    private var connectRequested = false
    private var controller: MediaController? = null
    private var connectionWaiter: CompletableDeferred<MediaController?>? = null
    private var startSignal: CompletableDeferred<Result<Unit>>? = null
    private var tickerJob: Job? = null
    private var lastCheckpointWall = 0L
    private var reconnectAttempts = 0

    override fun connect() {
        synchronized(connectLock) {
            if (connectRequested) return
            connectRequested = true
        }
        mainHandler.post { openController() }
    }

    private fun openController() {
        if (controller != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token)
            .setListener(controllerListener)
            .setApplicationLooper(Looper.getMainLooper())
            .buildAsync()
        future.addListener({
            val connectedController = runCatching { future.get() }.getOrNull()
            if (connectedController == null) {
                synchronized(connectLock) { connectRequested = false }
                completeConnection(null)
            } else {
                controller = connectedController
                connectedController.addListener(playerListener)
                _connected.value = true
                reconnectAttempts = 0
                updateFromPlayer()
                completeConnection(connectedController)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    /**
     * 锁屏期间控制器可能被系统回收，断连后自动重连一次到两次即可恢复控制；
     * 重连只重建控制器，不主动改变播放状态，避免把用户暂停变成自动播放。
     */
    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
        reconnectAttempts++
        Log.i(TAG, "scheduling controller reconnect attempt=$reconnectAttempts")
        mainHandler.postDelayed({
            if (controller == null) openController()
        }, RECONNECT_DELAY_MS)
    }

    private fun completeConnection(connectedController: MediaController?) {
        val waiter = synchronized(this) {
            connectionWaiter.also { connectionWaiter = null }
        }
        waiter?.complete(connectedController)
        if (connectedController != null) startTicker()
    }

    private suspend fun awaitController(): MediaController? {
        controller?.let { return it }
        connect()
        val waiter = synchronized(this) {
            connectionWaiter ?: CompletableDeferred<MediaController?>().also { connectionWaiter = it }
        }
        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) { waiter.await() }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(mediaController: MediaController) {
            // 断连只代表控制器失联，不代表媒体已暂停：这里刻意不调用 pause()，
            // 服务里的播放器仍在放，贸然暂停会把锁屏播放停掉。
            Log.i(TAG, "controller disconnected, media left untouched")
            controller = null
            _connected.value = false
            _state.update { it.copy(isPlaying = false) }
            synchronized(connectLock) { connectRequested = false }
            scheduleReconnect()
        }

        override fun onError(mediaController: MediaController, sessionError: SessionError) {
            controller = null
            _connected.value = false
            _state.update {
                it.copy(isPlaying = false, error = appContext.getString(R.string.playback_error_not_ready))
            }
            startSignal?.complete(
                Result.failure(PlaybackFailure(appContext.getString(R.string.playback_error_not_ready)))
            )
            synchronized(connectLock) { connectRequested = false }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            Log.i(TAG, "isPlaying=$isPlaying")
            if (isPlaying) {
                _state.update { it.copy(error = null) }
                startSignal?.complete(Result.success(Unit))
            }
        }

        /**
         * 锁屏后媒体被系统改状态时，这里是唯一能区分「用户想暂停」与「系统抑制/抢焦点」的地方。
         * 只记录不干预：干预会破坏用户主动暂停与按耳机线控的语义。
         */
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            Log.i(TAG, "playWhenReady=$playWhenReady reason=$reason")
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateFromPlayer()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.i(TAG, "playbackState=$playbackState")
            updateFromPlayer()
            if (playbackState == Player.STATE_ENDED) {
                scope.launch(Dispatchers.Main.immediate) { saveCurrentCheckpoint() }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                saveCheckpointForItem(oldPosition.mediaItem, oldPosition.positionMs)
            } else {
                saveCheckpointForItem(newPosition.mediaItem, newPosition.positionMs)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackError(error)
        }
    }

    override suspend fun playCourse(course: Course, positionMs: Long): Result<Unit> =
        playQueue(listOf(course), 0, positionMs)

    override suspend fun playQueue(
        courses: List<Course>,
        startIndex: Int,
        positionMs: Long
    ): Result<Unit> {
        if (courses.isEmpty()) {
            return Result.failure(PlaybackFailure(appContext.getString(R.string.playback_no_course)))
        }
        connect()
        val mediaController = awaitController()
            ?: return Result.failure(PlaybackFailure(appContext.getString(R.string.playback_error_not_ready)))

        saveCurrentCheckpoint()

        return withContext(Dispatchers.Main.immediate) {
            val index = startIndex.coerceIn(0, courses.lastIndex)
            val startPosition = positionMs.coerceAtLeast(0L)
            val items = courses.map { course ->
                MediaItem.Builder()
                    .setMediaId(course.id.toString())
                    .setUri(course.uri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(course.relativePath).build())
                    .build()
            }
            val signal = CompletableDeferred<Result<Unit>>()
            startSignal = signal
            _queue.value = courses
            _state.update {
                it.copy(
                    currentCourseId = courses[index].id,
                    currentTitle = courses[index].relativePath,
                    positionMs = startPosition,
                    durationMs = courses[index].durationMs,
                    hasVideo = courses[index].type == MediaType.VIDEO,
                    isPlaying = false,
                    error = null
                )
            }
            mediaController.pause()
            mediaController.setMediaItems(items, index, startPosition)
            mediaController.prepare()
            mediaController.play()
            if (mediaController.isPlaying) {
                signal.complete(Result.success(Unit))
            }
            val result = withTimeoutOrNull(START_TIMEOUT_MS) { signal.await() }
                ?: Result.failure(PlaybackFailure(appContext.getString(R.string.playback_error_timeout)))
            if (startSignal === signal) startSignal = null
            if (result.isFailure) {
                mediaController.pause()
                _state.update { it.copy(isPlaying = false, error = result.exceptionOrNull()?.message) }
            }
            result
        }
    }

    override fun play() {
        onMain {
            controller?.play()
            updateFromPlayer()
        }
    }

    override fun pause() {
        pauseInternal(saveCheckpoint = true, reason = "timer-or-user")
    }

    override fun pauseWithoutCheckpoint() {
        pauseInternal(saveCheckpoint = false, reason = "finish-without-history")
    }

    private fun pauseInternal(saveCheckpoint: Boolean, reason: String) {
        // 暂停来源写进日志：锁屏后若出现 AUDIO_FOCUS_LOSS / SUPPRESSED_TOO_LONG，
        // 就能确认不是应用自己暂停的。
        Log.i(TAG, "pause requested reason=$reason saveCheckpoint=$saveCheckpoint")
        onMain {
            controller?.pause()
            updateFromPlayer()
        }
        if (saveCheckpoint) {
            scope.launch(Dispatchers.Main.immediate) { saveCurrentCheckpoint() }
        }
    }

    override fun seekTo(positionMs: Long) {
        onMain {
            controller?.seekTo(positionMs.coerceAtLeast(0L))
            updateFromPlayer()
        }
    }

    override fun next() {
        onMain { controller?.seekToNextMediaItem() }
    }

    override fun previous() {
        onMain { controller?.seekToPreviousMediaItem() }
    }

    override fun setSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 4f)
        onMain { controller?.setPlaybackSpeed(safeSpeed) }
        _state.update { it.copy(speed = safeSpeed) }
    }

    override suspend fun saveCheckpointNow() {
        saveCurrentCheckpoint()
    }

    override suspend fun waitForPlaybackStarted(timeoutMs: Long): Boolean {
        if (controller?.isPlaying == true) return true
        val signal = startSignal ?: return false
        return withTimeoutOrNull(timeoutMs) { signal.await() }?.isSuccess == true
    }

    override fun player(): Player? = controller

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    private fun updateFromPlayer() {
        val mediaController = controller ?: return
        val course = courseFor(mediaController.currentMediaItem?.mediaId)
        val duration = mediaController.duration.takeIf { it > 0L } ?: course?.durationMs ?: 0L
        _state.update {
            it.copy(
                currentCourseId = course?.id ?: mediaController.currentMediaItem?.mediaId?.toLongOrNull(),
                currentTitle = course?.relativePath ?: mediaController.currentMediaItem?.mediaMetadata?.title?.toString(),
                positionMs = mediaController.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                isPlaying = mediaController.isPlaying,
                hasVideo = course?.type == MediaType.VIDEO,
                speed = mediaController.playbackParameters.speed
            )
        }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                val mediaController = controller ?: continue
                if (!mediaController.isPlaying) continue
                val position = mediaController.currentPosition.coerceAtLeast(0L)
                _state.update { it.copy(positionMs = position) }
                val now = System.currentTimeMillis()
                if (now - lastCheckpointWall >= CHECKPOINT_INTERVAL_MS) {
                    lastCheckpointWall = now
                    saveCurrentCheckpoint()
                }
            }
        }
    }

    private suspend fun saveCurrentCheckpoint() {
        val target = withContext(Dispatchers.Main.immediate) {
            val mediaController = controller ?: return@withContext null
            val courseId = mediaController.currentMediaItem?.mediaId?.toLongOrNull()
                ?: return@withContext null
            courseId to mediaController.currentPosition.coerceAtLeast(0L)
        } ?: return
        runCatching {
            withContext(Dispatchers.IO) { checkpoints.save(target.first, target.second) }
        }
    }

    private fun saveCheckpointForItem(mediaItem: MediaItem?, positionMs: Long) {
        // 用 mediaId 而不是队列下标：切课/换队列时下标会指向新列表的另一节课。
        val courseId = mediaItem?.mediaId?.toLongOrNull() ?: return
        val position = positionMs.coerceAtLeast(0L)
        scope.launch(Dispatchers.IO) {
            runCatching { checkpoints.save(courseId, position) }
        }
    }

    private fun courseFor(mediaId: String?): Course? {
        if (mediaId == null) return null
        return _queue.value.firstOrNull { it.id.toString() == mediaId }
    }

    private fun handlePlaybackError(error: PlaybackException) {
        Log.e(TAG, "playback error code=${error.errorCode} name=${error.errorCodeName}", error)
        val message = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                appContext.getString(R.string.playback_error_missing)

            else -> appContext.getString(R.string.playback_error_unplayable)
        }
        val courseId = courseFor(controller?.currentMediaItem?.mediaId)?.id
            ?: controller?.currentMediaItem?.mediaId?.toLongOrNull()
        _state.update { it.copy(isPlaying = false, error = message) }
        startSignal?.complete(Result.failure(PlaybackFailure(message)))
        if (courseId == null) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val course = courses.get(courseId) ?: return@launch
                if (course.status == CourseStatus.UNSUPPORTED) return@launch
                courses.update(
                    course.copy(
                        status = CourseStatus.UNSUPPORTED,
                        note = appContext.getString(R.string.playback_unsupported_note)
                    )
                )
            }
        }
    }

    private class PlaybackFailure(message: String) : Exception(message)

    companion object {
        private const val TAG = "PlaybackEngine"
        private const val CONNECT_TIMEOUT_MS = 6000L
        private const val START_TIMEOUT_MS = 6000L
        private const val TICK_INTERVAL_MS = 500L
        private const val CHECKPOINT_INTERVAL_MS = 4000L
        private const val RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_ATTEMPTS = 2
    }
}