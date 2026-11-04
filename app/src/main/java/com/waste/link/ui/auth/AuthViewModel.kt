package com.waste.link.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waste.link.data.models.AppUser
import com.waste.link.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: AppUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun checkCurrentUser() {
        val uid = authRepository.getCurrentUserId()
        if (uid != null) {
            _authState.value = AuthState.Loading
            viewModelScope.launch {
                authRepository.getUserRole(uid).fold(
                    onSuccess = { user -> _authState.value = AuthState.Authenticated(user) },
                    onFailure = { 
                        // If document not found but user is signed in, sign them out
                        authRepository.signOut()
                        _authState.value = AuthState.Idle 
                    }
                )
            }
        }
    }

    fun signIn(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.signIn(email, password).fold(
                onSuccess = { user -> _authState.value = AuthState.Authenticated(user) },
                onFailure = { e -> _authState.value = AuthState.Error(e.message ?: "Sign in failed") }
            )
        }
    }

    fun signUp(email: String, password: String, name: String, phone: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.signUp(email, password, name, phone).fold(
                onSuccess = { user -> _authState.value = AuthState.Authenticated(user) },
                onFailure = { e -> _authState.value = AuthState.Error(e.message ?: "Sign up failed") }
            )
        }
    }

    fun setRole(role: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.setRole(uid, role).fold(
                onSuccess = { user -> _authState.value = AuthState.Authenticated(user) },
                onFailure = { e -> _authState.value = AuthState.Error(e.message ?: "Role selection failed") }
            )
        }
    }

    fun sendPasswordResetEmail(email: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            authRepository.sendPasswordResetEmail(email).fold(
                onSuccess = { onResult(true, null) },
                onFailure = { e -> onResult(false, e.message) }
            )
        }
    }

    fun isUserSignedIn(): Boolean {
        return authRepository.isUserSignedIn()
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _authState.value = AuthState.Idle
        }
    }

    fun wipeDatabase() {
        viewModelScope.launch {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            com.waste.link.utils.DatabaseWipeUtility.wipeDatabase(db)
            authRepository.signOut()
            _authState.value = AuthState.Idle
        }
    }
}
