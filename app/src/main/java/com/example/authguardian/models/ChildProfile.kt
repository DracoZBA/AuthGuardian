package com.example.authguardian.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId // Importa la anotación

data class ChildProfile(
    @DocumentId // <--- ¡Añadido aquí!
    val childId: String = "", // Este será el UID del documento en Firestore
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