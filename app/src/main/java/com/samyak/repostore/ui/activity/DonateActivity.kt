package com.samyak.repostore.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.samyak.repostore.R
import com.samyak.repostore.databinding.ActivityDonateBinding
import com.samyak.repostore.ui.adapter.PaymentAppAdapter
import com.samyak.repostore.ui.adapter.PaymentAppInfo
import com.samyak.repostore.util.QrCodeGenerator
import com.samyak.repostore.util.openExternalUrl

class DonateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDonateBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDonateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupViews()
        setupPaymentApps()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViews() {
        binding.tvUpiId.text = UPI_ID

        // Generate the QR from the UPI URI at runtime so it always matches UPI_ID.
        val qr = QrCodeGenerator.generate(buildUpiUri())
        if (qr != null) {
            binding.ivQrCode.setImageBitmap(qr)
        } else {
            binding.ivQrCode.setImageResource(R.drawable.ic_account)
        }

        binding.btnCopyUpi.setOnClickListener {
            copyToClipboard(UPI_ID)
        }

        binding.cardBuyMeACoffee.setOnClickListener {
            openExternalUrl("https://buymeacoffee.com/mr_samyakkamble")
        }
    }

    private fun setupPaymentApps() {
        val upiApps = getInstalledUpiApps()

        if (upiApps.isEmpty()) {
            binding.tvNoUpiApps.visibility = View.VISIBLE
            binding.rvPaymentApps.visibility = View.GONE
        } else {
            binding.tvNoUpiApps.visibility = View.GONE
            binding.rvPaymentApps.visibility = View.VISIBLE

            binding.rvPaymentApps.layoutManager = LinearLayoutManager(this)
            binding.rvPaymentApps.adapter = PaymentAppAdapter(upiApps) { app ->
                openUpiApp(app.packageName)
            }
        }
    }

    private fun getInstalledUpiApps(): List<PaymentAppInfo> {
        val upiIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("upi://pay")
        }

        val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                upiIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(upiIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        return resolveInfoList.mapNotNull { resolveInfo ->
            try {
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val icon = packageManager.getApplicationIcon(appInfo)
                val packageName = resolveInfo.activityInfo.packageName

                PaymentAppInfo(appName, packageName, icon)
            } catch (e: Exception) {
                null
            }
        }.distinctBy { it.packageName }
    }

    private fun openUpiApp(packageName: String) {
        try {
            val upiUri = Uri.parse(buildUpiUri())
            val intent = Intent(Intent.ACTION_VIEW, upiUri).apply {
                setPackage(packageName)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cannot_open_payment_app, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildUpiUri(): String {
        return "upi://pay?pa=$UPI_ID&pn=$PAYEE_NAME&cu=INR"
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.upi_id), text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.upi_id_copied, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val UPI_ID = "samyakkamble01430-4@oksbi"
        private const val PAYEE_NAME = "Samyak"

        fun newIntent(context: Context): Intent {
            return Intent(context, DonateActivity::class.java)
        }
    }
}
