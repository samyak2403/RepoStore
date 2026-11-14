package com.samyak.repostore.util

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.samyak.gitcore.util.PackageIdParser
import com.samyak.gitcore.util.PackageIdResolver
import com.samyak.repostore.data.model.GitHubRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches the real Android applicationId (package name) directly from the
 * GitHub repository source code (`build.gradle`, `AndroidManifest.xml`, etc.).
 *
 * Delegates URL generation to [PackageIdResolver] and parsing/validation to
 * [PackageIdParser] in `GitCore`.
 */
object PackageIdFetcher {

    private const val TAG = "PackageIdFetcher"

    private val defaultClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
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
     * Fetch the real applicationId from a GitHub repo's source code.
     * Returns the package name string, or null if not found.
     */
    suspend fun fetchPackageId(repo: GitHubRepo): String? {
        return fetchPackageId(repo.owner.login, repo.name, repo.defaultBranch, repo.language)
    }

    /**
     * Fetch the real applicationId from a GitHub repo's source code.
     */
    suspend fun fetchPackageId(
        owner: String,
        name: String,
        defaultBranch: String?,
        language: String?
    ): String? = withContext(Dispatchers.IO) {
        val urls = PackageIdResolver.resolve(owner, name, defaultBranch, language)
        val rawOverride = testBaseUrl
        safeLogD("Resolving packageId for $owner/$name — checking ${urls.size} URLs")

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
                            val packageId = PackageIdParser.parse(rawUrl, content)
                            if (packageId != null) {
                                safeLogD("✓ Found packageId '$packageId' from $rawUrl")
                                return@withContext packageId
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore network error, try next URL
                safeLogV("Failed to fetch $rawUrl: ${e.message}")
            }
        }

        safeLogD("✗ Could not find packageId for $owner/$name")
        return@withContext null
    }

    @VisibleForTesting
    internal fun parsePackageId(url: String, content: String): String? {
        return PackageIdParser.parse(url, content)
    }

    private fun safeLogD(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: RuntimeException) {
            // Ignored in non-Robolectric JVM environments
        }
    }

    private fun safeLogV(message: String) {
        try {
            Log.v(TAG, message)
        } catch (_: RuntimeException) {
            // Ignored in non-Robolectric JVM environments
        }
    }
}
