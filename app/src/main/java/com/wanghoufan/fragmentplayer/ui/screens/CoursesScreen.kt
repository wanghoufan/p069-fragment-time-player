package com.wanghoufan.fragmentplayer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.model.Checkpoint
import com.wanghoufan.fragmentplayer.core.model.Course
import com.wanghoufan.fragmentplayer.core.model.CourseStatus
import com.wanghoufan.fragmentplayer.ui.components.SectionCard
import com.wanghoufan.fragmentplayer.ui.components.SnackbarMessageCollector
import com.wanghoufan.fragmentplayer.ui.components.StatusTag
import com.wanghoufan.fragmentplayer.ui.components.folderDisplayLabel
import com.wanghoufan.fragmentplayer.ui.components.formatDurationMs

@Composable
fun CoursesScreen() {
    val viewModel: CoursesSettingsViewModel = viewModel()
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val checkpoint by viewModel.checkpoint.collectAsStateWithLifecycle()
    val folderUri by viewModel.folderUri.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val progress by viewModel.scanProgress.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    SnackbarMessageCollector(snackbarHostState, viewModel.messages)

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val raw = uri.toString()
            if (folderUri.isNullOrBlank()) viewModel.importFolder(raw) else viewModel.relink(raw)
        }
    }
    val pickFolder: () -> Unit = {
        if (!scanning) folderPicker.launch(null)
    }

    var startPointTarget by remember { mutableStateOf<Course?>(null) }
    var reasonTarget by remember { mutableStateOf<Course?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                CoursesHeader(
                    courseCount = courses.size,
                    folderLabel = folderDisplayLabel(folderUri),
                    hasFolder = !folderUri.isNullOrBlank(),
                    scanning = scanning,
                    onRescan = { if (!scanning) viewModel.rescan() },
                    onPickFolder = pickFolder
                )
            }
            if (scanning) {
                item {
                    ScanProgressCard(
                        scanned = progress.scanned,
                        total = progress.total,
                        fileName = progress.fileName
                    )
                }
            }
            if (!scanning && courses.isEmpty()) {
                item { CoursesEmptyCard(onPickFolder = pickFolder) }
            }
            items(courses, key = { it.id }) { course ->
                CourseCard(
                    course = course,
                    checkpoint = checkpoint,
                    onClick = {
                        if (course.status == CourseStatus.READY) {
                            if (!course.hasAudio) viewModel.notifyNoAudioCourse()
                            viewModel.playCourse(course, false)
                        } else {
                            reasonTarget = course
                        }
                    },
                    onPlayFromStart = {
                        if (course.status == CourseStatus.READY) {
                            if (!course.hasAudio) viewModel.notifyNoAudioCourse()
                            viewModel.playCourse(course, true)
                        } else {
                            reasonTarget = course
                        }
                    },
                    onSetStartPoint = { startPointTarget = course },
                    onRelink = pickFolder,
                    onViewReason = { reasonTarget = course }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    startPointTarget?.let { course ->
        StartPointDialog(
            course = course,
            checkpoint = checkpoint,
            onDismiss = { startPointTarget = null },
            onConfirm = { positionMs ->
                viewModel.setStartPoint(course.id, positionMs)
                startPointTarget = null
            }
        )
    }

    reasonTarget?.let { course ->
        ReasonDialog(course = course, onDismiss = { reasonTarget = null })
    }
}

@Composable
private fun CoursesHeader(
    courseCount: Int,
    folderLabel: String,
    hasFolder: Boolean,
    scanning: Boolean,
    onRescan: () -> Unit,
    onPickFolder: () -> Unit
) {
    SectionCard(title = stringResource(R.string.title_courses)) {
        Text(
            text = stringResource(R.string.mcu_course_count, courseCount),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = folderLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onRescan,
            enabled = hasFolder && !scanning,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.mcu_action_rescan))
        }
        Button(
            onClick = onPickFolder,
            enabled = !scanning,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(
                text = stringResource(
                    if (hasFolder) R.string.mcu_action_relink else R.string.mcu_action_pick_folder
                )
            )
        }
    }
}

@Composable
private fun ScanProgressCard(scanned: Int, total: Int, fileName: String) {
    SectionCard(title = stringResource(R.string.mcu_scanning)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = if (fileName.isNotBlank()) {
                stringResource(R.string.mcu_scan_progress, scanned, total, fileName)
            } else {
                stringResource(R.string.mcu_scan_progress_no_name, scanned, total)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CoursesEmptyCard(onPickFolder: () -> Unit) {
    SectionCard(title = stringResource(R.string.mcu_empty_title)) {
        Text(
            text = stringResource(R.string.mcu_empty_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onPickFolder,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.mcu_empty_action))
        }
    }
}

@Composable
private fun CourseCard(
    course: Course,
    checkpoint: Checkpoint?,
    onClick: () -> Unit,
    onPlayFromStart: () -> Unit,
    onSetStartPoint: () -> Unit,
    onRelink: () -> Unit,
    onViewReason: () -> Unit
) {
    val last = checkpoint?.takeIf { it.courseId == course.id }
    val percent = if (last != null && last.positionMs > 0L && course.durationMs > 0L) {
        ((last.positionMs * 100) / course.durationMs).toInt().coerceIn(0, 100)
    } else {
        null
    }
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (course.sortIndex + 1).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = course.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (course.durationMs > 0L) {
                            formatDurationMs(course.durationMs)
                        } else {
                            stringResource(R.string.mcu_duration_unknown)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    when {
                        course.status == CourseStatus.MISSING ->
                            StatusTag(stringResource(R.string.mcu_status_missing))
                        course.status == CourseStatus.UNSUPPORTED ->
                            StatusTag(stringResource(R.string.mcu_status_unsupported))
                        !course.hasAudio -> StatusTag(
                            text = stringResource(R.string.mcu_status_no_audio),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        else -> Unit
                    }
                }
                if (percent != null) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                    Text(
                        text = stringResource(R.string.mcu_checkpoint_percent, percent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (course.status != CourseStatus.READY) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = onRelink,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.mcu_action_relink_short),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        TextButton(
                            onClick = onViewReason,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.mcu_action_view_reason),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.mcu_action_more)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mcu_action_play_from_start)) },
                        onClick = {
                            menuOpen = false
                            onPlayFromStart()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mcu_action_set_start)) },
                        onClick = {
                            menuOpen = false
                            onSetStartPoint()
                        }
                    )
                    if (course.status != CourseStatus.READY ||
                        course.note != null ||
                        !course.hasAudio
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mcu_action_view_reason)) },
                            onClick = {
                                menuOpen = false
                                onViewReason()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartPointDialog(
    course: Course,
    checkpoint: Checkpoint?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val durationMs = course.durationMs.coerceAtLeast(0L)
    val initialMs = checkpoint
        ?.takeIf { it.courseId == course.id }
        ?.positionMs
        ?.coerceIn(0L, durationMs)
        ?: 0L
    var value by remember(course.id) { mutableFloatStateOf(initialMs.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mcu_start_point_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.mcu_start_point_value,
                        formatDurationMs(value.toLong()),
                        formatDurationMs(durationMs)
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    enabled = durationMs > 0L
                )
                Text(
                    text = stringResource(R.string.mcu_start_point_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.toLong()) },
                enabled = durationMs > 0L
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ReasonDialog(course: Course, onDismiss: () -> Unit) {
    val note = course.note?.takeIf { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mcu_reason_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = note ?: stringResource(R.string.mcu_reason_unknown),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        course.status == CourseStatus.MISSING ->
                            stringResource(R.string.mcu_reason_missing_hint)
                        course.status == CourseStatus.UNSUPPORTED ->
                            stringResource(R.string.mcu_reason_unsupported_hint)
                        !course.hasAudio -> stringResource(R.string.mcu_reason_no_audio_hint)
                        else -> stringResource(R.string.mcu_reason_unknown)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_confirm))
            }
        }
    )
}