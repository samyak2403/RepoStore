package com.samyak.repostore.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.samyak.repostore.RepoStoreApp
import com.samyak.repostore.data.prefs.DownloadSettingsStore
import com.samyak.repostore.databinding.ActivityDownloadSettingsBinding
import kotlinx.coroutines.launch

class DownloadSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadSettingsBinding
    private lateinit var downloadSettingsStore: DownloadSettingsStore
    private var isPopulatingUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDownloadSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        downloadSettingsStore = (application as? RepoStoreApp)?.container?.downloadSettingsStore
            ?: DownloadSettingsStore.getInstance(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupMirrorProxySection()
        setupMultiPartSection()
        loadSettings()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupMirrorProxySection() {
        binding.switchMirrorProxy.setOnCheckedChangeListener { _, isChecked ->
            binding.tilProxyUrl.isEnabled = isChecked
            binding.chipGroupProxies.isEnabled = isChecked
            if (!isPopulatingUi) {
                lifecycleScope.launch {
                    downloadSettingsStore.setMirrorProxyEnabled(isChecked)
                }
            }
        }

        binding.etProxyUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isPopulatingUi) return
                val url = s?.toString()?.trim()
                val finalUrl = if (!url.isNullOrBlank()) {
                    val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        "https://$url"
                    } else {
                        url
                    }
                    if (normalizedUrl.endsWith("/")) normalizedUrl else "$normalizedUrl/"
                } else {
                    null
                }
                lifecycleScope.launch {
                    downloadSettingsStore.setMirrorProxyUrl(finalUrl)
                }
            }
        })

        binding.chipGhproxy.setOnClickListener {
            binding.etProxyUrl.setText("https://gh-proxy.com/")
        }
        binding.chipGhproxyNet.setOnClickListener {
            binding.etProxyUrl.setText("https://ghproxy.net/")
        }
        binding.chipMirrorGhproxy.setOnClickListener {
            binding.etProxyUrl.setText("https://mirror.ghproxy.com/")
        }
    }

    private fun setupMultiPartSection() {
        binding.switchMultiPart.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutThreadCount.isVisible = isChecked
            if (!isPopulatingUi) {
                lifecycleScope.launch {
                    downloadSettingsStore.setMultiPartEnabled(isChecked)
                }
            }
        }

        binding.sliderThreadCount.addOnChangeListener { _, value, _ ->
            val threadCount = value.toInt()
            binding.tvThreadCount.text = threadCount.toString()
            if (!isPopulatingUi) {
                lifecycleScope.launch {
                    downloadSettingsStore.setThreadCount(threadCount)
                }
            }
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            val settings = downloadSettingsStore.getSettings()
            isPopulatingUi = true
            try {
                binding.switchMirrorProxy.isChecked = settings.mirrorProxyEnabled
                binding.tilProxyUrl.isEnabled = settings.mirrorProxyEnabled
                binding.chipGroupProxies.isEnabled = settings.mirrorProxyEnabled

                if (!settings.mirrorProxyUrl.isNullOrBlank()) {
                    binding.etProxyUrl.setText(settings.mirrorProxyUrl)
                }

                binding.switchMultiPart.isChecked = settings.multiPartEnabled
                binding.layoutThreadCount.isVisible = settings.multiPartEnabled

                binding.sliderThreadCount.value = settings.threadCount.toFloat()
                binding.tvThreadCount.text = settings.threadCount.toString()
            } finally {
                isPopulatingUi = false
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, DownloadSettingsActivity::class.java)
        }
    }
}
