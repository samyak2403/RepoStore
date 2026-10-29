package com.samyak.repostore.util

import com.samyak.repostore.data.model.ReleaseAsset
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApkArchitectureHelperTest {
    @Before
    fun setUp() {
        mockkObject(AbiProvider)
        setSupportedAbis("arm64-v8a")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun selectBestApk_returnsExactMatchForPrimaryAbi() {
        setSupportedAbis("arm64-v8a", "armeabi-v7a")

        val assets = listOf(
            apk("app-universal.apk"),
            apk("app-arm64-v8a.apk"),
            apk("app-armeabi-v7a.apk")
        )

        val selected = ApkArchitectureHelper.selectBestApk(assets)

        assertTrue(selected is ApkSelectionResult.ExactMatch)
        assertEquals("arm64-v8a", (selected as ApkSelectionResult.ExactMatch).abi)
        assertEquals("app-arm64-v8a.apk", selected.asset.name)
    }

    @Test
    fun selectBestApk_returnsExactMatchForSecondaryAbi() {
        setSupportedAbis("x86_64", "arm64-v8a")

        val assets = listOf(
            apk("app-arm64-v8a.apk"),
            apk("app-x86.apk")
        )

        val selected = ApkArchitectureHelper.selectBestApk(assets)

        assertTrue(selected is ApkSelectionResult.ExactMatch)
        assertEquals("arm64-v8a", (selected as ApkSelectionResult.ExactMatch).abi)
        assertEquals("app-arm64-v8a.apk", selected.asset.name)
    }

    @Test
    fun selectBestApk_returnsUniversalWhenNoArchMatch() {
        setSupportedAbis("x86_64")

        val assets = listOf(
            apk("app-universal.apk"),
            apk("app-arm64-v8a.apk")
        )

        val selected = ApkArchitectureHelper.selectBestApk(assets)

        assertTrue(selected is ApkSelectionResult.Universal)
        assertEquals("app-universal.apk", (selected as ApkSelectionResult.Universal).asset.name)
    }

    @Test
    fun selectBestApk_returnsFallbackWhenNoMatch() {
        setSupportedAbis("x86_64")

        val assets = listOf(
            apk("app-release.apk"),
            apk("app-armeabi-v7a.apk")
        )

        val selected = ApkArchitectureHelper.selectBestApk(assets)

        assertTrue(selected is ApkSelectionResult.Fallback)
        assertEquals("app-release.apk", (selected as ApkSelectionResult.Fallback).asset.name)
    }

    @Test
    fun selectBestApk_returnsNoApkFoundWhenNoApkAssets() {
        setSupportedAbis("arm64-v8a")

        val assets = listOf(
            asset("readme.txt"),
            asset("app.aab")
        )

        val selected = ApkArchitectureHelper.selectBestApk(assets)

        assertTrue(selected is ApkSelectionResult.NoApkFound)
    }

    @Test
    fun getArchitectureDisplayName_usesPrimaryAbiMapping() {
        setSupportedAbis("arm64-v8a")

        assertEquals("ARM64", ApkArchitectureHelper.getArchitectureDisplayName())
    }

    @Test
    fun isApkCompatible_returnsTrueForUniversal() {
        setSupportedAbis("arm64-v8a")

        assertTrue(ApkArchitectureHelper.isApkCompatible("app-universal.apk"))
    }

    @Test
    fun isApkCompatible_returnsTrueForMatchingAbi() {
        setSupportedAbis("arm64-v8a", "armeabi-v7a")

        assertTrue(ApkArchitectureHelper.isApkCompatible("app-arm64-v8a.apk"))
    }

    @Test
    fun isApkCompatible_returnsFalseForMismatchedAbi() {
        setSupportedAbis("arm64-v8a")

        assertFalse(ApkArchitectureHelper.isApkCompatible("app-x86.apk"))
    }

    @Test
    fun isApkCompatible_returnsTrueWhenNoArchPatternPresent() {
        setSupportedAbis("arm64-v8a")

        assertTrue(ApkArchitectureHelper.isApkCompatible("app-release.apk"))
    }

    @Test
    fun selectBestApk_returnsSingleWhenOnlyOneApk() {
        setSupportedAbis("arm64-v8a")

        val assets = listOf(
            apk("app-release.apk")
        )

        val selected = ApkArchitectureHelper.selectBestApk(assets)

        assertTrue(selected is ApkSelectionResult.Single)
        assertEquals("app-release.apk", (selected as ApkSelectionResult.Single).asset.name)
    }

    @Test
    fun isApkCompatible_respectsSpecificAbiWhenAppNameContainsUniversal() {
        setSupportedAbis("x86_64")

        // On an x86_64 device:
        assertTrue(ApkArchitectureHelper.isApkCompatible("universal-revanced-manager-x86_64.apk"))
        assertFalse(ApkArchitectureHelper.isApkCompatible("universal-revanced-manager-arm64-v8a.apk"))
        assertFalse(ApkArchitectureHelper.isApkCompatible("universal-revanced-manager-armeabi-v7a.apk"))
        assertTrue(ApkArchitectureHelper.isApkCompatible("reseam-plugin.apk"))
    }

    @Test
    fun getVariantTitle_returnsClearReadableTitles() {
        assertEquals("ARM64 (v8a)", ApkArchitectureHelper.getVariantTitle("universal-revanced-manager-arm64-v8a.apk"))
        assertEquals("x86_64 (64-bit)", ApkArchitectureHelper.getVariantTitle("universal-revanced-manager-x86_64.apk"))
        assertEquals("ARMv7 (32-bit)", ApkArchitectureHelper.getVariantTitle("universal-revanced-manager-armeabi-v7a.apk"))
        assertEquals("reseam-plugin", ApkArchitectureHelper.getVariantTitle("reseam-plugin.apk"))
        assertEquals("Universal", ApkArchitectureHelper.getVariantTitle("app-universal.apk"))
    }

    @Test
    fun getArchitectureDescription_returnsAccurateLabels() {
        assertEquals("64-bit ARM", ApkArchitectureHelper.getArchitectureDescription("universal-revanced-manager-arm64-v8a.apk"))
        assertEquals("64-bit x86", ApkArchitectureHelper.getArchitectureDescription("universal-revanced-manager-x86_64.apk"))
        assertEquals("32-bit ARM", ApkArchitectureHelper.getArchitectureDescription("universal-revanced-manager-armeabi-v7a.apk"))
        assertEquals("Standard", ApkArchitectureHelper.getArchitectureDescription("reseam-plugin.apk"))
    }

    @Test
    fun getShortArchitectureName_returnsConciseTags() {
        assertEquals("ARM64", ApkArchitectureHelper.getShortArchitectureName("universal-revanced-manager-arm64-v8a.apk"))
        assertEquals("x86_64", ApkArchitectureHelper.getShortArchitectureName("universal-revanced-manager-x86_64.apk"))
        assertEquals("ARM32", ApkArchitectureHelper.getShortArchitectureName("universal-revanced-manager-armeabi-v7a.apk"))
        assertEquals("Plugin", ApkArchitectureHelper.getShortArchitectureName("reseam-plugin.apk"))
        assertEquals("Universal", ApkArchitectureHelper.getShortArchitectureName("app-universal.apk"))
    }

    private fun apk(name: String): ReleaseAsset = ReleaseAsset(
        id = name.hashCode().toLong(),
        name = name,
        size = 1234,
        downloadCount = 0,
        downloadUrl = "https://example.com/$name",
        contentType = "application/vnd.android.package-archive"
    )

    private fun asset(name: String): ReleaseAsset = ReleaseAsset(
        id = name.hashCode().toLong(),
        name = name,
        size = 1234,
        downloadCount = 0,
        downloadUrl = "https://example.com/$name",
        contentType = "application/octet-stream"
    )

    private fun setSupportedAbis(vararg abis: String) {
        every { AbiProvider.supportedAbis() } returns arrayOf(*abis)
    }
}
