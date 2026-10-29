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
        "armeabi-v7a" to listOf("armeabi-v7a", "armeabi_v7a", "armv7", "arm-v7a", "armeabi"),
        "x86_64" to listOf("x86_64", "x86-64", "x64", "amd64"),
        "x86" to listOf("x86", "x32", "i686", "i386")
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
     * Detect specific ABI targeting in the APK name.
     * Returns null if the APK has no architecture-specific markers.
     */
    fun detectAssetAbi(apkName: String): String? {
        val lowerName = apkName.lowercase()
        for ((abi, patterns) in abiPatterns) {
            if (patterns.any { pattern -> nameContainsToken(lowerName, pattern) }) {
                return abi
            }
        }
        return null
    }

    /**
     * Returns true if this APK is an explicit universal build
     * and does NOT target a specific restricted ABI.
     */
    fun isUniversalApk(apkName: String): Boolean {
        if (detectAssetAbi(apkName) != null) {
            return false
        }
        val lowerName = apkName.lowercase()
        return universalPatterns.any { pattern -> nameContainsToken(lowerName, pattern) }
    }
    
    /**
     * Select the best APK asset based on device architecture
     * 
     * Priority:
     * 1. Exact architecture match for primary ABI
     * 2. Match for any supported ABI (in priority order)
     * 3. Universal APK (must not target a different specific ABI)
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
        
        // Try universal APK (must not have an explicit ABI)
        val universalApk = apkAssets.find { isUniversalApk(it.name) }
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
            lowerPatterns.any { pattern -> nameContainsToken(lowerName, pattern) }
        }
    }

    /**
     * Returns true only when [token] appears in [lowerName] as a whole token — that is,
     * bounded by a non-alphanumeric separator (-, _, .) or the start/end of the string.
     * This avoids false positives like "install.apk" matching "all" or "alarm.apk"
     * matching "arm".
     */
    fun nameContainsToken(lowerName: String, token: String): Boolean {
        val t = token.lowercase()
        if (t.isEmpty()) return false
        var idx = lowerName.indexOf(t)
        while (idx >= 0) {
            val beforeIdx = idx - 1
            val afterIdx = idx + t.length
            val boundedBefore = beforeIdx < 0 || !lowerName[beforeIdx].isLetterOrDigit()
            val boundedAfter = afterIdx >= lowerName.length || !lowerName[afterIdx].isLetterOrDigit()
            if (boundedBefore && boundedAfter) return true
            idx = lowerName.indexOf(t, idx + 1)
        }
        return false
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
     * Check if a specific APK is compatible with this device.
     * If the APK targets a specific ABI (e.g. arm64-v8a), it is only compatible if
     * the device supports that ABI, even if the word "universal" appears in the app name.
     */
    fun isApkCompatible(apkName: String): Boolean {
        val specificAbi = detectAssetAbi(apkName)
        if (specificAbi != null) {
            return getDeviceAbis().contains(specificAbi)
        }
        
        val lowerName = apkName.lowercase()
        if (nameContainsToken(lowerName, "mips") || nameContainsToken(lowerName, "mips64") ||
            (nameContainsToken(lowerName, "riscv") && !getDeviceAbis().contains("riscv64"))) {
            return false
        }
        
        // If it has no architecture-specific markers, it is universal/compatible
        return true
    }

    /**
     * Returns a clear, human-readable variant title for the APK (e.g. "ARM64 (v8a)",
     * "x86_64 (64-bit)", "Universal", or clean filename without extension).
     */
    fun getVariantTitle(apkName: String): String {
        val abi = detectAssetAbi(apkName)
        val lower = apkName.lowercase()
        
        val archLabel = when (abi) {
            "arm64-v8a" -> "ARM64 (v8a)"
            "armeabi-v7a" -> "ARMv7 (32-bit)"
            "x86_64" -> "x86_64 (64-bit)"
            "x86" -> "x86 (32-bit)"
            else -> {
                if (isUniversalApk(apkName)) {
                    "Universal"
                } else {
                    null
                }
            }
        }
        
        val flavorLabel = when {
            nameContainsToken(lower, "fdroid") || nameContainsToken(lower, "f-droid") -> "F-Droid"
            nameContainsToken(lower, "play") || nameContainsToken(lower, "playstore") -> "Play Store"
            nameContainsToken(lower, "debug") -> "Debug"
            nameContainsToken(lower, "nightly") -> "Nightly"
            nameContainsToken(lower, "beta") -> "Beta"
            else -> null
        }
        
        if (archLabel != null) {
            return if (flavorLabel != null) "$archLabel ($flavorLabel)" else archLabel
        }
        
        val cleanName = apkName.removeSuffix(".apk").removeSuffix(".APK")
        return when {
            flavorLabel != null -> flavorLabel
            cleanName.equals("app", ignoreCase = true) ||
            cleanName.equals("release", ignoreCase = true) ||
            cleanName.equals("app-release", ignoreCase = true) -> "Universal (All Devices)"
            else -> cleanName
        }
    }

    /**
     * Returns a concise description of the architecture (e.g. "64-bit ARM", "64-bit x86", "Universal").
     */
    fun getArchitectureDescription(apkName: String): String {
        val abi = detectAssetAbi(apkName)
        return when (abi) {
            "arm64-v8a" -> "64-bit ARM"
            "armeabi-v7a" -> "32-bit ARM"
            "x86_64" -> "64-bit x86"
            "x86" -> "32-bit x86"
            else -> if (isUniversalApk(apkName)) "Universal" else "Standard"
        }
    }

    /**
     * Returns a short architecture tag suitable for button subtitles (e.g. "ARM64", "x86_64", "Universal").
     */
    fun getShortArchitectureName(apkName: String): String {
        val abi = detectAssetAbi(apkName)
        return when (abi) {
            "arm64-v8a" -> "ARM64"
            "armeabi-v7a" -> "ARM32"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> when {
                isUniversalApk(apkName) -> "Universal"
                apkName.lowercase().contains("plugin") -> "Plugin"
                else -> "APK"
            }
        }
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
