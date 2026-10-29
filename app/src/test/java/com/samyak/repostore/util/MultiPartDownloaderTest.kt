package com.samyak.repostore.util

import android.content.Context
import com.samyak.repostore.data.prefs.DownloadPreferences
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class MultiPartDownloaderTest {

    private lateinit var mockContext: Context
    private lateinit var tempDir: File

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        tempDir = File(System.getProperty("java.io.tmpdir"), "repostore_test_${System.currentTimeMillis()}").apply { mkdirs() }
        every { mockContext.getExternalFilesDir(any()) } returns tempDir
        every { mockContext.cacheDir } returns tempDir
    }

    @Test
    fun testHeadRequestWithHeaders() {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "GitHubAppStore-Android")
                    .header("Accept", "*/*")
                    .build()
                chain.proceed(request)
            }
            .build()

        val url = "https://github.com/VadimBoev/FlappyBird/releases/download/v1.9.2/FlappyBird-signed.apk"
        val request = Request.Builder()
            .url(url)
            .head()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            assertTrue("HEAD request should succeed or redirect", response.isSuccessful)
            val len = response.header("Content-Length")?.toLongOrNull() ?: -1L
            assertTrue("Content-Length should be positive", len > 0)
        }
    }

    @Test
    fun testDownloadTargetFileResolution() {
        val downloader = MultiPartDownloader(mockContext)
        val targetFile = downloader.getDownloadTargetFile("test.apk")
        assertEquals(File(tempDir, "test.apk").absolutePath, targetFile.absolutePath)
    }

    @Test
    fun testDownloadFallbackExecution() = runBlocking {
        mockkObject(DownloadPreferences)
        // Simulate mirror proxy being disabled
        every { DownloadPreferences.isMirrorProxyEnabled(any()) } returns false
        every { DownloadPreferences.isMultiPartEnabled(any()) } returns false
        every { DownloadPreferences.transformUrl(any(), any()) } answers { secondArg() }

        val downloader = MultiPartDownloader(mockContext)
        val states = mutableListOf<MultiPartDownloader.DownloadState>()

        val url = "https://github.com/VadimBoev/FlappyBird/releases/download/v1.9.2/FlappyBird-signed.apk"
        downloader.download(url, "FlappyBird-test.apk") { state ->
            states.add(state)
        }

        // Verify download completed successfully
        val lastState = states.lastOrNull()
        assertNotNull("Should have received states", lastState)
        assertTrue("Download should complete successfully, got: $lastState", lastState is MultiPartDownloader.DownloadState.Completed)
        val completedState = lastState as MultiPartDownloader.DownloadState.Completed
        assertTrue("Downloaded file should exist", completedState.file.exists())
        assertTrue("Downloaded file size should match FlappyBird APK (~94KB)", completedState.file.length() > 50_000L)

        // Cleanup
        completedState.file.delete()
        tempDir.deleteRecursively()
        unmockkObject(DownloadPreferences)
    }
}
