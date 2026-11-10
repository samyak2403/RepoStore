package com.samyak.repostore.util

/**
 * Utility class to parse GitHub URLs and extract owner and repository names.
 */
object GitHubUrlParser {
    private val GITHUB_URL_PATTERN = Regex(
        """^(?:https?://)?(?:www\.)?github\.com/([\w.-]+)/([\w.-]+)(?:[/?#].*)?$""",
        RegexOption.IGNORE_CASE
    )

    data class RepoInfo(val owner: String, val name: String)

    /**
     * Parses a string to check if it's a valid GitHub repository URL.
     * @param input The search query or URL
     * @return RepoInfo if valid GitHub URL, null otherwise
     */
    fun parse(input: String): RepoInfo? {
        val trimmedInput = input.trim()
        val matchResult = GITHUB_URL_PATTERN.matchEntire(trimmedInput)
        
        return matchResult?.let {
            val owner = it.groupValues[1]
            val name = it.groupValues[2].removeSuffix(".git")
            RepoInfo(owner, name)
        }
    }

    /**
     * Checks if the given input is a GitHub URL.
     */
    fun isGitHubUrl(input: String): Boolean {
        return parse(input) != null
    }
}
