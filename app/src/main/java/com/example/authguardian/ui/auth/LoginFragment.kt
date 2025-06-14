package com.example.authguardian.ui.auth

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.semantics.text
import androidx.fragment.app.viewModels // Para la delegación de viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.authguardian.R
import com.example.authguardian.databinding.FragmentLoginBinding // Importa la clase de View Binding generada
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.text.isNotEmpty
import kotlin.text.trim

@AndroidEntryPoint // Necesario para la inyección de Hilt en Fragments
class LoginFragment : Fragment() {

    // Declara la variable para View Binding
    private var _binding: FragmentLoginBinding? = null
    // Esta propiedad solo es válida entre onCreateView y onDestroyView.
    private val binding get() = _binding!!

    // Inyecta el AuthViewModel usando la delegación de Hilt
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Infla el layout usando View Binding
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root // Retorna la vista raíz del binding
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        observeAuthState()
    }

    // En LoginFragment.kt, dentro de la función setupClickListeners()

    private fun setupClickListeners() {
        binding.buttonLogin.setOnClickListener {
            val email = binding.editTextEmail.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()

            Log.d("LoginFragment", "Login button clicked. Email: $email")

            if (email.isNotEmpty() && password.isNotEmpty()) {
                authViewModel.login(email, password) // <--- CAMBIADO DE signIn a login
            } else {
                Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonRegister.setOnClickListener {
            val email = binding.editTextEmail.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()

            Log.d("LoginFragment", "Register button clicked. Email: $email")

            // Ejemplo: val isGuardian = binding.checkBoxIsGuardian.isChecked
            val isGuardian = false // CAMBIAR ESTO: Obtener de la UI

            if (email.isNotEmpty() && password.isNotEmpty()) {
                authViewModel.register(email, password, isGuardian) // <--- CAMBIADO DE signUp a register
            } else {
                Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            authViewModel.authState.collect { state ->
                Log.d("LoginFragment", "New AuthState: $state")
                binding.progressBar.visibility = View.GONE
                binding.buttonLogin.isEnabled = true
                binding.buttonRegister.isEnabled = true

                when (state) {
                    is AuthViewModel.AuthState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.buttonLogin.isEnabled = false
                        binding.buttonRegister.isEnabled = false
                        Log.d("LoginFragment", "State: Loading")
                    }
                    is AuthViewModel.AuthState.AuthenticatedAsGuardian -> {
                        Toast.makeText(context, "Authenticated as Guardian", Toast.LENGTH_SHORT).show()
                        Log.d("LoginFragment", "State: AuthenticatedAsGuardian. Navigating to Guardian Dashboard...")
                        // Aquí navegas a la pantalla del Guardián
                        // Ejemplo: findNavController().navigate(R.id.action_loginFragment_to_guardianDashboardFragment)
                        // DEBES DEFINIR ESTA ACCIÓN EN TU NAV_GRAPH.XML
                    }
                    is AuthViewModel.AuthState.AuthenticatedAsChild -> {
                        Toast.makeText(context, "Authenticated as Child", Toast.LENGTH_SHORT).show()
                        Log.d("LoginFragment", "State: AuthenticatedAsChild. Navigating to Child Dashboard...")
                        // Aquí navegas a la pantalla del Niño
                        // Ejemplo: findNavController().navigate(R.id.action_loginFragment_to_childDashboardFragment)
                        // DEBES DEFINIR ESTA ACCIÓN EN TU NAV_GRAPH.XML
                    }
                    is AuthViewModel.AuthState.Unauthenticated -> {
                        Log.d("LoginFragment", "State: Unauthenticated")
                        // Puedes mostrar un mensaje si es relevante o simplemente es el estado inicial
                    }
                    is AuthViewModel.AuthState.Error -> {
                        Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                        Log.e("LoginFragment", "Authentication Error: ${state.message}")
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Limpia la referencia al binding para evitar memory leaks
    }

    // El companion object para newInstance ya no es tan necesario si solo navegas
    // al fragmento sin pasar argumentos iniciales de esta forma.
    // Si lo necesitas para otros propósitos, puedes mantenerlo, pero los param1, param2
    // no se usan en la lógica de login actual.
}