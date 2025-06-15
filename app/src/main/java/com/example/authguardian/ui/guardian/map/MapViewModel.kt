package com.example.authguardian.ui.guardian.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.authguardian.data.repository.AuthRepository
import com.example.authguardian.data.repository.DataRepository // Your existing DataRepository
import com.example.authguardian.data.GeofenceManager // The NEW GeofenceManager for the guardian's app
import com.example.authguardian.models.ChildLocation
import com.example.authguardian.models.ChildProfile
import com.example.authguardian.models.GeofenceArea // Your GeofenceArea model
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val dataRepository: DataRepository, // Your existing consolidated DataRepository
    private val geofenceManager: GeofenceManager // The NEW GeofenceManager specifically for the guardian's device
) : ViewModel() {

    private val _selectedChildProfile = MutableStateFlow<ChildProfile?>(null)
    val selectedChildProfile: StateFlow<ChildProfile?> = _selectedChildProfile.asStateFlow()

    private val _childrenProfiles = MutableStateFlow<List<ChildProfile>>(emptyList())
    val childrenProfiles: StateFlow<List<ChildProfile>> = _childrenProfiles.asStateFlow()

    private val _childLocation = MutableStateFlow<ChildLocation?>(null)
    val childLocation: StateFlow<ChildLocation?> = _childLocation.asStateFlow()

    private val _childGeofences = MutableStateFlow<List<GeofenceArea>>(emptyList())
    val childGeofences: StateFlow<List<GeofenceArea>> = _childGeofences.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _geofenceOperationSuccess = MutableStateFlow<Boolean?>(null)
    val geofenceOperationSuccess: StateFlow<Boolean?> = _geofenceOperationSuccess.asStateFlow()

    init {
        fetchChildrenProfiles()
    }

    private fun fetchChildrenProfiles() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val currentUser = authRepository.getCurrentFirebaseUser()
                if (currentUser != null) {
                    val guardianId = currentUser.uid
                    dataRepository.getChildrenProfilesForGuardian(guardianId)
                        .collectLatest { profiles ->
                            _childrenProfiles.value = profiles
                            if (_selectedChildProfile.value == null && profiles.isNotEmpty()) {
                                selectChild(profiles.first())
                            }
                        }
                } else {
                    _errorMessage.value = "Guardian user not authenticated."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch children profiles: ${e.message}"
                Log.e("MapViewModel", "Error fetching children profiles: ", e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun selectChild(childProfile: ChildProfile) {
        _selectedChildProfile.value = childProfile
        _childLocation.value = null // Clear previous location
        _childGeofences.value = emptyList() // Clear previous geofences
        Log.d("MapViewModel", "Selected child: ${childProfile.name} (${childProfile.childId})")

        // Observe selected child's location from DataRepository
        viewModelScope.launch {
            val guardianId = authRepository.getCurrentFirebaseUser()?.uid
            if (guardianId != null) {
                dataRepository.getChildCurrentLocation(guardianId, childProfile.childId) // Using your existing method
                    .collectLatest { location ->
                        _childLocation.value = location
                        Log.d("MapViewModel", "New child location received: ${location?.geoPoint?.latitude}, ${location?.geoPoint?.longitude}")
                    }
            }
        }

        // Observe selected child's geofences from DataRepository
        viewModelScope.launch {
            val guardianId = authRepository.getCurrentFirebaseUser()?.uid
            if (guardianId != null) {
                dataRepository.getGeofencesStream(guardianId, childProfile.childId) // Using your existing method
                    .collectLatest { geofences ->
                        _childGeofences.value = geofences
                        Log.d("MapViewModel", "New geofences received: ${geofences.size} geofences")

                        // IMPORTANT: Update actual geofences with the NEW GeofenceManager on the GUARDIAN'S DEVICE.
                        // This ensures the guardian's device is also monitoring these areas for local notifications/display.
                        val currentRegisteredGeofenceIds = geofences.map { it.id } // IDs of geofences to manage

                        // First, try to remove any old geofences that might be registered on the guardian's device
                        // and then add the current list. This handles updates and deletions.
                        geofenceManager.removeGeofences(currentRegisteredGeofenceIds)
                            .addOnCompleteListener { removeTask ->
                                if (removeTask.isSuccessful) {
                                    Log.d("MapViewModel", "Successfully removed previous geofences on guardian's device.")
                                } else {
                                    Log.e("MapViewModel", "Failed to remove old geofences on guardian's device: ${removeTask.exception?.message}", removeTask.exception)
                                    // Even if removal fails, we'll try to add new ones.
                                }

                                // Then, add/re-add the current list of geofences from Firestore to the guardian's device
                                geofenceManager.addGeofences(geofences)
                                    .addOnSuccessListener {
                                        Log.d("MapViewModel", "Geofences updated on guardian's device via GeofenceManager successfully.")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("MapViewModel", "Failed to add/update geofences on guardian's device via GeofenceManager: ${e.message}", e)
                                    }
                            }
                    }
            }
        }
    }

    /**
     * Adds a new geofence. This calls your DataRepository, which saves to Firestore
     * and triggers geofence registration on the child's device (via DataRepository's logic).
     * The guardian's device updates itself via the geofences stream.
     */
    fun addGeofence(name: String, radius: Double, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _loading.value = true
            _geofenceOperationSuccess.value = null // Reset success state
            try {
                val currentUser = authRepository.getCurrentFirebaseUser()
                val selectedChild = _selectedChildProfile.value

                if (currentUser != null && selectedChild != null) {
                    val guardianId = currentUser.uid
                    val newGeofenceArea = GeofenceArea(
                        childId = selectedChild.childId,
                        name = name,
                        latitude = latitude,
                        longitude = longitude,
                        radius = radius,
                        creationTime = Timestamp.now()
                    )
                    // Call your existing DataRepository method to save to Firebase and trigger child's device update
                    dataRepository.saveGeofence(guardianId, selectedChild.childId, newGeofenceArea)
                    _geofenceOperationSuccess.value = true
                    Log.d("MapViewModel", "Geofence added successfully via DataRepository (Firestore and child device).")
                } else {
                    _errorMessage.value = "Please select a child and ensure guardian is authenticated to add geofence."
                    _geofenceOperationSuccess.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add geofence: ${e.message}"
                _geofenceOperationSuccess.value = false
                Log.e("MapViewModel", "Error adding geofence: ", e)
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Removes a geofence. This calls your DataRepository, which deletes from Firestore
     * and triggers geofence de-registration on the child's device.
     * The guardian's device updates itself via the geofences stream.
     */
    fun removeGeofence(geofenceAreaId: String) {
        viewModelScope.launch {
            _loading.value = true
            _geofenceOperationSuccess.value = null // Reset success state
            try {
                val currentUser = authRepository.getCurrentFirebaseUser()
                val selectedChild = _selectedChildProfile.value

                if (currentUser != null && selectedChild != null) {
                    val guardianId = currentUser.uid
                    // Call your existing DataRepository method to delete from Firebase and trigger child's device update
                    dataRepository.removeGeofence(guardianId, selectedChild.childId, geofenceAreaId)
                    _geofenceOperationSuccess.value = true
                    Log.d("MapViewModel", "Geofence removed successfully via DataRepository (Firestore and child device).")
                } else {
                    _errorMessage.value = "Please select a child and ensure guardian is authenticated to remove geofence."
                    _geofenceOperationSuccess.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to remove geofence: ${e.message}"
                _geofenceOperationSuccess.value = false
                Log.e("MapViewModel", "Error removing geofence: ", e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun clearGeofenceOperationSuccess() {
        _geofenceOperationSuccess.value = null
    }
}