package com.wanghoufan.fragmentplayer.timer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.model.AppSettings
import com.wanghoufan.fragmentplayer.core.model.Checkpoint
import com.wanghoufan.fragmentplayer.core.model.Course
import com.wanghoufan.fragmentplayer.core.model.CourseStatus
import com.wanghoufan.fragmentplayer.core.model.Preset
import com.wanghoufan.fragmentplayer.core.model.TimerPhase
import com.wanghoufan.fragmentplayer.core.model.TimerResult
import com.wanghoufan.fragmentplayer.core.model.TimerSessionRecord
import com.wanghoufan.fragmentplayer.core.model.TimerUiState
import com.wanghoufan.fragmentplayer.core.playback.PlaybackEngineApi
import com.wanghoufan.fragmentplayer.core.repo.CheckpointRepository
import com.wanghoufan.fragmentplayer.core.repo.CourseRepository
import com.wanghoufan.fragmentplayer.core.repo.PresetRepository
import com.wanghoufan.fragmentplayer.core.repo.SessionRepository
import com.wanghoufan.fragmentplayer.core.repo.SettingsRepository
import com.wanghoufan.fragmentplayer.core.timer.TimeFlowMath
import com.wanghoufan.fragmentplayer.core.timer.TimerControllerApi
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 计时控制器：倒计时用 TimerEngine 的单调时钟，闹钟/通知只做提醒。
 * 媒体暂停与计时暂停严格分开，见 pause()。
 */
class TimerController(
    context: Context,
    private val presets: PresetRepository,
    private val courses: CourseRepository,
    private val checkpoints: CheckpointRepository,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val playback: PlaybackEngineApi
) : TimerControllerApi {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = TimerEngine()
    private val completionLock = AtomicBoolean(false)

    private val _state = MutableStateFlow(TimerUiState())
    override val state: StateFlow<TimerUiState> = _state.asStateFlow()

    private var tickerJob: Job? = null
    private var currentSessionId: String? = null
    private var deadlineWallMs = 0L
    private var startedWallMs = 0L
    private var alerted = false
    /** 本轮「时光流速」倍率；只在新一轮开始时读取，运行中不中途变速。 */
    private var currentFlow = TimeFlowMath.NORMAL
    /** 本轮开始前的断点，用于「结束但不记入」时把进度回滚回去。 */
    private var initialCheckpoint: Checkpoint? = null
    private var pendingFinishRecord: PendingFinish? = null

    private val _interruptedNotice = MutableStateFlow<String?>(null)
    override val interruptedNotice: StateFlow<String?> = _interruptedNotice.asStateFlow()

    private val _pendingFinish = MutableStateFlow<TimerResult?>(null)
    override val pendingFinish: StateFlow<TimerResult?> = _pendingFinish.asStateFlow()

    init {
        // 先读出上次进程留下的提示，再让 bootstrap 决定是否覆盖；两者都在构造期同步完成，
        // 界面订阅到的初始值就是最终值，不存在“先 null 后变非空”的首帧竞态。
        _interruptedNotice.value = TimerSessionStore.readNotice(appContext)
        bootstrapFromStorage()
    }

    override suspend fun start(preset: Preset, countdownOnly: Boolean) {
        cancelActiveSession()
        val sessionId = UUID.randomUUID().toString()
        val nowWall = System.currentTimeMillis()
        val initialSec = preset.durationSec.coerceAtLeast(1)
        // 倍率只在这一刻读取：正在配置的上一轮已结束，新开一轮才换速度。
        val flow = runCatching { settings.current().timeFlow }
            .getOrDefault(TimeFlowMath.NORMAL)
            .let(TimeFlowMath::sanitize)

        currentSessionId = sessionId
        currentFlow = flow
        startedWallMs = nowWall
        deadlineWallMs = 0L
        alerted = false
        initialCheckpoint = null
        TimerSessionStore.clearSession(appContext)
        TimerNotifications.resetAlertPosted(appContext)

        _state.value = TimerUiState(
            phase = TimerPhase.PREPARING,
            sessionId = sessionId,
            presetId = preset.id,
            presetName = preset.name,
            targetSec = initialSec,
            remainingMs = TimeFlowMath.realTargetMs(initialSec * 1000L, flow),
            countdownOnly = countdownOnly,
            timeFlow = flow
        )
        persist()

        try {
            val latest = runCatching { presets.get(preset.id) }.getOrNull() ?: preset
            val targetSec = latest.durationSec.coerceAtLeast(1)
            if (!isPreparing(sessionId)) {
                abandonStartIfOwned(sessionId)
                return
            }
            val targetMs = targetSec * 1000L
            deadlineWallMs = TimeFlowMath.deadlineAt(startedWallMs, targetMs, flow)
            _state.update {
                it.copy(
                    presetId = latest.id,
                    presetName = latest.name,
                    targetSec = targetSec,
                    remainingMs = TimeFlowMath.realTargetMs(targetMs, flow)
                )
            }
            persist()

            if (countdownOnly) {
                beginRunningIfPreparing(sessionId, targetSec)
                return
            }

            val playableCourses = runCatching { courses.all() }
                .getOrDefault(emptyList())
                .filter { it.status == CourseStatus.READY }
            if (!isPreparing(sessionId)) {
                abandonStartIfOwned(sessionId)
                return
            }
            if (playableCourses.isEmpty()) {
                failStart(appContext.getString(R.string.timer_error_no_courses))
                return
            }

            val checkpoint = runCatching { checkpoints.get() }.getOrNull()
            initialCheckpoint = checkpoint
            val startIndex = checkpoint
                ?.let { point -> playableCourses.indexOfFirst { it.id == point.courseId } }
                ?.takeIf { it >= 0 }
                ?: 0
            val course = playableCourses[startIndex]
            val startPosition = safePosition(
                course,
                checkpoint?.takeIf { it.courseId == course.id }?.positionMs ?: 0L
            )

            val result = try {
                playback.playQueue(playableCourses, startIndex, startPosition)
            } catch (cancelled: CancellationException) {
                // 协程取消必须继续向上抛，不能被当成“播放失败”吞掉。
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            if (!isPreparing(sessionId)) {
                abandonStartIfOwned(sessionId)
                return
            }
            if (result.isFailure) {
                failStart(
                    result.exceptionOrNull()?.message
                        ?: appContext.getString(R.string.timer_error_playback_failed)
                )
                return
            }

            if (!isPreparing(sessionId)) {
                abandonStartIfOwned(sessionId)
                return
            }
            _state.update {
                it.copy(courseTitle = playback.state.value.currentTitle ?: course.relativePath)
            }
            beginRunningIfPreparing(sessionId, targetSec)
        } catch (cancelled: CancellationException) {
            // 启动被取消：仍属于本次会话时收尾回 IDLE，且不写会话记录。
            if (currentSessionId == sessionId) {
                withContext(NonCancellable) { cancelPreparingSession() }
            }
            throw cancelled
        }
    }

    override fun cancelStart() {
        if (_state.value.phase != TimerPhase.PREPARING) return
        cancelPreparingSession()
    }

    override suspend fun pause() {
        if (_state.value.phase != TimerPhase.RUNNING) return
        // 已到点占位：收尾正在进行，暂停不得打断收尾（否则会话不会落库）。
        if (TimerNotifications.isAlertPosted(appContext)) return
        tickerJob?.cancel()
        tickerJob = null
        engine.pause()
        deadlineWallMs = 0L
        _state.update { it.copy(phase = TimerPhase.PAUSED, remainingMs = engine.remainingMs()) }
        persist()
        pausePlayback(saveCheckpoint = currentFlow == TimeFlowMath.NORMAL)
        TimerNotifications.cancelAlarm(appContext)
        stopTimerService()
    }

    override suspend fun resume() {
        if (_state.value.phase != TimerPhase.PAUSED) return
        engine.resume()
        val remaining = engine.remainingMs()
        deadlineWallMs = System.currentTimeMillis() + remaining
        _state.update { it.copy(phase = TimerPhase.RUNNING, remainingMs = remaining) }
        persist()
        if (!_state.value.countdownOnly) {
            runCatching { playback.play() }
        }
        val sessionId = currentSessionId ?: return
        TimerNotifications.scheduleAlarm(appContext, sessionId, remaining)
        startTimerService()
        restartTicker()
    }

    /**
     * 手动结束：立刻停止播放并按实际用时结算，但这步不落盘。
     * 结算结果放进 pendingFinish，界面据此只问一次「是否记入历史」。
     */
    override suspend fun finishEarly() {
        val phase = _state.value.phase
        if (phase != TimerPhase.RUNNING && phase != TimerPhase.PAUSED) return
        if (pendingFinishRecord != null) return
        if (!completionLock.compareAndSet(false, true)) return
        try {
            withContext(NonCancellable) { settleManualFinish() }
        } finally {
            completionLock.set(false)
        }
    }

    override suspend fun commitPendingFinish(saveHistory: Boolean) {
        val record = pendingFinishRecord ?: return
        if (!completionLock.compareAndSet(false, true)) return
        pendingFinishRecord = null
        // 倍速 ≠ 1 只做演示：无论用户选「记入」还是「不记入」，都不写历史、不动进度。
        val keepsHistory = saveHistory && record.timeFlow == TimeFlowMath.NORMAL
        try {
            withContext(NonCancellable) {
                if (keepsHistory) {
                    // 与原先的手动结束一致：先把断点补写到最新位置，再写会话记录。
                    runCatching { playback.saveCheckpointNow() }
                    runCatching { sessions.record(record.toSessionRecord()) }
                } else if (!record.countdownOnly) {
                    // 不记入（含倍速 ≠ 1）：把本轮开始前的断点原样写回，本轮听力时长不落盘。
                    restoreInitialCheckpoint()
                }
                resetToIdle()
            }
        } finally {
            completionLock.set(false)
        }
    }

    override suspend fun clearResult() {
        if (_state.value.phase != TimerPhase.FINISHED) return
        resetToIdle()
    }

    private fun settleManualFinish() {
        val snapshot = TimerSessionStore.read(appContext)
        val targetSec = snapshot.targetSec.takeIf { it > 0 } ?: _state.value.targetSec
        if (targetSec <= 0) {
            resetToIdle()
            return
        }
        val presetName = snapshot.presetName.ifBlank { _state.value.presetName }
        val completed = TimerNotifications.isAlertPosted(appContext)
        val listenedSec = if (completed) {
            targetSec.toLong()
        } else {
            elapsedSeconds(snapshot, targetSec)
        }

        tickerJob?.cancel()
        tickerJob = null
        // 立即停播放；这一步刻意不写断点，是否落盘交给用户接下来的选择。
        runCatching { playback.pauseWithoutCheckpoint() }
        val endedAt = System.currentTimeMillis()
        val startedAt = snapshot.startedWall.takeIf { it > 0L }
            ?: startedWallMs.takeIf { it > 0L }
            ?: (endedAt - listenedSec * 1000L)

        pendingFinishRecord = PendingFinish(
            presetId = snapshot.presetId ?: _state.value.presetId,
            presetName = presetName,
            targetSec = targetSec,
            listenedSec = listenedSec,
            completed = completed,
            countdownOnly = snapshot.countdownOnly,
            startedAt = startedAt,
            endedAt = endedAt,
            timeFlow = TimeFlowMath.sanitize(snapshot.timeFlow)
        )

        engine.finish()
        deadlineWallMs = 0L
        TimerNotifications.cancelAlarm(appContext)
        if (!completed) TimerNotifications.cancelAlert(appContext)
        stopTimerService()
        // 清掉进行中的快照：此刻进程即使被杀，重启也只回到 IDLE，不会偷偷补记一轮。
        TimerSessionStore.clearSession(appContext)
        _pendingFinish.value = TimerResult(
            presetName = presetName,
            targetSec = targetSec,
            listenedSec = listenedSec,
            completed = completed
        )
    }

    private suspend fun restoreInitialCheckpoint() {
        val snapshot = initialCheckpoint
        runCatching {
            if (snapshot == null) {
                checkpoints.clear()
            } else {
                checkpoints.save(snapshot.courseId, snapshot.positionMs)
            }
        }
    }

    private fun resetToIdle() {
        pendingFinishRecord = null
        _pendingFinish.value = null
        initialCheckpoint = null
        TimerSessionStore.clearSession(appContext)
        TimerNotifications.resetAlertPosted(appContext)
        TimerNotifications.cancelAlert(appContext)
        currentSessionId = null
        deadlineWallMs = 0L
        alerted = false
        engine.finish()
        _state.value = TimerUiState()
    }

    override suspend fun handleAlarm(sessionId: String) {
        val snapshot = TimerSessionStore.read(appContext)
        if (snapshot.sessionId == null || snapshot.sessionId != sessionId) return
        if (snapshot.phase != TimerPhase.RUNNING && snapshot.phase != TimerPhase.PAUSED) {
            TimerNotifications.cancelAlarm(appContext)
            return
        }
        finishInternal(completed = true)
    }

    override fun peekInterruptedNotice(): String? =
        _interruptedNotice.value ?: TimerSessionStore.readNotice(appContext)

    override fun consumeInterruptedNotice() {
        _interruptedNotice.value = null
        TimerSessionStore.clearNotice(appContext)
    }

    /**
     * 代际令牌校验：会话 id 与状态都必须仍属于本次启动，且仍停在 PREPARING。
     * 只要新 start 或 cancelStart 改变了会话，旧协程的后续挂起点一律不得继续。
     */
    private fun isPreparing(sessionId: String): Boolean {
        val snapshot = _state.value
        return currentSessionId == sessionId &&
            snapshot.sessionId == sessionId &&
            snapshot.phase == TimerPhase.PREPARING
    }

    /** 旧启动协程发现会话已不属于自己时，只回收自己，绝不触碰新会话。 */
    private fun abandonStartIfOwned(sessionId: String) {
        if (currentSessionId != sessionId) return
        cancelPreparingSession()
    }

    private fun beginRunningIfPreparing(sessionId: String, targetSec: Int) {
        if (!isPreparing(sessionId)) {
            abandonStartIfOwned(sessionId)
            return
        }
        beginRunning(targetSec)
    }

    private fun beginRunning(targetSec: Int) {
        val sessionId = currentSessionId ?: return
        val nowWall = System.currentTimeMillis()
        val realTargetMs = TimeFlowMath.realTargetMs(targetSec * 1000L, currentFlow)
        engine.start(sessionId, realTargetMs, startedWallMs)
        deadlineWallMs = nowWall + realTargetMs
        alerted = false
        _state.update {
            it.copy(
                phase = TimerPhase.RUNNING,
                remainingMs = realTargetMs,
                error = null,
                timeFlow = currentFlow
            )
        }
        persist()
        startTimerService()
        TimerNotifications.scheduleAlarm(appContext, sessionId, engine.remainingMs())
        restartTicker()
    }

    private fun cancelPreparingSession() {
        tickerJob?.cancel()
        tickerJob = null
        pausePlayback(saveCheckpoint = currentFlow == TimeFlowMath.NORMAL)
        TimerNotifications.cancelAlarm(appContext)
        TimerNotifications.cancelAlert(appContext)
        stopTimerService()
        TimerSessionStore.clearSession(appContext)
        currentSessionId = null
        deadlineWallMs = 0L
        engine.finish()
        _state.value = TimerUiState()
    }

    private fun failStart(message: String) {
        tickerJob?.cancel()
        tickerJob = null
        pausePlayback(saveCheckpoint = currentFlow == TimeFlowMath.NORMAL)
        TimerNotifications.cancelAlarm(appContext)
        TimerNotifications.cancelAlert(appContext)
        stopTimerService()
        TimerSessionStore.clearSession(appContext)
        currentSessionId = null
        deadlineWallMs = 0L
        engine.finish()
        _state.update {
            it.copy(
                phase = TimerPhase.IDLE,
                sessionId = null,
                remainingMs = 0L,
                error = message
            )
        }
    }

    private fun restartTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var lastPersistedSec = -1L
            while (isActive) {
                if (_state.value.phase != TimerPhase.RUNNING) break
                val remaining = engine.remainingMs()
                _state.update {
                    it.copy(
                        remainingMs = remaining,
                        actualMediaMs = playback.state.value.positionMs,
                        courseTitle = playback.state.value.currentTitle ?: it.courseTitle
                    )
                }
                val remainingSec = remaining / 1000L
                if (remainingSec != lastPersistedSec) {
                    lastPersistedSec = remainingSec
                    persist()
                }
                if (remaining <= 0L) {
                    // ticker 自身正在收尾，先摘掉引用，避免 finishInternal 取消当前协程。
                    tickerJob = null
                    finishInternal(completed = true)
                    break
                }
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private suspend fun finishInternal(completed: Boolean) {
        if (!completionLock.compareAndSet(false, true)) return
        try {
            // 到点收尾（记会话、改状态、取消闹钟、持久化）必须不可取消：
            // 用户恰在到点瞬间点暂停时，也只能等这次收尾完整落盘。
            withContext(NonCancellable) { completeSession(completed) }
        } finally {
            completionLock.set(false)
        }
    }

    private suspend fun completeSession(completed: Boolean) {
        val snapshot = TimerSessionStore.read(appContext)
        val targetSec = snapshot.targetSec.takeIf { it > 0 } ?: _state.value.targetSec
        if (targetSec <= 0) {
            tickerJob?.cancel()
            tickerJob = null
            _state.value = TimerUiState()
            return
        }
        val presetName = snapshot.presetName.ifBlank { _state.value.presetName }
        val listenedSec = if (completed) {
            targetSec.toLong()
        } else {
            elapsedSeconds(snapshot, targetSec)
        }
        // 倍速 ≠ 1 只做演示：照常提醒，但不写历史、不推进断点。
        val keepsHistory = TimeFlowMath.sanitize(snapshot.timeFlow) == TimeFlowMath.NORMAL

        tickerJob?.cancel()
        tickerJob = null
        pausePlayback(saveCheckpoint = keepsHistory)
        if (keepsHistory) runCatching { playback.saveCheckpointNow() }
        val endedAt = System.currentTimeMillis()
        val startedAt = snapshot.startedWall.takeIf { it > 0L }
            ?: startedWallMs.takeIf { it > 0L }
            ?: (endedAt - listenedSec * 1000L)
        if (keepsHistory) {
            runCatching {
                sessions.record(
                    TimerSessionRecord(
                        presetId = snapshot.presetId ?: _state.value.presetId,
                        presetName = presetName,
                        targetSec = targetSec,
                        listenedSec = listenedSec,
                        completed = completed,
                        startedAt = startedAt,
                        endedAt = endedAt
                    )
                )
            }
        } else if (!snapshot.countdownOnly) {
            // 非 1×：把本轮开始前的断点写回，本轮走过的位置不落盘。
            restoreInitialCheckpoint()
        }

        if (completed) {
            postAlertOnce(presetName, targetSec)
        } else {
            TimerNotifications.cancelAlert(appContext)
        }
        alerted = completed
        engine.finish()
        deadlineWallMs = 0L
        TimerNotifications.cancelAlarm(appContext)
        stopTimerService()
        _state.update {
            it.copy(
                phase = TimerPhase.FINISHED,
                remainingMs = 0L,
                error = null,
                result = TimerResult(
                    presetName = presetName,
                    targetSec = targetSec,
                    listenedSec = listenedSec,
                    completed = completed
                )
            )
        }
        persist()
    }

    private suspend fun postAlertOnce(
        presetName: String,
        targetSec: Int
    ) {
        // ticker 与闹钟可能几乎同时到点，claimAlert 是唯一提醒入口。
        if (!TimerNotifications.claimAlert(appContext)) return
        val currentSettings = runCatching { settings.current() }.getOrDefault(AppSettings())
        TimerNotifications.postAlert(
            context = appContext,
            presetName = presetName,
            targetSec = targetSec,
            sound = currentSettings.alertSound,
            vibration = currentSettings.vibration
        )
    }

    private fun elapsedSeconds(snapshot: StoredTimerSession, targetSec: Int): Long {
        val targetMs = targetSec * 1000L
        val flow = TimeFlowMath.sanitize(snapshot.timeFlow)
        val remaining = if (engine.state != TimerState.IDLE) {
            engine.remainingMs()
        } else {
            when (snapshot.phase) {
                TimerPhase.PAUSED -> snapshot.remainingMs
                TimerPhase.RUNNING ->
                    (snapshot.deadlineWall - System.currentTimeMillis()).coerceAtLeast(0L)
                else -> 0L
            }
        }
        // 引擎里是压缩后的墙钟时间，回算成目标时长口径再取整到秒。
        return (TimeFlowMath.virtualElapsedMs(targetMs, remaining, flow) / 1000L)
            .coerceIn(0L, targetSec.toLong())
    }

    private fun persist() {
        val uiState = _state.value
        val sessionId = currentSessionId ?: uiState.sessionId ?: return
        TimerSessionStore.save(
            appContext,
            StoredTimerSession(
                sessionId = sessionId,
                presetId = uiState.presetId,
                presetName = uiState.presetName,
                targetSec = uiState.targetSec,
                deadlineWall = deadlineWallMs,
                remainingMs = uiState.remainingMs,
                phase = uiState.phase,
                countdownOnly = uiState.countdownOnly,
                alerted = alerted,
                alertPosted = TimerSessionStore.isAlertPosted(appContext),
                startedWall = startedWallMs,
                timeFlow = currentFlow
            )
        )
    }

    private fun cancelActiveSession() {
        tickerJob?.cancel()
        tickerJob = null
        TimerNotifications.cancelAlarm(appContext)
        TimerNotifications.cancelAlert(appContext)
        pausePlayback(saveCheckpoint = currentFlow == TimeFlowMath.NORMAL)
        stopTimerService()
        engine.finish()
    }

    /** 倍速 ≠ 1 的轮次只做演示：停播放时不写断点，本轮位置不会推进进度。 */
    private fun pausePlayback(saveCheckpoint: Boolean) {
        if (saveCheckpoint) {
            runCatching { playback.pause() }
        } else {
            runCatching { playback.pauseWithoutCheckpoint() }
        }
    }

    private fun startTimerService() {
        runCatching {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, TimerService::class.java).setAction(TimerService.ACTION_SYNC)
            )
        }
    }

    private fun stopTimerService() {
        runCatching { appContext.stopService(Intent(appContext, TimerService::class.java)) }
    }

    private fun safePosition(course: Course, positionMs: Long): Long {
        val duration = course.durationMs
        if (duration <= 0L) return positionMs.coerceAtLeast(0L)
        val position = positionMs.coerceIn(0L, (duration - 1L).coerceAtLeast(0L))
        return if (position >= duration - REWIND_GUARD_MS) 0L else position
    }

    private fun bootstrapFromStorage() {
        val snapshot = TimerSessionStore.read(appContext)
        currentFlow = TimeFlowMath.sanitize(snapshot.timeFlow)
        when (snapshot.phase) {
            TimerPhase.IDLE -> return
            TimerPhase.FINISHED -> {
                TimerSessionStore.clearSession(appContext)
                return
            }
            TimerPhase.PREPARING -> {
                TimerSessionStore.clearSession(appContext)
                TimerNotifications.cancelAlarm(appContext)
                stopTimerService()
                val notice = appContext.getString(R.string.timer_interrupted_preparing)
                _interruptedNotice.value = notice
                TimerSessionStore.writeNotice(appContext, notice)
                return
            }
            TimerPhase.RUNNING, TimerPhase.PAUSED -> {
                val remaining = if (snapshot.phase == TimerPhase.PAUSED) {
                    snapshot.remainingMs
                } else {
                    (snapshot.deadlineWall - System.currentTimeMillis()).coerceAtLeast(0L)
                }
                if (snapshot.alertPosted) {
                    TimerSessionStore.clearSession(appContext)
                    TimerNotifications.cancelAlarm(appContext)
                    return
                }
                if (snapshot.phase == TimerPhase.RUNNING && remaining <= RECOVERY_GRACE_MS) {
                    // 到点附近（含闹钟略早触发）说明上次被系统中断时其实已到点：
                    // 交给统一的到点收尾，并保证只提醒一次。
                    val sessionId = snapshot.sessionId
                    if (sessionId != null) {
                        _state.value = TimerUiState(
                            phase = TimerPhase.PREPARING,
                            sessionId = sessionId,
                            presetId = snapshot.presetId,
                            presetName = snapshot.presetName,
                            targetSec = snapshot.targetSec,
                            remainingMs = 0L,
                            countdownOnly = snapshot.countdownOnly,
                            timeFlow = currentFlow
                        )
                        scope.launch {
                            delay(RECOVERY_ALARM_DELAY_MS)
                            runCatching { handleAlarm(sessionId) }
                        }
                    }
                    return
                }
                // 被系统强制停止/重启：只说明真实经过时间，不假装媒体仍在播放。
                val elapsedSec = TimeFlowMath.virtualElapsedMs(
                    snapshot.targetSec * 1000L,
                    remaining,
                    currentFlow
                ) / 1000L
                TimerSessionStore.clearSession(appContext)
                TimerNotifications.cancelAlarm(appContext)
                stopTimerService()
                val notice = appContext.getString(
                    R.string.timer_interrupted_notice,
                    TimerNotifications.formatClock(elapsedSec)
                )
                _interruptedNotice.value = notice
                TimerSessionStore.writeNotice(appContext, notice)
            }
        }
    }

    private data class PendingFinish(
        val presetId: Long?,
        val presetName: String,
        val targetSec: Int,
        val listenedSec: Long,
        val completed: Boolean,
        val countdownOnly: Boolean,
        val startedAt: Long,
        val endedAt: Long,
        /** 本轮的时光流速倍率：≠ 1 时结算只展示，不写历史。 */
        val timeFlow: Int
    ) {
        fun toSessionRecord() = TimerSessionRecord(
            presetId = presetId,
            presetName = presetName,
            targetSec = targetSec,
            listenedSec = listenedSec,
            completed = completed,
            startedAt = startedAt,
            endedAt = endedAt
        )
    }

    companion object {
        private const val TICK_INTERVAL_MS = 250L
        private const val REWIND_GUARD_MS = 1000L
        private const val RECOVERY_ALARM_DELAY_MS = 1200L
        private const val RECOVERY_GRACE_MS = 5000L
    }
}