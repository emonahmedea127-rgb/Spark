package com.example.sociva.data.service

import android.util.Log
import com.example.sociva.data.local.CommentEntity
import com.example.sociva.data.local.MessageEntity
import com.example.sociva.data.local.PostEntity
import com.example.sociva.data.local.SocivaDao
import com.example.sociva.data.local.UserEntity
import com.example.sociva.data.supabase.CommentDto
import com.example.sociva.data.supabase.ConversationDto
import com.example.sociva.data.supabase.FollowDto
import com.example.sociva.data.supabase.FriendRequestDto
import com.example.sociva.data.supabase.FriendshipDto
import com.example.sociva.data.supabase.MessageDto
import com.example.sociva.data.supabase.NotificationDto
import com.example.sociva.data.supabase.PostDto
import com.example.sociva.data.supabase.PostLikeDto
import com.example.sociva.data.supabase.ProfileDto
import com.example.sociva.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Service to synchronize Spark data (Posts, Comments, Messages, Profiles, Likes, Friends)
 * with Supabase PostgreSQL and Supabase Realtime.
 */
class SupabaseService(
  private val dao: SocivaDao,
  private val scope: CoroutineScope
) {
  private val TAG = "SupabaseService"

  private val supabase
    get() = SupabaseClientProvider.client

  private var postsChannel: RealtimeChannel? = null
  private var messagesChannel: RealtimeChannel? = null
  private var profilesChannel: RealtimeChannel? = null
  private var notificationsChannel: RealtimeChannel? = null

  private var postsJob: Job? = null
  private var messagesJob: Job? = null
  private var profileJob: Job? = null
  private var notificationsJob: Job? = null

  fun startSync() {
    fetchRemotePosts()
    subscribeToPostsRealtime()
  }

  fun stopSync() {
    scope.launch(Dispatchers.IO) {
      postsJob?.cancel()
      postsJob = null
      messagesJob?.cancel()
      messagesJob = null
      profileJob?.cancel()
      profileJob = null
      notificationsJob?.cancel()
      notificationsJob = null

      try {
        postsChannel?.let { supabase?.realtime?.removeChannel(it) }
        messagesChannel?.let { supabase?.realtime?.removeChannel(it) }
        profilesChannel?.let { supabase?.realtime?.removeChannel(it) }
        notificationsChannel?.let { supabase?.realtime?.removeChannel(it) }
      } catch (e: Exception) {
        Log.w(TAG, "Error removing realtime channels: ${e.message}")
      }
      postsChannel = null
      messagesChannel = null
      profilesChannel = null
      notificationsChannel = null
    }
  }

  /**
   * Verifies if a given PostgreSQL table exists and is queryable in Supabase.
   */
  private suspend fun isTableAccessible(tableName: String): Boolean {
    val client = supabase ?: return false
    if (!SupabaseClientProvider.isConfigured()) return false
    return try {
      client.from(tableName).select { limit(1) }
      true
    } catch (e: Exception) {
      Log.d(TAG, "Table '$tableName' is not accessible in Supabase: ${e.message}")
      false
    }
  }

  /**
   * Guarantees a valid RFC 4122 UUID string for PostgreSQL UUID columns.
   * If rawId is already a valid UUID, it is returned untouched.
   * If rawId is any other string, a deterministic UUID is produced so relationships remain consistent.
   */
  fun toValidUuid(rawId: String): String {
    val trimmed = rawId.trim()
    if (trimmed.isBlank()) return java.util.UUID.randomUUID().toString()
    return try {
      java.util.UUID.fromString(trimmed)
      trimmed
    } catch (_: Exception) {
      java.util.UUID.nameUUIDFromBytes(trimmed.toByteArray(Charsets.UTF_8)).toString()
    }
  }

  /**
   * Fetches the latest 50 posts from Supabase PostgreSQL and caches into Room.
   */
  fun fetchRemotePosts() {
    val client = supabase ?: return
    if (!SupabaseClientProvider.isConfigured()) return

    scope.launch(Dispatchers.IO) {
      try {
        if (!isTableAccessible("posts")) {
          Log.i(TAG, "Remote 'posts' table not present in Supabase yet. Operating in local Room mode.")
          return@launch
        }

        val posts = client.from("posts")
          .select {
            order("timestamp", Order.DESCENDING)
            limit(50)
          }
          .decodeList<PostDto>()

        val entities = posts.map { dto ->
          val mediaStr = if (dto.mediaUrls.isNotEmpty()) {
            dto.mediaUrls.joinToString(",")
          } else {
            dto.mediaUrl
          }
          PostEntity(
            id = dto.id,
            authorId = dto.authorId,
            authorName = dto.authorName,
            authorUsername = dto.authorUsername,
            authorAvatar = dto.authorAvatar,
            isAuthorVerified = dto.isAuthorVerified,
            timestamp = dto.timestamp,
            content = dto.content.ifBlank { dto.text },
            mediaUrlsString = mediaStr,
            feelingOrActivity = dto.feeling.ifBlank { null },
            audience = dto.audience,
            likesCount = dto.likeCount,
            commentsCount = dto.commentCount,
            sharesCount = dto.shareCount,
            myReaction = null,
            isSaved = false,
            authorType = dto.authorType,
            targetGroupId = dto.targetGroupId,
            targetGroupName = dto.targetGroupName
          )
        }
        if (entities.isNotEmpty()) {
          dao.insertPosts(entities)
        }

        // Table exists, ensure realtime is active
        if (postsChannel == null) {
          subscribeToPostsRealtime()
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to fetch posts from Supabase: ${e.message}")
      }
    }
  }

  /**
   * Subscribes to real-time changes on public.posts table when enabled.
   */
  private fun subscribeToPostsRealtime() {
    val client = supabase ?: return
    if (!SupabaseClientProvider.isConfigured()) return

    postsJob?.cancel()
    postsJob = scope.launch(Dispatchers.IO) {
      try {
        if (!isTableAccessible("posts")) {
          Log.i(TAG, "Supabase 'posts' table not yet present. Realtime sync deferred.")
          return@launch
        }

        val channel = client.channel("public-posts-sync")
        postsChannel = channel
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
          table = "posts"
        }
        channel.subscribe()

        changeFlow.collect { action ->
          when (action) {
            is PostgresAction.Insert -> {
              try {
                val dto = action.decodeRecord<PostDto>()
                val mediaStr = if (dto.mediaUrls.isNotEmpty()) {
                  dto.mediaUrls.joinToString(",")
                } else {
                  dto.mediaUrl
                }
                val entity = PostEntity(
                  id = dto.id,
                  authorId = dto.authorId,
                  authorName = dto.authorName,
                  authorUsername = dto.authorUsername,
                  authorAvatar = dto.authorAvatar,
                  isAuthorVerified = dto.isAuthorVerified,
                  timestamp = dto.timestamp,
                  content = dto.content.ifBlank { dto.text },
                  mediaUrlsString = mediaStr,
                  feelingOrActivity = dto.feeling.ifBlank { null },
                  audience = dto.audience,
                  likesCount = dto.likeCount,
                  commentsCount = dto.commentCount,
                  sharesCount = dto.shareCount,
                  myReaction = null,
                  isSaved = false,
                  authorType = dto.authorType,
                  targetGroupId = dto.targetGroupId,
                  targetGroupName = dto.targetGroupName
                )
                dao.insertPost(entity)
              } catch (e: Exception) {
                Log.w(TAG, "Error handling post insert event: ${e.message}")
              }
            }
            is PostgresAction.Update -> {
              try {
                val dto = action.decodeRecord<PostDto>()
                val existing = dao.findPostById(dto.id)
                if (existing != null) {
                  dao.updatePost(
                    existing.copy(
                      likesCount = dto.likeCount,
                      commentsCount = dto.commentCount,
                      content = dto.content
                    )
                  )
                }
              } catch (e: Exception) {
                Log.w(TAG, "Error handling post update event: ${e.message}")
              }
            }
            is PostgresAction.Delete -> {
              // Delete handled locally in Room
            }
            else -> {}
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to attach posts realtime listener: ${e.message}")
      }
    }
  }

  /**
   * Publishes post to Supabase PostgreSQL and updates local Room database.
   */
  suspend fun publishPost(
    post: PostEntity,
    mediaUrls: List<String> = emptyList(),
    taggedUserIds: List<String> = emptyList()
  ): Result<PostEntity> = withContext(Dispatchers.IO) {
    val client = supabase
    val currentAuthUid = try {
      client?.auth?.currentUserOrNull()?.id
    } catch (_: Exception) {
      null
    }
    val authoritativeAuthorId = currentAuthUid ?: post.authorId
    val validPostId = toValidUuid(post.id)
    val validAuthorId = toValidUuid(authoritativeAuthorId)

    val mediaList = if (mediaUrls.isNotEmpty()) {
      mediaUrls
    } else if (post.mediaUrlsString.isNotBlank()) {
      post.mediaUrlsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    } else {
      emptyList()
    }

    val mediaType = when {
      mediaList.any { it.contains(".mp4", ignoreCase = true) || it.contains("video", ignoreCase = true) } -> "VIDEO"
      mediaList.isNotEmpty() -> "IMAGE"
      else -> "TEXT"
    }

    val postDto = PostDto(
      id = validPostId,
      authorId = validAuthorId,
      authorName = post.authorName.ifBlank { "Spark User" },
      authorUsername = post.authorUsername.ifBlank { "sparkuser" },
      authorAvatar = post.authorAvatar,
      isAuthorVerified = post.isAuthorVerified,
      text = post.content,
      content = post.content,
      mediaUrl = mediaList.firstOrNull() ?: "",
      mediaUrls = mediaList,
      mediaType = mediaType,
      feeling = post.feelingOrActivity ?: "",
      audience = post.audience,
      authorType = post.authorType,
      targetGroupId = post.targetGroupId,
      targetGroupName = post.targetGroupName,
      likeCount = post.likesCount,
      commentCount = post.commentsCount,
      shareCount = post.sharesCount,
      timestamp = post.timestamp
    )

    if (client != null && SupabaseClientProvider.isConfigured()) {
      try {
        // Upsert author profile first to guarantee foreign key constraint is satisfied
        try {
          client.from("profiles").upsert(
            ProfileDto(
              id = validAuthorId,
              name = post.authorName.ifBlank { "Spark User" },
              username = post.authorUsername.ifBlank { "user_" + validAuthorId.take(8) },
              profileImageUrl = post.authorAvatar
            )
          )
        } catch (pe: Exception) {
          Log.d(TAG, "Profile pre-upsert notice: ${pe.message}")
        }

        client.from("posts").upsert(postDto)
        Log.i(TAG, "Successfully published post $validPostId to Supabase PostgreSQL.")
      } catch (e: Exception) {
        Log.w(TAG, "Remote post sync fallback to local Room: ${e.message}")
      }
    }

    val finalPost = post.copy(id = validPostId, authorId = validAuthorId)
    dao.insertPost(finalPost)
    Result.success(finalPost)
  }

  /**
   * Publishes comment to Supabase PostgreSQL table 'comments'.
   */
  fun publishComment(comment: CommentEntity) {
    val client = supabase ?: return
    scope.launch(Dispatchers.IO) {
      try {
        val commentDto = CommentDto(
          id = toValidUuid(comment.id),
          postId = toValidUuid(comment.postId),
          authorId = toValidUuid(comment.authorId),
          authorName = comment.authorName,
          authorAvatar = comment.authorAvatar,
          isAuthorVerified = comment.isAuthorVerified,
          text = comment.content,
          parentCommentId = comment.parentCommentId?.takeIf { it.isNotBlank() }?.let { toValidUuid(it) },
          timestamp = comment.timestamp
        )
        if (SupabaseClientProvider.isConfigured()) {
          client.from("comments").upsert(commentDto)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to sync comment to Supabase: ${e.message}")
      }
    }
  }

  /**
   * Deletes a comment from Supabase PostgreSQL.
   */
  suspend fun deleteComment(commentId: String, postId: String): Boolean = withContext(Dispatchers.IO) {
    val client = supabase ?: return@withContext false
    if (!SupabaseClientProvider.isConfigured()) return@withContext true
    val validCommentId = toValidUuid(commentId)
    try {
      client.from("comments").delete {
        filter {
          eq("id", validCommentId)
        }
      }
      true
    } catch (e: Exception) {
      Log.w(TAG, "Failed to delete comment from Supabase: ${e.message}")
      false
    }
  }

  /**
   * Deletes a post from Supabase PostgreSQL.
   */
  suspend fun deletePost(postId: String): Boolean = withContext(Dispatchers.IO) {
    val client = supabase ?: return@withContext false
    if (!SupabaseClientProvider.isConfigured()) return@withContext true
    val validPostId = toValidUuid(postId)
    try {
      client.from("posts").delete {
        filter {
          eq("id", validPostId)
        }
      }
      true
    } catch (e: Exception) {
      Log.w(TAG, "Failed to delete post from Supabase: ${e.message}")
      false
    }
  }

  /**
   * Toggles a post like in Supabase PostgreSQL (post_likes table).
   */
  suspend fun toggleLike(postId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
    val client = supabase ?: return@withContext false
    if (!SupabaseClientProvider.isConfigured()) return@withContext true
    val validPostId = toValidUuid(postId)
    val validUserId = toValidUuid(userId)
    try {
      // Check if like exists
      val existing = client.from("post_likes")
        .select {
          filter {
            eq("post_id", validPostId)
            eq("user_id", validUserId)
          }
        }
        .decodeList<PostLikeDto>()

      if (existing.isNotEmpty()) {
        client.from("post_likes").delete {
          filter {
            eq("post_id", validPostId)
            eq("user_id", validUserId)
          }
        }
        false // unliked
      } else {
        client.from("post_likes").insert(PostLikeDto(postId = validPostId, userId = validUserId))
        true // liked
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to toggle like in Supabase: ${e.message}")
      false
    }
  }

  /**
   * Toggles friendship in Supabase PostgreSQL (friendships table).
   */
  suspend fun toggleFriend(userId: String, friendId: String): Boolean = withContext(Dispatchers.IO) {
    val client = supabase ?: return@withContext false
    if (!SupabaseClientProvider.isConfigured()) return@withContext true
    val validUserId = toValidUuid(userId)
    val validFriendId = toValidUuid(friendId)
    try {
      val existing = client.from("friendships")
        .select {
          filter {
            eq("user_id", validUserId)
            eq("friend_id", validFriendId)
          }
        }
        .decodeList<FriendshipDto>()

      if (existing.isNotEmpty()) {
        client.from("friendships").delete {
          filter {
            eq("user_id", validUserId)
            eq("friend_id", validFriendId)
          }
        }
        false
      } else {
        client.from("friendships").insert(FriendshipDto(userId = validUserId, friendId = validFriendId))
        true
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to toggle friendship in Supabase: ${e.message}")
      false
    }
  }

  /**
   * Toggles follow status in Supabase PostgreSQL (follows table).
   */
  suspend fun toggleFollow(followerId: String, followingId: String): Boolean = withContext(Dispatchers.IO) {
    val client = supabase ?: return@withContext false
    if (!SupabaseClientProvider.isConfigured()) return@withContext true
    val validFollowerId = toValidUuid(followerId)
    val validFollowingId = toValidUuid(followingId)
    try {
      val existing = client.from("follows")
        .select {
          filter {
            eq("follower_id", validFollowerId)
            eq("following_id", validFollowingId)
          }
        }
        .decodeList<FollowDto>()

      if (existing.isNotEmpty()) {
        client.from("follows").delete {
          filter {
            eq("follower_id", validFollowerId)
            eq("following_id", validFollowingId)
          }
        }
        false
      } else {
        client.from("follows").insert(FollowDto(followerId = validFollowerId, followingId = validFollowingId))
        true
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to toggle follow in Supabase: ${e.message}")
      false
    }
  }

  /**
   * Publishes message to Supabase PostgreSQL table 'messages'.
   */
  fun publishMessage(message: MessageEntity) {
    val client = supabase ?: return
    scope.launch(Dispatchers.IO) {
      try {
        val dto = MessageDto(
          id = toValidUuid(message.id),
          conversationId = toValidUuid(message.conversationId),
          senderId = toValidUuid(message.senderId),
          receiverId = message.receiverId?.takeIf { it.isNotBlank() }?.let { toValidUuid(it) },
          message = message.text,
          mediaUrl = message.mediaUrl,
          messageType = message.messageType,
          isSeen = message.isSeen,
          timestamp = message.timestamp
        )
        if (SupabaseClientProvider.isConfigured()) {
          client.from("messages").upsert(dto)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to sync message to Supabase: ${e.message}")
      }
    }
  }

  /**
   * Syncs user profile entity to Supabase PostgreSQL table 'profiles'.
   */
  fun syncUserProfile(user: UserEntity) {
    val client = supabase ?: return
    scope.launch(Dispatchers.IO) {
      try {
        val dto = ProfileDto(
          id = toValidUuid(user.id),
          name = user.fullName,
          username = user.username,
          email = user.email,
          phoneNumber = user.phone,
          profileImageUrl = user.avatarUrl,
          coverImageUrl = user.coverUrl,
          bio = user.bio,
          firstName = user.firstName,
          lastName = user.lastName,
          gender = user.gender,
          dateOfBirth = user.dateOfBirth,
          location = user.location,
          work = user.work,
          education = user.education,
          relationshipStatus = user.relationshipStatus,
          isVerified = user.isVerified,
          isOnline = user.isOnline
        )
        if (SupabaseClientProvider.isConfigured()) {
          client.from("profiles").upsert(dto)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to sync user profile to Supabase: ${e.message}")
      }
    }
  }

  /**
   * Updates profile image URL in Supabase PostgreSQL 'profiles' table.
   */
  suspend fun updateProfilePhotoUrl(uid: String, photoUrl: String): Boolean = withContext(Dispatchers.IO) {
    val client = supabase ?: return@withContext false
    if (!SupabaseClientProvider.isConfigured()) return@withContext true
    try {
      client.from("profiles").update(
        {
          set("profile_image_url", photoUrl)
        }
      ) {
        filter {
          eq("id", uid)
        }
      }
      true
    } catch (e: Exception) {
      Log.w(TAG, "Failed to update profile photo in Supabase: ${e.message}")
      false
    }
  }

  /**
   * Updates cover image URL in Supabase PostgreSQL 'profiles' table.
   */
  suspend fun updateCoverPhotoUrl(uid: String, coverUrl: String): Boolean = withContext(Dispatchers.IO) {
    val client = supabase ?: return@withContext false
    if (!SupabaseClientProvider.isConfigured()) return@withContext true
    try {
      client.from("profiles").update(
        {
          set("cover_image_url", coverUrl)
        }
      ) {
        filter {
          eq("id", uid)
        }
      }
      true
    } catch (e: Exception) {
      Log.w(TAG, "Failed to update cover photo in Supabase: ${e.message}")
      false
    }
  }

  /**
   * Fetches the user profile from Supabase PostgreSQL and caches into Room.
   */
  suspend fun fetchUserProfile(uid: String): UserEntity? = withContext(Dispatchers.IO) {
    val client = supabase ?: return@withContext null
    if (!SupabaseClientProvider.isConfigured()) return@withContext null
    try {
      val profiles = client.from("profiles")
        .select {
          filter {
            eq("id", uid)
          }
          limit(1)
        }
        .decodeList<ProfileDto>()

      val profile = profiles.firstOrNull() ?: return@withContext null
      val entity = UserEntity(
        id = profile.id,
        username = profile.username.ifBlank { uid },
        fullName = profile.name.ifBlank { "Spark User" },
        firstName = profile.firstName,
        lastName = profile.lastName,
        avatarUrl = profile.profileImageUrl,
        coverUrl = profile.coverImageUrl,
        bio = profile.bio,
        email = profile.email,
        phone = profile.phoneNumber,
        dateOfBirth = profile.dateOfBirth,
        gender = profile.gender,
        location = profile.location,
        relationshipStatus = profile.relationshipStatus,
        work = profile.work,
        education = profile.education,
        isVerified = profile.isVerified,
        joinedDate = "Joined recently",
        isOnline = true
      )
      dao.insertUser(entity)
      entity
    } catch (e: Exception) {
      Log.w(TAG, "Failed to fetch user profile from Supabase: ${e.message}")
      null
    }
  }

  /**
   * Attaches real-time listener to user profile in Supabase.
   */
  fun startUserProfileListener(uid: String) {
    val client = supabase ?: return
    if (!SupabaseClientProvider.isConfigured()) return

    profileJob?.cancel()
    profileJob = scope.launch(Dispatchers.IO) {
      try {
        if (!isTableAccessible("profiles")) {
          Log.i(TAG, "Supabase 'profiles' table not yet present. Profile realtime sync deferred.")
          return@launch
        }

        val channel = client.channel("profile-sync-$uid")
        profilesChannel = channel
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
          table = "profiles"
        }
        channel.subscribe()

        changeFlow.collect { action ->
          if (action is PostgresAction.Update || action is PostgresAction.Insert) {
            try {
              val profile = action.decodeRecord<ProfileDto>()
              if (profile.id != uid) return@collect
              val entity = UserEntity(
                id = profile.id,
                username = profile.username.ifBlank { uid },
                fullName = profile.name.ifBlank { "Spark User" },
                firstName = profile.firstName,
                lastName = profile.lastName,
                avatarUrl = profile.profileImageUrl,
                coverUrl = profile.coverImageUrl,
                bio = profile.bio,
                email = profile.email,
                phone = profile.phoneNumber,
                dateOfBirth = profile.dateOfBirth,
                gender = profile.gender,
                location = profile.location,
                relationshipStatus = profile.relationshipStatus,
                work = profile.work,
                education = profile.education,
                isVerified = profile.isVerified,
                joinedDate = "Joined recently",
                isOnline = true
              )
              dao.insertUser(entity)
            } catch (e: Exception) {
              Log.w(TAG, "Failed to map profile realtime update: ${e.message}")
            }
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to attach user profile listener: ${e.message}")
      }
    }
  }
}
