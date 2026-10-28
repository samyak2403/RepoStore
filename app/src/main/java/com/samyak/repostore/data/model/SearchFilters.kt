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
    val searchInReadme: Boolean = true // Also search README content
) {
    /**
     * Build the GitHub search query string from filters
     * Optimized for Play Store-like relevance matching
     */
    fun buildQuery(userQuery: String = query): String {
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
        
        // Language filter
        language?.let { lang ->
            if (lang.isNotBlank()) {
                queryParts.add("language:$lang")
            }
        }
        
        // Minimum stars - helps filter quality repos
        minStars?.let { stars ->
            if (stars > 0) {
                queryParts.add("stars:>=$stars")
            }
        }
        
        // Updated within timeframe
        updatedWithin?.let { timeframe ->
            val dateFilter = timeframe.toQueryString()
            if (dateFilter.isNotBlank()) {
                queryParts.add(dateFilter)
            }
        }
        
        // Exclude archived repos unless explicitly included
        if (!includeArchived) {
            queryParts.add("archived:false")
        }
        
        return queryParts.joinToString(" ")
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
