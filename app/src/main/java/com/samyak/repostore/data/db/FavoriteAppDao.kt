package com.samyak.repostore.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.samyak.repostore.data.model.FavoriteApp
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteAppDao {

    @Query("SELECT * FROM favorite_apps ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteApp>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_apps WHERE id = :repoId)")
    fun isFavorite(repoId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_apps WHERE id = :repoId)")
    suspend fun isFavoriteSync(repoId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(app: FavoriteApp)

    @Query("DELETE FROM favorite_apps WHERE id = :repoId")
    suspend fun removeFavorite(repoId: Long)

    @Query("SELECT COUNT(*) FROM favorite_apps")
    fun getFavoriteCount(): Flow<Int>
}
