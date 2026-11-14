package com.wanghoufan.fragmentplayer.timer

import android.content.Context
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.wanghoufan.fragmentplayer.core.model.AlertTone
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
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 倒计时的起算点必须是用户点「开始」的那一刻：
 * 播放准备（选片/缓冲/起播）耗时计入本轮，避免到点比系统倒计时更晚。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimerControllerStartTimingTest {

    private val fakeNow = AtomicLong(1_000_000L)
    private val preset = Preset(id = 3L, name = "刷牙", durationSec = 120)

    private lateinit var controller: TimerController

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        controller = TimerController(
            context = context,
            presets = FakePresets(preset),
            courses = FakeCourses(),
            checkpoints = FakeCheckpoints(),
            sessions = FakeSessions(),
            settings = FakeSettings(),
            playback = FakePlayback(onPlayQueue = { fakeNow.addAndGet(5_000L) }),
            nowWall = { fakeNow.get() }
        )
    }

    @Test
    fun preparingTimeIsIncludedInCountdown() = runBlocking {
        controller.start(preset, countdownOnly = false)

        assertEquals(TimerPhase.RUNNING, controller.state.value.phase)
        val remaining = controller.state.value.remainingMs
        assertTrue("准备 5 秒后剩余应约 115 秒，实际 $remaining", remaining in 114_500L..115_000L)
        assertEquals(115_000L, remaining)

        controller.pause()
    }

    @Test
    fun countdownOnlyStartHasNoPreparingDelay() = runBlocking {
        controller.start(preset, countdownOnly = true)

        assertEquals(TimerPhase.RUNNING, controller.state.value.phase)
        assertEquals(120_000L, controller.state.value.remainingMs)

        controller.pause()
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
        override val checkpoint = MutableStateFlow<Checkpoint?>(null)
        override suspend fun get(): Checkpoint? = null
        override suspend fun save(courseId: Long, positionMs: Long) = Unit
        override suspend fun clear() = Unit
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
        override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())
        override val folderUri: Flow<String?> = MutableStateFlow(null)
        override suspend fun current(): AppSettings = AppSettings()
        override suspend fun setTheme(mode: ThemeMode) = true
        override suspend fun setLanguage(mode: LanguageMode) = true
        override suspend fun setFontScale(scale: Float) = true
        override suspend fun setVibration(enabled: Boolean) = true
        override suspend fun setAlertSound(enabled: Boolean) = true
        override suspend fun setAlertTone(tone: AlertTone) = true
        override suspend fun setTimeFlow(multiplier: Int) = true
        override suspend fun setFolderUri(uri: String?) = true
        override suspend fun currentFolderUri(): String? = null
    }

    private class FakePlayback(
        private val onPlayQueue: () -> Unit = {}
    ) : PlaybackEngineApi {
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
        ): Result<Unit> {
            onPlayQueue()
            return Result.success(Unit)
        }

        override fun play() = Unit
        override fun pause() = Unit
        override fun pauseWithoutCheckpoint() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun next() = Unit
        override fun previous() = Unit
        override fun setSpeed(speed: Float) = Unit
        override suspend fun saveCheckpointNow() = Unit
        override suspend fun waitForPlaybackStarted(timeoutMs: Long): Boolean = true
    }
}