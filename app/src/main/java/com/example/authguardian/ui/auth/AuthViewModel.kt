package com.example.authguardian.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.authguardian.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val currentUser = authRepository.getCurrentUser()
            if (currentUser != null) {
                // Aquí necesitarás lógica para determinar si es Guardian o Niño
                // Podrías guardarlo en Firestore o en los claims del token de Firebase
                val isGuardian = authRepository.getUserRole(currentUser.uid)
                if (isGuardian) {
                    _authState.value = AuthState.AuthenticatedAsGuardian
                } else {
                    _authState.value = AuthState.AuthenticatedAsChild
                }
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                authRepository.loginUser(email, password)
                checkCurrentUser() // Re-check user role after successful login
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun register(email: String, password: String, isGuardian: Boolean) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                authRepository.registerUser(email, password, isGuardian)
                checkCurrentUser() // Re-check user role after successful registration
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _authState.value = AuthState.Unauthenticated
        }
    }

    sealed class AuthState {
        object Loading : AuthState()
        object Unauthenticated : AuthState()
        object AuthenticatedAsGuardian : AuthState()
        object AuthenticatedAsChild : AuthState()
        data class Error(val message: String) : AuthState()
    }
}