package com.samyak.repostore.util

/**
 * Utility for comparing version strings from GitHub releases with installed app versions.
 * Handles various version formats like "v1.2.3", "1.2.3", "2.0.0-beta", "1.0.0-rc1", etc.
 */
object VersionComparator {

    // Packaging / architecture / build flavor suffixes that denote packaging variants,
    // NOT pre-releases. These are treated as equivalent to the base semantic version.
    private val FLAVOR_SUFFIXES = setOf(
        "release",
        "debug",
        "fdroid",
        "f-droid",
        "universal",
        "arm64-v8a",
        "armeabi-v7a",
        "armeabi",
        "x86",
        "x86_64",
        "apk"
    )

    private val PREFIXES = listOf(
        "release-",
        "Release-",
        "version-",
        "Version-",
        "version",
        "Version",
        "ver-",
        "Ver-",
        "ver",
        "Ver",
        "v",
        "V"
    )

    /**
     * Check if the latest version is newer than the current installed version.
     * 
     * @param installedVersion The version string of the installed app (e.g., "1.2.3")
     * @param latestVersion The version string from GitHub release tag (e.g., "v1.2.4")
     * @return true if latestVersion is newer than installedVersion
     */
    fun isNewerVersion(installedVersion: String, latestVersion: String): Boolean {
        val installedCore = normalizeVersion(installedVersion)
        val latestCore = normalizeVersion(latestVersion)
        
        if (installedCore.isEmpty() || latestCore.isEmpty()) {
            return false
        }
        
        // 1. Compare the numeric core (major.minor.patch...) using Long to tolerate
        //    large build numbers that would overflow Int.
        val installedParts = installedCore.split(".")
        val latestParts = latestCore.split(".")
        val maxLength = maxOf(installedParts.size, latestParts.size)
        
        for (i in 0 until maxLength) {
            val installedPart = installedParts.getOrNull(i)?.toLongOrNull() ?: 0L
            val latestPart = latestParts.getOrNull(i)?.toLongOrNull() ?: 0L
            
            when {
                latestPart > installedPart -> return true
                latestPart < installedPart -> return false
            }
        }
        
        // 2. Numeric cores are equal -- fall back to pre-release ordering.
        //    A stable release (no pre-release tag) outranks any pre-release of the
        //    same core (e.g. 1.0.0 is newer than 1.0.0-rc1).
        val installedPre = extractPreRelease(installedVersion)
        val latestPre = extractPreRelease(latestVersion)
        
        return when {
            installedPre.isEmpty() && latestPre.isEmpty() -> false // truly equal
            installedPre.isNotEmpty() && latestPre.isEmpty() -> true  // stable > pre-release
            installedPre.isEmpty() && latestPre.isNotEmpty() -> false // pre-release < stable
            else -> comparePreRelease(latestPre, installedPre) > 0
        }
    }
    
    /**
     * Extract the pre-release identifier (the part after the first '-' or '_' in the
     * core-stripped version), e.g. "v1.2.3-rc2" -> "rc2". Returns "" when absent
     * or when the suffix is a packaging/flavor suffix (e.g. "-release", "-fdroid").
     */
    private fun extractPreRelease(version: String): String {
        var normalized = version.trim()
        
        // Strip build metadata after '+'
        val plusIndex = normalized.indexOf('+')
        if (plusIndex >= 0) {
            normalized = normalized.substring(0, plusIndex)
        }

        for (prefix in PREFIXES) {
            if (normalized.startsWith(prefix, ignoreCase = true)) {
                normalized = normalized.substring(prefix.length)
                break
            }
        }
        
        val sepIndex = normalized.indexOfFirst { it == '-' || it == '_' }
        if (sepIndex < 0) return ""
        val suffix = normalized.substring(sepIndex + 1).lowercase().trim()
        
        // If suffix is a known flavor/packaging suffix, treat as base semantic version
        if (FLAVOR_SUFFIXES.contains(suffix)) {
            return ""
        }
        
        return suffix
    }
    
    /**
     * Compare two non-empty pre-release strings. Splits on common separators and
     * compares segment by segment: numeric segments compare numerically, others
     * lexically. Returns >0 if [a] is newer, <0 if older, 0 if equal.
     */
    private fun comparePreRelease(a: String, b: String): Int {
        val aParts = a.split('.', '-', '_').filter { it.isNotEmpty() }
        val bParts = b.split('.', '-', '_').filter { it.isNotEmpty() }
        val max = maxOf(aParts.size, bParts.size)
        for (i in 0 until max) {
            val ap = aParts.getOrNull(i) ?: return -1 // shorter pre-release is older
            val bp = bParts.getOrNull(i) ?: return 1
            val an = ap.toLongOrNull()
            val bn = bp.toLongOrNull()
            val cmp = when {
                an != null && bn != null -> an.compareTo(bn)
                else -> ap.compareTo(bp)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }
    
    /**
     * Normalize a version string by removing common prefixes, build metadata after '+',
     * and suffixes after '-' or '_'.
     * Examples:
     * - "v1.2.3" -> "1.2.3"
     * - "release-1.2.0" -> "1.2.0"
     * - "v1.2.0-release" -> "1.2.0"
     * - "v1.2.0+build4" -> "1.2.0"
     * - "1.2.3-beta" -> "1.2.3"
     */
    fun normalizeVersion(version: String): String {
        var normalized = version.trim()
        
        // Strip build metadata after '+' (e.g., "1.2.0+build4" -> "1.2.0")
        val plusIndex = normalized.indexOf('+')
        if (plusIndex >= 0) {
            normalized = normalized.substring(0, plusIndex)
        }

        // Remove common prefixes
        for (prefix in PREFIXES) {
            if (normalized.startsWith(prefix, ignoreCase = true)) {
                normalized = normalized.substring(prefix.length)
                break
            }
        }
        
        // Remove suffixes after hyphen (e.g., "-beta", "-rc1", "-alpha", "-release")
        val hyphenIndex = normalized.indexOf('-')
        if (hyphenIndex > 0) {
            normalized = normalized.substring(0, hyphenIndex)
        }
        
        // Remove suffixes after underscore (e.g., "_beta", "_rc1")
        val underscoreIndex = normalized.indexOf('_')
        if (underscoreIndex > 0) {
            normalized = normalized.substring(0, underscoreIndex)
        }
        
        // Keep only digits and dots
        normalized = normalized.replace(Regex("[^0-9.]"), "")
        
        // Remove leading/trailing dots
        normalized = normalized.trim('.')
        
        return normalized
    }
}
