package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val skipIntervalSeconds: Int = 10,
    val useDynamicColors: Boolean = false,
    val fileSortMode: String = "NAME_ASC",
    val musicSortMode: String = "NAME_ASC",
    val videoSortMode: String = "NAME_ASC",
    val filterUnder60s: Boolean = true
)
