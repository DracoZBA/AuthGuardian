package com.example.authguardian.ui.guardian.graphs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.authguardian.BuildConfig // Importa BuildConfig para acceder a la clave API
import com.example.authguardian.data.repository.DataRepository
import com.example.authguardian.models.ChildProfile
import com.example.authguardian.models.HeartRateData
import com.example.authguardian.models.GyroscopeData
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    // NUEVAS PROPIEDADES PARA GEMINI AI
    private val _aiAnalysisResult = MutableStateFlow<String?>(null)
    val aiAnalysisResult: StateFlow<String?> = _aiAnalysisResult.asStateFlow()

    private val _isAnalyzingAi = MutableStateFlow(false)
    val isAnalyzingAi: StateFlow<Boolean> = _isAnalyzingAi.asStateFlow()

    // Inicializa el modelo de Gemini.
    // La clave API se obtiene de BuildConfig.
    private val generativeModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash", // Utiliza el modelo especificado por el usuario
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

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
                    _aiAnalysisResult.value = null // Limpiar análisis de IA si no hay niño seleccionado
                    return@collectLatest
                }

                _isLoadingGraphData.value = true
                Log.d(_TAG, "Fetching graph data for child: $childId")

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
                Log.d(_TAG, "Finished fetching graph data.")
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
            _aiAnalysisResult.value = null // Limpiar el análisis de IA anterior al cambiar de niño
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

    /**
     * Función para analizar los datos históricos de ritmo cardíaco y giroscopio
     * con la IA de Gemini.
     */
    fun analyzeGraphDataWithGemini() {
        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzingAi.value = true
            _aiAnalysisResult.value = null
            _errorMessage.value = null

            val selectedChild = _childrenProfiles.value.firstOrNull { it.childId == _selectedChildId.value }
            val childName = selectedChild?.name ?: "el niño"

            val heartRateDataPoints = _heartRateData.value
            val gyroscopeDataPoints = _gyroscopeData.value

            if (heartRateDataPoints.isEmpty() && gyroscopeDataPoints.isEmpty()) {
                _errorMessage.value = "No hay datos de sensor disponibles para el análisis de IA."
                _isAnalyzingAi.value = false
                return@launch
            }

            try {
                val formattedHeartRateData = formatHeartRateDataForPrompt(heartRateDataPoints)
                val formattedGyroscopeData = formatGyroscopeDataForPrompt(gyroscopeDataPoints)

                val prompt = """
                Eres un analista de datos especializado en niños en el espectro autista. Tu tarea es analizar información biométrica y de movimiento para ofrecer observaciones y recomendaciones claras.

                Has recibido los siguientes datos de los últimos 30 días para el niño llamado "$childName":

                ===== DATOS DE RITMO CARDÍACO (BPM) =====
                ${if (formattedHeartRateData.isNotBlank()) formattedHeartRateData else "No hay datos disponibles."}

                ===== DATOS DE GIROSCOPIO (X, Y, Z) =====
                ${if (formattedGyroscopeData.isNotBlank()) formattedGyroscopeData else "No hay datos disponibles."}

                Por favor, realiza un análisis estructurado basado en lo siguiente:

                1. Patrones Recurrentes y Tendencias:
                - Comportamiento del BPM a lo largo del día o en situaciones específicas.
                - Repetición de movimientos en giroscopio y su intensidad.

                2. Eventos Significativos:
                - Indicadores de estrés, sobrecarga o calma.

                3. Correlaciones:
                - Relación entre ritmo cardíaco y movimiento.

                4. Predicciones:
                - Qué comportamientos son más probables a futuro.

                5. Recomendaciones prácticas:
                - Estrategias útiles para cuidadores o terapeutas.
                - Qué datos adicionales serían útiles en adelante.

                IMPORTANTE:
                - Entrega el resultado con buena redacción, clara, profesional y empática.
                - NO uses negritas, Markdown ni símbolos como "**", "#", "*", etc.
                - Usa solo texto plano con títulos y saltos de línea si es necesario.
            """.trimIndent()

                Log.d(_TAG, "Sending prompt to Gemini for child: $childName")
                val response = generativeModel.generateContent(
                    content { text(prompt) }
                )

                val analysisText = response.text ?: "No se pudo generar el análisis de IA."
                withContext(Dispatchers.Main) {
                    _aiAnalysisResult.value = analysisText
                    Log.d(_TAG, "Gemini AI Analysis Received: ${analysisText.take(200)}...")
                }

            } catch (e: Exception) {
                Log.e(_TAG, "Error al analizar datos con Gemini AI: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error al comunicarse con Gemini AI: ${e.message}"
                    _aiAnalysisResult.value = "Error al generar el análisis. Inténtalo de nuevo más tarde."
                }
            } finally {
                _isAnalyzingAi.value = false
            }
        }
    }

    /**
     * Formatea una lista de HeartRateData en una cadena para el prompt de Gemini.
     * Limita la cantidad de datos para no exceder los límites de tokens.
     */
    private fun formatHeartRateDataForPrompt(dataList: List<HeartRateData>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val stringBuilder = StringBuilder()
        val sampleSize = 100 // Número máximo de puntos de datos a enviar a Gemini
        val step = (dataList.size / sampleSize).coerceAtLeast(1) // Muestrear cada 'step' puntos

        dataList.filterIndexed { index, _ -> index % step == 0 } // Tomar una muestra
            .takeLast(sampleSize) // Asegurarse de que no exceda el tamaño de muestra
            .forEach { data ->
                data.timestamp?.let { ts ->
                    val time = dateFormat.format(ts.toDate())
                    stringBuilder.append("Tiempo: $time, BPM: ${data.heartRateBpm}\n")
                }
            }
        return stringBuilder.toString()
    }

    /**
     * Formatea una lista de GyroscopeData en una cadena para el prompt de Gemini.
     * Limita la cantidad de datos para no exceder los límites de tokens.
     */
    private fun formatGyroscopeDataForPrompt(dataList: List<GyroscopeData>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val stringBuilder = StringBuilder()
        val sampleSize = 100 // Número máximo de puntos de datos a enviar a Gemini
        val step = (dataList.size / sampleSize).coerceAtLeast(1)

        dataList.filterIndexed { index, _ -> index % step == 0 }
            .takeLast(sampleSize)
            .forEach { data ->
                data.timestamp?.let { ts ->
                    val time = dateFormat.format(ts.toDate())
                    val gyro = data.value
                    stringBuilder.append("Tiempo: $time, X: ${String.format("%.2f", gyro?.x)}, Y: ${String.format("%.2f", gyro?.y)}, Z: ${String.format("%.2f", gyro?.z)}\n")
                }
            }
        return stringBuilder.toString()
    }
}