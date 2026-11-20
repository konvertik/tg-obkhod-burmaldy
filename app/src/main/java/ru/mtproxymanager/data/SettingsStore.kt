package ru.mtproxymanager.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

val REFRESH_HOURS_OPTIONS = listOf(1, 3, 6, 12, 24)

val DEFAULT_SOURCES = listOf(
    "https://raw.githubusercontent.com/0xRadikal/Free-Telegram-Proxy/main/top100.txt",
    "https://raw.githubusercontent.com/0xRadikal/Free-Telegram-Proxy/main/faketls/tme.txt",
)

private const val DEFAULT_REFRESH_HOURS = 1

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val sourcesKey = stringPreferencesKey("sources")
    private val refreshKey = intPreferencesKey("refresh_hours")
    private val fakeTlsKey = booleanPreferencesKey("prefer_faketls")
    private val lastRefreshKey = longPreferencesKey("last_refresh_at")

    private val data get() = context.settingsDataStore.data

    val theme: Flow<ThemeMode> = data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val sources: Flow<List<String>> = data.map { prefs ->
        prefs[sourcesKey]?.let { parseSources(it) } ?: DEFAULT_SOURCES
    }

    val refreshHours: Flow<Int> = data.map { prefs ->
        prefs[refreshKey]?.takeIf { it in REFRESH_HOURS_OPTIONS } ?: DEFAULT_REFRESH_HOURS
    }

    val preferFakeTls: Flow<Boolean> = data.map { prefs -> prefs[fakeTlsKey] ?: true }

    val lastRefreshAt: Flow<Long> = data.map { prefs -> prefs[lastRefreshKey] ?: 0L }

    suspend fun setTheme(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setSources(urls: List<String>) {
        context.settingsDataStore.edit { it[sourcesKey] = urls.joinToString("\n") }
    }

    suspend fun setRefreshHours(hours: Int) {
        context.settingsDataStore.edit { it[refreshKey] = hours }
    }

    suspend fun setPreferFakeTls(enabled: Boolean) {
        context.settingsDataStore.edit { it[fakeTlsKey] = enabled }
    }

    suspend fun setLastRefreshAt(millis: Long) {
        context.settingsDataStore.edit { it[lastRefreshKey] = millis }
    }

    private fun parseSources(raw: String): List<String> =
        raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
}
