package com.wanghoufan.fragmentplayer.timer

import android.content.Context
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.wanghoufan.fragmentplayer.core.model.AppSettings
import com.wanghoufan.fragmentplayer.core.model.Checkpoint
import com.wanghoufan.fragmentplayer.core.model.Course
import com.wanghoufan.fragmentplayer.core.model.CourseStatus
import com.wanghoufan.fragmentplayer.core.model.LanguageMode
import com.wanghoufan.fragmentplayer.core.model.MediaType
import com.wanghoufan.fragmentplayer.core.model.PlaybackUiState
import com.wanghoufan.fragmentplayer.core.model.Preset
import com.wanghoufan.fragmentplayer.core.model.ThemeMode
import com.wanghoufan.fragmentplayer.core.model.TimerPhase
import com.wanghoufan.fragmentplayer.core.model.TimerSessionRecord
import com.wanghoufan.fragmentplayer.core.playback.PlaybackEngineApi
import com.wanghoufan.fragmentplayer.core.repo.CheckpointRepository
import com.wanghoufan.fragmentplayer.core.repo.CourseRepository
import com.wanghoufan.fragmentplayer.core.repo.PresetRepository
import com.wanghoufan.fragmentplayer.core.repo.SessionRepository
import com.wanghoufan.fragmentplayer.core.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 倍速 ≠ 1 的轮次只做测试：提醒照常，但历史与进度都不落盘；1× 仍按原口径正常写。
 *
 * 提醒本身（通知/铃声/震动）要真机才有通知渠道资源，单测里先占位 alertPosted 让
 * [TimerNotifications.postAlert] 跳过系统通知；铃声与震动的判定由 AlertPolicyTest 覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimeFlowHistoryTest {

    private lateinit var context: Context
    private lateinit var checkpoints: FakeCheckpoints
    private lateinit var sessions: FakeSessions
    private lateinit var playback: FakePlayback
    private lateinit var settings: FakeSettings

    private val preset = Preset(id = 3L, name = "刷牙", durationSec = 120)
    private val startCheckpoint = Checkpoint(courseId = 7L, positionMs = 5_000L, updatedAt = 0L)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        checkpoints = FakeCheckpoints()
        sessions = FakeSessions()
        playback = FakePlayback()
        settings = FakeSettings()
    }

    private fun controller(): TimerController = TimerController(
        context = context,
        presets = FakePresets(preset),
        courses = FakeCourses(),
        checkpoints = checkpoints,
        sessions = sessions,
        settings = settings,
        playback = playback
    )

    @Test
    fun nonNormalFlowAlarmKeepsNoHistoryAndNoProgress() = runBlocking {
        settings.timeFlow = 6
        checkpoints.current = startCheckpoint
        val controller = controller()
        controller.start(preset, countdownOnly = false)
        val sessionId = controller.state.value.sessionId
        assertNotNull(sessionId)
        val pauseCallsAfterStart = playback.pauseCalls
        claimAlertForTest()

        controller.handleAlarm(sessionId!!)

        assertEquals(TimerPhase.FINISHED, controller.state.value.phase)
        assertTrue(sessions.records.isEmpty())
        assertEquals(0, playback.saveCheckpointCalls)
        assertEquals(pauseCallsAfterStart, playback.pauseCalls)
        assertTrue(playback.pauseWithoutCheckpointCalls > 0)
        // 本轮走过的位置不落盘：断点回到开始前那一处。
        assertEquals(7L to 5_000L, checkpoints.saves.last())
        assertEquals(5_000L, checkpoints.current?.positionMs)
    }

    @Test
    fun nonNormalFlowManualFinishIgnoresKeepHistoryChoice() = runBlocking {
        settings.timeFlow = 2
        checkpoints.current = startCheckpoint
        val controller = controller()
        controller.start(preset, countdownOnly = false)

        controller.finishEarly()
        // 用户在弹窗里选了「记入历史」，倍速 ≠ 1 时同样不写。
        controller.commitPendingFinish(saveHistory = true)

        assertEquals(TimerPhase.IDLE, controller.state.value.phase)
        assertTrue(sessions.records.isEmpty())
        assertEquals(0, playback.saveCheckpointCalls)
        assertEquals(7L to 5_000L, checkpoints.saves.last())
        assertFalse(checkpoints.cleared)
    }

    @Test
    fun nonNormalFlowCountdownOnlyWritesNothingAndKeepsCheckpoint() = runBlocking {
        settings.timeFlow = 10
        checkpoints.current = startCheckpoint
        val controller = controller()
        controller.start(preset, countdownOnly = true)
        val sessionId = controller.state.value.sessionId
        assertNotNull(sessionId)
        claimAlertForTest()

        controller.handleAlarm(sessionId!!)

        assertTrue(sessions.records.isEmpty())
        assertEquals(0, playback.saveCheckpointCalls)
        // 纯倒计时没有播放，断点既不清空也不改写。
        assertTrue(checkpoints.saves.isEmpty())
        assertFalse(checkpoints.cleared)
        assertEquals(5_000L, checkpoints.current?.positionMs)
    }

    @Test
    fun nonNormalFlowPauseDoesNotWriteCheckpoint() = runBlocking {
        settings.timeFlow = 10
        val controller = controller()
        controller.start(preset, countdownOnly = false)
        val pauseCallsAfterStart = playback.pauseCalls

        controller.pause()

        assertEquals(TimerPhase.PAUSED, controller.state.value.phase)
        assertEquals(pauseCallsAfterStart, playback.pauseCalls)
        assertEquals(1, playback.pauseWithoutCheckpointCalls)
        assertEquals(0, playback.saveCheckpointCalls)
    }

    @Test
    fun normalFlowAlarmStillWritesHistoryAndProgress() = runBlocking {
        settings.timeFlow = 1
        checkpoints.current = startCheckpoint
        val controller = controller()
        controller.start(preset, countdownOnly = false)
        val sessionId = controller.state.value.sessionId
        assertNotNull(sessionId)
        claimAlertForTest()

        controller.handleAlarm(sessionId!!)

        assertEquals(TimerPhase.FINISHED, controller.state.value.phase)
        assertEquals(1, sessions.records.size)
        assertTrue(sessions.records.first().completed)
        assertEquals(1, playback.saveCheckpointCalls)
        assertTrue(playback.pauseCalls > 0)
    }

    /** 单测没有通知渠道资源：先占位 alertPosted，让到点提醒只走收尾逻辑。 */
    private fun claimAlertForTest() {
        assertTrue(TimerNotifications.claimAlert(context))
    }

    private class FakePresets(private val preset: Preset) : PresetRepository {
        override val presets = MutableStateFlow(listOf(preset))
        override suspend fun ensureDefaults() = Unit
        override suspend fun all(): List<Preset> = listOf(preset)
        override suspend fun get(id: Long): Preset? = preset
        override suspend fun add(name: String, durationSec: Int): Long = 0L
        override suspend fun update(preset: Preset) = Unit
        override suspend fun delete(id: Long) = Unit
    }

    private class FakeCourses : CourseRepository {
        private val course = Course(
            id = 7L,
            uri = "content://course/7",
            displayName = "第1节.mp3",
            relativePath = "第1节.mp3",
            durationMs = 300_000L,
            type = MediaType.AUDIO,
            hasAudio = true,
            sizeBytes = 1_024L,
            lastModified = 0L,
            fingerprint = "fp",
            status = CourseStatus.READY
        )
        override val courses = MutableStateFlow(listOf(course))
        override suspend fun all(): List<Course> = listOf(course)
        override suspend fun get(id: Long): Course? = course
        override suspend fun insert(course: Course): Long = course.id
        override suspend fun update(course: Course) = Unit
        override suspend fun updateAll(courses: List<Course>) = Unit
        override suspend fun delete(ids: List<Long>) = Unit
        override suspend fun count(): Int = 1
    }

    private class FakeCheckpoints : CheckpointRepository {
        var current: Checkpoint? = null
        val saves = mutableListOf<Pair<Long, Long>>()
        var cleared = false
        override val checkpoint = MutableStateFlow<Checkpoint?>(null)
        override suspend fun get(): Checkpoint? = current
        override suspend fun save(courseId: Long, positionMs: Long) {
            saves += courseId to positionMs
            current = Checkpoint(courseId, positionMs, updatedAt = 0L)
        }

        override suspend fun clear() {
            cleared = true
            current = null
        }
    }

    private class FakeSessions : SessionRepository {
        val records = mutableListOf<TimerSessionRecord>()
        override suspend fun record(record: TimerSessionRecord): Long {
            records += record
            return records.size.toLong()
        }

        override suspend fun recent(limit: Int): List<TimerSessionRecord> = records.toList()
    }

    private class FakeSettings : SettingsRepository {
        var timeFlow = 1
        override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())
        override val folderUri: Flow<String?> = MutableStateFlow(null)
        override suspend fun current(): AppSettings = AppSettings(timeFlow = timeFlow)
        override suspend fun setTheme(mode: ThemeMode) = Unit
        override suspend fun setLanguage(mode: LanguageMode) = Unit
        override suspend fun setFontScale(scale: Float) = Unit
        override suspend fun setVibration(enabled: Boolean) = Unit
        override suspend fun setAlertSound(enabled: Boolean) = Unit
        override suspend fun setTimeFlow(multiplier: Int) = Unit
        override suspend fun setFolderUri(uri: String?) = Unit
        override suspend fun currentFolderUri(): String? = null
    }

    private class FakePlayback : PlaybackEngineApi {
        var pauseCalls = 0
        var pauseWithoutCheckpointCalls = 0
        var saveCheckpointCalls = 0

        override val state: StateFlow<PlaybackUiState> = MutableStateFlow(PlaybackUiState())
        override val queue: StateFlow<List<Course>> = MutableStateFlow(emptyList())
        override val connected: StateFlow<Boolean> = MutableStateFlow(true)

        override fun connect() = Unit
        override fun player(): Player? = null
        override suspend fun playCourse(course: Course, positionMs: Long): Result<Unit> =
            Result.success(Unit)

        override suspend fun playQueue(
            courses: List<Course>,
            startIndex: Int,
            positionMs: Long
        ): Result<Unit> = Result.success(Unit)

        override fun play() = Unit

        override fun pause() {
            pauseCalls++
        }

        override fun pauseWithoutCheckpoint() {
            pauseWithoutCheckpointCalls++
        }

        override fun seekTo(positionMs: Long) = Unit
        override fun next() = Unit
        override fun previous() = Unit
        override fun setSpeed(speed: Float) = Unit

        override suspend fun saveCheckpointNow() {
            saveCheckpointCalls++
        }

        override suspend fun waitForPlaybackStarted(timeoutMs: Long): Boolean = true
    }
}
