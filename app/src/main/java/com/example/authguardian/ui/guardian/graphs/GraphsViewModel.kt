package com.example.authguardian.ui.guardian.graphs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.authguardian.data.repository.DataRepository
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
    private val dataRepository: DataRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _TAG = "GraphsViewModel"

    private val _guardianUid: String?
        get() = firebaseAuth.currentUser?.uid

    private val _selectedChildId = MutableStateFlow<String?>(null)
    val selectedChildId: StateFlow<String?> = _selectedChildId.asStateFlow()

    private val _childrenProfiles = MutableStateFlow<List<ChildProfile>>(emptyList())
    val childrenProfiles: StateFlow<List<ChildProfile>> = _childrenProfiles.asStateFlow()

    private val _timeRange = MutableStateFlow(getThirtyDayTimeRange())
    val timeRange: StateFlow<Pair<Timestamp, Timestamp>> = _timeRange.asStateFlow()

    private val _heartRateData = MutableStateFlow<List<HeartRateData>>(emptyList())
    val heartRateData: StateFlow<List<HeartRateData>> = _heartRateData.asStateFlow()

    private val _gyroscopeData = MutableStateFlow<List<GyroscopeData>>(emptyList())
    val gyroscopeData: StateFlow<List<GyroscopeData>> = _gyroscopeData.asStateFlow()

    private val _isLoadingGraphData = MutableStateFlow(false)
    val isLoadingGraphData: StateFlow<Boolean> = _isLoadingGraphData.asStateFlow()

    private val _isLoadingProfiles = MutableStateFlow(false)
    val isLoadingProfiles: StateFlow<Boolean> = _isLoadingProfiles.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        fetchChildrenProfiles()
        observeGraphData()
    }

    private fun observeGraphData() {
        viewModelScope.launch {
            combine(_selectedChildId, _timeRange, _childrenProfiles) { childId, timeRange, profiles ->
                Triple(childId, timeRange, profiles)
            }.collectLatest { (childId, timeRange, profiles) ->
                val currentGuardianId = _guardianUid

                if (currentGuardianId == null) {
                    _errorMessage.value = "User not authenticated."
                    return@collectLatest
                }

                if (childId == null || profiles.none { it.childId == childId }) {
                    Log.d(_TAG, "Esperando selección de niño o perfiles.")
                    return@collectLatest
                }

                _isLoadingGraphData.value = true

                launch {
                    dataRepository.getHeartRateDataStream(currentGuardianId, childId, timeRange.first, timeRange.second)
                        .catch { e ->
                            Log.e(_TAG, "Error HeartRateData: ${e.message}", e)
                            _errorMessage.value = "Error loading heart rate data: ${e.message}"
                            _heartRateData.value = emptyList()
                        }
                        .collect {
                            _heartRateData.value = it
                            Log.d(_TAG, "Collected ${it.size} HeartRateData points.")
                        }
                }

                launch {
                    dataRepository.getGyroscopeDataStream(currentGuardianId, childId, timeRange.first, timeRange.second)
                        .catch { e ->
                            Log.e(_TAG, "Error GyroscopeData: ${e.message}", e)
                            _errorMessage.value = "Error loading gyroscope data: ${e.message}"
                            _gyroscopeData.value = emptyList()
                        }
                        .collect {
                            _gyroscopeData.value = it
                            Log.d(_TAG, "Collected ${it.size} GyroscopeData points.")
                        }
                }

                _isLoadingGraphData.value = false
            }
        }
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
                        _childrenProfiles.value = emptyList()
                    }
                    .collectLatest { profiles ->
                        _childrenProfiles.value = profiles
                        Log.d(_TAG, "Collected ${profiles.size} children profiles.")
                        if (_selectedChildId.value == null || !profiles.any { it.childId == _selectedChildId.value }) {
                            _selectedChildId.value = profiles.firstOrNull()?.childId
                            Log.d(_TAG, "Defaulting selected child to: ${_selectedChildId.value}")
                        }
                    }
            } catch (e: Exception) {
                Log.e(_TAG, "Exception in fetchChildrenProfiles: ${e.message}", e)
                _errorMessage.value = "Error initiating profile loading: ${e.message}"
            } finally {
                _isLoadingProfiles.value = false
            }
        }
    }

    fun selectChild(childId: String?) {
        if (_selectedChildId.value != childId) {
            _selectedChildId.value = childId
            Log.d(_TAG, "Child selected via UI: $childId")
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun getThirtyDayTimeRange(): Pair<Timestamp, Timestamp> {
        val calendar = Calendar.getInstance()
        val endTime = Timestamp(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val startTime = Timestamp(calendar.time)
        Log.d(_TAG, "Calculated 30-day range: Start=${startTime.toDate()}, End=${endTime.toDate()}")
        return Pair(startTime, endTime)
    }
}
