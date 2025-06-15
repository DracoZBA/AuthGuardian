package com.example.authguardian.data.remote

import android.util.Log
import androidx.compose.foundation.layout.size
// Quita la importación no usada: import androidx.compose.foundation.layout.size
import com.example.authguardian.models.* // Importa todos tus modelos de datos
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encapsula todas las interacciones directas con Firebase Firestore.
 * Provee métodos para leer y escribir datos de las colecciones.
 */
@Singleton
class FirebaseDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    // --- Métodos para la APLICACIÓN DEL NIÑO (subir datos) ---

    // Guarda los datos de frecuencia cardíaca en la subcolección 'bracelet_data' del niño.
    suspend fun saveHeartRateData(guardianId: String, childId: String, data: HeartRateData) {
        val path = "users/$guardianId/children_profiles/$childId/$BRACELET_DATA_SUBCOLLECTION"
        firestore.collection(path).add(data).await()
        Log.d(TAG, "Heart rate data uploaded for child $childId to path $path")
    }

    // Guarda los datos del giroscopio en la subcolección 'bracelet_data' del niño.
    suspend fun saveGyroscopeData(guardianId: String, childId: String, data: GyroscopeData) {
        val path = "users/$guardianId/children_profiles/$childId/$BRACELET_DATA_SUBCOLLECTION"
        firestore.collection(path).add(data).await()
        Log.d(TAG, "Gyroscope data uploaded for child $childId to path $path")
    }

    // Guarda un evento de crisis en la subcolección 'meltdown_events' del niño.
    suspend fun saveMeltdownEvent(guardianId: String, childId: String, event: MeltdownEvent) {
        val path = "users/$guardianId/children_profiles/$childId/$MELTDOWN_EVENTS_SUBCOLLECTION"
        firestore.collection(path).add(event).await()
        Log.d(TAG, "Meltdown event uploaded for child $childId to path $path")
    }

    // Guarda la ubicación del niño en la subcolección 'child_locations'.
    suspend fun saveChildLocation(guardianId: String, childId: String, location: ChildLocation) {
        val path = "users/$guardianId/children_profiles/$childId/$CHILD_LOCATIONS_SUBCOLLECTION"
        firestore.collection(path).add(location).await()
        Log.d(TAG, "Child location uploaded for child $childId to path $path")
    }

    // Guarda una geocerca definida por el padre en la subcolección 'geofences'.
    suspend fun saveGeofence(guardianId: String, childId: String, geofence: GeofenceArea) {
        val path = "users/$guardianId/children_profiles/$childId/$GEOFENCES_SUBCOLLECTION"
        // Asumiendo que GeofenceArea tiene un campo 'id' que es String y único
        firestore.collection(path).document(geofence.id).set(geofence).await()
        Log.d(TAG, "Geofence saved to Firestore: ${geofence.name} (ID: ${geofence.id}) in path $path")
    }

    // Elimina una geocerca específica de la subcolección 'geofences'.
    suspend fun deleteGeofence(guardianId: String, childId: String, geofenceId: String) {
        val path = "users/$guardianId/children_profiles/$childId/$GEOFENCES_SUBCOLLECTION"
        firestore.collection(path).document(geofenceId).delete().await()
        Log.d(TAG, "Geofence deleted from Firestore: $geofenceId in path $path")
    }

    // --- Métodos de Autenticación y Perfil ---

    suspend fun getUserProfile(userId: String): User? {
        return try {
            firestore.collection(USERS_COLLECTION).document(userId).get().await().toObject(User::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user profile for $userId: ${e.message}", e)
            null
        }
    }

    suspend fun saveUserProfile(user: User) {
        try {
            firestore.collection(USERS_COLLECTION).document(user.userId).set(user).await()
            Log.d(TAG, "User profile saved for ${user.userId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user profile for ${user.userId}: ${e.message}", e)
        }
    }

    suspend fun getChildProfile(guardianId: String, childId: String): ChildProfile? {
        return try {
            firestore.collection(USERS_COLLECTION).document(guardianId)
                .collection(CHILDREN_PROFILES_SUBCOLLECTION).document(childId)
                .get().await().toObject(ChildProfile::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching child profile $childId for guardian $guardianId: ${e.message}", e)
            null
        }
    }

    suspend fun updateChildThresholds(guardianId: String, childId: String, thresholds: ChildThresholds) {
        try {
            val childProfileRef = firestore.collection(USERS_COLLECTION).document(guardianId)
                .collection(CHILDREN_PROFILES_SUBCOLLECTION).document(childId)
            childProfileRef.update("thresholds", thresholds).await()
            Log.d(TAG, "Child thresholds updated for child $childId")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating child thresholds for $childId: ${e.message}", e)
        }
    }

    // --- Métodos para la APLICACIÓN DEL PADRE (leer datos en tiempo real) ---

    fun getChildrenProfilesStream(guardianId: String): Flow<List<ChildProfile>> = callbackFlow {
        val collectionPath = "$USERS_COLLECTION/$guardianId/$CHILDREN_PROFILES_SUBCOLLECTION"
        Log.d(TAG_STREAM, "getChildrenProfilesStream: Listening to $collectionPath")
        val subscription = firestore.collection(collectionPath)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG_STREAM, "getChildrenProfilesStream: Listen failed for $collectionPath.", e)
                    close(e)
                    return@addSnapshotListener
                }
                val children = snapshot?.documents?.mapNotNull {
                    try {
                        it.toObject(ChildProfile::class.java)
                    } catch (mapError: Exception) {
                        Log.e(TAG_STREAM, "getChildrenProfilesStream: Error mapping document ${it.id} to ChildProfile in $collectionPath", mapError)
                        null
                    }
                } ?: emptyList()
                Log.d(TAG_STREAM, "getChildrenProfilesStream: Received ${children.size} children profiles from $collectionPath")
                if(!trySend(children).isSuccess) {
                    Log.w(TAG_STREAM, "getChildrenProfilesStream: Failed to send children profiles to flow collector from $collectionPath")
                }
            }
        awaitClose {
            Log.d(TAG_STREAM, "getChildrenProfilesStream: Closing listener for $collectionPath")
            subscription.remove()
        }
    }

    fun getChildLocationStream(guardianId: String, childId: String): Flow<ChildLocation?> = callbackFlow {
        val collectionPath = "$USERS_COLLECTION/$guardianId/$CHILDREN_PROFILES_SUBCOLLECTION/$childId/$CHILD_LOCATIONS_SUBCOLLECTION"
        Log.d(TAG_STREAM, "getChildLocationStream: Listening to $collectionPath")
        val query = firestore.collection(collectionPath)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)

        val subscription = query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e(TAG_STREAM, "getChildLocationStream: Listen failed for $collectionPath.", e)
                close(e)
                return@addSnapshotListener
            }
            val latestLocation = snapshot?.documents?.firstOrNull()?.let {
                try {
                    it.toObject(ChildLocation::class.java)
                } catch (mapError: Exception) {
                    Log.e(TAG_STREAM, "getChildLocationStream: Error mapping document ${it.id} to ChildLocation in $collectionPath", mapError)
                    null
                }
            }
            Log.d(TAG_STREAM, "getChildLocationStream: Received latest location (null: ${latestLocation == null}) from $collectionPath")
            if(!trySend(latestLocation).isSuccess) {
                Log.w(TAG_STREAM, "getChildLocationStream: Failed to send location to flow collector from $collectionPath")
            }
        }
        awaitClose {
            Log.d(TAG_STREAM, "getChildLocationStream: Closing listener for $collectionPath")
            subscription.remove()
        }
    }

    // OBTENER DATOS DE FRECUENCIA CARDÍACA (CON LOGS MEJORADOS)
    fun getHeartRateDataStream(guardianId: String, childId: String, startTime: Timestamp, endTime: Timestamp): Flow<List<HeartRateData>> = callbackFlow {
        val collectionPath = "$USERS_COLLECTION/$guardianId/$CHILDREN_PROFILES_SUBCOLLECTION/$childId/$BRACELET_DATA_SUBCOLLECTION"
        val query = firestore.collection(collectionPath)
            .whereEqualTo("dataType", HeartRateData.TYPE_HEART_RATE) // Usar constante del modelo
            .whereGreaterThanOrEqualTo("timestamp", startTime)
            .whereLessThanOrEqualTo("timestamp", endTime)
            .orderBy("timestamp", Query.Direction.ASCENDING)

        Log.d(FDS_HEART_RATE_TAG, "Querying for heart rate data. Path: $collectionPath, DataType: ${HeartRateData.TYPE_HEART_RATE}, Start: $startTime, End: $endTime")

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(FDS_HEART_RATE_TAG, "Listen for heart rate data failed.", error)
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null) {
                Log.w(FDS_HEART_RATE_TAG, "Snapshot is null for heart rate.")
                trySend(emptyList()).isSuccess
                return@addSnapshotListener
            }

            Log.d(FDS_HEART_RATE_TAG, "Received heart rate snapshot. Document count: ${snapshot.size()}. Changes: ${snapshot.documentChanges.size}")
            snapshot.documents.forEach { doc ->
                Log.d(FDS_HEART_RATE_TAG, "Raw HeartRate Doc ID: ${doc.id}, Exists: ${doc.exists()}, Data: ${doc.data}")
            }

            val dataList = mutableListOf<HeartRateData>()
            for (document in snapshot.documents) {
                if (!document.exists()) {
                    Log.w(FDS_HEART_RATE_TAG, "HeartRate document ${document.id} does not exist, skipping.")
                    continue
                }
                try {
                    val hrObject = document.toObject(HeartRateData::class.java)
                    if (hrObject != null) {
                        // Verificación adicional de campos clave
                        if (hrObject.timestamp == null || hrObject.dataType == null) { // heartRateBpm puede ser 0, así que no se verifica si es null
                            Log.w(FDS_HEART_RATE_TAG, "Mapped HeartRateData for ${document.id} has null critical fields. Timestamp: ${hrObject.timestamp}, DataType: ${hrObject.dataType}, BPM: ${hrObject.heartRateBpm}. Raw Data: ${document.data}")
                        }
                        dataList.add(hrObject)
                    } else {
                        Log.w(FDS_HEART_RATE_TAG, "toObject() returned null for heart rate document ${document.id}. Raw Data: ${document.data}")
                    }
                } catch (mapException: Exception) {
                    Log.e(FDS_HEART_RATE_TAG, "Exception mapping heart rate document ${document.id} to HeartRateData: ${mapException.message}", mapException)
                }
            }
            Log.d(FDS_HEART_RATE_TAG, "Successfully mapped ${dataList.size} HeartRateData objects out of ${snapshot.size()} documents.")

            if (!trySend(dataList).isSuccess) {
                Log.w(FDS_HEART_RATE_TAG, "Failed to send heart rate data list to the flow collector.")
            }
        }
        awaitClose {
            Log.d(FDS_HEART_RATE_TAG, "HeartRateDataStream Flow is closing. Removing listener for path: $collectionPath")
            subscription.remove()
        }
    }

    // OBTENER DATOS DE GIROSCOPIO (CON LOGS MEJORADOS, MANTENIDOS DE TU VERSIÓN ANTERIOR)
    fun getGyroscopeDataStream(guardianId: String, childId: String, startTime: Timestamp, endTime: Timestamp): Flow<List<GyroscopeData>> = callbackFlow {
        val collectionPath = "$USERS_COLLECTION/$guardianId/$CHILDREN_PROFILES_SUBCOLLECTION/$childId/$BRACELET_DATA_SUBCOLLECTION"
        val query = firestore.collection(collectionPath)
            .whereEqualTo("dataType", GyroscopeData.TYPE_GYROSCOPE) // Usar la constante es más seguro
            .whereGreaterThanOrEqualTo("timestamp", startTime)
            .whereLessThanOrEqualTo("timestamp", endTime)
            .orderBy("timestamp", Query.Direction.ASCENDING)

        Log.d(FDS_GYRO_TAG, "Querying for gyroscope data. Path: $collectionPath, DataType: ${GyroscopeData.TYPE_GYROSCOPE}, Start: $startTime, End: $endTime")

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(FDS_GYRO_TAG, "Listen for gyroscope data failed.", error)
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null) {
                Log.w(FDS_GYRO_TAG, "Snapshot is null for gyroscope, no data or error.")
                trySend(emptyList()).isSuccess
                return@addSnapshotListener
            }

            Log.d(FDS_GYRO_TAG, "Received gyroscope snapshot. Document count: ${snapshot.size()}. Changes: ${snapshot.documentChanges.size}")
            snapshot.documents.forEach { document ->
                Log.d(FDS_GYRO_TAG, "Raw Gyro Doc ID: ${document.id}, Exists: ${document.exists()}, Data: ${document.data}")
            }

            val dataList = mutableListOf<GyroscopeData>()
            for (document in snapshot.documents) {
                if (!document.exists()) {
                    Log.w(FDS_GYRO_TAG, "Gyroscope document ${document.id} does not exist, skipping.")
                    continue
                }
                try {
                    val gyroObject = document.toObject(GyroscopeData::class.java)
                    if (gyroObject != null) {
                        if (gyroObject.timestamp == null || gyroObject.value == null || gyroObject.dataType == null) {
                            Log.w(FDS_GYRO_TAG, "Mapped GyroscopeData for ${document.id} has null critical fields. Timestamp: ${gyroObject.timestamp}, Value: ${gyroObject.value}, DataType: ${gyroObject.dataType}. Raw Data: ${document.data}")
                        }
                        dataList.add(gyroObject)
                    } else {
                        Log.w(FDS_GYRO_TAG, "toObject() returned null for gyroscope document ${document.id}. Raw Data: ${document.data}")
                    }
                } catch (mapException: Exception) {
                    Log.e(FDS_GYRO_TAG, "Exception mapping gyroscope document ${document.id} to GyroscopeData: ${mapException.message}", mapException)
                }
            }
            Log.d(FDS_GYRO_TAG, "Successfully mapped ${dataList.size} GyroscopeData objects out of ${snapshot.size()} documents.")

            if (!trySend(dataList).isSuccess) {
                Log.w(FDS_GYRO_TAG, "Failed to send gyroscope data list to the flow collector.")
            }
        }
        awaitClose {
            Log.d(FDS_GYRO_TAG, "GyroscopeDataStream Flow is closing. Removing listener for path: $collectionPath")
            subscription.remove()
        }
    }

    fun getMeltdownEventsStream(guardianId: String, childId: String, startTime: Timestamp, endTime: Timestamp): Flow<List<MeltdownEvent>> = callbackFlow {
        val collectionPath = "$USERS_COLLECTION/$guardianId/$CHILDREN_PROFILES_SUBCOLLECTION/$childId/$MELTDOWN_EVENTS_SUBCOLLECTION"
        Log.d(TAG_STREAM, "getMeltdownEventsStream: Listening to $collectionPath, Start: $startTime, End: $endTime")
        val query = firestore.collection(collectionPath)
            .whereGreaterThanOrEqualTo("startTime", startTime) // Asegúrate que MeltdownEvent tenga "startTime" como Timestamp
            .whereLessThanOrEqualTo("startTime", endTime)
            .orderBy("startTime", Query.Direction.DESCENDING)

        val subscription = query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e(TAG_STREAM, "getMeltdownEventsStream: Listen failed for $collectionPath.", e)
                close(e)
                return@addSnapshotListener
            }
            val events = snapshot?.documents?.mapNotNull {
                try {
                    it.toObject(MeltdownEvent::class.java)
                } catch (mapError: Exception) {
                    Log.e(TAG_STREAM, "getMeltdownEventsStream: Error mapping document ${it.id} to MeltdownEvent in $collectionPath", mapError)
                    null
                }
            } ?: emptyList()
            Log.d(TAG_STREAM, "getMeltdownEventsStream: Received ${events.size} meltdown events from $collectionPath")
            if(!trySend(events).isSuccess) {
                Log.w(TAG_STREAM, "getMeltdownEventsStream: Failed to send meltdown events to flow collector from $collectionPath")
            }
        }
        awaitClose {
            Log.d(TAG_STREAM, "getMeltdownEventsStream: Closing listener for $collectionPath")
            subscription.remove()
        }
    }

    fun getGeofencesStream(guardianId: String, childId: String): Flow<List<GeofenceArea>> = callbackFlow {
        val collectionPath = "$USERS_COLLECTION/$guardianId/$CHILDREN_PROFILES_SUBCOLLECTION/$childId/$GEOFENCES_SUBCOLLECTION"
        Log.d(TAG_STREAM, "getGeofencesStream: Listening to $collectionPath")
        val query = firestore.collection(collectionPath)
        // No se suele ordenar las geocercas por un campo específico a menos que sea necesario.
        // Si necesitas un orden, añade .orderBy("fieldName")

        val subscription = query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e(TAG_STREAM, "getGeofencesStream: Listen failed for $collectionPath.", e)
                close(e)
                return@addSnapshotListener
            }
            val geofences = snapshot?.documents?.mapNotNull {
                try {
                    it.toObject(GeofenceArea::class.java)
                } catch (mapError: Exception) {
                    Log.e(TAG_STREAM, "getGeofencesStream: Error mapping document ${it.id} to GeofenceArea in $collectionPath", mapError)
                    null
                }
            } ?: emptyList()
            Log.d(TAG_STREAM, "getGeofencesStream: Received ${geofences.size} geofences from $collectionPath")
            if(!trySend(geofences).isSuccess) {
                Log.w(TAG_STREAM, "getGeofencesStream: Failed to send geofences to flow collector from $collectionPath")
            }
        }
        awaitClose {
            Log.d(TAG_STREAM, "getGeofencesStream: Closing listener for $collectionPath")
            subscription.remove()
        }
    }

    fun getUserGraphsStream(userId: String): Flow<List<UserGraph>> = callbackFlow {
        val collectionPath = "$USERS_COLLECTION/$userId/$USER_GRAPHS_SUBCOLLECTION"
        Log.d(TAG_STREAM, "getUserGraphsStream: Listening to $collectionPath")
        val query = firestore.collection(collectionPath)
            .orderBy("generatedAt", Query.Direction.DESCENDING) // Asegúrate que UserGraph tenga "generatedAt" como Timestamp

        val subscription = query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e(TAG_STREAM, "getUserGraphsStream: Listen failed for $collectionPath.", e)
                close(e)
                return@addSnapshotListener
            }
            val graphs = snapshot?.documents?.mapNotNull {
                try {
                    it.toObject(UserGraph::class.java)
                } catch (mapError: Exception) {
                    Log.e(TAG_STREAM, "getUserGraphsStream: Error mapping document ${it.id} to UserGraph in $collectionPath", mapError)
                    null
                }
            } ?: emptyList()
            Log.d(TAG_STREAM, "getUserGraphsStream: Received ${graphs.size} user graphs from $collectionPath")
            if(!trySend(graphs).isSuccess) {
                Log.w(TAG_STREAM, "getUserGraphsStream: Failed to send user graphs to flow collector from $collectionPath")
            }
        }
        awaitClose {
            Log.d(TAG_STREAM, "getUserGraphsStream: Closing listener for $collectionPath")
            subscription.remove()
        }
    }

    suspend fun associateChildWithGuardian(guardianId: String, childId: String, childProfile: ChildProfile) {
        try {
            firestore.collection(USERS_COLLECTION).document(guardianId)
                .collection(CHILDREN_PROFILES_SUBCOLLECTION).document(childId)
                .set(childProfile).await()

            firestore.collection(USERS_COLLECTION).document(guardianId)
                .update("associatedChildren", FieldValue.arrayUnion(childId)).await() // Usar FieldValue
            Log.d(TAG, "Child $childId associated with guardian $guardianId in Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Error associating child $childId with guardian $guardianId: ${e.message}", e)
        }
    }

    suspend fun dissociateChildFromGuardian(guardianId: String, childId: String) {
        try {
            // Eliminar el perfil del niño
            firestore.collection(USERS_COLLECTION).document(guardianId)
                .collection(CHILDREN_PROFILES_SUBCOLLECTION).document(childId)
                .delete().await()

            // Quitar de la lista associatedChildren del guardián
            firestore.collection(USERS_COLLECTION).document(guardianId)
                .update("associatedChildren", FieldValue.arrayRemove(childId)).await() // Usar FieldValue
            Log.d(TAG, "Child $childId dissociated from guardian $guardianId in Firestore")

            // Opcional: Eliminar subcolecciones del niño (geofences, bracelet_data, etc.) si es necesario.
            // Esto requeriría más lógica para listar y eliminar documentos en cada subcolección.
            // Ejemplo para bracelet_data:
            // val braceletDataPath = "$USERS_COLLECTION/$guardianId/$CHILDREN_PROFILES_SUBCOLLECTION/$childId/$BRACELET_DATA_SUBCOLLECTION"
            // firestore.collection(braceletDataPath).get().await().documents.forEach { it.reference.delete().await() }
            // Log.d(TAG, "Associated bracelet_data for child $childId deleted.")
            // Repetir para otras subcolecciones.

        } catch (e: Exception) {
            Log.e(TAG, "Error dissociating child $childId from guardian $guardianId: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "FirebaseDataSource" // Tag general para operaciones de escritura/lectura única
        private const val TAG_STREAM = "FDS_Stream" // Tag para logs generales de los flows/streams
        private const val FDS_HEART_RATE_TAG = "FDS_HeartRate" // Tag específico para HeartRate Stream
        private const val FDS_GYRO_TAG = "FDS_Gyro"          // Tag específico para Gyroscope Stream

        // Nombres de Colecciones (coinciden con TestDataGenerator y tu estructura)
        private const val USERS_COLLECTION = "users"
        private const val CHILDREN_PROFILES_SUBCOLLECTION = "children_profiles"
        private const val GEOFENCES_SUBCOLLECTION = "geofences"
        private const val CHILD_LOCATIONS_SUBCOLLECTION = "child_locations"
        private const val BRACELET_DATA_SUBCOLLECTION = "bracelet_data"
        private const val MELTDOWN_EVENTS_SUBCOLLECTION = "meltdown_events"
        private const val USER_GRAPHS_SUBCOLLECTION = "user_graphs"
    }
}