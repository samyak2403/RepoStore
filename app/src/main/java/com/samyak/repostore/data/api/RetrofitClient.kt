package com.samyak.repostore.data.api

import android.content.Context
import com.samyak.repostore.data.auth.GitHubAuth
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "https://api.github.com/"
    private const val CACHE_SIZE = 10 * 1024 * 1024L // 10 MB

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

    /**
     * Get the best available token (OAuth token takes priority)
     */
    private fun getAuthToken(): String? {
        // First check OAuth token from GitHubAuth
        appContext?.let { ctx ->
            val oauthToken = GitHubAuth.getToken(ctx)
            if (!oauthToken.isNullOrBlank()) {
                return oauthToken
            }
        }
        // Fall back to manual token
        return githubToken
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private var okHttpClient = buildOkHttpClient()

    private fun buildOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .cache(cache)
            // Add auth header ONLY for GitHub API requests (security: principle of least privilege)
            .addInterceptor { chain ->
                val request = chain.request()
                val requestBuilder = request.newBuilder()
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .addHeader("User-Agent", "GitHubAppStore-Android")

                // Only add authorization for GitHub API domain (prevents token leakage)
                val host = request.url.host.lowercase()
                if (host == "api.github.com" || host.endsWith(".github.com")) {
                    val token = getAuthToken()
                    if (!token.isNullOrBlank()) {
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                }

                chain.proceed(requestBuilder.build())
            }
            // Cache interceptor - cache responses for 5 minutes
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val cacheControl = CacheControl.Builder()
                    .maxAge(5, TimeUnit.MINUTES)
                    .build()
                response.newBuilder()
                    .header("Cache-Control", cacheControl.toString())
                    .removeHeader("Pragma")
                    .build()
            }
            // Offline cache interceptor
            .addInterceptor { chain ->
                var request = chain.request()
                // If offline, use cache for up to 7 days
                val cacheControl = CacheControl.Builder()
                    .maxStale(7, TimeUnit.DAYS)
                    .build()
                request = request.newBuilder()
                    .cacheControl(cacheControl)
                    .build()
                chain.proceed(request)
            }
            // Retry interceptor for rate limiting
            .addInterceptor { chain ->
                var request = chain.request()
                var response = chain.proceed(request)
                var tryCount = 0
                val maxRetries = 3

                while (response.code == 429 && tryCount < maxRetries) {
                    tryCount++
                    response.close()

                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                        ?: (tryCount * 2000L)

                    Thread.sleep(minOf(retryAfter, 5000L))
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
            .baseUrl(BASE_URL)
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
