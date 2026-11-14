package com.samyak.repostore.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.Collections
import java.util.WeakHashMap

private val Context.updateNotifiedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "update_notified_store",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, UpdateNotificationStore.LEGACY_PREFS_NAME))
    }
)

/**
 * Tracks the last release tag notified per installed package to deduplicate background update alerts.
 */
class UpdateNotificationStore(
    private val dataStore: DataStore<Preferences>
) {
    suspend fun getLastNotifiedTag(packageName: String): String? {
        val prefs = dataStore.data.first()
        return prefs[stringPreferencesKey(packageName)]
    }

    suspend fun setLastNotifiedTag(packageName: String, tagName: String) {
        dataStore.edit { it[stringPreferencesKey(packageName)] = tagName }
    }

    suspend fun clearPackage(packageName: String) {
        dataStore.edit { it.remove(stringPreferencesKey(packageName)) }
    }

    companion object {
        const val LEGACY_PREFS_NAME = "update_notified_tags"

        private val instances = Collections.synchronizedMap(WeakHashMap<Context, UpdateNotificationStore>())

        fun getInstance(context: Context): UpdateNotificationStore {
            val appContext = context.applicationContext ?: context
            return synchronized(instances) {
                instances.getOrPut(appContext) {
                    UpdateNotificationStore(appContext.updateNotifiedDataStore)
                }
            }
        }
    }
}
