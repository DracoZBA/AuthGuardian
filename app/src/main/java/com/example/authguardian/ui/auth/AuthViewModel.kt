package com.example.authguardian.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.authguardian.data.repository.AuthRepository
import com.example.authguardian.models.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // Observa el estado de autenticación de Firebase en tiempo real
        viewModelScope.launch {
            authRepository.getAuthState().collectLatest { user ->
                if (user != null) {
                    if (user.role == "guardian") {
                        _authState.value = AuthState.AuthenticatedAsGuardian(user.userId)
                    } else if (user.role == "child") {
                        _authState.value = AuthState.AuthenticatedAsChild(user.userId)
                    } else {
                        _authState.value = AuthState.Error("Unknown user role.")
                    }
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                authRepository.loginUser(email, password)
                // El authState se actualizará automáticamente por el listener en init{}
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun register(email: String, password: String, role: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                authRepository.registerUser(email, password, role)
                // El authState se actualizará automáticamente por el listener en init{}
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            authRepository.logout()
        }
    }

    sealed class AuthState {
        object Loading : AuthState()
        object Unauthenticated : AuthState()
        data class AuthenticatedAsGuardian(val userId: String) : AuthState()
        data class AuthenticatedAsChild(val userId: String) : AuthState()
        data class Error(val message: String) : AuthState()
    }
}