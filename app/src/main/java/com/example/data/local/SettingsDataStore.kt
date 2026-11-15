package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mediasaver_settings")

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    ARABIC("ar")
}

enum class DefaultQuality(val label: String) {
    BEST("Best Available"),
    HD_1080("1080p"),
    HD_720("720p"),
    SD_480("480p"),
    AUDIO_ONLY("Audio Only")
}

enum class SaveLocation {
    MEDIA_STORE,
    APP_PRIVATE
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val defaultQuality: DefaultQuality = DefaultQuality.BEST,
    val saveLocation: SaveLocation = SaveLocation.MEDIA_STORE,
    val notificationsEnabled: Boolean = true,
    val autoClipboardEnabled: Boolean = true
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val SAVE_LOCATION = stringPreferencesKey("save_location")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val AUTO_CLIPBOARD = booleanPreferencesKey("auto_clipboard_enabled")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name)
            }.getOrDefault(ThemeMode.DARK),
            language = runCatching {
                AppLanguage.valueOf(prefs[Keys.LANGUAGE] ?: AppLanguage.ENGLISH.name)
            }.getOrDefault(AppLanguage.ENGLISH),
            defaultQuality = runCatching {
                DefaultQuality.valueOf(prefs[Keys.DEFAULT_QUALITY] ?: DefaultQuality.BEST.name)
            }.getOrDefault(DefaultQuality.BEST),
            saveLocation = runCatching {
                SaveLocation.valueOf(prefs[Keys.SAVE_LOCATION] ?: SaveLocation.MEDIA_STORE.name)
            }.getOrDefault(SaveLocation.MEDIA_STORE),
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            autoClipboardEnabled = prefs[Keys.AUTO_CLIPBOARD] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setDefaultQuality(quality: DefaultQuality) {
        context.dataStore.edit { it[Keys.DEFAULT_QUALITY] = quality.name }
    }

    suspend fun setSaveLocation(location: SaveLocation) {
        context.dataStore.edit { it[Keys.SAVE_LOCATION] = location.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun setAutoClipboardEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CLIPBOARD] = enabled }
    }
}
