package com.example.authguardian.ui.guardian

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.example.authguardian.R
import com.example.authguardian.databinding.ActivityGuardianMainBinding
import dagger.hilt.android.AndroidEntryPoint // Asegúrate de tener Hilt configurado

@AndroidEntryPoint
class GuardianMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuardianMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuardianMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Obtener una referencia al NavHostFragment
        // USA EL ID CORRECTO DEL XML
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.guardian_nav_host_fragment) as NavHostFragment // <--- CORREGIDO AQUÍ

        // 2. Obtener el NavController de ese NavHostFragment
        navController = navHostFragment.navController

        // 3. Conectar la BottomNavigationView con el NavController
        // USA LA PROPIEDAD CORRECTA GENERADA POR VIEW BINDING
        // El ID en XML es "bottom_navigation_view", entonces la propiedad es "bottomNavigationView"
        binding.bottomNavigationView.setupWithNavController(navController) // <--- CORREGIDO AQUÍ

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.dashboardFragment3,
                R.id.calendarFragment,
                R.id.mapFragment,
                R.id.graphsFragment,
                R.id.settingsFragment
            )
        )

        // Si tienes una Toolbar en tu activity_guardian_main.xml y quieres que la maneje el Navigation Component:
        // setSupportActionBar(binding.toolbar) // Descomenta si tienes un <androidx.appcompat.widget.Toolbar> con id="toolbar"
        // setupActionBarWithNavController(navController, appBarConfiguration)
        // Si no usas una Toolbar gestionada por AppCompat, puedes manejar el título manualmente
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Ejemplo: supportActionBar?.title = destination.label
        }
    }

    // Este método permite que el botón de flecha de "volver" en la AppBar funcione
    // (si lo tienes configurado con setupActionBarWithNavController)
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}