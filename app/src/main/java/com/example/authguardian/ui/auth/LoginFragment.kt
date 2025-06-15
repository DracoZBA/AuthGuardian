package com.example.authguardian.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope // Puedes mantener este import si lo usas para otras cosas
import androidx.lifecycle.repeatOnLifecycle // Importa este
import androidx.navigation.fragment.findNavController
import com.example.authguardian.R
import com.example.authguardian.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest // O solo 'collect' si no necesitas 'collectLatest' específicamente
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                authViewModel.login(email, password)
            } else {
                Toast.makeText(requireContext(), "Please enter email and password.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        // Observar el AuthState del ViewModel de forma segura con el ciclo de vida de la vista
        viewLifecycleOwner.lifecycleScope.launch { // Usa viewLifecycleOwner.lifecycleScope
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { // Y repeatOnLifecycle
                authViewModel.authState.collectLatest { authState -> // O .collect si es suficiente
                    // Ahora es seguro acceder a 'binding' aquí porque este bloque
                    // solo se ejecutará cuando la vista esté al menos en estado STARTED.
                    when (authState) {
                        AuthViewModel.AuthState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.btnLogin.isEnabled = false
                        }
                        AuthViewModel.AuthState.Unauthenticated -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnLogin.isEnabled = true
                        }
                        is AuthViewModel.AuthState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnLogin.isEnabled = true
                            Toast.makeText(
                                requireContext(),
                                "Login Error: ${authState.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is AuthViewModel.AuthState.AuthenticatedAsChild,
                        is AuthViewModel.AuthState.AuthenticatedAsGuardian -> {
                            binding.progressBar.visibility = View.GONE
                            // AuthActivity maneja la navegación.
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Muy importante para prevenir memory leaks y la causa del NPE
    }
}