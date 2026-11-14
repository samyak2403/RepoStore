package com.samyak.repostore.ui.activity

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.Switch
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import com.samyak.repostore.R
import com.samyak.repostore.RepoStoreApp
import com.samyak.repostore.ui.view.EmeraldToggleSwitch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DownloadSettingsActivityTest {

    @Test
    fun `DownloadSettingsActivity uses EmeraldToggleSwitch and persists toggles to DataStore`() {
        val app = ApplicationProvider.getApplicationContext<Application>() as RepoStoreApp
        runBlocking {
            app.container.downloadSettingsStore.setMirrorProxyEnabled(false)
            app.container.downloadSettingsStore.setMultiPartEnabled(false)
        }

        val controller = Robolectric.buildActivity(DownloadSettingsActivity::class.java).setup()
        val activity = controller.get()

        // Allow loadSettings() coroutine on main dispatcher to settle
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()

        val mirrorSwitch = activity.findViewById<EmeraldToggleSwitch>(R.id.switch_mirror_proxy)
        val multiPartSwitch = activity.findViewById<EmeraldToggleSwitch>(R.id.switch_multi_part)
        val threadCountLayout = activity.findViewById<View>(R.id.layout_thread_count)

        assertEquals(Switch::class.java.name, mirrorSwitch.accessibilityClassName.toString())
        assertEquals(Switch::class.java.name, multiPartSwitch.accessibilityClassName.toString())

        val min48DpPx = (48f * activity.resources.displayMetrics.density).toInt()
        assertTrue("Switch height must satisfy 48dp minimum touch target", mirrorSwitch.measuredHeight >= min48DpPx)
        assertTrue("Switch height must satisfy 48dp minimum touch target", multiPartSwitch.measuredHeight >= min48DpPx)

        assertFalse(mirrorSwitch.isChecked)
        assertFalse(multiPartSwitch.isChecked)
        assertFalse(threadCountLayout.isVisible)

        // Toggle Mirror Proxy ON
        mirrorSwitch.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(mirrorSwitch.isChecked)
        runBlocking {
            assertTrue(app.container.downloadSettingsStore.getSettings().mirrorProxyEnabled)
        }

        // Toggle Multi-Part ON and verify thread slider container reveals + persists
        multiPartSwitch.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(multiPartSwitch.isChecked)
        assertTrue(threadCountLayout.isVisible)
        runBlocking {
            assertTrue(app.container.downloadSettingsStore.getSettings().multiPartEnabled)
        }

        // Verify custom Canvas rendering in both ON and OFF states does not throw
        val bitmap = Bitmap.createBitmap(mirrorSwitch.measuredWidth, mirrorSwitch.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        mirrorSwitch.draw(canvas)
        mirrorSwitch.isChecked = false
        mirrorSwitch.draw(canvas)
    }
}
