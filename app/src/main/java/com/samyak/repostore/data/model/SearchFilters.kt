package com.samyak.repostore.data.model

/**
 * Advanced search filters for GitHub repository search
 */
data class SearchFilters(
    val query: String = "",
    val sortBy: SortOption = SortOption.BEST_MATCH,
    val language: String? = null,
    val minStars: Int? = null,
    val hasReleases: Boolean = false, // Default: Show all repos (APK filter is opt-in)
    val hasApk: Boolean = false,
    val includeArchived: Boolean = false,
    val updatedWithin: UpdatedWithin? = null,
    val topics: List<String> = listOf("android"), // Default: Preserves "android" topic
    val searchInReadme: Boolean = true, // Also search README content on broad search
    val androidOnly: Boolean = true // Default: Try Android-topic repositories first, broaden automatically if sparse
) {
    /**
     * Build the primary GitHub search query string from filters.
     * When [androidOnly] is true, includes `topic:android` (defaulting empty [topics] to "android").
     * When [androidOnly] is false, delegates to [buildBroadQuery] so untagged GitHub repos match directly.
     */
    fun buildQuery(userQuery: String = query): String {
        if (!androidOnly) {
            return buildBroadQuery(userQuery = userQuery, includeReadme = searchInReadme)
        }

        val queryParts = mutableListOf<String>()

        // User's search term - search in name and description
        val effectiveQuery = if (userQuery.isNotBlank()) userQuery.trim() else query.trim()
        if (effectiveQuery.isNotBlank()) {
            queryParts.add("$effectiveQuery in:name,description")
        }

        // Add topic filters - ensure "android" is preserved or defaulted when not explicitly overridden
        val effectiveTopics = if (topics.isEmpty()) listOf("android") else topics
        effectiveTopics.forEach { topic ->
            queryParts.add("topic:$topic")
        }

        appendCommonQualifiers(queryParts)
        return queryParts.joinToString(" ")
    }

    /**
     * Build a broader GitHub search query that drops the `topic:android` requirement
     * and optionally searches README content in addition to name & description.
     */
    fun buildBroadQuery(
        userQuery: String = query,
        includeReadme: Boolean = searchInReadme
    ): String {
        val queryParts = mutableListOf<String>()
        val effectiveQuery = if (userQuery.isNotBlank()) userQuery.trim() else query.trim()

        if (effectiveQuery.isNotBlank()) {
            val fields = if (includeReadme) "in:name,description,readme" else "in:name,description"
            queryParts.add("$effectiveQuery $fields")
        }

        appendCommonQualifiers(queryParts)
        return queryParts.joinToString(" ")
    }

    /**
     * Bare keyword fallback query without `in:...` qualifiers or `topic:android`,
     * used only if both primary and broad field-scoped searches return empty results.
     */
    fun buildBareFallbackQuery(userQuery: String = query): String {
        val queryParts = mutableListOf<String>()
        val effectiveQuery = if (userQuery.isNotBlank()) userQuery.trim() else query.trim()
        if (effectiveQuery.isNotBlank()) {
            queryParts.add(effectiveQuery)
        }
        appendCommonQualifiers(queryParts)
        return queryParts.joinToString(" ")
    }

    private fun appendCommonQualifiers(queryParts: MutableList<String>) {
        language?.let { lang ->
            if (lang.isNotBlank()) {
                queryParts.add("language:$lang")
            }
        }

        minStars?.let { stars ->
            if (stars > 0) {
                queryParts.add("stars:>=$stars")
            }
        }

        updatedWithin?.let { timeframe ->
            val dateFilter = timeframe.toQueryString()
            if (dateFilter.isNotBlank()) {
                queryParts.add(dateFilter)
            }
        }

        if (!includeArchived) {
            queryParts.add("archived:false")
        }
    }

    companion object {
        val DEFAULT = SearchFilters()

        val POPULAR_LANGUAGES = listOf(
            "Kotlin", "Java", "C++", "C", "Dart", "Rust", "Go"
        )

        val MIN_STAR_OPTIONS = listOf(0, 10, 50, 100, 500, 1000, 5000)
    }
}

/**
 * Sort options for search results
 */
enum class SortOption(val apiValue: String, val displayName: String) {
    BEST_MATCH("", "Best Match"),
    STARS("stars", "Most Stars"),
    FORKS("forks", "Most Forks"),
    UPDATED("updated", "Recently Updated"),
    HELP_WANTED_ISSUES("help-wanted-issues", "Help Wanted");

    companion object {
        fun fromApiValue(value: String): SortOption {
            return values().find { it.apiValue == value } ?: BEST_MATCH
        }
    }
}

/**
 * Time-based filter for when repos were last updated
 */
enum class UpdatedWithin(val displayName: String, private val daysAgo: Int?) {
    ANY_TIME("Any time", null),
    LAST_WEEK("Last week", 7),
    LAST_MONTH("Last month", 30),
    LAST_3_MONTHS("Last 3 months", 90),
    LAST_YEAR("Last year", 365);

    fun toQueryString(): String {
        if (daysAgo == null) return ""

        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return "pushed:>=${sdf.format(calendar.time)}"
    }
}
