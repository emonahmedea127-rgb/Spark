package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sociva.data.local.SocivaDatabase
import com.example.sociva.data.repository.AuthResult
import com.example.sociva.data.repository.AuthState
import com.example.sociva.data.repository.PasswordResetResult
import com.example.sociva.data.repository.PhoneOtpResult
import com.example.sociva.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseAuthRobolectricTest {

  private lateinit var context: Context
  private lateinit var database: SocivaDatabase
  private lateinit var authRepository: SupabaseAuthRepository

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("spark_auth_session", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("spark_auth_accounts", Context.MODE_PRIVATE).edit().clear().commit()

    database = Room.inMemoryDatabaseBuilder(context, SocivaDatabase::class.java)
      .allowMainThreadQueries()
      .build()

    authRepository = SupabaseAuthRepository(context, database.socivaDao())
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `startup auth state is Unauthenticated on fresh launch`() = runBlocking {
    val state = authRepository.checkAuthState()
    assertTrue(state is AuthState.Unauthenticated)
    assertEquals(AuthState.Unauthenticated, authRepository.authState.value)
  }

  @Test
  fun `register account successfully creates user and sets state to Authenticated`() = runBlocking {
    val result = authRepository.register(
      firstName = "Marcus",
      lastName = "Holloway",
      email = "marcus.h@example.com",
      phone = "+15551234567",
      password = "Password123!",
      dateOfBirth = "May 12, 1995",
      gender = "Male"
    )

    assertTrue(result is AuthResult.Success)
    val session = (result as AuthResult.Success).session
    assertEquals("marcus.h@example.com", session.email)
    assertEquals("Marcus Holloway", session.displayName)
    assertTrue(session.userId.isNotBlank())
    assertTrue(authRepository.authState.value is AuthState.Authenticated)

    // Verify persisted in Room user table
    val localUser = database.socivaDao().getUserById(session.userId).first()
    assertNotNull(localUser)
    assertEquals("Marcus Holloway", localUser?.fullName)
  }

  @Test
  fun `duplicate registration returns friendly error`() = runBlocking {
    authRepository.register(
      firstName = "John",
      lastName = "Doe",
      email = "john.doe@example.com",
      phone = null,
      password = "Password123!",
      dateOfBirth = "Jan 1, 1990",
      gender = "Male"
    )

    val dupResult = authRepository.register(
      firstName = "Johnny",
      lastName = "Doe",
      email = "john.doe@example.com",
      phone = null,
      password = "NewPassword456!",
      dateOfBirth = "Jan 1, 1990",
      gender = "Male"
    )

    assertTrue(dupResult is AuthResult.Error)
    assertTrue((dupResult as AuthResult.Error).message.contains("already exists", ignoreCase = true))
  }

  @Test
  fun `login with correct credentials succeeds and restores session`() = runBlocking {
    authRepository.register(
      firstName = "Elena",
      lastName = "Rostova",
      email = "elena.r@example.com",
      phone = null,
      password = "SecurePassword99",
      dateOfBirth = "Aug 15, 1998",
      gender = "Female"
    )

    authRepository.logout()
    assertTrue(authRepository.authState.value is AuthState.Unauthenticated)

    val result = authRepository.login("elena.r@example.com", "SecurePassword99")
    assertTrue(result is AuthResult.Success)
    val session = (result as AuthResult.Success).session
    assertEquals("Elena Rostova", session.displayName)
    assertTrue(authRepository.authState.value is AuthState.Authenticated)

    // Check auth state persistence on app relaunch
    val recheckState = authRepository.checkAuthState()
    assertTrue(recheckState is AuthState.Authenticated)
  }

  @Test
  fun `forgot password returns clear pending Supabase confirmation`() = runBlocking {
    val result = authRepository.sendPasswordReset("elena.r@example.com")
    assertTrue(result is PasswordResetResult.Success)
    val successMsg = (result as PasswordResetResult.Success).message
    assertTrue(successMsg.contains("Password reset", ignoreCase = true))
  }

  @Test
  fun `logout clears session and sets state to Unauthenticated`() = runBlocking {
    authRepository.register(
      firstName = "David",
      lastName = "Kim",
      email = "david.kim@example.com",
      phone = null,
      password = "Password888",
      dateOfBirth = "Jan 1, 2000",
      gender = "Male"
    )

    assertTrue(authRepository.authState.value is AuthState.Authenticated)
    authRepository.logout()
    assertTrue(authRepository.authState.value is AuthState.Unauthenticated)

    val restored = authRepository.checkAuthState()
    assertTrue(restored is AuthState.Unauthenticated)
  }

  @Test
  fun `phone OTP flow returns code sent and verifies successfully`() = runBlocking {
    val otpResult = authRepository.sendPhoneOtp("+15559876543")
    assertTrue(otpResult is PhoneOtpResult.CodeSent)

    val verifyResult = authRepository.verifyPhoneOtp("+15559876543", "123456", null)
    assertTrue(verifyResult is AuthResult.Success)
    assertTrue(authRepository.authState.value is AuthState.Authenticated)
  }
}
