package com.samyak.repostore.util

import android.os.Build
import com.samyak.repostore.data.model.ReleaseAsset

/**
 * Helper for selecting the best APK based on device CPU architecture.
 * Supports: arm64-v8a (aarch64), armeabi-v7a (arm), x86_64, x86
 */
object ApkArchitectureHelper {
    
    /**
     * Mapping of device ABIs to common APK naming patterns
     */
    private val abiPatterns = mapOf(
        "arm64-v8a" to listOf("arm64-v8a", "arm64", "aarch64", "arm64_v8a"),
        "armeabi-v7a" to listOf("armeabi-v7a", "armeabi_v7a", "armv7", "arm-v7a", "arm"),
        "x86_64" to listOf("x86_64", "x86-64", "amd64"),
        "x86" to listOf("x86", "i686", "i386")
    )
    
    /**
     * Universal APK patterns (works on all architectures)
     */
    private val universalPatterns = listOf("universal", "all", "fat", "noarch")
    
    /**
     * Get device's supported ABIs in priority order
     */
    private fun getDeviceAbis(): List<String> {
        return AbiProvider.supportedAbis().toList()
    }
    
    /**
     * Get the primary (best) ABI for this device
     */
    private fun getPrimaryAbi(): String {
        return AbiProvider.supportedAbis().firstOrNull() ?: "arm64-v8a"
    }
    
    /**
     * Select the best APK asset based on device architecture
     * 
     * Priority:
     * 1. Exact architecture match for primary ABI
     * 2. Match for any supported ABI (in priority order)
     * 3. Universal APK
     * 4. First APK found (fallback)
     */
    fun selectBestApk(assets: List<ReleaseAsset>): ApkSelectionResult {
        val apkAssets = assets.filter { it.name.lowercase().endsWith(".apk") }
        
        if (apkAssets.isEmpty()) {
            return ApkSelectionResult.NoApkFound
        }
        
        if (apkAssets.size == 1) {
            return ApkSelectionResult.Single(apkAssets[0])
        }
        
        val deviceAbis = getDeviceAbis()
        
        // Try to find exact architecture match in priority order
        for (abi in deviceAbis) {
            val patterns = abiPatterns[abi] ?: continue
            val matchedApk = findApkByPatterns(apkAssets, patterns)
            if (matchedApk != null) {
                return ApkSelectionResult.ExactMatch(abi, matchedApk)
            }
        }
        
        // Try universal APK
        val universalApk = findApkByPatterns(apkAssets, universalPatterns)
        if (universalApk != null) {
            return ApkSelectionResult.Universal(universalApk)
        }
        
        // Fallback: return first APK (likely a universal build without label)
        return ApkSelectionResult.Fallback(apkAssets[0])
    }
    
    /**
     * Find APK matching any of the given patterns
     */
    private fun findApkByPatterns(
        apkAssets: List<ReleaseAsset>,
        patterns: List<String>
    ): ReleaseAsset? {
        val lowerPatterns = patterns.map { it.lowercase() }
        
        return apkAssets.find { asset ->
            val lowerName = asset.name.lowercase()
            lowerPatterns.any { pattern ->
                // Match pattern with common separators: _, -, .
                lowerName.contains("-$pattern") ||
                lowerName.contains("_$pattern") ||
                lowerName.contains(".$pattern") ||
                lowerName.contains("${pattern}-") ||
                lowerName.contains("${pattern}_") ||
                lowerName.contains("${pattern}.")
            }
        }
    }
    
    /**
     * Get a human-readable architecture name for display
     */
    fun getArchitectureDisplayName(): String {
        return when (getPrimaryAbi()) {
            "arm64-v8a" -> "ARM64"
            "armeabi-v7a" -> "ARM32"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> getPrimaryAbi()
        }
    }
    
    /**
     * Check if a specific APK is compatible with this device
     */
    fun isApkCompatible(apkName: String): Boolean {
        val lowerName = apkName.lowercase()
        val deviceAbis = getDeviceAbis()
        
        // Check if it's a universal APK
        if (universalPatterns.any { pattern ->
            lowerName.contains(pattern)
        }) {
            return true
        }
        
        // Check if it matches any device ABI
        for (abi in deviceAbis) {
            val patterns = abiPatterns[abi] ?: continue
            if (patterns.any { pattern ->
                lowerName.contains(pattern.lowercase())
            }) {
                return true
            }
        }
        
        // If no architecture pattern found, assume it's universal
        val hasArchPattern = abiPatterns.values.flatten().any { pattern ->
            lowerName.contains(pattern.lowercase())
        }
        
        return !hasArchPattern
    }
}

internal object AbiProvider {
    fun supportedAbis(): Array<String> = Build.SUPPORTED_ABIS
}

sealed class ApkSelectionResult {
    data object NoApkFound : ApkSelectionResult()
    data class Single(val asset: ReleaseAsset) : ApkSelectionResult()
    data class ExactMatch(val abi: String, val asset: ReleaseAsset) : ApkSelectionResult()
    data class Universal(val asset: ReleaseAsset) : ApkSelectionResult()
    data class Fallback(val asset: ReleaseAsset) : ApkSelectionResult()
}
