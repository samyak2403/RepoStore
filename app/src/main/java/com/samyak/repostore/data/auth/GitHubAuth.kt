package com.samyak.repostore.data.auth

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.samyak.repostore.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * GitHub Device Flow Authentication (RFC 8628)
 *
 * Rate limits:
 * - Without auth: 60 requests/hour
 * - With auth: 5,000 requests/hour
 */
object GitHubAuth {

    private const val TAG = "GitHubAuth"

    private const val DEFAULT_DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val DEFAULT_ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
    private const val DEFAULT_USER_API_URL = "https://api.github.com/user"
    private const val SCOPE = ""
    private const val DEFAULT_MIN_POLL_INTERVAL_MS = 5000L
    private const val DEFAULT_SLOW_DOWN_INCREMENT_MS = 5000L
    private const val MAX_CONSECUTIVE_NETWORK_ERRORS = 5

    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Volatile
    private var client: OkHttpClient = defaultClient

    @Volatile
    private var clientId: String = BuildConfig.GITHUB_CLIENT_ID

    @Volatile
    private var deviceCodeUrl: String = DEFAULT_DEVICE_CODE_URL

    @Volatile
    private var accessTokenUrl: String = DEFAULT_ACCESS_TOKEN_URL

    @Volatile
    private var userApiUrl: String = DEFAULT_USER_API_URL

    @Volatile
    private var minPollIntervalMs: Long = DEFAULT_MIN_POLL_INTERVAL_MS

    @Volatile
    private var slowDownIncrementMs: Long = DEFAULT_SLOW_DOWN_INCREMENT_MS

    private val gson = Gson()

    @VisibleForTesting
    internal fun configureForTesting(
        okHttpClient: OkHttpClient? = null,
        testClientId: String? = null,
        deviceCodeEndpoint: String? = null,
        accessTokenEndpoint: String? = null,
        userApiEndpoint: String? = null,
        pollFloorMs: Long? = null,
        slowDownStepMs: Long? = null
    ) {
        client = okHttpClient ?: defaultClient
        clientId = testClientId ?: BuildConfig.GITHUB_CLIENT_ID
        deviceCodeUrl = deviceCodeEndpoint ?: DEFAULT_DEVICE_CODE_URL
        accessTokenUrl = accessTokenEndpoint ?: DEFAULT_ACCESS_TOKEN_URL
        userApiUrl = userApiEndpoint ?: DEFAULT_USER_API_URL
        minPollIntervalMs = pollFloorMs ?: DEFAULT_MIN_POLL_INTERVAL_MS
        slowDownIncrementMs = slowDownStepMs ?: DEFAULT_SLOW_DOWN_INCREMENT_MS
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        configureForTesting()
    }

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

    sealed class DeviceCodeResult {
        data class Success(val response: DeviceCodeResponse) : DeviceCodeResult()
        data class Error(val message: String) : DeviceCodeResult()
    }

    sealed class PollResult {
        data class Success(val tokenResponse: TokenResponse) : PollResult()
        data object Expired : PollResult()
        data object AccessDenied : PollResult()
        data class Error(val errorCode: String?, val message: String) : PollResult()
    }

    /**
     * Step 1: Request device code with structured error details.
     */
    suspend fun requestDeviceCodeResult(): DeviceCodeResult = withContext(Dispatchers.IO) {
        val currentClientId = clientId.trim()
        if (currentClientId.isEmpty()) {
            return@withContext DeviceCodeResult.Error(
                "GitHub OAuth Client ID is not configured."
            )
        }

        try {
            val formBody = FormBody.Builder()
                .add("client_id", currentClientId)
                .add("scope", SCOPE)
                .build()

            val request = Request.Builder()
                .url(deviceCodeUrl)
                .post(formBody)
                .header("Accept", "application/json")
                .header("User-Agent", "RepoStore-Android")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrBlank()) {
                    val parsed = gson.fromJson(body, DeviceCodeResponse::class.java)
                    if (parsed != null && !parsed.deviceCode.isNullOrBlank() && !parsed.userCode.isNullOrBlank()) {
                        DeviceCodeResult.Success(parsed)
                    } else {
                        DeviceCodeResult.Error("Invalid device code response from GitHub.")
                    }
                } else {
                    Log.e(TAG, "Device code error HTTP ${response.code}: $body")
                    DeviceCodeResult.Error(
                        "GitHub returned HTTP ${response.code} when starting sign-in."
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Device code request failed", e)
            DeviceCodeResult.Error(e.message ?: "Network error contacting GitHub.")
        }
    }

    suspend fun requestDeviceCode(): DeviceCodeResponse? {
        return (requestDeviceCodeResult() as? DeviceCodeResult.Success)?.response
    }

    /**
     * Step 2: Poll for access token per RFC 8628.
     * - Respects remaining device code lifetime via [expiresAtEpochMs]
     * - Permanently increases poll interval by 5s on every `slow_down` response
     * - Returns explicit [PollResult] states instead of collapsing failures to null
     */
    suspend fun pollForTokenResult(
        deviceCode: String,
        intervalSeconds: Int,
        expiresAtEpochMs: Long = System.currentTimeMillis() + 15 * 60 * 1000L
    ): PollResult = withContext(Dispatchers.IO) {
        val currentClientId = clientId.trim()
        if (currentClientId.isEmpty()) {
            return@withContext PollResult.Error(
                "missing_client_id",
                "GitHub OAuth Client ID is not configured."
            )
        }

        var pollIntervalMs = (intervalSeconds * 1000L).coerceAtLeast(minPollIntervalMs)
        var consecutiveErrors = 0

        while (System.currentTimeMillis() < expiresAtEpochMs) {
            delay(pollIntervalMs)
            if (System.currentTimeMillis() >= expiresAtEpochMs) {
                return@withContext PollResult.Expired
            }

            try {
                val formBody = FormBody.Builder()
                    .add("client_id", currentClientId)
                    .add("device_code", deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .build()

                val request = Request.Builder()
                    .url(accessTokenUrl)
                    .post(formBody)
                    .header("Accept", "application/json")
                    .header("User-Agent", "RepoStore-Android")
                    .build()

                val tokenResponse = client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        try {
                            gson.fromJson(body, TokenResponse::class.java)
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                }

                if (tokenResponse == null) {
                    consecutiveErrors++
                    if (consecutiveErrors >= MAX_CONSECUTIVE_NETWORK_ERRORS) {
                        return@withContext PollResult.Error(
                            "empty_response",
                            "Could not reach GitHub authorization service."
                        )
                    }
                    continue
                }

                consecutiveErrors = 0

                when (tokenResponse.error) {
                    null -> {
                        if (!tokenResponse.accessToken.isNullOrBlank()) {
                            return@withContext PollResult.Success(tokenResponse)
                        }
                    }
                    "authorization_pending" -> {
                        Log.d(TAG, "Waiting for user authorization...")
                    }
                    "slow_down" -> {
                        // RFC 8628 §3.5: increase interval for all subsequent polls
                        pollIntervalMs += slowDownIncrementMs
                        Log.d(TAG, "Rate-limited by GitHub, slowing poll interval to ${pollIntervalMs}ms")
                    }
                    "expired_token" -> {
                        Log.e(TAG, "Device code expired")
                        return@withContext PollResult.Expired
                    }
                    "access_denied" -> {
                        Log.e(TAG, "User denied authorization")
                        return@withContext PollResult.AccessDenied
                    }
                    "incorrect_client_credentials",
                    "bad_verification_code",
                    "unsupported_grant_type",
                    "incorrect_device_code" -> {
                        val desc = tokenResponse.errorDescription ?: tokenResponse.error
                        Log.e(TAG, "Terminal OAuth error: ${tokenResponse.error} ($desc)")
                        return@withContext PollResult.Error(tokenResponse.error, desc)
                    }
                    else -> {
                        Log.e(TAG, "Token error: ${tokenResponse.error} - ${tokenResponse.errorDescription}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Poll error", e)
                consecutiveErrors++
                if (consecutiveErrors >= MAX_CONSECUTIVE_NETWORK_ERRORS) {
                    return@withContext PollResult.Error(
                        "network_error",
                        e.message ?: "Network connection lost while waiting for GitHub."
                    )
                }
            }
        }

        PollResult.Expired
    }

    suspend fun pollForToken(deviceCode: String, interval: Int): TokenResponse? {
        return (pollForTokenResult(deviceCode, interval) as? PollResult.Success)?.tokenResponse
    }

    /**
     * Fetch authenticated user info.
     */
    suspend fun fetchUser(token: String): GitHubUser? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(userApiUrl)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "RepoStore-Android")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrBlank()) {
                    gson.fromJson(body, GitHubUser::class.java)
                } else {
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fetch user failed", e)
            null
        }
    }

    // Token management - delegated to SecureTokenStorage
    fun saveToken(context: Context, token: String): Boolean {
        return SecureTokenStorage.saveToken(context, token)
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

    fun onTokenUnauthorized(context: Context) {
        SecureTokenStorage.onTokenUnauthorized(context)
    }

    fun getSessionResetReason(context: Context): SessionResetReason? {
        return SecureTokenStorage.getSessionResetReason(context)
    }

    fun clearSessionResetReason(context: Context) {
        SecureTokenStorage.clearSessionResetReason(context)
    }

    fun savePendingDeviceFlow(context: Context, pending: PendingDeviceFlow) {
        SecureTokenStorage.savePendingDeviceFlow(context, pending)
    }

    fun getPendingDeviceFlow(context: Context): PendingDeviceFlow? {
        return SecureTokenStorage.getPendingDeviceFlow(context)
    }

    fun clearPendingDeviceFlow(context: Context) {
        SecureTokenStorage.clearPendingDeviceFlow(context)
    }

    fun signOut(context: Context) {
        SecureTokenStorage.signOut(context)
    }
}
