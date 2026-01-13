package com.samyak.repostore.data.auth

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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
 */
object GitHubAuth {

    private const val TAG = "GitHubAuth"
    private const val PREFS_NAME = "github_auth"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_USER_LOGIN = "user_login"
    private const val KEY_USER_AVATAR = "user_avatar"
    private const val KEY_USER_NAME = "user_name"

    // GitHub OAuth App Client ID
    // TO GET YOUR OWN CLIENT ID:
    // 1. Go to: https://github.com/settings/developers
    // 2. Click "New OAuth App"
    // 3. Fill: Application name: RepoStore, Homepage URL: https://github.com, Callback URL: https://github.com
    // 4. Click "Register application"
    // 5. Copy the Client ID and paste below
    private const val CLIENT_ID = "Ov23liinOZYK0IduPvuO"  // <-- REPLACE THIS!
    
    private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
    private const val USER_API_URL = "https://api.github.com/user"
    
    private const val SCOPE = "public_repo read:user"
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

    // Token management
    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun getToken(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)
    }

    fun isSignedIn(context: Context): Boolean {
        return getToken(context) != null
    }

    fun saveUser(context: Context, user: GitHubUser) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_LOGIN, user.login)
            .putString(KEY_USER_AVATAR, user.avatarUrl)
            .putString(KEY_USER_NAME, user.name)
            .apply()
    }

    fun getUser(context: Context): GitHubUser? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val login = prefs.getString(KEY_USER_LOGIN, null) ?: return null
        return GitHubUser(
            login = login,
            avatarUrl = prefs.getString(KEY_USER_AVATAR, null),
            name = prefs.getString(KEY_USER_NAME, null)
        )
    }

    fun signOut(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
