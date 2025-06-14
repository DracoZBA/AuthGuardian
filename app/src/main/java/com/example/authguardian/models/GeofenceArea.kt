package com.example.authguardian.models

data class GeofenceArea(
    val id: String,         // Un ID único para la geocerca (ej. UUID.randomUUID().toString())
    val name: String,       // Nombre de la geocerca (ej. "Casa", "Colegio")
    val latitude: Double,   // Latitud del centro
    val longitude: Double,  // Longitud del centro
    val radius: Float,      // Radio en metros
    val userId: String      // ID del padre/tutor que la creó
) {
    // Constructor sin argumentos para Firestore
    constructor() : this("", "", 0.0, 0.0, 0.0f, "")
}