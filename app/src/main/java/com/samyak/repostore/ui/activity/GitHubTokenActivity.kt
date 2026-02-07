package com.samyak.repostore.ui.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.samyak.repostore.R
import com.samyak.repostore.RepoStoreApp
import com.samyak.repostore.databinding.ActivityGithubTokenBinding
import androidx.core.net.toUri

class GitHubTokenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGithubTokenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityGithubTokenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupViews()
        loadCurrentToken()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViews() {
        binding.btnSaveToken.setOnClickListener {
            val token = binding.etToken.text?.toString()?.trim()
            if (token.isNullOrEmpty()) {
                (application as RepoStoreApp).setGitHubToken(null)
                Toast.makeText(this, R.string.token_removed, Toast.LENGTH_SHORT).show()
            } else {
                (application as RepoStoreApp).setGitHubToken(token)
                Toast.makeText(this, R.string.token_saved, Toast.LENGTH_SHORT).show()
            }
            finish()
        }

        binding.btnClearToken.setOnClickListener {
            binding.etToken.text?.clear()
            (application as RepoStoreApp).setGitHubToken(null)
            Toast.makeText(this, R.string.token_removed, Toast.LENGTH_SHORT).show()
        }

        binding.btnCreateToken.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, "https://github.com/settings/tokens/new".toUri())
            startActivity(intent)
        }
    }

    private fun loadCurrentToken() {
        val token = (application as RepoStoreApp).getStoredToken()
        token?.let {
            if (it.length > 8) {
                binding.etToken.setText("${it.take(4)}****${it.takeLast(4)}")
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, GitHubTokenActivity::class.java)
        }
    }
}
