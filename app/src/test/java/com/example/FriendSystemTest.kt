package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sociva.data.local.SocivaDatabase
import com.example.sociva.data.local.UserEntity
import com.example.sociva.data.model.FriendStatus
import com.example.sociva.data.model.NotificationType
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
class FriendSystemTest {

  private lateinit var db: SocivaDatabase
  private lateinit var repository: SocivaRepository

  private val userA = UserEntity(
    id = "user_a",
    fullName = "User A",
    username = "user_a",
    avatarUrl = "https://example.com/a.jpg",
    coverUrl = "https://example.com/cover_a.jpg",
    bio = "Bio A",
    followersCount = 0,
    followingCount = 0,
    friendsCount = 0
  )

  private val userB = UserEntity(
    id = "user_b",
    fullName = "User B",
    username = "user_b",
    avatarUrl = "https://example.com/b.jpg",
    coverUrl = "https://example.com/cover_b.jpg",
    bio = "Bio B",
    followersCount = 0,
    followingCount = 0,
    friendsCount = 0
  )

  @Before
  fun setup() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, SocivaDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = SocivaRepository(db.socivaDao())

    db.socivaDao().insertUsers(listOf(userA, userB))
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun testSendingFriendRequest_doesNotImmediatelyMakeFriends_andAutoFollows() = runBlocking {
    // Before sending request
    val statusBefore = repository.getFriendStatusFlow("user_a", "user_b").first()
    assertEquals(FriendStatus.NONE, statusBefore)
    assertFalse(repository.isFollowingFlow("user_a", "user_b").first())

    // User A sends request to User B
    val result = repository.sendFriendRequest("user_a", "user_b")
    assertTrue(result)

    // They must NOT be friends
    assertNotEquals(FriendStatus.FRIENDS, repository.getFriendStatusFlow("user_a", "user_b").first())

    // Status from A's perspective must be REQUEST_SENT
    val statusA = repository.getFriendStatusFlow("user_a", "user_b").first()
    assertEquals(FriendStatus.REQUEST_SENT, statusA)

    // Status from B's perspective must be REQUEST_RECEIVED
    val statusB = repository.getFriendStatusFlow("user_b", "user_a").first()
    assertEquals(FriendStatus.REQUEST_RECEIVED, statusB)

    // User A should automatically follow User B
    assertTrue("User A should auto-follow User B", repository.isFollowingFlow("user_a", "user_b").first())

    // User B should receive notification
    val bNotifications = repository.getNotifications("user_b").first()
    assertTrue(bNotifications.any { it.type == NotificationType.FRIEND_REQUEST && it.actorName == "User A" })
  }

  @Test
  fun testAcceptingFriendRequest_createsFriendship_andMaintainsFollow() = runBlocking {
    // User A sends request to User B
    repository.sendFriendRequest("user_a", "user_b")

    val pendingReq = repository.getIncomingFriendRequests("user_b").first().first()
    assertEquals("user_a", pendingReq.senderId)

    // User B accepts the request
    repository.acceptFriendRequest(pendingReq.id, "user_b")

    // Both should now be friends
    val statusA = repository.getFriendStatusFlow("user_a", "user_b").first()
    val statusB = repository.getFriendStatusFlow("user_b", "user_a").first()
    assertEquals(FriendStatus.FRIENDS, statusA)
    assertEquals(FriendStatus.FRIENDS, statusB)

    // Pending request should be removed
    val bRequests = repository.getIncomingFriendRequests("user_b").first()
    assertTrue(bRequests.isEmpty())

    // Follow relationship must be maintained
    assertTrue("User A should still be following User B", repository.isFollowingFlow("user_a", "user_b").first())

    // User A receives acceptance notification
    val aNotifications = repository.getNotifications("user_a").first()
    assertTrue(aNotifications.any { it.type == NotificationType.ACCEPT_REQUEST && it.actorName == "User B" })
  }

  @Test
  fun testRejectingFriendRequest_removesRequest_andKeepsFollow() = runBlocking {
    // User A sends request to User B
    repository.sendFriendRequest("user_a", "user_b")
    assertTrue(repository.isFollowingFlow("user_a", "user_b").first())

    val pendingReq = repository.getIncomingFriendRequests("user_b").first().first()

    // User B rejects the request
    repository.rejectFriendRequest(pendingReq.id)

    // Must NOT be friends
    assertNotEquals(FriendStatus.FRIENDS, repository.getFriendStatusFlow("user_a", "user_b").first())

    // Pending request should be removed
    val bRequests = repository.getIncomingFriendRequests("user_b").first()
    assertTrue(bRequests.isEmpty())

    // Follow relationship MUST be preserved
    assertTrue("Follow relationship must be preserved after rejection", repository.isFollowingFlow("user_a", "user_b").first())
  }

  @Test
  fun testCancelingFriendRequest_resetsStatus_andKeepsFollow() = runBlocking {
    // User A sends request to User B
    repository.sendFriendRequest("user_a", "user_b")
    assertTrue(repository.isFollowingFlow("user_a", "user_b").first())

    // User A cancels the request
    val cancelled = repository.cancelFriendRequest("user_a", "user_b")
    assertTrue(cancelled)

    // Status returns to NONE
    val statusA = repository.getFriendStatusFlow("user_a", "user_b").first()
    assertEquals(FriendStatus.NONE, statusA)

    // Follow relationship MUST be preserved
    assertTrue("Follow relationship must be preserved after cancellation", repository.isFollowingFlow("user_a", "user_b").first())
  }

  @Test
  fun testUnfollowing_doesNotRemoveFriendship() = runBlocking {
    // Establish friendship
    repository.sendFriendRequest("user_a", "user_b")
    val pendingReq = repository.getIncomingFriendRequests("user_b").first().first()
    repository.acceptFriendRequest(pendingReq.id, "user_b")

    assertEquals(FriendStatus.FRIENDS, repository.getFriendStatusFlow("user_a", "user_b").first())
    assertTrue(repository.isFollowingFlow("user_a", "user_b").first())

    // User A unfollows User B
    repository.unfollowUser("user_a", "user_b")
    assertFalse("User A should no longer follow User B", repository.isFollowingFlow("user_a", "user_b").first())

    // Friendship MUST still remain intact!
    assertEquals(FriendStatus.FRIENDS, repository.getFriendStatusFlow("user_a", "user_b").first())
  }

  @Test
  fun testRemovingFriend_doesNotUnfollow() = runBlocking {
    // Establish friendship
    repository.sendFriendRequest("user_a", "user_b")
    val pendingReq = repository.getIncomingFriendRequests("user_b").first().first()
    repository.acceptFriendRequest(pendingReq.id, "user_b")

    assertEquals(FriendStatus.FRIENDS, repository.getFriendStatusFlow("user_a", "user_b").first())
    assertTrue(repository.isFollowingFlow("user_a", "user_b").first())

    // User A removes User B from friends
    repository.removeFriend("user_a", "user_b")

    // Must no longer be friends
    assertEquals(FriendStatus.NONE, repository.getFriendStatusFlow("user_a", "user_b").first())

    // Follow relationship MUST still be preserved!
    assertTrue("Follow relationship must remain intact after removing friend", repository.isFollowingFlow("user_a", "user_b").first())
  }
}
