package com.samyak.repostore.util

import android.app.Activity
import android.os.Build

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

