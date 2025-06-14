package com.example.authguardian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "child_locations")
data class ChildLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val childId: String, // Para identificar a qué niño pertenece la ubicación
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null // Opcional: precisión de la ubicación
    // Otros campos que necesites
)