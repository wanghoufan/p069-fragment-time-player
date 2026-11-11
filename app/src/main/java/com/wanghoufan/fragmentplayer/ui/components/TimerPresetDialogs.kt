package com.wanghoufan.fragmentplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.model.Preset

private const val DEFAULT_DURATION_SEC = 120
private const val MIN_DURATION_SEC = 5
private const val MAX_DURATION_SEC = 60 * 60

@Composable
fun PresetEditorDialog(
    initial: Preset?,
    onDismiss: () -> Unit,
    onSave: (name: String, durationSec: Int) -> Unit
) {
    val startingDuration = initial?.durationSec ?: DEFAULT_DURATION_SEC
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var minutes by remember(initial) { mutableStateOf((startingDuration / 60).toString()) }
    var seconds by remember(initial) { mutableStateOf((startingDuration % 60).toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val invalidName = stringResource(R.string.tui_preset_invalid_name)
    val invalidDuration = stringResource(R.string.tui_preset_invalid_duration)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.tui_preset_new_title else R.string.tui_preset_edit_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorText = null
                    },
                    label = { Text(stringResource(R.string.tui_field_name)) },
                    placeholder = { Text(stringResource(R.string.tui_field_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        value = minutes,
                        onValueChange = {
                            minutes = it
                            errorText = null
                        },
                        label = stringResource(R.string.tui_field_minutes),
                        max = 60,
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        value = seconds,
                        onValueChange = {
                            seconds = it
                            errorText = null
                        },
                        label = stringResource(R.string.tui_field_seconds),
                        max = 59,
                        modifier = Modifier.weight(1f)
                    )
                }
                errorText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedName = name.trim()
                    val totalSec = (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
                    when {
                        trimmedName.isEmpty() -> errorText = invalidName
                        totalSec < MIN_DURATION_SEC || totalSec > MAX_DURATION_SEC ->
                            errorText = invalidDuration
                        else -> onSave(trimmedName, totalSec)
                    }
                }
            ) {
                Text(stringResource(R.string.tui_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tui_action_cancel))
            }
        }
    )
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    max: Int,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(2)
            val number = digits.toIntOrNull()
            if (digits.isEmpty() || (number != null && number <= max)) {
                onValueChange(digits)
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
fun DeletePresetDialog(
    preset: Preset,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tui_delete_preset_title)) },
        text = { Text(stringResource(R.string.tui_delete_preset_message, preset.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.tui_action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tui_action_cancel))
            }
        }
    )
}

@Composable
fun FinishHistoryDialog(
    onKeep: () -> Unit,
    onDiscard: () -> Unit
) {
    // 这一轮已经结束、只有这一个选择，必须选了才回首页，取消/返回键不做任何事。
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.tui_finish_history_title)) },
        confirmButton = {
            TextButton(onClick = onKeep) {
                Text(stringResource(R.string.tui_action_keep_history))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.tui_action_discard_history))
            }
        }
    )
}