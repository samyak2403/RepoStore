package com.samyak.repostore.util

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.os.Looper
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.samyak.repostore.data.api.GitHubApi
import com.samyak.repostore.data.api.VirusTotalApi
import com.samyak.repostore.data.model.VtAnalysisStats
import com.samyak.repostore.data.model.VtFileAttributes
import com.samyak.repostore.data.model.VtFileData
import com.samyak.repostore.data.model.VtFileResponse
import com.samyak.repostore.data.db.InstalledAppMappingDao
import com.samyak.repostore.data.db.RepoDao
import com.samyak.repostore.data.model.GitHubUser
import com.samyak.repostore.data.model.ReleaseAsset
import com.samyak.repostore.data.prefs.DownloadSettings
import com.samyak.repostore.data.prefs.DownloadSettingsProvider
import com.samyak.repostore.data.prefs.DownloadSettingsStore
import com.samyak.repostore.data.prefs.ThemeSettingsStore
import com.samyak.repostore.data.repository.GitHubRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApkIntegrityAndDiTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var app: Application
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private class FakeDownloadSettingsProvider(
        initial: DownloadSettings
    ) : DownloadSettingsProvider {
        private val state = MutableStateFlow(initial)
        override val settingsFlow: Flow<DownloadSettings> = state
        override suspend fun getSettings(): DownloadSettings = state.value
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun waitWithMainLooper(latch: CountDownLatch) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (latch.count > 0 && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (!latch.await(20, TimeUnit.MILLISECONDS)) {
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `ApkChecksumVerifier verifies matching digests, rejects mismatches, and skips absent digests`() = runBlocking {
        val verifier = ApkChecksumVerifier()
        val payload = "valid-apk-content-123456".toByteArray()
        val expectedHex = sha256Hex(payload)

        val file = tempFolder.newFile("sample.apk").apply { writeBytes(payload) }

        val validWithPrefix = verifier.verifyFile(file, "sha256:$expectedHex")
        assertTrue(validWithPrefix is ApkChecksumVerifier.ChecksumVerificationResult.Verified)
        assertEquals(expectedHex, (validWithPrefix as ApkChecksumVerifier.ChecksumVerificationResult.Verified).computedSha256)

        val validUppercase = verifier.verifyFile(file, expectedHex.uppercase())
        assertTrue(validUppercase is ApkChecksumVerifier.ChecksumVerificationResult.Verified)

        val wrongHash = "0".repeat(64)
        val mismatch = verifier.verifyFile(file, "sha256:$wrongHash")
        assertTrue(mismatch is ApkChecksumVerifier.ChecksumVerificationResult.Mismatch)
        assertEquals(expectedHex, (mismatch as ApkChecksumVerifier.ChecksumVerificationResult.Mismatch).actualSha256)

        assertEquals(
            ApkChecksumVerifier.ChecksumVerificationResult.SkippedNoDigest,
            verifier.verifyFile(file, null)
        )
        assertEquals(
            ApkChecksumVerifier.ChecksumVerificationResult.SkippedNoDigest,
            verifier.verifyFile(file, "md5:d41d8cd98f00b204e9800998ecf8427e")
        )

        val uriResult = verifier.verifyUri(app, Uri.fromFile(file), "sha256:$expectedHex")
        assertTrue(uriResult is ApkChecksumVerifier.ChecksumVerificationResult.Verified)
        assertEquals(expectedHex, verifier.computeSha256(ByteArrayInputStream(payload)))
    }

    @Test
    fun `MultiPartDownloader rejects direct hash mismatch and falls back from tampered mirror proxy to valid direct URL`() = runBlocking {
        val goodBytes = ByteArray(24 * 1024) { ((it * 17 + 3) and 0xFF).toByte() }
        val tamperedBytes = ByteArray(24 * 1024) { ((it * 19 + 5) and 0xFF).toByte() }
        val expectedSha256 = "sha256:${sha256Hex(goodBytes)}"

        val directUrl = server.url("/github.com/user/repo/releases/download/v1/app.apk").toString()
        val proxyUrl = server.url("/proxy/github.com/user/repo/releases/download/v1/app.apk").toString()

        val settingsProvider = object : DownloadSettingsProvider {
            override val settingsFlow: Flow<DownloadSettings> = MutableStateFlow(
                DownloadSettings(mirrorProxyEnabled = true, mirrorProxyUrl = server.url("/proxy/").toString(), multiPartEnabled = false)
            )
            override suspend fun getSettings() = settingsFlow.let {
                DownloadSettings(mirrorProxyEnabled = true, mirrorProxyUrl = server.url("/proxy/").toString(), multiPartEnabled = false)
            }
            override suspend fun transformUrl(originalUrl: String): String {
                return if (originalUrl == directUrl) proxyUrl else originalUrl
            }
        }

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val body = when {
                    path.startsWith("/proxy/") -> tamperedBytes
                    path.startsWith("/github.com/") -> goodBytes
                    else -> return MockResponse().setResponseCode(404)
                }
                return if (request.method.equals("HEAD", ignoreCase = true)) {
                    MockResponse().setResponseCode(200).setHeader("Content-Length", body.size.toString())
                } else {
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", body.size.toString())
                        .setBody(Buffer().write(body))
                }
            }
        }

        val downloader = MultiPartDownloader(app, downloadSettings = settingsProvider)
        val states = mutableListOf<MultiPartDownloader.DownloadState>()
        downloader.download(directUrl, "mirror-check.apk", expectedSha256 = expectedSha256) {
            states.add(it)
        }

        val finalState = states.last()
        assertTrue("Expected fallback to direct URL to succeed, got $finalState", finalState is MultiPartDownloader.DownloadState.Completed)
        val file = (finalState as MultiPartDownloader.DownloadState.Completed).file
        try {
            assertArrayEquals(goodBytes, file.readBytes())
        } finally {
            file.delete()
        }

        // Now verify direct download fails cleanly and deletes file if its digest doesn't match
        val noProxyDownloader = MultiPartDownloader(
            app,
            downloadSettings = FakeDownloadSettingsProvider(DownloadSettings(mirrorProxyEnabled = false))
        )
        val badHashStates = mutableListOf<MultiPartDownloader.DownloadState>()
        val target = noProxyDownloader.getDownloadTargetFile("tampered.apk")
        noProxyDownloader.download(directUrl, "tampered.apk", expectedSha256 = "sha256:${"a".repeat(64)}") {
            badHashStates.add(it)
        }
        assertTrue("Expected Error state on direct mismatch", badHashStates.last() is MultiPartDownloader.DownloadState.Error)
        assertFalse("Tampered APK must be deleted on mismatch", target.exists())
    }

    @Test
    fun `AppInstaller blocks install on MultiPart SHA-256 mismatch and permits match`() {
        val goodBytes = ByteArray(16 * 1024) { ((it * 7 + 1) and 0xFF).toByte() }
        val expectedSha256 = "sha256:${sha256Hex(goodBytes)}"
        val mappingDao = mockk<InstalledAppMappingDao>(relaxed = true)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.method.equals("HEAD", ignoreCase = true)) {
                    MockResponse().setResponseCode(200).setHeader("Content-Length", goodBytes.size.toString())
                } else {
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", goodBytes.size.toString())
                        .setBody(Buffer().write(goodBytes))
                }
            }
        }

        val downloader = MultiPartDownloader(
            app,
            downloadSettings = FakeDownloadSettingsProvider(DownloadSettings(multiPartEnabled = true, threadCount = 2))
        )
        val installer = AppInstaller(
            context = app,
            downloadSettings = FakeDownloadSettingsProvider(DownloadSettings(multiPartEnabled = true, threadCount = 2)),
            multiPartDownloader = downloader,
            checksumVerifier = ApkChecksumVerifier(),
            mappingDaoProvider = { mappingDao }
        )

        // 1. Mismatch must emit Error, delete file, and never start package installer Intent
        val badStates = CopyOnWriteArrayList<AppInstaller.InstallState>()
        val badLatch = CountDownLatch(1)
        installer.download(
            url = server.url("/app.apk").toString(),
            fileName = "mp-mismatch.apk",
            title = "Test App",
            repoName = "Repo",
            ownerName = "Owner",
            expectedSha256 = "sha256:${"f".repeat(64)}"
        ) { state ->
            badStates.add(state)
            if (state is AppInstaller.InstallState.Error || state is AppInstaller.InstallState.Success) {
                badLatch.countDown()
            }
        }
        waitWithMainLooper(badLatch)

        assertTrue("Expected Error state, got $badStates", badStates.lastOrNull() is AppInstaller.InstallState.Error)
        assertNull("Installer activity must NOT be launched on SHA-256 mismatch", shadowOf(app).nextStartedActivity)
        assertFalse(downloader.getDownloadTargetFile("mp-mismatch.apk").exists())

        // 2. Matching SHA-256 must emit Success and launch installer Intent
        val okStates = CopyOnWriteArrayList<AppInstaller.InstallState>()
        val okLatch = CountDownLatch(1)
        installer.download(
            url = server.url("/app.apk").toString(),
            fileName = "mp-match.apk",
            title = "Test App",
            repoName = "Repo",
            ownerName = "Owner",
            expectedSha256 = expectedSha256
        ) { state ->
            okStates.add(state)
            if (state is AppInstaller.InstallState.Error || state is AppInstaller.InstallState.Success) {
                okLatch.countDown()
            }
        }
        waitWithMainLooper(okLatch)

        assertTrue("Expected Success state, got $okStates", okStates.lastOrNull() is AppInstaller.InstallState.Success)
        val startedIntent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, startedIntent?.action)
        downloader.getDownloadTargetFile("mp-match.apk").delete()
    }

    @Test
    fun `AppInstaller verifies SHA-256 on legacy DownloadManager path and blocks mismatches`() {
        val goodBytes = "download-manager-apk-bytes-ok".toByteArray()
        val expectedSha256 = "sha256:${sha256Hex(goodBytes)}"
        val downloadId = 77L

        val dm = mockk<DownloadManager>(relaxed = true)
        every { dm.enqueue(any()) } returns downloadId

        val wrappedContext = object : ContextWrapper(app) {
            override fun getSystemService(name: String): Any? {
                return if (name == Context.DOWNLOAD_SERVICE) dm else super.getSystemService(name)
            }
        }

        val apkFileName = "dm-verified-test.apk"
        val externalDownloads = wrappedContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
            .apply { mkdirs() }
        val apkFile = File(externalDownloads, apkFileName)

        every { dm.query(any()) } answers {
            MatrixCursor(
                arrayOf(
                    DownloadManager.COLUMN_STATUS,
                    DownloadManager.COLUMN_REASON,
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                )
            ).apply {
                addRow(arrayOf(DownloadManager.STATUS_SUCCESSFUL, 0, goodBytes.size, goodBytes.size))
            }
        }
        every { dm.getUriForDownloadedFile(downloadId) } returns Uri.fromFile(apkFile)

        val installer = AppInstaller(
            context = wrappedContext,
            downloadSettings = FakeDownloadSettingsProvider(
                DownloadSettings(mirrorProxyEnabled = false, multiPartEnabled = false)
            )
        )

        // 1. Mismatch on DownloadManager path
        val mismatchStates = CopyOnWriteArrayList<AppInstaller.InstallState>()
        val mismatchLatch = CountDownLatch(1)
        installer.download(
            url = "https://github.com/samyak2403/RepoStore/releases/download/v1.0/app.apk",
            fileName = apkFileName,
            title = "RepoStore",
            expectedSha256 = "sha256:${"b".repeat(64)}"
        ) { state ->
            mismatchStates.add(state)
            if (state is AppInstaller.InstallState.Error || state is AppInstaller.InstallState.Success) {
                mismatchLatch.countDown()
            }
        }
        // Let downloadSettings coroutine register DownloadManager receiver & enqueue
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()

        apkFile.writeBytes(goodBytes)
        wrappedContext.sendBroadcast(
            Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE).putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId)
        )
        waitWithMainLooper(mismatchLatch)

        assertTrue("Expected Error state on DownloadManager hash mismatch, got $mismatchStates", mismatchStates.lastOrNull() is AppInstaller.InstallState.Error)
        assertNull("Installer must NOT start on DownloadManager hash mismatch", shadowOf(app).nextStartedActivity)
        assertFalse("Corrupt DownloadManager APK must be deleted", apkFile.exists())
        verify { dm.remove(downloadId) }

        // 2. Match on DownloadManager path
        val matchStates = CopyOnWriteArrayList<AppInstaller.InstallState>()
        val matchLatch = CountDownLatch(1)
        installer.download(
            url = "https://github.com/samyak2403/RepoStore/releases/download/v1.0/app.apk",
            fileName = apkFileName,
            title = "RepoStore",
            expectedSha256 = expectedSha256
        ) { state ->
            matchStates.add(state)
            if (state is AppInstaller.InstallState.Error || state is AppInstaller.InstallState.Success) {
                matchLatch.countDown()
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()

        apkFile.writeBytes(goodBytes)
        wrappedContext.sendBroadcast(
            Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE).putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId)
        )
        waitWithMainLooper(matchLatch)

        assertTrue("Expected Success state on DownloadManager hash match, got $matchStates", matchStates.lastOrNull() is AppInstaller.InstallState.Success)
        assertEquals(Intent.ACTION_VIEW, shadowOf(app).nextStartedActivity?.action)
        apkFile.delete()
    }

    @Test
    fun `DataStore stores migrate existing SharedPreferences seamlessly and expose reactive flows`() = runBlocking {
        // Seed legacy SharedPreferences before constructing DataStores
        app.getSharedPreferences(DownloadSettingsStore.LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("use_mirror_proxy", true)
            .putString("mirror_proxy_url", "https://gh-proxy.com/")
            .putBoolean("use_multi_part_download", true)
            .putInt("download_thread_count", 16)
            .commit()

        app.getSharedPreferences(ThemeSettingsStore.LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("theme_mode", ThemeSettingsStore.THEME_DARK)
            .commit()

        val dsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val dlDataStore = PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(app, DownloadSettingsStore.LEGACY_PREFS_NAME)),
                scope = dsScope,
                produceFile = { File(tempFolder.root, "download_settings_test.preferences_pb") }
            )
            val downloadStore = DownloadSettingsStore(dlDataStore)
            val migratedDl = downloadStore.getSettings()
            assertTrue(migratedDl.mirrorProxyEnabled)
            assertEquals("https://gh-proxy.com/", migratedDl.mirrorProxyUrl)
            assertTrue(migratedDl.multiPartEnabled)
            assertEquals(16, migratedDl.threadCount)

            downloadStore.setThreadCount(999)
            assertEquals(DownloadSettingsStore.MAX_THREAD_COUNT, downloadStore.getSettings().threadCount)

            val themeDataStore = PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(app, ThemeSettingsStore.LEGACY_PREFS_NAME)),
                scope = dsScope,
                produceFile = { File(tempFolder.root, "theme_settings_test.preferences_pb") }
            )
            val themeStore = ThemeSettingsStore(themeDataStore)
            assertEquals(ThemeSettingsStore.THEME_DARK, themeStore.getThemeMode())
            themeStore.setThemeMode(ThemeSettingsStore.THEME_LIGHT)
            assertEquals(ThemeSettingsStore.THEME_LIGHT, themeStore.getThemeMode())
        } finally {
            dsScope.cancel()
        }
    }

    @Test
    fun `constructor-injected GitHubRepository and ApkSecurityChecker operate without global singletons`() = runBlocking {
        val repoDao = mockk<RepoDao>(relaxed = true)
        val mockGitHubApi = mockk<GitHubApi>()
        val expectedUser = GitHubUser(
            id = 1L,
            login = "octocat",
            name = "Octo",
            avatarUrl = "https://avatars/octo",
            htmlUrl = "https://github.com/octocat",
            bio = "Bio",
            company = null,
            location = null,
            email = null,
            blog = null,
            twitterUsername = null,
            publicRepos = 10,
            publicGists = 5,
            followers = 100,
            following = 5,
            createdAt = "2020-01-01T00:00:00Z"
        )
        coEvery { mockGitHubApi.getUser("octocat") } returns expectedUser

        val injectedRepo = GitHubRepository(repoDao = repoDao, apiProvider = { mockGitHubApi })
        val userResult = injectedRepo.getUser("octocat")
        assertTrue(userResult.isSuccess)
        assertEquals("octocat", userResult.getOrNull()?.login)
        coVerify(exactly = 1) { mockGitHubApi.getUser("octocat") }

        val vtApi = mockk<VirusTotalApi>()
        val rateLimited = HttpException(Response.error<Any>(429, "rate".toResponseBody("text/plain".toMediaType())))
        coEvery { vtApi.getFileReport(any()) } throws rateLimited andThen VtFileResponse(
            data = VtFileData(
                id = "id",
                type = "file",
                attributes = VtFileAttributes(
                    lastAnalysisStats = VtAnalysisStats(harmless = 60, malicious = 0, suspicious = 0, undetected = 10),
                    meaningfulName = "app.apk"
                )
            )
        )

        var rotations = 0
        val securityChecker = ApkSecurityChecker(
            virusTotalApiProvider = { vtApi },
            isConfiguredProvider = { true },
            ensureKeysLoaded = {},
            rotateKey = {
                rotations++
                true
            }
        )
        val asset = ReleaseAsset(
            id = 1L,
            name = "app.apk",
            size = 1024L,
            downloadCount = 1,
            downloadUrl = "https://github.com/a/b/releases/download/v1/app.apk",
            contentType = "application/vnd.android.package-archive",
            digest = "sha256:${"c".repeat(64)}"
        )
        val status = securityChecker.check(asset)
        assertEquals(SecurityStatus.Safe(70), status)
        assertEquals(1, rotations)
    }
}
