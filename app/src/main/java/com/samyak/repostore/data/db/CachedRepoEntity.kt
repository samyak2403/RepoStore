package com.samyak.repostore.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.data.model.AppTag
import com.samyak.repostore.data.model.GitHubRelease
import com.samyak.repostore.data.model.GitHubRepo
import com.samyak.repostore.data.model.ReleaseAsset
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "cached_repositories")
data class CachedRepoEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val fullName: String,
    val description: String?,
    val htmlUrl: String,
    val stars: Int,
    val forks: Int,
    val language: String?,
    val updatedAt: String,
    val createdAt: String,
    val archived: Boolean,
    val defaultBranch: String?,
    val ownerLogin: String,
    val ownerAvatarUrl: String,
    val ownerHtmlUrl: String,
    val topics: String?,
    val latestReleaseTag: String?,
    val latestReleaseName: String?,
    val latestReleaseBody: String?,
    val latestReleasePublishedAt: String?,
    val apkAssetSummaryJson: String?,
    val iconUrlsJson: String?,
    val categoryTag: String?,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis()
) {
    fun toAppItem(gson: Gson = Gson()): AppItem {
        val owner = GitHubRepo.Owner(
            login = ownerLogin,
            avatarUrl = ownerAvatarUrl,
            htmlUrl = ownerHtmlUrl
        )
        val parsedTopics: List<String>? = topics?.let {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson<List<String>>(it, type)
            } catch (e: Exception) {
                it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
            }
        }
        val repo = GitHubRepo(
            id = id,
            name = name,
            fullName = fullName,
            description = description,
            htmlUrl = htmlUrl,
            stars = stars,
            forks = forks,
            language = language,
            updatedAt = updatedAt,
            createdAt = createdAt,
            archived = archived,
            owner = owner,
            topics = parsedTopics,
            defaultBranch = defaultBranch
        )
        val parsedRelease: GitHubRelease? = latestReleaseTag?.let { tag ->
            val assets = apkAssetSummaryJson?.let { json ->
                try {
                    val type = object : TypeToken<List<ReleaseAsset>>() {}.type
                    gson.fromJson<List<ReleaseAsset>>(json, type)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
            GitHubRelease(
                id = id,
                tagName = tag,
                name = latestReleaseName,
                body = latestReleaseBody,
                htmlUrl = htmlUrl,
                publishedAt = latestReleasePublishedAt ?: updatedAt,
                prerelease = false,
                draft = false,
                assets = assets
            )
        }
        val parsedIcons: List<String> = iconUrlsJson?.let {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson<List<String>>(it, type)
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()

        return AppItem(
            repo = repo,
            latestRelease = parsedRelease,
            tag = categoryTag?.let {
                try {
                    AppTag.valueOf(it)
                } catch (e: Exception) {
                    null
                }
            },
            iconUrls = parsedIcons
        )
    }

    companion object {
        fun fromAppItem(appItem: AppItem, categoryTag: String? = null, gson: Gson = Gson()): CachedRepoEntity {
            val repo = appItem.repo
            val release = appItem.latestRelease
            val topicsJson = repo.topics?.let { gson.toJson(it) }
            val assetsJson = release?.assets?.let { gson.toJson(it) }
            val iconsJson = gson.toJson(appItem.iconUrls)

            return CachedRepoEntity(
                id = repo.id,
                name = repo.name,
                fullName = repo.fullName,
                description = repo.description,
                htmlUrl = repo.htmlUrl,
                stars = repo.stars,
                forks = repo.forks,
                language = repo.language,
                updatedAt = repo.updatedAt,
                createdAt = repo.createdAt,
                archived = repo.archived,
                defaultBranch = repo.defaultBranch,
                ownerLogin = repo.owner.login,
                ownerAvatarUrl = repo.owner.avatarUrl,
                ownerHtmlUrl = repo.owner.htmlUrl,
                topics = topicsJson,
                latestReleaseTag = release?.tagName,
                latestReleaseName = release?.name,
                latestReleaseBody = release?.body,
                latestReleasePublishedAt = release?.publishedAt,
                apkAssetSummaryJson = assetsJson,
                iconUrlsJson = iconsJson,
                categoryTag = categoryTag ?: appItem.tag?.name,
                lastUpdatedEpochMs = System.currentTimeMillis()
            )
        }
    }
}

