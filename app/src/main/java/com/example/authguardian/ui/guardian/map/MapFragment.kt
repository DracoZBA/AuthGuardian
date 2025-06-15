package com.example.authguardian.ui.guardian.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.tooling.data.position
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.authguardian.R
import com.example.authguardian.databinding.FragmentMap2Binding
import com.example.authguardian.models.GeofenceArea // Asegúrate de importar tu modelo
import com.example.authguardian.service.LocationTrackingService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMap2Binding? = null
    private val binding get() = _binding!!

    private lateinit var googleMap: GoogleMap
    private val mapViewModel: MapViewModel by viewModels()

    private val geofenceCircles = mutableListOf<Circle>()
    private var childMarker: com.google.android.gms.maps.model.Marker? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestBackgroundLocationPermission()
            } else {
                // El mapa ya debería estar listo o a punto de estarlo si se conceden aquí.
                // startLocationTracking y enableMyLocationOnMap se llamarán desde onMapReady
                // después de que checkAndRequestLocationPermissions determine que los permisos están concedidos.
                if (::googleMap.isInitialized) {
                    startLocationTracking()
                    enableMyLocationOnMap()
                }
            }
        } else {
            Toast.makeText(
                context,
                "Permisos de ubicación denegados. El mapa y el seguimiento serán limitados.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val requestBackgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (::googleMap.isInitialized) { // Asegurarse de que el mapa esté listo
                startLocationTracking()
                enableMyLocationOnMap() // Puede que quieras volver a habilitar si se denegó antes
            }
            Toast.makeText(context, "Permiso de ubicación en segundo plano concedido", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                "Permiso de ubicación en segundo plano denegado. Las alertas pueden no funcionar con la app cerrada.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMap2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this) // Inicia la carga del mapa

        binding.fabAddGeofence.setOnClickListener {
            findNavController().navigate(R.id.action_mapFragment_to_geofenceSetupFragment)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map // Mapa inicializado
        Log.d("MapFragment", "Map is ready")

        val defaultLocation = LatLng(-16.409047, -71.537451) // Arequipa, Perú
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))

        checkAndRequestLocationPermissions() // Solicita permisos y luego habilita funciones del mapa si se conceden

        // Observar el niño seleccionado para iniciar la carga de su ubicación y geocercas
        viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.selectedChildProfile.collectLatest { childProfile ->
                if (childProfile != null) {
                    Log.d("MapFragment", "Selected child changed to: ${childProfile.name}.")
                    // La lógica de carga ya está en los otros colectores.
                    // Si se selecciona un nuevo niño, los flows de childLocation y childGeofences
                    // deberían emitir nuevos valores y actualizar el mapa.
                } else {
                    Log.d("MapFragment", "No child selected. Clearing map of child-specific data.")
                    googleMap.clear() // Limpia marcadores, círculos, polilíneas, etc.
                    childMarker = null // Resetea la referencia al marcador del niño
                    geofenceCircles.clear() // Aunque clear() los elimina del mapa, limpia tu lista local
                }
            }
        }

        // Observar la ubicación actual del niño
        viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.childLocation.collectLatest { location ->
                location?.geoPoint?.let { geoPoint ->
                    val childLatLng = LatLng(geoPoint.latitude, geoPoint.longitude)

                    if (childMarker == null) {
                        childMarker = googleMap.addMarker(
                            MarkerOptions().position(childLatLng).title("Ubicación del Niño")
                        )
                    } else {
                        childMarker?.position = childLatLng
                    }
                    // Solo animar la cámara si el niño está seleccionado
                    if (mapViewModel.selectedChildProfile.value != null) {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(childLatLng, 15f))
                    }
                    Log.d("MapFragment", "Child location updated: ${geoPoint.latitude}, ${geoPoint.longitude}")

                    // Considera si es necesario redibujar geocercas aquí.
                    // Si las geocercas dependen del niño y no de su ubicación actual,
                    // el colector de childGeofences es suficiente.
                    // drawGeofencesOnMap(mapViewModel.childGeofences.value)
                }
            }
        }

        // Observar las geocercas guardadas para dibujarlas
        viewLifecycleOwner.lifecycleScope.launch {
            mapViewModel.childGeofences.collectLatest { geofenceList ->
                Log.d("MapFragment", "Geofences list updated: ${geofenceList.size}")
                drawGeofencesOnMap(geofenceList)
            }
        }
    }

    private fun enableMyLocationOnMap() {
        if (!::googleMap.isInitialized) {
            Log.w("MapFragment", "enableMyLocationOnMap called before map was ready.")
            return
        }
        try {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                googleMap.isMyLocationEnabled = true
                Toast.makeText(context, "Capa 'Mi Ubicación' habilitada.", Toast.LENGTH_SHORT).show()
            } else {
                Log.w("MapFragment", "enableMyLocationOnMap: Location permission not granted.")
            }
        } catch (e: SecurityException) {
            Log.e("MapFragment", "SecurityException en enableMyLocationOnMap", e)
        }
    }

    private fun checkAndRequestLocationPermissions() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val permissionsToRequest = mutableListOf<String>()
        if (!fineLocationGranted) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Permiso ACCESS_FINE_LOCATION ya concedido
            // Asegurarse de que el mapa esté listo antes de interactuar con él
            if (::googleMap.isInitialized) {
                startLocationTracking()
                enableMyLocationOnMap()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestBackgroundLocationPermission()
            }
        }
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                Toast.makeText(
                    context,
                    "La ubicación en segundo plano es para alertas de geocerca con la app cerrada.",
                    Toast.LENGTH_LONG
                ).show()
            }
            requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun startLocationTracking() {
        Log.d("MapFragment", "startLocationTracking llamado.")
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val serviceIntent = Intent(requireContext(), LocationTrackingService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireContext().startForegroundService(serviceIntent)
                } else {
                    requireContext().startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("MapFragment", "Error starting LocationTrackingService", e)
            }
        } else {
            Log.w("MapFragment", "startLocationTracking: Permiso de ubicación fina no concedido.")
        }
    }

    private fun drawGeofencesOnMap(geofencesToDraw: List<GeofenceArea>) {
        if (!::googleMap.isInitialized) {
            Log.w("MapFragment", "drawGeofencesOnMap called before map was ready.")
            return
        }

        // Limpiar solo los círculos de geocercas anteriores del mapa y de la lista local
        geofenceCircles.forEach { it.remove() }
        geofenceCircles.clear()

        Log.d("MapFragment", "Drawing ${geofencesToDraw.size} geofences on map.")
        geofencesToDraw.forEach { geofence ->
            val center = LatLng(geofence.latitude, geofence.longitude)
            val radiusInMeters = geofence.radius

            Log.d("MapFragment", "Drawing geofence: ${geofence.name} at $center with radius $radiusInMeters")

            val circleOptions = CircleOptions()
                .center(center)
                .radius(radiusInMeters)
                .strokeColor(ContextCompat.getColor(requireContext(), R.color.primary_dark_blue)) // Asegúrate que este color existe
                .fillColor(ContextCompat.getColor(requireContext(), R.color.primary_blue_light_transparent)) // Asegúrate que este color existe
                .strokeWidth(2f)

            googleMap.addCircle(circleOptions)?.let {
                geofenceCircles.add(it) // Añadir el nuevo círculo a la lista para seguimiento
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // El objeto googleMap se libera con el ciclo de vida del SupportMapFragment.
        // Limpiar referencias para ayudar al GC.
        childMarker = null
        geofenceCircles.clear() // Limpia la lista, los círculos en el mapa se van con el mapa.
        _binding = null
    }
}