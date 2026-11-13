package com.wanghoufan.fragmentplayer.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.model.Checkpoint
import com.wanghoufan.fragmentplayer.core.model.Course
import com.wanghoufan.fragmentplayer.core.model.MediaType
import com.wanghoufan.fragmentplayer.core.model.PlaybackUiState
import com.wanghoufan.fragmentplayer.core.model.Preset
import com.wanghoufan.fragmentplayer.core.model.TimerPhase
import com.wanghoufan.fragmentplayer.core.model.TimerUiState
import com.wanghoufan.fragmentplayer.core.timer.TimeFlowMath
import com.wanghoufan.fragmentplayer.ui.MainViewModel
import com.wanghoufan.fragmentplayer.ui.components.CoursePlayerView
import com.wanghoufan.fragmentplayer.ui.components.DeletePresetDialog
import com.wanghoufan.fragmentplayer.ui.components.FinishHistoryDialog
import com.wanghoufan.fragmentplayer.ui.components.LocalNavigateToCourses
import com.wanghoufan.fragmentplayer.ui.components.PresetEditorDialog
import kotlin.math.abs

private val screenPadding = 20.dp
private val speedOptions = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private const val FALLBACK_DURATION_SEC = 120

@Composable
fun TimerScreen(vm: MainViewModel = viewModel()) {
    val timer by vm.timer.collectAsStateWithLifecycle()
    val pendingFinish by vm.pendingFinish.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val courses by vm.courses.collectAsStateWithLifecycle()
    val checkpoint by vm.checkpoint.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()

    val navigateToCourses = LocalNavigateToCourses.current
    val haptics = LocalHapticFeedback.current

    var editorPreset by remember { mutableStateOf<Preset?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Preset?>(null) }
    // 启动失败时「仅倒计时」必须沿用用户刚点的那一个场景时长，
    // 否则会退回到列表第一个预设，用户选 3 分钟却按 2 分钟计时。
    var lastStartPreset by remember { mutableStateOf<Preset?>(null) }

    val fallbackName = stringResource(R.string.tui_default_preset_name)
    val fallbackPreset = remember(presets, fallbackName) {
        presets.firstOrNull() ?: Preset(
            id = 0L,
            name = fallbackName,
            durationSec = FALLBACK_DURATION_SEC
        )
    }

    val startWithFeedback: (Preset, Boolean) -> Unit = { preset, countdownOnly ->
        lastStartPreset = preset
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.start(preset, countdownOnly)
    }

    LaunchedEffect(timer.phase, timer.countdownOnly) {
        if (timer.phase == TimerPhase.RUNNING && !timer.countdownOnly) {
            vm.ensurePlaybackConnected()
        }
    }

    when (timer.phase) {
        TimerPhase.IDLE -> IdleSection(
            presets = presets,
            courses = courses,
            checkpoint = checkpoint,
            error = timer.error,
            fallbackPreset = fallbackPreset,
            onStart = startWithFeedback,
            onAdd = {
                editorPreset = null
                showEditor = true
            },
            onEdit = {
                editorPreset = it
                showEditor = true
            },
            onDelete = { pendingDelete = it },
            onGoImport = navigateToCourses,
            countdownPreset = lastStartPreset ?: fallbackPreset,
            onRetry = {
                val target = timer.presetId
                    ?.let { id -> presets.firstOrNull { it.id == id } }
                    ?: fallbackPreset
                startWithFeedback(target, false)
            }
        )

        TimerPhase.PREPARING -> PreparingSection(timer = timer, onCancel = vm::cancelPreparing)

        TimerPhase.RUNNING, TimerPhase.PAUSED -> ActiveSection(
            timer = timer,
            playback = playback,
            currentCourse = courses.firstOrNull { it.id == playback.currentCourseId },
            connected = connected,
            playerProvider = vm::player,
            onPauseTimer = vm::pauseTimer,
            onResumeTimer = vm::resumeTimer,
            onFinishRequest = vm::finishEarly,
            onToggleMedia = vm::toggleMediaPlayback,
            onSeek = vm::seekTo,
            onPrevious = vm::previousCourse,
            onNext = vm::nextCourse,
            onSpeed = vm::setSpeed
        )

        TimerPhase.FINISHED -> FinishedSection(
            timer = timer,
            onDismiss = vm::clearResult,
            onAgain = {
                val target = timer.presetId
                    ?.let { id -> presets.firstOrNull { it.id == id } }
                    ?: timer.result?.let { result ->
                        Preset(id = 0L, name = result.presetName, durationSec = result.targetSec)
                    }
                    ?: fallbackPreset
                startWithFeedback(target, false)
            }
        )
    }

    if (showEditor) {
        PresetEditorDialog(
            initial = editorPreset,
            onDismiss = {
                showEditor = false
                editorPreset = null
            },
            onSave = { name, durationSec ->
                val editing = editorPreset
                if (editing == null) {
                    vm.addPreset(name, durationSec)
                } else {
                    vm.updatePreset(editing.copy(name = name, durationSec = durationSec))
                }
                showEditor = false
                editorPreset = null
            }
        )
    }

    pendingDelete?.let { preset ->
        DeletePresetDialog(
            preset = preset,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                vm.deletePreset(preset.id)
                pendingDelete = null
            }
        )
    }

    if (pendingFinish != null) {
        FinishHistoryDialog(
            onKeep = { vm.commitFinish(saveHistory = true) },
            onDiscard = { vm.commitFinish(saveHistory = false) }
        )
    }
}

@Composable
private fun IdleSection(
    presets: List<Preset>,
    courses: List<Course>,
    checkpoint: Checkpoint?,
    error: String?,
    fallbackPreset: Preset,
    countdownPreset: Preset,
    onStart: (Preset, Boolean) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Preset) -> Unit,
    onDelete: (Preset) -> Unit,
    onGoImport: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = screenPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.tui_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.tui_hint_idle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (error != null) {
            StartErrorCard(
                message = error,
                onRetry = onRetry,
                onCountdownOnly = { onStart(countdownPreset, true) }
            )
        }

        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.tui_section_presets),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (presets.isEmpty()) {
            Text(
                text = stringResource(R.string.tui_preset_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        presets.forEach { preset ->
            PresetCard(
                preset = preset,
                onStart = { onStart(preset, false) },
                onEdit = { onEdit(preset) },
                onDelete = { onDelete(preset) }
            )
        }

        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.tui_action_add_preset))
        }

        CurrentCourseCard(
            courses = courses,
            checkpoint = checkpoint,
            onContinue = { onStart(fallbackPreset, false) },
            onCountdownOnly = { onStart(fallbackPreset, true) },
            onGoImport = onGoImport
        )
    }
}

@Composable
private fun StartErrorCard(
    message: String,
    onRetry: () -> Unit,
    onCountdownOnly: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.tui_error_start_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = stringResource(R.string.tui_error_start_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.tui_action_retry), maxLines = 1)
                }
                TextButton(
                    onClick = onCountdownOnly,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(stringResource(R.string.tui_action_countdown_only), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: Preset,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = clockText(preset.durationSec.toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Button(
                onClick = onStart,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.tui_action_start))
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.tui_action_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tui_action_edit)) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tui_action_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentCourseCard(
    courses: List<Course>,
    checkpoint: Checkpoint?,
    onContinue: () -> Unit,
    onCountdownOnly: () -> Unit,
    onGoImport: () -> Unit
) {
    val checkpointCourse = checkpoint?.let { point ->
        courses.firstOrNull { it.id == point.courseId }
    }
    val firstCourse = courses.firstOrNull()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.tui_current_course),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                checkpointCourse != null && checkpoint != null -> {
                    Text(
                        text = checkpointCourse.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.tui_checkpoint_percent,
                            progressPercent(checkpointCourse, checkpoint.positionMs)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FilledTonalButton(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.tui_action_continue_last), maxLines = 1)
                    }
                }

                firstCourse != null -> {
                    Text(
                        text = firstCourse.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    FilledTonalButton(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.tui_action_start_first), maxLines = 1)
                    }
                }

                else -> {
                    Text(
                        text = stringResource(R.string.tui_no_course),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.tui_no_course_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onGoImport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.tui_action_go_import), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onCountdownOnly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.tui_action_countdown_only), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreparingSection(timer: TimerUiState, onCancel: () -> Unit) {
    val targetMs = (timer.targetSec * 1000L).coerceAtLeast(1L)
    // 准备阶段也以「点击开始」为起点递减，和系统倒计时同步；
    // 播放器起播前的加载时间不再白白多算。
    val displayRemainingMs = TimeFlowMath.displayRemainingMs(targetMs, timer.remainingMs, timer.timeFlow)
    val remainingSec = (displayRemainingMs + 999L) / 1000L
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = clockText(remainingSec),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.tui_preparing),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.tui_action_cancel))
            }
        }
    }
}

@Composable
private fun ActiveSection(
    timer: TimerUiState,
    playback: PlaybackUiState,
    currentCourse: Course?,
    connected: Boolean,
    playerProvider: () -> Player?,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onFinishRequest: () -> Unit,
    onToggleMedia: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeed: (Float) -> Unit
) {
    val targetMs = (timer.targetSec * 1000L).coerceAtLeast(1L)
    // 引擎里是压缩后的墙钟剩余；界面按本轮倍率换回目标时长显示，与到点时刻同步。
    val displayRemainingMs = TimeFlowMath.displayRemainingMs(targetMs, timer.remainingMs, timer.timeFlow)
    val remainingSec = (displayRemainingMs + 999L) / 1000L
    val fraction = (displayRemainingMs.toFloat() / targetMs.toFloat()).coerceIn(0f, 1f)
    val isRunning = timer.phase == TimerPhase.RUNNING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = screenPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timer.presetName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (timer.timeFlow != TimeFlowMath.NORMAL) {
                // 非 1× 是测试开关：在计时页明确标出，避免被误当成「正常计时」，
                // 否则用户会看到 2 分钟 12 秒就结束，以为时间不准。
                Text(
                    text = stringResource(R.string.tui_label_time_flow, timer.timeFlow),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (timer.countdownOnly) {
                Text(
                    text = stringResource(R.string.tui_label_countdown_only),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = clockText(remainingSec),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )

        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )

        val courseLabel = currentCourse?.displayName
            ?: timer.courseTitle
            ?: playback.currentTitle
            ?: stringResource(R.string.tui_no_course_playing)

        Text(
            text = if (timer.countdownOnly) {
                stringResource(R.string.tui_label_countdown_only)
            } else {
                courseLabel
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = if (isRunning) onPauseTimer else onResumeTimer,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            Text(
                text = stringResource(
                    if (isRunning) R.string.tui_action_pause_timer
                    else R.string.tui_action_resume_timer
                ),
                style = MaterialTheme.typography.titleMedium
            )
        }

        OutlinedButton(
            onClick = onFinishRequest,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.tui_action_finish_early))
        }

        if (!timer.countdownOnly) {
            HorizontalDivider()
            if (currentCourse?.hasAudio == false) {
                NoAudioNotice()
            }
            MediaControls(
                playback = playback,
                currentCourse = currentCourse,
                connected = connected,
                playerProvider = playerProvider,
                onToggleMedia = onToggleMedia,
                onSeek = onSeek,
                onPrevious = onPrevious,
                onNext = onNext,
                onSpeed = onSpeed
            )
        }
    }
}

@Composable
private fun NoAudioNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = stringResource(R.string.tui_no_audio_notice),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun MediaControls(
    playback: PlaybackUiState,
    currentCourse: Course?,
    connected: Boolean,
    playerProvider: () -> Player?,
    onToggleMedia: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeed: (Float) -> Unit
) {
    Text(
        text = stringResource(R.string.tui_label_media),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onPrevious,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.tui_media_previous), maxLines = 1)
        }
        Button(
            onClick = onToggleMedia,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
        ) {
            Text(
                text = stringResource(
                    if (playback.isPlaying) R.string.tui_media_pause else R.string.tui_media_play
                ),
                maxLines = 1
            )
        }
        OutlinedButton(
            onClick = onNext,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.tui_media_next), maxLines = 1)
        }
    }

    Text(
        text = stringResource(R.string.tui_pause_playback_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )

    val durationMs = playback.durationMs.coerceAtLeast(0L)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val liveFraction = if (durationMs > 0L) {
        (playback.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shownFraction = if (dragging) dragFraction else liveFraction
    val shownPositionMs = if (dragging) {
        (dragFraction * durationMs).toLong()
    } else {
        playback.positionMs
    }

    Slider(
        value = shownFraction,
        onValueChange = {
            dragging = true
            dragFraction = it
        },
        onValueChangeFinished = {
            if (durationMs > 0L) {
                onSeek((dragFraction * durationMs).toLong())
            }
            dragging = false
        },
        enabled = durationMs > 0L,
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = clockText(shownPositionMs / 1000L),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = clockText(durationMs / 1000L),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Text(
        text = stringResource(R.string.tui_label_speed),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        speedOptions.forEach { speed ->
            FilterChip(
                selected = abs(playback.speed - speed) < 0.01f,
                onClick = { onSpeed(speed) },
                label = {
                    Text(stringResource(R.string.tui_speed_value, speedLabel(speed)))
                },
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
    }

    if (playback.error != null) {
        Text(
            text = stringResource(R.string.tui_playback_error_inline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (currentCourse?.type == MediaType.VIDEO) {
        var videoExpanded by rememberSaveable(currentCourse?.id) { mutableStateOf(false) }
        TextButton(
            onClick = { videoExpanded = !videoExpanded },
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(
                stringResource(
                    if (videoExpanded) R.string.tui_action_hide_video
                    else R.string.tui_action_show_video
                )
            )
        }
        if (videoExpanded) {
            CoursePlayerView(
                player = playerProvider(),
                connected = connected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
        }
    }
}

@Composable
private fun FinishedSection(
    timer: TimerUiState,
    onDismiss: () -> Unit,
    onAgain: () -> Unit
) {
    val result = timer.result
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = screenPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.tui_finished_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = result?.presetName ?: timer.presetName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (result != null) {
                    Text(
                        text = stringResource(
                            R.string.tui_finished_minutes,
                            minutesOf(result.listenedSec)
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(
                            if (result.completed) R.string.tui_result_completed
                            else R.string.tui_result_early
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tui_action_got_it),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                OutlinedButton(
                    onClick = onAgain,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.tui_action_again))
                }
            }
        }
    }
}

@Composable
private fun clockText(totalSec: Long): String {
    val safe = totalSec.coerceAtLeast(0L)
    return stringResource(
        R.string.tui_clock,
        (safe / 60L).toInt(),
        (safe % 60L).toInt()
    )
}

private fun speedLabel(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()

private fun progressPercent(course: Course, positionMs: Long): Int {
    val duration = course.durationMs.coerceAtLeast(1L)
    return ((positionMs.toFloat() / duration.toFloat()) * 100f)
        .toInt()
        .coerceIn(0, 100)
}

private fun minutesOf(listenedSec: Long): Int =
    ((listenedSec.coerceAtLeast(0L) + 59L) / 60L).toInt().coerceAtLeast(1)