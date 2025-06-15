package com.example.authguardian.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.authguardian.R
import com.example.authguardian.databinding.FragmentRegisterBinding // Make sure this binding is generated!
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    // Use activityViewModels() to share the ViewModel with AuthActivity and other fragments
    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRoleSpinner()
        setupListeners()
        observeAuthState()
    }

    private fun setupRoleSpinner() {
        val roles = arrayOf("guardian", "child") // Define roles
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRole.adapter = adapter
    }

    private fun setupListeners() {
        // Set up register button click listener
        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val selectedRole = binding.spinnerRole.selectedItem.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                authViewModel.register(email, password, selectedRole)
            } else {
                Toast.makeText(requireContext(), "Please enter email and password.", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up login text click listener to navigate back
        binding.tvLogin.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }
    }

    private fun observeAuthState() {
        lifecycleScope.launch {
            authViewModel.authState.collect { authState ->
                when (authState) {
                    AuthViewModel.AuthState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.btnRegister.isEnabled = false // Disable button while loading
                    }
                    AuthViewModel.AuthState.Unauthenticated -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnRegister.isEnabled = true
                    }
                    is AuthViewModel.AuthState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnRegister.isEnabled = true
                        Toast.makeText(requireContext(), "Registration Error: ${authState.message}", Toast.LENGTH_LONG).show()
                    }
                    // For successful registration, we generally navigate back to login
                    // or let AuthActivity handle the final navigation.
                    // If registration is successful, the AuthActivity's observeAuthState will
                    // eventually navigate to GuardianMainActivity or ChildMainActivity.
                    is AuthViewModel.AuthState.AuthenticatedAsChild,
                    is AuthViewModel.AuthState.AuthenticatedAsGuardian -> {
                        binding.progressBar.visibility = View.GONE
                        // If you want to show a success message *before* navigating away, do it here
                        Toast.makeText(requireContext(), "Registration successful!", Toast.LENGTH_SHORT).show()
                        // AuthActivity will handle navigation to main screens
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clear binding when view is destroyed to prevent memory leaks
    }
}