package com.samyak.repostore.di

import android.content.Context
import com.samyak.repostore.data.api.GitHubApi
import com.samyak.repostore.data.api.RetrofitClient
import com.samyak.repostore.data.db.AppDatabase
import com.samyak.repostore.data.db.CachedRepoDao
import com.samyak.repostore.data.db.FavoriteAppDao
import com.samyak.repostore.data.db.InstalledAppMappingDao
import com.samyak.repostore.data.db.RepoDao
import com.samyak.repostore.data.prefs.DownloadSettingsStore
import com.samyak.repostore.data.prefs.ThemeSettingsStore
import com.samyak.repostore.data.prefs.UpdateNotificationStore
import com.samyak.repostore.data.repository.GitHubRepository
import com.samyak.repostore.util.ApkChecksumVerifier
import com.samyak.repostore.util.ApkSecurityChecker
import com.samyak.repostore.util.AppInstaller
import com.samyak.repostore.util.MultiPartDownloader

/**
 * Application-scoped dependency container replacing global singleton lookups
 * with explicit constructor-injected dependencies.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(appContext)
    }

    val repoDao: RepoDao by lazy { database.repoDao() }
    val cachedRepoDao: CachedRepoDao by lazy { database.cachedRepoDao() }
    val favoriteAppDao: FavoriteAppDao by lazy { database.favoriteAppDao() }
    val installedAppMappingDao: InstalledAppMappingDao by lazy { database.installedAppMappingDao() }

    val downloadSettingsStore: DownloadSettingsStore by lazy {
        DownloadSettingsStore.getInstance(appContext)
    }

    val themeSettingsStore: ThemeSettingsStore by lazy {
        ThemeSettingsStore.getInstance(appContext)
    }

    val updateNotificationStore: UpdateNotificationStore by lazy {
        UpdateNotificationStore.getInstance(appContext)
    }

    val apkChecksumVerifier: ApkChecksumVerifier by lazy {
        ApkChecksumVerifier()
    }

    val securityChecker: ApkSecurityChecker by lazy {
        ApkSecurityChecker()
    }

    private val gitHubApiProvider: () -> GitHubApi = { RetrofitClient.api }

    val repository: GitHubRepository by lazy {
        GitHubRepository(
            repoDao = repoDao,
            cachedRepoDao = cachedRepoDao,
            apiProvider = gitHubApiProvider
        )
    }

    val multiPartDownloader: MultiPartDownloader by lazy {
        MultiPartDownloader(
            context = appContext,
            downloadSettings = downloadSettingsStore,
            checksumVerifier = apkChecksumVerifier
        )
    }

    val appInstaller: AppInstaller by lazy {
        AppInstaller(
            context = appContext,
            downloadSettings = downloadSettingsStore,
            multiPartDownloader = multiPartDownloader,
            checksumVerifier = apkChecksumVerifier,
            mappingDaoProvider = { installedAppMappingDao }
        )
    }

    fun initNetworking() {
        RetrofitClient.init(appContext, null)
    }

    fun refreshAuth() {
        RetrofitClient.refreshAuth()
    }
}
