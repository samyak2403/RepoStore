package com.samyak.repostore.util

/**
 * Utility for comparing version strings from GitHub releases with installed app versions.
 * Handles various version formats like "v1.2.3", "1.2.3", "2.0.0-beta", "1.0.0-rc1",
 * app-prefixed tags ("termux-v0.118.0", "app-1.2.3"), and version codes in parens ("1.2.0 (42)").
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

    private val SEMVER_REGEX = Regex("""(\d+(?:\.\d+)+)""")
    private val SINGLE_INT_REGEX = Regex("""\d+""")

    /**
     * Check if the latest version is newer than the current installed version.
     * 
     * @param installedVersion The version string of the installed app (e.g., "1.2.3" or "1.2.3 (42)")
     * @param latestVersion The version string from GitHub release tag (e.g., "v1.2.4" or "app-v1.2.4")
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
     * Extract the pre-release identifier (the part after the semantic version digits),
     * e.g. "v1.2.3-rc2" -> "rc2". Returns "" when absent or when the suffix is a
     * packaging/flavor suffix (e.g. "-release", "-fdroid", "-universal").
     */
    private fun extractPreRelease(version: String): String {
        val trimmed = version.trim()
        val match = SEMVER_REGEX.find(trimmed) ?: return ""
        val afterSemver = trimmed.substring(match.range.last + 1)
        
        // Strip build metadata after '+', comment/code in '(', '[', or spaces
        val cleanAfter = afterSemver
            .substringBefore('+')
            .substringBefore('(')
            .substringBefore('[')
            .substringBefore(' ')
            .trimStart('-', '_')
            .lowercase()
            .trim()
        
        if (cleanAfter.isEmpty() || FLAVOR_SUFFIXES.contains(cleanAfter)) {
            return ""
        }
        
        return cleanAfter
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
     * Normalize a version string by extracting the core numeric version (e.g. "1.2.3").
     * Handles prefixes ("v", "release-", "termux-v"), build metadata ("+build4"),
     * version codes in parentheses ("1.2.0 (42)"), and flavors ("-universal").
     */
    fun normalizeVersion(version: String): String {
        val trimmed = version.trim()
        val semverMatch = SEMVER_REGEX.find(trimmed)
        if (semverMatch != null) {
            return semverMatch.groupValues[1]
        }
        val singleIntMatch = SINGLE_INT_REGEX.find(trimmed)
        return singleIntMatch?.value ?: ""
    }
}
