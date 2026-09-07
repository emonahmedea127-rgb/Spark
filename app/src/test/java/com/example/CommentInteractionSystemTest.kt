package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sociva.data.local.PostEntity
import com.example.sociva.data.local.SocivaDatabase
import com.example.sociva.data.local.UserEntity
import com.example.sociva.data.model.ReactionType
import com.example.sociva.data.model.User
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
class CommentInteractionSystemTest {

  private lateinit var db: SocivaDatabase
  private lateinit var repository: SocivaRepository

  private val userA = User(
    id = "user_a",
    username = "usera",
    fullName = "User A",
    avatarUrl = "https://example.com/a.jpg",
    coverUrl = "",
    bio = ""
  )

  private val userB = User(
    id = "user_b",
    username = "userb",
    fullName = "User B",
    avatarUrl = "https://example.com/b.jpg",
    coverUrl = "",
    bio = ""
  )

  private val userC = User(
    id = "user_c",
    username = "userc",
    fullName = "User C",
    avatarUrl = "https://example.com/c.jpg",
    coverUrl = "",
    bio = ""
  )

  @Before
  fun setup() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, SocivaDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = SocivaRepository(db.socivaDao())

    db.socivaDao().insertUsers(
      listOf(
        UserEntity(id = userA.id, username = userA.username, fullName = userA.fullName, avatarUrl = userA.avatarUrl, coverUrl = "", bio = ""),
        UserEntity(id = userB.id, username = userB.username, fullName = userB.fullName, avatarUrl = userB.avatarUrl, coverUrl = "", bio = ""),
        UserEntity(id = userC.id, username = userC.username, fullName = userC.fullName, avatarUrl = userC.avatarUrl, coverUrl = "", bio = "")
      )
    )

    // User A creates a post
    db.socivaDao().insertPost(
      PostEntity(
        id = "post_1",
        authorId = userA.id,
        authorName = userA.fullName,
        authorUsername = userA.username,
        authorAvatar = userA.avatarUrl,
        timestamp = System.currentTimeMillis(),
        content = "User A's test post",
        mediaUrlsString = ""
      )
    )
  }

  @After
  fun teardown() {
    db.close()
  }

  @Test
  fun test1_userCReactsToUserBComment_reactionCountIncreasesAndUserBNotified() = runBlocking {
    // User B comments
    val commentResult = repository.addComment(
      postId = "post_1",
      author = userB,
      content = "User B's comment"
    )
    assertTrue("Comment creation should succeed", commentResult.isSuccess)
    val commentId = commentResult.getOrThrow().id

    // User C reacts to User B's comment
    val reactResult = repository.reactToComment(
      commentId = commentId,
      user = userC,
      reactionType = ReactionType.LIKE
    )
    assertTrue(reactResult.isSuccess)
    assertEquals(ReactionType.LIKE, reactResult.getOrThrow())

    // Verify reaction count in Flow
    val comments = repository.getComments("post_1", userC.id).first()
    assertEquals(1, comments.size)
    val comment = comments[0]
    assertEquals(1, comment.reactionsCount)
    assertEquals(ReactionType.LIKE, comment.myReaction)

    // Verify User B receives notification
    val notifications = db.socivaDao().getNotificationsForRecipient(userB.id).first()
    val notif = notifications.firstOrNull { it.recipientId == userB.id && it.type == "COMMENT_REACTION" }
    assertNotNull("User B should receive a COMMENT_REACTION notification", notif)
    assertEquals(userC.fullName, notif!!.actorName)
    assertTrue(notif.messageSnippet.contains("reacted to your comment"))
  }

  @Test
  fun test2_userCRepliesToUserBComment_replyAppearsNestedAndUserBNotified() = runBlocking {
    // User B comments
    val parentComment = repository.addComment("post_1", userB, "User B root comment").getOrThrow()

    // User C replies to User B's comment
    val reply = repository.addComment(
      postId = "post_1",
      author = userC,
      content = "User C replying to User B",
      parentCommentId = parentComment.id
    ).getOrThrow()

    // Verify reply appears nested under parent comment
    val comments = repository.getComments("post_1", userC.id).first()
    assertEquals(1, comments.size)
    val rootComment = comments[0]
    assertEquals(1, rootComment.repliesCount)
    assertEquals(1, rootComment.replies.size)
    assertEquals(reply.id, rootComment.replies[0].id)
    assertEquals("User C replying to User B", rootComment.replies[0].content)

    // Verify User B receives reply notification
    val notifications = db.socivaDao().getNotificationsForRecipient(userB.id).first()
    val replyNotif = notifications.firstOrNull { it.recipientId == userB.id && it.type == "COMMENT_REPLY" }
    assertNotNull("User B should receive a COMMENT_REPLY notification", replyNotif)
    assertTrue(replyNotif!!.messageSnippet.contains("replied to your comment"))
  }

  @Test
  fun test3_userCChangesReactionFromLikeToLove_singleReactionAndCorrectCount() = runBlocking {
    val comment = repository.addComment("post_1", userB, "Comment for reaction change").getOrThrow()

    // Step 1: React with LIKE
    repository.reactToComment(comment.id, userC, ReactionType.LIKE)
    var comments = repository.getComments("post_1", userC.id).first()
    assertEquals(1, comments[0].reactionsCount)
    assertEquals(ReactionType.LIKE, comments[0].myReaction)

    // Step 2: Change to LOVE
    repository.reactToComment(comment.id, userC, ReactionType.LOVE)
    comments = repository.getComments("post_1", userC.id).first()
    assertEquals("Count should remain 1", 1, comments[0].reactionsCount)
    assertEquals(ReactionType.LOVE, comments[0].myReaction)

    // Verify database record count
    val totalReactionsInDb = db.socivaDao().countReactionsForComment(comment.id)
    assertEquals("Only 1 reaction record should exist in DB for User C", 1, totalReactionsInDb)
  }

  @Test
  fun test4_userCTapsSameReactionAgain_reactionIsRemoved() = runBlocking {
    val comment = repository.addComment("post_1", userB, "Comment for reaction toggle").getOrThrow()

    // React with LIKE
    repository.reactToComment(comment.id, userC, ReactionType.LIKE)
    var comments = repository.getComments("post_1", userC.id).first()
    assertEquals(1, comments[0].reactionsCount)

    // Tap LIKE again -> toggles off
    val removeResult = repository.reactToComment(comment.id, userC, ReactionType.LIKE)
    assertTrue(removeResult.isSuccess)
    assertNull("Tapping same reaction should return null (removed)", removeResult.getOrThrow())

    // Verify count is 0 and myReaction is null
    comments = repository.getComments("post_1", userC.id).first()
    assertEquals(0, comments[0].reactionsCount)
    assertNull(comments[0].myReaction)
  }

  @Test
  fun test5_userBEditsTheirOwnComment_updatesSuccessfully() = runBlocking {
    val comment = repository.addComment("post_1", userB, "Original comment").getOrThrow()

    // User B edits their own comment
    val editResult = repository.editComment(comment.id, userB.id, "Edited comment content")
    assertTrue("Owner editing comment should succeed", editResult.isSuccess)
    assertEquals("Edited comment content", editResult.getOrThrow().content)

    // Verify in database / flow
    val comments = repository.getComments("post_1").first()
    assertEquals("Edited comment content", comments[0].content)
  }

  @Test
  fun test6_userCTriesToEditUserBComment_operationIsDenied() = runBlocking {
    val comment = repository.addComment("post_1", userB, "User B original comment").getOrThrow()

    // User C attempts to edit User B's comment
    val unauthorizedEdit = repository.editComment(comment.id, userC.id, "Malicious edit")
    assertTrue("Non-owner edit should fail", unauthorizedEdit.isFailure)
    assertTrue(unauthorizedEdit.exceptionOrNull() is SecurityException)

    // Verify content remained unchanged
    val comments = repository.getComments("post_1").first()
    assertEquals("User B original comment", comments[0].content)
  }

  @Test
  fun test7_userCDeletesTheirOwnReply_replyDisappearsAndCountsUpdate() = runBlocking {
    val parentComment = repository.addComment("post_1", userB, "Parent comment").getOrThrow()
    val reply = repository.addComment("post_1", userC, "User C reply to be deleted", parentComment.id).getOrThrow()

    // Verify reply exists
    var comments = repository.getComments("post_1").first()
    assertEquals(1, comments[0].repliesCount)

    // User C deletes their own reply
    val deleteResult = repository.deleteComment(reply.id, "post_1", userC.id)
    assertTrue("Owner deleting their own reply should succeed", deleteResult.isSuccess)

    // Verify reply is gone
    comments = repository.getComments("post_1").first()
    assertEquals(0, comments[0].repliesCount)
    assertTrue(comments[0].replies.isEmpty())
  }
}
