package com.samyak.repostore.ui.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LicensesActivityTest {

    @Test
    fun getLibraries_containsAllCoreAndThirdPartyDependencies() {
        val libraries = LicensesActivity.getLibraries()

        assertTrue("Expected at least 20 libraries listed", libraries.size >= 20)

        // Verify all project dependencies are included
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
        assertEquals("MIT", repoStore.license)
        assertEquals("Samyak Kamble", repoStore.author)
        assertFalse(repoStore.description.isNullOrBlank())

        // Verify Glide license is BSD 2-Clause
        val glide = libraries.first { it.name == "Glide" }
        assertEquals("BSD 2-Clause", glide.license)

        // Verify no empty fields, invalid URLs, and each has an understandable description
        for (lib in libraries) {
            assertFalse("Library name should not be blank", lib.name.isBlank())
            assertFalse("Author should not be blank for ${lib.name}", lib.author.isBlank())
            assertFalse("License should not be blank for ${lib.name}", lib.license.isBlank())
            assertNotNull("Description must be provided for ${lib.name}", lib.description)
            assertFalse("Description should not be blank for ${lib.name}", lib.description!!.isBlank())
            assertTrue(
                "URL should start with https:// for ${lib.name}",
                lib.url.startsWith("https://")
            )
        }
    }
}
