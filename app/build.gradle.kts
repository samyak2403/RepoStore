import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Load local.properties for secrets like GITHUB_CLIENT_ID
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { this.load(it) }
}
val localGithubClientId = (localProps.getProperty("GITHUB_CLIENT_ID") ?: "Ov23liinOZYK0IduPvuO").trim()

android {
    namespace = "com.samyak.repostore"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.samyak.repostore"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.0.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // GitHub OAuth Client ID
        // To use your own: Add GITHUB_CLIENT_ID=your_client_id to local.properties
        // Get your own at: https://github.com/settings/developers -> "New OAuth App"
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"${localGithubClientId}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    
    // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
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
    implementation("io.getstream:photoview:1.0.3")
    
    // DotsIndicator - ViewPager2 page indicators
    implementation("com.tbuonomo:dotsindicator:5.1.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
