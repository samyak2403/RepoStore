package com.samyak.repostore.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.samyak.repostore.data.db.FavoriteAppDao
import com.samyak.repostore.data.model.FavoriteApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class MyAppsUiState {
    data object Loading : MyAppsUiState()
    data class Success(val apps: List<FavoriteApp>) : MyAppsUiState()
    data object Empty : MyAppsUiState()
}

class MyAppsViewModel(
    private val favoriteAppDao: FavoriteAppDao,
    private val packageManager: PackageManager
) : ViewModel() {

    val uiState: StateFlow<MyAppsUiState> = favoriteAppDao.getAllFavorites()
        .map { favorites ->
            // Filter to show only installed apps
            val installedApps = favorites.filter { app ->
                isAppInstalled(app.name)
            }
            
            if (installedApps.isEmpty()) {
                MyAppsUiState.Empty
            } else {
                MyAppsUiState.Success(installedApps)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MyAppsUiState.Loading
        )

    private fun isAppInstalled(appName: String): Boolean {
        return try {
            // Try to find installed app by searching installed packages
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            installedApps.any { appInfo ->
                val label = packageManager.getApplicationLabel(appInfo).toString()
                label.equals(appName, ignoreCase = true) ||
                appInfo.packageName.contains(appName, ignoreCase = true)
            }
        } catch (e: Exception) {
            false
        }
    }

    fun removeFavorite(repoId: Long) {
        viewModelScope.launch {
            favoriteAppDao.removeFavorite(repoId)
        }
    }
}

class MyAppsViewModelFactory(
    private val favoriteAppDao: FavoriteAppDao,
    private val packageManager: PackageManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyAppsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MyAppsViewModel(favoriteAppDao, packageManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
