package com.example.authguardian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

// Asumo que "Meltdown" se refiere a un evento crítico o una crisis
@Entity(tableName = "meltdown_events")
data class MeltdownEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val childId: String,
    val eventTimestamp: Long,
    val durationSeconds: Int? = null, // Duración del evento si es aplicable
    val intensity: Int? = null, // Escala de intensidad (ej. 1-5)
    val trigger: String? = null, // Posible desencadenante
    val notes: String? = null // Notas adicionales
    // Otros campos que necesites
)