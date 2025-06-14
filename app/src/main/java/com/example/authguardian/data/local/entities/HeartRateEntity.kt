package com.example.authguardian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "heart_rate_data")
data class HeartRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String, // ID del usuario/niño asociado
    val timestamp: Long, // Unix timestamp
    val bpm: Int // Pulsaciones por minuto
)