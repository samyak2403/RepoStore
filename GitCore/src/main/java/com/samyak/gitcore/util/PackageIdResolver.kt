package com.samyak.gitcore.util

/**
 * Generates prioritized URLs to files that may contain the applicationId / package name
 * for a GitHub repository's Android app (`build.gradle`, `build.gradle.kts`,
 * `AndroidManifest.xml`, Compose Multiplatform, Expo/Capacitor, or .NET MAUI configs).
 */
object PackageIdResolver {

    fun resolve(owner: String, name: String, defaultBranch: String?, language: String?): List<String> {
        return resolve(
            owner = owner,
            name = name,
            defaultBranch = defaultBranch,
            language = language,
            fallbackBranches = GitHubRawUrlBuilder.DEFAULT_FALLBACK_BRANCHES
        )
    }

    fun resolve(
        owner: String,
        name: String,
        defaultBranch: String?,
        language: String?,
        fallbackBranches: List<String>
    ): List<String> {
        val cleanName = name.trim().trim('/')
        if (cleanName.isBlank()) return emptyList()

        val gradlePaths = listOf(
            "app/build.gradle.kts",
            "app/build.gradle",
            "build.gradle.kts",
            "build.gradle"
        )

        val manifestPaths = listOf(
            "app/src/main/AndroidManifest.xml",
            "src/main/AndroidManifest.xml"
        )

        val composeAndKmmPaths = listOf(
            "composeApp/build.gradle.kts",
            "composeApp/build.gradle",
            "composeApp/src/androidMain/AndroidManifest.xml",
            "androidApp/build.gradle.kts",
            "androidApp/build.gradle",
            "androidApp/src/main/AndroidManifest.xml"
        )

        val crossPlatformPaths = listOf(
            "android/app/build.gradle.kts",
            "android/app/build.gradle",
            "android/app/src/main/AndroidManifest.xml",
            "app.json",
            "app.config.json",
            "capacitor.config.json",
            "capacitor.config.ts"
        )

        val dotnetPaths = listOf(
            "$cleanName/$cleanName.csproj",
            "$cleanName.csproj",
            "Platforms/Android/AndroidManifest.xml"
        )

        val dynamicPaths = listOf(
            "$cleanName/build.gradle.kts",
            "$cleanName/build.gradle",
            "$cleanName-android/build.gradle.kts",
            "$cleanName-android/build.gradle",
            "$cleanName/src/main/AndroidManifest.xml",
            "$cleanName-android/src/main/AndroidManifest.xml"
        )

        val prioritizedPaths = when (GitHubRawUrlBuilder.classifyEcosystem(language)) {
            GitHubRawUrlBuilder.Ecosystem.CROSS_PLATFORM ->
                crossPlatformPaths + gradlePaths + manifestPaths + composeAndKmmPaths + dynamicPaths + dotnetPaths
            GitHubRawUrlBuilder.Ecosystem.DOTNET_MAUI ->
                dotnetPaths + crossPlatformPaths + gradlePaths + manifestPaths + dynamicPaths + composeAndKmmPaths
            GitHubRawUrlBuilder.Ecosystem.NATIVE_ANDROID ->
                gradlePaths + manifestPaths + composeAndKmmPaths + dynamicPaths + crossPlatformPaths + dotnetPaths
        }

        return GitHubRawUrlBuilder.buildUrls(
            owner = owner,
            name = cleanName,
            defaultBranch = defaultBranch,
            relativePaths = prioritizedPaths,
            fallbackBranches = fallbackBranches
        )
    }
}
