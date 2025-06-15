package com.example.authguardian.models

import com.google.firebase.Timestamp

import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId val userId: String = "",
    val email: String = "",
    val role: String = "", // "guardian" or "child"
    val name: String? = null, // Opcional: Para el nombre de usuario
    val associatedChildren: List<String> = emptyList(), // Solo para roles "guardian"
    val guardianId: String? = null // ¡NUEVO CAMPO! Para roles "child"
)