package com.samyak.repostore.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

data class GitHubSearchResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("incomplete_results") val incompleteResults: Boolean,
    val items: List<GitHubRepo>
)

@Entity(tableName = "repositories")
data class GitHubRepo(
    @PrimaryKey val id: Long,
    val name: String,
    @SerializedName("full_name") val fullName: String,
    val description: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("stargazers_count") val stars: Int,
    @SerializedName("forks_count") val forks: Int,
    val language: String?,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("created_at") val createdAt: String,
    val archived: Boolean,
    val owner: Owner,
    val topics: List<String>?,
    @SerializedName("default_branch") val defaultBranch: String?
) {
    data class Owner(
        val login: String,
        @SerializedName("avatar_url") val avatarUrl: String,
        @SerializedName("html_url") val htmlUrl: String
    )
}

data class GitHubRelease(
    val id: Long,
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("published_at") val publishedAt: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val assets: List<ReleaseAsset>
)

data class ReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long,
    @SerializedName("download_count") val downloadCount: Int,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("content_type") val contentType: String
)

data class ReadmeResponse(
    val content: String,
    val encoding: String
)

// GitHub Contents API response
data class GitHubContent(
    val name: String,
    val path: String,
    val type: String, // "file" or "dir"
    @SerializedName("download_url") val downloadUrl: String?,
    @SerializedName("html_url") val htmlUrl: String,
    val size: Long?
)

// UI Models
data class AppItem(
    val repo: GitHubRepo,
    val latestRelease: GitHubRelease?,
    val tag: AppTag?
)

enum class AppTag {
    NEW, UPDATED, ARCHIVED
}

enum class AppCategory(val displayName: String, val queries: List<String>) {
    ALL("All", listOf("android app", "topic:android")),
    TOOLS("Tools", listOf("android tool", "android utility", "topic:android-tool")),
    PRODUCTIVITY("Productivity", listOf("android productivity", "android notes", "android todo", "topic:android-productivity")),
    GAMES("Games", listOf("android game", "topic:android-game", "mobile game android")),
    OPEN_SOURCE("Open Source", listOf("android foss", "android open-source", "topic:foss topic:android"));

    // For backward compatibility
    val query: String get() = queries.first()
}

data class GitHubUser(
    val id: Long,
    val login: String,
    val name: String?,
    @SerializedName("avatar_url") val avatarUrl: String,
    @SerializedName("html_url") val htmlUrl: String,
    val bio: String?,
    val company: String?,
    val location: String?,
    val email: String?,
    val blog: String?,
    @SerializedName("twitter_username") val twitterUsername: String?,
    @SerializedName("public_repos") val publicRepos: Int,
    @SerializedName("public_gists") val publicGists: Int,
    val followers: Int,
    val following: Int,
    @SerializedName("created_at") val createdAt: String
)
