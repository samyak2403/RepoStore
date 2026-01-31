package com.samyak.repostore

import android.app.Application
import com.samyak.repostore.data.api.RetrofitClient
import com.samyak.repostore.data.auth.GitHubAuth
import com.samyak.repostore.data.auth.SecureTokenStorage
import com.samyak.repostore.data.db.AppDatabase
import com.samyak.repostore.data.repository.GitHubRepository

class RepoStoreApp : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { GitHubRepository(database.repoDao()) }
    val favoriteAppDao by lazy { database.favoriteAppDao() }

    override fun onCreate() {
        super.onCreate()

        // Initialize RetrofitClient with cache
        // OAuth token from GitHubAuth takes priority in RetrofitClient
        RetrofitClient.init(this, null)
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
