package com.example.authguardian.service // Or your actual package name

import android.annotation.SuppressLint
import android.app.Notification // Asegúrate de tener este import
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
// import android.content.Context // No se usa directamente, se puede quitar si no hay otros usos
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.example.authguardian.R
import com.example.authguardian.data.local.entities.ChildLocationEntity
import com.example.authguardian.data.repository.DataRepository
import com.example.authguardian.ui.guardian.GuardianMainActivity
import com.example.authguardian.util.GeofenceHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var dataRepository: DataRepository

    @Inject
    lateinit var geofenceHelper: GeofenceHelper

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object { // Es buena práctica tener constantes en un companion object
        private const val NOTIFICATION_CHANNEL_ID = "LocationTrackingServiceChannel"
        private const val NOTIFICATION_ID = 102
        private const val TAG = "LocationService" // Para logs
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification()) // Esta línea ahora recibe un Notification
        setupLocationTracking()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Location tracking service started")
        // Aquí podrías manejar diferentes acciones basadas en el 'intent' si es necesario
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // No binding provided
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceJob.cancel() // Cancela todas las coroutines lanzadas en este scope
        Log.d(TAG, "Location tracking service destroyed")
    }

    @SuppressLint("MissingPermission") // Asegúrate que los permisos se solicitan y otorgan antes de iniciar el servicio
    private fun setupLocationTracking() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L) // Actualización cada 5 segundos
            // .setWaitForActivity(true) // Considera si realmente lo necesitas. Puede retrasar las actualizaciones si no hay actividad.
            .setMinUpdateIntervalMillis(3000L) // Mínimo 3 segundos entre actualizaciones
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d(TAG, "Location: ${location.latitude}, ${location.longitude}, Accuracy: ${location.accuracy}")
                    processNewLocation(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            Log.d(TAG, "Location updates requested.")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted when requesting updates.", e)
            // Considerar detener el servicio o notificar al usuario si esto ocurre
            stopSelf() // Detiene el servicio si no puede operar
        }
    }

    private fun processNewLocation(location: Location) {
        serviceScope.launch {
            // TODO: Obtener el childId dinámicamente. No hardcodear.
            // Esto podría venir de SharedPreferences, un Intent al iniciar el servicio,
            // o una base de datos si el servicio es para un niño específico logueado en el dispositivo.
            val childId = getActiveChildId() // Implementa esta función
            if (childId == null) {
                Log.w(TAG, "Child ID not found. Cannot save location or check geofence.")
                return@launch
            }
            dataRepository.saveChildLocation(
                ChildLocationEntity(
                    // userId = childId, // This was the field name in my example, your entity has 'childId'
                    childId = childId ?: "unknown_child", // Ensure childId from getActiveChildId() is used
                    timestamp = System.currentTimeMillis(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy
                )
            )
            Log.d(TAG, "Child location saved for $childId.")
            checkGeofenceStatus(location, childId)
        }
    }

    // TODO: Implementar una forma de obtener el ID del niño activo
    private suspend fun getActiveChildId(): String? {
        // Ejemplo: Leerlo de SharedPreferences o de alguna fuente de datos
        // val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        // return sharedPreferences.getString("current_child_id", null)
        Log.w(TAG, "getActiveChildId() is not implemented. Using placeholder.")
        return "child_id_placeholder" // Placeholder - ¡DEBES CAMBIAR ESTO!
    }

    private fun checkGeofenceStatus(currentLocation: Location, childId: String) {
        serviceScope.launch {
            try {
                dataRepository.getGeofencesForChild(childId)
                    .collect { geofenceAreaList -> // Recolecta el Flow
                        Log.d(TAG, "Checking ${geofenceAreaList.size} geofences for child $childId.")
                        if (geofenceAreaList.isEmpty()) {
                            Log.d(TAG, "No geofences found for child $childId.")
                            return@collect // Sal de esta iteración de collect si no hay geofences
                        }
                        for (geofenceArea in geofenceAreaList) {
                            val isInside = geofenceHelper.isPointInCircle(
                                currentLocation.latitude, currentLocation.longitude,
                                geofenceArea.latitude, geofenceArea.longitude,
                                geofenceArea.radius
                            )
                            // ... tu lógica de comprobación y alerta ...
                            if (!isInside) {
                                Log.i(TAG, "Child $childId exited geofence: ${geofenceArea.name}")
                                dataRepository.sendGeofenceAlert(childId, geofenceArea.name, currentLocation)
                            } else {
                                Log.d(TAG, "Child $childId is inside geofence: ${geofenceArea.name}")
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in geofence status flow for child $childId", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Aura Guardian Location Service"
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_LOW // Usar LOW o MIN para foreground services que no necesitan atención inmediata
            ).apply {
                description = "Channel for Aura Guardian location tracking service."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            Log.d(TAG, "Notification channel created.")
        }
    }

    private fun createNotification(): Notification { // Cambiado el tipo de retorno a Notification
        val notificationIntent = Intent(this, GuardianMainActivity::class.java).apply {
            // Flags para manejar cómo se comporta la actividad al ser lanzada desde la notificación
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // O considera FLAG_ACTIVITY_SINGLE_TOP
        }

        // El requestCode para PendingIntent debe ser único si tienes varios o quieres un comportamiento específico.
        // FLAG_UPDATE_CURRENT asegura que si el intent cambia, el PendingIntent se actualiza.
        // FLAG_IMMUTABLE es recomendado por seguridad para intents que no deben ser modificados.
        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, // requestCode
            notificationIntent,
            pendingIntentFlag
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name)) // Usar recursos de strings
            .setContentText(getString(R.string.notification_tracking_location)) // Usar recursos de strings
            .setSmallIcon(R.drawable.ic_notification_icon) // TODO: Reemplaza con un ícono de notificación adecuado (monocromático)
            .setOngoing(true) // Indica que la notificación es para un proceso en curso (foreground service)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Consistente con NotificationManager.IMPORTANCE_LOW
            .build() // <--- ¡LA MODIFICACIÓN CLAVE!
    }
}