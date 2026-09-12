package com.example.sociva.data.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
  val id: String,
  val name: String = "",
  val username: String = "",
  val email: String = "",
  @SerialName("phone_number") val phoneNumber: String = "",
  @SerialName("profile_image_url") val profileImageUrl: String = "",
  @SerialName("cover_image_url") val coverImageUrl: String = "",
  val bio: String = "",
  @SerialName("first_name") val firstName: String = "",
  @SerialName("last_name") val lastName: String = "",
  val gender: String = "",
  @SerialName("date_of_birth") val dateOfBirth: String = "",
  val location: String = "",
  val work: String = "",
  val education: String = "",
  @SerialName("relationship_status") val relationshipStatus: String = "",
  @SerialName("is_verified") val isVerified: Boolean = false,
  @SerialName("is_online") val isOnline: Boolean = false,
  @SerialName("created_at") val createdAt: String? = null,
  @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PostDto(
  val id: String,
  @SerialName("author_id") val authorId: String,
  @SerialName("author_name") val authorName: String = "Spark User",
  @SerialName("author_username") val authorUsername: String = "sparkuser",
  @SerialName("author_avatar") val authorAvatar: String = "",
  @SerialName("is_author_verified") val isAuthorVerified: Boolean = false,
  val text: String = "",
  val content: String = "",
  @SerialName("media_url") val mediaUrl: String = "",
  @SerialName("media_urls") val mediaUrls: List<String> = emptyList(),
  @SerialName("media_type") val mediaType: String = "TEXT",
  val feeling: String = "",
  val audience: String = "Public",
  @SerialName("author_type") val authorType: String = "PERSONAL",
  @SerialName("target_group_id") val targetGroupId: String? = null,
  @SerialName("target_group_name") val targetGroupName: String? = null,
  @SerialName("like_count") val likeCount: Int = 0,
  @SerialName("comment_count") val commentCount: Int = 0,
  @SerialName("share_count") val shareCount: Int = 0,
  val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class CommentDto(
  val id: String,
  @SerialName("post_id") val postId: String,
  @SerialName("author_id") val authorId: String,
  @SerialName("author_name") val authorName: String = "Spark User",
  @SerialName("author_avatar") val authorAvatar: String = "",
  @SerialName("is_author_verified") val isAuthorVerified: Boolean = false,
  val text: String = "",
  @SerialName("parent_comment_id") val parentCommentId: String? = null,
  val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class PostLikeDto(
  @SerialName("post_id") val postId: String,
  @SerialName("user_id") val userId: String
)

@Serializable
data class FriendRequestDto(
  val id: String,
  @SerialName("sender_id") val senderId: String,
  @SerialName("receiver_id") val receiverId: String,
  val status: String = "pending"
)

@Serializable
data class FriendshipDto(
  @SerialName("user_id") val userId: String,
  @SerialName("friend_id") val friendId: String
)

@Serializable
data class FollowDto(
  @SerialName("follower_id") val followerId: String,
  @SerialName("following_id") val followingId: String
)

@Serializable
data class ConversationDto(
  val id: String,
  @SerialName("participant_one") val participantOne: String,
  @SerialName("participant_two") val participantTwo: String,
  @SerialName("last_message") val lastMessage: String = "",
  @SerialName("last_message_timestamp") val lastMessageTimestamp: String? = null
)

@Serializable
data class MessageDto(
  val id: String,
  @SerialName("conversation_id") val conversationId: String,
  @SerialName("sender_id") val senderId: String,
  @SerialName("receiver_id") val receiverId: String? = null,
  val message: String = "",
  @SerialName("media_url") val mediaUrl: String? = null,
  @SerialName("message_type") val messageType: String = "TEXT",
  @SerialName("is_seen") val isSeen: Boolean = false,
  val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class NotificationDto(
  val id: String,
  @SerialName("user_id") val userId: String,
  @SerialName("actor_id") val actorId: String? = null,
  val type: String,
  @SerialName("entity_id") val entityId: String? = null,
  val message: String = "",
  @SerialName("is_read") val isRead: Boolean = false
)
