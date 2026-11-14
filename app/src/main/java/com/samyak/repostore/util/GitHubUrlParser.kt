package com.samyak.repostore.util

/**
 * Utility class to parse GitHub URLs and owner/repo references and extract owner and repository names.
 */
object GitHubUrlParser {
    private val GITHUB_URL_PATTERN = Regex(
        """^(?:https?://)?(?:www\.)?github\.com/([\w.-]+)/([\w.-]+)(?:[/?#].*)?$""",
        RegexOption.IGNORE_CASE
    )

    private val OWNER_REPO_PATTERN = Regex(
        """^([A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?)/([A-Za-z0-9._-]+)$"""
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
     * Parses either a full GitHub repository URL or an "owner/repo" shorthand reference.
     */
    fun parseOwnerRepo(input: String): RepoInfo? {
        parse(input)?.let { return it }
        val trimmedInput = input.trim().trimEnd('/')
        val matchResult = OWNER_REPO_PATTERN.matchEntire(trimmedInput) ?: return null
        val owner = matchResult.groupValues[1]
        val name = matchResult.groupValues[2].removeSuffix(".git")
        if (name.isBlank() || name == "." || name == "..") return null
        return RepoInfo(owner, name)
    }

    /**
     * Checks if the given input is a GitHub URL.
     */
    fun isGitHubUrl(input: String): Boolean {
        return parse(input) != null
    }

    /**
     * Checks if the given input is either a full GitHub URL or an "owner/repo" shorthand reference.
     */
    fun isRepoReference(input: String): Boolean {
        return parseOwnerRepo(input) != null
    }
}
