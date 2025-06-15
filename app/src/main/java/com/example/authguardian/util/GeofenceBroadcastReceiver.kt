package com.example.authguardian.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/**
 * Receives geofence transition events, primarily for the GUARDIAN'S DEVICE.
 * This can be used to trigger local notifications or updates on the guardian's side.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    private val TAG = "GeofenceReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent?.hasError() == true) {
            val errorMessage = GeofenceErrorMessages.getErrorString(
                context,
                geofencingEvent.errorCode
            )
            Log.e(TAG, errorMessage)
            return
        }

        // Get the transition type.
        val geofenceTransition = geofencingEvent?.geofenceTransition

        // Test that the reported transition was of interest.
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            // Get the geofences that were triggered. A single event can trigger multiple geofences.
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            val geofenceTransitionDetails = getGeofenceTransitionDetails(
                context,
                geofenceTransition,
                triggeringGeofences
            )

            Log.i(TAG, geofenceTransitionDetails)

            // Here, for the guardian's app, you would typically:
            // 1. Send a local notification to the guardian.
            // 2. (Less common for guardian's app unless it's also a tracking device): Update a status in Firestore.
            // For now, we're just logging the event.

        } else {
            // Log the error.
            Log.e(TAG, "Invalid geofence transition type: $geofenceTransition")
        }
    }

    private fun getGeofenceTransitionDetails(
        context: Context,
        geofenceTransition: Int,
        triggeringGeofences: List<Geofence>?
    ): String {
        val geofenceTransitionString = getTransitionString(geofenceTransition)

        // Get the Ids of all the geofences triggered.
        val triggeringGeofencesIdsList = ArrayList<String>()
        for (geofence in triggeringGeofences!!) {
            triggeringGeofencesIdsList.add(geofence.requestId)
        }
        val triggeringGeofencesIdsString = triggeringGeofencesIdsList.joinToString(", ")

        return "$geofenceTransitionString: $triggeringGeofencesIdsString"
    }

    private fun getTransitionString(transitionType: Int): String {
        return when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "Entered"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "Exited"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "Dwelling in"
            else -> "Unknown transition"
        }
    }
}