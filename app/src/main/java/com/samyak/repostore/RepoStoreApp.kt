package com.samyak.repostore

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.samyak.repostore.data.api.RetrofitClient
import com.samyak.repostore.data.auth.GitHubAuth
import com.samyak.repostore.data.auth.SecureTokenStorage
import com.samyak.repostore.data.db.AppDatabase
import com.samyak.repostore.data.repository.GitHubRepository
import com.samyak.repostore.worker.UpdateCheckWorker
import java.util.concurrent.TimeUnit

class RepoStoreApp : Application() {

    companion object {
        private const val UPDATE_CHECK_WORK = "periodic_update_check"
    }

    val database by lazy { AppDatabase.getDatabase(this) }
    val cachedRepoDao by lazy { database.cachedRepoDao() }
    val repository by lazy { GitHubRepository(database.repoDao(), database.cachedRepoDao()) }
    val favoriteAppDao by lazy { database.favoriteAppDao() }
    val installedAppMappingDao by lazy { database.installedAppMappingDao() }

    override fun onCreate() {
        super.onCreate()
        
        // Apply saved theme preference
        com.samyak.repostore.data.prefs.ThemePreferences.applySavedTheme(this)

        // Initialize RetrofitClient with cache
        // OAuth token from GitHubAuth takes priority in RetrofitClient
        RetrofitClient.init(this, null)

        // Schedule periodic background checks for app updates
        scheduleUpdateChecks()
    }

    /**
     * Schedule a periodic background job that checks installed apps for
     * available updates and posts a notification for each one.
     */
    private fun scheduleUpdateChecks() {
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
    }

    /**
     * Get the stored OAuth token (for display purposes only).
     * For actual API calls, RetrofitClient uses GitHubAuth.getToken() which uses SecureTokenStorage.
     */
    fun getStoredToken(): String? {
        return GitHubAuth.getToken(this)
    }

    /**
     * Set a manual GitHub Personal Access Token.
     * This uses the same secure storage as OAuth tokens.
     * Pass null to clear the token.
     */
    fun setGitHubToken(token: String?) {
        if (token.isNullOrBlank()) {
            // Clear the token
            SecureTokenStorage.signOut(this)
        } else {
            // Save the manual token
            SecureTokenStorage.saveToken(this, token)
        }
        // Refresh RetrofitClient to pick up the new token
        RetrofitClient.refreshAuth()
    }

    /**
     * Refresh RetrofitClient auth after sign-in/sign-out
     */
    fun refreshAuth() {
        RetrofitClient.refreshAuth()
    }
}
