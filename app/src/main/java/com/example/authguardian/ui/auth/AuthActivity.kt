package com.example.authguardian.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // The NavHostFragment is configured via app:navGraph in XML
        // and app:defaultNavHost="true" in activity_auth.xml (which you'd need if you had one)
        // If AuthActivity contains a NavHostFragment for Login/Register,
        // it handles its own navigation within that graph.
        // The current AuthActivity logic directly starts other activities based on auth state.

        observeAuthState()
    }

    private fun observeAuthState() {
        lifecycleScope.launch {
            authViewModel.authState.collect { authState ->
                when (authState) {
                    is AuthViewModel.AuthState.AuthenticatedAsGuardian -> { // <-- Corrected here
                        // You can access authState.userId here if needed
                        navigateToGuardianMain()
                    }
                    is AuthViewModel.AuthState.AuthenticatedAsChild -> {   // <-- Corrected here
                        // You can access authState.userId here if needed
                        navigateToChildMain()
                    }
                    AuthViewModel.AuthState.Unauthenticated -> {
                        // The NavHostFragment in your layout (if exists) should
                        // automatically show the LoginFragment (startDestination)
                        Toast.makeText(this@AuthActivity, "Please log in or register.", Toast.LENGTH_SHORT).show()
                    }
                    AuthViewModel.AuthState.Loading -> {
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
        startActivity(Intent(this, ChildMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}