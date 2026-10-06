package com.samyak.repostore.data.api

import com.sun.net.httpserver.HttpServer
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression tests for the stale-cache bug behind "RepoStore keeps showing the old APK
 * and the old description".
 *
 * The client used to send `maxStale(7, DAYS)` on every request, so a cached GitHub
 * response was served from disk without ever being revalidated for up to a week: a
 * newly published release (and an edited README/description) kept showing the previous
 * content until the entry expired. The stale window must apply only while offline.
 *
 * The client is built from the very interceptors [RetrofitClient] installs — see
 * [CacheInterceptors] — with only the 5-minute freshness window shortened to one second
 * so the test does not sleep for minutes.
 */
class GitHubResponseCachePolicyTest {

    private lateinit var server: HttpServer
    private lateinit var cacheDir: File
    private val requests = AtomicInteger()
    @Volatile private var payload = """{"tag_name":"v1.9"}"""

    private val port = 18099

    @Before
    fun setUp() {
        cacheDir = File(System.getProperty("java.io.tmpdir"), "http-cache-" + System.nanoTime()).apply { mkdirs() }
        requests.set(0)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.createContext("/releases/latest") { exchange ->
            requests.incrementAndGet()
            val bytes = payload.toByteArray()
            exchange.responseHeaders.add("Cache-Control", "public, max-age=60, s-maxage=60")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.executor = null
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
        cacheDir.deleteRecursively()
    }

    /** The exact interceptor chain of RetrofitClient, with the offline state injected. */
    private fun client(offline: () -> Boolean): OkHttpClient {
        return OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http_cache"), 10L * 1024 * 1024))
            .addNetworkInterceptor(CacheInterceptors.freshnessInterceptor(freshnessSeconds = 1))
            .addInterceptor(CacheInterceptors.stalePolicyInterceptor(offline))
            .build()
    }

    private fun fetch(client: OkHttpClient): String =
        client.newCall(Request.Builder().url("http://127.0.0.1:$port/releases/latest").build())
            .execute().use { response ->
                assertEquals(200, response.code)
                response.body!!.string()
            }

    @Test
    fun `online request never serves a stale release after the freshness window expired`() {
        val client = client(offline = { false })

        assertEquals("""{"tag_name":"v1.9"}""", fetch(client))
        assertEquals(1, requests.get())

        // Freshness window expires and a new release is published upstream.
        Thread.sleep(1500)
        payload = """{"tag_name":"v1.18"}"""

        assertEquals("""{"tag_name":"v1.18"}""", fetch(client))
        assertEquals(2, requests.get())
    }

    @Test
    fun `offline request still serves the cached response instead of failing`() {
        var offline = false
        val client = client(offline = { offline })

        assertEquals("""{"tag_name":"v1.9"}""", fetch(client))
        assertEquals(1, requests.get())

        // The window expires and the device goes offline: the entry must still be usable.
        Thread.sleep(1500)
        offline = true

        assertEquals("""{"tag_name":"v1.9"}""", fetch(client))
        assertEquals(1, requests.get())
    }
}
