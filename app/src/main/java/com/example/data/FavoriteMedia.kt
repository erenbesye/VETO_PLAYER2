package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_media")
data class FavoriteMedia(
    @PrimaryKey val uri: String,
    val title: String,
    val artist: String?,
    val duration: Long,
    val isVideo: Boolean,
    val dateAdded: Long = System.currentTimeMillis()
)
