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
import com.google.firebase.Timestamp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class GraphsFragment : Fragment() {

    private var _binding: FragmentGraphsBinding? = null
    private val binding get() = _binding!!

    private val graphsViewModel: GraphsViewModel by viewModels()

    private val childrenListForSpinner = mutableListOf<ChildProfile>()
    private var childrenSpinnerAdapter: ArrayAdapter<String>? = null

    private val _TAG = "GraphsFragment"
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

        childrenSpinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            mutableListOf()
        )
        childrenSpinnerAdapter?.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerChild.adapter = childrenSpinnerAdapter
        binding.spinnerChild.isEnabled = false

        setupChart(binding.heartRateChart, "Ritmo Cardíaco")
        setupChart(binding.gyroscopeChart, "Datos de Giroscopio")

        binding.tvAiAnalysisResult.text = getString(R.string.ai_analysis_placeholder_initial)
        binding.tvAiAnalysisResult.visibility = View.VISIBLE
    }

    private fun setupChart(chart: com.github.mikephil.charting.charts.LineChart, description: String) {
        chart.description.text = description
        chart.setNoDataText("Cargando datos...")
        chart.setNoDataTextColor(Color.GRAY)
        chart.xAxis.valueFormatter = TimestampAxisValueFormatter()
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.setLabelRotationAngle(45f)
        chart.invalidate()
    }

    private fun setupListeners() {
        binding.spinnerChild.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isProgrammaticSelection) {
                    isProgrammaticSelection = false
                    return
                }

                if (position >= 0 && position < childrenListForSpinner.size) {
                    val selectedChildProfile = childrenListForSpinner[position]
                    graphsViewModel.selectChild(selectedChildProfile.childId)
                    binding.tvAiAnalysisResult.text = getString(R.string.ai_analysis_placeholder_initial)
                    binding.progressBarAiAnalysis.isVisible = false
                } else {
                    graphsViewModel.selectChild(null)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                if (!isProgrammaticSelection) {
                    graphsViewModel.selectChild(null)
                }
            }
        }

        binding.btnAnalyzeAi.setOnClickListener {
            graphsViewModel.analyzeGraphDataWithGemini()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    graphsViewModel.childrenProfiles.collectLatest { profiles ->
                        childrenListForSpinner.clear()
                        childrenListForSpinner.addAll(profiles)

                        if (profiles.isNotEmpty()) {
                            val profileNames = profiles.mapNotNull { it.name }
                            childrenSpinnerAdapter = ArrayAdapter(
                                requireContext(),
                                android.R.layout.simple_spinner_item,
                                profileNames
                            ).apply {
                                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            }
                            binding.spinnerChild.adapter = childrenSpinnerAdapter

                            val selectedChildInVm = graphsViewModel.selectedChildId.value
                            val selectedIndex = profiles.indexOfFirst { it.childId == selectedChildInVm }

                            if (selectedIndex != -1) {
                                if (binding.spinnerChild.selectedItemPosition != selectedIndex) {
                                    isProgrammaticSelection = true
                                    binding.spinnerChild.setSelection(selectedIndex, false)
                                }
                            } else {
                                isProgrammaticSelection = true
                                binding.spinnerChild.setSelection(0, false)
                                graphsViewModel.selectChild(profiles[0].childId)
                            }
                            binding.spinnerChild.isEnabled = true
                        } else {
                            binding.spinnerChild.adapter = null
                            binding.spinnerChild.isEnabled = false
                            graphsViewModel.selectChild(null)
                        }
                    }
                }

                launch {
                    graphsViewModel.isLoadingProfiles.collectLatest { isLoading ->
                        binding.progressBarProfiles.isVisible = isLoading
                        if (isLoading) binding.spinnerChild.isEnabled = false
                    }
                }

                launch {
                    graphsViewModel.isLoadingGraphData.collectLatest { isLoading ->
                        binding.progressBarGraphData.isVisible = isLoading
                    }
                }

                launch {
                    graphsViewModel.heartRateData.collectLatest { data ->
                        displayHeartRateChart(data)
                    }
                }

                launch {
                    graphsViewModel.gyroscopeData.collectLatest { data ->
                        displayGyroscopeChart(data)
                    }
                }

                launch {
                    graphsViewModel.errorMessage.collectLatest { error ->
                        error?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                            graphsViewModel.clearErrorMessage()
                        }
                    }
                }

                // ✅ CORREGIDO: Mostrar resultado de IA cuando isAnalyzingAi pasa a false
                launch {
                    graphsViewModel.isAnalyzingAi.collectLatest { isLoading ->
                        binding.progressBarAiAnalysis.isVisible = isLoading
                        binding.btnAnalyzeAi.isEnabled = !isLoading

                        if (isLoading) {
                            binding.tvAiAnalysisResult.text =
                                getString(R.string.ai_analysis_placeholder_analyzing)
                        } else {
                            val result = graphsViewModel.aiAnalysisResult.value
                            val textToDisplay = result
                                ?: getString(R.string.ai_analysis_placeholder_no_result)
                            binding.tvAiAnalysisResult.text = textToDisplay
                            binding.tvAiAnalysisResult.visibility = View.VISIBLE

                            binding.scrollView.post {
                                binding.scrollView.fullScroll(View.FOCUS_DOWN)
                            }
                        }
                    }
                }

                // Solo logging: el UI se actualiza al cambiar isAnalyzingAi
                launch {
                    graphsViewModel.aiAnalysisResult.collectLatest { result ->
                        Log.d(_TAG, "aiAnalysisResult: Received new result. Length: ${result?.length}")
                    }
                }
            }
        }
    }

    private fun displayHeartRateChart(data: List<HeartRateData>) {
        val (startDate, _) = graphsViewModel.getThirtyDayTimeRange()
        if (data.isEmpty()) {
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

        val dataSet = LineDataSet(entries, "Ritmo Cardíaco (BPM)").apply {
            color = Color.RED
            valueTextColor = Color.BLACK
            setCircleColor(Color.RED)
            circleRadius = 3f
            lineWidth = 2f
        }

        binding.heartRateChart.data = LineData(dataSet)
        binding.heartRateChart.xAxis.valueFormatter =
            RelativeTimestampAxisValueFormatter(referenceTimestamp)
        binding.heartRateChart.invalidate()
    }

    private fun displayGyroscopeChart(data: List<GyroscopeData>) {
        val (startDate, _) = graphsViewModel.getThirtyDayTimeRange()
        if (data.isEmpty()) {
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

        val dataSetX = LineDataSet(entriesX, "Giro X").apply {
            color = Color.BLUE
            setDrawCircles(false)
            lineWidth = 1.5f
        }
        val dataSetY = LineDataSet(entriesY, "Giro Y").apply {
            color = Color.GREEN
            setDrawCircles(false)
            lineWidth = 1.5f
        }
        val dataSetZ = LineDataSet(entriesZ, "Giro Z").apply {
            color = Color.MAGENTA
            setDrawCircles(false)
            lineWidth = 1.5f
        }

        binding.gyroscopeChart.data = LineData(dataSetX, dataSetY, dataSetZ)
        binding.gyroscopeChart.xAxis.valueFormatter =
            RelativeTimestampAxisValueFormatter(referenceTimestamp)
        binding.gyroscopeChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.heartRateChart.data = null
        binding.heartRateChart.clear()
        binding.gyroscopeChart.data = null
        binding.gyroscopeChart.clear()
        _binding = null
    }
}

class RelativeTimestampAxisValueFormatter(private val referenceTimestampMillis: Long) :
    ValueFormatter() {
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
