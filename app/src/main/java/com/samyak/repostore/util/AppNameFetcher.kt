package com.samyak.repostore.util

import androidx.annotation.VisibleForTesting
import com.samyak.gitcore.util.AppNameParser
import com.samyak.gitcore.util.AppNameResolver
import com.samyak.repostore.data.model.GitHubRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object AppNameFetcher {

    private val defaultClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var testBaseUrl: String? = null

    @Volatile
    private var testClient: OkHttpClient? = null

    private val activeClient: OkHttpClient
        get() = testClient ?: defaultClient

    @VisibleForTesting
    internal fun configureForTesting(baseUrl: String?, client: OkHttpClient? = null) {
        testBaseUrl = baseUrl?.trimEnd('/')
        testClient = client
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        testBaseUrl = null
        testClient = null
    }

    /**
     * Loops through potential paths and tries to extract the real app name.
     */
    suspend fun fetchRealName(repo: GitHubRepo): String? {
        return fetchRealName(repo.owner.login, repo.name, repo.defaultBranch, repo.language)
    }

    /**
     * Loops through potential paths and tries to extract the real app name using raw fields.
     */
    suspend fun fetchRealName(
        owner: String,
        name: String,
        defaultBranch: String?,
        language: String?
    ): String? = withContext(Dispatchers.IO) {
        val urls = AppNameResolver.resolve(owner, name, defaultBranch, language)
        val rawOverride = testBaseUrl

        for (rawUrl in urls) {
            val requestUrl = if (rawOverride != null) {
                rawUrl.replaceFirst("https://raw.githubusercontent.com", rawOverride)
            } else {
                rawUrl
            }
            try {
                val request = Request.Builder()
                    .url(requestUrl)
                    .header("Accept", "text/plain")
                    .build()
                activeClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val content = response.body?.string()
                        if (!content.isNullOrBlank()) {
                            val parsedName = AppNameParser.parse(rawUrl, content)
                            if (parsedName != null) {
                                return@withContext parsedName
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore network error for this specific URL and try next
            }
        }
        return@withContext null
    }

    @VisibleForTesting
    internal fun parseNameFromContent(url: String, content: String): String? {
        return AppNameParser.parse(url, content)
    }
}
