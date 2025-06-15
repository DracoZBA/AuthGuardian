package com.example.authguardian.models // Asegúrate que el paquete sea el correcto

import com.google.firebase.Timestamp

/**
 * Clase base sellada para diferentes tipos de datos de la pulsera.
 */
sealed class BraceletParentData(
    open val timestamp: Timestamp?,
    open val dataType: String?
)

/**
 * Datos de frecuencia cardíaca.
 * `heartRateBpm` es un campo directo.
 */
data class HeartRateData(
    override val timestamp: Timestamp? = null,
    val heartRateBpm: Int = 0, // Campo directo, no anidado
    override val dataType: String? = TYPE_HEART_RATE
) : BraceletParentData(timestamp, dataType) {
    constructor() : this(null, 0, TYPE_HEART_RATE) // Constructor para Firestore

    companion object {
        const val TYPE_HEART_RATE = "heartRate"
    }
}

/**
 * Representa los valores X, Y, Z de los datos del giroscopio.
 */
data class GyroscopeValues(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    constructor() : this(0f, 0f, 0f) // Constructor para Firestore
}

/**
 * Datos del giroscopio.
 * Contiene un objeto anidado [GyroscopeValues] bajo el campo "value".
 */
data class GyroscopeData(
    override val timestamp: Timestamp? = null,
    val value: GyroscopeValues? = null, // Objeto anidado para los valores x, y, z
    override val dataType: String? = TYPE_GYROSCOPE
) : BraceletParentData(timestamp, dataType) {
    constructor() : this(null, null, TYPE_GYROSCOPE) // Constructor para Firestore

    companion object {
        const val TYPE_GYROSCOPE = "gyroscope"
    }
}