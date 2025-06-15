package com.example.authguardian.ui.guardian.graphs // Asegúrate de que el paquete sea el correcto

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.authguardian.data.repository.DataRepository // Importa tu DataRepository
import com.example.authguardian.models.ChildProfile
import com.example.authguardian.models.HeartRateData
import com.example.authguardian.models.GyroscopeData
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class GraphsViewModel @Inject constructor(
    private val dataRepository: DataRepository, // Inyecta tu DataRepository
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _TAG = "GraphsViewModel"

    private val _guardianUid: String?
        get() = firebaseAuth.currentUser?.uid

    // Para perfiles de niños
    private val _childrenProfiles = MutableStateFlow<List<ChildProfile>>(emptyList())
    val childrenProfiles: StateFlow<List<ChildProfile>> = _childrenProfiles.asStateFlow()

    // Para datos de ritmo cardíaco
    private val _heartRateData = MutableStateFlow<List<HeartRateData>>(emptyList())
    val heartRateData: StateFlow<List<HeartRateData>> = _heartRateData.asStateFlow()

    // Para datos de giroscopio
    private val _gyroscopeData = MutableStateFlow<List<GyroscopeData>>(emptyList())
    val gyroscopeData: StateFlow<List<GyroscopeData>> = _gyroscopeData.asStateFlow()

    // Para el estado de carga general de los gráficos
    private val _isLoadingGraphData = MutableStateFlow(false)
    val isLoadingGraphData: StateFlow<Boolean> = _isLoadingGraphData.asStateFlow()

    // Para el estado de carga de los perfiles
    private val _isLoadingProfiles = MutableStateFlow(false)
    val isLoadingProfiles: StateFlow<Boolean> = _isLoadingProfiles.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Para manejar las suscripciones a los flujos de datos y poder cancelarlas
    private var heartRateJob: kotlinx.coroutines.Job? = null
    private var gyroscopeJob: kotlinx.coroutines.Job? = null

    init {
        fetchChildrenProfiles()
    }

    private fun fetchChildrenProfiles() {
        viewModelScope.launch {
            _isLoadingProfiles.value = true
            Log.d(_TAG, "fetchChildrenProfiles CALLED")
            val guardianId = _guardianUid
            if (guardianId == null) {
                _errorMessage.value = "User not authenticated."
                _isLoadingProfiles.value = false
                Log.e(_TAG, "User not authenticated for fetching profiles.")
                return@launch
            }
            try {
                dataRepository.getChildrenProfilesForGuardian(guardianId)
                    .catch { e ->
                        Log.e(_TAG, "Error collecting children profiles: ${e.message}", e)
                        _errorMessage.value = "Error loading profiles: ${e.message}"
                        _childrenProfiles.value = emptyList() // Asegurar lista vacía en error
                    }
                    .collectLatest { profiles ->
                        _childrenProfiles.value = profiles
                        Log.d(_TAG, "Collected ${profiles.size} children profiles.")
                    }
            } catch (e: Exception) { // Por si el Flow mismo lanza una excepción al crearse (raro)
                Log.e(_TAG, "Exception in fetchChildrenProfiles flow creation: ${e.message}", e)
                _errorMessage.value = "Error initiating profile loading: ${e.message}"
            } finally {
                _isLoadingProfiles.value = false
            }
        }
    }

    fun fetchGraphData(childId: String?, startDate: Timestamp?, endDate: Timestamp?) {
        // Cancelar jobs anteriores para evitar múltiples escuchas si se llama repetidamente
        heartRateJob?.cancel()
        gyroscopeJob?.cancel()

        if (childId == null || startDate == null || endDate == null) {
            _errorMessage.value = "Child, start date, or end date not selected."
            Log.w(_TAG, "fetchGraphData aborted: missing parameters. ChildID: $childId, Start: $startDate, End: $endDate")
            _heartRateData.value = emptyList()
            _gyroscopeData.value = emptyList()
            return
        }

        val guardianId = _guardianUid
        if (guardianId == null) {
            _errorMessage.value = "User not authenticated."
            Log.e(_TAG, "User not authenticated for fetching graph data.")
            return
        }

        viewModelScope.launch {
            _isLoadingGraphData.value = true
            _heartRateData.value = emptyList() // Limpiar datos anteriores
            _gyroscopeData.value = emptyList() // Limpiar datos anteriores
            Log.d(_TAG, "fetchGraphData CALLED for guardian: $guardianId, childId: $childId, from: ${startDate.toDate()} to: ${endDate.toDate()}")

            // Obtener HeartRateData
            heartRateJob = launch {
                Log.d(_TAG, "Starting HeartRateData flow collection...")
                dataRepository.getHeartRateDataStream(guardianId, childId, startDate, endDate)
                    .catch { e ->
                        Log.e(_TAG, "Error in HeartRateData stream: ${e.message}", e)
                        _errorMessage.value = "Error loading heart rate data: ${e.message}"
                        // _heartRateData.value podría quedarse con el último valor o limpiarse,
                        // lo ideal es que el stream maneje errores y emita lista vacía si falla
                    }
                    .collectLatest { hrData ->
                        _heartRateData.value = hrData
                        Log.d(_TAG, "Collected ${hrData.size} HeartRateData points.")
                    }
            }

            // Obtener GyroscopeData
            gyroscopeJob = launch {
                Log.d(_TAG, "Starting GyroscopeData flow collection...")
                dataRepository.getGyroscopeDataStream(guardianId, childId, startDate, endDate)
                    .catch { e ->
                        Log.e(_TAG, "Error in GyroscopeData stream: ${e.message}", e)
                        _errorMessage.value = "Error loading gyroscope data: ${e.message}"
                    }
                    .collectLatest { gyroData ->
                        _gyroscopeData.value = gyroData
                        Log.d(_TAG, "Collected ${gyroData.size} GyroscopeData points.")
                    }
            }

            // Esperar a que ambos jobs terminen o gestionarlos individualmente si se prefiere
            // En este caso, como los StateFlows se actualizan independientemente,
            // no es estrictamente necesario un .joinAll() aquí para que la UI reaccione.
            // El estado de carga se desactiva cuando ambos terminan.
            // O, si quieres que se desactive solo cuando ambos terminen, podrías hacer:
            // joinAll(heartRateJob, gyroscopeJob) // Necesitaría que los jobs se asignen bien

            // Una forma simple de manejar el estado de carga es desactivarlo después de un tiempo
            // o cuando ambos flujos hayan emitido al menos una vez (más complejo).
            // Por ahora, lo desactivaremos cuando la función fetchGraphData se complete.
            // Los flujos seguirán actualizando los datos si Firestore cambia.
        }
        // El _isLoadingGraphData se manejará basado en la finalización del bloque launch principal
        // o con más precisión si es necesario (ej. contando callbacks)
        // Por ahora, lo ponemos en false cuando se lanzan las coroutines.
        // Si los streams son de larga duración, quizá quieras un loading por cada stream.
        _isLoadingGraphData.value = false // Se establece en false después de iniciar las colectas.
        // La UI debería mostrar datos a medida que llegan.
        Log.d(_TAG, "fetchGraphData finished launching collection jobs. isLoadingGraphData: ${_isLoadingGraphData.value}")

    }

    // Llama a esto desde el Fragmento en onDestroyView para limpiar las escuchas
    fun onClear() {
        heartRateJob?.cancel()
        gyroscopeJob?.cancel()
        Log.d(_TAG, "onClear called, graph data jobs cancelled.")
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    // Helper para obtener Timestamps por defecto (ej. últimas 24 horas)
    fun getDefaultTimeRange(): Pair<Timestamp, Timestamp> {
        val calendar = Calendar.getInstance()
        val endTime = Timestamp(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -1) // 24 horas atrás
        val startTime = Timestamp(calendar.time)
        return Pair(startTime, endTime)
    }
}