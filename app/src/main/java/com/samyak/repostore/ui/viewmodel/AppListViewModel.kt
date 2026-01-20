package com.samyak.repostore.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.samyak.repostore.data.model.AppCategory
import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.data.repository.GitHubRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ListType {
    FEATURED,
    TRENDING,
    UPDATED,
    CATEGORY
}

class AppListViewModel(
    private val repository: GitHubRepository,
    private val listType: String,
    private val categoryName: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppListUiState>(AppListUiState.Loading)
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    private var currentPage = 1
    private val loadedApps = mutableListOf<AppItem>()
    private var loadJob: Job? = null
    private var isLoadingMore = false

    private val category: AppCategory? = categoryName?.let { 
        AppCategory.entries.find { cat -> cat.name == it }
    }

    init {
        loadApps()
    }

    private fun getQuery(): String {
        // Use simple keywords that work well with GitHub search
        return when {
            category == AppCategory.GAMES -> "android game"
            category != null -> category.query
            else -> when (ListType.valueOf(listType)) {
                ListType.FEATURED -> "android app"
                ListType.TRENDING -> "android app"
                ListType.UPDATED -> "android app"
                ListType.CATEGORY -> "android app"
            }
        }
    }

    private fun loadApps(refresh: Boolean = false) {
        if (refresh) {
            currentPage = 1
            loadedApps.clear()
            isLoadingMore = false
        }

        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _uiState.value = if (loadedApps.isEmpty()) {
                AppListUiState.Loading
            } else {
                AppListUiState.LoadingMore(loadedApps.toList())
            }

            val query = getQuery()
            val result = repository.searchApps(query, currentPage)

            result.fold(
                onSuccess = { apps ->
                    val sortedApps = when (ListType.valueOf(listType)) {
                        ListType.FEATURED -> apps.sortedByDescending { it.repo.stars }
                        ListType.TRENDING -> apps.sortedByDescending { it.repo.stars }
                        ListType.UPDATED -> apps.sortedByDescending { it.repo.updatedAt }
                        ListType.CATEGORY -> apps.sortedByDescending { it.repo.stars }
                    }

                    if (refresh || currentPage == 1) {
                        loadedApps.clear()
                    }
                    loadedApps.addAll(sortedApps)

                    _uiState.value = if (loadedApps.isEmpty()) {
                        AppListUiState.Empty
                    } else {
                        AppListUiState.Success(loadedApps.toList())
                    }
                    isLoadingMore = false
                },
                onFailure = { error ->
                    _uiState.value = if (loadedApps.isEmpty()) {
                        AppListUiState.Error(error.message ?: "Failed to load apps")
                    } else {
                        AppListUiState.Success(loadedApps.toList())
                    }
                    isLoadingMore = false
                }
            )
        }
    }

    fun loadMore() {
        if (isLoadingMore) return
        if (_uiState.value is AppListUiState.Loading) return

        isLoadingMore = true
        currentPage++

        viewModelScope.launch {
            _uiState.value = AppListUiState.LoadingMore(loadedApps.toList())

            val query = getQuery()
            val result = repository.searchApps(query, currentPage)

            result.fold(
                onSuccess = { apps ->
                    val sortedApps = when (ListType.valueOf(listType)) {
                        ListType.FEATURED -> apps.sortedByDescending { it.repo.stars }
                        ListType.TRENDING -> apps.sortedByDescending { it.repo.stars }
                        ListType.UPDATED -> apps.sortedByDescending { it.repo.updatedAt }
                        ListType.CATEGORY -> apps.sortedByDescending { it.repo.stars }
                    }
                    loadedApps.addAll(sortedApps)
                    _uiState.value = AppListUiState.Success(loadedApps.toList())
                },
                onFailure = {
                    _uiState.value = AppListUiState.Success(loadedApps.toList())
                }
            )
            isLoadingMore = false
        }
    }

    fun refresh() {
        loadApps(refresh = true)
    }

    fun retry() {
        loadApps(refresh = true)
    }
}

sealed class AppListUiState {
    data object Loading : AppListUiState()
    data object Empty : AppListUiState()
    data class LoadingMore(val currentApps: List<AppItem>) : AppListUiState()
    data class Success(val apps: List<AppItem>) : AppListUiState()
    data class Error(val message: String) : AppListUiState()
}

class AppListViewModelFactory(
    private val repository: GitHubRepository,
    private val listType: String,
    private val categoryName: String? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppListViewModel(repository, listType, categoryName) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
