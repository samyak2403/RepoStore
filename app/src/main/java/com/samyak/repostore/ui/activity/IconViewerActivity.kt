package com.samyak.repostore.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.samyak.repostore.R
import com.samyak.repostore.databinding.ActivityIconViewerBinding
import com.samyak.repostore.util.applyTransition
import com.samyak.repostore.util.loadIconWithFallback

class IconViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIconViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityIconViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val iconUrls = intent.getStringArrayListExtra(EXTRA_ICON_URLS)?.toList() ?: emptyList()
        val fallbackUrl = intent.getStringExtra(EXTRA_FALLBACK_URL) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: ""

        if (fallbackUrl.isEmpty()) {
            finish()
            return
        }

        setupToolbar(appName)
        loadIcon(iconUrls, fallbackUrl)
    }

    private fun setupToolbar(appName: String) {
        binding.toolbar.title = appName
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadIcon(iconUrls: List<String>, fallbackUrl: String) {
        binding.ivIcon.loadIconWithFallback(iconUrls, fallbackUrl)
    }

    override fun finish() {
        super.finish()
        applyTransition(isOpen = false, R.anim.fade_in, R.anim.slide_down)
    }

    companion object {
        private const val EXTRA_ICON_URLS = "icon_urls"
        private const val EXTRA_FALLBACK_URL = "fallback_url"
        private const val EXTRA_APP_NAME = "app_name"

        fun newIntent(context: Context, iconUrls: List<String>, fallbackUrl: String, appName: String): Intent {
            return Intent(context, IconViewerActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_ICON_URLS, ArrayList(iconUrls))
                putExtra(EXTRA_FALLBACK_URL, fallbackUrl)
                putExtra(EXTRA_APP_NAME, appName)
            }
        }
    }
}
