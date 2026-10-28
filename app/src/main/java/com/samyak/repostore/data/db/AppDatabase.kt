package com.samyak.repostore.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.samyak.repostore.data.model.FavoriteApp
import com.samyak.repostore.data.model.GitHubRepo
import com.samyak.repostore.data.model.InstalledAppMapping

@Database(entities = [GitHubRepo::class, FavoriteApp::class, InstalledAppMapping::class, CachedRepoEntity::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun repoDao(): RepoDao
    abstract fun favoriteAppDao(): FavoriteAppDao
    abstract fun installedAppMappingDao(): InstalledAppMappingDao
    abstract fun cachedRepoDao(): CachedRepoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "github_app_store_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
