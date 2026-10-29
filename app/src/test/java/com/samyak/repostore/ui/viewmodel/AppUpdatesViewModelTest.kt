package com.samyak.repostore.ui.viewmodel

import com.samyak.repostore.data.db.CachedRepoDao
import com.samyak.repostore.data.db.FavoriteAppDao
import com.samyak.repostore.data.db.InstalledAppMappingDao
import com.samyak.repostore.data.model.FavoriteApp
import com.samyak.repostore.data.model.GitHubRelease
import com.samyak.repostore.data.model.InstalledAppMapping
import com.samyak.repostore.data.model.ReleaseAsset
import com.samyak.repostore.data.repository.GitHubRepository
import com.samyak.repostore.util.AbiProvider
import com.samyak.repostore.util.AppInstaller
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppUpdatesViewModelTest {

    private lateinit var repository: GitHubRepository
    private lateinit var installedAppMappingDao: InstalledAppMappingDao
    private lateinit var favoriteAppDao: FavoriteAppDao
    private lateinit var cachedRepoDao: CachedRepoDao
    private lateinit var appInstaller: AppInstaller

    @Before
    fun setUp() {
        mockkObject(AbiProvider)
        every { AbiProvider.supportedAbis() } returns arrayOf("arm64-v8a")

        repository = mockk(relaxed = true)
        installedAppMappingDao = mockk(relaxed = true)
        favoriteAppDao = mockk(relaxed = true)
        cachedRepoDao = mockk(relaxed = true)
        appInstaller = mockk(relaxed = true)

        every { installedAppMappingDao.getAllMappingsFlow() } returns flowOf(emptyList())
        coEvery { favoriteAppDao.getAllFavoritesSync() } returns emptyList()
        coEvery { cachedRepoDao.getAllCachedRepos() } returns emptyList()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun loadUpdates_returnsEmptyWhenNoAppsAreInstalled() = runBlocking {
        val viewModel = AppUpdatesViewModel(
            repository, installedAppMappingDao, favoriteAppDao, cachedRepoDao, appInstaller, autoRefresh = false
        )

        val updates = viewModel.loadUpdates()
        assertTrue("Expected empty list but got $updates", updates.isEmpty())
    }

    @Test
    fun loadUpdates_returnsUpdateWhenInstalledAppHasNewerRelease() = runBlocking {
        val mapping = InstalledAppMapping("FossifyOrg", "Calendar", "org.fossify.calendar")
        every { installedAppMappingDao.getAllMappingsFlow() } returns flowOf(listOf(mapping))
        every { appInstaller.isInstalled("org.fossify.calendar") } returns true
        every { appInstaller.getInstalledVersion("org.fossify.calendar") } returns "1.0.0"
        every { appInstaller.getAppLabel("org.fossify.calendar") } returns "Fossify Calendar"

        val release = createRelease(
            tagName = "v1.2.0",
            apkName = "calendar-1.2.0-arm64-v8a.apk"
        )
        coEvery { repository.getLatestRelease("FossifyOrg", "Calendar", forceRefresh = true) } returns Result.success(release)

        val viewModel = AppUpdatesViewModel(
            repository, installedAppMappingDao, favoriteAppDao, cachedRepoDao, appInstaller, autoRefresh = false
        )

        val updates = viewModel.loadUpdates()
        assertEquals(1, updates.size)
        assertEquals("Fossify Calendar", updates[0].appName)
        assertEquals("1.0.0", updates[0].installedVersion)
        assertEquals("v1.2.0", updates[0].latestVersion)
        assertEquals("calendar-1.2.0-arm64-v8a.apk", updates[0].asset.name)
    }

    @Test
    fun loadUpdates_returnsEmptyWhenInstalledAppIsAlreadyAtLatestVersion() = runBlocking {
        val mapping = InstalledAppMapping("FossifyOrg", "Calendar", "org.fossify.calendar")
        every { installedAppMappingDao.getAllMappingsFlow() } returns flowOf(listOf(mapping))
        every { appInstaller.isInstalled("org.fossify.calendar") } returns true
        every { appInstaller.getInstalledVersion("org.fossify.calendar") } returns "1.2.0"

        val release = createRelease(
            tagName = "v1.2.0",
            apkName = "calendar-1.2.0-arm64-v8a.apk"
        )
        coEvery { repository.getLatestRelease("FossifyOrg", "Calendar", forceRefresh = true) } returns Result.success(release)

        val viewModel = AppUpdatesViewModel(
            repository, installedAppMappingDao, favoriteAppDao, cachedRepoDao, appInstaller, autoRefresh = false
        )

        val updates = viewModel.loadUpdates()
        assertTrue("Expected empty list but got $updates", updates.isEmpty())
    }

    @Test
    fun loadUpdates_discoversUpdatesForFavoritedAppsThatAreInstalled() = runBlocking {
        val favorite = FavoriteApp(
            id = 1,
            fullName = "termux/termux-app",
            name = "termux-app",
            ownerLogin = "termux",
            ownerAvatarUrl = "",
            description = "Terminal emulator",
            stars = 25000,
            language = "Java"
        )
        coEvery { favoriteAppDao.getAllFavoritesSync() } returns listOf(favorite)
        coEvery { installedAppMappingDao.getPackageName("termux", "termux-app") } returns null
        every { appInstaller.findPackage("termux-app", "termux") } returns "com.termux"
        every { appInstaller.isInstalled("com.termux") } returns true
        every { appInstaller.getInstalledVersion("com.termux") } returns "0.117.0"
        every { appInstaller.getAppLabel("com.termux") } returns "Termux"

        val release = createRelease(
            tagName = "termux-v0.118.0",
            apkName = "termux-app_v0.118.0+github-debug_arm64-v8a.apk"
        )
        coEvery { repository.getLatestRelease("termux", "termux-app", forceRefresh = true) } returns Result.success(release)

        val viewModel = AppUpdatesViewModel(
            repository, installedAppMappingDao, favoriteAppDao, cachedRepoDao, appInstaller, autoRefresh = false
        )

        val updates = viewModel.loadUpdates()
        assertEquals(1, updates.size)
        assertEquals("Termux", updates[0].appName)
        assertEquals("0.117.0", updates[0].installedVersion)
        assertEquals("termux-v0.118.0", updates[0].latestVersion)
    }

    private fun createRelease(tagName: String, apkName: String): GitHubRelease {
        val asset = ReleaseAsset(
            id = 101,
            name = apkName,
            size = 45 * 1024 * 1024,
            downloadCount = 1000,
            downloadUrl = "https://github.com/downloads/$apkName",
            contentType = "application/vnd.android.package-archive"
        )
        return GitHubRelease(
            id = 1,
            tagName = tagName,
            name = "Release $tagName",
            body = "Notes",
            publishedAt = "2026-10-28T00:00:00Z",
            htmlUrl = "https://github.com",
            assets = listOf(asset),
            draft = false,
            prerelease = false
        )
    }
}
