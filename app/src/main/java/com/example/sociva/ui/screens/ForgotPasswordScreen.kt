package com.example.sociva.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SocivaBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
  onSendResetLink: (String) -> Unit,
  onBackToLogin: () -> Unit,
  isLoading: Boolean,
  errorMessage: String?,
  successMessage: String?,
  onClearMessages: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler(enabled = !isLoading) {
    onClearMessages()
    onBackToLogin()
  }

  var email by remember { mutableStateOf("") }
  var emailError by remember { mutableStateOf<String?>(null) }

  val focusManager = LocalFocusManager.current

  fun validateAndSubmit() {
    focusManager.clearFocus()
    onClearMessages()

    val trimmedEmail = email.trim()
    if (trimmedEmail.isBlank()) {
      emailError = "Please enter your email address."
    } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
      emailError = "Please enter a valid email address."
    } else {
      emailError = null
      if (!isLoading) {
        onSendResetLink(trimmedEmail)
      }
    }
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Reset Password",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
        },
        navigationIcon = {
          IconButton(
            onClick = {
              if (!isLoading) {
                onClearMessages()
                onBackToLogin()
              }
            },
            enabled = !isLoading
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back to Login"
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 480.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header Icon
        Box(
          modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Outlined.LockReset,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(36.dp)
          )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
          text = "Find Your Account",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "Enter the email associated with your Spark account to receive password reset instructions.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Error message card
        if (!errorMessage.isNullOrBlank()) {
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 16.dp)
          ) {
            Text(
              text = errorMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(14.dp)
            )
          }
        }

        // Success state card
        if (!successMessage.isNullOrBlank()) {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 24.dp)
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
              )

              Spacer(modifier = Modifier.height(10.dp))

              Text(
                text = "Request Recorded",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )

              Spacer(modifier = Modifier.height(8.dp))

              Text(
                text = successMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }
          }

          Button(
            onClick = {
              onClearMessages()
              onBackToLogin()
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
          ) {
            Text(
              text = "Back to Log In",
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = Color.White
            )
          }
        } else {
          // Email input field
          OutlinedTextField(
            value = email,
            onValueChange = {
              email = it
              if (emailError != null) emailError = null
              if (errorMessage != null) onClearMessages()
            },
            label = { Text("Email address") },
            placeholder = { Text("name@example.com") },
            leadingIcon = {
              Icon(
                imageVector = Icons.Outlined.Email,
                contentDescription = null
              )
            },
            isError = emailError != null,
            supportingText = if (emailError != null) {
              { Text(emailError!!, color = MaterialTheme.colorScheme.error) }
            } else null,
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Email,
              imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
              onDone = { validateAndSubmit() }
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("forgot_password_email_input")
          )

          Spacer(modifier = Modifier.height(24.dp))

          // Submit button
          Button(
            onClick = { validateAndSubmit() },
            enabled = !isLoading,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = SocivaBlue,
              disabledContainerColor = SocivaBlue.copy(alpha = 0.6f)
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("forgot_password_submit_button")
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
              )
            } else {
              Text(
                text = "Continue",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          TextButton(
            onClick = {
              if (!isLoading) {
                onClearMessages()
                onBackToLogin()
              }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "Back to Log In",
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary,
              fontSize = 14.sp
            )
          }
        }
      }
    }
  }
}
