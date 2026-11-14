package com.samyak.repostore.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Collections
import java.util.WeakHashMap

data class DownloadSettings(
    val mirrorProxyEnabled: Boolean = false,
    val mirrorProxyUrl: String? = null,
    val multiPartEnabled: Boolean = false,
    val threadCount: Int = DownloadSettingsStore.DEFAULT_THREAD_COUNT
) {
    fun transformUrl(originalUrl: String): String {
        if (!mirrorProxyEnabled) return originalUrl
        val proxyUrl = mirrorProxyUrl
        if (proxyUrl.isNullOrBlank()) return originalUrl

        if (!originalUrl.contains("github.com") && !originalUrl.contains("githubusercontent.com")) {
            return originalUrl
        }

        val normalizedProxy = if (proxyUrl.endsWith("/")) proxyUrl else "$proxyUrl/"
        if (originalUrl.startsWith(normalizedProxy, ignoreCase = true)) {
            return originalUrl
        }
        return "$normalizedProxy$originalUrl"
    }
}

interface DownloadSettingsProvider {
    val settingsFlow: Flow<DownloadSettings>
    suspend fun getSettings(): DownloadSettings
    fun getSettingsBlocking(): DownloadSettings = runBlocking { getSettings() }
    suspend fun transformUrl(originalUrl: String): String = getSettings().transformUrl(originalUrl)
}

private val Context.downloadDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "download_settings_store",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, DownloadSettingsStore.LEGACY_PREFS_NAME))
    }
)

/**
 * DataStore-backed repository for mirror proxy and multi-part download settings,
 * automatically migrating legacy SharedPreferences values on first access.
 */
class DownloadSettingsStore(
    private val dataStore: DataStore<Preferences>
) : DownloadSettingsProvider {

    override val settingsFlow: Flow<DownloadSettings> = dataStore.data.map { prefs ->
        DownloadSettings(
            mirrorProxyEnabled = prefs[KEY_USE_MIRROR_PROXY] ?: false,
            mirrorProxyUrl = prefs[KEY_MIRROR_PROXY_URL],
            multiPartEnabled = prefs[KEY_USE_MULTI_PART] ?: false,
            threadCount = (prefs[KEY_THREAD_COUNT] ?: DEFAULT_THREAD_COUNT)
                .coerceIn(MIN_THREAD_COUNT, MAX_THREAD_COUNT)
        )
    }

    override suspend fun getSettings(): DownloadSettings = settingsFlow.first()

    suspend fun setMirrorProxyEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_USE_MIRROR_PROXY] = enabled }
    }

    suspend fun setMirrorProxyUrl(url: String?) {
        dataStore.edit { prefs ->
            val trimmed = url?.trim()
            if (trimmed.isNullOrEmpty()) {
                prefs.remove(KEY_MIRROR_PROXY_URL)
            } else {
                prefs[KEY_MIRROR_PROXY_URL] = trimmed
            }
        }
    }

    suspend fun setMultiPartEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_USE_MULTI_PART] = enabled }
    }

    suspend fun setThreadCount(count: Int) {
        val clamped = count.coerceIn(MIN_THREAD_COUNT, MAX_THREAD_COUNT)
        dataStore.edit { it[KEY_THREAD_COUNT] = clamped }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        const val LEGACY_PREFS_NAME = "download_preferences"
        const val DEFAULT_THREAD_COUNT = 4
        const val MIN_THREAD_COUNT = 1
        const val MAX_THREAD_COUNT = 32

        private val KEY_USE_MIRROR_PROXY = booleanPreferencesKey("use_mirror_proxy")
        private val KEY_MIRROR_PROXY_URL = stringPreferencesKey("mirror_proxy_url")
        private val KEY_USE_MULTI_PART = booleanPreferencesKey("use_multi_part_download")
        private val KEY_THREAD_COUNT = intPreferencesKey("download_thread_count")

        private val instances = Collections.synchronizedMap(WeakHashMap<Context, DownloadSettingsStore>())

        fun getInstance(context: Context): DownloadSettingsStore {
            val appContext = context.applicationContext ?: context
            return synchronized(instances) {
                instances.getOrPut(appContext) {
                    DownloadSettingsStore(appContext.downloadDataStore)
                }
            }
        }

        fun isValidProxyUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val parsed = url.trim().toHttpUrlOrNull() ?: return false
            return (parsed.scheme == "http" || parsed.scheme == "https") && parsed.host.isNotBlank()
        }
    }
}
