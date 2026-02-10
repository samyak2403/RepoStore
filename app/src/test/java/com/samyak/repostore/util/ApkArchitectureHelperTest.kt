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
    fun selectBestApk_prefersPrimaryAbiMatch() {
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
    fun selectBestApk_fallsBackToSecondaryAbiMatch() {
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
    fun selectBestApk_usesUniversalWhenNoArchMatch() {
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
    fun selectBestApk_fallsBackToFirstApkWhenNoMatch() {
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
    fun selectBestApk_returnsNullWhenNoApkAssets() {
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
