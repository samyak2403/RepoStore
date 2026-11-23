package com.samyak.repostore.ui.viewmodel

import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.data.model.GitHubRepo
import com.samyak.repostore.data.repository.GitHubRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: GitHubRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createDummyAppItem(id: Long, name: String, stars: Int = 10): AppItem {
        val repo = GitHubRepo(
            id = id,
            name = name,
            fullName = "owner/$name",
            description = "Test Description",
            htmlUrl = "https://github.com/owner/$name",
            stars = stars,
            forks = 0,
            language = "Kotlin",
            updatedAt = "2026-01-01T00:00:00Z",
            createdAt = "2025-01-01T00:00:00Z",
            archived = false,
            owner = GitHubRepo.Owner(
                login = "owner",
                avatarUrl = "https://example.com/avatar.png",
                htmlUrl = "https://github.com/owner"
            ),
            topics = emptyList(),
            defaultBranch = "main"
        )
        return AppItem(repo = repo, latestRelease = null, tag = null)
    }

    @Test
    fun `loadMore stops requesting when page is empty and sets hasMore to false`() {
        val page1Apps = listOf(createDummyAppItem(1L, "App1"))
        coEvery { repository.searchApps(any(), page = 1) } returns Result.success(page1Apps)
        coEvery { repository.searchApps(any(), page = 2) } returns Result.success(emptyList())

        val viewModel = AppListViewModel(repository, ListType.TRENDING.name)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.hasMore)
        assertEquals(1, (viewModel.uiState.value as AppListUiState.Success).apps.size)

        // Trigger loadMore() for page 2
        viewModel.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        // Page 2 returned empty list -> hasMore must be false
        assertFalse(viewModel.hasMore)
        coVerify(exactly = 1) { repository.searchApps(any(), page = 2) }

        // Attempting to loadMore() again must NOT invoke repository
        viewModel.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { repository.searchApps(any(), page = 2) }
        coVerify(exactly = 0) { repository.searchApps(any(), page = 3) }
    }

    @Test
    fun `loadMore sets hasMore to false when all returned items are duplicates`() {
        val page1Apps = listOf(createDummyAppItem(1L, "App1"))
        coEvery { repository.searchApps(any(), page = 1) } returns Result.success(page1Apps)
        // Page 2 returns same item as page 1
        coEvery { repository.searchApps(any(), page = 2) } returns Result.success(page1Apps)

        val viewModel = AppListViewModel(repository, ListType.TRENDING.name)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.hasMore)
    }

    @Test
    fun `loadMore does not advance page on failure`() {
        val page1Apps = listOf(createDummyAppItem(1L, "App1"))
        coEvery { repository.searchApps(any(), page = 1) } returns Result.success(page1Apps)
        coEvery { repository.searchApps(any(), page = 2) } returns Result.failure(RuntimeException("Network error"))

        val viewModel = AppListViewModel(repository, ListType.TRENDING.name)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        // Should still be at page 1 content and can retry page 2
        assertEquals(1, (viewModel.uiState.value as AppListUiState.Success).apps.size)

        // Mock success on second attempt for page 2
        val page2Apps = listOf(createDummyAppItem(2L, "App2"))
        coEvery { repository.searchApps(any(), page = 2) } returns Result.success(page2Apps)

        viewModel.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        // Page 2 was re-requested, NOT page 3
        coVerify(exactly = 2) { repository.searchApps(any(), page = 2) }
        coVerify(exactly = 0) { repository.searchApps(any(), page = 3) }
        assertEquals(2, (viewModel.uiState.value as AppListUiState.Success).apps.size)
    }

    @Test
    fun `refresh resets hasMore to true and restarts from page 1`() {
        val page1Apps = listOf(createDummyAppItem(1L, "App1"))
        coEvery { repository.searchApps(any(), page = 1) } returns Result.success(page1Apps)
        coEvery { repository.searchApps(any(), page = 2) } returns Result.success(emptyList())

        val viewModel = AppListViewModel(repository, ListType.TRENDING.name)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.hasMore)

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.hasMore)
        coVerify(exactly = 2) { repository.searchApps(any(), page = 1) }
    }

    @Test
    fun `invalid listType string does not throw exception and defaults safely`() {
        coEvery { repository.searchApps(any(), page = 1) } returns Result.success(emptyList())

        val viewModel = AppListViewModel(repository, "UNKNOWN_INVALID_TYPE")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppListUiState.Empty)
    }
}
