package com.example.authguardian.ui.guardian.graphs // Asegúrate de que el paquete sea el correcto

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.compose.ui.semantics.text
import androidx.compose.ui.tooling.data.position
import androidx.core.text.color
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.authguardian.R
import com.example.authguardian.databinding.FragmentGraphsBinding
import com.example.authguardian.models.ChildProfile
import com.example.authguardian.models.GyroscopeData
import com.example.authguardian.models.HeartRateData
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.Timestamp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class GraphsFragment : Fragment() {

    private var _binding: FragmentGraphsBinding? = null
    private val binding get() = _binding!!

    private val graphsViewModel: GraphsViewModel by viewModels()

    private var selectedChild: ChildProfile? = null
    private var selectedStartDate: Timestamp? = null
    private var selectedEndDate: Timestamp? = null

    private lateinit var childrenSpinnerAdapter: ArrayAdapter<String>
    private val childrenListForSpinner = mutableListOf<ChildProfile>()

    private val _TAG = "GraphsFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGraphsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(_TAG, "onViewCreated")

        setupUI()
        setupListeners()
        observeViewModel()

        // Set default date range
        val (defaultStart, defaultEnd) = graphsViewModel.getDefaultTimeRange()
        updateStartDate(defaultStart)
        updateEndDate(defaultEnd)
    }

    private fun setupUI() {
        Log.d(_TAG, "setupUI called")
        childrenSpinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, mutableListOf<String>())
        childrenSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerChild.adapter = childrenSpinnerAdapter

        // Initialize charts (basic setup)
        setupChart(binding.heartRateChart, "Heart Rate")
        setupChart(binding.gyroscopeChart, "Gyroscope Data")
    }

    private fun setupChart(chart: com.github.mikephil.charting.charts.LineChart, description: String) {
        chart.description.text = description
        chart.setNoDataText("No data available yet.")
        chart.setNoDataTextColor(Color.GRAY)
        chart.xAxis.valueFormatter = TimestampAxisValueFormatter()
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f // Evita duplicados si los timestamps son muy cercanos
        chart.xAxis.setLabelRotationAngle(45f) // Rota las etiquetas para mejor legibilidad
        chart.invalidate()
    }


    private fun setupListeners() {
        Log.d(_TAG, "setupListeners called")
        binding.spinnerChild.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position >= 0 && position < childrenListForSpinner.size) {
                    selectedChild = childrenListForSpinner[position]
                    Log.d(_TAG, "Child selected: ${selectedChild?.name}")
                    // No necesitamos buscar datos aquí automáticamente, el botón lo hará.
                } else {
                    selectedChild = null
                    Log.w(_TAG, "Invalid child selection or no child selected.")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedChild = null
            }
        }

        binding.btnStartDate.setOnClickListener {
            showDatePickerDialog(true)
        }

        binding.btnEndDate.setOnClickListener {
            showDatePickerDialog(false)
        }

        binding.btnFetchData.setOnClickListener {
            Log.d(_TAG, "btnFetchData CLICKED!")
            if (selectedChild == null) {
                Toast.makeText(requireContext(), "Please select a child.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedStartDate == null || selectedEndDate == null) {
                Toast.makeText(requireContext(), "Please select start and end dates.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedStartDate!! > selectedEndDate!!) {
                Toast.makeText(requireContext(), "Start date cannot be after end date.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d(_TAG, "Fetching data for ${selectedChild?.childId} from $selectedStartDate to $selectedEndDate")
            graphsViewModel.fetchGraphData(selectedChild?.childId, selectedStartDate, selectedEndDate)
        }
    }

    private fun observeViewModel() {
        Log.d(_TAG, "observeViewModel called")
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    graphsViewModel.childrenProfiles.collectLatest { profiles ->
                        Log.d(_TAG, "Observed childrenProfiles: ${profiles.size} profiles")
                        childrenListForSpinner.clear()
                        childrenListForSpinner.addAll(profiles)
                        val profileNames = profiles.mapNotNull { it.name }
                        childrenSpinnerAdapter.clear()
                        if (profileNames.isNotEmpty()) {
                            childrenSpinnerAdapter.addAll(profileNames)
                            // Seleccionar el primero por defecto si no hay selección previa
                            if (selectedChild == null && profiles.isNotEmpty()) {
                                selectedChild = profiles.first()
                                binding.spinnerChild.setSelection(0, false) // No activar onItemSelected
                                Log.d(_TAG, "Default child selected: ${selectedChild?.name}")
                            } else {
                                // Si hay selección previa, intentar mantenerla
                                val currentSelectionIndex = profiles.indexOfFirst { it.childId == selectedChild?.childId }
                                if (currentSelectionIndex != -1) {
                                    binding.spinnerChild.setSelection(currentSelectionIndex, false)
                                } else if (profiles.isNotEmpty()) { // Selección previa inválida, tomar el primero
                                    selectedChild = profiles.first()
                                    binding.spinnerChild.setSelection(0, false)
                                }
                            }
                        } else {
                            selectedChild = null
                            Log.d(_TAG, "No child profiles to display in spinner.")
                        }
                        childrenSpinnerAdapter.notifyDataSetChanged()
                    }
                }

                launch {
                    graphsViewModel.isLoadingProfiles.collectLatest { isLoading ->
                        Log.d(_TAG, "Observed isLoadingProfiles: $isLoading")
                        binding.progressBarProfiles.isVisible = isLoading
                        binding.spinnerChild.isEnabled = !isLoading
                    }
                }

                launch {
                    graphsViewModel.isLoadingGraphData.collectLatest { isLoading ->
                        Log.d(_TAG, "Observed isLoadingGraphData: $isLoading")
                        binding.progressBarGraphData.isVisible = isLoading
                        binding.btnFetchData.isEnabled = !isLoading
                        // Podrías deshabilitar también los selectores de fecha aquí
                    }
                }

                launch {
                    graphsViewModel.heartRateData.collectLatest { data ->
                        Log.d(_TAG, "Observed heartRateData: ${data.size} items")
                        displayHeartRateChart(data)
                    }
                }

                launch {
                    graphsViewModel.gyroscopeData.collectLatest { data ->
                        Log.d(_TAG, "Observed gyroscopeData: ${data.size} items")
                        displayGyroscopeChart(data)
                    }
                }

                launch {
                    graphsViewModel.errorMessage.collectLatest { error ->
                        error?.let {
                            Log.e(_TAG, "Observed error: $it")
                            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                            graphsViewModel.clearErrorMessage() // Limpiar después de mostrar
                        }
                    }
                }
            }
        }
    }

    private fun displayHeartRateChart(data: List<HeartRateData>) {
        if (data.isEmpty()) {
            Log.d(_TAG, "displayHeartRateChart: No heart rate data to display.")
            binding.heartRateChart.data = null // Limpiar gráfico
            binding.heartRateChart.setNoDataText("No heart rate data for selected period.")
            binding.heartRateChart.invalidate()
            return
        }

        val entries = ArrayList<Entry>()
        val referenceTimestamp = data.firstOrNull()?.timestamp?.toDate()?.time ?: System.currentTimeMillis()

        data.forEach {
            it.timestamp?.let { ts ->
                // Usar segundos o minutos desde el primer timestamp para el eje X
                // O usar directamente el timestamp en milisegundos si TimestampAxisValueFormatter lo maneja bien
                val timeOffset = (ts.toDate().time - referenceTimestamp).toFloat() // En milisegundos
                entries.add(Entry(timeOffset, it.heartRateBpm.toFloat()))
            }
        }

        val dataSet = LineDataSet(entries, "Heart Rate (BPM)")
        dataSet.color = Color.RED
        dataSet.valueTextColor = Color.BLACK
        dataSet.setCircleColor(Color.RED)
        dataSet.circleRadius = 3f
        dataSet.lineWidth = 2f

        val lineData = LineData(dataSet)
        binding.heartRateChart.data = lineData
        binding.heartRateChart.xAxis.valueFormatter = RelativeTimestampAxisValueFormatter(referenceTimestamp)

        binding.heartRateChart.invalidate() // Refresh chart
        Log.d(_TAG, "Heart rate chart updated with ${entries.size} entries.")
    }

    private fun displayGyroscopeChart(data: List<GyroscopeData>) {
        if (data.isEmpty()) {
            Log.d(_TAG, "displayGyroscopeChart: No gyroscope data to display.")
            binding.gyroscopeChart.data = null // Limpiar gráfico
            binding.gyroscopeChart.setNoDataText("No gyroscope data for selected period.")
            binding.gyroscopeChart.invalidate()
            return
        }

        val entriesX = ArrayList<Entry>()
        val entriesY = ArrayList<Entry>()
        val entriesZ = ArrayList<Entry>()
        val referenceTimestamp = data.firstOrNull()?.timestamp?.toDate()?.time ?: System.currentTimeMillis()


        data.forEach {
            it.timestamp?.let { ts ->
                val timeOffset = (ts.toDate().time - referenceTimestamp).toFloat()
                it.value?.let { gyroValues ->
                    entriesX.add(Entry(timeOffset, gyroValues.x))
                    entriesY.add(Entry(timeOffset, gyroValues.y))
                    entriesZ.add(Entry(timeOffset, gyroValues.z))
                }
            }
        }

        val dataSetX = LineDataSet(entriesX, "Gyro X")
        dataSetX.color = Color.BLUE
        dataSetX.setDrawCircles(false)
        dataSetX.lineWidth = 1.5f

        val dataSetY = LineDataSet(entriesY, "Gyro Y")
        dataSetY.color = Color.GREEN
        dataSetY.setDrawCircles(false)
        dataSetY.lineWidth = 1.5f

        val dataSetZ = LineDataSet(entriesZ, "Gyro Z")
        dataSetZ.color = Color.MAGENTA
        dataSetZ.setDrawCircles(false)
        dataSetZ.lineWidth = 1.5f

        val lineData = LineData(dataSetX, dataSetY, dataSetZ)
        binding.gyroscopeChart.data = lineData
        binding.gyroscopeChart.xAxis.valueFormatter = RelativeTimestampAxisValueFormatter(referenceTimestamp)

        binding.gyroscopeChart.invalidate() // Refresh chart
        Log.d(_TAG, "Gyroscope chart updated with X:${entriesX.size}, Y:${entriesY.size}, Z:${entriesZ.size} entries.")
    }

    private fun showDatePickerDialog(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        // Usar la fecha ya seleccionada si existe, sino la actual
        val initialTimestamp = if (isStartDate) selectedStartDate else selectedEndDate
        initialTimestamp?.let { calendar.time = it.toDate() }

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)
                // Para mantener la hora si ya estaba seleccionada (o ponerla a medianoche)
                // Por simplicidad, aquí se resetea la hora a medianoche del día seleccionado.
                // Si necesitas seleccionar hora también, añade un TimePickerDialog.
                selectedCalendar.set(Calendar.HOUR_OF_DAY, if(isStartDate) 0 else 23)
                selectedCalendar.set(Calendar.MINUTE, if(isStartDate) 0 else 59)
                selectedCalendar.set(Calendar.SECOND, if(isStartDate) 0 else 59)

                val newTimestamp = Timestamp(selectedCalendar.time)
                if (isStartDate) {
                    updateStartDate(newTimestamp)
                } else {
                    updateEndDate(newTimestamp)
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateStartDate(timestamp: Timestamp) {
        selectedStartDate = timestamp
        binding.btnStartDate.text = "Start: ${formatDate(timestamp.toDate())}"
        Log.d(_TAG, "Start date updated: ${timestamp.toDate()}")
    }

    private fun updateEndDate(timestamp: Timestamp) {
        selectedEndDate = timestamp
        binding.btnEndDate.text = "End: ${formatDate(timestamp.toDate())}"
        Log.d(_TAG, "End date updated: ${timestamp.toDate()}")
    }

    private fun formatDate(date: Date): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(_TAG, "onDestroyView")
        graphsViewModel.onClear() // Limpiar jobs del ViewModel
        binding.heartRateChart.data = null // Limpiar datos de gráficos para liberar memoria
        binding.gyroscopeChart.data = null
        _binding = null
    }
}

// Formateador para el eje X para mostrar timestamps relativos (ej. "0s", "30s", "1m")
class RelativeTimestampAxisValueFormatter(private val referenceTimestampMillis: Long) : ValueFormatter() {
    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault()) // Para mostrar hora:minuto:segundo

    override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
        // value es el offset en milisegundos desde referenceTimestampMillis
        val actualTimestamp = referenceTimestampMillis + value.toLong()
        return sdf.format(Date(actualTimestamp))
    }
}

// Formateador genérico de Timestamp si prefieres mostrar la fecha/hora completa
class TimestampAxisValueFormatter : ValueFormatter() {
    private val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
        // Aquí 'value' se espera que sea el timestamp original en milisegundos
        return sdf.format(Date(value.toLong()))
    }
}