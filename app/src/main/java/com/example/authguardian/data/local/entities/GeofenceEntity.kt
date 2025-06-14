package com.example.authguardian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geofences")
data class GeofenceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val userId: String // ID del usuario/padre
)