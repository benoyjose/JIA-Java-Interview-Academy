package com.jia.academy.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val otpRequested: Boolean = false,
    val session: JiaSession? = null
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun authenticate(baseUrl: String, registering: Boolean, name: String, email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                if (registering) repository.register(baseUrl, name.trim(), email.trim(), password)
                else repository.login(baseUrl, email.trim(), password)
            }.onSuccess { _state.value = _state.value.copy(loading = false, session = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Authentication failed.") }
        }
    }

    fun loginWithGoogle(baseUrl: String, idToken: String, nonce: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.loginWithGoogle(baseUrl, idToken, nonce) }
                .onSuccess { _state.value = _state.value.copy(loading = false, session = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Google sign-in failed.") }
        }
    }

    fun requestPhoneOtp(baseUrl: String, phoneNumber: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.requestPhoneOtp(baseUrl, phoneNumber.trim()) }
                .onSuccess { _state.value = _state.value.copy(loading = false, otpRequested = true) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "OTP request failed.") }
        }
    }

    fun verifyPhoneOtp(baseUrl: String, phoneNumber: String, code: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.verifyPhoneOtp(baseUrl, phoneNumber.trim(), code) }
                .onSuccess { _state.value = _state.value.copy(loading = false, session = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Phone verification failed.") }
        }
    }

    fun setError(message: String?) {
        _state.value = _state.value.copy(error = message)
    }

    fun resetOtp() {
        _state.value = _state.value.copy(otpRequested = false, error = null)
    }

    fun consumeSession() {
        _state.value = _state.value.copy(session = null)
    }
}

class AuthViewModelFactory(private val repository: AuthRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) return AuthViewModel(repository) as T
        throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
    }
}
