package com.samyak.repostore.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.samyak.repostore.data.db.FavoriteAppDao
import com.samyak.repostore.data.model.FavoriteApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class FavoriteUiState {
    data object Loading : FavoriteUiState()
    data class Success(val apps: List<FavoriteApp>) : FavoriteUiState()
    data object Empty : FavoriteUiState()
}

class FavoriteViewModel(
    private val favoriteAppDao: FavoriteAppDao
) : ViewModel() {

    val uiState: StateFlow<FavoriteUiState> = favoriteAppDao.getAllFavorites()
        .map { favorites ->
            if (favorites.isEmpty()) {
                FavoriteUiState.Empty
            } else {
                FavoriteUiState.Success(favorites)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FavoriteUiState.Loading
        )

    fun removeFavorite(repoId: Long) {
        viewModelScope.launch {
            favoriteAppDao.removeFavorite(repoId)
        }
    }
}

class FavoriteViewModelFactory(
    private val favoriteAppDao: FavoriteAppDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FavoriteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FavoriteViewModel(favoriteAppDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
