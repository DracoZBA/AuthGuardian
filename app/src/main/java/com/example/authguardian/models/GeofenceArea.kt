package com.example.authguardian.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint // Ensure you have this import if using GeoPoint

data class GeofenceArea(
    @DocumentId val id: String = "", // Crucial: Document ID in Firestore for this geofence area
    val childId: String = "", // Crucial: The ID of the child this geofence applies to
    val name: String = "", // A user-friendly name (e.g., "Home", "School")
    val latitude: Double = 0.0, // Or use GeoPoint: val geoPoint: GeoPoint = GeoPoint(0.0, 0.0),
    val longitude: Double = 0.0,
    val radius: Double = 0.0, // Radius in meters
    val creationTime: Timestamp = Timestamp.now()
)