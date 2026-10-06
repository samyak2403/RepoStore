package com.samyak.repostore.data.repository

import com.samyak.repostore.data.api.RetrofitClient
import com.samyak.repostore.data.db.RepoDao
import com.samyak.repostore.data.model.*
import com.samyak.repostore.util.GitHubUrlParser
import com.samyak.gitcore.util.IconResolver
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
import java.util.concurrent.ConcurrentHashMap

class GitHubRepository(private val repoDao: RepoDao) {

    private val api = RetrofitClient.api

    // In-memory cache. These are read/written from many parallel IO coroutines
    // (e.g. filterReposWithApk fans out with async{}), so they must be thread-safe.
    // ConcurrentHashMap disallows null values, so repos that are known to have NO
    // release (HTTP 404) are tracked in a separate concurrent set instead of a null entry.
    private val releaseCache = ConcurrentHashMap<String, GitHubRelease>()
    private val noReleaseRepos: MutableSet<String> =
        java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val totalDownloadsCache = ConcurrentHashMap<String, Long>()
    private val screenshotCache = ConcurrentHashMap<String, List<String>>()
    private val developerReposCache = ConcurrentHashMap<String, Pair<Long, List<AppItem>>>()
    private val apkReposCache = ConcurrentHashMap<String, Boolean>() // Cache for repos with APK
    
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
                        AppItem(repo, release, tag, IconResolver.resolve(repo.owner.login, repo.name, repo.defaultBranch, repo.language))
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

    /**
     * Search result wrapper with metadata
     */
    data class SearchResult(
        val items: List<AppItem>,
        val totalCount: Int,
        val hasNextPage: Boolean,
        val query: String,
        val filters: SearchFilters
    )

    suspend fun searchApps(query: String, page: Int = 1): Result<List<AppItem>> = withContext(Dispatchers.IO) {
        try {
            // Search in name, description, and readme
            val searchQuery = "$query in:name,description topic:android"
//            val searchQuery = "$query in:name,description,readme"
            val response = api.searchRepositories(searchQuery, perPage = 40, page = page)

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

    /**
     * Advanced search with filters for language, stars, sort order, etc.
     * @param query User's search term
     * @param filters Advanced search filters
     * @param page Page number for pagination
     * @return List of matching apps with APK releases
     */
    suspend fun advancedSearchApps(
        query: String, 
        filters: SearchFilters = SearchFilters.DEFAULT,
        page: Int = 1
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val searchQuery = filters.buildQuery(query)
            
            // For "Best Match", pass null to use GitHub's relevance-based sorting
            // Otherwise, use the specified sort option
            val sortBy = filters.sortBy.apiValue.ifEmpty { null }
            val order = "desc"
            
            val response = api.searchRepositories(
                query = searchQuery,
                sort = sortBy ?: "stars", // Default to stars for Best Match (GitHub's default)
                order = order,
                perPage = 40,
                page = page
            )

            // Always cache the repos for future searches
            if (response.items.isNotEmpty()) {
                repoDao.insertRepos(response.items)
            }

            // Filter to only repos with APK releases if required
            val appItems = if (filters.hasReleases) {
                val filtered = filterReposWithApk(response.items)
                // If APK filtering returns empty but we have results, 
                // fall back to showing unfiltered results so user sees something
                if (filtered.isEmpty() && response.items.isNotEmpty()) {
                    response.items.map { repo ->
                        val tag = determineTag(repo, null)
                        AppItem(repo, null, tag, IconResolver.resolve(repo.owner.login, repo.name, repo.defaultBranch, repo.language))
                    }
                } else {
                    filtered
                }
            } else {
                response.items.map { repo ->
                    val tag = determineTag(repo, null)
                    AppItem(repo, null, tag, IconResolver.resolve(repo.owner.login, repo.name, repo.defaultBranch, repo.language))
                }
            }
            
            // Apply Play Store-like relevance scoring and sort
            val rankedItems = rankByRelevance(appItems, query)
            
            Result.success(SearchResult(
                items = rankedItems,
                totalCount = response.totalCount,
                hasNextPage = response.items.size >= 40,
                query = query,
                filters = filters
            ))
        } catch (e: HttpException) {
            handleHttpException(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch a single app by its GitHub URL
     */
    suspend fun getAppByUrl(url: String): Result<AppItem> = withContext(Dispatchers.IO) {
        try {
            val repoInfo = GitHubUrlParser.parse(url) 
                ?: return@withContext Result.failure(Exception("Invalid GitHub URL"))
            
            val repoResult = getRepoDetails(repoInfo.owner, repoInfo.name)
            val repo = repoResult.getOrThrow()
            
            val releaseResult = getLatestRelease(repo.owner.login, repo.name)
            val release = releaseResult.getOrNull()
            
            val tag = determineTag(repo, release)
            Result.success(AppItem(repo, release, tag, IconResolver.resolve(repo.owner.login, repo.name, repo.defaultBranch, repo.language)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Rank search results by relevance score (Play Store-like algorithm)
     * Scoring factors:
     * - Exact name match: +100 points
     * - Name starts with query: +50 points
     * - Name contains query: +25 points
     * - Description contains query: +10 points
     * - Star count bonus: log10(stars) * 5 points
     * - Android topic bonus: +15 points
     * - Has releases (APK): +20 points
     */
    private fun rankByRelevance(items: List<AppItem>, query: String): List<AppItem> {
        if (query.isBlank()) return items
        
        val queryLower = query.lowercase().trim()
        val queryWords = queryLower.split(" ").filter { it.isNotBlank() }
        
        return items.sortedByDescending { item ->
            var score = 0.0
            val repo = item.repo
            val nameLower = repo.name.lowercase()
            val descLower = repo.description?.lowercase() ?: ""
            
            // Exact name match (highest priority)
            if (nameLower == queryLower) {
                score += 100
            }
            // Name starts with query
            else if (nameLower.startsWith(queryLower)) {
                score += 50
            }
            // Name contains query as whole word
            else if (nameLower.contains(queryLower)) {
                score += 25
            }
            // Name contains all query words
            else if (queryWords.all { word -> nameLower.contains(word) }) {
                score += 20
            }
            
            // Description contains query
            if (descLower.contains(queryLower)) {
                score += 10
            }
            // Description contains all query words
            else if (queryWords.all { word -> descLower.contains(word) }) {
                score += 5
            }
            
            // Star count bonus (logarithmic to prevent mega-repos from dominating)
            if (repo.stars > 0) {
                score += kotlin.math.log10(repo.stars.toDouble()) * 5
            }
            
            // Android topic bonus
            repo.topics?.let { topics ->
                if (topics.any { it.lowercase().contains("android") }) {
                    score += 15
                }
            }
            
            // Has releases (APK) bonus
            if (item.latestRelease != null) {
                score += 20
            }
            
            score
        }
    }

    /**
     * Get search suggestions based on partial query
     * Uses cached repos for quick suggestions
     */
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        
        try {
            val cachedRepos = repoDao.searchRepos(query).first()
            cachedRepos.take(5).map { it.name }
        } catch (e: Exception) {
            emptyList()
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
                val allRepos = mutableListOf<GitHubRepo>()
                val seenIds = mutableSetOf<Long>()

                if (category == AppCategory.TRENDING) {
                    // For trending: use time-based filter for recently active popular Android apps
                    val thirtyDaysAgo = java.time.LocalDate.now().minusDays(30)
                    val dateFilter = "pushed:>${thirtyDaysAgo}"
                    
                    // Multiple queries to catch diverse trending Android apps
                    val queries = listOf(
                        "repo:libre-tube/libretube",
                        "topic:android $dateFilter stars:>500",
                        "topic:android-app $dateFilter stars:>100",
                        "android app $dateFilter stars:>200 language:Kotlin",
                        "android app $dateFilter stars:>200 language:Java"
                    )
                    
                    for (query in queries) {
                        try {
                            val response = api.searchRepositories(
                                query = query, 
                                sort = "stars", 
                                perPage = 30, 
                                page = page
                            )
                            for (repo in response.items) {
                                if (seenIds.add(repo.id)) {
                                    allRepos.add(repo)
                                }
                            }
                        } catch (e: Exception) {
                            // Continue with next query if one fails
                        }
                    }

                    if (allRepos.isEmpty()) {
                        return@withContext Result.success(emptyList())
                    }

                    // Filter to only repos with APK releases
                    val appItems = filterReposWithApk(allRepos)

                    if (appItems.isNotEmpty()) {
                        repoDao.insertRepos(allRepos)
                    }

                    Result.success(appItems.sortedByDescending { it.repo.stars })
                } else {
                    // For other categories: try each query and merge results
                    for (query in category.queries) {
                        try {
                            val searchQuery = "$query stars:>50"
                            val response = api.searchRepositories(searchQuery, perPage = 30, page = page)
                            for (repo in response.items) {
                                if (seenIds.add(repo.id)) {
                                    allRepos.add(repo)
                                }
                            }
                        } catch (e: Exception) {
                            // Continue with next query if one fails
                        }
                        if (allRepos.size >= 30) break
                    }

                    if (allRepos.isEmpty()) {
                        return@withContext Result.success(emptyList())
                    }

                    // Filter to only repos with APK releases
                    val appItems = filterReposWithApk(allRepos)

                    if (appItems.isNotEmpty()) {
                        repoDao.insertRepos(allRepos)
                    }
                    
                    Result.success(appItems.sortedByDescending { it.repo.stars })
                }
            } catch (e: HttpException) {
                handleHttpException(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getRepoDetails(owner: String, repoName: String): Result<GitHubRepo> = withContext(Dispatchers.IO) {
        try {
            // Always revalidate with GitHub. Returning the local row directly kept the
            // description the app first saw — editing the repository description upstream
            // never reached the detail screen. OkHttp serves this call from its cache
            // while the response is still fresh and from disk while offline, so nothing
            // is wasted on the hot path.
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

    /**
     * Total number of APK/asset downloads across ALL releases (all versions) of a repo.
     * Walks every page of the releases API and sums each release asset's download count.
     */
    suspend fun getTotalDownloads(owner: String, repoName: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val cacheKey = "$owner/$repoName"
            totalDownloadsCache[cacheKey]?.let {
                return@withContext Result.success(it)
            }

            var total = 0L
            var page = 1
            val perPage = 100
            // Safety cap: 20 pages = up to 2000 releases, plenty for any real repo.
            while (page <= 20) {
                val releases = api.getReleases(owner, repoName, perPage = perPage, page = page)
                if (releases.isEmpty()) break
                total += releases.sumOf { release ->
                    release.assets.sumOf { it.downloadCount.coerceAtLeast(0).toLong() }
                }
                if (releases.size < perPage) break
                page++
            }

            totalDownloadsCache[cacheKey] = total
            Result.success(total)
        } catch (e: HttpException) {
            handleHttpException(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLatestRelease(
        owner: String,
        repoName: String,
        forceRefresh: Boolean = false
    ): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        try {
            val cacheKey = "$owner/$repoName"

            // When forceRefresh is requested (e.g. the App Updates screen checking for
            // new versions) we skip the in-memory cache and always hit the network so
            // freshly published releases show up immediately.
            if (!forceRefresh) {
                releaseCache[cacheKey]?.let {
                    return@withContext Result.success(it)
                }
                // Remembered as having no release — avoid re-hitting the network.
                if (noReleaseRepos.contains(cacheKey)) {
                    return@withContext Result.failure(NoSuchElementException("No release found for $cacheKey"))
                }
            }

            val release = api.getLatestRelease(owner, repoName)
            releaseCache[cacheKey] = release
            noReleaseRepos.remove(cacheKey)
            Result.success(release)
        } catch (e: HttpException) {
            if (e.code() == 404) {
                noReleaseRepos.add("$owner/$repoName")
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
                AppItem(repo, null, tag, IconResolver.resolve(repo.owner.login, repo.name, repo.defaultBranch, repo.language))
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
        noReleaseRepos.clear()
        screenshotCache.clear()
        developerReposCache.clear()
        apkReposCache.clear()
        lastFetchTime = 0L
    }
}
