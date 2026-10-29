package com.samyak.repostore.data.repository

import com.samyak.repostore.data.api.GitHubApi
import com.samyak.repostore.data.api.RetrofitClient
import com.samyak.repostore.data.db.CachedRepoDao
import com.samyak.repostore.data.db.RepoDao
import com.samyak.repostore.data.model.GitHubUser
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class GitHubRepositoryUserTest {

    private lateinit var repoDao: RepoDao
    private lateinit var cachedRepoDao: CachedRepoDao
    private lateinit var mockApi: GitHubApi
    private lateinit var repository: GitHubRepository

    @Before
    fun setUp() {
        repoDao = mockk(relaxed = true)
        cachedRepoDao = mockk(relaxed = true)
        mockApi = mockk(relaxed = true)

        mockkObject(RetrofitClient)
        coEvery { RetrofitClient.api } returns mockApi

        repository = GitHubRepository(repoDao, cachedRepoDao)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun getUser_handlesHttp403GracefullyWithoutThrowing() = runBlocking {
        val errorResponse = Response.error<GitHubUser>(
            403,
            "{\"message\":\"API rate limit exceeded\"}".toResponseBody("application/json".toMediaTypeOrNull())
        )
        coEvery { mockApi.getUser("samyak2403") } throws HttpException(errorResponse)

        val result = repository.getUser("samyak2403")

        assertTrue("Expected failure result on 403 rate limit", result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue("Expected rate limit message but got: $message", message.contains("rate limit", ignoreCase = true))
    }

    @Test
    fun getUser_returnsSuccessAndCachesUser() = runBlocking {
        val expectedUser = GitHubUser(
            id = 1,
            login = "samyak2403",
            name = "Samyak",
            avatarUrl = "https://example.com/avatar.png",
            htmlUrl = "https://github.com/samyak2403",
            bio = "Dev",
            company = null,
            location = "India",
            email = null,
            blog = null,
            twitterUsername = null,
            publicRepos = 42,
            publicGists = 0,
            followers = 100,
            following = 10,
            createdAt = "2020-01-01T00:00:00Z"
        )
        coEvery { mockApi.getUser("samyak2403") } returns expectedUser

        val result = repository.getUser("samyak2403")

        assertTrue(result.isSuccess)
        assertEquals("samyak2403", result.getOrNull()?.login)

        // Second call should return cached without hitting API again
        val cachedResult = repository.getUser("samyak2403")
        assertTrue(cachedResult.isSuccess)
        assertEquals("samyak2403", cachedResult.getOrNull()?.login)
    }

    @Test
    fun getContributors_handlesHttp403GracefullyWithoutThrowing() = runBlocking {
        val errorResponse = Response.error<List<GitHubUser>>(
            403,
            "{\"message\":\"API rate limit exceeded\"}".toResponseBody("application/json".toMediaTypeOrNull())
        )
        coEvery { mockApi.getContributors("samyak2403", "RepoStore") } throws HttpException(errorResponse)

        val result = repository.getContributors("samyak2403", "RepoStore")

        assertTrue("Expected failure result on 403 rate limit", result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue("Expected rate limit message but got: $message", message.contains("rate limit", ignoreCase = true))
    }
}
