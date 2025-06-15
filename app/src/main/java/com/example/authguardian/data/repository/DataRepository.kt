package com.example.authguardian.data.repository

import android.content.Context
import android.util.Log
import com.example.authguardian.data.local.AppDatabase
import com.example.authguardian.data.local.dao.ChildLocationDao
import com.example.authguardian.data.local.dao.GeofenceDao
import com.example.authguardian.data.local.dao.GyroscopeDao
import com.example.authguardian.data.local.dao.HeartRateDao
import com.example.authguardian.data.local.dao.MeltdownDao
import com.example.authguardian.data.remote.FirebaseDataSource // Importa tu nuevo DataSource
import com.example.authguardian.models.*
import kotlinx.coroutines.tasks.await
import com.example.authguardian.util.GeofenceHelper
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataRepository @Inject constructor(
    private val context: Context,
    private val firebaseDataSource: FirebaseDataSource, // Inyecta el DataSource
    private val appDatabase: AppDatabase, // Room Database (para cacheo o datos locales)
    private val geofenceHelper: GeofenceHelper,
    // DAOs de Room (manténlos para posible cacheo local o funcionalidad offline)
    private val heartRateDao: HeartRateDao,
    private val gyroscopeDao: GyroscopeDao,
    private val meltdownDao: MeltdownDao,
    private val childLocationDao: ChildLocationDao,
    private val geofenceDao: GeofenceDao
) {
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    // --- Funciones para la APLICACIÓN DEL NIÑO (subir datos a Firestore) ---

    suspend fun saveHeartRateData(guardianId: String, childId: String, data: HeartRateData) {
        firebaseDataSource.saveHeartRateData(guardianId, childId, data)
    }

    suspend fun saveGyroscopeData(guardianId: String, childId: String, data: GyroscopeData) {
        firebaseDataSource.saveGyroscopeData(guardianId, childId, data)
    }

    suspend fun saveMeltdownEvent(guardianId: String, childId: String, event: MeltdownEvent) {
        firebaseDataSource.saveMeltdownEvent(guardianId, childId, event)
    }

    suspend fun saveChildLocation(guardianId: String, childId: String, location: ChildLocation) {
        firebaseDataSource.saveChildLocation(guardianId, childId, location)
    }

    // Aquí, la lógica de añadir/eliminar a la API de Play Services sigue siendo responsabilidad del DataRepository,
    // ya que no es una interacción directa con Firestore, sino con el sistema Android.
    suspend fun saveGeofence(guardianId: String, childId: String, geofence: GeofenceArea) {
        firebaseDataSource.saveGeofence(guardianId, childId, geofence)
        addGeofenceToSystem(geofence) // Esta función se ejecutaría en el dispositivo del NIÑO
    }

    @Throws(SecurityException::class, Exception::class)
    private suspend fun addGeofenceToSystem(geofence: GeofenceArea) {
        val gf = geofenceHelper.getGeofence(
            geofence.id,
            // Usa geofence.latitude y geofence.longitude directamente
            com.google.android.gms.maps.model.LatLng(geofence.latitude, geofence.longitude), // <--- CAMBIO AQUÍ
            geofence.radius.toFloat(), // Asegúrate de que el radio sea Float si getGeofence lo espera
            Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
        )
        val geofencingRequest = geofenceHelper.getGeofencingRequest(gf)
        val pendingIntent = geofenceHelper.getPendingIntent()

        try {
            geofencingClient.addGeofences(geofencingRequest, pendingIntent).await()
            Log.d("DataRepository", "Geofence '${geofence.name}' added to Play Services system.")
        } catch (e: SecurityException) {
            Log.e("DataRepository", "Location permissions not granted for geofencing: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e("DataRepository", "Error adding geofence to system: ${e.message}", e)
            throw e
        }
    }

    suspend fun removeGeofence(guardianId: String, childId: String, geofenceId: String) {
        firebaseDataSource.deleteGeofence(guardianId, childId, geofenceId)
        removeGeofenceFromSystem(geofenceId)
    }

    private suspend fun removeGeofenceFromSystem(geofenceId: String) {
        try {
            geofencingClient.removeGeofences(listOf(geofenceId)).await()
            Log.d("DataRepository", "Geofence '$geofenceId' removed from Play Services system.")
        } catch (e: Exception) {
            Log.e("DataRepository", "Error removing geofence from system: ${e.message}", e)
            throw e
        }
    }

    // --- Funciones para la APLICACIÓN DEL PADRE (leer datos de Firestore) ---

    fun getChildrenProfilesForGuardian(guardianId: String): Flow<List<ChildProfile>> {
        return firebaseDataSource.getChildrenProfilesStream(guardianId)
    }

    fun getChildCurrentLocation(guardianId: String, childId: String): Flow<ChildLocation?> {
        return firebaseDataSource.getChildLocationStream(guardianId, childId)
    }

    fun getHeartRateDataStream(guardianId: String, childId: String, startTime: Timestamp, endTime: Timestamp): Flow<List<HeartRateData>> {
        return firebaseDataSource.getHeartRateDataStream(guardianId, childId, startTime, endTime)
    }

    fun getGyroscopeDataStream(guardianId: String, childId: String, startTime: Timestamp, endTime: Timestamp): Flow<List<GyroscopeData>> {
        return firebaseDataSource.getGyroscopeDataStream(guardianId, childId, startTime, endTime)
    }

    fun getMeltdownEventsStream(guardianId: String, childId: String, startTime: Timestamp, endTime: Timestamp): Flow<List<MeltdownEvent>> {
        return firebaseDataSource.getMeltdownEventsStream(guardianId, childId, startTime, endTime)
    }

    fun getGeofencesStream(guardianId: String, childId: String): Flow<List<GeofenceArea>> {
        return firebaseDataSource.getGeofencesStream(guardianId, childId)
    }

    fun getUserGraphsStream(userId: String): Flow<List<UserGraph>> {
        return firebaseDataSource.getUserGraphsStream(userId)
    }
}