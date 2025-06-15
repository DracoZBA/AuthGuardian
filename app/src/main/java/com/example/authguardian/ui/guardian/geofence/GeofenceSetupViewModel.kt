package com.example.authguardian.ui.guardian.geofence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.authguardian.data.repository.AuthRepository
import com.example.authguardian.data.repository.DataRepository
import com.example.authguardian.models.ChildProfile
import com.example.authguardian.models.GeofenceArea
import com.google.firebase.firestore.GeoPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import android.util.Log

@HiltViewModel
class GeofenceSetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val dataRepository: DataRepository
) : ViewModel() {

    private val _currentGuardianId = MutableStateFlow<String?>(null)
    private val _activeChildProfile = MutableStateFlow<ChildProfile?>(null)
    val activeChildProfile: StateFlow<ChildProfile?> = _activeChildProfile.asStateFlow()

    private val _addGeofenceStatus = MutableStateFlow<GeofenceOperationStatus>(GeofenceOperationStatus.Idle)
    val addGeofenceStatus: StateFlow<GeofenceOperationStatus> = _addGeofenceStatus.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.getAuthState().collectLatest { user ->
                if (user?.role == "guardian") {
                    _currentGuardianId.value = user.userId
                    val childId = user.associatedChildren?.firstOrNull() // O carga el childId de otra forma
                    if (childId != null) {
                        authRepository.getChildProfile(user.userId, childId)
                            ?.let { _activeChildProfile.value = it }
                    } else {
                        _activeChildProfile.value = null
                    }
                } else {
                    _currentGuardianId.value = null
                    _activeChildProfile.value = null
                }
            }
        }
    }

    fun addGeofence(name: String, latitude: Double, longitude: Double, radius: Float) {
        viewModelScope.launch {
            _addGeofenceStatus.value = GeofenceOperationStatus.Loading
            val guardianId = _currentGuardianId.value
            val childProfile = _activeChildProfile.value

            if (guardianId == null || childProfile == null) {
                _addGeofenceStatus.value = GeofenceOperationStatus.Error("Guardian or child not selected.")
                return@launch
            }

            val newGeofence = GeofenceArea(
                id = UUID.randomUUID().toString(),
                childId = childProfile.childId,
                name = name,
                latitude = latitude,      // <--- Pass latitude directly
                longitude = longitude,    // <--- Pass longitude directly
                radius = radius.toDouble(), // <--- Convert Float to Double to match model
                // creationTime will use its default value (Timestamp.now())
                // isEnabled = true // Remove this if GeofenceArea doesn't have it, or add it to the model
            )

            try {
                dataRepository.saveGeofence(guardianId, childProfile.childId, newGeofence)
                _addGeofenceStatus.value = GeofenceOperationStatus.Success("Geocerca '${name}' añadida con éxito.")
            } catch (e: Exception) {
                _addGeofenceStatus.value = GeofenceOperationStatus.Error(e.message ?: "Error al añadir geocerca.")
            }
        }
    }

    fun removeGeofence(geofenceId: String) {
        viewModelScope.launch {
            val guardianId = _currentGuardianId.value
            val childProfile = _activeChildProfile.value

            if (guardianId == null || childProfile == null) {
                Log.e("GeofenceSetupViewModel", "Cannot remove geofence: Guardian or child not selected.")
                return@launch
            }

            try {
                dataRepository.removeGeofence(guardianId, childProfile.childId, geofenceId)
                // Estado de éxito o error se puede añadir si es necesario
            } catch (e: Exception) {
                Log.e("GeofenceSetupViewModel", "Error removing geofence: ${e.message}")
            }
        }
    }

    fun resetStatus() {
        _addGeofenceStatus.value = GeofenceOperationStatus.Idle
    }

    sealed class GeofenceOperationStatus {
        object Idle : GeofenceOperationStatus()
        object Loading : GeofenceOperationStatus()
        data class Success(val message: String) : GeofenceOperationStatus()
        data class Error(val message: String) : GeofenceOperationStatus()
    }
}