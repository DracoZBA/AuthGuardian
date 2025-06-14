package com.example.authguardian.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location // <<<--- AÑADE ESTE IMPORT SI FALTA
import android.util.Log
import androidx.compose.ui.geometry.isEmpty
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.example.authguardian.data.repository.DataRepository
// import com.example.authguardian.service.LocationTrackingService // No se usa directamente aquí, se puede quitar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var dataRepository: DataRepository

    private val receiverJob = SupervisorJob()
    private val receiverScope = CoroutineScope(Dispatchers.IO + receiverJob)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == "com.google.android.gms.location.ACTION_GEOFENCE_TRANSITION") {
            val geofencingEvent = GeofencingEvent.fromIntent(intent)
            if (geofencingEvent?.hasError() == true) {
                val errorMessage = "Geofence error: ${geofencingEvent.errorCode}"
                Log.e("GeofenceReceiver", errorMessage)
                NotificationHelper.showNotification(context, "Geofence Error", errorMessage)
                return
            }

            val geofenceTransition = geofencingEvent?.geofenceTransition
            val geofences = geofencingEvent?.triggeringGeofences
            // Es buena práctica también obtener la ubicación que disparó el evento aquí arriba
            val triggeringLocationFromEvent: Location? = geofencingEvent?.triggeringLocation


            if (geofences != null && geofenceTransition != null && triggeringLocationFromEvent != null) { // Asegúrate que la ubicación no sea nula
                // Procesa solo si hay al menos una geocerca
                if (geofences.isEmpty()) {
                    Log.w("GeofenceReceiver", "Triggering geofences list is empty.")
                    return
                }
                val triggeredGeofenceId = geofences[0].requestId
                val transitionType = when (geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> "entered"
                    Geofence.GEOFENCE_TRANSITION_EXIT -> "exited"
                    Geofence.GEOFENCE_TRANSITION_DWELL -> "dwelled"
                    else -> "unknown"
                }
                val transitionMessage = "Child $transitionType geofence: $triggeredGeofenceId"
                Log.d("GeofenceReceiver", transitionMessage)

                receiverScope.launch {
                    dataRepository.recordGeofenceTransition(
                        triggeredGeofenceId,
                        transitionType,
                        triggeringLocationFromEvent // <<<--- CORRECCIÓN AQUÍ: Pasa el objeto Location
                    )

                    // Envía una notificación push al padre
                    NotificationHelper.sendGeofencePushNotification(
                        context,
                        "Alerta de Geocerca: ${triggeredGeofenceId}",
                        "El niño ha $transitionType la zona segura."
                    )
                }
            } else {
                if (triggeringLocationFromEvent == null) {
                    Log.w("GeofenceReceiver", "Triggering location was null. Cannot process geofence event.")
                }
                if (geofences == null || geofences.isEmpty()) {
                    Log.w("GeofenceReceiver", "No geofences triggered or geofences list is null/empty.")
                }
            }
        }
    }
}