package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sociva.data.local.SocivaDatabase
import com.example.sociva.data.local.UserEntity
import com.example.sociva.data.local.UserSettingsEntity
import com.example.sociva.data.model.FriendStatus
import com.example.sociva.data.repository.SocivaRepository
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
class ProfileVisitorSystemTest {

  private lateinit var db: SocivaDatabase
  private lateinit var repository: SocivaRepository

  private val userOwner = UserEntity(
    id = "user_owner",
    fullName = "Owner User",
    username = "owner",
    avatarUrl = "https://example.com/owner.jpg",
    coverUrl = "https://example.com/cover_owner.jpg",
    bio = "Owner Bio",
    followersCount = 10,
    followingCount = 5,
    friendsCount = 3
  )

  private val userVisitor1 = UserEntity(
    id = "user_visitor_1",
    fullName = "Alice Smith",
    username = "alice",
    avatarUrl = "https://example.com/alice.jpg",
    coverUrl = "https://example.com/cover_alice.jpg",
    bio = "Alice Bio",
    followersCount = 50,
    followingCount = 20,
    friendsCount = 12
  )

  private val userVisitor2 = UserEntity(
    id = "user_visitor_2",
    fullName = "Bob Jones",
    username = "bob",
    avatarUrl = "https://example.com/bob.jpg",
    coverUrl = "https://example.com/cover_bob.jpg",
    bio = "Bob Bio",
    followersCount = 15,
    followingCount = 8,
    friendsCount = 4
  )

  @Before
  fun setup() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, SocivaDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = SocivaRepository(db.socivaDao())

    db.socivaDao().insertUsers(listOf(userOwner, userVisitor1, userVisitor2))
    db.socivaDao().insertOrUpdateUserSettings(
      UserSettingsEntity(userId = "user_owner", profileViewHistoryEnabled = true)
    )
    db.socivaDao().insertOrUpdateUserSettings(
      UserSettingsEntity(userId = "user_visitor_1", profileViewHistoryEnabled = true)
    )
    db.socivaDao().insertOrUpdateUserSettings(
      UserSettingsEntity(userId = "user_visitor_2", profileViewHistoryEnabled = true)
    )
  }

  @After
  fun teardown() {
    db.close()
  }

  @Test
  fun `test visit recording and view counter increments`() = runBlocking {
    // Initially zero
    val initialStats = repository.getProfileViewStatsFlow("user_owner").first()
    assertEquals(0, initialStats.totalCount)

    // Visitor 1 visits Owner
    repository.recordProfileVisit(viewerUserId = "user_visitor_1", viewedUserId = "user_owner")

    val updatedStats = repository.getProfileViewStatsFlow("user_owner").first()
    assertEquals(1, updatedStats.totalCount)
    assertEquals(1, updatedStats.todayCount)
    assertEquals(1, updatedStats.unseenCount)

    // Check visitors list
    val visitors = repository.getProfileVisitorsFlow("user_owner").first()
    assertEquals(1, visitors.size)
    assertEquals("user_visitor_1", visitors[0].user.id)
    assertEquals("Alice Smith", visitors[0].user.fullName)
    assertFalse(visitors[0].isSeen)
  }

  @Test
  fun `test 30-minute deduplication prevents duplicate counts`() = runBlocking {
    // Visitor 1 visits Owner
    repository.recordProfileVisit(viewerUserId = "user_visitor_1", viewedUserId = "user_owner")

    // Immediate repeat visit from same user within 30 minutes
    repository.recordProfileVisit(viewerUserId = "user_visitor_1", viewedUserId = "user_owner")

    val stats = repository.getProfileViewStatsFlow("user_owner").first()
    assertEquals(1, stats.totalCount)

    val visitors = repository.getProfileVisitorsFlow("user_owner").first()
    assertEquals(1, visitors.size)
  }

  @Test
  fun `test self-views are not recorded`() = runBlocking {
    // Owner visits own profile
    repository.recordProfileVisit(viewerUserId = "user_owner", viewedUserId = "user_owner")

    val stats = repository.getProfileViewStatsFlow("user_owner").first()
    assertEquals(0, stats.totalCount)

    val visitors = repository.getProfileVisitorsFlow("user_owner").first()
    assertEquals(0, visitors.size)
  }

  @Test
  fun `test blocked user visits are not recorded`() = runBlocking {
    // Owner blocks visitor 1
    repository.blockUser("user_owner", "user_visitor_1")

    // Visitor 1 visits Owner
    repository.recordProfileVisit(viewerUserId = "user_visitor_1", viewedUserId = "user_owner")

    val stats = repository.getProfileViewStatsFlow("user_owner").first()
    assertEquals(0, stats.totalCount)

    val visitors = repository.getProfileVisitorsFlow("user_owner").first()
    assertEquals(0, visitors.size)
  }

  @Test
  fun `test privacy setting disabled makes visit anonymous`() = runBlocking {
    // Visitor 2 disables Profile View History
    repository.updateProfileViewHistorySetting("user_visitor_2", false)

    // Visitor 2 visits Owner
    repository.recordProfileVisit(viewerUserId = "user_visitor_2", viewedUserId = "user_owner")

    // Counter still increments neutrally
    val stats = repository.getProfileViewStatsFlow("user_owner").first()
    assertEquals(1, stats.totalCount)

    // But owner's visitor list does NOT identify Visitor 2
    val visitors = repository.getProfileVisitorsFlow("user_owner").first()
    val visitor2Item = visitors.find { it.user.id == "user_visitor_2" }
    assertNull("Visitor 2 with history disabled must not be identified in visitor list", visitor2Item)
  }

  @Test
  fun `test markProfileVisitorsSeen clears unseen count`() = runBlocking {
    repository.recordProfileVisit(viewerUserId = "user_visitor_1", viewedUserId = "user_owner")

    val beforeStats = repository.getProfileViewStatsFlow("user_owner").first()
    assertEquals(1, beforeStats.unseenCount)

    // Mark seen
    repository.markProfileVisitorsSeen("user_owner")

    val afterStats = repository.getProfileViewStatsFlow("user_owner").first()
    assertEquals(0, afterStats.unseenCount)
    assertEquals(1, afterStats.totalCount)

    val visitors = repository.getProfileVisitorsFlow("user_owner").first()
    assertTrue(visitors[0].isSeen)
  }
}
