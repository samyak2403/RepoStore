package com.samyak.repostore.ui.viewmodel

import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.samyak.repostore.data.db.FavoriteAppDao
import com.samyak.repostore.data.db.InstalledAppMappingDao
import com.samyak.repostore.data.model.FavoriteApp
import com.samyak.repostore.data.model.InstalledAppMapping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class FavoriteUiState {
    data object Loading : FavoriteUiState()
    data class Success(val apps: List<FavoriteApp>) : FavoriteUiState()
    data object Empty : FavoriteUiState()
}

class FavoriteViewModel(
    private val favoriteAppDao: FavoriteAppDao,
    private val installedAppMappingDao: InstalledAppMappingDao? = null,
    private val packageManager: PackageManager? = null
) : ViewModel() {

    enum class FilterMode { ALL_INSTALLED, FAVORITES }

    private val _filterMode = MutableStateFlow(FilterMode.ALL_INSTALLED)
    val filterMode: StateFlow<FilterMode> = _filterMode.asStateFlow()

    fun setFilterMode(mode: FilterMode) {
        _filterMode.value = mode
    }

    private val mappingsFlow = installedAppMappingDao?.getAllMappingsFlow()
        ?: kotlinx.coroutines.flow.flowOf(emptyList())

    val uiState: StateFlow<FavoriteUiState> = combine(
        favoriteAppDao.getAllFavorites(),
        mappingsFlow,
        _filterMode
    ) { favorites, mappings, mode ->
        when (mode) {
            FilterMode.FAVORITES -> {
                if (favorites.isEmpty()) {
                    FavoriteUiState.Empty
                } else {
                    FavoriteUiState.Success(favorites)
                }
            }
            FilterMode.ALL_INSTALLED -> {
                val installedApps = mutableListOf<FavoriteApp>()
                val installedIndex = loadInstalledAppsIndex()

                // 1. Process Favorites that are installed on the device
                favorites.forEach { app ->
                    val pkg = mappings.find {
                        it.ownerName.equals(app.ownerLogin, ignoreCase = true) &&
                        it.repoName.equals(app.name, ignoreCase = true)
                    }?.packageName
                    if (pkg != null && isPackageInstalled(pkg)) {
                        installedApps.add(app.copy(ownerAvatarUrl = "pkg:$pkg"))
                    } else if (isAppInstalledInIndex(app.name, installedIndex)) {
                        installedApps.add(app)
                    }
                }

                // 2. Process Mappings for tracked/installed apps (even if not favorited)
                mappings.forEach { mapping ->
                    if (installedApps.none {
                        it.ownerLogin.equals(mapping.ownerName, ignoreCase = true) &&
                        it.name.equals(mapping.repoName, ignoreCase = true)
                    }) {
                        if (isPackageInstalled(mapping.packageName)) {
                            installedApps.add(
                                FavoriteApp(
                                    id = (mapping.ownerName + "/" + mapping.repoName).hashCode().toLong(),
                                    fullName = "${mapping.ownerName}/${mapping.repoName}",
                                    name = mapping.repoName,
                                    ownerLogin = mapping.ownerName,
                                    ownerAvatarUrl = "pkg:${mapping.packageName}",
                                    description = "Installed via RepoStore",
                                    stars = 0,
                                    language = "Installed"
                                )
                            )
                        }
                    }
                }

                if (installedApps.isEmpty()) {
                    FavoriteUiState.Empty
                } else {
                    FavoriteUiState.Success(installedApps.distinctBy { "${it.ownerLogin}/${it.name}".lowercase() })
                }
            }
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FavoriteUiState.Loading
    )

    private data class InstalledAppEntry(val packageName: String, val label: String)

    private fun loadInstalledAppsIndex(): List<InstalledAppEntry> {
        val pm = packageManager ?: return emptyList()
        return try {
            val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            installed.map { appInfo ->
                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    appInfo.packageName
                }
                InstalledAppEntry(appInfo.packageName, label)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        val pm = packageManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isAppInstalledInIndex(appName: String, index: List<InstalledAppEntry>): Boolean {
        if (index.isEmpty()) return false
        return index.any { entry ->
            entry.label.equals(appName, ignoreCase = true) ||
                entry.packageName.contains(appName, ignoreCase = true)
        }
    }

    fun removeFavorite(repoId: Long) {
        viewModelScope.launch {
            favoriteAppDao.removeFavorite(repoId)
        }
    }

    suspend fun getFavoritesForExport(): List<FavoriteApp> {
        return favoriteAppDao.getAllFavoritesSync()
    }

    fun importFavorites(apps: List<FavoriteApp>) {
        viewModelScope.launch {
            favoriteAppDao.addFavorites(apps)
        }
    }
}

class FavoriteViewModelFactory(
    private val favoriteAppDao: FavoriteAppDao,
    private val installedAppMappingDao: InstalledAppMappingDao? = null,
    private val packageManager: PackageManager? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FavoriteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FavoriteViewModel(favoriteAppDao, installedAppMappingDao, packageManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
