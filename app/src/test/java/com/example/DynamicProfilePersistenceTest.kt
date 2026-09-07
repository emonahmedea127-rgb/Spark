package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sociva.data.local.SocivaDatabase
import com.example.sociva.data.local.UserEntity
import com.example.sociva.data.model.User
import com.example.sociva.data.repository.SocivaRepository
import com.example.sociva.ui.components.CountryHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DynamicProfilePersistenceTest {

  private lateinit var db: SocivaDatabase
  private lateinit var repository: SocivaRepository

  private val initialUser = UserEntity(
    id = "user_me",
    fullName = "Original Name",
    username = "originaluser",
    avatarUrl = "https://example.com/avatar.jpg",
    coverUrl = "https://example.com/cover.jpg",
    bio = "Original Bio",
    currentCity = "",
    hometown = "",
    country = "",
    dateOfBirth = "",
    workplace = "",
    college = ""
  )

  @Before
  fun setup() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, SocivaDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = SocivaRepository(db.socivaDao())
    db.socivaDao().insertUser(initialUser)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun testDynamicProfilePersistenceAndImmediateRefresh() = runBlocking {
    // 1. Verify initial user in database
    val fetchedInitial = repository.getUser("user_me").first()
    assertNotNull(fetchedInitial)
    assertEquals("Original Name", fetchedInitial?.fullName)
    assertEquals("", fetchedInitial?.currentCity)
    assertEquals("", fetchedInitial?.hometown)
    assertEquals("", fetchedInitial?.dateOfBirth)

    // 2. User edits profile with comprehensive dynamic data
    val updatedUser = fetchedInitial!!.copy(
      fullName = "Alex Mercer",
      username = "alexmercer",
      bio = "Exploring Android & Jetpack Compose",
      firstName = "Alex",
      lastName = "Mercer",
      pronouns = "they/them",
      nickname = "Al",
      dateOfBirth = "1995-06-15",
      gender = "Non-binary",
      currentCity = "San Francisco",
      currentRegion = "California",
      currentCountryCode = "US",
      currentLatitude = 37.7749,
      currentLongitude = -122.4194,
      hometown = "Seattle",
      hometownRegion = "Washington",
      hometownCountryCode = "US",
      hometownLatitude = 47.6062,
      hometownLongitude = -122.3321,
      country = "United States",
      countryCode = "US",
      workplace = "Innovatech Labs",
      workPosition = "Staff Engineer",
      workStartDate = "2021",
      college = "Stanford University",
      degree = "M.S.",
      fieldOfStudy = "Computer Science",
      graduationYear = "2019",
      relationshipStatus = "In a relationship",
      website = "https://alexmercer.dev",
      email = "alex@example.com",
      phone = "+1 555-0199"
    )

    // 3. Save to database using repository single source of truth
    repository.updateFullUserProfile(updatedUser)

    // 4. Fetch updated flow directly from database
    val refreshed = repository.getUser("user_me").first()
    assertNotNull(refreshed)
    assertEquals("Alex Mercer", refreshed?.fullName)
    assertEquals("alexmercer", refreshed?.username)
    assertEquals("Exploring Android & Jetpack Compose", refreshed?.bio)
    assertEquals("Alex", refreshed?.firstName)
    assertEquals("Mercer", refreshed?.lastName)
    assertEquals("they/them", refreshed?.pronouns)
    assertEquals("1995-06-15", refreshed?.dateOfBirth)
    assertEquals("Non-binary", refreshed?.gender)
    assertEquals("San Francisco", refreshed?.currentCity)
    assertEquals("California", refreshed?.currentRegion)
    assertEquals("US", refreshed?.currentCountryCode)
    assertEquals(37.7749, refreshed?.currentLatitude ?: 0.0, 0.001)
    assertEquals(-122.4194, refreshed?.currentLongitude ?: 0.0, 0.001)
    assertEquals("Seattle", refreshed?.hometown)
    assertEquals("Washington", refreshed?.hometownRegion)
    assertEquals("US", refreshed?.hometownCountryCode)
    assertEquals("United States", refreshed?.country)
    assertEquals("US", refreshed?.countryCode)
    assertEquals("Innovatech Labs", refreshed?.workplace)
    assertEquals("Staff Engineer", refreshed?.workPosition)
    assertEquals("Stanford University", refreshed?.college)
    assertEquals("M.S.", refreshed?.degree)
    assertEquals("In a relationship", refreshed?.relationshipStatus)
    assertEquals("https://alexmercer.dev", refreshed?.website)
    assertEquals("alex@example.com", refreshed?.email)
  }

  @Test
  fun testCountryHelperLookupAndFlag() {
    val us = CountryHelper.findCountryByCode("US")
    assertNotNull(us)
    assertEquals("United States", us?.name)
    assertEquals("🇺🇸", us?.flag)

    val canadaFlag = CountryHelper.getFlagForCountry("Canada")
    assertEquals("🇨🇦", canadaFlag)

    val gbFlag = CountryHelper.getFlagForCountry("GB")
    assertEquals("🇬🇧", gbFlag)
  }

  @Test
  fun testEmptyFieldsDoNotProduceFakeData() = runBlocking {
    val minimalUser = UserEntity(
      id = "user_minimal",
      fullName = "Minimal User",
      username = "minimal",
      avatarUrl = "",
      coverUrl = "",
      bio = "",
      currentCity = "",
      hometown = "",
      country = "",
      dateOfBirth = "",
      workplace = "",
      college = "",
      school = "",
      relationshipStatus = ""
    )
    db.socivaDao().insertUser(minimalUser)

    val fetched = repository.getUser("user_minimal").first()
    assertNotNull(fetched)
    assertTrue(fetched?.currentCity.isNullOrBlank())
    assertTrue(fetched?.hometown.isNullOrBlank())
    assertTrue(fetched?.country.isNullOrBlank())
    assertTrue(fetched?.workplace.isNullOrBlank())
    assertTrue(fetched?.college.isNullOrBlank())
    assertTrue(fetched?.relationshipStatus.isNullOrBlank())
  }
}
