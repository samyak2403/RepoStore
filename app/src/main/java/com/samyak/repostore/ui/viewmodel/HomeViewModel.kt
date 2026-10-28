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

class HomeViewModel(private val repository: GitHubRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Per-category loaded apps for home category sections
    private val _categoryApps = MutableStateFlow<Map<AppCategory, List<AppItem>>>(emptyMap())
    val categoryApps: StateFlow<Map<AppCategory, List<AppItem>>> = _categoryApps.asStateFlow()

    // All categories to display as sections (excluding ALL)
    val displayCategories: List<AppCategory> = AppCategory.entries.filter { it != AppCategory.ALL }

    // Track requested categories to avoid duplicate API calls
    private val requestedCategories = mutableSetOf<AppCategory>()

    private var loadJob: Job? = null

    init {
        loadFeaturedApps()
        loadInitialCategories()
    }

    /**
     * Load featured/popular apps for the carousel
     */
    private fun loadFeaturedApps() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = HomeUiState.Loading

            val result = repository.getPopularAndroidApps(1)
            result.fold(
                onSuccess = { apps ->
                    _uiState.value = if (apps.isEmpty()) {
                        HomeUiState.Empty
                    } else {
                        HomeUiState.Success(apps)
                    }
                },
                onFailure = { error ->
                    _uiState.value = HomeUiState.Error(error.message ?: "Failed to load apps")
                }
            )
        }
    }

    /**
     * Load apps for a specific category section if not already loaded
     */
    fun loadCategoryIfNeeded(category: AppCategory) {
        if (category == AppCategory.ALL) return
        if (requestedCategories.contains(category)) return
        requestedCategories.add(category)

        viewModelScope.launch {
            try {
                val result = repository.getAppsByCategory(category, 1)
                result.onSuccess { apps ->
                    if (apps.isNotEmpty()) {
                        _categoryApps.value = _categoryApps.value.toMutableMap().apply {
                            put(category, apps)
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently skip failed categories
            }
        }
    }

    /**
     * Load initial batch of category sections
     */
    private fun loadInitialCategories() {
        displayCategories.take(3).forEach { category ->
            loadCategoryIfNeeded(category)
        }
    }

    /**
     * Load more categories as user scrolls
     */
    fun loadMoreCategories() {
        val nextBatch = displayCategories.filter { !requestedCategories.contains(it) }.take(4)
        nextBatch.forEach { loadCategoryIfNeeded(it) }
    }

    fun refresh() {
        _categoryApps.value = emptyMap()
        requestedCategories.clear()
        loadFeaturedApps()
        loadInitialCategories()
    }

    fun retry() {
        refresh()
    }
}

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data object Empty : HomeUiState()
    data class LoadingMore(val currentApps: List<AppItem>) : HomeUiState()
    data class Success(val apps: List<AppItem>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModelFactory(private val repository: GitHubRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
