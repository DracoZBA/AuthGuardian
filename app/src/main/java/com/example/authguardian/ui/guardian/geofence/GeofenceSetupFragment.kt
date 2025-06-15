package com.example.authguardian.ui.guardian.geofence

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels // Asegúrate de que este sea el import correcto para by viewModels()
// Si MapViewModel debe ser compartido con la Activity, usa:
// import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
// import androidx.preference.isNotEmpty // No utilizado, se puede eliminar
import androidx.recyclerview.widget.LinearLayoutManager
// import androidx.recyclerview.widget.RecyclerView // No es estrictamente necesario importar si solo se usa en el binding
import com.example.authguardian.databinding.FragmentGeofenceSetupBinding
import com.example.authguardian.ui.guardian.map.MapViewModel
import com.example.authguardian.models.GeofenceArea
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GeofenceSetupFragment : Fragment() {

    private var _binding: FragmentGeofenceSetupBinding? = null
    private val binding get() = _binding!!

    // ViewModel específico para la lógica de este fragmento (añadir/quitar geocercas, etc.)
    private val geofenceSetupViewModel: GeofenceSetupViewModel by viewModels()

    // ViewModel para obtener la lista de geocercas (potencialmente compartido con MapFragment)
    // Considera usar activityViewModels() si MapViewModel debe ser la misma instancia que la usada en MapFragment
    // Ejemplo: private val mapViewModel: MapViewModel by activityViewModels()
    private val mapViewModel: MapViewModel by viewModels() // Si usas by viewModels(), esta será una instancia nueva/diferente a la de otros fragmentos.

    private lateinit var geofenceAdapter: GeofenceAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGeofenceSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        // Observar el niño activo desde GeofenceSetupViewModel
        // (este ViewModel debería ser la fuente de verdad para el niño seleccionado en ESTA pantalla)
        lifecycleScope.launch {
            geofenceSetupViewModel.activeChildProfile.collectLatest { childProfile ->
                binding.tvSelectedChildName.text = "Niño Seleccionado: ${childProfile?.name ?: "N/A"}"
                if (childProfile != null) {
                    // Informa a MapViewModel qué niño está activo en ESTA pantalla
                    // para que pueda cargar/filtrar las geocercas correspondientes.
                    // Esto es crucial si MapViewModel necesita saber el contexto del niño.
                    mapViewModel.selectChild(childProfile) // Asume que selectChild en MapViewModel actualiza sus datos internos

                    observeGeofences(childProfile.childId)
                } else {
                    geofenceAdapter.submitList(emptyList()) // Limpia la lista si no hay niño
                    binding.tvSelectedChildName.text = "Niño Seleccionado: N/A"
                }
            }
        }

        // Observar el estado de las operaciones de añadir geocerca
        lifecycleScope.launch {
            geofenceSetupViewModel.addGeofenceStatus.collectLatest { status ->
                when (status) {
                    is GeofenceSetupViewModel.GeofenceOperationStatus.Loading -> {
                        binding.tvStatusMessage.text = "Añadiendo geocerca..."
                        binding.btnAddGeofence.isEnabled = false
                    }
                    is GeofenceSetupViewModel.GeofenceOperationStatus.Success -> {
                        binding.tvStatusMessage.text = status.message
                        Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                        binding.btnAddGeofence.isEnabled = true
                        clearInputFields()
                        geofenceSetupViewModel.resetStatus() // Resetea el estado en el ViewModel
                    }
                    is GeofenceSetupViewModel.GeofenceOperationStatus.Error -> {
                        binding.tvStatusMessage.text = "Error: ${status.message}"
                        Toast.makeText(context, "Error: ${status.message}", Toast.LENGTH_LONG).show()
                        binding.btnAddGeofence.isEnabled = true
                        geofenceSetupViewModel.resetStatus() // Resetea el estado en el ViewModel
                    }
                    GeofenceSetupViewModel.GeofenceOperationStatus.Idle -> {
                        binding.tvStatusMessage.text = ""
                        binding.btnAddGeofence.isEnabled = true
                    }
                }
            }
        }

        binding.btnAddGeofence.setOnClickListener {
            addGeofence()
        }
    }

    private fun setupRecyclerView() {
        geofenceAdapter = GeofenceAdapter { geofenceId ->
            // La eliminación se maneja a través del geofenceSetupViewModel,
            // que debería coordinar con el DataRepository. MapViewModel se actualizará
            // a través de su propia observación del DataRepository.
            geofenceSetupViewModel.removeGeofence(geofenceId)
        }
        binding.rvExistingGeofences.layoutManager = LinearLayoutManager(context)
        binding.rvExistingGeofences.adapter = geofenceAdapter
    }

    private fun observeGeofences(childIdForFilter: String) {
        // Observamos el StateFlow 'childGeofences' del MapViewModel.
        // Este Flow ya debería estar filtrado por el niño seleccionado en MapViewModel
        // gracias a la llamada mapViewModel.selectChild(childProfile) que hicimos antes.
        lifecycleScope.launch {
            mapViewModel.childGeofences.collectLatest { geofenceList ->
                // Dado que MapViewModel.childGeofences ya está (o debería estar)
                // actualizado para el niño seleccionado (selectedChildProfile en MapViewModel),
                // la lista ya debería ser la correcta para ese niño.
                // Si aún necesitas asegurarte de que el childIdForFilter coincide con el del MapViewModel,
                // podrías añadir una comprobación con mapViewModel.selectedChildProfile.value.
                val currentSelectedChildInMapVM = mapViewModel.selectedChildProfile.value
                if (currentSelectedChildInMapVM != null && currentSelectedChildInMapVM.childId == childIdForFilter) {
                    geofenceAdapter.submitList(geofenceList)
                    Log.d("GeofenceSetupFragment", "Updated geofence list for ${currentSelectedChildInMapVM.name}: ${geofenceList.size} items")
                } else if (currentSelectedChildInMapVM == null) {
                    // Si no hay niño seleccionado en MapViewModel, pero SÍ en GeofenceSetupViewModel (childIdForFilter),
                    // es una discrepancia. Intentamos seleccionar el niño en MapViewModel.
                    // Esto puede suceder si GeofenceSetupFragment carga su estado antes que MapFragment, por ejemplo.
                    val childToSelect = mapViewModel.childrenProfiles.value.firstOrNull { it.childId == childIdForFilter }
                    if (childToSelect != null) {
                        mapViewModel.selectChild(childToSelect)
                        // La recolección de childGeofences se activará de nuevo cuando selectedChildProfile cambie.
                    } else {
                        geofenceAdapter.submitList(emptyList())
                        Log.d("GeofenceSetupFragment", "Child $childIdForFilter not found in MapViewModel's profiles. Clearing list.")
                    }
                } else {
                    // El niño seleccionado en MapViewModel es DIFERENTE al childIdForFilter de este fragmento.
                    // Esto podría indicar que el usuario cambió de niño en otra parte (e.g., MapFragment).
                    // GeofenceSetupFragment debería reaccionar a esto, posiblemente limpiando la lista
                    // o actualizando su propio activeChildProfile si MapViewModel es la fuente de verdad principal.
                    // Por ahora, limpiamos si no coincide.
                    geofenceAdapter.submitList(emptyList())
                    Log.d("GeofenceSetupFragment", "MapViewModel selected child (${currentSelectedChildInMapVM.childId}) differs from this fragment's child ($childIdForFilter). Clearing list.")
                }
            }
        }
    }

    private fun addGeofence() {
        val name = binding.etGeofenceName.text.toString().trim()
        val latStr = binding.etGeofenceLatitude.text.toString().trim()
        val lonStr = binding.etGeofenceLongitude.text.toString().trim()
        val radStr = binding.etGeofenceRadius.text.toString().trim()

        if (name.isEmpty() || latStr.isEmpty() || lonStr.isEmpty() || radStr.isEmpty()) {
            Toast.makeText(context, "Por favor, completa todos los campos.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val latitude = latStr.toDouble()
            val longitude = lonStr.toDouble()
            val radius = radStr.toFloat() // Geofence API suele usar Float para el radio

            if (radius <= 0) {
                Toast.makeText(context, "El radio debe ser mayor a 0.", Toast.LENGTH_SHORT).show()
                return
            }

            // La lógica de añadir geocerca se delega al GeofenceSetupViewModel
            geofenceSetupViewModel.addGeofence(name, latitude, longitude, radius)

        } catch (e: NumberFormatException) {
            Toast.makeText(context, "Latitud, Longitud y Radio deben ser números válidos.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearInputFields() {
        binding.etGeofenceName.text?.clear()
        binding.etGeofenceLatitude.text?.clear()
        binding.etGeofenceLongitude.text?.clear()
        binding.etGeofenceRadius.text?.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Evitar fugas de memoria con el binding
    }
}