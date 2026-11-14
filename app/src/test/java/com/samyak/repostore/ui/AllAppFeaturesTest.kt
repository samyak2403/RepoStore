package com.samyak.repostore.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.samyak.repostore.data.db.AppDatabase
import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.data.model.FavoriteApp
import com.samyak.repostore.data.model.GitHubRelease
import com.samyak.repostore.data.model.GitHubRepo
import com.samyak.repostore.data.model.InstalledAppMapping
import com.samyak.repostore.data.model.ReleaseAsset
import com.samyak.repostore.data.model.SearchFilters
import com.samyak.repostore.data.prefs.ThemePreferences
import com.samyak.repostore.data.repository.GitHubRepository
import com.samyak.repostore.ui.viewmodel.DetailUiState
import com.samyak.repostore.ui.viewmodel.DetailViewModel
import com.samyak.repostore.ui.viewmodel.FavoriteUiState
import com.samyak.repostore.ui.viewmodel.FavoriteViewModel
import com.samyak.repostore.ui.viewmodel.SearchUiState
import com.samyak.repostore.ui.viewmodel.SearchViewModel
import com.samyak.repostore.util.AppNameFetcher
import com.samyak.repostore.util.SecurityChecker
import com.samyak.repostore.util.SecurityStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
class AllAppFeaturesTest {

    private lateinit var context: Context
    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer()
        server.start()
        AppNameFetcher.configureForTesting(server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        AppNameFetcher.resetForTesting()
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun sampleRepo(
        id: Long = 100L,
        owner: String = "samyak2403",
        name: String = "RepoStore",
        topics: List<String> = listOf("android", "kotlin", "app-store")
    ) = GitHubRepo(
        id = id,
        name = name,
        fullName = "$owner/$name",
        description = "Open-source Play Store for GitHub",
        htmlUrl = "https://github.com/$owner/$name",
        stars = 500,
        forks = 40,
        language = "Kotlin",
        updatedAt = "2026-11-01T00:00:00Z",
        createdAt = "2024-01-01T00:00:00Z",
        archived = false,
        owner = GitHubRepo.Owner(owner, "https://avatars/$owner", "https://github.com/$owner"),
        topics = topics,
        defaultBranch = "main"
    )

    private fun sampleRelease(tag: String = "v1.0.22") = GitHubRelease(
        id = 1L,
        tagName = tag,
        name = "Release $tag",
        body = "Bug fixes",
        publishedAt = "2026-11-01T00:00:00Z",
        prerelease = false,
        draft = false,
        htmlUrl = "https://github.com/samyak2403/RepoStore/releases/tag/$tag",
        assets = listOf(
            ReleaseAsset(
                id = 10L,
                name = "app-release.apk",
                size = 5_000_000L,
                downloadCount = 1234,
                downloadUrl = "https://github.com/samyak2403/RepoStore/releases/download/$tag/app-release.apk",
                contentType = "application/vnd.android.package-archive",
                digest = "sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
            )
        )
    )

    @Test
    fun `SecurityChecker distinguishes null, SHA-256 verified, and unverified assets`() = runBlocking {
        assertEquals(SecurityStatus.Unknown, SecurityChecker.check(null))

        val noHashAsset = ReleaseAsset(
            1L,
            "app.apk",
            1024L,
            10,
            "https://example.com/a.apk",
            "application/vnd.android.package-archive",
            digest = null
        )
        assertEquals(SecurityStatus.Unknown, SecurityChecker.check(noHashAsset))

        val hashedAsset = sampleRelease().assets.first()
        assertEquals(SecurityStatus.Verified, SecurityChecker.check(hashedAsset))
    }

    @Test
    fun `DetailViewModel loads repo details, screenshots, README, real app name, and similar apps skipping generic topics`() = runBlocking {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                return if (request.path.orEmpty().endsWith("/app/src/main/res/values/strings.xml")) {
                    MockResponse().setResponseCode(200)
                        .setBody("<resources><string name=\"app_name\">Repo Store</string></resources>")
                } else {
                    MockResponse().setResponseCode(404)
                }
            }
        }

        val repo = sampleRepo(id = 100L, topics = listOf("android", "kotlin", "music-player"))
        val release = sampleRelease()
        val similarRepo = sampleRepo(id = 200L, name = "RetroMusic", topics = listOf("music-player"))

        val repository = mockk<GitHubRepository>()
        coEvery { repository.getRepoDetails("samyak2403", "RepoStore", any()) } returns Result.success(repo)
        coEvery { repository.getCachedRelease("samyak2403", "RepoStore") } returns release
        coEvery { repository.getLatestRelease("samyak2403", "RepoStore", any()) } returns Result.success(release)
        coEvery { repository.getScreenshots("samyak2403", "RepoStore", "main") } returns Result.success(listOf("https://img/1.png"))
        coEvery { repository.getReadme("samyak2403", "RepoStore") } returns Result.success("# RepoStore README")
        coEvery { repository.searchApps("music player", any()) } returns Result.success(
            listOf(AppItem(repo, null, null), AppItem(similarRepo, null, null))
        )

        val vm = DetailViewModel(repository)
        vm.loadAppDetails("samyak2403", "RepoStore")
        vm.loadSimilarApps(repo)

        val detailState = withTimeout(5_000L) { vm.uiState.first { it is DetailUiState.Success } } as DetailUiState.Success
        assertEquals("RepoStore", detailState.repo.name)
        assertEquals("v1.0.22", detailState.release?.tagName)
        assertEquals(listOf("https://img/1.png"), withTimeout(5_000L) { vm.screenshots.first { it.isNotEmpty() } })
        assertEquals("# RepoStore README", withTimeout(5_000L) { vm.readme.first { it != null } })
        assertEquals("Repo Store", withTimeout(5_000L) { vm.realAppName.first { it != null } })

        val similar = withTimeout(5_000L) { vm.similarApps.first { it.isNotEmpty() } }
        assertEquals(1, similar.size)
        assertEquals(200L, similar.first().repo.id)
        coVerify(exactly = 1) { repository.searchApps("music player", any()) }
    }

    @Test
    fun `SearchViewModel handles empty filtered results without stale cache, URL search, and pagination`() = runTest(mainDispatcher) {
        val repo1 = sampleRepo(id = 1L, name = "NotePad")
        val repo2 = sampleRepo(id = 2L, name = "NotePadPro")
        val repository = mockk<GitHubRepository>()

        coEvery { repository.searchCachedRepos("note") } returns flowOf(listOf(repo1))
        coEvery { repository.searchCachedRepos(match { it != "note" }) } returns flowOf(emptyList())
        coEvery { repository.getSearchSuggestions(any()) } returns emptyList()
        coEvery { repository.advancedSearchApps("note", any(), 1) } answers {
            val filters = secondArg<SearchFilters>()
            if (filters.language == "Rust") {
                Result.success(
                    GitHubRepository.SearchResult(
                        emptyList(),
                        totalCount = 0,
                        hasNextPage = false,
                        query = "note",
                        filters = filters
                    )
                )
            } else {
                Result.success(
                    GitHubRepository.SearchResult(
                        listOf(AppItem(repo1, null, null)),
                        totalCount = 2,
                        hasNextPage = true,
                        query = "note",
                        filters = filters
                    )
                )
            }
        }
        coEvery { repository.advancedSearchApps("note", any(), 2) } answers {
            val filters = secondArg<SearchFilters>()
            Result.success(
                GitHubRepository.SearchResult(
                    listOf(AppItem(repo2, null, null)),
                    totalCount = 2,
                    hasNextPage = false,
                    query = "note",
                    filters = filters
                )
            )
        }
        coEvery { repository.getAppByUrl("https://github.com/samyak2403/RepoStore") } returns Result.success(
            AppItem(sampleRepo(id = 99L), null, null)
        )

        val vm = SearchViewModel(repository)
        vm.search("note")
        advanceTimeBy(600)
        advanceUntilIdle()
        val page1 = vm.uiState.value as SearchUiState.Success
        assertEquals(listOf(1L), page1.apps.map { it.repo.id })

        vm.loadMore()
        advanceUntilIdle()
        val page2 = vm.uiState.value as SearchUiState.Success
        assertEquals(listOf(1L, 2L), page2.apps.map { it.repo.id })

        // Applying restrictive filter with 0 network matches must show Empty (not unfiltered cache)
        vm.updateLanguageFilter("Rust")
        advanceTimeBy(600)
        advanceUntilIdle()
        assertTrue("Expected Empty state, got ${vm.uiState.value}", vm.uiState.value is SearchUiState.Empty)

        // Direct GitHub URL lookup resolves directly
        vm.resetFilters()
        vm.search("https://github.com/samyak2403/RepoStore")
        advanceTimeBy(600)
        advanceUntilIdle()
        val urlResult = vm.uiState.value as SearchUiState.Success
        assertEquals(99L, urlResult.apps.first().repo.id)
        assertEquals("RepoStore", urlResult.apps.first().repo.name)
    }

    @Test
    fun `FavoriteViewModel manages Room favorites, export import round-trip, and installed mapping mode`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val favoriteDao = db.favoriteAppDao()
            val mappingDao = db.installedAppMappingDao()
            val pm: PackageManager = context.packageManager

            val fav = FavoriteApp(
                id = 55L,
                fullName = "samyak2403/RepoStore",
                name = "RepoStore",
                ownerLogin = "samyak2403",
                ownerAvatarUrl = "https://avatars/1",
                description = "App Store",
                stars = 500,
                language = "Kotlin"
            )
            val vm = FavoriteViewModel(favoriteDao, mappingDao, pm)

            vm.importFavorites(listOf(fav))
            val exported = withTimeout(5_000L) {
                while (vm.getFavoritesForExport().isEmpty()) { kotlinx.coroutines.delay(20) }
                vm.getFavoritesForExport()
            }
            assertEquals(1, exported.size)
            assertEquals("RepoStore", exported.first().name)

            vm.setFilterMode(FavoriteViewModel.FilterMode.FAVORITES)
            val favState = withTimeout(5_000L) {
                vm.uiState.first { it is FavoriteUiState.Success }
            } as FavoriteUiState.Success
            assertEquals(1, favState.apps.size)

            // Map installed Robolectric test package -> ALL_INSTALLED surfaces it
            mappingDao.saveMapping(InstalledAppMapping("samyak2403", "RepoStore", context.packageName))
            vm.setFilterMode(FavoriteViewModel.FilterMode.ALL_INSTALLED)
            val installedState = withTimeout(5_000L) {
                vm.uiState.first { it is FavoriteUiState.Success && it.apps.any { a -> a.ownerAvatarUrl.startsWith("pkg:") } }
            } as FavoriteUiState.Success
            assertEquals("pkg:${context.packageName}", installedState.apps.first().ownerAvatarUrl)

            vm.removeFavorite(55L)
            val remaining = withTimeout(5_000L) {
                while (vm.getFavoritesForExport().isNotEmpty()) { kotlinx.coroutines.delay(20) }
                vm.getFavoritesForExport()
            }
            assertTrue(remaining.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `ThemePreferences persists System, For You, Dark, and Light modes`() {
        ThemePreferences.setThemeMode(context, ThemePreferences.THEME_DARK)
        assertEquals(ThemePreferences.THEME_DARK, ThemePreferences.getThemeMode(context))

        ThemePreferences.setThemeMode(context, ThemePreferences.THEME_LIGHT)
        assertEquals(ThemePreferences.THEME_LIGHT, ThemePreferences.getThemeMode(context))

        ThemePreferences.setThemeMode(context, ThemePreferences.THEME_SYSTEM)
        assertEquals(ThemePreferences.THEME_SYSTEM, ThemePreferences.getThemeMode(context))
    }
}
