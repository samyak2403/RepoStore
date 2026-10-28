package com.samyak.repostore.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.data.model.GitHubRelease
import com.samyak.repostore.data.model.GitHubRepo
import com.samyak.repostore.data.repository.GitHubRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.samyak.repostore.util.AppNameFetcher

class DetailViewModel(private val repository: GitHubRepository) : ViewModel() {

    private companion object {
        const val SIMILAR_APPS_LIMIT = 12

        /** Topics too common in this store to imply similarity. */
        val GENERIC_TOPICS = setOf(
            "android", "android-app", "android-application", "app", "apps",
            "kotlin", "java", "flutter", "dart", "mobile", "mobile-app",
            "hacktoberfest", "open-source", "opensource", "foss", "free",
            "material-design", "material-you", "jetpack-compose"
        )
    }

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _readme = MutableStateFlow<String?>(null)
    val readme: StateFlow<String?> = _readme.asStateFlow()

    private val _screenshots = MutableStateFlow<List<String>>(emptyList())
    val screenshots: StateFlow<List<String>> = _screenshots.asStateFlow()

    private val _realAppName = MutableStateFlow<String?>(null)
    val realAppName: StateFlow<String?> = _realAppName.asStateFlow()

    private val _similarApps = MutableStateFlow<List<AppItem>>(emptyList())
    val similarApps: StateFlow<List<AppItem>> = _similarApps.asStateFlow()

    /**
     * Loads apps related to [repo] for the "Similar apps" shelf.
     *
     * Similarity comes from the repository's most distinctive topic, falling
     * back to its language. Generic topics like "android" or "hacktoberfest" are
     * skipped because nearly every repo in this store carries them, so they
     * would return an arbitrary list rather than related apps.
     *
     * Failures are swallowed: this is a supplementary shelf, and the section
     * simply stays hidden rather than surfacing an error over the app details.
     */
    fun loadSimilarApps(repo: GitHubRepo) {
        viewModelScope.launch {
            val query = buildSimilarQuery(repo) ?: return@launch

            repository.searchApps(query).onSuccess { apps ->
                _similarApps.value = apps
                    .filter { it.repo.id != repo.id }
                    .take(SIMILAR_APPS_LIMIT)
            }
        }
    }

    private fun buildSimilarQuery(repo: GitHubRepo): String? {
        val topic = repo.topics
            ?.firstOrNull { it.isNotBlank() && it.lowercase() !in GENERIC_TOPICS }
            ?.replace('-', ' ')

        return topic ?: repo.language?.takeIf { it.isNotBlank() }
    }

    fun loadAppDetails(owner: String, repoName: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading

            val repoResult = repository.getRepoDetails(owner, repoName, forceRefresh)

            repoResult.fold(
                onSuccess = { repo ->
                    // Show repo data immediately
                    _uiState.value = DetailUiState.Success(repo, null)

                    // Load release in background
                    loadRelease(owner, repoName, repo, forceRefresh)

                    // Load screenshots in background
                    loadScreenshots(owner, repoName, repo.defaultBranch)

                    // Load readme in background
                    loadReadme(owner, repoName)
                    
                    // Fetch real internal app name in background
                    fetchRealAppName(repo)
                },
                onFailure = { error ->
                    _uiState.value = DetailUiState.Error(error.message ?: "Failed to load app details")
                }
            )
        }
    }

    private fun loadRelease(owner: String, repoName: String, repo: GitHubRepo, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val releaseResult = repository.getLatestRelease(owner, repoName, forceRefresh)
            releaseResult.onSuccess { release ->
                _uiState.value = DetailUiState.Success(repo, release)
            }
        }
    }

    private fun loadScreenshots(owner: String, repoName: String, defaultBranch: String?) {
        viewModelScope.launch {
            val result = repository.getScreenshots(owner, repoName, defaultBranch)
            result.onSuccess { images ->
                _screenshots.value = images
            }
        }
    }

    private fun loadReadme(owner: String, repoName: String) {
        viewModelScope.launch {
            val result = repository.getReadme(owner, repoName)
            result.onSuccess { content ->
                _readme.value = content
            }
        }
    }

    private fun fetchRealAppName(repo: GitHubRepo) {
        viewModelScope.launch {
            val realName = AppNameFetcher.fetchRealName(repo)
            if (realName != null) {
                _realAppName.value = realName
            }
        }
    }

    fun retry(owner: String, repoName: String) {
        loadAppDetails(owner, repoName, forceRefresh = true)
    }
}

sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Success(val repo: GitHubRepo, val release: GitHubRelease?) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

class DetailViewModelFactory(private val repository: GitHubRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetailViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
