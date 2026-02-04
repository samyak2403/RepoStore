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
import com.samyak.repostore.R
import com.samyak.repostore.data.prefs.DownloadPreferences
import com.samyak.repostore.databinding.ActivityDownloadSettingsBinding

class DownloadSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadSettingsBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        binding = ActivityDownloadSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
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
        // Mirror proxy toggle
        binding.switchMirrorProxy.setOnCheckedChangeListener { _, isChecked ->
            DownloadPreferences.setMirrorProxyEnabled(this, isChecked)
            binding.tilProxyUrl.isEnabled = isChecked
            binding.chipGroupProxies.isEnabled = isChecked
        }
        
        // Proxy URL input
        binding.etProxyUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val url = s?.toString()?.trim()
                if (!url.isNullOrBlank()) {
                    // Ensure URL has https:// prefix
                    val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        "https://$url"
                    } else {
                        url
                    }
                    // Ensure URL ends with /
                    val finalUrl = if (normalizedUrl.endsWith("/")) normalizedUrl else "$normalizedUrl/"
                    DownloadPreferences.setMirrorProxyUrl(this@DownloadSettingsActivity, finalUrl)
                } else {
                    DownloadPreferences.setMirrorProxyUrl(this@DownloadSettingsActivity, null)
                }
            }
        })
        
        // Popular proxy chips
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
        // Multi-part download toggle
        binding.switchMultiPart.setOnCheckedChangeListener { _, isChecked ->
            DownloadPreferences.setMultiPartEnabled(this, isChecked)
            binding.layoutThreadCount.isVisible = isChecked
        }
        
        // Thread count slider
        binding.sliderThreadCount.addOnChangeListener { _, value, _ ->
            val threadCount = value.toInt()
            binding.tvThreadCount.text = threadCount.toString()
            DownloadPreferences.setThreadCount(this, threadCount)
        }
    }
    
    private fun loadSettings() {
        // Load mirror proxy settings
        val mirrorEnabled = DownloadPreferences.isMirrorProxyEnabled(this)
        binding.switchMirrorProxy.isChecked = mirrorEnabled
        binding.tilProxyUrl.isEnabled = mirrorEnabled
        
        val proxyUrl = DownloadPreferences.getMirrorProxyUrl(this)
        if (!proxyUrl.isNullOrBlank()) {
            binding.etProxyUrl.setText(proxyUrl)
        }
        
        // Load multi-part settings
        val multiPartEnabled = DownloadPreferences.isMultiPartEnabled(this)
        binding.switchMultiPart.isChecked = multiPartEnabled
        binding.layoutThreadCount.isVisible = multiPartEnabled
        
        val threadCount = DownloadPreferences.getThreadCount(this)
        binding.sliderThreadCount.value = threadCount.toFloat()
        binding.tvThreadCount.text = threadCount.toString()
    }
    
    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, DownloadSettingsActivity::class.java)
        }
    }
}
