package com.example.authguardian.util

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.example.authguardian.models.HeartRateData // Para usar la constante TYPE_HEART_RATE
import com.example.authguardian.models.GyroscopeData // Para usar la constante TYPE_GYROSCOPE
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID
import kotlin.random.Random // Para una generación de números aleatorios más idiomática en Kotlin

/**
 * Clase de utilidad para generar datos de prueba y poblar Firestore
 * siguiendo el esquema definido.
 */
object TestDataGenerator {

    private const val TAG = "TestDataGenerator"
    private val firestore = FirebaseFirestore.getInstance()

    const val TEST_GUARDIAN_UID = "jf7fGTnocebkw9pRqZxNX9KkLIG3" // Reemplaza con tu UID real si es necesario
    const val TEST_GUARDIAN_EMAIL = "padreejemplo@gmail.com"
    const val TEST_GUARDIAN_NAME = "Padre Ejemplo"

    private const val USERS_COLLECTION = "users"
    private const val CHILDREN_PROFILES_SUBCOLLECTION = "children_profiles"
    private const val GEOFENCES_SUBCOLLECTION = "geofences"
    private const val CHILD_LOCATIONS_SUBCOLLECTION = "child_locations"
    private const val BRACELET_DATA_SUBCOLLECTION = "bracelet_data"
    private const val MELTDOWN_EVENTS_SUBCOLLECTION = "meltdown_events"
    private const val USER_GRAPHS_SUBCOLLECTION = "user_graphs"

    suspend fun generateAllTestData() {
        try {
            Log.d(TAG, "Iniciando generación de datos de prueba para el guardián: $TEST_GUARDIAN_UID")

            val userDocRef = firestore.collection(USERS_COLLECTION).document(TEST_GUARDIAN_UID)
            val guardianUser = mapOf(
                "email" to TEST_GUARDIAN_EMAIL,
                "role" to "guardian",
                "createdAt" to Timestamp.now(),
                "lastLogin" to Timestamp.now(),
                "name" to TEST_GUARDIAN_NAME,
                "associatedChildren" to emptyList<String>()
            )
            userDocRef.set(guardianUser).await()
            Log.d(TAG, "Documento de usuario guardián creado/actualizado: ${userDocRef.id}")

            val childrenProfilesCollectionRef = userDocRef.collection(CHILDREN_PROFILES_SUBCOLLECTION)

            // --- Primer Niño ---
            val child1Id = "child_test_01_${UUID.randomUUID().toString().take(4)}" // ID más legible
            val child1Profile = mapOf(
                "childId" to child1Id,
                "name" to "Hijo de Prueba 1",
                "dob" to Timestamp(Calendar.getInstance().apply { set(2018, Calendar.APRIL, 15) }.time),
                "avatarUrl" to "https://firebasestorage.googleapis.com/v0/b/your-project.appspot.com/o/avatars%2Fchild1_avatar.png?alt=media",
                "thresholds" to mapOf(
                    "meltdownSeverityThreshold" to 7,
                    "alertVibrationPattern" to "long",
                    "alertSound" to "chime"
                )
            )
            childrenProfilesCollectionRef.document(child1Id).set(child1Profile).await()
            Log.d(TAG, "Perfil de hijo 1 creado: $child1Id")

            // --- Segundo Niño (Opcional) ---
            val child2Id = "child_test_02_${UUID.randomUUID().toString().take(4)}"
            val child2Profile = mapOf(
                "childId" to child2Id,
                "name" to "Hija de Prueba 2",
                "dob" to Timestamp(Calendar.getInstance().apply { set(2020, Calendar.JULY, 20) }.time),
                "avatarUrl" to "https://firebasestorage.googleapis.com/v0/b/your-project.appspot.com/o/avatars%2Fchild2_avatar.png?alt=media",
                "thresholds" to mapOf(
                    "meltdownSeverityThreshold" to 5,
                    "alertVibrationPattern" to "short",
                    "alertSound" to "beep"
                )
            )
            childrenProfilesCollectionRef.document(child2Id).set(child2Profile).await()
            Log.d(TAG, "Perfil de hijo 2 creado: $child2Id")

            val associatedChildrenIds = listOf(child1Id, child2Id)
            userDocRef.update("associatedChildren", associatedChildrenIds).await()
            Log.d(TAG, "Campo 'associatedChildren' actualizado para el guardián.")

            // --- Generar Datos para el Hijo 1 ---
            val child1DocRef = childrenProfilesCollectionRef.document(child1Id)
            generateDataForChild(child1DocRef, child1Id)

            // --- Generar Datos para el Hijo 2 (si lo deseas) ---
            // val child2DocRef = childrenProfilesCollectionRef.document(child2Id)
            // generateDataForChild(child2DocRef, child2Id) // Descomenta si quieres datos para el hijo 2

            Log.d(TAG, "¡Generación de todos los datos de prueba completada exitosamente!")

        } catch (e: Exception) {
            Log.e(TAG, "Error al generar datos de prueba: ${e.message}", e)
        }
    }

    private suspend fun generateDataForChild(childDocRef: com.google.firebase.firestore.DocumentReference, childId: String) {
        Log.d(TAG, "Generando datos para el childId: $childId")
        // 3. Geocercas (Sin cambios significativos necesarios aquí)
        val geofencesCollectionRef = childDocRef.collection(GEOFENCES_SUBCOLLECTION)
        // ... (código de geocercas como lo tenías)
        Log.d(TAG, "Geocercas generadas para $childId.")

        // 4. Historial de Ubicaciones (Sin cambios significativos necesarios aquí)
        val childLocationsCollectionRef = childDocRef.collection(CHILD_LOCATIONS_SUBCOLLECTION)
        // ... (código de ubicaciones como lo tenías)
        Log.d(TAG, "Ubicaciones generadas para $childId.")


        // 5. Generar Datos de Pulsera (Ritmo Cardíaco y Giroscopio)
        val braceletDataCollectionRef = childDocRef.collection(BRACELET_DATA_SUBCOLLECTION)
        val currentTimeMillis = System.currentTimeMillis()

        Log.d(TAG, "Generando 20 puntos de datos de pulsera para $childId...")
        for (i in 0 until 20) { // 20 puntos de datos
            // Generar timestamps que sean realistas para las consultas (ej. en las últimas 20 horas)
            val pastTimeMillis = currentTimeMillis - (i * 60 * 60 * 1000L) // i horas atrás
            val timestamp = Timestamp(pastTimeMillis / 1000, ((pastTimeMillis % 1000) * 1000000).toInt())


            // *** CAMBIO CLAVE AQUÍ para HeartRateData ***
            val heartRateMap = mapOf(
                "dataType" to HeartRateData.TYPE_HEART_RATE, // Usar constante del modelo
                "timestamp" to timestamp,
                "heartRateBpm" to (70 + Random.nextInt(0, 31)) // BPM entre 70 y 100
            )
            braceletDataCollectionRef.add(heartRateMap).await()

            // Datos de Giroscopio (ESTO SE MANTIENE COMO ESTABA, CON EL 'value' ANIDADO)
            val gyroscopeMap = mapOf(
                "dataType" to GyroscopeData.TYPE_GYROSCOPE, // Usar constante del modelo
                "timestamp" to timestamp,
                "value" to mapOf(
                    "x" to (Random.nextFloat() * 2 - 1), // Valores entre -1.0 y 1.0
                    "y" to (Random.nextFloat() * 2 - 1),
                    "z" to (Random.nextFloat() * 2 - 1)
                )
            )
            braceletDataCollectionRef.add(gyroscopeMap).await()
        }
        Log.d(TAG, "Datos de pulsera generados para $childId.")

        // 6. Eventos de Crisis (Sin cambios significativos necesarios aquí)
        val meltdownEventsCollectionRef = childDocRef.collection(MELTDOWN_EVENTS_SUBCOLLECTION)
        // ... (código de eventos de crisis como lo tenías)
        Log.d(TAG, "Eventos de crisis generados para $childId.")

        // 7. Gráficos de Usuario (Sin cambios aquí, pero asegúrate que los childId coincidan)
        // ... (código de gráficos de usuario como lo tenías, asegúrate de usar el childId correcto)
        Log.d(TAG, "Gráficos de usuario generados para el guardián (asociados a $childId si aplica).")
    }
}