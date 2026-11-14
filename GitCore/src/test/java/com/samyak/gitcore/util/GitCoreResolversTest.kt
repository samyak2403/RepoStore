package com.samyak.gitcore.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitCoreResolversTest {

    @Test
    fun `blank owner or name returns empty list instead of malformed URLs`() {
        assertTrue(IconResolver.resolve("", "App", "main", "Kotlin").isEmpty())
        assertTrue(IconResolver.resolve("   ", "App", "main", "Kotlin").isEmpty())
        assertTrue(IconResolver.resolve("owner", "", "main", "Kotlin").isEmpty())
        assertTrue(IconResolver.resolve("..", "App", "main", "Kotlin").isEmpty())

        assertTrue(AppNameResolver.resolve("", "App", null, null).isEmpty())
        assertTrue(PackageIdResolver.resolve("owner", "  ", null, null).isEmpty())
    }

    @Test
    fun `null or blank defaultBranch falls back cleanly to main and master without double slashes`() {
        val iconUrls = IconResolver.resolve("samyak2403", "RepoStore", "   ", "Kotlin")
        assertTrue(iconUrls.isNotEmpty())
        assertTrue(iconUrls.none { it.removePrefix("https://").contains("//") })
        assertTrue(iconUrls.any { it.contains("/samyak2403/RepoStore/main/") })
        assertTrue(iconUrls.any { it.contains("/samyak2403/RepoStore/master/") })

        val nameUrls = AppNameResolver.resolve("samyak2403", "RepoStore", "", "Kotlin")
        assertTrue(nameUrls.any { it.contains("/samyak2403/RepoStore/main/") })
        assertTrue(nameUrls.any { it.contains("/samyak2403/RepoStore/master/") })

        val pkgUrls = PackageIdResolver.resolve("samyak2403", "RepoStore", null, "Kotlin")
        assertTrue(pkgUrls.any { it.contains("/samyak2403/RepoStore/main/") })
        assertTrue(pkgUrls.any { it.contains("/samyak2403/RepoStore/master/") })
    }

    @Test
    fun `explicit branch with slashes and spaces is properly URL encoded and trimmed`() {
        val urls = AppNameResolver.resolve(" owner ", " My App ", " feature/release 2 ", "Kotlin")
        assertTrue(urls.isNotEmpty())
        assertTrue(urls.all { it.startsWith("https://raw.githubusercontent.com/owner/My%20App/feature/release%202/") })
    }

    @Test
    fun `IconResolver prioritizes high-res store metadata icons before mipmaps and includes WebP and composeApp`() {
        val urls = IconResolver.resolve("org", "Sample", "main", "Kotlin")
        assertEquals(urls.size, urls.distinct().size)

        val fastlaneIdx = urls.indexOfFirst { it.endsWith("fastlane/metadata/android/en-US/images/icon.png") }
        val playstoreIdx = urls.indexOfFirst { it.endsWith("app/src/main/ic_launcher-playstore.png") }
        val mipmapWebpIdx = urls.indexOfFirst { it.endsWith("app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp") }
        val composeAppIdx = urls.indexOfFirst { it.endsWith("composeApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.webp") }

        assertTrue("Expected fastlane icon URL", fastlaneIdx >= 0)
        assertTrue("Expected playstore icon URL", playstoreIdx > fastlaneIdx)
        assertTrue("Expected native WebP mipmap URL", mipmapWebpIdx > playstoreIdx)
        assertTrue("Expected composeApp icon URL", composeAppIdx > mipmapWebpIdx)
    }

    @Test
    fun `PackageIdResolver prioritizes ecosystem layouts and avoids duplicate URLs`() {
        val dartUrls = PackageIdResolver.resolve("org", "FlutterApp", "main", "Dart")
        assertEquals(dartUrls.size, dartUrls.distinct().size)
        assertTrue(dartUrls.first().endsWith("android/app/build.gradle.kts"))
        assertTrue(dartUrls.any { it.endsWith("app.json") })

        val kotlinUrls = PackageIdResolver.resolve("org", "KmpApp", "main", "Kotlin")
        assertEquals(kotlinUrls.size, kotlinUrls.distinct().size)
        assertTrue(kotlinUrls.first().endsWith("app/build.gradle.kts"))
        assertTrue(kotlinUrls.any { it.endsWith("composeApp/build.gradle.kts") })

        val csharpUrls = PackageIdResolver.resolve("org", "MauiApp", "main", "C#")
        assertEquals(csharpUrls.size, csharpUrls.distinct().size)
        assertTrue(csharpUrls.first().endsWith("MauiApp/MauiApp.csproj"))
        assertFalse(csharpUrls.isEmpty())
    }
}
