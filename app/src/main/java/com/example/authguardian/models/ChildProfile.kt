package com.example.authguardian.models

import com.google.firebase.Timestamp

data class ChildProfile(
    val childId: String = "", // Este será el UID del usuario "child"
    val name: String = "",
    val dob: Timestamp = Timestamp(0, 0), // Fecha de nacimiento
    val avatarUrl: String? = null,
    val thresholds: ChildThresholds = ChildThresholds() // Objeto anidado para configuraciones
) {
    // Constructor sin argumentos para Firestore
    constructor() : this("", "", Timestamp(0, 0))
}

data class ChildThresholds(
    val meltdownSeverityThreshold: Int = 5, // Nivel por defecto
    val alertVibrationPattern: String = "default",
    val alertSound: String = "default"
) {
    // Constructor sin argumentos para Firestore
    constructor() : this(5, "default", "default")
}