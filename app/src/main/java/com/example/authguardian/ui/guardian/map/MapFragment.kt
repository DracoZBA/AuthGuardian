package com.example.authguardian.ui.guardian.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.Circle
import com.example.authguardian.R
import com.example.authguardian.databinding.FragmentMap2Binding // Asegúrate que el binding sea correcto
import com.example.authguardian.service.LocationTrackingService
import com.example.authguardian.ui.guardian.geofence.GeofenceSetupFragment // Para navegar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMap2Binding? = null
    private val binding get() = _binding!!

    private lateinit var googleMap: GoogleMap
    private val mapViewModel: MapViewModel by viewModels()

    // Para almacenar los círculos de geocercas en el mapa
    private val geofenceCircles = mutableListOf<Circle>()

    // Request permissions launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                // Fine location granted. Check background if needed.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true) {
                        startLocationTracking()
                        googleMap.isMyLocationEnabled = true
                        Toast.makeText(context, "Location permissions granted", Toast.LENGTH_SHORT).show()
                    } else {
                        // Request background location
                        requestBackgroundLocationPermission()
                    }
                } else {
                    startLocationTracking()
                    googleMap.isMyLocationEnabled = true
                    Toast.makeText(context, "Location permission granted", Toast.LENGTH_SHORT).show()
                }
            }
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                // Only coarse location granted
                Toast.makeText(context, "Coarse location granted. For better accuracy, enable fine location.", Toast.LENGTH_LONG).show()
                startLocationTracking()
                googleMap.isMyLocationEnabled = true
            }
            else -> {
                Toast.makeText(context, "Location permissions denied. Map and tracking will be limited.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val requestBackgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startLocationTracking()
            googleMap.isMyLocationEnabled = true
            Toast.makeText(context, "Background location granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Background location denied. Geofence alerts may not work correctly when app is in background.", Toast.LENGTH_LONG).show()
            // Optionally explain why background location is needed.
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

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        binding.fabAddGeofence.setOnClickListener {
            // Navegar al fragmento de configuración de geocercas
            // Asume que tienes una acción definida en mobile_navigation.xml
            // findNavController().navigate(R.id.action_mapFragment_to_geofenceSetupFragment)
            // O si usas Safe Args:
            // findNavController().navigate(MapFragmentDirections.actionMapFragmentToGeofenceSetupFragment())
            Toast.makeText(context, "Navigate to Geofence Setup", Toast.LENGTH_SHORT).show() // Placeholder
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        Log.d("MapFragment", "Map is ready")

        // Configurar el mapa inicial (ej. Arequipa, Perú)
        val arequipa = LatLng(-16.409047, -71.537451)
        googleMap.addMarker(MarkerOptions().position(arequipa).title("Arequipa, Peru"))
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(arequipa, 15f)) // Nivel de zoom

        // Habilitar la capa "My Location" si se tienen permisos
        checkAndRequestLocationPermissions()

        // Observar la ubicación actual del niño
        lifecycleScope.launch {
            mapViewModel.childCurrentLocation.collectLatest { location ->
                location?.let {
                    val childLatLng = LatLng(it.latitude, it.longitude)
                    googleMap.clear() // Limpiar marcadores anteriores
                    // Volver a añadir el marcador de la ciudad inicial si es necesario
                    // googleMap.addMarker(MarkerOptions().position(arequipa).title("Arequipa, Peru"))

                    googleMap.addMarker(MarkerOptions().position(childLatLng).title("Ubicación del Niño"))
                    // Puedes animar la cámara si la ubicación cambia significativamente
                    // googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(childLatLng, 17f))
                    Log.d("MapFragment", "Child location updated: ${it.latitude}, ${it.longitude}")

                    // Redibujar las geocercas
                    drawGeofencesOnMap()
                }
            }
        }

        // Observar las geocercas guardadas para dibujarlas
        lifecycleScope.launch {
            mapViewModel.geofences.collectLatest { geofences ->
                Log.d("MapFragment", "Geofences updated: ${geofences.size}")
                drawGeofencesOnMap() // Redibujar cada vez que cambian las geocercas
            }
        }
    }

    private fun checkAndRequestLocationPermissions() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val backgroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true // Not required for API < 29

        val permissionsToRequest = mutableListOf<String>()
        if (!fineLocationGranted) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!coarseLocationGranted && !fineLocationGranted) { // Request coarse if fine is not granted
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Permissions already granted
            startLocationTracking()
            googleMap.isMyLocationEnabled = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !backgroundLocationGranted) {
                requestBackgroundLocationPermission()
            }
        }
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    // Explica al usuario por qué necesitas este permiso antes de solicitarlo
                    Toast.makeText(context, "Background location is needed for geofence alerts when app is closed.", Toast.LENGTH_LONG).show()
                    requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    // Directamente solicita el permiso
                    requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        }
    }


    private fun startLocationTracking() {
        val serviceIntent = Intent(requireContext(), LocationTrackingService::class.java)
        // Asegúrate de iniciar como un Foreground Service para Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent)
        } else {
            requireContext().startService(serviceIntent)
        }
    }

    private fun drawGeofencesOnMap() {
        // Limpiar círculos de geocercas anteriores
        geofenceCircles.forEach { it.remove() }
        geofenceCircles.clear()

        mapViewModel.geofences.value.forEach { geofence ->
            val center = LatLng(geofence.latitude, geofence.longitude)
            val circle = googleMap.addCircle(
                CircleOptions()
                    .center(center)
                    .radius(geofence.radius.toDouble()) // Radio en metros
                    .strokeColor(ContextCompat.getColor(requireContext(), R.color.primary_dark_blue))
                    .fillColor(ContextCompat.getColor(requireContext(), R.color.primary_blue_light_transparent)) // Define un color transparente si quieres
                    .strokeWidth(2f)
            )
            geofenceCircles.add(circle)

            googleMap.addMarker(MarkerOptions().position(center).title(geofence.name))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}