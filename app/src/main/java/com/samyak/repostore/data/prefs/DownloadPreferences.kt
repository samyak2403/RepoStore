package com.samyak.repostore.data.prefs

import android.content.Context
import kotlinx.coroutines.runBlocking

/**
 * Compatibility facade delegating download preference reads and writes
 * to the DataStore-backed [DownloadSettingsStore].
 */
object DownloadPreferences {

    const val MIN_THREAD_COUNT = DownloadSettingsStore.MIN_THREAD_COUNT
    const val MAX_THREAD_COUNT = DownloadSettingsStore.MAX_THREAD_COUNT

    val POPULAR_PROXIES = listOf(
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        "https://mirror.ghproxy.com/",
        "https://gh.api.99988866.xyz/"
    )

    fun isMirrorProxyEnabled(context: Context): Boolean {
        return runBlocking { DownloadSettingsStore.getInstance(context).getSettings().mirrorProxyEnabled }
    }

    fun setMirrorProxyEnabled(context: Context, enabled: Boolean) {
        runBlocking { DownloadSettingsStore.getInstance(context).setMirrorProxyEnabled(enabled) }
    }

    fun getMirrorProxyUrl(context: Context): String? {
        return runBlocking { DownloadSettingsStore.getInstance(context).getSettings().mirrorProxyUrl }
    }

    fun setMirrorProxyUrl(context: Context, url: String?) {
        runBlocking { DownloadSettingsStore.getInstance(context).setMirrorProxyUrl(url) }
    }

    fun isMultiPartEnabled(context: Context): Boolean {
        return runBlocking { DownloadSettingsStore.getInstance(context).getSettings().multiPartEnabled }
    }

    fun setMultiPartEnabled(context: Context, enabled: Boolean) {
        runBlocking { DownloadSettingsStore.getInstance(context).setMultiPartEnabled(enabled) }
    }

    fun getThreadCount(context: Context): Int {
        return runBlocking { DownloadSettingsStore.getInstance(context).getSettings().threadCount }
    }

    fun setThreadCount(context: Context, count: Int) {
        runBlocking { DownloadSettingsStore.getInstance(context).setThreadCount(count) }
    }

    fun transformUrl(context: Context, originalUrl: String): String {
        return runBlocking { DownloadSettingsStore.getInstance(context).transformUrl(originalUrl) }
    }

    fun isValidProxyUrl(url: String?): Boolean {
        return DownloadSettingsStore.isValidProxyUrl(url)
    }

    fun adapterFor(context: Context): DownloadSettingsProvider = object : DownloadSettingsProvider {
        override val settingsFlow
            get() = DownloadSettingsStore.getInstance(context).settingsFlow

        override suspend fun getSettings(): DownloadSettings {
            val mirrorEnabled = isMirrorProxyEnabled(context)
            val multiPartEnabled = isMultiPartEnabled(context)
            return DownloadSettings(
                mirrorProxyEnabled = mirrorEnabled,
                mirrorProxyUrl = if (mirrorEnabled) getMirrorProxyUrl(context) else null,
                multiPartEnabled = multiPartEnabled,
                threadCount = if (multiPartEnabled) getThreadCount(context) else DownloadSettingsStore.DEFAULT_THREAD_COUNT
            )
        }

        override suspend fun transformUrl(originalUrl: String): String {
            return transformUrl(context, originalUrl)
        }
    }
}
