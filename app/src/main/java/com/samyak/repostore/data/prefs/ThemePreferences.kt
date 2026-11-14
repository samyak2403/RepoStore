package com.samyak.repostore.data.prefs

import android.content.Context
import kotlinx.coroutines.runBlocking

/**
 * Compatibility facade delegating theme preference reads and writes
 * to the DataStore-backed [ThemeSettingsStore].
 */
object ThemePreferences {

    const val THEME_SYSTEM = ThemeSettingsStore.THEME_SYSTEM
    const val THEME_FOR_YOU = ThemeSettingsStore.THEME_FOR_YOU
    const val THEME_DARK = ThemeSettingsStore.THEME_DARK
    const val THEME_LIGHT = ThemeSettingsStore.THEME_LIGHT

    fun getThemeMode(context: Context): Int {
        return runBlocking { ThemeSettingsStore.getInstance(context).getThemeMode() }
    }

    fun setThemeMode(context: Context, mode: Int) {
        runBlocking { ThemeSettingsStore.getInstance(context).setThemeMode(mode) }
    }

    fun applyTheme(mode: Int) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                THEME_SYSTEM -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                THEME_FOR_YOU -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                THEME_DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                THEME_LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    fun applySavedTheme(context: Context) {
        applyTheme(getThemeMode(context))
    }
}