package com.samyak.repostore.ui.activity

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.samyak.repostore.R
import com.samyak.repostore.data.auth.GitHubAuth
import com.samyak.repostore.data.auth.PendingDeviceFlow
import com.samyak.repostore.data.auth.SecureTokenStorage
import com.samyak.repostore.data.auth.SessionResetReason
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GitHubSignInActivityRobolectricTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer

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
            pollFloorMs = 5_000L, // Keep polling pending during UI verification
            slowDownStepMs = 5_000L
        )
    }

    @After
    fun tearDown() {
        SecureTokenStorage.clearAll(context)
        SecureTokenStorage.setCryptoBackendForTesting(null)
        GitHubAuth.resetForTesting()
        server.shutdown()
    }

    @Test
    fun `resumes pending device flow across browser switch and activity recreation without generating new code`() {
        // Enqueue response in case polling fires (but /login/device/code must NEVER be called)
        server.enqueue(MockResponse().setBody("""{"error":"authorization_pending"}"""))

        val pending = PendingDeviceFlow(
            deviceCode = "dc_existing_session",
            userCode = "KEEP-1234",
            verificationUri = "https://github.com/login/device",
            intervalSeconds = 5,
            expiresAtEpochMs = System.currentTimeMillis() + 600_000L
        )
        GitHubAuth.savePendingDeviceFlow(context, pending)
        SecureTokenStorage.setSessionResetReason(context, SessionResetReason.CORRUPTED_OR_RESTORED)

        val controller = Robolectric.buildActivity(GitHubSignInActivity::class.java).setup()
        val activity = controller.get()

        val layoutCode = activity.findViewById<View>(R.id.layout_code)
        val tvUserCode = activity.findViewById<TextView>(R.id.tv_user_code)
        val cardNotice = activity.findViewById<View>(R.id.card_reset_notice)
        val tvNotice = activity.findViewById<TextView>(R.id.tv_reset_notice)

        assertEquals(View.VISIBLE, layoutCode.visibility)
        assertEquals("KEEP-1234", tvUserCode.text.toString())
        assertEquals(View.VISIBLE, cardNotice.visibility)
        assertEquals(
            activity.getString(R.string.reauth_reset_notice_reinstall),
            tvNotice.text.toString()
        )

        // Simulate Android killing/recreating the Activity during Edge/Chrome handoff
        controller.recreate()
        val recreated = controller.get()
        assertEquals(View.VISIBLE, recreated.findViewById<View>(R.id.layout_code).visibility)
        assertEquals("KEEP-1234", recreated.findViewById<TextView>(R.id.tv_user_code).text.toString())
        assertEquals(
            "Should NOT issue a new /login/device/code request when pending flow is still valid",
            0,
            server.requestCount
        )

        controller.pause().stop().destroy()
    }

    @Test
    fun `shows signed in state immediately when token and user are stored`() {
        assertEquals(true, GitHubAuth.saveToken(context, "gho_active_token"))
        GitHubAuth.saveUser(
            context,
            GitHubAuth.GitHubUser(
                login = "existingDev",
                avatarUrl = null,
                name = "Existing Dev"
            )
        )

        val controller = Robolectric.buildActivity(GitHubSignInActivity::class.java).setup()
        val activity = controller.get()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.layout_signed_in).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.layout_code).visibility)
        assertEquals("Existing Dev", activity.findViewById<TextView>(R.id.tv_display_name).text.toString())
        assertEquals("@existingDev", activity.findViewById<TextView>(R.id.tv_username).text.toString())

        controller.pause().stop().destroy()
    }
}
