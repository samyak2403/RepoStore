package com.samyak.repostore.data.api

import okhttp3.CacheControl
import okhttp3.Interceptor
import java.util.concurrent.TimeUnit

/**
 * Cache policy of the GitHub API client.
 *
 * OkHttp is told a response is fresh for a short window; while the device is online every
 * request past that window must be revalidated with GitHub, so a release published a minute
 * ago and an edited README/description are visible immediately. Only when the device is
 * offline may the on-disk cache be reused — for a while, so the app still opens with content
 * — instead of failing.
 *
 * These interceptors are extracted from [RetrofitClient] so unit tests can drive the exact
 * policy production installs (see `GitHubResponseCachePolicyTest`).
 */
internal object CacheInterceptors {

    /** How long a successful response counts as fresh. */
    const val FRESHNESS_SECONDS = 300

    /** How long a cached response may be served while the device is offline. */
    const val OFFLINE_STALE_DAYS = 7

    /**
     * Network interceptor: mark every successful response as fresh for [freshnessSeconds].
     *
     * GitHub answers with `Cache-Control: public, max-age=60, s-maxage=60`; the window is
     * pinned here so the policy is in one place regardless of what the server sends.
     */
    fun freshnessInterceptor(freshnessSeconds: Int = FRESHNESS_SECONDS): Interceptor =
        Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val cacheControl = CacheControl.Builder()
                .maxAge(freshnessSeconds, TimeUnit.SECONDS)
                .build()
            response.newBuilder()
                .header("Cache-Control", cacheControl.toString())
                .removeHeader("Pragma")
                .build()
        }

    /**
     * Application interceptor: apply the offline stale window only while actually offline.
     *
     * `maxStale` on every request made a cached release list, README or repository payload
     * usable for days without ever asking GitHub, so a newly published release (and an
     * updated description) kept showing the previous content until the entry expired.
     */
    fun stalePolicyInterceptor(isOffline: () -> Boolean): Interceptor =
        Interceptor { chain ->
            val request = chain.request()
            if (!isOffline()) {
                return@Interceptor chain.proceed(request)
            }
            val cacheControl = CacheControl.Builder()
                .maxStale(OFFLINE_STALE_DAYS, TimeUnit.DAYS)
                .build()
            chain.proceed(request.newBuilder().cacheControl(cacheControl).build())
        }
}
