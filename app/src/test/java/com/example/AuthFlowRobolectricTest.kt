package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sociva.data.local.SocivaDatabase
import com.example.sociva.data.repository.AuthResult
import com.example.sociva.data.repository.AuthState
import com.example.sociva.data.repository.LocalAuthRepository
import com.example.sociva.data.repository.PasswordResetResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthFlowRobolectricTest {

  private lateinit var context: Context
  private lateinit var database: SocivaDatabase
  private lateinit var authRepository: LocalAuthRepository

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("spark_auth_session", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("spark_auth_accounts", Context.MODE_PRIVATE).edit().clear().commit()

    database = Room.inMemoryDatabaseBuilder(context, SocivaDatabase::class.java)
      .allowMainThreadQueries()
      .build()

    authRepository = LocalAuthRepository(context, database.socivaDao())
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
      dateOfBirth = "Jul 15, 1998",
      gender = "Male"
    )

    assertTrue(result is AuthResult.Success)
    val session = (result as AuthResult.Success).session
    assertEquals("Marcus Holloway", session.displayName)
    assertEquals("marcus.h@example.com", session.email)

    // Current auth state must now be Authenticated
    val currentAuthState = authRepository.authState.value
    assertTrue(currentAuthState is AuthState.Authenticated)
    assertEquals(session.userId, (currentAuthState as AuthState.Authenticated).session.userId)
  }

  @Test
  fun `login with wrong password returns descriptive Error and does not crash`() = runBlocking {
    // First register
    authRepository.register(
      firstName = "Elena",
      lastName = "Rostova",
      email = "elena.r@example.com",
      phone = null,
      password = "SecurePassword99",
      dateOfBirth = "Apr 20, 1995",
      gender = "Female"
    )
    authRepository.logout()

    // Try incorrect password
    val result = authRepository.login("elena.r@example.com", "WrongPassword")
    assertTrue(result is AuthResult.Error)
    val errorMsg = (result as AuthResult.Error).message
    assertTrue(errorMsg.contains("Incorrect password", ignoreCase = true))
    assertTrue(authRepository.authState.value is AuthState.Unauthenticated)
  }

  @Test
  fun `login with correct credentials succeeds and restores session`() = runBlocking {
    authRepository.register(
      firstName = "Elena",
      lastName = "Rostova",
      email = "elena.r@example.com",
      phone = "+15559876543",
      password = "SecurePassword99",
      dateOfBirth = "Apr 20, 1995",
      gender = "Female"
    )
    authRepository.logout()

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
  fun `forgot password returns clear pending Firebase confirmation without pretending email was sent`() = runBlocking {
    val result = authRepository.sendPasswordReset("elena.r@example.com")
    assertTrue(result is PasswordResetResult.Success)
    val successMsg = (result as PasswordResetResult.Success).message
    assertTrue(successMsg.contains("Firebase Authentication", ignoreCase = true))
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

    // Recheck after restart
    val state = authRepository.checkAuthState()
    assertTrue(state is AuthState.Unauthenticated)
  }
}
