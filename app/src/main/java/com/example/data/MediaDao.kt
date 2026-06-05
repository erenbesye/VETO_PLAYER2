package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)

    @Query("SELECT * FROM favorite_media ORDER BY dateAdded DESC")
    fun getFavoritesFlow(): Flow<List<FavoriteMedia>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(media: FavoriteMedia)

    @Query("DELETE FROM favorite_media WHERE uri = :uri")
    suspend fun deleteFavorite(uri: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_media WHERE uri = :uri)")
    suspend fun isFavorite(uri: String): Boolean

    @Query("SELECT * FROM secure_items ORDER BY timestamp DESC")
    fun getSecureItemsFlow(): Flow<List<SecureItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSecureItem(item: SecureItem)

    @Query("DELETE FROM secure_items WHERE id = :id")
    suspend fun deleteSecureItem(id: Long)
}
