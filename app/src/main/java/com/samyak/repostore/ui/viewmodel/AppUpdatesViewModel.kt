package com.samyak.repostore.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.samyak.repostore.data.db.CachedRepoDao
import com.samyak.repostore.data.db.FavoriteAppDao
import com.samyak.repostore.data.db.InstalledAppMappingDao
import com.samyak.repostore.data.model.InstalledAppMapping
import com.samyak.repostore.data.model.ReleaseAsset
import com.samyak.repostore.data.repository.GitHubRepository
import com.samyak.repostore.util.AppInstaller
import com.samyak.repostore.util.ApkArchitectureHelper
import com.samyak.repostore.util.ApkSelectionResult
import com.samyak.repostore.util.VersionComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * A single app that has an update available.
 */
data class AppUpdate(
    val owner: String,
    val repo: String,
    val packageName: String,
    val appName: String,
    val installedVersion: String,
    val latestVersion: String,
    val asset: ReleaseAsset
) {
    val sizeBytes: Long get() = asset.size
}

sealed class AppUpdatesUiState {
    data object Loading : AppUpdatesUiState()
    data class Success(val updates: List<AppUpdate>) : AppUpdatesUiState()
    data object Empty : AppUpdatesUiState()
    data class Error(val message: String) : AppUpdatesUiState()
}

class AppUpdatesViewModel(
    private val repository: GitHubRepository,
    private val installedAppMappingDao: InstalledAppMappingDao,
    private val favoriteAppDao: FavoriteAppDao? = null,
    private val cachedRepoDao: CachedRepoDao? = null,
    private val appInstaller: AppInstaller,
    autoRefresh: Boolean = true
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppUpdatesUiState>(AppUpdatesUiState.Loading)
    val uiState: StateFlow<AppUpdatesUiState> = _uiState.asStateFlow()

    init {
        if (autoRefresh) {
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = AppUpdatesUiState.Loading
            try {
                val updates = loadUpdates()
                _uiState.value = if (updates.isEmpty()) {
                    AppUpdatesUiState.Empty
                } else {
                    AppUpdatesUiState.Success(updates)
                }
            } catch (e: Exception) {
                _uiState.value = AppUpdatesUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private data class Candidate(
        val owner: String,
        val repo: String,
        val packageName: String
    )

    internal suspend fun loadUpdates(): List<AppUpdate> = withContext(Dispatchers.IO) {
        val candidates = mutableMapOf<String, Candidate>()

        // 1. Existing tracked mappings (apps installed through RepoStore or previously detected)
        val mappings = try {
            installedAppMappingDao.getAllMappingsFlow().first()
        } catch (_: Exception) {
            emptyList()
        }
        for (m in mappings) {
            if (appInstaller.isInstalled(m.packageName)) {
                candidates[m.packageName] = Candidate(m.ownerName, m.repoName, m.packageName)
            }
        }

        // 2. Discover installed apps from user's Favorites
        favoriteAppDao?.let { favDao ->
            try {
                val favorites = favDao.getAllFavoritesSync()
                for (fav in favorites) {
                    val key = "${fav.ownerLogin}/${fav.name}".lowercase()
                    if (candidates.values.any { "${it.owner}/${it.repo}".lowercase() == key }) {
                        continue
                    }
                    val mappedPkg = installedAppMappingDao.getPackageName(fav.ownerLogin, fav.name)
                    val pkg = if (mappedPkg != null && appInstaller.isInstalled(mappedPkg)) {
                        mappedPkg
                    } else {
                        appInstaller.findPackage(fav.name, fav.ownerLogin)
                    }
                    if (pkg != null && appInstaller.isInstalled(pkg)) {
                        candidates[pkg] = Candidate(fav.ownerLogin, fav.name, pkg)
                        try {
                            installedAppMappingDao.saveMapping(
                                InstalledAppMapping(fav.ownerLogin, fav.name, pkg)
                            )
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Discover installed apps from Cached Repositories
        cachedRepoDao?.let { cacheDao ->
            try {
                val cachedRepos = cacheDao.getAllCachedRepos()
                for (cached in cachedRepos) {
                    val key = "${cached.ownerLogin}/${cached.name}".lowercase()
                    if (candidates.values.any { "${it.owner}/${it.repo}".lowercase() == key }) {
                        continue
                    }
                    val mappedPkg = installedAppMappingDao.getPackageName(cached.ownerLogin, cached.name)
                    val pkg = if (mappedPkg != null && appInstaller.isInstalled(mappedPkg)) {
                        mappedPkg
                    } else {
                        appInstaller.findPackage(cached.name, cached.ownerLogin)
                    }
                    if (pkg != null && appInstaller.isInstalled(pkg)) {
                        candidates[pkg] = Candidate(cached.ownerLogin, cached.name, pkg)
                        try {
                            installedAppMappingDao.saveMapping(
                                InstalledAppMapping(cached.ownerLogin, cached.name, pkg)
                            )
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        // Controlled concurrency to prevent hitting GitHub secondary rate limits
        val semaphore = Semaphore(3)

        candidates.values.map { candidate ->
            async {
                semaphore.withPermit {
                    try {
                        val packageName = candidate.packageName
                        if (!appInstaller.isInstalled(packageName)) return@withPermit null

                        val installedVersion = appInstaller.getInstalledVersion(packageName)
                            ?: return@withPermit null

                        val release = repository.getLatestRelease(
                            candidate.owner,
                            candidate.repo,
                            forceRefresh = true
                        ).getOrNull() ?: return@withPermit null

                        if (release.draft) return@withPermit null
                        val latestTag = release.tagName.takeIf { it.isNotBlank() } ?: return@withPermit null

                        if (!VersionComparator.isNewerVersion(installedVersion, latestTag)) {
                            return@withPermit null
                        }

                        val asset = when (val selection = ApkArchitectureHelper.selectBestApk(release.assets)) {
                            is ApkSelectionResult.Single -> selection.asset
                            is ApkSelectionResult.ExactMatch -> selection.asset
                            is ApkSelectionResult.Universal -> selection.asset
                            is ApkSelectionResult.Fallback -> selection.asset
                            is ApkSelectionResult.NoApkFound -> return@withPermit null
                        }

                        val appName = appInstaller.getAppLabel(packageName) ?: candidate.repo

                        AppUpdate(
                            owner = candidate.owner,
                            repo = candidate.repo,
                            packageName = packageName,
                            appName = appName,
                            installedVersion = installedVersion,
                            latestVersion = latestTag,
                            asset = asset
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull().sortedBy { it.appName.lowercase() }
    }
}

class AppUpdatesViewModelFactory(
    private val repository: GitHubRepository,
    private val installedAppMappingDao: InstalledAppMappingDao,
    private val favoriteAppDao: FavoriteAppDao? = null,
    private val cachedRepoDao: CachedRepoDao? = null,
    private val appInstaller: AppInstaller
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppUpdatesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppUpdatesViewModel(
                repository = repository,
                installedAppMappingDao = installedAppMappingDao,
                favoriteAppDao = favoriteAppDao,
                cachedRepoDao = cachedRepoDao,
                appInstaller = appInstaller
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
