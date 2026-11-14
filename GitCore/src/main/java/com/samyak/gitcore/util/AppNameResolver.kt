package com.samyak.gitcore.util

/**
 * Utility for discovering the "real" application name for a GitHub repository.
 * Generates a prioritized list of potential metadata, resource, and project config URLs
 * across Android, Compose Multiplatform, KMP, Flutter, React Native, Capacitor, and .NET MAUI projects.
 */
object AppNameResolver {

    fun resolve(owner: String, name: String, defaultBranch: String?, rawLanguage: String?): List<String> {
        return resolve(
            owner = owner,
            name = name,
            defaultBranch = defaultBranch,
            rawLanguage = rawLanguage,
            fallbackBranches = GitHubRawUrlBuilder.DEFAULT_FALLBACK_BRANCHES
        )
    }

    fun resolve(
        owner: String,
        name: String,
        defaultBranch: String?,
        rawLanguage: String?,
        fallbackBranches: List<String>
    ): List<String> {
        val cleanName = name.trim().trim('/')
        if (cleanName.isBlank()) return emptyList()

        // Fastlane / F-Droid store titles contain the exact human-readable app title without
        // build-variant tokens or @string indirection.
        val metadataTitlePaths = listOf(
            "fastlane/metadata/android/en-US/title.txt",
            "fastlane/metadata/android/en/title.txt",
            "metadata/en-US/title.txt",
            "metadata/android/en-US/title.txt"
        )

        val nativePaths = listOf(
            "app/src/main/res/values/strings.xml",
            "app/src/main/res/values-en/strings.xml",
            "app/src/main/res/values/strings_non_localized.xml",
            "app/src/main/res/values/app_name.xml",
            "src/main/res/values/strings.xml",
            "src/main/res/values/strings_non_localized.xml",
            "res/values/strings.xml"
        )

        val composeAndKmmPaths = listOf(
            "composeApp/src/androidMain/res/values/strings.xml",
            "composeApp/src/androidMain/res/values/app_name.xml",
            "composeApp/src/commonMain/composeResources/values/strings.xml",
            "androidApp/src/main/res/values/strings.xml",
            "androidApp/src/main/res/values/app_name.xml",
            "shared/src/commonMain/resources/strings.xml"
        )

        val crossPlatformPaths = listOf(
            "android/app/src/main/res/values/strings.xml",
            "android/app/src/main/res/values/app_name.xml",
            "app.json",
            "app.config.json",
            "capacitor.config.json",
            "capacitor.config.ts",
            "ionic.config.json",
            "package.json",
            "pubspec.yaml"
        )

        val dotnetPaths = listOf(
            "Platforms/Android/Resources/values/strings.xml",
            "Platforms/Android/Resources/values/app_name.xml",
            "$cleanName/$cleanName.csproj",
            "$cleanName.csproj",
            "Package.appxmanifest"
        )

        val dynamicPaths = listOf(
            "src/$cleanName.Android/Resources/values/strings.xml",
            "$cleanName-android/src/main/res/values/strings.xml",
            "$cleanName/src/main/res/values/strings.xml",
            "$cleanName/app/src/main/res/values/strings.xml",
            "$cleanName-android/src/main/res/values/app_name.xml",
            "$cleanName/src/main/res/values/app_name.xml",
            "$cleanName/app/src/main/res/values/app_name.xml"
        )

        val gradleAndDocPaths = listOf(
            "settings.gradle.kts",
            "settings.gradle",
            "README.md"
        )

        val prioritizedPaths = when (GitHubRawUrlBuilder.classifyEcosystem(rawLanguage)) {
            GitHubRawUrlBuilder.Ecosystem.CROSS_PLATFORM ->
                metadataTitlePaths + crossPlatformPaths + nativePaths + composeAndKmmPaths + dotnetPaths + dynamicPaths + gradleAndDocPaths
            GitHubRawUrlBuilder.Ecosystem.DOTNET_MAUI ->
                metadataTitlePaths + dotnetPaths + crossPlatformPaths + nativePaths + dynamicPaths + composeAndKmmPaths + gradleAndDocPaths
            GitHubRawUrlBuilder.Ecosystem.NATIVE_ANDROID ->
                metadataTitlePaths + nativePaths + composeAndKmmPaths + dynamicPaths + crossPlatformPaths + dotnetPaths + gradleAndDocPaths
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
