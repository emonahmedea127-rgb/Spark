package com.example.sociva.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SocivaBlue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountScreen(
  onRegister: (
    firstName: String,
    lastName: String,
    email: String,
    phone: String?,
    password: String,
    dateOfBirth: String,
    gender: String
  ) -> Unit,
  onBackToLogin: () -> Unit,
  isLoading: Boolean,
  errorMessage: String?,
  onClearError: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler(enabled = !isLoading) {
    onClearError()
    onBackToLogin()
  }

  var firstName by remember { mutableStateOf("") }
  var lastName by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var dateOfBirth by remember { mutableStateOf("") }
  var gender by remember { mutableStateOf("Female") }

  var isPasswordVisible by remember { mutableStateOf(false) }
  var isConfirmPasswordVisible by remember { mutableStateOf(false) }

  // Validation Error States
  var firstNameError by remember { mutableStateOf<String?>(null) }
  var lastNameError by remember { mutableStateOf<String?>(null) }
  var emailError by remember { mutableStateOf<String?>(null) }
  var phoneError by remember { mutableStateOf<String?>(null) }
  var passwordError by remember { mutableStateOf<String?>(null) }
  var confirmPasswordError by remember { mutableStateOf<String?>(null) }
  var dobError by remember { mutableStateOf<String?>(null) }

  val context = LocalContext.current
  val focusManager = LocalFocusManager.current

  // DatePickerDialog setup
  val calendar = remember { Calendar.getInstance().apply { add(Calendar.YEAR, -18) } }
  val datePickerDialog = remember {
    DatePickerDialog(
      context,
      { _, year, month, dayOfMonth ->
        val selectedCalendar = Calendar.getInstance().apply {
          set(year, month, dayOfMonth)
        }
        val format = SimpleDateFormat("MMM d, yyyy", Locale.US)
        dateOfBirth = format.format(selectedCalendar.time)
        dobError = null
      },
      calendar.get(Calendar.YEAR),
      calendar.get(Calendar.MONTH),
      calendar.get(Calendar.DAY_OF_MONTH)
    )
  }

  fun validateAndSubmit() {
    focusManager.clearFocus()
    onClearError()

    var isValid = true

    val cleanFirst = firstName.trim()
    val cleanLast = lastName.trim()
    val cleanEmail = email.trim()
    val cleanPhone = phone.trim()
    val cleanPassword = password.trim()
    val cleanConfirm = confirmPassword.trim()

    if (cleanFirst.isBlank()) {
      firstNameError = "First name is required."
      isValid = false
    } else if (cleanFirst.length < 2) {
      firstNameError = "Must be at least 2 characters."
      isValid = false
    } else {
      firstNameError = null
    }

    if (cleanLast.isBlank()) {
      lastNameError = "Last name is required."
      isValid = false
    } else if (cleanLast.length < 2) {
      lastNameError = "Must be at least 2 characters."
      isValid = false
    } else {
      lastNameError = null
    }

    if (cleanEmail.isBlank()) {
      emailError = "Email address is required."
      isValid = false
    } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
      emailError = "Please enter a valid email address."
      isValid = false
    } else {
      emailError = null
    }

    if (cleanPhone.isNotBlank() && cleanPhone.filter { it.isDigit() }.length < 7) {
      phoneError = "Please enter a valid phone number."
      isValid = false
    } else {
      phoneError = null
    }

    if (cleanPassword.isBlank()) {
      passwordError = "Password is required."
      isValid = false
    } else if (cleanPassword.length < 6) {
      passwordError = "Password must be at least 6 characters."
      isValid = false
    } else if (!cleanPassword.any { it.isDigit() } || !cleanPassword.any { it.isLetter() }) {
      passwordError = "Password must contain both letters and numbers."
      isValid = false
    } else {
      passwordError = null
    }

    if (cleanConfirm.isBlank()) {
      confirmPasswordError = "Please confirm your password."
      isValid = false
    } else if (cleanPassword != cleanConfirm) {
      confirmPasswordError = "Passwords do not match."
      isValid = false
    } else {
      confirmPasswordError = null
    }

    if (dateOfBirth.isBlank()) {
      dobError = "Please select your date of birth."
      isValid = false
    } else {
      dobError = null
    }

    if (isValid && !isLoading) {
      onRegister(
        cleanFirst,
        cleanLast,
        cleanEmail,
        cleanPhone.ifBlank { null },
        cleanPassword,
        dateOfBirth,
        gender
      )
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
            text = "Join Spark",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
        },
        navigationIcon = {
          IconButton(
            onClick = {
              if (!isLoading) {
                onClearError()
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
        .padding(horizontal = 24.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 480.dp)
      ) {
        Text(
          text = "Create new account",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "It's quick and easy to get started on Spark.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Error Banner
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

        // Row for First Name & Last Name
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          OutlinedTextField(
            value = firstName,
            onValueChange = {
              firstName = it
              if (firstNameError != null) firstNameError = null
            },
            label = { Text("First name") },
            leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null) },
            isError = firstNameError != null,
            supportingText = if (firstNameError != null) {
              { Text(firstNameError!!, color = MaterialTheme.colorScheme.error) }
            } else null,
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) }),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("register_first_name_input")
          )

          OutlinedTextField(
            value = lastName,
            onValueChange = {
              lastName = it
              if (lastNameError != null) lastNameError = null
            },
            label = { Text("Last name") },
            isError = lastNameError != null,
            supportingText = if (lastNameError != null) {
              { Text(lastNameError!!, color = MaterialTheme.colorScheme.error) }
            } else null,
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("register_last_name_input")
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Email field
        OutlinedTextField(
          value = email,
          onValueChange = {
            email = it
            if (emailError != null) emailError = null
          },
          label = { Text("Email address") },
          leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
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
          keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("register_email_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Phone field (optional)
        OutlinedTextField(
          value = phone,
          onValueChange = {
            phone = it
            if (phoneError != null) phoneError = null
          },
          label = { Text("Mobile number (optional)") },
          leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
          isError = phoneError != null,
          supportingText = if (phoneError != null) {
            { Text(phoneError!!, color = MaterialTheme.colorScheme.error) }
          } else null,
          singleLine = true,
          enabled = !isLoading,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Next
          ),
          keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("register_phone_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Date of Birth selection
        OutlinedTextField(
          value = dateOfBirth,
          onValueChange = { },
          readOnly = true,
          label = { Text("Date of birth") },
          placeholder = { Text("Select your birthday") },
          leadingIcon = { Icon(Icons.Outlined.Cake, contentDescription = null) },
          trailingIcon = {
            IconButton(
              onClick = { if (!isLoading) datePickerDialog.show() },
              enabled = !isLoading
            ) {
              Icon(Icons.Outlined.CalendarMonth, contentDescription = "Select Date")
            }
          },
          isError = dobError != null,
          supportingText = if (dobError != null) {
            { Text(dobError!!, color = MaterialTheme.colorScheme.error) }
          } else null,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading) { datePickerDialog.show() }
            .testTag("register_dob_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Gender Selection
        Text(
          text = "Gender",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf("Female", "Male", "Custom").forEach { option ->
            val isSelected = gender == option
            Surface(
              onClick = { if (!isLoading) gender = option },
              shape = RoundedCornerShape(12.dp),
              color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
              border = if (isSelected) {
                ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary))
              } else {
                ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant))
              },
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = option,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Password field
        OutlinedTextField(
          value = password,
          onValueChange = {
            password = it
            if (passwordError != null) passwordError = null
          },
          label = { Text("Password (min 6 chars)") },
          leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
          trailingIcon = {
            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
              Icon(
                imageVector = if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = null
              )
            }
          },
          visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          isError = passwordError != null,
          supportingText = if (passwordError != null) {
            { Text(passwordError!!, color = MaterialTheme.colorScheme.error) }
          } else {
            { Text("Use at least 6 characters with letters and numbers.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
          },
          singleLine = true,
          enabled = !isLoading,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next
          ),
          keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("register_password_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Confirm Password field
        OutlinedTextField(
          value = confirmPassword,
          onValueChange = {
            confirmPassword = it
            if (confirmPasswordError != null) confirmPasswordError = null
          },
          label = { Text("Confirm password") },
          leadingIcon = { Icon(Icons.Outlined.LockReset, contentDescription = null) },
          trailingIcon = {
            IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
              Icon(
                imageVector = if (isConfirmPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = null
              )
            }
          },
          visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          isError = confirmPasswordError != null,
          supportingText = if (confirmPasswordError != null) {
            { Text(confirmPasswordError!!, color = MaterialTheme.colorScheme.error) }
          } else null,
          singleLine = true,
          enabled = !isLoading,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
          ),
          keyboardActions = KeyboardActions(onDone = { validateAndSubmit() }),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("register_confirm_password_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = "By tapping Create account, you agree to Spark's Terms of Service and Privacy Policy.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Create Account Button
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
            .testTag("register_submit_button")
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = Color.White,
              strokeWidth = 2.5.dp
            )
          } else {
            Text(
              text = "Create account",
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = Color.White
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Back to Login Button
        TextButton(
          onClick = {
            if (!isLoading) {
              onClearError()
              onBackToLogin()
            }
          },
          enabled = !isLoading,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("back_to_login_button")
        ) {
          Text(
            text = "Already have an account? Log in",
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp
          )
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}
