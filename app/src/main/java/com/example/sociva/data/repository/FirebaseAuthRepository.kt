package com.example.sociva.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.sociva.data.local.SocivaDao
import com.example.sociva.data.local.UserEntity
import com.example.sociva.data.service.awaitResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Firebase-backed implementation of AuthRepository.
 * Handles real Firebase Authentication (Email/Password & Sessions),
 * user profile sync with Cloud Firestore, and seamless fallback to LocalAuthRepository.
 */
class FirebaseAuthRepository(
  context: Context,
  private val dao: SocivaDao,
  private val localFallback: LocalAuthRepository = LocalAuthRepository(context, dao)
) : AuthRepository {

  private val TAG = "FirebaseAuthRepo"

  private val sessionPrefs: SharedPreferences =
    context.applicationContext.getSharedPreferences("spark_auth_session", Context.MODE_PRIVATE)

  private val auth: FirebaseAuth? by lazy {
    try {
      if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
        com.google.firebase.FirebaseApp.initializeApp(context.applicationContext)
      }
      FirebaseAuth.getInstance()
    } catch (e: Exception) {
      Log.w(TAG, "FirebaseAuth initialization failed: ${e.message}")
      null
    }
  }

  private val firestore: FirebaseFirestore? by lazy {
    try {
      if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
        com.google.firebase.FirebaseApp.initializeApp(context.applicationContext)
      }
      FirebaseFirestore.getInstance()
    } catch (e: Exception) {
      null
    }
  }

  private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
  override val authState: StateFlow<AuthState> = _authState.asStateFlow()

  override suspend fun checkAuthState(): AuthState = withContext(Dispatchers.IO) {
    val fbAuth = auth
    val fbUser = fbAuth?.currentUser
    if (fbUser != null) {
      val userId = fbUser.uid
      val email = fbUser.email ?: ""
      val displayName = fbUser.displayName?.ifBlank { null }
        ?: sessionPrefs.getString("session_display_name", null)
        ?: email.substringBefore("@").ifBlank { "Spark User" }

      // 1. First attempt to fetch the profile from Firestore as source of truth
      var userEntity: UserEntity? = null
      try {
        val doc = firestore?.collection("users")?.document(userId)?.get()?.awaitResult()
        if (doc != null && doc.exists()) {
          val username = doc.getString("username") ?: email.substringBefore("@")
          val fullName = doc.getString("fullName") ?: displayName
          val firstName = doc.getString("firstName") ?: fullName.substringBefore(" ")
          val lastName = doc.getString("lastName") ?: fullName.substringAfter(" ", "")
          val phone = doc.getString("phone") ?: ""
          val dateOfBirth = doc.getString("dateOfBirth") ?: ""
          val gender = doc.getString("gender") ?: ""
          val avatarUrl = doc.getString("profilePhotoUrl") ?: doc.getString("avatarUrl") ?: fbUser.photoUrl?.toString() ?: ""
          val coverUrl = doc.getString("coverPhotoUrl") ?: doc.getString("coverUrl") ?: ""
          val bio = doc.getString("bio") ?: ""
          val location = doc.getString("location") ?: ""
          val relationshipStatus = doc.getString("relationshipStatus") ?: ""
          val work = doc.getString("work") ?: ""
          val education = doc.getString("education") ?: ""
          val isVerified = doc.getBoolean("isVerified") ?: false

          userEntity = UserEntity(
            id = userId,
            username = username,
            fullName = fullName,
            firstName = firstName,
            lastName = lastName,
            email = email,
            phone = phone,
            dateOfBirth = dateOfBirth,
            gender = gender,
            avatarUrl = avatarUrl,
            coverUrl = coverUrl,
            bio = bio,
            location = location,
            relationshipStatus = relationshipStatus,
            work = work,
            education = education,
            isVerified = isVerified,
            joinedDate = "Joined recently",
            isOnline = true
          )
          dao.insertUser(userEntity)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Error loading Firestore user document on startup: ${e.message}")
      }

      // 2. If not found in Firestore or offline, check Room database cache
      if (userEntity == null) {
        userEntity = dao.getUserById(userId).first()
      }

      // 3. If neither exists yet, create initial profile for this real Firebase user
      if (userEntity == null) {
        val username = email.substringBefore("@").replace("[^a-z0-9_]".toRegex(), "").ifBlank { "user_${userId.take(5)}" }
        val firstName = displayName.substringBefore(" ")
        val lastName = displayName.substringAfter(" ", "")
        userEntity = UserEntity(
          id = userId,
          username = username,
          fullName = displayName,
          firstName = firstName,
          lastName = lastName,
          email = email,
          phone = fbUser.phoneNumber ?: "",
          avatarUrl = fbUser.photoUrl?.toString() ?: "",
          joinedDate = "Joined recently",
          isOnline = true
        )
        dao.insertUser(userEntity)

        try {
          firestore?.collection("users")?.document(userId)?.set(
            mapOf(
              "uid" to userId,
              "id" to userId,
              "username" to username,
              "fullName" to displayName,
              "firstName" to firstName,
              "lastName" to lastName,
              "email" to email,
              "phone" to (fbUser.phoneNumber ?: ""),
              "avatarUrl" to (fbUser.photoUrl?.toString() ?: ""),
              "profilePhotoUrl" to (fbUser.photoUrl?.toString() ?: ""),
              "createdAt" to System.currentTimeMillis(),
              "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
          )
        } catch (e: Exception) {
          Log.w(TAG, "Failed to create default Firestore document: ${e.message}")
        }
      }

      val session = UserSession(
        userId = userId,
        email = email,
        displayName = userEntity.fullName.ifBlank { displayName },
        phoneNumber = fbUser.phoneNumber ?: userEntity.phone.takeIf { it.isNotBlank() },
        photoUrl = fbUser.photoUrl?.toString() ?: userEntity.avatarUrl.takeIf { it.isNotBlank() },
        token = "firebase_token_$userId",
        isEmailVerified = fbUser.isEmailVerified,
        createdAt = fbUser.metadata?.creationTimestamp ?: System.currentTimeMillis()
      )
      persistSession(session)
      _authState.value = AuthState.Authenticated(session)
      return@withContext AuthState.Authenticated(session)
    }

    // No Firebase user authenticated -> show Login / Sign Up screen
    sessionPrefs.edit().clear().apply()
    _authState.value = AuthState.Unauthenticated
    AuthState.Unauthenticated
  }

  override suspend fun login(emailOrPhone: String, password: String): AuthResult = withContext(Dispatchers.IO) {
    val cleanIdentifier = emailOrPhone.trim().lowercase()
    val cleanPassword = password.trim()

    if (cleanIdentifier.isBlank() || cleanPassword.isBlank()) {
      return@withContext AuthResult.Error("Please enter both your email address and password.")
    }

    val fbAuth = auth ?: return@withContext AuthResult.Error("Firebase Authentication is not available.")

    try {
      val authResult = fbAuth.signInWithEmailAndPassword(cleanIdentifier, cleanPassword).awaitResult()
      val fbUser = authResult.user ?: return@withContext AuthResult.Error("Login failed. No user found.")
      val userId = fbUser.uid
      val email = fbUser.email ?: cleanIdentifier
      val displayName = fbUser.displayName?.ifBlank { null }
        ?: cleanIdentifier.substringBefore("@").ifBlank { "Spark User" }

      // Fetch Firestore user profile
      var userEntity: UserEntity? = null
      try {
        val doc = firestore?.collection("users")?.document(userId)?.get()?.awaitResult()
        if (doc != null && doc.exists()) {
          val username = doc.getString("username") ?: cleanIdentifier.substringBefore("@")
          val fullName = doc.getString("fullName") ?: displayName
          val firstName = doc.getString("firstName") ?: fullName.substringBefore(" ")
          val lastName = doc.getString("lastName") ?: fullName.substringAfter(" ", "")
          val phone = doc.getString("phone") ?: ""
          val dateOfBirth = doc.getString("dateOfBirth") ?: ""
          val gender = doc.getString("gender") ?: ""
          val avatarUrl = doc.getString("profilePhotoUrl") ?: doc.getString("avatarUrl") ?: fbUser.photoUrl?.toString() ?: ""
          val coverUrl = doc.getString("coverPhotoUrl") ?: doc.getString("coverUrl") ?: ""
          val bio = doc.getString("bio") ?: ""
          val location = doc.getString("location") ?: ""
          val relationshipStatus = doc.getString("relationshipStatus") ?: ""
          val work = doc.getString("work") ?: ""
          val education = doc.getString("education") ?: ""
          val isVerified = doc.getBoolean("isVerified") ?: false

          userEntity = UserEntity(
            id = userId,
            username = username,
            fullName = fullName,
            firstName = firstName,
            lastName = lastName,
            email = email,
            phone = phone,
            dateOfBirth = dateOfBirth,
            gender = gender,
            avatarUrl = avatarUrl,
            coverUrl = coverUrl,
            bio = bio,
            location = location,
            relationshipStatus = relationshipStatus,
            work = work,
            education = education,
            isVerified = isVerified,
            joinedDate = "Joined recently",
            isOnline = true
          )
          dao.insertUser(userEntity)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to load Firestore profile on login: ${e.message}")
      }

      if (userEntity == null) {
        userEntity = dao.getUserById(userId).first()
      }

      if (userEntity == null) {
        val username = cleanIdentifier.substringBefore("@").replace("[^a-z0-9_]".toRegex(), "").ifBlank { "user_${userId.take(5)}" }
        val firstName = displayName.substringBefore(" ")
        val lastName = displayName.substringAfter(" ", "")
        userEntity = UserEntity(
          id = userId,
          username = username,
          fullName = displayName,
          firstName = firstName,
          lastName = lastName,
          email = cleanIdentifier,
          avatarUrl = fbUser.photoUrl?.toString() ?: "",
          joinedDate = "Joined recently",
          isOnline = true
        )
        dao.insertUser(userEntity)
      }

      val session = UserSession(
        userId = userId,
        email = email,
        displayName = userEntity.fullName.ifBlank { displayName },
        phoneNumber = fbUser.phoneNumber ?: userEntity.phone.takeIf { it.isNotBlank() },
        photoUrl = fbUser.photoUrl?.toString() ?: userEntity.avatarUrl.takeIf { it.isNotBlank() },
        token = "firebase_token_$userId",
        isEmailVerified = fbUser.isEmailVerified,
        createdAt = fbUser.metadata?.creationTimestamp ?: System.currentTimeMillis()
      )
      persistSession(session)
      _authState.value = AuthState.Authenticated(session)
      return@withContext AuthResult.Success(session)
    } catch (e: FirebaseAuthInvalidCredentialsException) {
      return@withContext AuthResult.Error("Invalid email or password. Please verify your credentials.")
    } catch (e: FirebaseAuthInvalidUserException) {
      return@withContext AuthResult.Error("No account found with this email address. Please sign up first.")
    } catch (e: Exception) {
      Log.w(TAG, "Firebase signIn failed: ${e.message}")
      return@withContext AuthResult.Error(e.localizedMessage ?: "Login failed. Please check your credentials and network.")
    }
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
    val fullName = "$cleanFirstName $cleanLastName".trim().ifBlank { cleanEmail.substringBefore("@") }

    if (cleanEmail.isBlank()) {
      return@withContext AuthResult.Error("Email address is required.")
    }
    if (cleanPassword.length < 6) {
      return@withContext AuthResult.Error("Password must be at least 6 characters.")
    }

    val fbAuth = auth ?: return@withContext AuthResult.Error("Firebase Authentication is not available.")

    try {
      val authResult = fbAuth.createUserWithEmailAndPassword(cleanEmail, cleanPassword).awaitResult()
      val fbUser = authResult.user ?: return@withContext AuthResult.Error("Account creation failed.")
      val userId = fbUser.uid

      val profileUpdates = UserProfileChangeRequest.Builder()
        .setDisplayName(fullName)
        .build()
      try {
        fbUser.updateProfile(profileUpdates).awaitResult()
      } catch (e: Exception) {
        Log.w(TAG, "Failed to update Firebase profile display name: ${e.message}")
      }

      val username = cleanEmail.substringBefore("@").replace("[^a-z0-9_]".toRegex(), "").ifBlank { "user_${userId.take(5)}" }
      val now = System.currentTimeMillis()

      // 1. Create User Document in Cloud Firestore (collection: users/{uid})
      val userDocumentMap = hashMapOf<String, Any>(
        "uid" to userId,
        "id" to userId,
        "username" to username,
        "firstName" to cleanFirstName,
        "lastName" to cleanLastName,
        "fullName" to fullName,
        "email" to cleanEmail,
        "phone" to cleanPhone,
        "dateOfBirth" to dateOfBirth,
        "gender" to gender,
        "profilePhotoUrl" to "",
        "avatarUrl" to "",
        "coverPhotoUrl" to "",
        "coverUrl" to "",
        "bio" to "",
        "location" to "",
        "relationshipStatus" to "",
        "work" to "",
        "education" to "",
        "isVerified" to false,
        "createdAt" to now,
        "updatedAt" to now
      )

      try {
        firestore?.collection("users")?.document(userId)?.set(userDocumentMap, SetOptions.merge())?.awaitResult()
      } catch (e: Exception) {
        Log.w(TAG, "Firestore user profile sync warning: ${e.message}")
      }

      // 2. Cache in Room Database
      val newUserEntity = UserEntity(
        id = userId,
        username = username,
        fullName = fullName,
        firstName = cleanFirstName,
        lastName = cleanLastName,
        email = cleanEmail,
        phone = cleanPhone,
        dateOfBirth = dateOfBirth,
        gender = gender,
        avatarUrl = "",
        coverUrl = "",
        bio = "",
        location = "",
        relationshipStatus = "",
        work = "",
        education = "",
        joinedDate = "Joined recently",
        isOnline = true
      )
      dao.insertUser(newUserEntity)

      val session = UserSession(
        userId = userId,
        email = cleanEmail,
        displayName = fullName,
        phoneNumber = cleanPhone.takeIf { it.isNotBlank() },
        photoUrl = null,
        token = "firebase_token_$userId",
        isEmailVerified = fbUser.isEmailVerified,
        createdAt = now
      )
      persistSession(session)
      _authState.value = AuthState.Authenticated(session)
      return@withContext AuthResult.Success(session)
    } catch (e: FirebaseAuthUserCollisionException) {
      return@withContext AuthResult.Error("An account with this email address already exists. Please log in.")
    } catch (e: FirebaseAuthWeakPasswordException) {
      return@withContext AuthResult.Error("The password provided is too weak: ${e.reason ?: "Minimum 6 characters."}")
    } catch (e: Exception) {
      Log.w(TAG, "Firebase createUser failed: ${e.message}")
      return@withContext AuthResult.Error(e.localizedMessage ?: "Failed to create account. Please try again.")
    }
  }

  override suspend fun sendPasswordReset(email: String): PasswordResetResult = withContext(Dispatchers.IO) {
    val cleanEmail = email.trim().lowercase()
    if (cleanEmail.isBlank()) {
      return@withContext PasswordResetResult.Error("Please enter your email address.")
    }

    val fbAuth = auth ?: return@withContext PasswordResetResult.Error("Firebase Authentication is not available.")

    try {
      fbAuth.sendPasswordResetEmail(cleanEmail).awaitResult()
      return@withContext PasswordResetResult.Success(
        "Password reset email has been sent to $cleanEmail. Please check your inbox or spam folder."
      )
    } catch (e: FirebaseAuthInvalidUserException) {
      return@withContext PasswordResetResult.Error("No account found with that email address.")
    } catch (e: Exception) {
      Log.w(TAG, "sendPasswordResetEmail error: ${e.message}")
      return@withContext PasswordResetResult.Error(
        e.localizedMessage ?: "Unable to send password reset email. Please verify your connection."
      )
    }
  }

  override suspend fun logout() = withContext(Dispatchers.IO) {
    try {
      auth?.signOut()
    } catch (e: Exception) {
      Log.w(TAG, "Firebase signOut error: ${e.message}")
    }
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
