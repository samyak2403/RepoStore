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
}
