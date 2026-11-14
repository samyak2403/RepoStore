package com.samyak.repostore.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.samyak.repostore.data.prefs.DownloadPreferences
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DownloadFeatureTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPrefs()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        clearPrefs()
        unmockkObject(DownloadPreferences)
        server.shutdown()
    }

    private fun clearPrefs() {
        context.getSharedPreferences("download_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun buildBinaryPayload(size: Int): ByteArray {
        return ByteArray(size) { index -> ((index * 31 + 7) and 0xFF).toByte() }
    }

    @Test
    fun `single-part APK download writes exact bytes and emits Completed`() = runBlocking {
        val payload = buildBinaryPayload(48 * 1024)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method.equals("HEAD", ignoreCase = true)) {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", payload.size.toString())
                }
                val buf = Buffer().write(payload)
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", payload.size.toString())
                    .setBody(buf)
            }
        }

        val downloader = MultiPartDownloader(context)
        val states = mutableListOf<MultiPartDownloader.DownloadState>()
        downloader.download(server.url("/release/sample.apk").toString(), "sample-single.apk") {
            states.add(it)
        }

        val finalState = states.last()
        assertTrue("Expected Completed state, got $finalState", finalState is MultiPartDownloader.DownloadState.Completed)
        val file = (finalState as MultiPartDownloader.DownloadState.Completed).file
        try {
            assertTrue(file.exists())
            assertArrayEquals(payload, file.readBytes())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `multi-part parallel Range 206 download reassembles chunks byte-for-byte`() = runBlocking {
        DownloadPreferences.setMultiPartEnabled(context, true)
        DownloadPreferences.setThreadCount(context, 4)

        val payload = buildBinaryPayload(128 * 1024 + 333) // non-aligned size across 4 threads
        val rangeRequestsSeen = java.util.concurrent.atomic.AtomicInteger(0)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method.equals("HEAD", ignoreCase = true)) {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Length", payload.size.toString())
                }
                val rangeHeader = request.getHeader("Range")
                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    rangeRequestsSeen.incrementAndGet()
                    val bounds = rangeHeader.removePrefix("bytes=").split("-")
                    val start = bounds[0].toInt()
                    val end = bounds.getOrNull(1)?.toIntOrNull() ?: (payload.size - 1)
                    val slice = payload.copyOfRange(start, end + 1)
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                        .setHeader("Content-Length", slice.size.toString())
                        .setBody(Buffer().write(slice))
                }
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", payload.size.toString())
                    .setBody(Buffer().write(payload))
            }
        }

        val downloader = MultiPartDownloader(context)
        val states = mutableListOf<MultiPartDownloader.DownloadState>()
        downloader.download(server.url("/release/multi.apk").toString(), "sample-multi.apk") {
            states.add(it)
        }

        val finalState = states.last()
        assertTrue("Expected Completed state, got $finalState", finalState is MultiPartDownloader.DownloadState.Completed)
        assertEquals(4, rangeRequestsSeen.get())
        val file = (finalState as MultiPartDownloader.DownloadState.Completed).file
        try {
            assertArrayEquals(payload, file.readBytes())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `multi-part falls back to single-part cleanly when chunk request returns 200 instead of 206`() = runBlocking {
        DownloadPreferences.setMultiPartEnabled(context, true)
        DownloadPreferences.setThreadCount(context, 4)

        val payload = buildBinaryPayload(32 * 1024)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method.equals("HEAD", ignoreCase = true)) {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Length", payload.size.toString())
                }
                // Deliberately ignore Range header and return full 200 body
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", payload.size.toString())
                    .setBody(Buffer().write(payload))
            }
        }

        val downloader = MultiPartDownloader(context)
        val states = mutableListOf<MultiPartDownloader.DownloadState>()
        downloader.download(server.url("/release/no-range.apk").toString(), "fallback-single.apk") {
            states.add(it)
        }

        val finalState = states.last()
        assertTrue("Expected clean fallback Completed state, got $finalState", finalState is MultiPartDownloader.DownloadState.Completed)
        val file = (finalState as MultiPartDownloader.DownloadState.Completed).file
        try {
            assertArrayEquals("Fallback download must not be corrupted", payload, file.readBytes())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `proxy failure automatically falls back to direct download URL`() = runBlocking {
        val payload = buildBinaryPayload(16 * 1024)
        val directUrl = server.url("/direct/app.apk").toString()
        val proxyUrl = server.url("/proxy/app.apk").toString()

        mockkObject(DownloadPreferences)
        every { DownloadPreferences.isMirrorProxyEnabled(any()) } returns true
        every { DownloadPreferences.isMultiPartEnabled(any()) } returns false
        every { DownloadPreferences.transformUrl(any(), directUrl) } returns proxyUrl

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/proxy/app.apk" -> MockResponse().setResponseCode(502).setBody("Bad Gateway")
                    "/direct/app.apk" -> {
                        if (request.method.equals("HEAD", ignoreCase = true)) {
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Length", payload.size.toString())
                        } else {
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Length", payload.size.toString())
                                .setBody(Buffer().write(payload))
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val downloader = MultiPartDownloader(context)
        val states = mutableListOf<MultiPartDownloader.DownloadState>()
        downloader.download(directUrl, "proxy-fallback.apk") { states.add(it) }

        val finalState = states.last()
        assertTrue("Expected direct URL fallback Completed, got $finalState", finalState is MultiPartDownloader.DownloadState.Completed)
        val file = (finalState as MultiPartDownloader.DownloadState.Completed).file
        try {
            assertArrayEquals(payload, file.readBytes())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `HTTP 404 emits Error and filename traversal is sanitized into downloads folder`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse().setResponseCode(404).setBody("Not Found")
            }
        }

        val downloader = MultiPartDownloader(context)
        val sanitizedTarget = downloader.getDownloadTargetFile(File("../../evil.apk").name)
        val states = mutableListOf<MultiPartDownloader.DownloadState>()
        downloader.download(server.url("/missing.apk").toString(), "../../evil.apk") { states.add(it) }

        val finalState = states.last()
        assertTrue("Expected Error state on 404, got $finalState", finalState is MultiPartDownloader.DownloadState.Error)
        assertFalse("Partial/failed APK must be cleaned up", sanitizedTarget.exists())
        assertEquals("evil.apk", sanitizedTarget.name)
    }

    @Test
    fun `cancelling active download stops transfer cleanly and deletes partial file`() = runBlocking {
        val largeChunk = buildBinaryPayload(256 * 1024)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.method.equals("HEAD", ignoreCase = true)) {
                    MockResponse().setResponseCode(200).setHeader("Content-Length", largeChunk.size.toString())
                } else {
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", largeChunk.size.toString())
                        .setBody(Buffer().write(largeChunk))
                        .throttleBody(4096, 100, TimeUnit.MILLISECONDS)
                }
            }
        }

        val downloader = MultiPartDownloader(context)
        val target = downloader.getDownloadTargetFile("cancel-test.apk")
        val states = mutableListOf<MultiPartDownloader.DownloadState>()

        val job = launch {
            downloader.download(server.url("/slow.apk").toString(), "cancel-test.apk") { states.add(it) }
        }
        delay(250)
        downloader.cancel()
        job.join()

        assertFalse("Cancelled download must remove partial file", target.exists())
        assertFalse("Cancelled download must not complete", states.any { it is MultiPartDownloader.DownloadState.Completed })
    }

    @Test
    fun `DownloadPreferences validates hosts, clamps threads, and prevents double-proxying`() {
        DownloadPreferences.setThreadCount(context, 99)
        assertEquals(DownloadPreferences.MAX_THREAD_COUNT, DownloadPreferences.getThreadCount(context))
        DownloadPreferences.setThreadCount(context, 0)
        assertEquals(DownloadPreferences.MIN_THREAD_COUNT, DownloadPreferences.getThreadCount(context))

        assertTrue(DownloadPreferences.isValidProxyUrl("https://gh-proxy.com/"))
        assertFalse(DownloadPreferences.isValidProxyUrl("http://"))
        assertFalse(DownloadPreferences.isValidProxyUrl("ftp://gh-proxy.com/"))
        assertFalse(DownloadPreferences.isValidProxyUrl("not a url"))

        DownloadPreferences.setMirrorProxyEnabled(context, true)
        DownloadPreferences.setMirrorProxyUrl(context, "https://gh-proxy.com")

        val ghUrl = "https://github.com/samyak2403/RepoStore/releases/download/v1.0/app.apk"
        val transformed = DownloadPreferences.transformUrl(context, ghUrl)
        assertEquals("https://gh-proxy.com/$ghUrl", transformed)
        // Second pass must NOT double-prefix
        assertEquals(transformed, DownloadPreferences.transformUrl(context, transformed))
        // Non-GitHub URL stays unchanged
        val nonGh = "https://example.com/app.apk"
        assertEquals(nonGh, DownloadPreferences.transformUrl(context, nonGh))
    }

    @Test
    fun `AppInstaller detects current test package cleanly`() {
        val installer = AppInstaller.getInstance(context)
        assertTrue(installer.isInstalled(context.packageName))
        assertFalse(installer.isInstalled("com.nonexistent.repostore.missing.pkg"))
    }
}
