package com.wanghoufan.fragmentplayer.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wanghoufan.fragmentplayer.core.model.AppSettings
import com.wanghoufan.fragmentplayer.core.model.LanguageMode
import com.wanghoufan.fragmentplayer.core.model.ThemeMode
import com.wanghoufan.fragmentplayer.core.repo.SettingsRepository
import com.wanghoufan.fragmentplayer.core.timer.TimeFlowMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl(context: Context) : SettingsRepository {
    private val dataStore = context.applicationContext.settingsDataStore

    private val data: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override val settings: Flow<AppSettings> = data.map { it.toAppSettings() }

    override val folderUri: Flow<String?> = data.map { it[KEY_FOLDER_URI].orNullIfBlank() }

    override suspend fun current(): AppSettings = data.first().toAppSettings()

    override suspend fun setTheme(mode: ThemeMode) = write { it[KEY_THEME] = mode.name }

    override suspend fun setLanguage(mode: LanguageMode) = write { it[KEY_LANGUAGE] = mode.name }

    override suspend fun setFontScale(scale: Float) = write { it[KEY_FONT_SCALE] = scale }

    override suspend fun setVibration(enabled: Boolean) = write { it[KEY_VIBRATION] = enabled }

    override suspend fun setAlertSound(enabled: Boolean) = write { it[KEY_ALERT_SOUND] = enabled }

    override suspend fun setTimeFlow(multiplier: Int) = write { it[KEY_TIME_FLOW] = multiplier }

    override suspend fun setFolderUri(uri: String?) = write {
        if (uri.isNullOrBlank()) it.remove(KEY_FOLDER_URI) else it[KEY_FOLDER_URI] = uri
    }

    override suspend fun currentFolderUri(): String? = folderUri.first()

    private suspend fun write(block: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit { block(it) }
        } catch (_: IOException) {
        }
    }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        theme = runCatching { ThemeMode.valueOf(this[KEY_THEME] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM),
        language = runCatching {
            LanguageMode.valueOf(this[KEY_LANGUAGE] ?: LanguageMode.FOLLOW_SYSTEM.name)
        }.getOrDefault(LanguageMode.FOLLOW_SYSTEM),
        fontScale = this[KEY_FONT_SCALE] ?: DEFAULT_FONT_SCALE,
        vibration = this[KEY_VIBRATION] ?: true,
        alertSound = this[KEY_ALERT_SOUND] ?: true,
        timeFlow = TimeFlowMath.sanitize(this[KEY_TIME_FLOW] ?: TimeFlowMath.NORMAL)
    )

    private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

    private companion object {
        const val DEFAULT_FONT_SCALE = 1.15f

        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        val KEY_VIBRATION = booleanPreferencesKey("vibration")
        val KEY_ALERT_SOUND = booleanPreferencesKey("alert_sound")
        val KEY_TIME_FLOW = intPreferencesKey("time_flow")
        val KEY_FOLDER_URI = stringPreferencesKey("folder_uri")
    }
}