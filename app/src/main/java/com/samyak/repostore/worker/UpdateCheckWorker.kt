
package com.samyak.repostore.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.samyak.repostore.R
import com.samyak.repostore.RepoStoreApp
import com.samyak.repostore.ui.activity.DetailActivity
import com.samyak.repostore.util.AppInstaller
import com.samyak.repostore.util.VersionComparator
import kotlinx.coroutines.flow.first

/**
 * Periodic background worker that checks every app installed through RepoStore
 * for an available update and posts a notification for each one that has one.
 *
 * How it works:
 *  1. Reads all (owner, repo) -> packageName mappings stored when apps were installed.
 *  2. For each mapping that is still installed, reads the installed versionName.
 *  3. Fetches the latest GitHub release and compares tags using [VersionComparator].
 *  4. If newer AND the user hasn't already been notified about that exact tag,
 *     posts a notification that deep-links into [DetailActivity] to perform the update.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val app = context as? RepoStoreApp ?: return Result.success()

        // No point checking if the user can't see notifications.
        if (!areNotificationsAllowed(context)) {
            Log.d(TAG, "Notifications not permitted/enabled — skipping update check.")
            return Result.success()
        }

        ensureChannel(context)

        val container = app.container
        val installer = container.appInstaller
        val repository = container.repository
        val dao = container.installedAppMappingDao
        val notifiedStore = container.updateNotificationStore

        val mappings = try {
            dao.getAllMappingsFlow().first()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read installed mappings", e)
            return Result.retry()
        }

        var updatesFound = 0

        for (mapping in mappings) {
            try {
                val packageName = mapping.packageName
                if (!installer.isInstalled(packageName)) {
                    notifiedStore.clearPackage(packageName)
                    continue
                }

                val installedVersion = installer.getInstalledVersion(packageName) ?: continue

                val release = repository.getLatestRelease(mapping.ownerName, mapping.repoName)
                    .getOrNull() ?: continue

                if (release.draft) continue
                val latestTag = release.tagName.takeIf { it.isNotBlank() } ?: continue

                val hasInstallable = release.assets.any { asset ->
                    val n = asset.name.lowercase()
                    n.endsWith(".apk") || n.endsWith(".aab") || n.endsWith(".xapk") || n.endsWith(".apks")
                }
                if (!hasInstallable) continue

                if (!VersionComparator.isNewerVersion(installedVersion, latestTag)) continue

                if (notifiedStore.getLastNotifiedTag(packageName) == latestTag) continue

                val appLabel = installer.getAppLabel(packageName) ?: mapping.repoName
                notifyUpdate(context, mapping.ownerName, mapping.repoName, appLabel, installedVersion, latestTag)
                notifiedStore.setLastNotifiedTag(packageName, latestTag)
                updatesFound++
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed for ${mapping.ownerName}/${mapping.repoName}", e)
            }
        }

        Log.d(TAG, "Update check complete. Updates found: $updatesFound / ${mappings.size} apps")
        return Result.success()
    }

    private fun notifyUpdate(
        context: Context,
        owner: String,
        repo: String,
        appLabel: String,
        installedVersion: String,
        latestTag: String
    ) {
        val detailIntent = DetailActivity.newIntent(context, owner, repo).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val notificationId = notificationIdFor("$owner/$repo")
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            detailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update)
            .setContentTitle(context.getString(R.string.update_notification_title, appLabel))
            .setContentText(context.getString(R.string.update_notification_text, installedVersion, latestTag))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.update_notification_text, installedVersion, latestTag)
                )
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(NOTIFICATION_GROUP)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and the post.
            Log.w(TAG, "Notification permission missing when posting", e)
        }
    }

    private fun areNotificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.update_notification_channel_desc)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun notificationIdFor(key: String): Int {
        // Stable positive id per repo so re-checks update the same notification.
        return (BASE_NOTIFICATION_ID + (key.hashCode() and 0x0000FFFF))
    }

    companion object {
        private const val TAG = "UpdateCheckWorker"
        const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_GROUP = "com.samyak.repostore.UPDATES"
        private const val BASE_NOTIFICATION_ID = 10_000
        private const val PREFS_NOTIFIED = "update_notified_tags"
    }
}
