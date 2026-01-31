package com.samyak.repostore.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a user's favorite/saved app.
 * Stores essential information from GitHubRepo for offline display.
 */
@Entity(tableName = "favorite_apps")
data class FavoriteApp(
    @PrimaryKey val id: Long,
    val fullName: String,
    val name: String,
    val ownerLogin: String,
    val ownerAvatarUrl: String,
    val description: String?,
    val stars: Int,
    val language: String?,
    val addedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Creates a FavoriteApp from a GitHubRepo
         */
        fun fromRepo(repo: GitHubRepo): FavoriteApp {
            return FavoriteApp(
                id = repo.id,
                fullName = repo.fullName,
                name = repo.name,
                ownerLogin = repo.owner.login,
                ownerAvatarUrl = repo.owner.avatarUrl,
                description = repo.description,
                stars = repo.stars,
                language = repo.language
            )
        }
    }
}
