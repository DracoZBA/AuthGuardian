package com.example.authguardian.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.authguardian.R
import com.example.authguardian.databinding.ActivityAuthBinding // Asegúrate que el binding se actualice
import com.example.authguardian.ui.child.ChildMainActivity // Asume que tienes esta Activity
import com.example.authguardian.ui.guardian.GuardianMainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val authViewModel: AuthViewModel by viewModels()
    // private lateinit var navController: NavController // No es estrictamente necesario aquí

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // El NavHostFragment se configura a través de app:navGraph en el XML
        // y app:defaultNavHost="true"

        // val navHostFragment = supportFragmentManager
        //     .findFragmentById(R.id.auth_nav_host_fragment) as NavHostFragment
        // navController = navHostFragment.navController
        // No necesitas interactuar directamente con el navController aquí si el flujo de login/registro
        // se maneja dentro de auth_nav_graph

        observeAuthState()
    }

    private fun observeAuthState() {
        lifecycleScope.launch {
            authViewModel.authState.collect { authState ->
                when (authState) {
                    AuthViewModel.AuthState.AuthenticatedAsGuardian -> {
                        navigateToGuardianMain()
                    }
                    AuthViewModel.AuthState.AuthenticatedAsChild -> {
                        navigateToChildMain()
                    }
                    AuthViewModel.AuthState.Unauthenticated -> {
                        // El NavHostFragment ya mostrará el LoginFragment (startDestination)
                        // No necesitas hacer nada aquí para mostrar el login.
                        // Si el usuario cierra sesión y vuelve a este estado,
                        // el NavController del auth_nav_graph debería volver al LoginFragment.
                        // Podrías añadir lógica aquí si necesitas resetear el auth_nav_graph explícitamente:
                        // navController.navigate(R.id.loginFragment) { // O el ID del startDestination
                        //     popUpTo(R.id.auth_nav_graph) { inclusive = true }
                        // }
                        Toast.makeText(this@AuthActivity, "Please log in or register.", Toast.LENGTH_SHORT).show()
                    }
                    AuthViewModel.AuthState.Loading -> {
                        // Podrías mostrar un ProgressBar global en AuthActivity si lo deseas,
                        // o dejar que LoginFragment maneje su propio ProgressBar.
                        Toast.makeText(this@AuthActivity, "Loading...", Toast.LENGTH_SHORT).show()
                    }
                    is AuthViewModel.AuthState.Error -> {
                        Toast.makeText(this@AuthActivity, "Auth Error: ${authState.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun navigateToGuardianMain() {
        startActivity(Intent(this, GuardianMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun navigateToChildMain() {
        // Asegúrate de tener una ChildMainActivity o la Activity correspondiente
        startActivity(Intent(this, ChildMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish() // Cierra AuthActivity para que el usuario no pueda volver con el botón "atrás"
    }
}