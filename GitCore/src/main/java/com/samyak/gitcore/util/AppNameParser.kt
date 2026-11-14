package com.samyak.gitcore.util

/**
 * Pure-Kotlin parser for extracting human-readable application names from repository
 * resource and configuration files matching URLs produced by [AppNameResolver].
 */
object AppNameParser {

    private val XML_APP_NAME_REGEX = Regex(
        """<string\b[^>]*\bname\s*=\s*["']app_name["'][^>]*>([\s\S]*?)</string>""",
        RegexOption.IGNORE_CASE
    )
    private val XML_TAG_STRIP_REGEX = Regex("<[^>]+>")

    private val CSPROJ_TITLE_REGEX = Regex(
        """<(?:ApplicationTitle|Product|AssemblyName)>([^<]+)</(?:ApplicationTitle|Product|AssemblyName)>""",
        RegexOption.IGNORE_CASE
    )
    private val APPX_DISPLAY_NAME_REGEX = Regex(
        """<DisplayName>([^<]+)</DisplayName>""",
        RegexOption.IGNORE_CASE
    )

    private val GRADLE_ROOT_PROJECT_REGEX = Regex(
        """rootProject\s*\.\s*name\s*=\s*["']([^"'\r\n]+)["']"""
    )

    private val JSON_EXPO_NAME_REGEX = Regex(
        """"expo"\s*:\s*\{[^{}]*"name"\s*:\s*"([^"\r\n]+)"""",
        RegexOption.DOT_MATCHES_ALL
    )
    private val JSON_DISPLAY_NAME_REGEX = Regex(""""displayName"\s*:\s*"([^"\r\n]+)"""")
    private val JSON_APP_NAME_REGEX = Regex(""""appName"\s*:\s*"([^"\r\n]+)"""")
    private val JSON_TOP_NAME_REGEX = Regex(""""name"\s*:\s*"([^"\r\n]+)"""")

    private val YAML_ROOT_NAME_REGEX = Regex(
        """^name\s*:\s*(?:"([^"\r\n]+)"|'([^'\r\n]+)'|([^\s#\r\n]+))""",
        RegexOption.MULTILINE
    )

    private val TS_APP_NAME_REGEX = Regex("""\bappName\s*:\s*["']([^"'\r\n]+)["']""")
    private val TS_NAME_REGEX = Regex("""\bname\s*:\s*["']([^"'\r\n]+)["']""")

    private val MARKDOWN_H1_ATX_REGEX = Regex("""^#\s+(.+)$""", RegexOption.MULTILINE)
    private val MARKDOWN_H1_SETEXT_REGEX = Regex("""^([^\r\n#=-][^\r\n]*)\r?\n=+\s*$""", RegexOption.MULTILINE)
    private val MARKDOWN_BADGE_LINK_REGEX = Regex("""\[!\[[^\]]*]\([^)]*\)]\([^)]*\)""")
    private val MARKDOWN_IMAGE_REGEX = Regex("""!\[[^\]]*]\([^)]*\)""")
    private val MARKDOWN_LINK_REGEX = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val NUMERIC_ENTITY_REGEX = Regex("""&#(x?[0-9a-fA-F]+);""")

    fun parse(url: String, content: String): String? {
        if (content.isBlank()) return null
        val pathWithoutQuery = url.substringBefore('?').substringBefore('#').lowercase()

        val rawCandidate = when {
            pathWithoutQuery.endsWith(".txt") -> parseTitleTxt(content)
            pathWithoutQuery.endsWith("package.appxmanifest") -> parseAppxManifest(content)
            pathWithoutQuery.endsWith(".csproj") -> parseCsproj(content)
            pathWithoutQuery.endsWith(".xml") -> parseAndroidXml(content)
            pathWithoutQuery.endsWith(".json") -> parseJson(content)
            pathWithoutQuery.endsWith(".yaml") || pathWithoutQuery.endsWith(".yml") -> parseYaml(content)
            pathWithoutQuery.endsWith("settings.gradle") || pathWithoutQuery.endsWith("settings.gradle.kts") ->
                parseSettingsGradle(content)
            pathWithoutQuery.endsWith(".ts") || pathWithoutQuery.endsWith(".js") -> parseTsConfig(content)
            pathWithoutQuery.endsWith(".md") -> parseReadmeHeading(content)
            else -> null
        }

        return normalizeAndValidate(rawCandidate)
    }

    private fun parseTitleTxt(content: String): String? {
        return content.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun parseAndroidXml(content: String): String? {
        val cleaned = SourceCommentStripper.stripXmlComments(content)
        val match = XML_APP_NAME_REGEX.find(cleaned) ?: return null
        val inner = XML_TAG_STRIP_REGEX.replace(match.groupValues[1], "").trim()
        if (inner.startsWith("@string/", ignoreCase = true) || inner.startsWith("@android:", ignoreCase = true)) {
            return null
        }
        return unescapeAndroidXmlString(inner)
    }

    private fun parseAppxManifest(content: String): String? {
        val cleaned = SourceCommentStripper.stripXmlComments(content)
        val value = APPX_DISPLAY_NAME_REGEX.find(cleaned)?.groupValues?.get(1)?.trim() ?: return null
        if (value.startsWith("ms-resource:", ignoreCase = true)) return null
        return unescapeAndroidXmlString(value)
    }

    private fun parseCsproj(content: String): String? {
        val cleaned = SourceCommentStripper.stripXmlComments(content)
        val tags = listOf("ApplicationTitle", "Product", "AssemblyName")
        for (tag in tags) {
            val regex = Regex("""<$tag>([^<]+)</$tag>""", RegexOption.IGNORE_CASE)
            val match = regex.find(cleaned)?.groupValues?.get(1)?.trim()
            if (!match.isNullOrBlank() && !match.contains("$(")) {
                return unescapeAndroidXmlString(match)
            }
        }
        // Fallback match in case casing/structure differs
        val fallback = CSPROJ_TITLE_REGEX.find(cleaned)?.groupValues?.get(1)?.trim()
        return fallback?.takeIf { !it.contains("$(") }?.let(::unescapeAndroidXmlString)
    }

    private fun parseJson(content: String): String? {
        val cleaned = SourceCommentStripper.stripCStyleComments(content)
        return JSON_EXPO_NAME_REGEX.find(cleaned)?.groupValues?.get(1)
            ?: JSON_DISPLAY_NAME_REGEX.find(cleaned)?.groupValues?.get(1)
            ?: JSON_APP_NAME_REGEX.find(cleaned)?.groupValues?.get(1)
            ?: JSON_TOP_NAME_REGEX.find(cleaned)?.groupValues?.get(1)
    }

    private fun parseYaml(content: String): String? {
        val match = YAML_ROOT_NAME_REGEX.find(content) ?: return null
        return match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
    }

    private fun parseSettingsGradle(content: String): String? {
        val cleaned = SourceCommentStripper.stripCStyleComments(content)
        return GRADLE_ROOT_PROJECT_REGEX.find(cleaned)?.groupValues?.get(1)
    }

    private fun parseTsConfig(content: String): String? {
        val cleaned = SourceCommentStripper.stripCStyleComments(content)
        return TS_APP_NAME_REGEX.find(cleaned)?.groupValues?.get(1)
            ?: TS_NAME_REGEX.find(cleaned)?.groupValues?.get(1)
    }

    private fun parseReadmeHeading(content: String): String? {
        val rawHeading = MARKDOWN_H1_ATX_REGEX.find(content)?.groupValues?.get(1)
            ?: MARKDOWN_H1_SETEXT_REGEX.find(content)?.groupValues?.get(1)
            ?: return null

        var text = rawHeading.trim().removeSuffix("#").trim()
        // Remove clickable badge images first: [![alt](img)](link)
        text = MARKDOWN_BADGE_LINK_REGEX.replace(text, "")
        // Remove standalone images: ![alt](img)
        text = MARKDOWN_IMAGE_REGEX.replace(text, "")
        // Convert markdown links [Label](url) -> Label
        text = MARKDOWN_LINK_REGEX.replace(text, "$1")
        // Strip inline HTML tags & backticks
        text = XML_TAG_STRIP_REGEX.replace(text, "")
        text = text.replace("`", "").trim()
        return text.takeIf { it.isNotBlank() }
    }

    internal fun unescapeAndroidXmlString(raw: String): String {
        var result = raw
            .replace("\\'", "'")
            .replace("\\\"", "\"")
            .replace("\\n", " ")
            .replace("\\t", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")

        result = NUMERIC_ENTITY_REGEX.replace(result) { match ->
            val token = match.groupValues[1]
            val codePoint = if (token.startsWith("x", ignoreCase = true)) {
                token.substring(1).toIntOrNull(16)
            } else {
                token.toIntOrNull(10)
            }
            if (codePoint != null && Character.isValidCodePoint(codePoint)) {
                String(Character.toChars(codePoint))
            } else {
                match.value
            }
        }
        return result.trim()
    }

    internal fun normalizeAndValidate(candidate: String?): String? {
        val normalized = candidate
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.removeSurrounding("\"")
            ?.trim()
            ?: return null

        if (normalized.length < 2 || normalized.length > 60) return null
        if (normalized.startsWith("@string/", ignoreCase = true)) return null
        if (normalized.contains("\${") || normalized.contains("$(")) return null
        if (normalized.startsWith("http://", ignoreCase = true) ||
            normalized.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        return normalized
    }
}
