package com.example.authguardian.data.remote

import android.util.Log
import com.example.authguardian.models.GeofenceArea
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    private val TAG = "GeofenceRemoteDS"
    private val GUARDIANS_COLLECTION = "guardians"
    private val CHILDREN_COLLECTION = "children"
    private val GEOFENCE_AREAS_COLLECTION = "geofence_areas" // Renamed for clarity

    /**
     * Streams all GeofenceArea objects for a specific child under a guardian.
     */
    fun getGeofencesForChildStream(guardianId: String, childId: String): Flow<List<GeofenceArea>> = callbackFlow {
        val path = "$GUARDIANS_COLLECTION/$guardianId/$CHILDREN_COLLECTION/$childId/$GEOFENCE_AREAS_COLLECTION"
        val subscription = firestore.collection(path)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed for geofences.", e)
                    close(e) // Close the flow with the error
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val geofences = snapshot.documents.mapNotNull { document ->
                        try {
                            // Ensure the ID from the document is explicitly set in the model
                            document.toObject(GeofenceArea::class.java)?.copy(id = document.id)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document to GeofenceArea: ${document.id}", e)
                            null // Skip malformed documents
                        }
                    }
                    trySend(geofences).isSuccess
                } else {
                    trySend(emptyList()).isSuccess
                }
            }

        awaitClose { subscription.remove() } // Cleanup listener on flow cancellation
    }

    /**
     * Adds a new GeofenceArea to Firestore for a specific child.
     * If geofenceArea.id is empty, Firestore will auto-generate it.
     */
    suspend fun addGeofence(guardianId: String, childId: String, geofenceArea: GeofenceArea) {
        val path = "$GUARDIANS_COLLECTION/$guardianId/$CHILDREN_COLLECTION/$childId/$GEOFENCE_AREAS_COLLECTION"
        try {
            // If ID is provided, set the document. Otherwise, let Firestore auto-generate.
            if (geofenceArea.id.isNotBlank()) {
                firestore.collection(path).document(geofenceArea.id).set(geofenceArea).await()
            } else {
                firestore.collection(path).add(geofenceArea).await()
            }
            Log.d(TAG, "GeofenceArea added/updated in Firestore for child $childId.")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding/updating GeofenceArea to Firestore: ${e.message}", e)
            throw e
        }
    }

    /**
     * Removes a GeofenceArea from Firestore.
     */
    suspend fun removeGeofence(guardianId: String, childId: String, geofenceAreaId: String) {
        val path = "$GUARDIANS_COLLECTION/$guardianId/$CHILDREN_COLLECTION/$childId/$GEOFENCE_AREAS_COLLECTION/$geofenceAreaId"
        try {
            firestore.document(path).delete().await()
            Log.d(TAG, "GeofenceArea $geofenceAreaId removed from Firestore.")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing GeofenceArea $geofenceAreaId from Firestore: ${e.message}", e)
            throw e
        }
    }

    // You will need to add other remote data source methods here as your app grows,
    // for example, fetching ChildLocation, HeartRateData, etc., if they are not already in another remote data source.
}