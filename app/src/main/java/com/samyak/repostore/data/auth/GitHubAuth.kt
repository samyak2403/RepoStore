package com.samyak.repostore.data.auth

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.samyak.repostore.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * GitHub Device Flow Authentication
 * 
 * Rate limits:
 * - Without auth: 60 requests/hour
 * - With auth: 5,000 requests/hour
 * 
 * Security notes:
 * - Uses SecureTokenStorage with Android Keystore encryption for token persistence
 * - Client ID is loaded from BuildConfig (can be overridden in local.properties)
 * - For forks: Create your own OAuth App at https://github.com/settings/developers
 */
object GitHubAuth {

    private const val TAG = "GitHubAuth"

    // GitHub OAuth App Client ID - loaded from BuildConfig
    // To use your own: Add GITHUB_CLIENT_ID=your_client_id to local.properties
    // Get your own at: https://github.com/settings/developers -> "New OAuth App"
    private val CLIENT_ID: String = BuildConfig.GITHUB_CLIENT_ID
    
    private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
    private const val USER_API_URL = "https://api.github.com/user"
    
    // No scopes requested - only using auth for increased rate limits (5000/hour vs 60/hour)
    // This grants ZERO permissions to the app - read-only access to public data only
    private const val SCOPE = ""
    private const val MAX_POLL_ATTEMPTS = 60 // 5 minutes max

    private val client = OkHttpClient()
    private val gson = Gson()

    // Data classes
    data class DeviceCodeResponse(
        @SerializedName("device_code") val deviceCode: String,
        @SerializedName("user_code") val userCode: String,
        @SerializedName("verification_uri") val verificationUri: String,
        @SerializedName("expires_in") val expiresIn: Int,
        @SerializedName("interval") val interval: Int
    )

    data class TokenResponse(
        @SerializedName("access_token") val accessToken: String?,
        @SerializedName("token_type") val tokenType: String?,
        @SerializedName("scope") val scope: String?,
        @SerializedName("error") val error: String?,
        @SerializedName("error_description") val errorDescription: String?
    )

    data class GitHubUser(
        @SerializedName("login") val login: String,
        @SerializedName("avatar_url") val avatarUrl: String?,
        @SerializedName("name") val name: String?
    )

    /**
     * Step 1: Request device code
     */
    suspend fun requestDeviceCode(): DeviceCodeResponse? = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("scope", SCOPE)
                .build()

            val request = Request.Builder()
                .url(DEVICE_CODE_URL)
                .post(formBody)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                gson.fromJson(body, DeviceCodeResponse::class.java)
            } else {
                Log.e(TAG, "Device code error: $body")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Device code request failed", e)
            null
        }
    }

    /**
     * Step 2: Poll for access token
     */
    suspend fun pollForToken(deviceCode: String, interval: Int): TokenResponse? = withContext(Dispatchers.IO) {
        var attempts = 0
        val pollInterval = (interval * 1000L).coerceAtLeast(5000L)

        while (attempts < MAX_POLL_ATTEMPTS) {
            delay(pollInterval)
            attempts++

            try {
                val formBody = FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("device_code", deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .build()

                val request = Request.Builder()
                    .url(ACCESS_TOKEN_URL)
                    .post(formBody)
                    .header("Accept", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()

                if (body != null) {
                    val tokenResponse = gson.fromJson(body, TokenResponse::class.java)

                    when (tokenResponse.error) {
                        null -> {
                            // Success!
                            if (tokenResponse.accessToken != null) {
                                return@withContext tokenResponse
                            }
                        }
                        "authorization_pending" -> {
                            // User hasn't entered code yet, continue polling
                            Log.d(TAG, "Waiting for user authorization...")
                        }
                        "slow_down" -> {
                            // Need to slow down polling
                            delay(5000)
                        }
                        "expired_token" -> {
                            // Code expired
                            Log.e(TAG, "Device code expired")
                            return@withContext null
                        }
                        "access_denied" -> {
                            // User denied
                            Log.e(TAG, "User denied authorization")
                            return@withContext null
                        }
                        else -> {
                            Log.e(TAG, "Token error: ${tokenResponse.error}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Poll error", e)
            }
        }

        null
    }

    /**
     * Fetch authenticated user info
     */
    suspend fun fetchUser(token: String): GitHubUser? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(USER_API_URL)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                gson.fromJson(body, GitHubUser::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fetch user failed", e)
            null
        }
    }

    // Token management - delegated to SecureTokenStorage
    fun saveToken(context: Context, token: String) {
        SecureTokenStorage.saveToken(context, token)
    }

    fun getToken(context: Context): String? {
        return SecureTokenStorage.getToken(context)
    }

    fun isSignedIn(context: Context): Boolean {
        return SecureTokenStorage.isSignedIn(context)
    }

    fun saveUser(context: Context, user: GitHubUser) {
        SecureTokenStorage.saveUser(context, user.login, user.avatarUrl, user.name)
    }

    fun getUser(context: Context): GitHubUser? {
        val login = SecureTokenStorage.getUserLogin(context) ?: return null
        return GitHubUser(
            login = login,
            avatarUrl = SecureTokenStorage.getUserAvatar(context),
            name = SecureTokenStorage.getUserName(context)
        )
    }

    fun signOut(context: Context) {
        SecureTokenStorage.signOut(context)
    }
}
