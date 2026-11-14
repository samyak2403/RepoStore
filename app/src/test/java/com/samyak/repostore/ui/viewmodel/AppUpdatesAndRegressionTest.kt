package com.samyak.repostore.ui.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.samyak.repostore.data.db.AppDatabase
import com.samyak.repostore.data.db.CachedRepoDao
import com.samyak.repostore.data.db.FavoriteAppDao
import com.samyak.repostore.data.db.InstalledAppMappingDao
import com.samyak.repostore.data.model.AppCategory
import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.data.model.GitHubRepo
import com.samyak.repostore.data.model.InstalledAppMapping
import com.samyak.repostore.data.repository.GitHubRepository
import com.samyak.repostore.util.AppInstaller
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdatesAndRegressionTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `AppUpdatesViewModel emits Error instead of Empty when all release checks fail with rate limit`() = runBlocking {
        val repository = mockk<GitHubRepository>()
        val mappingDao = mockk<InstalledAppMappingDao>(relaxed = true)
        val favoriteDao = mockk<FavoriteAppDao>(relaxed = true)
        val cachedDao = mockk<CachedRepoDao>(relaxed = true)
        val installer = mockk<AppInstaller>(relaxed = true)

        val mapping = InstalledAppMapping("FossifyOrg", "Calendar", "org.fossify.calendar")
        every { mappingDao.getAllMappingsFlow() } returns flowOf(listOf(mapping))
        coEvery { favoriteDao.getAllFavoritesSync() } returns emptyList()
        coEvery { cachedDao.getAllCachedRepos() } returns emptyList()
        every { installer.isInstalled("org.fossify.calendar") } returns true
        every { installer.getInstalledVersion("org.fossify.calendar") } returns "1.0.0"

        coEvery {
            repository.getLatestRelease("FossifyOrg", "Calendar", forceRefresh = true)
        } returns Result.failure(
            Exception("API rate limit reached. Add a GitHub token to increase limit (60 → 5000 requests/hour).")
        )

        val viewModel = AppUpdatesViewModel(
            repository = repository,
            installedAppMappingDao = mappingDao,
            favoriteAppDao = favoriteDao,
            cachedRepoDao = cachedDao,
            appInstaller = installer,
            autoRefresh = true
        )

        val state = withTimeout(5_000L) {
            viewModel.uiState.first { it !is AppUpdatesUiState.Loading }
        }
        assertTrue("Expected Error state on rate limit, got $state", state is AppUpdatesUiState.Error)
        assertTrue((state as AppUpdatesUiState.Error).message.contains("rate limit", ignoreCase = true))
    }

    @Test
    fun `HomeViewModel allows retrying a failed category instead of permanently suppressing it`() = runBlocking {
        val repository = mockk<GitHubRepository>()
        coEvery { repository.getPopularAndroidApps(any()) } returns Result.success(emptyList())

        val sampleRepo = GitHubRepo(
            id = 42L,
            name = "ToolApp",
            fullName = "dev/ToolApp",
            description = "desc",
            htmlUrl = "https://github.com/dev/ToolApp",
            stars = 100,
            forks = 5,
            language = "Kotlin",
            updatedAt = "2026-01-01T00:00:00Z",
            createdAt = "2025-01-01T00:00:00Z",
            archived = false,
            owner = GitHubRepo.Owner("dev", "", ""),
            topics = listOf("android"),
            defaultBranch = "main"
        )
        val appItem = AppItem(sampleRepo, null, null, emptyList())

        var calls = 0
        coEvery { repository.getAppsByCategory(any(), any()) } coAnswers {
            val cat = firstArg<AppCategory>()
            if (cat == AppCategory.BROWSER) {
                calls++
                if (calls == 1) Result.failure(Exception("Temporary 403")) else Result.success(listOf(appItem))
            } else {
                Result.success(emptyList())
            }
        }

        val viewModel = HomeViewModel(repository)
        viewModel.loadCategoryIfNeeded(AppCategory.BROWSER)
        assertEquals(1, calls)
        assertEquals(null, viewModel.categoryApps.value[AppCategory.BROWSER])

        // Retry same category must succeed because failure removed it from requestedCategories
        viewModel.loadCategoryIfNeeded(AppCategory.BROWSER)
        val loaded = withTimeout(5_000L) {
            viewModel.categoryApps.first { (it[AppCategory.BROWSER]?.size ?: 0) == 1 }
        }
        assertEquals(2, calls)
        assertEquals(1, loaded[AppCategory.BROWSER]?.size)
    }

    @Test
    fun `InstalledAppMappingDao queries match owner and repo case-insensitively`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.installedAppMappingDao()
            dao.saveMapping(InstalledAppMapping("FossifyOrg", "Calendar", "org.fossify.calendar"))

            assertEquals("org.fossify.calendar", dao.getPackageName("fossifyorg", "CALENDAR"))
            assertEquals("org.fossify.calendar", dao.getPackageNameSync("FOSSIFYORG", "calendar"))

            dao.deleteMapping("FOSSIFYORG", "CALENDAR")
            assertEquals(null, dao.getPackageName("FossifyOrg", "Calendar"))
        } finally {
            db.close()
        }
    }
}
