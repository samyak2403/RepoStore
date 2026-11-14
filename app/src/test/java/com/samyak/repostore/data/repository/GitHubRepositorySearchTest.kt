package com.samyak.repostore.data.repository

import com.samyak.repostore.data.api.GitHubApi
import com.samyak.repostore.data.db.RepoDao
import com.samyak.repostore.data.model.GitHubRelease
import com.samyak.repostore.data.model.GitHubRepo
import com.samyak.repostore.data.model.GitHubSearchResponse
import com.samyak.repostore.data.model.ReleaseAsset
import com.samyak.repostore.data.model.SearchFilters
import com.samyak.repostore.ui.viewmodel.SearchUiState
import com.samyak.repostore.ui.viewmodel.SearchViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubRepositorySearchTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeRepo(
        id: Long,
        name: String,
        owner: String = "dev",
        description: String? = null,
        stars: Int = 10,
        language: String? = null,
        topics: List<String>? = null
    ) = GitHubRepo(
        id = id,
        name = name,
        fullName = "$owner/$name",
        description = description,
        htmlUrl = "https://github.com/$owner/$name",
        stars = stars,
        forks = 1,
        language = language,
        topics = topics,
        updatedAt = "2026-11-01T00:00:00Z",
        createdAt = "2026-01-01T00:00:00Z",
        archived = false,
        owner = GitHubRepo.Owner(login = owner, avatarUrl = "https://example.com/a.png", htmlUrl = "https://github.com/$owner"),
        defaultBranch = "main"
    )

    private fun makeApkRelease(tag: String = "v1.0.0") = GitHubRelease(
        id = 101L,
        tagName = tag,
        name = tag,
        body = "Release notes",
        publishedAt = "2026-11-01T00:00:00Z",
        prerelease = false,
        draft = false,
        assets = listOf(
            ReleaseAsset(
                id = 500L,
                name = "app-release.apk",
                size = 5_000_000L,
                downloadCount = 42,
                downloadUrl = "https://example.com/app-release.apk",
                contentType = "application/vnd.android.package-archive"
            )
        ),
        htmlUrl = "https://github.com/dev/NetVeill/releases/tag/$tag"
    )

    @Test
    fun `advancedSearchApps finds untagged NetVeill via automatic broad README fallback when topic_android returns empty`() = runBlocking {
        val repoDao = mockk<RepoDao>(relaxed = true)
        val api = mockk<GitHubApi>()

        val netVeillRepo = makeRepo(
            id = 4041L,
            name = "NetVeill",
            owner = "samyak2403",
            description = "Network visibility utility",
            stars = 12,
            language = "Kotlin",
            topics = emptyList() // Missing topic:android!
        )

        // Stage 1 (topic:android) returns 0 results
        coEvery {
            api.searchRepositories(
                query = match { it.contains("topic:android") && it.contains("NetVeill") },
                sort = any(),
                order = any(),
                perPage = any(),
                page = 1
            )
        } returns GitHubSearchResponse(totalCount = 0, incompleteResults = false, items = emptyList())

        // Stage 2 (broad without topic:android + README) finds NetVeill!
        coEvery {
            api.searchRepositories(
                query = match { !it.contains("topic:android") && it.contains("in:name,description,readme") && it.contains("NetVeill") },
                sort = any(),
                order = any(),
                perPage = any(),
                page = 1
            )
        } returns GitHubSearchResponse(totalCount = 1, incompleteResults = false, items = listOf(netVeillRepo))

        coEvery { api.getLatestRelease("samyak2403", "NetVeill") } returns makeApkRelease()

        val repository = GitHubRepository(repoDao = repoDao, apiProvider = { api })
        val result = repository.advancedSearchApps("NetVeill", SearchFilters.DEFAULT, page = 1)

        assertTrue(result.isSuccess)
        val searchResult = result.getOrThrow()
        assertEquals(1, searchResult.items.size)
        assertEquals("NetVeill", searchResult.items.first().repo.name)
        assertEquals("v1.0.0", searchResult.items.first().latestRelease?.tagName)
        coVerify(exactly = 2) { api.searchRepositories(any(), any(), any(), any(), 1) }
    }

    @Test
    fun `advancedSearchApps prioritizes Android and APK repos above non-APK matches and shows others below`() = runBlocking {
        val repoDao = mockk<RepoDao>(relaxed = true)
        val api = mockk<GitHubApi>()

        val netVeillWebDocs = makeRepo(
            id = 1L,
            name = "NetVeill-docs",
            owner = "docsorg",
            description = "Static website documentation",
            stars = 500,
            language = "HTML",
            topics = emptyList()
        )
        val netVeillAndroidApk = makeRepo(
            id = 2L,
            name = "NetVeill",
            owner = "samyak2403",
            description = "Android network monitor",
            stars = 15,
            language = "Kotlin",
            topics = listOf("network")
        )

        coEvery {
            api.searchRepositories(
                query = match { it.contains("in:name,description,readme") && !it.contains("topic:android") },
                sort = any(),
                order = any(),
                perPage = any(),
                page = 1
            )
        } returns GitHubSearchResponse(
            totalCount = 2,
            incompleteResults = false,
            items = listOf(netVeillWebDocs, netVeillAndroidApk)
        )

        coEvery { api.getLatestRelease("samyak2403", "NetVeill") } returns makeApkRelease()
        coEvery { api.getLatestRelease("docsorg", "NetVeill-docs") } throws HttpException(
            Response.error<Any>(404, "not found".toResponseBody("text/plain".toMediaType()))
        )
        coEvery { api.getReleases("docsorg", "NetVeill-docs", any()) } returns emptyList()

        val repository = GitHubRepository(repoDao = repoDao, apiProvider = { api })
        val filters = SearchFilters(androidOnly = false)
        val result = repository.advancedSearchApps("NetVeill", filters, page = 1)

        assertTrue(result.isSuccess)
        val items = result.getOrThrow().items
        assertEquals(2, items.size)
        // Android + APK repo ranks #1; non-APK repo remains visible below (#2)
        assertEquals("NetVeill", items[0].repo.name)
        assertEquals("NetVeill-docs", items[1].repo.name)
    }

    @Test
    fun `advancedSearchApps triggers bare fallback query when qualified queries return empty`() = runBlocking {
        val repoDao = mockk<RepoDao>(relaxed = true)
        val api = mockk<GitHubApi>()

        val netVeillHyphenated = makeRepo(
            id = 90L,
            name = "Net-Veill",
            owner = "samyak2403",
            description = null,
            stars = 3
        )

        coEvery {
            api.searchRepositories(
                query = match { it.contains("in:name,description") },
                sort = any(),
                order = any(),
                perPage = any(),
                page = 1
            )
        } returns GitHubSearchResponse(0, false, emptyList())

        coEvery {
            api.searchRepositories(
                query = match { it == "NetVeill archived:false" },
                sort = any(),
                order = any(),
                perPage = any(),
                page = 1
            )
        } returns GitHubSearchResponse(1, false, listOf(netVeillHyphenated))

        coEvery { api.getLatestRelease("samyak2403", "Net-Veill") } throws HttpException(
            Response.error<Any>(404, "none".toResponseBody("text/plain".toMediaType()))
        )
        coEvery { api.getReleases("samyak2403", "Net-Veill", any()) } returns emptyList()

        val repository = GitHubRepository(repoDao = repoDao, apiProvider = { api })
        val result = repository.advancedSearchApps("NetVeill", SearchFilters.DEFAULT, page = 1)

        assertTrue(result.isSuccess)
        assertEquals(listOf("Net-Veill"), result.getOrThrow().items.map { it.repo.name })
    }

    @Test
    fun `SearchViewModel resolves owner_repo shorthand and updates active filter state on toggleAndroidOnly`() = runTest(testDispatcher) {
        val repository = mockk<GitHubRepository>()
        val netVeillRepo = makeRepo(id = 777L, name = "NetVeill", owner = "samyak2403")
        val item = com.samyak.repostore.data.model.AppItem(netVeillRepo, makeApkRelease(), null)

        coEvery { repository.searchCachedRepos(any()) } returns flowOf(emptyList())
        coEvery { repository.getSearchSuggestions(any()) } returns emptyList()
        coEvery { repository.getAppByUrl("samyak2403/NetVeill") } returns Result.success(item)
        coEvery { repository.advancedSearchApps("NetVeill", any(), 1) } returns Result.success(
            GitHubRepository.SearchResult(
                items = listOf(item),
                totalCount = 1,
                hasNextPage = false,
                query = "NetVeill",
                filters = SearchFilters(androidOnly = false)
            )
        )

        val vm = SearchViewModel(repository)
        assertFalse(vm.hasActiveFilters())

        vm.search("samyak2403/NetVeill")
        advanceTimeBy(600)
        advanceUntilIdle()

        val directState = vm.uiState.value as SearchUiState.Success
        assertEquals("NetVeill", directState.apps.first().repo.name)
        coVerify(exactly = 1) { repository.getAppByUrl("samyak2403/NetVeill") }

        vm.search("NetVeill")
        vm.toggleAndroidOnly(false)
        advanceTimeBy(600)
        advanceUntilIdle()

        assertTrue(vm.hasActiveFilters())
        assertFalse(vm.filters.value.androidOnly)
        coVerify { repository.advancedSearchApps("NetVeill", match { !it.androidOnly }, 1) }

        vm.resetFilters()
        advanceTimeBy(600)
        advanceUntilIdle()
        assertFalse(vm.hasActiveFilters())
        assertTrue(vm.filters.value.androidOnly)
    }
}
