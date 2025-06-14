package com.example.authguardian.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.authguardian.models.User
import com.example.authguardian.models.MeltdownEvent // Asegúrate de tener este import si se usa
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseDataSource @Inject constructor(
    private val auth: FirebaseAuth, // Nombre de la instancia de FirebaseAuth
    private val firestore: FirebaseFirestore
) {

    suspend fun registerUser(email: String, password: String, isGuardian: Boolean): String {
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val userId = authResult.user?.uid ?: throw Exception("User ID not found after registration")

        val user = User(userId, email, isGuardian)
        firestore.collection("users").document(userId).set(user).await()
        return userId
    }

    suspend fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    fun getCurrentFirebaseUser() = auth.currentUser

    suspend fun getUserRole(userId: String): Boolean {
        val doc = firestore.collection("users").document(userId).get().await()
        return doc.getBoolean("isGuardian") ?: false // Asume false si no está definido
    }

    suspend fun uploadMeltdownData(meltdownEvent: MeltdownEvent) { // Asegúrate que el tipo es el correcto
        firestore.collection("meltdown_events")
            .add(meltdownEvent)
            .await()
    }

    // MÉTODO AÑADIDO A FIREBASEDATASOURCE
    fun logout() {
        auth.signOut() // Llama a signOut() en la instancia de FirebaseAuth
    }

    // Agrega funciones para subir datos de pulso, giroscopio, ubicación, geofences, etc.
}