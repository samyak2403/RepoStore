package com.samyak.gitcore.util

/**
 * Pure-Kotlin parser and validator for extracting Android `applicationId` / package names
 * from Gradle scripts, `AndroidManifest.xml`, cross-platform JSON/TS configs, and `.csproj` files.
 */
object PackageIdParser {

    // Match explicit applicationId assignment while excluding testApplicationId / myApplicationId.
    private val APPLICATION_ID_REGEX = Regex(
        """\bapplicationId\s*(?:=|\s)\s*["']([a-zA-Z][a-zA-Z0-9_.]+)["']"""
    )

    // Variable reference in Gradle, e.g.: applicationId = APP_ID or applicationId appId
    private val APPLICATION_ID_VAR_REGEX = Regex(
        """\bapplicationId\s*(?:=|\s)\s*([a-zA-Z_][a-zA-Z0-9_]*)"""
    )

    // Optional applicationIdSuffix ".foss" inside defaultConfig only
    private val APPLICATION_ID_SUFFIX_REGEX = Regex(
        """\bapplicationIdSuffix\s*(?:=|\s)\s*["'](\.[a-zA-Z0-9_.]+)["']"""
    )

    private val NAMESPACE_REGEX = Regex(
        """\bnamespace\s*(?:=|\s)\s*["']([a-zA-Z][a-zA-Z0-9_.]+)["']"""
    )

    private val VAL_APP_ID_REGEX = Regex(
        """(?:val|var|def|const\s+val)\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*=\s*["']([a-zA-Z][a-zA-Z0-9_.]+)["']"""
    )

    private val WELL_KNOWN_VAR_NAMES = setOf("appid", "applicationid", "packagename", "app_id", "application_id")

    private val MANIFEST_PACKAGE_REGEX = Regex(
        """<manifest\b[^>]*\bpackage\s*=\s*["']([a-zA-Z][a-zA-Z0-9_.]+)["']""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val EXPO_ANDROID_PACKAGE_REGEX = Regex(
        """"android"\s*:\s*\{[^{}]*"package"\s*:\s*"([a-zA-Z][a-zA-Z0-9_.]+)"""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val CAPACITOR_APP_ID_JSON_REGEX = Regex(
        """"appId"\s*:\s*"([a-zA-Z][a-zA-Z0-9_.]+)"""",
    )

    private val CAPACITOR_APP_ID_TS_REGEX = Regex(
        """\bappId\s*:\s*["']([a-zA-Z][a-zA-Z0-9_.]+)["']"""
    )

    private val CSPROJ_APPLICATION_ID_REGEX = Regex(
        """<ApplicationId>([a-zA-Z][a-zA-Z0-9_.]+)</ApplicationId>""",
        RegexOption.IGNORE_CASE
    )

    fun parse(url: String, content: String): String? {
        if (content.isBlank()) return null
        val path = url.substringBefore('?').substringBefore('#').lowercase()

        return when {
            path.endsWith(".gradle") || path.endsWith(".gradle.kts") -> parseFromGradle(content)
            path.endsWith("androidmanifest.xml") -> parseFromManifest(content)
            path.endsWith(".json") -> parseFromJsonConfig(content)
            path.endsWith(".ts") || path.endsWith(".js") -> parseFromTsConfig(content)
            path.endsWith(".csproj") -> parseFromCsproj(content)
            else -> null
        }
    }

    /**
     * Extract `applicationId` from Gradle build script content using balanced-brace extraction
     * for `defaultConfig { ... }`, with variable lookup and `namespace` fallback.
     */
    fun parseFromGradle(content: String): String? {
        val cleaned = SourceCommentStripper.stripCStyleComments(content)

        // Pre-collect string constants in the file so `applicationId = APP_ID` resolves accurately.
        val stringVars = mutableMapOf<String, String>()
        for (m in VAL_APP_ID_REGEX.findAll(cleaned)) {
            val varName = m.groupValues[1]
            val varValue = m.groupValues[2]
            if (isValidPackageId(varValue)) {
                stringVars[varName] = varValue
            }
        }

        // Strategy 1: balanced defaultConfig { ... } block (supports arbitrary nesting depth)
        val defaultConfigBody = SourceCommentStripper.extractBalancedBlock(cleaned, "defaultConfig")
        if (defaultConfigBody != null) {
            val direct = APPLICATION_ID_REGEX.find(defaultConfigBody)?.groupValues?.get(1)
            val resolvedDirect = direct ?: resolveVarReference(defaultConfigBody, stringVars)
            if (resolvedDirect != null) {
                val suffix = APPLICATION_ID_SUFFIX_REGEX.find(defaultConfigBody)?.groupValues?.get(1)
                val combined = if (suffix != null) "$resolvedDirect$suffix" else resolvedDirect
                if (isValidPackageId(combined)) return combined
            }
        }

        // Strategy 2: explicit applicationId anywhere outside comments
        for (match in APPLICATION_ID_REGEX.findAll(cleaned)) {
            val candidate = match.groupValues[1]
            if (isValidPackageId(candidate)) return candidate
        }

        // Strategy 3: namespace declaration (modern AGP fallback)
        for (match in NAMESPACE_REGEX.findAll(cleaned)) {
            val candidate = match.groupValues[1]
            if (isValidPackageId(candidate)) return candidate
        }

        // Strategy 4: well-known appId constant declarations
        for ((varName, varValue) in stringVars) {
            if (varName.lowercase() in WELL_KNOWN_VAR_NAMES && isValidPackageId(varValue)) {
                return varValue
            }
        }

        return null
    }

    private fun resolveVarReference(block: String, stringVars: Map<String, String>): String? {
        for (match in APPLICATION_ID_VAR_REGEX.findAll(block)) {
            val identifier = match.groupValues[1]
            val resolved = stringVars[identifier]
            if (resolved != null && isValidPackageId(resolved)) {
                return resolved
            }
        }
        return null
    }

    fun parseFromManifest(content: String): String? {
        val cleaned = SourceCommentStripper.stripXmlComments(content)
        val pkg = MANIFEST_PACKAGE_REGEX.find(cleaned)?.groupValues?.get(1)
        return pkg?.takeIf(::isValidPackageId)
    }

    fun parseFromJsonConfig(content: String): String? {
        val cleaned = SourceCommentStripper.stripCStyleComments(content)
        val candidate = EXPO_ANDROID_PACKAGE_REGEX.find(cleaned)?.groupValues?.get(1)
            ?: CAPACITOR_APP_ID_JSON_REGEX.find(cleaned)?.groupValues?.get(1)
        return candidate?.takeIf(::isValidPackageId)
    }

    fun parseFromTsConfig(content: String): String? {
        val cleaned = SourceCommentStripper.stripCStyleComments(content)
        val candidate = CAPACITOR_APP_ID_TS_REGEX.find(cleaned)?.groupValues?.get(1)
        return candidate?.takeIf(::isValidPackageId)
    }

    fun parseFromCsproj(content: String): String? {
        val cleaned = SourceCommentStripper.stripXmlComments(content)
        val candidate = CSPROJ_APPLICATION_ID_REGEX.find(cleaned)?.groupValues?.get(1)
        return candidate?.takeIf(::isValidPackageId)
    }

    fun isValidPackageId(id: String): Boolean {
        if (id.length < 5 || id.length > 100) return false
        if (id.startsWith('.') || id.endsWith('.') || id.contains("..")) return false

        val segments = id.split('.')
        if (segments.size < 2) return false

        for (segment in segments) {
            if (segment.isEmpty()) return false
            if (!segment[0].isLetter()) return false
            if (!segment.all { it.isLetterOrDigit() || it == '_' }) return false
        }

        val lower = id.lowercase()
        val blockedPrefixes = listOf(
            "com.android.tools",
            "com.android.application",
            "com.android.library",
            "org.gradle",
            "com.github.",
            "org.jetbrains.",
            "androidx."
        )
        if (blockedPrefixes.any { lower == it || lower.startsWith("$it.") || (it.endsWith(".") && lower.startsWith(it)) }) {
            return false
        }
        return true
    }
}
