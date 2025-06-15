package com.example.authguardian.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.authguardian.R
import com.example.authguardian.data.repository.AuthRepository
import com.example.authguardian.data.repository.DataRepository
import com.example.authguardian.models.ChildLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Timestamp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.authguardian.ui.child.ChildMainActivity // Asume que esta es la actividad principal para el niño

@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var dataRepository: DataRepository

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var currentChildId: String? = null
    private var currentGuardianId: String? = null

    companion object {
        private const val CHANNEL_ID = "LocationTrackingChannel"
        private const val NOTIFICATION_ID = 123
        private const val LOCATION_INTERVAL_MS = 10000L // 10 segundos
        private const val LOCATION_FASTEST_INTERVAL_MS = 5000L // 5 segundos
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Service onCreate")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        // Obtener el ID del niño y del guardián cuando el servicio se crea
        serviceScope.launch {
            val currentUser = authRepository.getCurrentFirebaseUser()
            if (currentUser != null) {
                val userProfile = authRepository.getUserProfile(currentUser.uid)
                if (userProfile?.role == "child") {
                    currentChildId = currentUser.uid
                    // Si el niño se registra solo, puede que necesites un mecanismo para
                    // que el guardián lo asocie y el niño sepa quién es su guardián.
                    // Para el prototipo, asumiremos que de alguna manera el niño ya sabe su guardianId
                    // o que el guardianId se recupera del perfil del niño en Firestore
                    // (ej. si el ChildProfile tiene un campo guardianId).
                    // Por simplicidad en el prototipo, lo hardcodeamos o lo recuperamos de un ChildProfile
                    // ya pre-asociado por el guardián.
                    // Aquí, una forma simple para el prototipo es buscar el guardián si el niño está solo.
                    // En un sistema real, un ChildProfile creado por un guardián tendría el guardianId.
                    // O el AuthRepository del niño podría almacenar el guardianId al que está asociado.

                    val childProfile = dataRepository.getChildrenProfilesForGuardian(userProfile.userId) // Intenta obtener perfiles de niños para el guardián
                        .firstOrNull()?.firstOrNull { it.childId == currentChildId }

                    currentGuardianId = if (childProfile != null) {
                        // Si el childProfile se encontró bajo un guardián, ese es el guardianId
                        // (Esto es si decides que ChildProfile se guarda bajo guardian/children_profiles/{childId})
                        // Para la estructura actual, necesitarías otro mecanismo para que el niño sepa su guardianId
                        // Por simplicidad para el prototipo, asumimos que el padre tiene un solo hijo, o que el guardianId se pasa de alguna forma.
                        // EJEMPLO: Si el perfil del niño tiene un campo `associatedGuardianId`
                        val childUserProfile = authRepository.getUserProfile(currentChildId!!)
                        childUserProfile?.associatedChildren?.firstOrNull() // Si el niño tiene un campo que apunte a su guardián
                    } else {
                        // Alternativa para el prototipo: Asumir un guardianId conocido o el mismo si el niño es su propio guardián (no aplica aquí).
                        // O obtenerlo de SharedPreferences si el padre lo ha configurado.
                        "your_guardian_uid_here" // Reemplaza con un UID de guardián real si es necesario para pruebas
                    }

                    if (currentGuardianId == null) {
                        Log.e("LocationService", "No guardianId found for child $currentChildId. Location data will not be uploaded.")
                        stopSelf() // Detener el servicio si no se puede determinar el guardián
                        return@launch
                    }
                    Log.d("LocationService", "Child ID: $currentChildId, Guardian ID: $currentGuardianId")
                    startLocationUpdates()
                } else {
                    Log.w("LocationService", "Service started on non-child profile. Stopping service.")
                    stopSelf()
                }
            } else {
                Log.e("LocationService", "No authenticated user found. Stopping service.")
                stopSelf()
            }
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    onNewLocation(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "Service onStartCommand")
        startForeground(NOTIFICATION_ID, createNotification().build()) // <--- AÑADE .build()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Este servicio no se enlaza a actividades
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocationService", "Service onDestroy")
        stopLocationUpdates()
        serviceJob.cancel() // Cancelar todas las corrutinas asociadas al servicio
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): NotificationCompat.Builder {
        val notificationIntent = Intent(this, ChildMainActivity::class.java) // Actividad para abrir al tocar la notificación
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aura Guardian")
            .setContentText("Seguimiento de ubicación activo para la seguridad del niño.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Asegúrate de tener un icono
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Hace que la notificación no pueda ser deslizada
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL_MS) // Intervalo más rápido
            .build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            Log.d("LocationService", "Location updates started.")
        } else {
            Log.e("LocationService", "Location permissions not granted. Cannot start updates.")
            stopSelf() // Detener el servicio si no hay permisos
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("LocationService", "Location updates stopped.")
    }

    private fun onNewLocation(location: Location) {
        Log.d("LocationService", "New location: ${location.latitude}, ${location.longitude}")

        currentChildId?.let { childId ->
            currentGuardianId?.let { guardianId ->
                val childLocation = ChildLocation(
                    childId = childId,
                    timestamp = Timestamp.now(),
                    geoPoint = com.google.firebase.firestore.GeoPoint(location.latitude, location.longitude),
                    accuracy = location.accuracy,
                    speed = location.speed
                )

                serviceScope.launch {
                    try {
                        dataRepository.saveChildLocation(guardianId, childId, childLocation)
                        Log.d("LocationService", "Location uploaded to Firestore for child $childId")
                    } catch (e: Exception) {
                        Log.e("LocationService", "Failed to upload location: ${e.message}", e)
                    }
                }
            } ?: Log.e("LocationService", "Guardian ID is null, cannot upload location.")
        } ?: Log.e("LocationService", "Child ID is null, cannot upload location.")
    }
}