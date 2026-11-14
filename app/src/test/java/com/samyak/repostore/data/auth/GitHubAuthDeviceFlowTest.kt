package com.samyak.repostore.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class GitHubAuthDeviceFlowTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val fastClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        GitHubAuth.configureForTesting(
            okHttpClient = fastClient,
            testClientId = "test_client_id",
            deviceCodeEndpoint = server.url("/login/device/code").toString(),
            accessTokenEndpoint = server.url("/login/oauth/access_token").toString(),
            userApiEndpoint = server.url("/user").toString(),
            pollFloorMs = 20L,
            slowDownStepMs = 40L
        )
    }

    @After
    fun tearDown() {
        GitHubAuth.resetForTesting()
        server.shutdown()
    }

    @Test
    fun `requestDeviceCodeResult parses valid GitHub response`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "device_code": "dc_123",
                  "user_code": "WXYZ-9876",
                  "verification_uri": "https://github.com/login/device",
                  "expires_in": 900,
                  "interval": 5
                }
                """.trimIndent()
            )
        )

        val result = GitHubAuth.requestDeviceCodeResult()
        assertTrue(result is GitHubAuth.DeviceCodeResult.Success)
        val device = (result as GitHubAuth.DeviceCodeResult.Success).response
        assertEquals("dc_123", device.deviceCode)
        assertEquals("WXYZ-9876", device.userCode)
        assertEquals("https://github.com/login/device", device.verificationUri)
    }

    @Test
    fun `pollForTokenResult permanently slows polling on slow_down and succeeds on token`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"error":"authorization_pending"}"""))
        server.enqueue(MockResponse().setBody("""{"error":"slow_down"}"""))
        server.enqueue(MockResponse().setBody("""{"error":"slow_down"}"""))
        server.enqueue(MockResponse().setBody("""{"access_token":"gho_existing_user_token","token_type":"bearer","scope":""}"""))

        val requestTimes = mutableListOf<Long>()
        val start = System.currentTimeMillis()
        val result = GitHubAuth.pollForTokenResult(
            deviceCode = "dc_123",
            intervalSeconds = 0, // hits pollFloorMs = 20ms initially
            expiresAtEpochMs = start + 10_000L
        )

        assertTrue("Expected Success but got $result", result is GitHubAuth.PollResult.Success)
        val success = result as GitHubAuth.PollResult.Success
        assertEquals("gho_existing_user_token", success.tokenResponse.accessToken)
        assertEquals(4, server.requestCount)
        val elapsed = System.currentTimeMillis() - start
        // Poll 1: >=20ms, Poll 2: >=20ms, Poll 3 (after slow_down): >=60ms, Poll 4 (after 2nd slow_down): >=100ms => >=200ms total
        assertTrue("Expected slow_down backoff accumulation (>=160ms), elapsed=${elapsed}ms", elapsed >= 160L)
        requestTimes.clear()
    }

    @Test
    fun `pollForTokenResult returns AccessDenied when user cancels on GitHub`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"error":"access_denied","error_description":"User denied"}"""))

        val result = GitHubAuth.pollForTokenResult(
            deviceCode = "dc_123",
            intervalSeconds = 0,
            expiresAtEpochMs = System.currentTimeMillis() + 5_000L
        )

        assertEquals(GitHubAuth.PollResult.AccessDenied, result)
    }

    @Test
    fun `pollForTokenResult returns Expired when GitHub returns expired_token`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"error":"expired_token"}"""))

        val result = GitHubAuth.pollForTokenResult(
            deviceCode = "dc_123",
            intervalSeconds = 0,
            expiresAtEpochMs = System.currentTimeMillis() + 5_000L
        )

        assertEquals(GitHubAuth.PollResult.Expired, result)
    }

    @Test
    fun `pollForTokenResult fails immediately on invalid client credentials instead of spinning`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"error":"incorrect_client_credentials","error_description":"Invalid client"}"""
            )
        )

        val result = GitHubAuth.pollForTokenResult(
            deviceCode = "dc_123",
            intervalSeconds = 0,
            expiresAtEpochMs = System.currentTimeMillis() + 5_000L
        )

        assertTrue(result is GitHubAuth.PollResult.Error)
        assertEquals("incorrect_client_credentials", (result as GitHubAuth.PollResult.Error).errorCode)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `fetchUser decodes authenticated user profile`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"login":"existingUser","avatar_url":"https://avatars.githubusercontent.com/u/1","name":"Existing User"}"""
            )
        )

        val user = GitHubAuth.fetchUser("gho_valid")
        assertNotNull(user)
        assertEquals("existingUser", user!!.login)
        assertEquals("Existing User", user.name)
    }
}
