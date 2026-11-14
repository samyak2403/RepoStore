package com.samyak.repostore.data.prefs

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.WeakHashMap

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "theme_settings_store",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, ThemeSettingsStore.LEGACY_PREFS_NAME))
    }
)

/**
 * DataStore-backed manager for app theme mode with SharedPreferences migration.
 */
class ThemeSettingsStore(
    private val dataStore: DataStore<Preferences>
) {

    val themeModeFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: THEME_SYSTEM
    }

    suspend fun getThemeMode(): Int = themeModeFlow.first()

    fun getThemeModeBlocking(): Int = runBlocking { getThemeMode() }

    suspend fun setThemeMode(mode: Int) {
        dataStore.edit { it[KEY_THEME_MODE] = mode }
        applyTheme(mode)
    }

    fun applyTheme(mode: Int) {
        val nightMode = when (mode) {
            THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            THEME_FOR_YOU -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun applySavedTheme() {
        applyTheme(getThemeModeBlocking())
    }

    companion object {
        const val LEGACY_PREFS_NAME = "theme_preferences"
        const val THEME_SYSTEM = 0
        const val THEME_FOR_YOU = 1
        const val THEME_DARK = 2
        const val THEME_LIGHT = 3

        private val KEY_THEME_MODE = intPreferencesKey("theme_mode")

        private val instances = Collections.synchronizedMap(WeakHashMap<Context, ThemeSettingsStore>())

        fun getInstance(context: Context): ThemeSettingsStore {
            val appContext = context.applicationContext ?: context
            return synchronized(instances) {
                instances.getOrPut(appContext) {
                    ThemeSettingsStore(appContext.themeDataStore)
                }
            }
        }
    }
}
