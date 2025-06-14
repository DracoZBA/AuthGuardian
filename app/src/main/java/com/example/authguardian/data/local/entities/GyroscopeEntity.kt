package com.example.authguardian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gyroscope_readings") // Puedes elegir el nombre de la tabla
data class GyroscopeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val xValue: Float,
    val yValue: Float,
    val zValue: Float
    // Otros campos que necesites
)