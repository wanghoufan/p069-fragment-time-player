package com.wanghoufan.fragmentplayer.ui.screens

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wanghoufan.fragmentplayer.BuildConfig
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.model.AppSettings
import com.wanghoufan.fragmentplayer.core.model.LanguageMode
import com.wanghoufan.fragmentplayer.core.model.ThemeMode
import com.wanghoufan.fragmentplayer.core.repo.BackupSummary
import com.wanghoufan.fragmentplayer.ui.components.ChoiceRow
import com.wanghoufan.fragmentplayer.ui.components.SectionCard
import com.wanghoufan.fragmentplayer.ui.components.SettingRow
import com.wanghoufan.fragmentplayer.ui.components.SnackbarMessageCollector
import com.wanghoufan.fragmentplayer.ui.components.folderDisplayName
import com.wanghoufan.fragmentplayer.ui.labelRes
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private data class FontChoice(val scale: Float, @StringRes val labelRes: Int)

private val FONT_CHOICES = listOf(
    FontChoice(1.0f, R.string.seu_font_small),
    FontChoice(1.15f, R.string.seu_font_standard),
    FontChoice(1.3f, R.string.seu_font_large),
    FontChoice(1.5f, R.string.seu_font_xlarge)
)

private val BACKUP_MIME_TYPES = arrayOf(
    "application/json",
    "text/plain",
    "application/octet-stream"
)

private const val TEST_VIBRATION_MS = 200L
private const val TEST_SOUND_DURATION_MS = 4_000L
private const val FONT_SCALE_EPSILON = 0.01f

@Composable
fun SettingsScreen() {
    val viewModel: CoursesSettingsViewModel = viewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val folderUri by viewModel.folderUri.collectAsStateWithLifecycle()
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    SnackbarMessageCollector(snackbarHostState, viewModel.messages)

    val handler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(Unit) {
        onDispose { handler.removeCallbacksAndMessages(null) }
    }

    var notificationGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var exactAlarmAllowed by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    var pendingImportUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSummary by remember { mutableStateOf<BackupSummary?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = hasNotificationPermission(context)
                exactAlarmAllowed = canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationGranted = granted
        if (!granted) {
            val activity = context.findActivity()
            val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (!canAskAgain) {
                openAppDetailsSettings(context)
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.seu_notification_hint)) }
            } else {
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.seu_notification_hint)) }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportTo(uri.toString())
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = uri.toString()
            viewModel.peek(raw) { summary ->
                if (summary != null) {
                    pendingImportUri = raw
                    pendingSummary = summary
                }
            }
        }
    }

    fun testVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            val legacyVibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            legacyVibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.seu_test_vibration_unavailable)) }
            return
        }
        val played = runCatching {
            vibrator.vibrate(
                VibrationEffect.createOneShot(TEST_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }.isSuccess
        if (!played) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.seu_test_vibration_unavailable)) }
        }
    }

    fun testAlertSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = uri?.let { runCatching { RingtoneManager.getRingtone(context, it) }.getOrNull() }
        if (ringtone == null) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.seu_test_sound_unavailable)) }
            return
        }
        val played = runCatching {
            ringtone.play()
            handler.postDelayed({ runCatching { ringtone.stop() } }, TEST_SOUND_DURATION_MS)
        }.isSuccess
        if (!played) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.seu_test_sound_failed)) }
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasNotificationPermission(context)) {
            notificationGranted = true
            return
        }
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.seu_exact_alarm_unavailable)) }
        } catch (_: SecurityException) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.seu_exact_alarm_degraded)) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.title_settings),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            PlaybackAlertSection(
                settings = settings,
                onVibrationChange = viewModel::setVibration,
                onAlertSoundChange = viewModel::setAlertSound,
                onTestVibration = { testVibration() },
                onTestSound = { testAlertSound() }
            )
            PermissionsSection(
                notificationGranted = notificationGranted,
                exactAlarmAllowed = exactAlarmAllowed,
                onOpenNotification = { requestNotificationPermission() },
                onOpenExactAlarm = { requestExactAlarmPermission() }
            )
            CourseSourceSection(
                folderUri = folderUri,
                courseCount = courses.size,
                scanning = scanning,
                onPickFolder = pickFolder,
                onRescan = { if (!scanning) viewModel.rescan() }
            )
            AppearanceSection(
                settings = settings,
                onThemeChange = viewModel::setTheme,
                onFontScaleChange = viewModel::setFontScale
            )
            LanguageSection(
                language = settings.language,
                onLanguageChange = viewModel::setLanguage
            )
            BackupSection(
                onExport = { exportLauncher.launch(backupFileName()) },
                onImport = { importLauncher.launch(BACKUP_MIME_TYPES) }
            )
            AboutSection()
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    pendingSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = {
                pendingSummary = null
                pendingImportUri = null
            },
            title = { Text(stringResource(R.string.seu_import_confirm_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.seu_import_confirm_message,
                        summary.presets,
                        summary.courses
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingImportUri
                        pendingSummary = null
                        pendingImportUri = null
                        if (uri != null) viewModel.importFrom(uri)
                    }
                ) {
                    Text(stringResource(R.string.seu_import_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingSummary = null
                        pendingImportUri = null
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun PlaybackAlertSection(
    settings: AppSettings,
    onVibrationChange: (Boolean) -> Unit,
    onAlertSoundChange: (Boolean) -> Unit,
    onTestVibration: () -> Unit,
    onTestSound: () -> Unit
) {
    SectionCard(title = stringResource(R.string.seu_section_playback)) {
        SettingRow(
            label = stringResource(R.string.seu_label_vibration),
            trailing = {
                Switch(checked = settings.vibration, onCheckedChange = onVibrationChange)
            }
        )
        SettingRow(
            label = stringResource(R.string.seu_label_alert_sound),
            trailing = {
                Switch(checked = settings.alertSound, onCheckedChange = onAlertSoundChange)
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onTestVibration,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.seu_action_test_vibration))
            }
            OutlinedButton(
                onClick = onTestSound,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.seu_action_test_sound))
            }
        }
    }
}

@Composable
private fun PermissionsSection(
    notificationGranted: Boolean,
    exactAlarmAllowed: Boolean,
    onOpenNotification: () -> Unit,
    onOpenExactAlarm: () -> Unit
) {
    SectionCard(title = stringResource(R.string.seu_section_permissions)) {
        PermissionRow(
            label = stringResource(R.string.seu_label_notification),
            granted = notificationGranted,
            grantedLabel = stringResource(R.string.seu_notification_on),
            missingLabel = stringResource(R.string.seu_notification_off),
            actionLabel = stringResource(R.string.seu_action_open_notification),
            onAction = onOpenNotification
        )
        if (!notificationGranted) {
            HintText(stringResource(R.string.seu_notification_hint))
        }
        PermissionRow(
            label = stringResource(R.string.seu_label_exact_alarm),
            granted = exactAlarmAllowed,
            grantedLabel = stringResource(R.string.seu_exact_alarm_on),
            missingLabel = stringResource(R.string.seu_exact_alarm_off),
            actionLabel = stringResource(R.string.seu_action_open_exact_alarm),
            onAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) onOpenExactAlarm else null
        )
        if (!exactAlarmAllowed) {
            HintText(stringResource(R.string.seu_exact_alarm_hint))
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    grantedLabel: String,
    missingLabel: String,
    actionLabel: String,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (granted) grantedLabel else missingLabel,
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        if (!granted && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun CourseSourceSection(
    folderUri: String?,
    courseCount: Int,
    scanning: Boolean,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit
) {
    val hasFolder = !folderUri.isNullOrBlank()
    val folderName = folderDisplayName(folderUri)

    SectionCard(title = stringResource(R.string.seu_section_courses)) {
        SettingRow(
            label = stringResource(R.string.seu_label_folder),
            description = folderName
                ?: stringResource(if (hasFolder) R.string.mcu_folder_selected else R.string.seu_folder_none)
        )
        HintText(stringResource(R.string.seu_course_count, courseCount))
        OutlinedButton(
            onClick = onRescan,
            enabled = hasFolder && !scanning,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.seu_action_rescan))
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
                    if (hasFolder) R.string.seu_action_relink else R.string.seu_action_pick_folder
                )
            )
        }
    }
}

@Composable
private fun AppearanceSection(
    settings: AppSettings,
    onThemeChange: (ThemeMode) -> Unit,
    onFontScaleChange: (Float) -> Unit
) {
    SectionCard(title = stringResource(R.string.seu_section_appearance)) {
        Text(
            text = stringResource(R.string.seu_label_theme),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ThemeMode.entries.forEach { mode ->
            ChoiceRow(
                label = themeLabelText(mode),
                selected = settings.theme == mode,
                onClick = { onThemeChange(mode) }
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.seu_label_font_scale),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FONT_CHOICES.forEach { choice ->
            ChoiceRow(
                label = stringResource(choice.labelRes),
                selected = abs(settings.fontScale - choice.scale) < FONT_SCALE_EPSILON,
                onClick = { onFontScaleChange(choice.scale) }
            )
        }
    }
}

@Composable
private fun LanguageSection(
    language: LanguageMode,
    onLanguageChange: (LanguageMode) -> Unit
) {
    SectionCard(title = stringResource(R.string.seu_section_language)) {
        LanguageMode.entries.forEach { mode ->
            ChoiceRow(
                label = stringResource(mode.labelRes()),
                selected = language == mode,
                onClick = { onLanguageChange(mode) }
            )
        }
    }
}

@Composable
private fun BackupSection(onExport: () -> Unit, onImport: () -> Unit) {
    SectionCard(title = stringResource(R.string.seu_section_backup)) {
        HintText(stringResource(R.string.seu_backup_hint))
        Button(
            onClick = onExport,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.seu_action_export))
        }
        OutlinedButton(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.seu_action_import))
        }
    }
}

@Composable
private fun AboutSection() {
    SectionCard(title = stringResource(R.string.seu_section_about)) {
        Text(
            text = stringResource(R.string.seu_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        HintText(stringResource(R.string.seu_about_offline))
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun themeLabelText(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.seu_theme_system
        ThemeMode.LIGHT -> R.string.seu_theme_light
        ThemeMode.DARK -> R.string.seu_theme_dark
    }
)

private fun backupFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    return "fragment-time-backup-$stamp.json"
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

private fun canScheduleExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

private fun openAppDetailsSettings(context: Context): Boolean = runCatching {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}.isSuccess

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}