package com.example.authguardian.ui.guardian.graphs

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
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
import com.google.firebase.Timestamp // Asegúrate de que esta importación sea correcta si usas Firebase Timestamp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class GraphsFragment : Fragment() {

    private var _binding: FragmentGraphsBinding? = null
    private val binding get() = _binding!!

    private val graphsViewModel: GraphsViewModel by viewModels()

    // Esta lista es necesaria para que el onItemSelectedListener pueda acceder
    // al perfil completo del niño seleccionado por su posición.
    private val childrenListForSpinner = mutableListOf<ChildProfile>()

    // El ArrayAdapter se inicializará en setupUI o cuando lleguen los datos.
    // Lo hacemos nullable o late-init si lo inicializamos más tarde.
    private var childrenSpinnerAdapter: ArrayAdapter<String>? = null

    private val _TAG = "GraphsFragment"

    // Bandera para evitar que onItemSelected se dispare por cambios programáticos.
    private var isProgrammaticSelection = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGraphsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(_TAG, "onViewCreated: Fragment view is being created.")

        setupUI()
        setupListeners()
        observeViewModel()
    }

    private fun setupUI() {
        Log.d(_TAG, "setupUI: Initializing UI components.")
        // Inicializa el adaptador del spinner aquí, con una lista vacía.
        // Esto asegura que el adaptador exista desde el principio.
        childrenSpinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item, // Layout estándar para el item seleccionado
            mutableListOf() // Lista vacía inicial
        )
        childrenSpinnerAdapter?.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) // Layout para el menú desplegable
        binding.spinnerChild.adapter = childrenSpinnerAdapter
        // Deshabilita el spinner al inicio, se habilitará cuando los datos de perfiles estén listos.
        binding.spinnerChild.isEnabled = false

        setupChart(binding.heartRateChart, "Ritmo Cardíaco")
        setupChart(binding.gyroscopeChart, "Datos de Giroscopio")
    }

    private fun setupChart(chart: com.github.mikephil.charting.charts.LineChart, description: String) {
        chart.description.text = description
        chart.setNoDataText("No hay datos disponibles.")
        chart.setNoDataTextColor(Color.GRAY)
        chart.xAxis.valueFormatter = TimestampAxisValueFormatter()
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.setLabelRotationAngle(45f)
        chart.invalidate() // Asegura que el gráfico se redibuje con la configuración inicial
    }

    private fun setupListeners() {
        Log.d(_TAG, "setupListeners: Setting up spinner selection listener.")
        binding.spinnerChild.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isProgrammaticSelection) {
                    // Si la selección fue programática, ignoramos el evento y reseteamos la bandera.
                    isProgrammaticSelection = false
                    Log.d(_TAG, "onItemSelected: Ignored programmatic selection at position $position.")
                    return // Sale de la función
                }

                // Esta es una selección realizada por el usuario.
                if (position >= 0 && position < childrenListForSpinner.size) {
                    val selectedChildProfile = childrenListForSpinner[position]
                    Log.d(_TAG, "onItemSelected: USER selected child: ${selectedChildProfile.name} (ID: ${selectedChildProfile.childId}) at position $position.")
                    graphsViewModel.selectChild(selectedChildProfile.childId)
                } else {
                    // Esta situación es improbable si el spinner está bien gestionado y childrenListForSpinner
                    // coincide con los elementos del adaptador.
                    graphsViewModel.selectChild(null)
                    Log.w(_TAG, "onItemSelected: Invalid position $position selected by user. Setting selected child to null.")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Si la selección se borra (ej. el adaptador cambia a vacío), y no es programática.
                if (!isProgrammaticSelection) {
                    graphsViewModel.selectChild(null)
                    Log.d(_TAG, "onNothingSelected: Spinner selection cleared, likely due to empty list. Setting selected child to null.")
                }
            }
        }
    }

    private fun observeViewModel() {
        Log.d(_TAG, "observeViewModel: Starting observation of ViewModel flows.")
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // --- OBSERVADOR PRINCIPAL: PERFILES DE HIJOS PARA EL SPINNER ---
                launch {
                    graphsViewModel.childrenProfiles.collectLatest { profiles ->
                        Log.d(_TAG, "CHILD_PROFILES_COLLECTOR: Received a new list of profiles. Size: ${profiles.size}")

                        // 1. Actualiza la lista local que usa el onItemSelectedListener. Es crucial que esto ocurra primero.
                        childrenListForSpinner.clear()
                        childrenListForSpinner.addAll(profiles)
                        Log.d(_TAG, "CHILD_PROFILES_COLLECTOR: Updated local list `childrenListForSpinner` with ${childrenListForSpinner.size} profiles.")

                        if (profiles.isNotEmpty()) {
                            Log.d(_TAG, "CHILD_PROFILES_COLLECTOR: Profiles list is not empty. Proceeding to update spinner.")

                            // 2. Obtiene los nombres de los perfiles para el ArrayAdapter.
                            val profileNames = profiles.mapNotNull { it.name }

                            // 3. ¡MUY IMPORTANTE! Siempre que la lista de perfiles cambie,
                            // RECREA y ASIGNA un nuevo ArrayAdapter para asegurar que el Spinner se refresque.
                            childrenSpinnerAdapter = ArrayAdapter(
                                requireContext(),
                                android.R.layout.simple_spinner_item,
                                profileNames
                            ).apply {
                                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            }
                            binding.spinnerChild.adapter = childrenSpinnerAdapter
                            Log.d(_TAG, "CHILD_PROFILES_COLLECTOR: NEW ArrayAdapter created and set to spinner with names: $profileNames")

                            // 4. Lógica para establecer la selección correcta en el spinner.
                            val selectedChildInVm = graphsViewModel.selectedChildId.value // O selectedChildProfile.value si tienes un Flow para el perfil completo
                            val selectedIndex = profiles.indexOfFirst { it.childId == selectedChildInVm } // Busca el índice del niño seleccionado por el VM
                            Log.d(_TAG, "CHILD_PROFILES_COLLECTOR: Searching for selection. Child ID in VM: $selectedChildInVm. Found at index: $selectedIndex.")

                            if (selectedIndex != -1) {
                                // Si hay un niño seleccionado en el ViewModel y está en la lista de perfiles, lo seleccionamos.
                                if (binding.spinnerChild.selectedItemPosition != selectedIndex) {
                                    isProgrammaticSelection = true // Activa la bandera para evitar el listener
                                    binding.spinnerChild.setSelection(selectedIndex, false) // `false` para no animar
                                    Log.d(_TAG, "CHILD_PROFILES_COLLECTOR: Programmatically setting spinner selection to index $selectedIndex ('${profiles[selectedIndex].name}').")
                                }
                            } else {
                                // Si no hay una selección válida en el ViewModel o el niño seleccionado ya no existe,
                                // seleccionamos el primer niño de la lista si hay alguno.
                                isProgrammaticSelection = true // Activa la bandera
                                binding.spinnerChild.setSelection(0, false) // Selecciona el primer elemento (índice 0)
                                // También actualiza el ViewModel para que refleje esta selección por defecto.
                                graphsViewModel.selectChild(profiles[0].childId)
                                Log.d(_TAG, "CHILD_PROFILES_COLLECTOR: No valid selection found in VM or child not in list. Defaulting to first child at index 0 ('${profiles[0].name}').")
                            }
                            binding.spinnerChild.isEnabled = true // Habilita el spinner una vez que tiene datos y selección
                            Log.d(_TAG, "CHILD_PROFILES_COLLECTOR: Spinner has been enabled and updated.")
                        } else {
                            // Si la lista de perfiles está vacía.
                            Log.d(_TAG, "CHILD_PROFILES_COLLECTOR: Profiles list is empty. Clearing and disabling spinner.")
                            binding.spinnerChild.adapter = null // Limpia el adaptador para que no muestre nada
                            binding.spinnerChild.isEnabled = false // Deshabilita el spinner
                            graphsViewModel.selectChild(null) // Asegúrate de que el ViewModel también sepa que no hay selección.
                        }
                    }
                }

                // --- OTROS OBSERVADORES (sin cambios importantes, ya estaban bien) ---

                launch {
                    graphsViewModel.isLoadingProfiles.collectLatest { isLoading ->
                        Log.d(_TAG, "isLoadingProfiles: $isLoading")
                        binding.progressBarProfiles.isVisible = isLoading
                        // Si está cargando perfiles, deshabilitar el spinner para evitar interacciones.
                        // Se habilitará de nuevo en el colector de childrenProfiles cuando terminen de cargar.
                        if (isLoading) {
                            binding.spinnerChild.isEnabled = false
                            Log.d(_TAG, "isLoadingProfiles: Profiles are loading, spinner temporarily disabled.")
                        }
                    }
                }

                launch {
                    graphsViewModel.isLoadingGraphData.collectLatest { isLoading ->
                        Log.d(_TAG, "isLoadingGraphData: $isLoading")
                        binding.progressBarGraphData.isVisible = isLoading
                    }
                }

                launch {
                    graphsViewModel.heartRateData.collectLatest { data ->
                        Log.d(_TAG, "heartRateData: Observed ${data.size} items.")
                        displayHeartRateChart(data)
                    }
                }

                launch {
                    graphsViewModel.gyroscopeData.collectLatest { data ->
                        Log.d(_TAG, "gyroscopeData: Observed ${data.size} items.")
                        displayGyroscopeChart(data)
                    }
                }

                launch {
                    graphsViewModel.errorMessage.collectLatest { error ->
                        error?.let {
                            Log.e(_TAG, "errorMessage: Displaying error: $it")
                            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                            graphsViewModel.clearErrorMessage()
                        }
                    }
                }
            }
        }
    }

    private fun displayHeartRateChart(data: List<HeartRateData>) {
        val (startDate, _) = graphsViewModel.getThirtyDayTimeRange()
        if (data.isEmpty()) {
            Log.d(_TAG, "displayHeartRateChart: No heart rate data to display.")
            binding.heartRateChart.data = null
            binding.heartRateChart.setNoDataText("No hay datos de ritmo cardíaco para el período seleccionado.")
            binding.heartRateChart.invalidate()
            return
        }

        val entries = ArrayList<Entry>()
        val referenceTimestamp = startDate.toDate().time

        data.forEach {
            it.timestamp?.let { ts ->
                val timeOffset = (ts.toDate().time - referenceTimestamp).toFloat()
                entries.add(Entry(timeOffset, it.heartRateBpm.toFloat()))
            }
        }

        val dataSet = LineDataSet(entries, "Ritmo Cardíaco (BPM)")
        dataSet.color = Color.RED
        dataSet.valueTextColor = Color.BLACK
        dataSet.setCircleColor(Color.RED)
        dataSet.circleRadius = 3f
        dataSet.lineWidth = 2f

        binding.heartRateChart.data = LineData(dataSet)
        binding.heartRateChart.xAxis.valueFormatter = RelativeTimestampAxisValueFormatter(referenceTimestamp)
        binding.heartRateChart.invalidate()
        Log.d(_TAG, "displayHeartRateChart: Chart updated with ${entries.size} entries.")
    }

    private fun displayGyroscopeChart(data: List<GyroscopeData>) {
        val (startDate, _) = graphsViewModel.getThirtyDayTimeRange()
        if (data.isEmpty()) {
            Log.d(_TAG, "displayGyroscopeChart: No gyroscope data to display.")
            binding.gyroscopeChart.data = null
            binding.gyroscopeChart.setNoDataText("No hay datos de giroscopio para el período seleccionado.")
            binding.gyroscopeChart.invalidate()
            return
        }

        val entriesX = ArrayList<Entry>()
        val entriesY = ArrayList<Entry>()
        val entriesZ = ArrayList<Entry>()
        val referenceTimestamp = startDate.toDate().time

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

        val dataSetX = LineDataSet(entriesX, "Giro X")
        dataSetX.color = Color.BLUE
        dataSetX.setDrawCircles(false)
        dataSetX.lineWidth = 1.5f

        val dataSetY = LineDataSet(entriesY, "Giro Y")
        dataSetY.color = Color.GREEN
        dataSetY.setDrawCircles(false)
        dataSetY.lineWidth = 1.5f

        val dataSetZ = LineDataSet(entriesZ, "Giro Z")
        dataSetZ.color = Color.MAGENTA
        dataSetZ.setDrawCircles(false)
        dataSetZ.lineWidth = 1.5f

        val lineData = LineData(dataSetX, dataSetY, dataSetZ)
        binding.gyroscopeChart.data = lineData
        binding.gyroscopeChart.xAxis.valueFormatter = RelativeTimestampAxisValueFormatter(referenceTimestamp)

        binding.gyroscopeChart.invalidate()
        Log.d(_TAG, "displayGyroscopeChart: Chart updated with X:${entriesX.size}, Y:${entriesY.size}, Z:${entriesZ.size} entries.")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(_TAG, "onDestroyView: View is being destroyed, cleaning up.")
        // Limpiar los datos de los gráficos para evitar leaks y asegurar un estado limpio
        binding.heartRateChart.data = null
        binding.heartRateChart.clear()
        binding.gyroscopeChart.data = null
        binding.gyroscopeChart.clear()
        _binding = null
    }
}

// Formateadores de fecha para los gráficos (sin cambios)
class RelativeTimestampAxisValueFormatter(private val referenceTimestampMillis: Long) : ValueFormatter() {
    private val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
        val actualTimestamp = referenceTimestampMillis + value.toLong()
        return sdf.format(Date(actualTimestamp))
    }
}

class TimestampAxisValueFormatter : ValueFormatter() {
    private val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
        return sdf.format(Date(value.toLong()))
    }
}