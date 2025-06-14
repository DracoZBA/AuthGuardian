package com.example.authguardian.data.repository // O donde esté tu AuthRepository

import android.util.Log
import com.example.authguardian.data.remote.FirebaseDataSource
import com.example.authguardian.models.MeltdownEvent // Asegúrate que el import sea el correcto para MeltdownEvent

class AuthRepository(
    private val firebaseDataSource: FirebaseDataSource // DEBE TENER ESTE CONSTRUCTOR
) {

    suspend fun registerUser(email: String, password: String, isGuardian: Boolean): String {
        return firebaseDataSource.registerUser(email, password, isGuardian)
    }

    suspend fun loginUser(email: String, password: String) {
        firebaseDataSource.loginUser(email, password)
    }

    // Ya habías renombrado este a getCurrentUser, lo cual es bueno para la consistencia con AuthViewModel
    fun getCurrentUser() = firebaseDataSource.getCurrentFirebaseUser()

    suspend fun getUserRole(userId: String): Boolean {
        Log.d("AuthRepository", "DEBUG: Forcing user role to Guardian (true) for testing.")
        return true
    }

    suspend fun uploadMeltdownData(meltdownEvent: MeltdownEvent) { // Usando el nombre corto por el import
        firebaseDataSource.uploadMeltdownData(meltdownEvent)
    }

    // MÉTODO AÑADIDO
    fun logout() {
        firebaseDataSource.logout() // Delega a FirebaseDataSource
    }

    // ... puedes añadir más funciones que deleguen a firebaseDataSource o que tengan lógica de negocio adicional ...
}