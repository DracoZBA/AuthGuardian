package com.example.authguardian.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.maps.model.LatLng
import com.example.authguardian.models.GeofenceArea
import com.example.authguardian.service.LocationTrackingService // Si el servicio de tracking procesa las transiciones

class GeofenceHelper(private val context: Context) {

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        // ACTION_GEOFENCE_TRANSITION es la acción que espera nuestro BroadcastReceiver
        intent.action = "com.google.android.gms.location.ACTION_GEOFENCE_TRANSITION"
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // FLAG_IMMUTABLE es requerido en Android 12+
        )
    }

    fun getGeofence(id: String, latLng: LatLng, radius: Float, transitionType: Int): Geofence {
        return Geofence.Builder()
            .setRequestId(id) // ID de la geocerca
            .setCircularRegion(latLng.latitude, latLng.longitude, radius) // Centro y radio
            .setExpirationDuration(Geofence.NEVER_EXPIRE) // Duración de la geocerca
            .setTransitionTypes(transitionType) // Tipos de transición (ENTRADA, SALIDA, AMBOS)
            .build()
    }

    fun getGeofencingRequest(geofence: Geofence): GeofencingRequest {
        return GeofencingRequest.Builder()
            .addGeofence(geofence)
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER) // O INITIAL_TRIGGER_DWELL
            .build()
    }

    fun getPendingIntent(): PendingIntent {
        return geofencePendingIntent
    }

    fun isPointInCircle(pointLat: Double, pointLng: Double, circleLat: Double, circleLng: Double, circleRadiusMeters: Float): Boolean {
        val result = FloatArray(1)
        android.location.Location.distanceBetween(pointLat, pointLng, circleLat, circleLng, result)
        return result[0] <= circleRadiusMeters
    }
}