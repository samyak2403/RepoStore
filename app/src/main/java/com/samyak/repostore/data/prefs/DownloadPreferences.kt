package com.samyak.repostore.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages download-related preferences including:
 * - Mirror proxy URL for regions where GitHub is blocked
 * - Multi-part download settings for faster speeds
 */
object DownloadPreferences {
    
    private const val PREFS_NAME = "download_preferences"
    
    // Mirror Proxy Settings
    private const val KEY_USE_MIRROR_PROXY = "use_mirror_proxy"
    private const val KEY_MIRROR_PROXY_URL = "mirror_proxy_url"
    
    // Multi-Part Download Settings
    private const val KEY_USE_MULTI_PART = "use_multi_part_download"
    private const val KEY_THREAD_COUNT = "download_thread_count"
    
    // Default values
    private const val DEFAULT_THREAD_COUNT = 4
    const val MIN_THREAD_COUNT = 1
    const val MAX_THREAD_COUNT = 32
    
    // Popular mirror proxies
    val POPULAR_PROXIES = listOf(
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        "https://mirror.ghproxy.com/",
        "https://gh.api.99988866.xyz/"
    )
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // ==================== Mirror Proxy ====================
    
    /**
     * Check if mirror proxy is enabled
     */
    fun isMirrorProxyEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_MIRROR_PROXY, false)
    }
    
    /**
     * Enable or disable mirror proxy
     */
    fun setMirrorProxyEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USE_MIRROR_PROXY, enabled).apply()
    }
    
    /**
     * Get the mirror proxy URL (e.g., "https://gh-proxy.com/")
     */
    fun getMirrorProxyUrl(context: Context): String? {
        return getPrefs(context).getString(KEY_MIRROR_PROXY_URL, null)
    }
    
    /**
     * Set the mirror proxy URL
     */
    fun setMirrorProxyUrl(context: Context, url: String?) {
        getPrefs(context).edit().putString(KEY_MIRROR_PROXY_URL, url?.trim()).apply()
    }
    
    // ==================== Multi-Part Download ====================
    
    /**
     * Check if multi-part download is enabled
     */
    fun isMultiPartEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_MULTI_PART, false)
    }
    
    /**
     * Enable or disable multi-part download
     */
    fun setMultiPartEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USE_MULTI_PART, enabled).apply()
    }
    
    /**
     * Get the number of download threads (1-32)
     */
    fun getThreadCount(context: Context): Int {
        return getPrefs(context).getInt(KEY_THREAD_COUNT, DEFAULT_THREAD_COUNT)
            .coerceIn(MIN_THREAD_COUNT, MAX_THREAD_COUNT)
    }
    
    /**
     * Set the number of download threads
     */
    fun setThreadCount(context: Context, count: Int) {
        val validCount = count.coerceIn(MIN_THREAD_COUNT, MAX_THREAD_COUNT)
        getPrefs(context).edit().putInt(KEY_THREAD_COUNT, validCount).apply()
    }
    
    // ==================== URL Transformation ====================
    
    /**
     * Transform a GitHub URL through the mirror proxy if enabled
     * Example: https://github.com/user/repo/releases/download/v1.0/app.apk
     *       -> https://gh-proxy.com/https://github.com/user/repo/releases/download/v1.0/app.apk
     */
    fun transformUrl(context: Context, originalUrl: String): String {
        if (!isMirrorProxyEnabled(context)) {
            return originalUrl
        }
        
        val proxyUrl = getMirrorProxyUrl(context)
        if (proxyUrl.isNullOrBlank()) {
            return originalUrl
        }
        
        // Only transform GitHub URLs
        if (!originalUrl.contains("github.com") && !originalUrl.contains("githubusercontent.com")) {
            return originalUrl
        }
        
        // Ensure proxy URL ends with /
        val normalizedProxy = if (proxyUrl.endsWith("/")) proxyUrl else "$proxyUrl/"
        
        return "$normalizedProxy$originalUrl"
    }
    
    /**
     * Validate a mirror proxy URL
     */
    fun isValidProxyUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val trimmed = url.trim()
            trimmed.startsWith("http://") || trimmed.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }
}
