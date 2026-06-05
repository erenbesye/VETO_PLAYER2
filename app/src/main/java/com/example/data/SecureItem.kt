package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "secure_items")
data class SecureItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val encryptedContent: String,
    val signature: String,
    val timestamp: Long
)
