package com.example.authguardian.ui.guardian.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.authguardian.data.local.entities.ChildLocationEntity
import com.example.authguardian.data.repository.DataRepository
import com.example.authguardian.models.GeofenceArea
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val dataRepository: DataRepository
) : ViewModel() {

    private val _childCurrentLocation = MutableStateFlow<ChildLocationEntity?>(null)
    val childCurrentLocation: StateFlow<ChildLocationEntity?> = _childCurrentLocation.asStateFlow()

    private val _geofences = MutableStateFlow<List<GeofenceArea>>(emptyList())
    val geofences: StateFlow<List<GeofenceArea>> = _geofences.asStateFlow()

    init {
        // Observar la ubicación del niño desde el repositorio
        // Esto es solo un ejemplo, la ubicación real vendría del servicio
        dataRepository.childCurrentLocation
            .onEach { _childCurrentLocation.value = it }
            .launchIn(viewModelScope)

        // Observar las geocercas del usuario actual
        // Asume que obtienes el ID del usuario actual de alguna manera (ej. FirebaseAuth)
        val currentUserId = "guardian_user_id_here" // ¡Reemplaza con el ID del usuario autenticado!
        dataRepository.getGeofencesForChild(currentUserId)
            .onEach { _geofences.value = it }
            .launchIn(viewModelScope)
    }

    fun addGeofence(geofenceArea: GeofenceArea) {
        viewModelScope.launch {
            try {
                dataRepository.addGeofence(geofenceArea)
                // La UI se actualizará automáticamente via Flow en el Fragmento
            } catch (e: Exception) {
                // Manejar error (mostrar Toast, log, etc.)
                e.printStackTrace()
            }
        }
    }

    fun removeGeofence(geofenceId: String) {
        viewModelScope.launch {
            try {
                dataRepository.removeGeofence(geofenceId)
                // La UI se actualizará automáticamente via Flow en el Fragmento
            } catch (e: Exception) {
                // Manejar error
                e.printStackTrace()
            }
        }
    }
}