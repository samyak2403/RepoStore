package com.samyak.repostore.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.samyak.repostore.data.model.InstalledAppMapping

/**
 * DAO for InstalledAppMapping — stores the real package name
 * extracted from APKs during download/install.
 */
@Dao
interface InstalledAppMappingDao {

    /**
     * Save or update the mapping for a (owner, repo) → packageName.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMapping(mapping: InstalledAppMapping)

    /**
     * Get the stored package name for a specific owner/repo.
     * Returns null if no mapping exists (app was never installed through RepoStore).
     */
    @Query("SELECT packageName FROM installed_app_mappings WHERE ownerName = :owner COLLATE NOCASE AND repoName = :repo COLLATE NOCASE LIMIT 1")
    suspend fun getPackageName(owner: String, repo: String): String?

    /**
     * Synchronous version for use on background threads.
     */
    @Query("SELECT packageName FROM installed_app_mappings WHERE ownerName = :owner COLLATE NOCASE AND repoName = :repo COLLATE NOCASE LIMIT 1")
    fun getPackageNameSync(owner: String, repo: String): String?

    /**
     * Delete mapping when user uninstalls an app.
     */
    @Query("DELETE FROM installed_app_mappings WHERE ownerName = :owner COLLATE NOCASE AND repoName = :repo COLLATE NOCASE")
    suspend fun deleteMapping(owner: String, repo: String)

    /**
     * Get all stored mappings as a Flow (for reactive updates in MyApps).
     */
    @Query("SELECT * FROM installed_app_mappings ORDER BY updatedAt DESC")
    fun getAllMappingsFlow(): kotlinx.coroutines.flow.Flow<List<InstalledAppMapping>>
}
