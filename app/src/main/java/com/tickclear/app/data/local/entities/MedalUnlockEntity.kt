package com.tickclear.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medal_unlock")
data class MedalUnlockEntity(
    @PrimaryKey val medalKey: String,
    val unlockedAt: Long = System.currentTimeMillis(),
)
