package com.samyak.repostore.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubUrlParserTest {

    @Test
    fun parse_validUrl() {
        val url = "https://github.com/samyak2403/RepoStore"
        val info = GitHubUrlParser.parse(url)
        assertNotNull(info)
        assertEquals("samyak2403", info?.owner)
        assertEquals("RepoStore", info?.name)
    }

    @Test
    fun parse_validUrlWithTrailingSlash() {
        val url = "https://github.com/samyak2403/RepoStore/"
        val info = GitHubUrlParser.parse(url)
        assertNotNull(info)
        assertEquals("samyak2403", info?.owner)
        assertEquals("RepoStore", info?.name)
    }

    @Test
    fun parse_validUrlWithGitExtension() {
        val url = "https://github.com/samyak2403/RepoStore.git"
        val info = GitHubUrlParser.parse(url)
        assertNotNull(info)
        assertEquals("samyak2403", info?.owner)
        assertEquals("RepoStore", info?.name)
    }

    @Test
    fun parse_validUrlWithoutProtocol() {
        val url = "github.com/samyak2403/RepoStore"
        val info = GitHubUrlParser.parse(url)
        assertNotNull(info)
        assertEquals("samyak2403", info?.owner)
        assertEquals("RepoStore", info?.name)
    }

    @Test
    fun parse_validUrlWithSubpath() {
        val url = "https://github.com/samyak2403/RepoStore/blob/main/README.md"
        val info = GitHubUrlParser.parse(url)
        assertNotNull(info)
        assertEquals("samyak2403", info?.owner)
        assertEquals("RepoStore", info?.name)
    }

    @Test
    fun parse_invalidUrl() {
        val url = "https://google.com/samyak2403/RepoStore"
        val info = GitHubUrlParser.parse(url)
        assertNull(info)
    }

    @Test
    fun parse_notAUrl() {
        val url = "just a search query"
        val info = GitHubUrlParser.parse(url)
        assertNull(info)
    }

    @Test
    fun parse_validUrlWithQueryString() {
        val info = GitHubUrlParser.parse("https://github.com/samyak2403/RepoStore?tab=readme-ov-file")
        assertNotNull(info)
        assertEquals("samyak2403", info?.owner)
        assertEquals("RepoStore", info?.name)
    }

    @Test
    fun parse_validUrlWithFragment() {
        val info = GitHubUrlParser.parse("https://github.com/samyak2403/RepoStore#readme")
        assertNotNull(info)
        assertEquals("samyak2403", info?.owner)
        assertEquals("RepoStore", info?.name)
    }

    @Test
    fun parseOwnerRepo_acceptsShorthandAndRejectsMultiWordQueries() {
        val shorthand = GitHubUrlParser.parseOwnerRepo("samyak2403/NetVeill")
        assertNotNull(shorthand)
        assertEquals("samyak2403", shorthand?.owner)
        assertEquals("NetVeill", shorthand?.name)

        val withGitSuffix = GitHubUrlParser.parseOwnerRepo("samyak2403/NetVeill.git")
        assertNotNull(withGitSuffix)
        assertEquals("samyak2403", withGitSuffix?.owner)
        assertEquals("NetVeill", withGitSuffix?.name)

        // parse() strictly requires github.com URL and continues to return null for plain shorthand
        assertNull(GitHubUrlParser.parse("samyak2403/NetVeill"))
        assertNull(GitHubUrlParser.parseOwnerRepo("NetVeill app"))
        assertNull(GitHubUrlParser.parseOwnerRepo("NetVeill"))
    }
}
