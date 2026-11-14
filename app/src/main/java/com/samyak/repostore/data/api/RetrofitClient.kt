package com.samyak.repostore.data.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.VisibleForTesting
import com.samyak.repostore.data.auth.GitHubAuth
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val DEFAULT_BASE_URL = "https://api.github.com/"
    private const val CACHE_SIZE = 10 * 1024 * 1024L // 10 MB

    private var baseUrl: String = DEFAULT_BASE_URL
    private var cache: Cache? = null
    private var githubToken: String? = null
    private var appContext: Context? = null

    fun init(context: Context, token: String? = null) {
        appContext = context.applicationContext
        cache = Cache(File(context.cacheDir, "http_cache"), CACHE_SIZE)
        githubToken = token
        rebuildClient()
    }

    fun setToken(token: String?) {
        githubToken = token
        rebuildClient()
    }

    @VisibleForTesting
    internal fun configureForTesting(
        testBaseUrl: String,
        context: Context? = null,
        token: String? = null
    ) {
        baseUrl = testBaseUrl
        context?.let { appContext = it.applicationContext }
        githubToken = token
        cache = null
        rebuildClient()
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        baseUrl = DEFAULT_BASE_URL
        githubToken = null
        cache = appContext?.let { Cache(File(it.cacheDir, "http_cache"), CACHE_SIZE) }
        rebuildClient()
    }

    /**
     * Get the best available token (OAuth token takes priority)
     */
    private fun getAuthToken(): String? {
        appContext?.let { ctx ->
            val oauthToken = GitHubAuth.getToken(ctx)
            if (!oauthToken.isNullOrBlank()) {
                return oauthToken
            }
        }
        return githubToken
    }

    private fun invalidateUnauthorizedToken() {
        githubToken = null
        appContext?.let { ctx ->
            GitHubAuth.onTokenUnauthorized(ctx)
        }
    }

    private fun isAuthorizedTargetHost(host: String): Boolean {
        val lower = host.lowercase()
        if (lower == "api.github.com" || lower.endsWith(".github.com")) {
            return true
        }
        val configuredHost = baseUrl.toHttpUrlOrNull()?.host?.lowercase()
        return baseUrl != DEFAULT_BASE_URL && configuredHost == lower
    }

    private fun isNetworkAvailable(): Boolean {
        val context = appContext ?: return true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private var okHttpClient = buildOkHttpClient()

    private fun buildOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .cache(cache)
            // Add auth header ONLY for GitHub API domain (prevents token leakage),
            // and auto-clear stale/revoked tokens on HTTP 401 with one unauthenticated retry.
            .addInterceptor { chain ->
                val request = chain.request()
                val baseBuilder = request.newBuilder()
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "GitHubAppStore-Android")

                val host = request.url.host
                val token = if (isAuthorizedTargetHost(host)) getAuthToken() else null
                val authedRequest = if (!token.isNullOrBlank()) {
                    baseBuilder.header("Authorization", "Bearer $token").build()
                } else {
                    baseBuilder.build()
                }

                val response = chain.proceed(authedRequest)
                if (response.code == 401 && !token.isNullOrBlank()) {
                    response.close()
                    invalidateUnauthorizedToken()
                    val unauthenticatedRequest = request.newBuilder()
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "GitHubAppStore-Android")
                        .removeHeader("Authorization")
                        .build()
                    return@addInterceptor chain.proceed(unauthenticatedRequest)
                }
                response
            }
            // Network Cache interceptor:
            // Short TTL (60s) on releases, repo metadata, and readme so new releases/descriptions are picked up quickly.
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)

                if (request.cacheControl.noCache) {
                    return@addNetworkInterceptor response
                }

                val path = request.url.encodedPath
                val maxAgeSeconds = if (path.contains("/releases") || path.contains("/repos/") || path.contains("/readme")) {
                    60
                } else {
                    300
                }
                val cacheControl = CacheControl.Builder()
                    .maxAge(maxAgeSeconds, TimeUnit.SECONDS)
                    .build()
                response.newBuilder()
                    .header("Cache-Control", cacheControl.toString())
                    .removeHeader("Pragma")
                    .build()
            }
            // Offline cache interceptor:
            // Only apply max-stale when actually offline; honor no-cache if forceRefresh is requested.
            .addInterceptor { chain ->
                var request = chain.request()
                if (!request.cacheControl.noCache && !isNetworkAvailable()) {
                    val cacheControl = CacheControl.Builder()
                        .maxStale(7, TimeUnit.DAYS)
                        .build()
                    request = request.newBuilder()
                        .cacheControl(cacheControl)
                        .build()
                }
                chain.proceed(request)
            }
            // Retry interceptor for rate limiting
            .addInterceptor { chain ->
                val request = chain.request()
                var response = chain.proceed(request)
                var tryCount = 0
                val maxRetries = 3

                while (response.code == 429 && tryCount < maxRetries) {
                    tryCount++
                    val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()
                    response.close()

                    val delayMs = if (retryAfterSeconds != null) {
                        retryAfterSeconds.coerceAtLeast(0L) * 1000L
                    } else {
                        tryCount * 2000L
                    }
                    Thread.sleep(minOf(delayMs, 5000L))
                    response = chain.proceed(request)
                }
                response
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun rebuildClient() {
        okHttpClient = buildOkHttpClient()
        retrofit = buildRetrofit()
        _api = retrofit.create(GitHubApi::class.java)
    }

    private var retrofit = buildRetrofit()

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private var _api: GitHubApi = retrofit.create(GitHubApi::class.java)

    val api: GitHubApi get() = _api

    /**
     * Refresh client to pick up new auth token
     */
    fun refreshAuth() {
        rebuildClient()
    }
}
