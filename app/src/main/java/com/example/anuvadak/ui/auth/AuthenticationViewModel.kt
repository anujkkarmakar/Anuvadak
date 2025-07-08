package com.example.anuvadak.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthenticationViewModel : ViewModel() {
    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val auth: FirebaseAuth = Firebase.auth

    init {
        // Check if user is already logged in
        checkAuthState()
    }

    private fun checkAuthState() {
        val currentUser = auth.currentUser
        _authState.value = _authState.value.copy(
            isAuthenticated = currentUser != null,
            user = currentUser
        )
    }

    fun updateEmail(email: String) {
        _authState.value = _authState.value.copy(
            email = email,
            error = null
        )
    }

    fun updatePassword(password: String) {
        _authState.value = _authState.value.copy(
            password = password,
            error = null
        )
    }

    fun updateConfirmPassword(confirmPassword: String) {
        _authState.value = _authState.value.copy(
            confirmPassword = confirmPassword,
            error = null
        )
    }

    fun updateName(name: String) {
        _authState.value = _authState.value.copy(
            name = name,
            error = null
        )
    }

    fun togglePasswordVisibility() {
        _authState.value = _authState.value.copy(
            isPasswordVisible = !_authState.value.isPasswordVisible
        )
    }

    fun toggleConfirmPasswordVisibility() {
        _authState.value = _authState.value.copy(
            isConfirmPasswordVisible = !_authState.value.isConfirmPasswordVisible
        )
    }

    fun switchToSignUp() {
        _authState.value = _authState.value.copy(
            isSignUpMode = true,
            error = null,
            email = "",
            password = "",
            confirmPassword = "",
            name = ""
        )
    }

    fun switchToSignIn() {
        _authState.value = _authState.value.copy(
            isSignUpMode = false,
            error = null,
            email = "",
            password = "",
            confirmPassword = "",
            name = ""
        )
    }

    fun signIn() {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(
                isLoading = true,
                error = null
            )

            // Validation
            if (_authState.value.email.isBlank()) {
                _authState.value = _authState.value.copy(
                    error = "Please enter your email.",
                    isLoading = false
                )
                return@launch
            }

            if (!isValidEmail(_authState.value.email)) {
                _authState.value = _authState.value.copy(
                    error = "Please enter a valid email address.",
                    isLoading = false
                )
                return@launch
            }

            if (_authState.value.password.isBlank()) {
                _authState.value = _authState.value.copy(
                    error = "Please enter your password.",
                    isLoading = false
                )
                return@launch
            }

            try {
                val result = auth.signInWithEmailAndPassword(
                    _authState.value.email,
                    _authState.value.password
                ).await()

                _authState.value = _authState.value.copy(
                    isLoading = false,
                    isAuthenticated = true,
                    user = result.user,
                    error = null
                )
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    error = getAuthErrorMessage(e)
                )
            }
        }
    }

    fun signUp() {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(
                isLoading = true,
                error = null
            )

            // Validation
            if (_authState.value.name.isBlank()) {
                _authState.value = _authState.value.copy(
                    error = "Please enter your name.",
                    isLoading = false
                )
                return@launch
            }

            if (_authState.value.email.isBlank()) {
                _authState.value = _authState.value.copy(
                    error = "Please enter your email.",
                    isLoading = false
                )
                return@launch
            }

            if (!isValidEmail(_authState.value.email)) {
                _authState.value = _authState.value.copy(
                    error = "Please enter a valid email address.",
                    isLoading = false
                )
                return@launch
            }

            if (_authState.value.password.length < 6) {
                _authState.value = _authState.value.copy(
                    error = "Password must be at least 6 characters long.",
                    isLoading = false
                )
                return@launch
            }

            if (_authState.value.password != _authState.value.confirmPassword) {
                _authState.value = _authState.value.copy(
                    error = "Passwords do not match.",
                    isLoading = false
                )
                return@launch
            }

            try {
                val result = auth.createUserWithEmailAndPassword(
                    _authState.value.email,
                    _authState.value.password
                ).await()

                // Update user profile with name
                result.user?.let { user ->
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(_authState.value.name)
                        .build()

                    user.updateProfile(profileUpdates).await()
                }

                _authState.value = _authState.value.copy(
                    isLoading = false,
                    isAuthenticated = true,
                    user = result.user,
                    error = null
                )
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    error = getAuthErrorMessage(e)
                )
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _authState.value = AuthState() // Reset to initial state
    }

    fun resetPassword() {
        viewModelScope.launch {
            if (_authState.value.email.isBlank()) {
                _authState.value = _authState.value.copy(
                    error = "Please enter your email to reset password."
                )
                return@launch
            }

            if (!isValidEmail(_authState.value.email)) {
                _authState.value = _authState.value.copy(
                    error = "Please enter a valid email address."
                )
                return@launch
            }

            _authState.value = _authState.value.copy(isLoading = true)

            try {
                auth.sendPasswordResetEmail(_authState.value.email).await()
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    error = null,
                    successMessage = "Password reset email sent. Please check your inbox."
                )
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    error = getAuthErrorMessage(e)
                )
            }
        }
    }

    fun clearMessages() {
        _authState.value = _authState.value.copy(
            error = null,
            successMessage = null
        )
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun getAuthErrorMessage(exception: Exception): String {
        return when {
            exception.message?.contains("badly formatted") == true ->
                "Please enter a valid email address."
            exception.message?.contains("no user record") == true ->
                "No account found with this email address."
            exception.message?.contains("wrong-password") == true ->
                "Incorrect password. Please try again."
            exception.message?.contains("user-disabled") == true ->
                "This account has been disabled."
            exception.message?.contains("email-already-in-use") == true ->
                "An account with this email already exists."
            exception.message?.contains("weak-password") == true ->
                "Password is too weak. Please choose a stronger password."
            exception.message?.contains("network error") == true ->
                "Network error. Please check your internet connection."
            else -> "Authentication failed: ${exception.localizedMessage ?: "Unknown error"}"
        }
    }
}

data class AuthState(
    val isAuthenticated: Boolean = false,
    val user: FirebaseUser? = null,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val name: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val isSignUpMode: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false
)