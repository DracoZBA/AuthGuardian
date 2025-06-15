package com.example.authguardian.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.example.authguardian.data.remote.FirebaseDataSource
import com.example.authguardian.models.ChildProfile
import com.example.authguardian.models.ChildThresholds
import com.example.authguardian.models.User
import kotlinx.coroutines.flow.Flow
// import kotlinx.coroutines.flow.flow // Ya no se usa directamente aquí
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch // Para this@callbackFlow.launch

import javax.inject.Inject
import javax.inject.Singleton

// Imports necesarios para callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow


@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseDataSource: FirebaseDataSource
) {

    fun getAuthState(): Flow<User?> = callbackFlow { // <--- CAMBIADO A callbackFlow
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            if (firebaseUser != null) {
                // Lanza una corrutina para llamar a la función suspend
                // this@callbackFlow es el ProducerScope del callbackFlow
                this@callbackFlow.launch { // o simplemente 'launch { }' dentro de callbackFlow
                    try {
                        Log.d("AuthRepository", "AuthState: User found, fetching profile for ${firebaseUser.uid}")
                        val user = firebaseDataSource.getUserProfile(firebaseUser.uid)
                        Log.d("AuthRepository", "AuthState: Profile fetched: $user")
                        trySend(user) // Correcto para callbackFlow
                    } catch (e: Exception) {
                        Log.e("AuthRepository", "Error fetching user profile for auth state: ${e.message}", e)
                        trySend(null) // Correcto para callbackFlow
                    }
                }
            } else {
                Log.d("AuthRepository", "AuthState: No user")
                trySend(null) // Correcto para callbackFlow
            }
        }
        firebaseAuth.addAuthStateListener(authStateListener)

        // awaitClose es crucial en callbackFlow para limpiar el listener
        // cuando el Flow es cancelado por el colector.
        awaitClose { // Correcto para callbackFlow
            Log.d("AuthRepository", "Removing AuthStateListener from callbackFlow")
            firebaseAuth.removeAuthStateListener(authStateListener)
        }
    }

    suspend fun registerUser(email: String, password: String, role: String): String {
        val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        val userId = authResult.user?.uid ?: throw Exception("User ID not found after registration")

        val user = User(userId, email, role)
        firebaseDataSource.saveUserProfile(user) // Usa FirebaseDataSource para guardar el perfil

        // Si es un niño, también crea un perfil de niño vacío para que el guardian pueda asociarlo
        if (role == "child") {
            val childProfile = ChildProfile(childId = userId, name = "New Child", dob = com.google.firebase.Timestamp(0,0))
            // Esto es un poco especial. El perfil del niño vive bajo el UID del niño en la colección 'users',
            // pero para que el guardián lo encuentre, también lo necesitas bajo 'children_profiles' del guardián.
            // Para simplificar, la app del niño creará su propio ChildProfile bajo su propio UID.
            // El guardián luego 'asociará' al niño buscando por su UID/email.
            // Por ahora, creamos un perfil 'dummy' en la colección users del propio niño,
            // que luego el guardián puede poblar o referenciar.
            // Ajuste: si el perfil del niño está dentro del guardián, esta lógica de registro es para el guardián quien agrega un niño
            // Si el niño se registra solo, su perfil estará directamente en users/{childId}.
            // Para tu estructura actual (`users/{guardianId}/children_profiles/{childId}`),
            // el ChildProfile se crea cuando el GUARDIAN lo agrega, no cuando el NIÑO se registra.
            // Aquí, si un "niño" se registra, solo crea su entrada básica en `users`.
            Log.d("AuthRepository", "User $userId registered with role $role. ChildProfile for associated guardian needs to be set separately.")
        }

        return userId
    }

    suspend fun loginUser(email: String, password: String) {
        firebaseAuth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun logout() {
        firebaseAuth.signOut()
    }

    fun getCurrentFirebaseUser() = firebaseAuth.currentUser

    // Usa FirebaseDataSource
    suspend fun getUserProfile(userId: String): User? {
        return firebaseDataSource.getUserProfile(userId)
    }

    // Usa FirebaseDataSource
    suspend fun getChildProfile(guardianId: String, childId: String): ChildProfile? {
        return firebaseDataSource.getChildProfile(guardianId, childId)
    }

    // Usa FirebaseDataSource
    suspend fun updateChildThresholds(guardianId: String, childId: String, thresholds: ChildThresholds) {
        firebaseDataSource.updateChildThresholds(guardianId, childId, thresholds)
    }

    // Función para que un guardián asocie un niño a su perfil
    suspend fun associateChildToGuardian(guardianId: String, childId: String, childName: String) {
        val childProfile = ChildProfile(childId = childId, name = childName)
        // Llama al método en FirebaseDataSource
        firebaseDataSource.associateChildWithGuardian(guardianId, childId, childProfile)
        Log.d("AuthRepository", "Child $childId association processed via DataSource")
    }

    suspend fun dissociateChildFromGuardian(guardianId: String, childId: String) {
        // Llama al método en FirebaseDataSource
        firebaseDataSource.dissociateChildFromGuardian(guardianId, childId)
        Log.d("AuthRepository", "Child $childId dissociation processed via DataSource")
    }
}