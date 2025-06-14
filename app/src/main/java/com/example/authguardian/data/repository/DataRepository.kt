package com.example.authguardian.data.repository

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.example.authguardian.data.local.AppDatabase
import com.example.authguardian.data.local.dao.GeofenceDao
import com.example.authguardian.data.local.entities.ChildLocationEntity
import com.example.authguardian.data.local.entities.GeofenceEntity
import com.example.authguardian.models.GeofenceArea
import com.example.authguardian.util.GeofenceHelper
import com.example.authguardian.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val appDatabase: AppDatabase,
    private val geofenceHelper: GeofenceHelper
) {
    private val geofenceDao: GeofenceDao = appDatabase.geofenceDao()
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    companion object {
        private const val TAG = "DataRepository"
    }

    // --- Flujo de ubicación en tiempo real ---
    private val _childCurrentLocation = MutableStateFlow<ChildLocationEntity?>(null)
    val childCurrentLocation: StateFlow<ChildLocationEntity?> = _childCurrentLocation

    // --- Conversiones entre Entity y Model ---
    private fun GeofenceEntity.toGeofenceArea(): GeofenceArea {
        return GeofenceArea(id, name, latitude, longitude, radius, userId)
    }

    private fun GeofenceArea.toGeofenceEntity(): GeofenceEntity {
        return GeofenceEntity(id, name, latitude, longitude, radius, userId)
    }

    // --- Manejo de ubicación ---
    suspend fun saveChildLocation(location: ChildLocationEntity) {
        try {
            // Guardar localmente
            appDatabase.childLocationDao().insertLocation(location)

            // Actualizar flujo
            _childCurrentLocation.value = location

            // Opcional: Sincronizar con Firestore
            firestore.collection("child_locations")
                .document(location.childId)
                .set(location)
                .await()

            Log.d(TAG, "Child location saved: ${location.latitude}, ${location.longitude}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving child location", e)
            throw e
        }
    }

    // --- Geofences ---
    @Throws(Exception::class)
    suspend fun addGeofence(geofenceArea: GeofenceArea) {
        val geofence = geofenceHelper.getGeofence(
            geofenceArea.id,
            com.google.android.gms.maps.model.LatLng(geofenceArea.latitude, geofenceArea.longitude),
            geofenceArea.radius,
            Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
        )

        try {
            // Registrar en sistema Android
            geofencingClient.addGeofences(
                geofenceHelper.getGeofencingRequest(geofence),
                geofenceHelper.getPendingIntent()
            ).await()

            // Guardar localmente
            geofenceDao.insertGeofence(geofenceArea.toGeofenceEntity())

            // Sincronizar con Firestore
            firestore.collection("geofences")
                .document(geofenceArea.id)
                .set(geofenceArea)
                .await()

            Log.d(TAG, "Geofence added: ${geofenceArea.name}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permissions not granted", e)
            throw Exception("Location permissions required")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding geofence", e)
            throw e
        }
    }

    @Throws(Exception::class)
    suspend fun removeGeofence(geofenceId: String) {
        try {
            // Remover del sistema Android
            geofencingClient.removeGeofences(listOf(geofenceId)).await()

            // Eliminar localmente
            geofenceDao.deleteGeofence(geofenceId)

            // Eliminar de Firestore
            firestore.collection("geofences")
                .document(geofenceId)
                .delete()
                .await()

            Log.d(TAG, "Geofence removed: $geofenceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing geofence", e)
            throw e
        }
    }

    fun getGeofencesForChild(userId: String): Flow<List<GeofenceArea>> {
        return geofenceDao.getGeofencesForUser(userId)
            .map { entities -> entities.map { it.toGeofenceArea() } }
    }

    // --- Procesamiento de MQTT ---
    suspend fun processMqttData(topic: String?, payload: String) {
        if (topic == null) return

        Log.d(TAG, "Processing MQTT - Topic: $topic, Payload: $payload")

        try {
            val json = JSONObject(payload)
            val childId = json.optString("childId", "default")

            when {
                // Procesamiento de ubicación
                topic.contains("location") -> processLocationData(childId, json)

                // Procesamiento de ritmo cardíaco
                topic.contains("heart") -> processHeartRateData(childId, json)

                // Procesamiento de datos del giroscopio
                topic.contains("gyro") -> processGyroData(childId, json)

                else -> Log.w(TAG, "Unhandled MQTT topic: $topic")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing MQTT data", e)
        }
    }

    private suspend fun processLocationData(childId: String, json: JSONObject) {
        val location = ChildLocationEntity(
            childId = childId,
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
            accuracy = json.optDouble("accuracy", 0.0).toFloat()
        )
        saveChildLocation(location)

        // Aquí podrías añadir detección de geocercas
    }

    private suspend fun processHeartRateData(childId: String, json: JSONObject) {
        val heartRate = json.getInt("heartRate")
        // Guardar en base de datos
        // appDatabase.heartRateDao().insert(HeartRateEntity(...))

        // Aquí podrías añadir detección de crisis
    }

    private suspend fun processGyroData(childId: String, json: JSONObject) {
        val x = json.getDouble("x")
        val y = json.getDouble("y")
        val z = json.getDouble("z")
        // Guardar en base de datos
        // appDatabase.gyroDao().insert(GyroEntity(...))
    }

    // --- Notificaciones y alertas ---
    suspend fun recordGeofenceTransition(
        geofenceId: String,
        transitionType: String,
        location: Location
    ) {
        try {
            val event = hashMapOf(
                "geofenceId" to geofenceId,
                "transitionType" to transitionType,
                "timestamp" to System.currentTimeMillis(),
                "latitude" to location.latitude,
                "longitude" to location.longitude
            )

            firestore.collection("geofence_transitions")
                .add(event)
                .await()

            Log.d(TAG, "Geofence transition recorded: $geofenceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error recording geofence transition", e)
        }
    }

    suspend fun sendGeofenceAlert(
        childId: String,
        geofenceName: String,
        location: Location
    ) {
        val message = "Alerta: $childId ha entrado/salido de $geofenceName " +
                "(${location.latitude}, ${location.longitude})"

        NotificationHelper.sendGeofencePushNotification(
            context,
            "Alerta de Geocerca",
            message
        )

        Log.d(TAG, "Geofence alert sent: $message")
    }
}