package com.samyak.repostore.util

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GitCoreFetchersIntegrationTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/").toString().trimEnd('/')
        AppNameFetcher.configureForTesting(baseUrl)
        PackageIdFetcher.configureForTesting(baseUrl)
    }

    @After
    fun tearDown() {
        AppNameFetcher.resetForTesting()
        PackageIdFetcher.resetForTesting()
        server.shutdown()
    }

    @Test
    fun `AppNameFetcher falls back to master branch and unescapes XML entities`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return if (path.endsWith("/master/app/src/main/res/values/strings.xml")) {
                    MockResponse().setResponseCode(200).setBody(
                        """<resources><string name="app_name">Pixel &amp; Ink\'s App</string></resources>"""
                    )
                } else {
                    MockResponse().setResponseCode(404).setBody("Not Found")
                }
            }
        }

        val resolved = AppNameFetcher.fetchRealName("dev", "PixelInk", defaultBranch = "", language = "Kotlin")
        assertEquals("Pixel & Ink's App", resolved)
    }

    @Test
    fun `PackageIdFetcher resolves Compose Multiplatform defaultConfig despite nested blocks and comments`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return if (path.endsWith("/main/composeApp/build.gradle.kts")) {
                    MockResponse().setResponseCode(200).setBody(
                        """
                        // applicationId = "com.stale.ignored"
                        android {
                            defaultConfig {
                                ndk { abiFilters += "arm64-v8a" }
                                applicationId = "dev.kmp.repostore"
                            }
                        }
                        """.trimIndent()
                    )
                } else {
                    MockResponse().setResponseCode(404).setBody("Not Found")
                }
            }
        }

        val pkgId = PackageIdFetcher.fetchPackageId("dev", "KmpStore", defaultBranch = "main", language = "Kotlin")
        assertEquals("dev.kmp.repostore", pkgId)
    }
}
