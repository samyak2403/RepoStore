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
    private var canLoadMore = true

    val hasMore: Boolean
        get() = canLoadMore

    private val parsedListType: ListType = runCatching {
        ListType.valueOf(listType)
    }.getOrDefault(ListType.TRENDING)

    private val category: AppCategory? = categoryName?.let { 
        AppCategory.entries.find { cat -> cat.name == it }
    }

    init {
        loadApps()
    }

    private fun getQuery(): String {
        return when {
            category == AppCategory.GAMES -> "android game"
            category != null -> category.query
            else -> when (parsedListType) {
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
            canLoadMore = true
        }

        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _uiState.value = if (loadedApps.isEmpty()) {
                AppListUiState.Loading
            } else {
                AppListUiState.LoadingMore(loadedApps.toList())
            }

            val result = if (category != null && parsedListType == ListType.CATEGORY) {
                repository.getAppsByCategory(category, currentPage)
            } else {
                val query = getQuery()
                // searchApps now filters for APK releases automatically
                repository.searchApps(query, currentPage)
            }

            result.fold(
                onSuccess = { apps ->
                    if (refresh || currentPage == 1) {
                        loadedApps.clear()
                    }
                    val existingIds = loadedApps.map { it.repo.id }.toSet()
                    val newApps = apps.filter { it.repo.id !in existingIds }
                    loadedApps.addAll(newApps)

                    when (parsedListType) {
                        ListType.FEATURED -> loadedApps.sortByDescending { it.repo.stars }
                        ListType.TRENDING -> loadedApps.sortByDescending { it.repo.stars }
                        ListType.UPDATED -> loadedApps.sortByDescending { it.repo.updatedAt }
                        ListType.CATEGORY -> loadedApps.sortByDescending { it.repo.stars }
                    }

                    if (apps.isEmpty()) {
                        canLoadMore = false
                    }

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
        if (!canLoadMore || isLoadingMore) return
        if (_uiState.value is AppListUiState.Loading) return

        isLoadingMore = true
        val nextPage = currentPage + 1

        viewModelScope.launch {
            _uiState.value = AppListUiState.LoadingMore(loadedApps.toList())

            val result = if (category != null && parsedListType == ListType.CATEGORY) {
                repository.getAppsByCategory(category, nextPage)
            } else {
                val query = getQuery()
                repository.searchApps(query, nextPage)
            }

            result.fold(
                onSuccess = { apps ->
                    if (apps.isEmpty()) {
                        canLoadMore = false
                    } else {
                        val existingIds = loadedApps.map { it.repo.id }.toSet()
                        val newApps = apps.filter { it.repo.id !in existingIds }
                        if (newApps.isEmpty()) {
                            canLoadMore = false
                        } else {
                            currentPage = nextPage
                            loadedApps.addAll(newApps)
                        }
                    }

                    _uiState.value = AppListUiState.Success(loadedApps.toList())
                    isLoadingMore = false
                },
                onFailure = {
                    isLoadingMore = false
                    _uiState.value = AppListUiState.Success(loadedApps.toList())
                }
            )
        }
    }

    fun refresh() {
        canLoadMore = true
        loadApps(refresh = true)
    }

    fun retry() {
        canLoadMore = true
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
