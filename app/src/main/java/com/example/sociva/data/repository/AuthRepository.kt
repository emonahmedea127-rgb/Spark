package com.example.sociva.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.sociva.data.local.SocivaDao
import com.example.sociva.data.local.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * Clean UserSession representing the active authenticated session.
 * Designed to seamlessly map to Supabase User / Auth Session.
 */
data class UserSession(
  val userId: String,
  val email: String,
  val displayName: String,
  val phoneNumber: String? = null,
  val photoUrl: String? = null,
  val token: String? = null,
  val isEmailVerified: Boolean = false,
  val createdAt: Long = System.currentTimeMillis()
)

/**
 * Core Authentication States for the app startup and lifecycle.
 */
sealed interface AuthState {
  data object Loading : AuthState
  data class Authenticated(val session: UserSession) : AuthState
  data object Unauthenticated : AuthState
}

/**
 * Results from authentication operations.
 */
sealed interface AuthResult {
  data class Success(val session: UserSession) : AuthResult
  data class Error(val message: String) : AuthResult
}

/**
 * Result from password reset operation.
 */
sealed interface PasswordResetResult {
  data class Success(val message: String) : PasswordResetResult
  data class Error(val message: String) : PasswordResetResult
}

sealed interface PhoneOtpResult {
  data class CodeSent(val verificationId: String, val message: String) : PhoneOtpResult
  data class Error(val message: String) : PhoneOtpResult
}

data class OtpSessionData(
  val firstName: String = "",
  val lastName: String = ""
)

/**
 * Clean AuthRepository abstraction.
 * UI and ViewModels depend strictly on this interface.
 * Implemented by SupabaseAuthRepository and LocalAuthRepository.
 */
interface AuthRepository {
  val authState: StateFlow<AuthState>
  suspend fun checkAuthState(): AuthState
  suspend fun login(emailOrPhone: String, password: String): AuthResult
  suspend fun register(
    firstName: String,
    lastName: String,
    email: String,
    phone: String?,
    password: String,
    dateOfBirth: String,
    gender: String
  ): AuthResult
  suspend fun sendPasswordReset(email: String): PasswordResetResult
  suspend fun logout()
  suspend fun sendPhoneOtp(phoneNumber: String): PhoneOtpResult =
    PhoneOtpResult.Error("Phone authentication is not supported.")
  suspend fun verifyPhoneOtp(
    phoneNumber: String,
    otpCode: String,
    sessionData: OtpSessionData? = null
  ): AuthResult = AuthResult.Error("Phone authentication is not supported.")
  suspend fun resendPhoneOtp(phoneNumber: String): PhoneOtpResult =
    sendPhoneOtp(phoneNumber)
  suspend fun signInWithGoogle(idToken: String): AuthResult =
    AuthResult.Error("Google sign-in is not supported.")
}

/**
 * Local implementation of AuthRepository.
 * Handles local session persistence and registration when Supabase is unreachable or in offline mode.
 * Does NOT use hardcoded demo credentials.
 */
class LocalAuthRepository(
  context: Context,
  private val dao: SocivaDao
) : AuthRepository {

  private val sessionPrefs: SharedPreferences =
    context.applicationContext.getSharedPreferences("spark_auth_session", Context.MODE_PRIVATE)

  private val accountsPrefs: SharedPreferences =
    context.applicationContext.getSharedPreferences("spark_auth_accounts", Context.MODE_PRIVATE)

  private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
  override val authState: StateFlow<AuthState> = _authState.asStateFlow()

  override suspend fun checkAuthState(): AuthState = withContext(Dispatchers.IO) {
    val userId = sessionPrefs.getString("session_user_id", null)
    val email = sessionPrefs.getString("session_email", null)
    val displayName = sessionPrefs.getString("session_display_name", null)

    val resolvedState = if (!userId.isNullOrBlank() && !email.isNullOrBlank() && !displayName.isNullOrBlank()) {
      val session = UserSession(
        userId = userId,
        email = email,
        displayName = displayName,
        phoneNumber = sessionPrefs.getString("session_phone", null),
        photoUrl = sessionPrefs.getString("session_photo_url", null),
        token = sessionPrefs.getString("session_token", null),
        isEmailVerified = sessionPrefs.getBoolean("session_email_verified", true),
        createdAt = sessionPrefs.getLong("session_created_at", System.currentTimeMillis())
      )
      AuthState.Authenticated(session)
    } else {
      AuthState.Unauthenticated
    }

    _authState.value = resolvedState
    resolvedState
  }

  override suspend fun login(emailOrPhone: String, password: String): AuthResult = withContext(Dispatchers.IO) {
    val cleanIdentifier = emailOrPhone.trim().lowercase()
    val cleanPassword = password.trim()

    if (cleanIdentifier.isBlank() || cleanPassword.isBlank()) {
      return@withContext AuthResult.Error("Please enter both your email/phone and password.")
    }

    // 1. Check registered accounts in spark_auth_accounts
    val rawAccount = accountsPrefs.getString(cleanIdentifier, null)
    if (rawAccount != null) {
      try {
        val json = JSONObject(rawAccount)
        val storedPassword = json.optString("password", "")
        if (storedPassword == cleanPassword) {
          val session = UserSession(
            userId = json.getString("userId"),
            email = json.getString("email"),
            displayName = json.getString("displayName"),
            phoneNumber = json.optString("phone").takeIf { it.isNotBlank() },
            photoUrl = json.optString("photoUrl").takeIf { it.isNotBlank() },
            token = "local_token_${UUID.randomUUID()}",
            isEmailVerified = true
          )
          persistSession(session)
          _authState.value = AuthState.Authenticated(session)
          return@withContext AuthResult.Success(session)
        } else {
          return@withContext AuthResult.Error("Incorrect password. Please try again.")
        }
      } catch (e: Exception) {
        return@withContext AuthResult.Error("Unable to process login: ${e.localizedMessage}")
      }
    }

    // 2. Check if identifier matches a user entity in database
    val allUsers = dao.getAllUsers().first()
    val matchedUser = allUsers.find {
      it.email.equals(cleanIdentifier, ignoreCase = true) ||
        (it.phone.isNotBlank() && it.phone.replace("[^0-9]".toRegex(), "") == cleanIdentifier.replace("[^0-9]".toRegex(), ""))
    }

    if (matchedUser != null) {
      // Check if there is an account entry saved by user id
      val rawUserAccount = accountsPrefs.getString(matchedUser.id, null)
      if (rawUserAccount != null) {
        val json = JSONObject(rawUserAccount)
        if (json.optString("password") == cleanPassword) {
          val session = UserSession(
            userId = matchedUser.id,
            email = matchedUser.email.ifBlank { cleanIdentifier },
            displayName = matchedUser.fullName,
            phoneNumber = matchedUser.phone.takeIf { it.isNotBlank() },
            photoUrl = matchedUser.avatarUrl.takeIf { it.isNotBlank() },
            token = "local_token_${UUID.randomUUID()}",
            isEmailVerified = true
          )
          persistSession(session)
          _authState.value = AuthState.Authenticated(session)
          return@withContext AuthResult.Success(session)
        } else {
          return@withContext AuthResult.Error("Incorrect password. Please try again.")
        }
      }
    }

    AuthResult.Error("No account found with that email or phone number. Please check your credentials or create a new account.")
  }

  override suspend fun register(
    firstName: String,
    lastName: String,
    email: String,
    phone: String?,
    password: String,
    dateOfBirth: String,
    gender: String
  ): AuthResult = withContext(Dispatchers.IO) {
    val cleanEmail = email.trim().lowercase()
    val cleanPhone = phone?.trim() ?: ""
    val cleanFirstName = firstName.trim()
    val cleanLastName = lastName.trim()
    val cleanPassword = password.trim()
    val fullName = "$cleanFirstName $cleanLastName"

    if (cleanEmail.isBlank()) {
      return@withContext AuthResult.Error("Email address is required.")
    }
    if (cleanPassword.length < 6) {
      return@withContext AuthResult.Error("Password must be at least 6 characters.")
    }

    // Check if email already registered
    if (accountsPrefs.contains(cleanEmail)) {
      return@withContext AuthResult.Error("An account with this email address already exists. Please log in.")
    }

    val newUserId = "user_" + UUID.randomUUID().toString().take(8)
    val username = cleanEmail.substringBefore("@").replace("[^a-z0-9_]".toRegex(), "").ifBlank { "user" }

    // Save account in local accounts registry
    val accountJson = JSONObject().apply {
      put("userId", newUserId)
      put("email", cleanEmail)
      put("phone", cleanPhone)
      put("displayName", fullName)
      put("firstName", cleanFirstName)
      put("lastName", cleanLastName)
      put("password", cleanPassword)
      put("dateOfBirth", dateOfBirth)
      put("gender", gender)
      put("createdAt", System.currentTimeMillis())
    }

    accountsPrefs.edit()
      .putString(cleanEmail, accountJson.toString())
      .apply()

    if (cleanPhone.isNotBlank()) {
      accountsPrefs.edit().putString(cleanPhone, accountJson.toString()).apply()
    }
    accountsPrefs.edit().putString(newUserId, accountJson.toString()).apply()

    // Create UserEntity in Room database
    val newUserEntity = UserEntity(
      id = newUserId,
      username = username,
      fullName = fullName,
      firstName = cleanFirstName,
      lastName = cleanLastName,
      email = cleanEmail,
      phone = cleanPhone,
      dateOfBirth = dateOfBirth,
      gender = gender,
      joinedDate = "Joined recently",
      isOnline = true
    )
    dao.insertUser(newUserEntity)

    // Create and persist active session
    val session = UserSession(
      userId = newUserId,
      email = cleanEmail,
      displayName = fullName,
      phoneNumber = cleanPhone.takeIf { it.isNotBlank() },
      photoUrl = null,
      token = "local_token_${UUID.randomUUID()}",
      isEmailVerified = true
    )
    persistSession(session)
    _authState.value = AuthState.Authenticated(session)

    AuthResult.Success(session)
  }

  override suspend fun sendPasswordReset(email: String): PasswordResetResult = withContext(Dispatchers.IO) {
    val cleanEmail = email.trim().lowercase()
    if (cleanEmail.isBlank()) {
      return@withContext PasswordResetResult.Error("Please enter your email address.")
    }

    // Return clear message noting Supabase connection is pending
    PasswordResetResult.Success(
      "Password reset request recorded for $cleanEmail. Real email delivery will activate once Supabase Authentication is connected."
    )
  }

  override suspend fun logout() = withContext(Dispatchers.IO) {
    sessionPrefs.edit().clear().apply()
    _authState.value = AuthState.Unauthenticated
  }

  private fun persistSession(session: UserSession) {
    sessionPrefs.edit()
      .putString("session_user_id", session.userId)
      .putString("session_email", session.email)
      .putString("session_display_name", session.displayName)
      .putString("session_phone", session.phoneNumber)
      .putString("session_photo_url", session.photoUrl)
      .putString("session_token", session.token)
      .putBoolean("session_email_verified", session.isEmailVerified)
      .putLong("session_created_at", session.createdAt)
      .apply()
  }
}
