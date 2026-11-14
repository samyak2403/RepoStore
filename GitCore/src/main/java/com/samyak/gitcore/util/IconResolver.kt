package com.samyak.gitcore.util

/**
 * Utility for discovering the "real" application icon for a GitHub repository.
 * Generates a prioritized list of potential icon URLs based on store metadata,
 * framework conventions (Android, Compose Multiplatform, KMP, Flutter/RN, MAUI),
 * and repository documentation assets.
 */
object IconResolver {

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

        // High-resolution store metadata and Play Store listing icons first:
        // these are unclipped 512px graphics when present, avoiding transparent adaptive foregrounds.
        val metadataPaths = listOf(
            "fastlane/metadata/android/en-US/images/icon.png",
            "fastlane/metadata/android/en-US/images/icon.webp",
            "fastlane/metadata/android/en/images/icon.png",
            "fastlane/metadata/android/images/icon.png",
            "metadata/en-US/images/icon.png",
            "metadata/android/en-US/images/icon.png",
            "app/src/main/ic_launcher-playstore.png",
            "app/src/main/ic_launcher-web.png",
            "composeApp/src/androidMain/ic_launcher-playstore.png",
            "composeApp/src/androidMain/ic_launcher-web.png",
            "androidApp/src/main/ic_launcher-playstore.png",
            "androidApp/src/main/ic_launcher-web.png",
            "android/app/src/main/ic_launcher-playstore.png",
            "android/app/src/main/ic_launcher-web.png"
        )

        val nativePaths = listOf(
            "app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp",
            "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
            "app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp",
            "app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png",
            "app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp",
            "app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png",
            "app/src/main/res/drawable/app_icon.png",
            "app/src/main/res/drawable/app_icon.webp",
            "app/src/main/res/drawable/ic_launcher.png",
            "app/src/main/res/drawable/ic_launcher.webp",
            "app/src/main/res/mipmap-xxhdpi/ic_launcher.webp",
            "app/src/main/res/mipmap-xxhdpi/ic_launcher.png",
            "app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.webp",
            "app/src/main/res/mipmap-xhdpi/ic_launcher.webp",
            "app/src/main/res/mipmap-xhdpi/ic_launcher.png",
            "app/src/main/res/mipmap-hdpi/ic_launcher.webp",
            "app/src/main/res/mipmap-hdpi/ic_launcher.png"
        )

        val composeAndKmmPaths = listOf(
            "composeApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.webp",
            "composeApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png",
            "composeApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher_round.webp",
            "composeApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher_round.png",
            "composeApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher_foreground.webp",
            "composeApp/src/androidMain/res/drawable/app_icon.png",
            "composeApp/src/commonMain/composeResources/drawable/icon.png",
            "composeApp/src/commonMain/composeResources/drawable/app_icon.png",
            "androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.webp",
            "androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
            "androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp",
            "androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png",
            "androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp",
            "androidApp/src/main/res/drawable/app_icon.png",
            "androidApp/src/main/res/mipmap-hdpi/ic_launcher_foreground.webp"
        )

        val crossPlatformPaths = listOf(
            "android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp",
            "android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
            "android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp",
            "android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png",
            "android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp",
            "android/app/src/main/res/drawable/app_icon.png",
            "android/app/src/main/res/mipmap-xxhdpi/ic_launcher.webp",
            "android/app/src/main/res/mipmap-xxhdpi/ic_launcher.png",
            "assets/images/icon.png",
            "assets/icon.png",
            "assets/images/adaptive-icon.png"
        )

        val dotnetPaths = listOf(
            "Platforms/Android/Resources/mipmap-xxxhdpi/appicon.png",
            "Platforms/Android/Resources/mipmap-xxxhdpi/ic_launcher.webp",
            "Platforms/Android/Resources/mipmap-xxxhdpi/ic_launcher.png",
            "Platforms/Android/Resources/mipmap-xxxhdpi/app_icon.png",
            "Platforms/Android/Resources/mipmap-hdpi/ic_launcher.png",
            "Resources/AppIcon/appicon.svg"
        )

        val dynamicPaths = listOf(
            "src/$cleanName.Android/Icon.png",
            "src/$cleanName.Android/icon.png",
            "src/$cleanName.Android/Icon.svg",
            "src/$cleanName.Android/icon.svg",
            "src/$cleanName.Android/app_icon.png",
            "src/$cleanName.Android/ic_launcher-web.png",
            "src/$cleanName.Android/res/mipmap-xxxhdpi/ic_launcher.png",
            "src/$cleanName.Android/Resources/mipmap-xxxhdpi/ic_launcher.png",
            "$cleanName-android/src/main/res/mipmap-xxxhdpi/ic_launcher.webp",
            "$cleanName-android/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
            "$cleanName-android/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png",
            "$cleanName-android/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp",
            "$cleanName-android/src/main/ic_launcher-web.png",
            "$cleanName-android/src/main/res/drawable/app_icon.png",
            "$cleanName/src/main/res/mipmap-xxxhdpi/ic_launcher.webp",
            "$cleanName/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
            "$cleanName/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp",
            "$cleanName/src/main/ic_launcher-web.png"
        )

        val docPaths = listOf(
            "doc/image/$cleanName.png",
            "doc/image/$cleanName.svg",
            "doc/image/logo.png",
            "doc/image/logo.svg",
            "doc/image/icon.png",
            "doc/image/icon.svg",
            "docs/image/$cleanName.png",
            "docs/image/$cleanName.svg",
            "docs/image/logo.png",
            "docs/image/logo.svg",
            "docs/image/icon.png",
            "docs/image/icon.svg",
            "docs/$cleanName.png",
            "docs/$cleanName.svg",
            "docs/logo.png",
            "docs/icon.png",
            "app/smartphone/icon.png",
            "app/smartphone/icon.svg",
            "app/smartphone/logo.png",
            "app/smartphone/logo.svg",
            "smartphone/icon.png",
            "smartphone/icon.svg",
            "smartphone/logo.png",
            "smartphone/logo.svg",
            "assets/$cleanName.png",
            "assets/$cleanName.svg",
            "assets/logo.png",
            "images/rounded_default.png",
            "images/default.png",
            "image/rounded_default.png",
            "image/default.png",
            "images/app_logo.png",
            "images/app_logo.svg",
            "image/app_logo.png",
            "image/app_logo.svg",
            "images/$cleanName.png",
            "images/$cleanName.svg",
            "logo.png",
            "icon.png",
            "logo.svg",
            "icon.svg",
            "$cleanName.png",
            "$cleanName.svg"
        )

        val prioritizedPaths = when (GitHubRawUrlBuilder.classifyEcosystem(rawLanguage)) {
            GitHubRawUrlBuilder.Ecosystem.CROSS_PLATFORM ->
                metadataPaths + crossPlatformPaths + nativePaths + composeAndKmmPaths + dotnetPaths + dynamicPaths + docPaths
            GitHubRawUrlBuilder.Ecosystem.DOTNET_MAUI ->
                metadataPaths + dotnetPaths + crossPlatformPaths + nativePaths + dynamicPaths + composeAndKmmPaths + docPaths
            GitHubRawUrlBuilder.Ecosystem.NATIVE_ANDROID ->
                metadataPaths + nativePaths + composeAndKmmPaths + dynamicPaths + crossPlatformPaths + dotnetPaths + docPaths
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
