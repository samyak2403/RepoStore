package com.samyak.repostore

import com.samyak.gitcore.util.IconResolver
import com.samyak.repostore.data.model.GitHubRepo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconResolverTest {

    @Test
    fun testResolveIcons_returnsCandidateUrlsForSimpleTube() {
        val repo = GitHubRepo(
            id = 1,
            name = "Simple-Tube",
            fullName = "samyak2403/Simple-Tube",
            description = "A simple YouTube client",
            htmlUrl = "https://github.com/samyak2403/Simple-Tube",
            stars = 100,
            forks = 10,
            language = "Kotlin",
            updatedAt = "2024-03-24T00:00:00Z",
            createdAt = "2023-01-01T00:00:00Z",
            archived = false,
            owner = GitHubRepo.Owner(
                login = "samyak2403",
                avatarUrl = "https://avatars.githubusercontent.com/u/1?v=4",
                htmlUrl = "https://github.com/samyak2403"
            ),
            topics = listOf("android", "youtube"),
            defaultBranch = "main"
        )

        val icons = IconResolver.resolve(repo.owner.login, repo.name, repo.defaultBranch, repo.language)
        assertNotNull("Icons list should not be null", icons)
        assertFalse("Icons list should not be empty", icons.isEmpty())
        assertTrue(
            "Icons should contain raw GitHub URLs pointing to the repository branch",
            icons.any { it.contains("raw.githubusercontent.com/samyak2403/Simple-Tube/main") }
        )
    }
}

