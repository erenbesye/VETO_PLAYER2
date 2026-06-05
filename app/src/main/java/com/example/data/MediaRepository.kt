package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MediaRepository(private val mediaDao: MediaDao) {
    val settings: Flow<AppSettings> = mediaDao.getSettingsFlow().map { it ?: AppSettings() }
    val favorites: Flow<List<FavoriteMedia>> = mediaDao.getFavoritesFlow()

    suspend fun getSettingsDirect(): AppSettings {
        return mediaDao.getSettings() ?: AppSettings()
    }

    suspend fun saveSettings(settings: AppSettings) {
        mediaDao.saveSettings(settings)
    }

    suspend fun addFavorite(media: FavoriteMedia) {
        mediaDao.addFavorite(media)
    }

    suspend fun deleteFavorite(uri: String) {
        mediaDao.deleteFavorite(uri)
    }

    suspend fun isFavorite(uri: String): Boolean {
        return mediaDao.isFavorite(uri)
    }

    val secureItems: Flow<List<SecureItem>> = mediaDao.getSecureItemsFlow()

    suspend fun insertSecureItem(item: SecureItem) {
        mediaDao.insertSecureItem(item)
    }

    suspend fun deleteSecureItem(id: Long) {
        mediaDao.deleteSecureItem(id)
    }
}
