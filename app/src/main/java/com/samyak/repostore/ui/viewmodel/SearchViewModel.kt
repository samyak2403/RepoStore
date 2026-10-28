package com.samyak.repostore.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.data.model.SearchFilters
import com.samyak.repostore.data.model.SortOption
import com.samyak.repostore.data.model.UpdatedWithin
import com.samyak.repostore.data.repository.GitHubRepository
import com.samyak.repostore.util.GitHubUrlParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: GitHubRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _filters = MutableStateFlow(SearchFilters.DEFAULT)
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()
    
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()
    
    private val _showFilters = MutableStateFlow(false)
    val showFilters: StateFlow<Boolean> = _showFilters.asStateFlow()
    
    private val _totalResults = MutableStateFlow(0)
    val totalResults: StateFlow<Int> = _totalResults.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null
    private var currentPage = 1
    private var hasNextPage = false
    private var isLoadingMore = false

    fun search(query: String) {
        _searchQuery.value = query
        currentPage = 1
        hasNextPage = false

        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.value = SearchUiState.Idle
            _suggestions.value = emptyList()
            return
        }

        if (query.length < 2) {
            // Fetch suggestions as user types
            fetchSuggestions(query)
            return
        }

        fetchSuggestions(query)
        performSearch(query, _filters.value, page = 1)
    }

    /**
     * Search with current query and updated filters
     */
    fun searchWithFilters(newFilters: SearchFilters) {
        _filters.value = newFilters
        currentPage = 1
        
        val query = _searchQuery.value
        if (query.isNotBlank() && query.length >= 2) {
            performSearch(query, newFilters, page = 1)
        }
    }

    /**
     * Load more results (pagination)
     */
    fun loadMore() {
        if (isLoadingMore || !hasNextPage) return
        
        val query = _searchQuery.value
        if (query.isBlank()) return
        
        val currentQuery = query // Capture current query to detect changes
        val currentFilters = _filters.value
        
        isLoadingMore = true
        currentPage++
        
        viewModelScope.launch {
            // Verify query hasn't changed during async operation
            if (_searchQuery.value != currentQuery) {
                isLoadingMore = false
                currentPage-- // Revert page increment
                return@launch
            }
            
            val result = repository.advancedSearchApps(currentQuery, currentFilters, currentPage)
            
            result.fold(
                onSuccess = { searchResult ->
                    // Double-check query hasn't changed before updating UI
                    if (_searchQuery.value == currentQuery) {
                        hasNextPage = searchResult.hasNextPage
                        _totalResults.value = searchResult.totalCount
                        
                        val currentState = _uiState.value
                        if (currentState is SearchUiState.Success) {
                            val combined = currentState.apps + searchResult.items
                            _uiState.value = SearchUiState.Success(combined.distinctBy { it.repo.id })
                        }
                    }
                },
                onFailure = { /* Silently fail on load more */ }
            )
            
            isLoadingMore = false
        }
    }

    private fun performSearch(query: String, filters: SearchFilters, page: Int) {
        // Cancel any in-flight search so rapid filter/query changes can't race, with a
        // stale request finishing last and overwriting the newest results.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500) // Debounce

            _uiState.value = SearchUiState.Loading

            // First try local cache for instant results
            val cachedResults = repository.searchCachedRepos(query).first()
            if (cachedResults.isNotEmpty()) {
                val appItems = cachedResults.map { repo ->
                    AppItem(repo, null, null)
                }
                _uiState.value = SearchUiState.Success(appItems)
            }

            // Check if query is a GitHub URL
            if (GitHubUrlParser.isGitHubUrl(query)) {
                val urlResult = repository.getAppByUrl(query)
                urlResult.fold(
                    onSuccess = { appItem ->
                        _totalResults.value = 1
                        hasNextPage = false
                        _uiState.value = SearchUiState.Success(listOf(appItem))
                        return@launch
                    },
                    onFailure = { /* Fall back to regular search if URL fetch fails */ }
                )
            }

            // Then fetch from API with filters
            val result = repository.advancedSearchApps(query, filters, page)

            result.fold(
                onSuccess = { searchResult ->
                    hasNextPage = searchResult.hasNextPage
                    _totalResults.value = searchResult.totalCount
                    
                    _uiState.value = if (searchResult.items.isEmpty()) {
                        if (cachedResults.isNotEmpty()) {
                            val appItems = cachedResults.map { repo ->
                                AppItem(repo, null, null)
                            }
                            SearchUiState.Success(appItems)
                        } else {
                            SearchUiState.Empty
                        }
                    } else {
                        SearchUiState.Success(searchResult.items)
                    }
                },
                onFailure = { error ->
                    if (cachedResults.isNotEmpty()) {
                        val appItems = cachedResults.map { repo ->
                            AppItem(repo, null, null)
                        }
                        _uiState.value = SearchUiState.Success(appItems)
                    } else {
                        _uiState.value = SearchUiState.Error(error.message ?: "Search failed")
                    }
                }
            )
        }
    }

    private fun fetchSuggestions(query: String) {
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            delay(200)
            val suggestions = repository.getSearchSuggestions(query)
            _suggestions.value = suggestions
        }
    }

    // Filter operations
    fun updateSortOption(sortOption: SortOption) {
        val newFilters = _filters.value.copy(sortBy = sortOption)
        searchWithFilters(newFilters)
    }

    fun updateLanguageFilter(language: String?) {
        val newFilters = _filters.value.copy(language = language)
        searchWithFilters(newFilters)
    }

    fun updateMinStars(minStars: Int?) {
        val newFilters = _filters.value.copy(minStars = minStars)
        searchWithFilters(newFilters)
    }

    fun updateUpdatedWithin(updatedWithin: UpdatedWithin?) {
        val newFilters = _filters.value.copy(updatedWithin = updatedWithin)
        searchWithFilters(newFilters)
    }

    fun toggleHasReleases(hasReleases: Boolean) {
        val newFilters = _filters.value.copy(hasReleases = hasReleases, hasApk = hasReleases)
        searchWithFilters(newFilters)
    }

    fun toggleShowFilters() {
        _showFilters.value = !_showFilters.value
    }

    fun resetFilters() {
        _filters.value = SearchFilters.DEFAULT
        val query = _searchQuery.value
        if (query.isNotBlank() && query.length >= 2) {
            performSearch(query, SearchFilters.DEFAULT, page = 1)
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _uiState.value = SearchUiState.Idle
        _suggestions.value = emptyList()
        _totalResults.value = 0
        currentPage = 1
        hasNextPage = false
        searchJob?.cancel()
        suggestionJob?.cancel()
    }
    
    fun hasActiveFilters(): Boolean {
        val default = SearchFilters.DEFAULT
        val current = _filters.value
        return current.sortBy != default.sortBy ||
               current.language != default.language ||
               current.minStars != default.minStars ||
               current.updatedWithin != default.updatedWithin ||
               (current.hasReleases != default.hasReleases || current.hasApk != default.hasApk)
    }
}

sealed class SearchUiState {
    data object Idle : SearchUiState()
    data object Loading : SearchUiState()
    data object Empty : SearchUiState()
    data class Success(val apps: List<AppItem>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

class SearchViewModelFactory(private val repository: GitHubRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
