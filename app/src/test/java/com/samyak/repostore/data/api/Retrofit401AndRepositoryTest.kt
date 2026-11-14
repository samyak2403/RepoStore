package com.samyak.repostore.data.api

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.samyak.repostore.data.auth.GitHubAuth
import com.samyak.repostore.data.auth.SecureTokenStorage
import com.samyak.repostore.data.auth.SessionResetReason
import com.samyak.repostore.data.db.RepoDao
import com.samyak.repostore.data.repository.GitHubRepository
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Retrofit401AndRepositoryTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private var secondServer: MockWebServer? = null

    private val testCryptoBackend = object : SecureTokenStorage.CryptoBackend {
        override fun encrypt(plainText: String): Pair<ByteArray, ByteArray> =
            plainText.toByteArray(Charsets.UTF_8) to byteArrayOf(1, 2, 3, 4)

        override fun decrypt(encryptedBytes: ByteArray, iv: ByteArray): String =
            String(encryptedBytes, Charsets.UTF_8)

        override fun resetKey() {}
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SecureTokenStorage.setCryptoBackendForTesting(testCryptoBackend)
        SecureTokenStorage.clearAll(context)
        server = MockWebServer()
        server.start()
        RetrofitClient.configureForTesting(server.url("/").toString(), context)
    }

    @After
    fun tearDown() {
        secondServer?.shutdown()
        server.shutdown()
        RetrofitClient.resetForTesting()
        SecureTokenStorage.clearAll(context)
        SecureTokenStorage.setCryptoBackendForTesting(null)
    }

    @Test
    fun `HTTP 401 clears invalid token, sets TOKEN_REVOKED, and retries unauthenticated`() = runBlocking {
        assertTrue(GitHubAuth.saveToken(context, "gho_revoked_token"))
        assertTrue(GitHubAuth.isSignedIn(context))

        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Bad credentials"}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": 1,
                  "login": "octocat",
                  "name": "The Octocat",
                  "avatar_url": "https://avatars/1",
                  "html_url": "https://github.com/octocat",
                  "bio": null,
                  "company": null,
                  "location": null,
                  "email": null,
                  "blog": null,
                  "twitter_username": null,
                  "public_repos": 8,
                  "public_gists": 0,
                  "followers": 100,
                  "following": 1,
                  "created_at": "2011-01-25T18:44:36Z"
                }
                """.trimIndent()
            )
        )

        val user = RetrofitClient.api.getUser("octocat")
        assertEquals("octocat", user.login)

        val firstReq = server.takeRequest()
        val secondReq = server.takeRequest()
        assertEquals("Bearer gho_revoked_token", firstReq.getHeader("Authorization"))
        assertNull(secondReq.getHeader("Authorization"))

        assertFalse(GitHubAuth.isSignedIn(context))
        assertEquals(SessionResetReason.TOKEN_REVOKED, GitHubAuth.getSessionResetReason(context))
    }

    @Test
    fun `GitHubRepository picks up refreshed RetrofitClient and sends ref query parameter for screenshots`() = runBlocking {
        val repoDao = mockk<RepoDao>(relaxed = true)
        val repository = GitHubRepository(repoDao)

        // Point RetrofitClient to a fresh MockWebServer AFTER repository creation
        // to verify GitHubRepository dynamically reads RetrofitClient.api (no stale ref).
        secondServer = MockWebServer().apply { start() }
        RetrofitClient.configureForTesting(secondServer!!.url("/").toString(), context)

        // 1. README without images
        val emptyReadme = Base64.encodeToString("No images here".toByteArray(), Base64.DEFAULT)
        secondServer!!.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"content": ${com.google.gson.Gson().toJson(emptyReadme)}, "encoding": "base64"}"""
            )
        )
        // 2. Root contents has screenshots dir
        secondServer!!.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"name":"screenshots","path":"screenshots","type":"dir","download_url":null,"html_url":"https://github.com","size":null}]"""
            )
        )
        // 3. Folder contents has shot.png
        secondServer!!.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"name":"shot.png","path":"screenshots/shot.png","type":"file","download_url":"https://raw/shot.png","html_url":"https://github.com","size":123}]"""
            )
        )

        val result = repository.getScreenshots("samyak2403", "RepoStore", "develop")
        assertTrue(result.isSuccess)
        assertEquals(listOf("https://raw/shot.png"), result.getOrThrow())

        val req1 = secondServer!!.takeRequest()
        val req2 = secondServer!!.takeRequest()
        val req3 = secondServer!!.takeRequest()
        assertEquals("/repos/samyak2403/RepoStore/readme", req1.path)
        assertEquals("/repos/samyak2403/RepoStore/contents?ref=develop", req2.path)
        assertEquals("/repos/samyak2403/RepoStore/contents/screenshots?ref=develop", req3.path)
    }

    @Test
    fun `getReadme handles malformed base64 safely as Result failure instead of throwing`() = runBlocking {
        val repoDao = mockk<RepoDao>(relaxed = true)
        val repository = GitHubRepository(repoDao)

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"content":"%%%NOT_BASE64%%%","encoding":"base64"}"""
            )
        )

        val result = repository.getReadme("owner", "repo")
        assertTrue(result.isFailure)
    }
}
