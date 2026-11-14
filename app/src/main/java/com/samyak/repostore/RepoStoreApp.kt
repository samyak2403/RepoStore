package com.samyak.repostore

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.samyak.repostore.data.auth.GitHubAuth
import com.samyak.repostore.data.auth.SecureTokenStorage
import com.samyak.repostore.di.AppContainer
import com.samyak.repostore.worker.UpdateCheckWorker
import java.util.concurrent.TimeUnit

class RepoStoreApp : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "RepoStoreApp"
        private const val UPDATE_CHECK_WORK = "periodic_update_check"
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    val container: AppContainer by lazy { AppContainer(this) }

    val database get() = container.database
    val cachedRepoDao get() = container.cachedRepoDao
    val repository get() = container.repository
    val favoriteAppDao get() = container.favoriteAppDao
    val installedAppMappingDao get() = container.installedAppMappingDao

    override fun onCreate() {
        super.onCreate()

        // Apply saved theme preference from DataStore
        container.themeSettingsStore.applySavedTheme()

        // Initialize networking (Retrofit + OkHttp cache)
        container.initNetworking()

        // Schedule periodic background checks for app updates
        scheduleUpdateChecks()
    }

    /**
     * Schedule a periodic background job that checks installed apps for
     * available updates and posts a notification for each one.
     */
    private fun scheduleUpdateChecks() {
        try {
            if (!WorkManager.isInitialized()) {
                WorkManager.initialize(this, workManagerConfiguration)
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                UPDATE_CHECK_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to schedule background update checks", e)
        }
    }

    fun getStoredToken(): String? {
        return GitHubAuth.getToken(this)
    }

    fun setGitHubToken(token: String?) {
        if (token.isNullOrBlank()) {
            SecureTokenStorage.signOut(this)
        } else {
            SecureTokenStorage.saveToken(this, token)
        }
        container.refreshAuth()
    }

    fun refreshAuth() {
        container.refreshAuth()
    }
}
