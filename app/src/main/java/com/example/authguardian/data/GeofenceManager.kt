package com.example.authguardian.data

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.authguardian.models.GeofenceArea
import com.example.authguardian.util.GeofenceBroadcastReceiver // This BroadcastReceiver will be used here
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    // A PendingIntent for the Broadcast Receiver that will handle geofence transitions on the GUARDIAN'S device
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        // FLAG_UPDATE_CURRENT ensures we get the same PendingIntent back for add/remove operations.
        // FLAG_IMMUTABLE is required for Android 12 (API 31) and above for security.
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * Adds geofences to be monitored by the system on the GUARDIAN'S DEVICE.
     * @param geofencesToMonitor A list of GeofenceArea objects to add.
     * @return A Task that completes when the geofences are added.
     */
    fun addGeofences(geofencesToMonitor: List<GeofenceArea>): Task<Void> {
        if (geofencesToMonitor.isEmpty()) {
            Log.d("GeofenceManager", "No geofences to add to guardian's device.")
            return com.google.android.gms.tasks.Tasks.forResult(null) // Return a completed task if nothing to do
        }

        // --- Permission Checks ---
        // ACCESS_FINE_LOCATION is always required
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val errorMsg = "ACCESS_FINE_LOCATION permission not granted for guardian's device. Cannot add geofences."
            Log.e("GeofenceManager", errorMsg)
            return com.google.android.gms.tasks.Tasks.forException(SecurityException(errorMsg))
        }
        // ACCESS_BACKGROUND_LOCATION is needed for Android 10 (API 29) and above for background geofencing
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val errorMsg = "ACCESS_BACKGROUND_LOCATION permission not granted for guardian's device. Cannot add geofences."
            Log.e("GeofenceManager", errorMsg)
            return com.google.android.gms.tasks.Tasks.forException(SecurityException(errorMsg))
        }
        // --- End Permission Checks ---

        // Convert your GeofenceArea models to Google's Geofence objects
        val geofenceList = geofencesToMonitor.map { geofenceArea ->
            Geofence.Builder()
                .setRequestId(geofenceArea.id) // Use the Firestore document ID as the Geofence API ID
                .setCircularRegion(
                    geofenceArea.latitude,
                    geofenceArea.longitude,
                    geofenceArea.radius.toFloat() // Radius needs to be a Float
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE) // Or a specific duration
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }

        val geofencingRequest = GeofencingRequest.Builder()
            .addGeofences(geofenceList)
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER) // Trigger if already inside
            .build()

        return geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
            .addOnSuccessListener {
                Log.d("GeofenceManager", "Geofences successfully added to guardian's device for monitoring.")
            }
            .addOnFailureListener { e ->
                Log.e("GeofenceManager", "Failed to add geofences to guardian's device: ${e.message}", e)
            }
    }

    /**
     * Removes geofences that are currently being monitored on the GUARDIAN'S DEVICE.
     * @param geofenceRequestIds A list of IDs of the geofences to remove.
     * These should match the requestIds used when adding them (i.e., GeoFenceArea.id).
     * @return A Task that completes when the geofences are removed.
     */
    fun removeGeofences(geofenceRequestIds: List<String>): Task<Void> {
        if (geofenceRequestIds.isEmpty()) {
            Log.d("GeofenceManager", "No geofences to remove from guardian's device.")
            return com.google.android.gms.tasks.Tasks.forResult(null)
        }

        return geofencingClient.removeGeofences(geofenceRequestIds)
            .addOnSuccessListener {
                Log.d("GeofenceManager", "Geofences successfully removed from guardian's device monitoring.")
            }
            .addOnFailureListener { e ->
                Log.e("GeofenceManager", "Failed to remove geofences from guardian's device: ${e.message}", e)
            }
    }
}