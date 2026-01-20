package com.samyak.repostore.data.repository

import com.samyak.repostore.data.api.RetrofitClient
import com.samyak.repostore.data.db.RepoDao
import com.samyak.repostore.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.time.Instant
import java.time.temporal.ChronoUnit

class GitHubRepository(private val repoDao: RepoDao) {

    private val api = RetrofitClient.api

    // In-memory cache
    private val releaseCache = mutableMapOf<String, GitHubRelease?>()
    private val screenshotCache = mutableMapOf<String, List<String>>()
    private val developerReposCache = mutableMapOf<String, Pair<Long, List<AppItem>>>()
    private val apkReposCache = mutableMapOf<String, Boolean>() // Cache for repos with APK
    
    private var lastFetchTime = 0L
    private val cacheValidityMs = 10 * 60 * 1000L // 10 minutes
    private val developerCacheValidityMs = 15 * 60 * 1000L // 15 minutes

    private val screenshotFolders = listOf(
        "screenshots", "screenshot", "images", "image", "assets",
        "art", "media", "pics", "pictures", "img"
    )

    private val imageExtensions = listOf(".png", ".jpg", ".jpeg", ".gif", ".webp")
    
    // Installable asset extensions (APK for Android)
    private val installableExtensions = listOf(".apk", ".aab")

    /**
     * Check if a release has installable APK assets
     */
    private fun hasInstallableAsset(release: GitHubRelease?): Boolean {
        if (release == null) return false
        return release.assets.any { asset ->
            installableExtensions.any { ext ->
                asset.name.lowercase().endsWith(ext)
            }
        }
    }

    /**
     * Check if repo has APK in latest release
     */
    private suspend fun repoHasApk(owner: String, repoName: String): Boolean {
        val cacheKey = "$owner/$repoName"
        
        // Check cache first
        apkReposCache[cacheKey]?.let { return it }
        
        return try {
            val release = api.getLatestRelease(owner, repoName)
            val hasApk = hasInstallableAsset(release)
            apkReposCache[cacheKey] = hasApk
            if (hasApk) {
                releaseCache[cacheKey] = release
            }
            hasApk
        } catch (e: Exception) {
            apkReposCache[cacheKey] = false
            false
        }
    }

    /**
     * Filter repos to only include those with APK releases
     */
    private suspend fun filterReposWithApk(repos: List<GitHubRepo>): List<AppItem> = coroutineScope {
        val results = repos.map { repo ->
            async {
                try {
                    val hasApk = repoHasApk(repo.owner.login, repo.name)
                    if (hasApk) {
                        val release = releaseCache["${repo.owner.login}/${repo.name}"]
                        val tag = determineTag(repo, release)
                        AppItem(repo, release, tag)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
        results.awaitAll().filterNotNull()
    }

    suspend fun searchApps(query: String, page: Int = 1): Result<List<AppItem>> = withContext(Dispatchers.IO) {
        try {
            val searchQuery = "$query in:name,description topic:android"
            val response = api.searchRepositories(searchQuery, perPage = 30, page = page)

            // Filter to only repos with APK releases
            val appItems = filterReposWithApk(response.items)

            if (appItems.isNotEmpty()) {
                repoDao.insertRepos(response.items)
            }
            
            Result.success(appItems)
        } catch (e: HttpException) {
            handleHttpException(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPopularAndroidApps(page: Int = 1): Result<List<AppItem>> = withContext(Dispatchers.IO) {
        try {
            val query = "android app topic:android stars:>100"
            val response = api.searchRepositories(query, perPage = 40, page = page)

            lastFetchTime = System.currentTimeMillis()

            // Filter to only repos with APK releases
            val appItems = filterReposWithApk(response.items)

            if (appItems.isNotEmpty() && page == 1) {
                repoDao.clearAll()
                repoDao.insertRepos(response.items)
            }

            Result.success(appItems)
        } catch (e: HttpException) {
            handleHttpException(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAppsByCategory(category: AppCategory, page: Int = 1): Result<List<AppItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val query = "${category.query} stars:>50"
                val response = api.searchRepositories(query, perPage = 30, page = page)

                // Filter to only repos with APK releases
                val appItems = filterReposWithApk(response.items)

                if (appItems.isNotEmpty()) {
                    repoDao.insertRepos(response.items)
                }
                
                Result.success(appItems)
            } catch (e: HttpException) {
                handleHttpException(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getRepoDetails(owner: String, repoName: String): Result<GitHubRepo> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cached = repoDao.getRepoByFullName("$owner/$repoName")
            if (cached != null) {
                // Return cached, but also try to update in background
                return@withContext Result.success(cached)
            }

            val repo = api.getRepository(owner, repoName)
            repoDao.insertRepo(repo)
            Result.success(repo)
        } catch (e: HttpException) {
            val cached = repoDao.getRepoByFullName("$owner/$repoName")
            if (cached != null) {
                Result.success(cached)
            } else {
                handleHttpException(e)
            }
        } catch (e: Exception) {
            val cached = repoDao.getRepoByFullName("$owner/$repoName")
            if (cached != null) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun getReleases(owner: String, repoName: String): Result<List<GitHubRelease>> = withContext(Dispatchers.IO) {
        try {
            val releases = api.getReleases(owner, repoName, perPage = 5)
            Result.success(releases)
        } catch (e: HttpException) {
            handleHttpException(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLatestRelease(owner: String, repoName: String): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        try {
            val cacheKey = "$owner/$repoName"

            releaseCache[cacheKey]?.let {
                return@withContext Result.success(it)
            }

            val release = api.getLatestRelease(owner, repoName)
            releaseCache[cacheKey] = release
            Result.success(release)
        } catch (e: HttpException) {
            if (e.code() == 404) {
                releaseCache["$owner/$repoName"] = null
            }
            handleHttpException(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReadme(owner: String, repoName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.getReadme(owner, repoName)
            val decoded = if (response.encoding == "base64") {
                String(android.util.Base64.decode(response.content.replace("\n", ""), android.util.Base64.DEFAULT))
            } else {
                response.content
            }
            Result.success(decoded)
        } catch (e: HttpException) {
            handleHttpException(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDeveloperRepos(username: String, page: Int = 1): Result<List<AppItem>> = withContext(Dispatchers.IO) {
        try {
            // Check cache
            val currentTime = System.currentTimeMillis()
            val cacheKey = "$username-$page"
            developerReposCache[cacheKey]?.let { (timestamp, apps) ->
                if (currentTime - timestamp < developerCacheValidityMs) {
                    return@withContext Result.success(apps)
                }
            }

            val repos = api.getUserRepos(username, sort = "updated", perPage = 20, page = page)

            val appItems = repos.map { repo ->
                val tag = determineTag(repo, null)
                AppItem(repo, null, tag)
            }

            // Cache the result
            developerReposCache[cacheKey] = currentTime to appItems

            repoDao.insertRepos(repos)
            Result.success(appItems)
        } catch (e: HttpException) {
            handleHttpException(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch screenshots - simplified to reduce API calls
     */
    suspend fun getScreenshots(owner: String, repoName: String, defaultBranch: String?): Result<List<String>> = withContext(Dispatchers.IO) {
        val cacheKey = "$owner/$repoName"

        screenshotCache[cacheKey]?.let {
            return@withContext Result.success(it)
        }

        try {
            val screenshots = mutableListOf<String>()
            val branch = defaultBranch ?: "main"

            // Only check README for images to minimize API calls
            val readmeImages = getImagesFromReadme(owner, repoName, branch)
            screenshots.addAll(readmeImages)

            // Only if no images found in README, try one folder
            if (screenshots.isEmpty()) {
                try {
                    val rootContents = api.getRootContents(owner, repoName, branch)
                    val screenshotFolder = rootContents.find { content ->
                        content.type == "dir" && screenshotFolders.any { folder ->
                            content.name.equals(folder, ignoreCase = true)
                        }
                    }

                    screenshotFolder?.let { folder ->
                        val images = getImagesFromFolder(owner, repoName, folder.path, branch)
                        screenshots.addAll(images)
                    }
                } catch (e: Exception) {
                    // Ignore - just use README images
                }
            }

            val uniqueScreenshots = screenshots.distinct().take(8)
            screenshotCache[cacheKey] = uniqueScreenshots

            Result.success(uniqueScreenshots)
        } catch (e: Exception) {
            screenshotCache[cacheKey] = emptyList()
            Result.success(emptyList())
        }
    }

    private suspend fun getImagesFromFolder(owner: String, repoName: String, path: String, branch: String): List<String> {
        return try {
            val contents = api.getContents(owner, repoName, path, branch)
            contents.filter { content ->
                content.type == "file" && imageExtensions.any { ext ->
                    content.name.lowercase().endsWith(ext)
                }
            }.mapNotNull { it.downloadUrl }.take(4)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getImagesFromReadme(owner: String, repoName: String, branch: String): List<String> {
        return try {
            val readmeResult = getReadme(owner, repoName)
            val readme = readmeResult.getOrNull() ?: return emptyList()

            val imageRegex = Regex("""!\[.*?\]\((.*?)\)""")
            val htmlImgRegex = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

            val markdownImages = imageRegex.findAll(readme).map { it.groupValues[1] }.toList()
            val htmlImages = htmlImgRegex.findAll(readme).map { it.groupValues[1] }.toList()

            (markdownImages + htmlImages)
                .filter { url ->
                    imageExtensions.any { ext -> url.lowercase().contains(ext) }
                }
                .map { url ->
                    if (url.startsWith("http")) {
                        url
                    } else {
                        "https://raw.githubusercontent.com/$owner/$repoName/$branch/${url.trimStart('/')}"
                    }
                }
                .take(5)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getCachedRepos(): Flow<List<GitHubRepo>> = repoDao.getAllRepos()

    fun searchCachedRepos(query: String): Flow<List<GitHubRepo>> = repoDao.searchRepos(query)

    private fun <T> handleHttpException(e: HttpException): Result<T> {
        val message = when (e.code()) {
            429 -> "Rate limit exceeded. Please wait a few minutes or add a GitHub token in settings."
            403 -> "API rate limit reached. Add a GitHub token to increase limit (60 → 5000 requests/hour)."
            404 -> "Not found"
            500, 502, 503 -> "GitHub server error. Please try again."
            else -> "Network error: ${e.message()}"
        }
        return Result.failure(Exception(message))
    }

    private fun determineTag(repo: GitHubRepo, release: GitHubRelease?): AppTag? {
        if (repo.archived) return AppTag.ARCHIVED

        val now = Instant.now()
        val createdAt = try {
            Instant.parse(repo.createdAt)
        } catch (e: Exception) {
            null
        }

        if (createdAt != null && ChronoUnit.DAYS.between(createdAt, now) <= 30) {
            return AppTag.NEW
        }

        if (release != null) {
            val publishedAt = try {
                Instant.parse(release.publishedAt)
            } catch (e: Exception) {
                null
            }
            if (publishedAt != null && ChronoUnit.DAYS.between(publishedAt, now) <= 7) {
                return AppTag.UPDATED
            }
        }

        return null
    }

    fun clearCache() {
        releaseCache.clear()
        screenshotCache.clear()
        developerReposCache.clear()
        apkReposCache.clear()
        lastFetchTime = 0L
    }
}
