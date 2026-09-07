package com.example.sociva.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.sociva.data.repository.PasswordResetResult
import com.example.sociva.ui.SocivaViewModel

/**
 * Screen modes within the unauthenticated Spark flow.
 */
enum class AuthScreenMode {
  LOGIN,
  REGISTER,
  FORGOT_PASSWORD
}

@Composable
fun AuthScreen(
  viewModel: SocivaViewModel,
  modifier: Modifier = Modifier
) {
  var currentMode by remember { mutableStateOf(AuthScreenMode.LOGIN) }
  val isAuthLoading by viewModel.isAuthLoading.collectAsState()
  val authErrorMessage by viewModel.authErrorMessage.collectAsState()
  var passwordResetSuccessMessage by remember { mutableStateOf<String?>(null) }

  AnimatedContent(
    targetState = currentMode,
    transitionSpec = {
      if (targetState == AuthScreenMode.LOGIN) {
        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
      } else {
        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
      }
    },
    label = "spark_auth_flow_transition",
    modifier = modifier.fillMaxSize()
  ) { mode ->
    when (mode) {
      AuthScreenMode.LOGIN -> {
        LoginScreen(
          onLogin = { emailOrPhone, password ->
            viewModel.login(emailOrPhone, password)
          },
          onForgotPasswordClick = {
            viewModel.clearAuthError()
            passwordResetSuccessMessage = null
            currentMode = AuthScreenMode.FORGOT_PASSWORD
          },
          onCreateAccountClick = {
            viewModel.clearAuthError()
            passwordResetSuccessMessage = null
            currentMode = AuthScreenMode.REGISTER
          },
          isLoading = isAuthLoading,
          errorMessage = authErrorMessage,
          onClearError = { viewModel.clearAuthError() }
        )
      }

      AuthScreenMode.REGISTER -> {
        CreateAccountScreen(
          onRegister = { firstName, lastName, email, phone, password, dob, gender ->
            viewModel.register(
              firstName = firstName,
              lastName = lastName,
              email = email,
              phone = phone,
              password = password,
              dateOfBirth = dob,
              gender = gender
            )
          },
          onBackToLogin = {
            viewModel.clearAuthError()
            currentMode = AuthScreenMode.LOGIN
          },
          isLoading = isAuthLoading,
          errorMessage = authErrorMessage,
          onClearError = { viewModel.clearAuthError() }
        )
      }

      AuthScreenMode.FORGOT_PASSWORD -> {
        ForgotPasswordScreen(
          onSendResetLink = { email ->
            viewModel.sendPasswordReset(email) { result ->
              when (result) {
                is PasswordResetResult.Success -> {
                  passwordResetSuccessMessage = result.message
                }
                is PasswordResetResult.Error -> {
                  // Captured by viewModel.authErrorMessage
                }
              }
            }
          },
          onBackToLogin = {
            viewModel.clearAuthError()
            passwordResetSuccessMessage = null
            currentMode = AuthScreenMode.LOGIN
          },
          isLoading = isAuthLoading,
          errorMessage = authErrorMessage,
          successMessage = passwordResetSuccessMessage,
          onClearMessages = {
            viewModel.clearAuthError()
            passwordResetSuccessMessage = null
          }
        )
      }
    }
  }
}
