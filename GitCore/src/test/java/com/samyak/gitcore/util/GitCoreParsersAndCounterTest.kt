package com.samyak.gitcore.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitCoreParsersAndCounterTest {

    @Test
    fun `AppNameParser ignores XML comments, resolves entities, and rejects resource pointers`() {
        val xmlWithCommentAndEntities = """
            <resources>
                <!-- <string name="app_name">Old Obsolete Name</string> -->
                <string name="other_label">Ignore</string>
                <string name="app_name" translatable="false">Tom &amp; Jerry\'s Reader &#39;Pro&#39;</string>
            </resources>
        """.trimIndent()
        assertEquals(
            "Tom & Jerry's Reader 'Pro'",
            AppNameParser.parse("https://raw.githubusercontent.com/a/b/main/app/src/main/res/values/strings.xml", xmlWithCommentAndEntities)
        )

        val xmlReference = """
            <resources><string name="app_name">@string/app_display_name</string></resources>
        """.trimIndent()
        assertNull(AppNameParser.parse("https://raw.githubusercontent.com/a/b/main/app/src/main/res/values/strings.xml", xmlReference))
    }

    @Test
    fun `AppNameParser handles pubspec yaml top-level key, json expo name, csproj, settings gradle, and readme`() {
        val pubspec = """
            # Sample pubspec
            description: Demo app
            dependencies:
              name: nested_dep_should_be_ignored
            name: pocket_notes # comment
        """.trimIndent()
        assertEquals("pocket_notes", AppNameParser.parse("https://raw.githubusercontent.com/a/b/main/pubspec.yaml", pubspec))

        val expoAppJson = """
            {
              "expo": {
                "slug": "slug-app",
                "name": "Expo Wallet"
              }
            }
        """.trimIndent()
        assertEquals("Expo Wallet", AppNameParser.parse("https://raw.githubusercontent.com/a/b/main/app.json", expoAppJson))

        val csproj = """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <ApplicationTitle>Maui Tracker</ApplicationTitle>
              </PropertyGroup>
            </Project>
        """.trimIndent()
        assertEquals("Maui Tracker", AppNameParser.parse("https://raw.githubusercontent.com/a/b/main/MauiApp/MauiApp.csproj", csproj))

        val settingsGradle = """
            // rootProject.name = "CommentedOut"
            rootProject.name = "RepoStore"
        """.trimIndent()
        assertEquals("RepoStore", AppNameParser.parse("https://raw.githubusercontent.com/a/b/main/settings.gradle.kts", settingsGradle))

        val readme = """
            # [![Logo](https://img.shields.io/badge.svg)](https://example.com) [Awesome Client](https://example.com)
            Some description below.
        """.trimIndent()
        assertEquals("Awesome Client", AppNameParser.parse("https://raw.githubusercontent.com/a/b/main/README.md", readme))
    }

    @Test
    fun `PackageIdParser extracts deeply nested defaultConfig applicationId and ignores comments and test ids`() {
        val gradleKts = """
            // applicationId = "com.old.commented"
            /*
             * applicationId = "com.block.commented"
             */
            val URL = "https://example.com // keep url literal intact"
            val APP_PACKAGE = "com.samyak.repostore"

            android {
                namespace = "com.samyak.repostore.base"
                defaultConfig {
                    ndk {
                        abiFilters += listOf("arm64-v8a", "armeabi-v7a")
                    }
                    vectorDrawables {
                        useSupportLibrary = true
                    }
                    testApplicationId = "com.samyak.repostore.test"
                    applicationId = APP_PACKAGE
                    minSdk = 26
                }
                buildTypes {
                    debug {
                        applicationIdSuffix = ".debug"
                    }
                }
            }
        """.trimIndent()

        assertEquals(
            "com.samyak.repostore",
            PackageIdParser.parse("https://raw.githubusercontent.com/a/b/main/app/build.gradle.kts", gradleKts)
        )
    }

    @Test
    fun `PackageIdParser handles defaultConfig suffix, Manifest comments, Expo JSON, Capacitor TS, and blocks false positives`() {
        val gradleWithSuffix = """
            android {
                defaultConfig {
                    applicationId "org.fossify.notes"
                    applicationIdSuffix ".foss"
                }
            }
        """.trimIndent()
        assertEquals(
            "org.fossify.notes.foss",
            PackageIdParser.parse("https://raw.githubusercontent.com/a/b/main/app/build.gradle", gradleWithSuffix)
        )

        val manifest = """
            <!-- <manifest package="com.wrong.comment"> -->
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="org.example.reader">
            </manifest>
        """.trimIndent()
        assertEquals(
            "org.example.reader",
            PackageIdParser.parse("https://raw.githubusercontent.com/a/b/main/app/src/main/AndroidManifest.xml", manifest)
        )

        val expoJson = """{ "expo": { "android": { "package": "io.expo.wallet" } } }"""
        assertEquals("io.expo.wallet", PackageIdParser.parse("https://raw.githubusercontent.com/a/b/main/app.json", expoJson))

        val capTs = """const config = { appId: 'dev.cap.demo', appName: 'Cap' };"""
        assertEquals("dev.cap.demo", PackageIdParser.parse("https://raw.githubusercontent.com/a/b/main/capacitor.config.ts", capTs))

        val pluginIdGradle = """
            plugins {
                id("com.android.application")
            }
        """.trimIndent()
        assertNull(PackageIdParser.parse("https://raw.githubusercontent.com/a/b/main/build.gradle.kts", pluginIdGradle))
    }

    @Test
    fun `DownloadCounter clamps negatives, handles boundary rounding rollover, and sums Long totals safely`() {
        assertEquals("0", DownloadCounter.format(-10L))
        assertEquals("0", DownloadCounter.format(0L))
        assertEquals("999", DownloadCounter.format(999L))
        assertEquals("1.0K", DownloadCounter.format(1_000L))
        assertEquals("999.9K", DownloadCounter.format(999_949L))
        assertEquals("1.0M", DownloadCounter.format(999_950L))
        assertEquals("999.9M", DownloadCounter.format(999_949_999L))
        assertEquals("1.0B", DownloadCounter.format(999_950_000L))

        assertEquals(150L, DownloadCounter.total(listOf(-50, 100, 50)))
        assertEquals(3_000_000_000L, DownloadCounter.total(listOf(2_000_000_000, 1_000_000_000)))
        assertEquals("3.0B", DownloadCounter.totalFormattedFromLongs(listOf(2_000_000_000L, 1_000_000_000L)))
    }
}
