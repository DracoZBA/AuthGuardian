package com.example.authguardian.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

data class ChildLocation(
    val childId: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val geoPoint: GeoPoint = GeoPoint(0.0, 0.0), // Usa GeoPoint de Firestore
    val accuracy: Float = 0f,
    val speed: Float? = null
) {
    // Constructor sin argumentos para Firestore
    constructor() : this("", Timestamp.now(), GeoPoint(0.0, 0.0))
}