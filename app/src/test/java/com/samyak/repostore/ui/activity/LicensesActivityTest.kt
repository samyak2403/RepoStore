package com.samyak.repostore.ui.activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicensesActivityTest {

    @Test
    fun getLibraries_containsAllCoreAndThirdPartyDependencies() {
        val libraries = LicensesActivity.getLibraries()

        assertTrue("Expected at least 20 libraries listed", libraries.size >= 20)

        // Verify newly added missing open source libraries are present
        val names = libraries.map { it.name }.toSet()
        val required = listOf(
            "RepoStore",
            "AndroidX Core KTX",
            "AndroidX AppCompat",
            "AndroidX Activity KTX",
            "AndroidX Fragment KTX",
            "Material Components",
            "AndroidX ConstraintLayout",
            "AndroidX ViewPager2",
            "AndroidX Lifecycle",
            "AndroidX WorkManager",
            "Room Database",
            "SwipeRefreshLayout",
            "Kotlin Standard Library",
            "Kotlin Coroutines",
            "Retrofit",
            "OkHttp",
            "Gson",
            "Glide",
            "Markwon",
            "PhotoView",
            "Lottie Android",
            "DotsIndicator",
            "ZXing"
        )

        for (req in required) {
            assertTrue("Expected library '$req' in open source licenses list", names.contains(req))
        }

        // Verify RepoStore app license is MIT matching LICENSE file
        val repoStore = libraries.first { it.name == "RepoStore" }
        org.junit.Assert.assertEquals("MIT", repoStore.license)
        org.junit.Assert.assertEquals("Samyak Kamble", repoStore.author)

        // Verify no empty fields or invalid URLs
        for (lib in libraries) {
            assertFalse("Library name should not be blank", lib.name.isBlank())
            assertFalse("Author should not be blank for ${lib.name}", lib.author.isBlank())
            assertFalse("License should not be blank for ${lib.name}", lib.license.isBlank())
            assertTrue(
                "URL should start with https:// for ${lib.name}",
                lib.url.startsWith("https://")
            )
        }
    }
}
