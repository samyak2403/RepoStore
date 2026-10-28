package com.samyak.repostore.data.db

import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.data.model.AppTag
import com.samyak.repostore.data.model.GitHubRelease
import com.samyak.repostore.data.model.GitHubRepo
import com.samyak.repostore.data.model.ReleaseAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedRepoEntityTest {

    @Test
    fun fromAppItem_and_toAppItem_roundTrip() {
        val repo = GitHubRepo(
            id = 12345L,
            name = "TestApp",
            fullName = "testowner/TestApp",
            description = "A sample open-source Android app",
            htmlUrl = "https://github.com/testowner/TestApp",
            stars = 420,
            forks = 69,
            language = "Kotlin",
            updatedAt = "2026-10-28T12:00:00Z",
            createdAt = "2025-01-01T00:00:00Z",
            archived = false,
            owner = GitHubRepo.Owner(
                login = "testowner",
                avatarUrl = "https://avatars.githubusercontent.com/u/123",
                htmlUrl = "https://github.com/testowner"
            ),
            topics = listOf("android", "kotlin", "sample"),
            defaultBranch = "main"
        )

        val asset = ReleaseAsset(
            id = 999L,
            name = "TestApp-v1.0.0.apk",
            size = 15_000_000L,
            downloadCount = 1337,
            downloadUrl = "https://github.com/testowner/TestApp/releases/download/v1.0.0/TestApp.apk",
            contentType = "application/vnd.android.package-archive"
        )

        val release = GitHubRelease(
            id = 888L,
            tagName = "v1.0.0",
            name = "v1.0.0 Initial Release",
            body = "Initial release notes",
            htmlUrl = "https://github.com/testowner/TestApp/releases/tag/v1.0.0",
            publishedAt = "2026-10-28T12:00:00Z",
            prerelease = false,
            draft = false,
            assets = listOf(asset)
        )

        val originalAppItem = AppItem(
            repo = repo,
            latestRelease = release,
            tag = AppTag.NEW,
            iconUrls = listOf("https://raw.githubusercontent.com/testowner/TestApp/main/icon.png")
        )

        val entity = CachedRepoEntity.fromAppItem(originalAppItem, categoryTag = "TRENDING")

        assertEquals(12345L, entity.id)
        assertEquals("TestApp", entity.name)
        assertEquals("testowner/TestApp", entity.fullName)
        assertEquals(420, entity.stars)
        assertEquals("v1.0.0", entity.latestReleaseTag)
        assertEquals("TRENDING", entity.categoryTag)
        assertTrue(entity.lastUpdatedEpochMs > 0)
        assertNotNull(entity.apkAssetSummaryJson)

        val restoredAppItem = entity.toAppItem()

        assertEquals(originalAppItem.repo.id, restoredAppItem.repo.id)
        assertEquals(originalAppItem.repo.name, restoredAppItem.repo.name)
        assertEquals(originalAppItem.repo.fullName, restoredAppItem.repo.fullName)
        assertEquals(originalAppItem.repo.stars, restoredAppItem.repo.stars)
        assertEquals(originalAppItem.repo.topics, restoredAppItem.repo.topics)
        assertNotNull(restoredAppItem.latestRelease)
        assertEquals("v1.0.0", restoredAppItem.latestRelease?.tagName)
        assertEquals(1, restoredAppItem.latestRelease?.assets?.size)
        assertEquals("TestApp-v1.0.0.apk", restoredAppItem.latestRelease?.assets?.first()?.name)
        assertEquals(1, restoredAppItem.iconUrls.size)
        assertEquals("https://raw.githubusercontent.com/testowner/TestApp/main/icon.png", restoredAppItem.iconUrls.first())
    }

    @Test
    fun toAppItem_handlesNullReleaseAndEmptyFieldsGracefully() {
        val entity = CachedRepoEntity(
            id = 54321L,
            name = "NoReleaseApp",
            fullName = "owner/NoReleaseApp",
            description = null,
            htmlUrl = "https://github.com/owner/NoReleaseApp",
            stars = 10,
            forks = 2,
            language = null,
            updatedAt = "2026-10-28T00:00:00Z",
            createdAt = "2026-10-01T00:00:00Z",
            archived = false,
            defaultBranch = null,
            ownerLogin = "owner",
            ownerAvatarUrl = "https://avatars.githubusercontent.com/u/456",
            ownerHtmlUrl = "https://github.com/owner",
            topics = null,
            latestReleaseTag = null,
            latestReleaseName = null,
            latestReleaseBody = null,
            latestReleasePublishedAt = null,
            apkAssetSummaryJson = null,
            iconUrlsJson = null,
            categoryTag = null
        )

        val appItem = entity.toAppItem()

        assertEquals("NoReleaseApp", appItem.repo.name)
        assertNull(appItem.latestRelease)
        assertNull(appItem.tag)
        assertTrue(appItem.iconUrls.isEmpty())
        assertNull(appItem.repo.topics)
    }
}

