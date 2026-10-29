package com.samyak.repostore.ui.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.samyak.repostore.databinding.ActivityLicensesBinding
import com.samyak.repostore.ui.adapter.LicenseAdapter
import com.samyak.repostore.ui.adapter.LibraryInfo

class LicensesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLicensesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLicensesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupLicensesList()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupLicensesList() {
        val libraries = getLibraries()
        
        binding.rvLicenses.layoutManager = LinearLayoutManager(this)
        binding.rvLicenses.adapter = LicenseAdapter(libraries) { library ->
            openUrl(library.url)
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, LicensesActivity::class.java)
        }

        fun getLibraries(): List<LibraryInfo> {
            return listOf(
                LibraryInfo(
                    name = "RepoStore",
                    author = "Samyak Kamble",
                    license = "MIT",
                    url = "https://github.com/samyak2403/RepoStore"
                ),
                LibraryInfo(
                    name = "AndroidX Core KTX",
                    author = "Google",
                    license = "Apache 2.0",
                    url = "https://developer.android.com/jetpack/androidx"
                ),
                LibraryInfo(
                    name = "AndroidX AppCompat",
                    author = "Google",
                    license = "Apache 2.0",
                    url = "https://developer.android.com/jetpack/androidx"
                ),
                LibraryInfo(
                    name = "AndroidX Activity KTX",
                    author = "Google",
                    license = "Apache 2.0",
                    url = "https://developer.android.com/jetpack/androidx/releases/activity"
                ),
                LibraryInfo(
                    name = "AndroidX Fragment KTX",
                    author = "Google",
                    license = "Apache 2.0",
                    url = "https://developer.android.com/jetpack/androidx/releases/fragment"
                ),
                LibraryInfo(
                    name = "Material Components",
                    author = "Google",
                    license = "Apache 2.0",
                    url = "https://github.com/material-components/material-components-android"
                ),
                LibraryInfo(
                    name = "AndroidX ConstraintLayout",
                    author = "Google",
                    license = "Apache 2.0",
                    url = "https://developer.android.com/jetpack/androidx/releases/constraintlayout"
                ),
                LibraryInfo(
                    name = "AndroidX ViewPager2",
                    author = "Google",
                    license = "Apache 2.0",
                    url = "https://developer.android.com/jetpack/androidx/releases/viewpager2"
                ),
                LibraryInfo(
                    name = "AndroidX Lifecycle",
                    author = "Google",
                    license = "Apache 2.0",
                    url = "https://developer.android.com/jetpack/androidx/releases/lifecycle"
                ),
                LibraryInfo(
                    name = "AndroidX WorkManager",
                    author = "Google",
                    license = "Apache 2.0",
                    url = "https://developer.android.com/jetpack/androidx/releases/work"
                ),
                LibraryInfo(
                    name = "Room Database",
                    author = "Google",
                    license = "Apache 2.0",
                    url = "https://developer.android.com/jetpack/androidx/releases/room"
                ),
                LibraryInfo(
                    name = "SwipeRefreshLayout",
                    author = "Google",
                    license = "Apache 2.0",
                    url = "https://developer.android.com/jetpack/androidx/releases/swiperefreshlayout"
                ),
                LibraryInfo(
                    name = "Kotlin Standard Library",
                    author = "JetBrains",
                    license = "Apache 2.0",
                    url = "https://github.com/JetBrains/kotlin"
                ),
                LibraryInfo(
                    name = "Kotlin Coroutines",
                    author = "JetBrains",
                    license = "Apache 2.0",
                    url = "https://github.com/Kotlin/kotlinx.coroutines"
                ),
                LibraryInfo(
                    name = "Retrofit",
                    author = "Square",
                    license = "Apache 2.0",
                    url = "https://github.com/square/retrofit"
                ),
                LibraryInfo(
                    name = "OkHttp",
                    author = "Square",
                    license = "Apache 2.0",
                    url = "https://github.com/square/okhttp"
                ),
                LibraryInfo(
                    name = "Gson",
                    author = "Google",
                    license = "Apache 2.0",
                    url = "https://github.com/google/gson"
                ),
                LibraryInfo(
                    name = "Glide",
                    author = "Bump Technologies",
                    license = "BSD / Apache 2.0",
                    url = "https://github.com/bumptech/glide"
                ),
                LibraryInfo(
                    name = "Markwon",
                    author = "Dimitry Ivanov",
                    license = "Apache 2.0",
                    url = "https://github.com/noties/Markwon"
                ),
                LibraryInfo(
                    name = "PhotoView",
                    author = "GetStream",
                    license = "Apache 2.0",
                    url = "https://github.com/GetStream/photoview-android"
                ),
                LibraryInfo(
                    name = "Lottie Android",
                    author = "Airbnb",
                    license = "Apache 2.0",
                    url = "https://github.com/airbnb/lottie-android"
                ),
                LibraryInfo(
                    name = "DotsIndicator",
                    author = "Tommaso Buonomo",
                    license = "Apache 2.0",
                    url = "https://github.com/tommybuonomo/dotsindicator"
                ),
                LibraryInfo(
                    name = "ZXing",
                    author = "ZXing Authors",
                    license = "Apache 2.0",
                    url = "https://github.com/zxing/zxing"
                )
            )
        }
    }
}
