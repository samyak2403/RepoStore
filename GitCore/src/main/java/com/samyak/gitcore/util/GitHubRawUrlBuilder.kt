package com.samyak.gitcore.util

/**
 * Shared helper for validating GitHub repository coordinates, normalizing branches,
 * classifying ecosystems by primary repository language, and constructing properly
 * encoded `raw.githubusercontent.com` candidate URLs.
 */
internal object GitHubRawUrlBuilder {

    const val RAW_BASE_URL = "https://raw.githubusercontent.com"
    val DEFAULT_FALLBACK_BRANCHES = listOf("main", "master")

    enum class Ecosystem {
        CROSS_PLATFORM,
        NATIVE_ANDROID,
        DOTNET_MAUI
    }

    fun classifyEcosystem(rawLanguage: String?): Ecosystem {
        return when (rawLanguage?.trim()?.lowercase()) {
            "dart", "javascript", "typescript", "html", "vue", "css", "svelte", "coffeescript" ->
                Ecosystem.CROSS_PLATFORM
            "c#", "csharp" ->
                Ecosystem.DOTNET_MAUI
            else ->
                Ecosystem.NATIVE_ANDROID
        }
    }

    /**
     * Resolves branch candidates in priority order:
     * - When [defaultBranch] is non-blank, uses that exact trimmed branch.
     * - When [defaultBranch] is null or blank, falls back to [fallbackBranches] (defaults to `main`, `master`).
     */
    fun resolveBranches(
        defaultBranch: String?,
        fallbackBranches: List<String> = DEFAULT_FALLBACK_BRANCHES
    ): List<String> {
        val trimmed = defaultBranch?.trim()?.trim('/')
        if (!trimmed.isNullOrBlank()) {
            return listOf(trimmed)
        }
        return fallbackBranches
            .map { it.trim().trim('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { DEFAULT_FALLBACK_BRANCHES }
    }

    /**
     * Builds deduplicated `raw.githubusercontent.com` URLs for valid repository coordinates.
     * Returns an empty list if [owner] or [name] is blank or contains path traversal segments.
     */
    fun buildUrls(
        owner: String,
        name: String,
        defaultBranch: String?,
        relativePaths: List<String>,
        fallbackBranches: List<String> = DEFAULT_FALLBACK_BRANCHES
    ): List<String> {
        val cleanOwner = sanitizeRepoSegment(owner) ?: return emptyList()
        val cleanName = sanitizeRepoSegment(name) ?: return emptyList()
        val branches = resolveBranches(defaultBranch, fallbackBranches)
        val cleanPaths = relativePaths
            .asSequence()
            .map { it.trim().trimStart('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

        if (cleanPaths.isEmpty()) return emptyList()

        val encodedOwner = encodePathSegment(cleanOwner)
        val encodedName = encodePathSegment(cleanName)

        return buildList(branches.size * cleanPaths.size) {
            for (branch in branches) {
                val encodedBranch = encodeSlashPreservedPath(branch)
                for (path in cleanPaths) {
                    val encodedPath = encodeSlashPreservedPath(path)
                    add("$RAW_BASE_URL/$encodedOwner/$encodedName/$encodedBranch/$encodedPath")
                }
            }
        }
    }

    private fun sanitizeRepoSegment(value: String): String? {
        val trimmed = value.trim().trim('/')
        if (trimmed.isBlank() || trimmed == "." || trimmed == ".." || trimmed.contains('/')) {
            return null
        }
        return trimmed
    }

    private fun encodeSlashPreservedPath(path: String): String {
        return path.split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { encodePathSegment(it) }
    }

    /**
     * Percent-encodes RFC 3986 path-segment unsafe characters (such as spaces, `#`, `?`, `%`)
     * without disturbing standard URL path characters (`-`, `_`, `.`, `~`).
     */
    private fun encodePathSegment(segment: String): String {
        val bytes = segment.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val code = b.toInt() and 0xFF
            if (isUnreservedUrlChar(code)) {
                sb.append(code.toChar())
            } else {
                sb.append('%')
                sb.append(HEX_DIGITS[code ushr 4])
                sb.append(HEX_DIGITS[code and 0x0F])
            }
        }
        return sb.toString()
    }

    private fun isUnreservedUrlChar(code: Int): Boolean {
        return (code in 'A'.code..'Z'.code) ||
            (code in 'a'.code..'z'.code) ||
            (code in '0'.code..'9'.code) ||
            code == '-'.code ||
            code == '_'.code ||
            code == '.'.code ||
            code == '~'.code
    }

    private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()
}
