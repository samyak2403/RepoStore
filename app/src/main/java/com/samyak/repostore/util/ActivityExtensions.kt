package com.samyak.repostore.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.samyak.repostore.R

/**
 * Applies activity transition animations cleanly across Android versions.
 * Uses [Activity.overrideActivityTransition] on Android 14+ (API 34) and
 * falls back to [Activity.overridePendingTransition] on earlier Android versions.
 */
fun Activity.applyTransition(isOpen: Boolean, enterAnim: Int, exitAnim: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val overrideType = if (isOpen) {
            Activity.OVERRIDE_TRANSITION_OPEN
        } else {
            Activity.OVERRIDE_TRANSITION_CLOSE
        }
        overrideActivityTransition(overrideType, enterAnim, exitAnim)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(enterAnim, exitAnim)
    }
}

/**
 * Safely opens an external HTTP/HTTPS URL without crashing if no browser is
 * installed or the URL is malformed.
 */
fun Context.openExternalUrl(rawUrl: String): Boolean {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return false
    val normalized = if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "https://$trimmed"
    }
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
        if (this !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.cannot_open_app, Toast.LENGTH_SHORT).show()
        false
    } catch (_: Exception) {
        Toast.makeText(this, R.string.cannot_open_app, Toast.LENGTH_SHORT).show()
        false
    }
}

