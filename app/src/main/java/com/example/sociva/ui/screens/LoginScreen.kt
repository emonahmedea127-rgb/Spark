package com.example.sociva.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaIndigo
import com.example.ui.theme.SocivaPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
  onLogin: (String, String) -> Unit,
  onForgotPasswordClick: () -> Unit,
  onCreateAccountClick: () -> Unit,
  isLoading: Boolean,
  errorMessage: String?,
  onClearError: () -> Unit,
  modifier: Modifier = Modifier
) {
  var emailOrPhone by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var isPasswordVisible by remember { mutableStateOf(false) }

  var emailError by remember { mutableStateOf<String?>(null) }
  var passwordError by remember { mutableStateOf<String?>(null) }

  val focusManager = LocalFocusManager.current

  fun validateAndSubmit() {
    focusManager.clearFocus()
    onClearError()

    var isValid = true
    val trimmedIdentifier = emailOrPhone.trim()
    val trimmedPassword = password.trim()

    if (trimmedIdentifier.isBlank()) {
      emailError = "Please enter your mobile number or email address."
      isValid = false
    } else if (trimmedIdentifier.contains("@") && !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedIdentifier).matches()) {
      emailError = "Please enter a valid email address."
      isValid = false
    } else if (!trimmedIdentifier.contains("@") && trimmedIdentifier.filter { it.isDigit() }.length < 6) {
      emailError = "Please enter a valid phone number or email."
      isValid = false
    } else {
      emailError = null
    }

    if (trimmedPassword.isBlank()) {
      passwordError = "Please enter your password."
      isValid = false
    } else {
      passwordError = null
    }

    if (isValid && !isLoading) {
      onLogin(trimmedIdentifier, trimmedPassword)
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .windowInsetsPadding(WindowInsets.safeDrawing)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 480.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(modifier = Modifier.height(28.dp))

        // Spark App Logo
        Box(
          modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
              Brush.linearGradient(
                colors = listOf(SocivaIndigo, SocivaPurple)
              )
            )
            .padding(2.dp),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .clip(RoundedCornerShape(18.dp))
          ) {
            Image(
              painter = painterResource(id = R.drawable.spark_app_logo_1788636063462),
              contentDescription = "Spark Logo",
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Title and Subtitle
        Text(
          text = "Log in to Spark",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = "Connect with friends and the world around you.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Error Banner
        if (!errorMessage.isNullOrBlank()) {
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 16.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
              )
            }
          }
        }

        // Email or Phone input field
        OutlinedTextField(
          value = emailOrPhone,
          onValueChange = {
            emailOrPhone = it
            if (emailError != null) emailError = null
            if (errorMessage != null) onClearError()
          },
          label = { Text("Mobile number or email") },
          placeholder = { Text("Enter email or phone") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.Person,
              contentDescription = "Email or Phone"
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
            imeAction = ImeAction.Next
          ),
          keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
          ),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("login_email_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Password input field
        OutlinedTextField(
          value = password,
          onValueChange = {
            password = it
            if (passwordError != null) passwordError = null
            if (errorMessage != null) onClearError()
          },
          label = { Text("Password") },
          placeholder = { Text("Enter password") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.Lock,
              contentDescription = "Password"
            )
          },
          trailingIcon = {
            IconButton(
              onClick = { isPasswordVisible = !isPasswordVisible },
              enabled = !isLoading
            ) {
              Icon(
                imageVector = if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
              )
            }
          },
          visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          isError = passwordError != null,
          supportingText = if (passwordError != null) {
            { Text(passwordError!!, color = MaterialTheme.colorScheme.error) }
          } else null,
          singleLine = true,
          enabled = !isLoading,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
          ),
          keyboardActions = KeyboardActions(
            onDone = { validateAndSubmit() }
          ),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("login_password_input")
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Primary Log In Button
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
            .testTag("login_submit_button")
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = Color.White,
              strokeWidth = 2.5.dp
            )
          } else {
            Text(
              text = "Log In",
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = Color.White
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Forgot password link
        TextButton(
          onClick = {
            if (!isLoading) {
              onClearError()
              onForgotPasswordClick()
            }
          },
          enabled = !isLoading,
          modifier = Modifier.testTag("forgot_password_button")
        ) {
          Text(
            text = "Forgot password?",
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp
          )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Divider: --- or ---
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
          )
          Text(
            text = "or",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
          )
          HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
          )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Create new account Button (Facebook / Messenger style)
        OutlinedButton(
          onClick = {
            if (!isLoading) {
              onClearError()
              onCreateAccountClick()
            }
          },
          enabled = !isLoading,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
          ),
          border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(
              colors = listOf(SocivaIndigo, SocivaPurple)
            )
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("create_account_button")
        ) {
          Text(
            text = "Create new account",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
          )
        }
      }

      // Bottom Footer Brand
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 32.dp, bottom = 12.dp)
      ) {
        Text(
          text = "from",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
          text = "SPARK",
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.8.sp,
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}
