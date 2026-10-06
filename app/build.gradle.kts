import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

// Load local.properties for secrets like GITHUB_CLIENT_ID
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { this.load(it) }
}
val localGithubClientId = (localProps.getProperty("GITHUB_CLIENT_ID") ?: "Ov23liinOZYK0IduPvuO").trim()

// VirusTotal API key used to verify APK security status on the detail screen.
// To use your own: add VIRUSTOTAL_API_KEY=your_key to local.properties.
// Get a free key at: https://www.virustotal.com/gui/my-apikey
val virusTotalApiKey = (localProps.getProperty("VIRUSTOTAL_API_KEY") ?: "").trim()

android {
    namespace = "com.samyak.repostore"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.samyak.repostore"
        minSdk = 26
        targetSdk = 36
        versionCode = 24
        versionName = "1.0.23"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // GitHub OAuth Client ID
        // To use your own: Add GITHUB_CLIENT_ID=your_client_id to local.properties
        // Get your own at: https://github.com/settings/developers -> "New OAuth App"
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"${localGithubClientId}\"")

        // VirusTotal API key for APK security verification badge.
        // Default (debug/local) uses the value from local.properties. Release builds
        // override this to an empty string below so F-Droid reproducible builds match
        // the published APK (F-Droid builds without local.properties).
        buildConfigField("String", "VIRUSTOTAL_API_KEY", "\"${virusTotalApiKey}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Reproducible builds: never embed local secrets in the published APK.
            // F-Droid builds with no local.properties, so the release must too.
            buildConfigField("String", "VIRUSTOTAL_API_KEY", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // Exclude non-deterministic baseline profile files so F-Droid reproducible
    // builds match the published APK.
    packaging {
        resources {
            excludes += "assets/dexopt/baseline.prof"
            excludes += "assets/dexopt/baseline.profm"
        }
    }

    // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests {
            // Return default values for un-mocked android.* stubs (e.g. android.util.Log)
            // so pure-JVM unit tests don't crash on framework logging calls.
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":GitCore"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)

    // Image loading
    implementation(libs.glide)

    // Fragment
    implementation(libs.fragment.ktx)

    // SwipeRefresh
    implementation(libs.swiperefresh)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Markwon - Markdown rendering
    implementation(libs.markwon.core)
    implementation(libs.markwon.image)
    implementation(libs.markwon.linkify)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.html)

    // PhotoView - Zoomable image viewer
    implementation(libs.photoview)

    // DotsIndicator - ViewPager2 page indicators
    implementation(libs.dotsindicator)

    // Lottie - Animations
    implementation(libs.lottie)

    // WorkManager - background update checks
    implementation(libs.work.runtime.ktx)

    // ZXing - QR code generation for the donate screen
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}