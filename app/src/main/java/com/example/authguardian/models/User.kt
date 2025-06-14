package com.example.authguardian.models

data class User(
    val uid: String,        // Or whatever you named the user ID field
    val email: String,
    val isGuardian: Boolean,
)