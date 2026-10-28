package com.samyak.repostore.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedRepoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRepos(repos: List<CachedRepoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRepo(repo: CachedRepoEntity)

    @Query("SELECT * FROM cached_repositories ORDER BY stars DESC")
    suspend fun getAllCachedRepos(): List<CachedRepoEntity>

    @Query("SELECT * FROM cached_repositories ORDER BY stars DESC")
    fun observeAllCachedRepos(): Flow<List<CachedRepoEntity>>

    @Query("SELECT * FROM cached_repositories WHERE categoryTag = :categoryTag ORDER BY stars DESC")
    suspend fun getCachedReposByCategory(categoryTag: String): List<CachedRepoEntity>

    @Query("SELECT * FROM cached_repositories WHERE id = :id LIMIT 1")
    suspend fun getCachedRepoById(id: Long): CachedRepoEntity?

    @Query("SELECT * FROM cached_repositories WHERE fullName = :fullName LIMIT 1")
    suspend fun getCachedRepoByFullName(fullName: String): CachedRepoEntity?

    @Query("SELECT * FROM cached_repositories WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY stars DESC")
    suspend fun searchCachedRepos(query: String): List<CachedRepoEntity>

    @Query("DELETE FROM cached_repositories")
    suspend fun clearAll()

    @Query("DELETE FROM cached_repositories WHERE categoryTag = :categoryTag")
    suspend fun clearCategory(categoryTag: String)
}

