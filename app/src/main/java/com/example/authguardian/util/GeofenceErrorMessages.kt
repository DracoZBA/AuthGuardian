package com.example.authguardian.util

import android.content.Context
import com.example.authguardian.R
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes // <-- Add this import
import com.google.android.gms.location.GeofenceStatusCodes

/**
 * Returns the error string for a geofencing error code.
 */
object GeofenceErrorMessages {
    fun getErrorString(context: Context, errorCode: Int): String {
        return when (errorCode) {
            GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE ->
                context.getString(R.string.geofence_not_available)
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES ->
                context.getString(R.string.geofence_too_many_geofences)
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS ->
                context.getString(R.string.geofence_too_many_pending_intents)
            CommonStatusCodes.RESOLUTION_REQUIRED -> // <-- Corrected: Use CommonStatusCodes for this error type
                context.getString(R.string.geofence_request_too_fine)
            else ->
                context.getString(R.string.geofence_unknown_error)
        }
    }
}