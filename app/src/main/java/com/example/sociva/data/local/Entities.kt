package com.example.sociva.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
  @PrimaryKey val id: String,
  val username: String,
  val fullName: String,
  val avatarUrl: String = "",
  val coverUrl: String = "",
  val bio: String = "",
  val isVerified: Boolean = false,
  val followersCount: Int = 0,
  val followingCount: Int = 0,
  val friendsCount: Int = 0,
  val postsCount: Int = 0,
  val work: String = "",
  val education: String = "",
  val location: String = "",
  val joinedDate: String = "September 2024",
  val isOnline: Boolean = false,
  val lastActiveAt: Long = 0L,
  val isFriend: Boolean = false,
  val isFollowing: Boolean = false,
  val profilePictureUpdatedAt: Long = 0L,
  val coverPhotoUpdatedAt: Long = 0L,
  // Section A: Basic Information
  val firstName: String = "",
  val lastName: String = "",
  val pronouns: String = "",
  val nickname: String = "",
  val otherNames: String = "",
  // Section B: Personal Information
  val dateOfBirth: String = "",
  val gender: String = "",
  val interestedIn: String = "",
  val hometown: String = "",
  val currentCity: String = "",
  val country: String = "",
  val currentRegion: String = "",
  val currentCountryCode: String = "",
  val currentLatitude: Double? = null,
  val currentLongitude: Double? = null,
  val hometownRegion: String = "",
  val hometownCountryCode: String = "",
  val hometownLatitude: Double? = null,
  val hometownLongitude: Double? = null,
  val countryCode: String = "",
  // Work Information
  val workplace: String = "",
  val workPosition: String = "",
  val workStartDate: String = "",
  val workEndDate: String = "",
  // Education Information
  val school: String = "",
  val college: String = "",
  val university: String = "",
  val degree: String = "",
  val fieldOfStudy: String = "",
  val graduationYear: String = "",
  // Contact Information
  val website: String = "",
  val email: String = "",
  val phone: String = "",
  // Relationship Status & Partner
  val relationshipStatus: String = "",
  val relationshipPartnerId: String? = null,
  val relationshipPartnerName: String? = null,
  val customRelationshipText: String? = null,
  // Privacy Controls
  val birthdayPrivacy: String = "Public",
  val currentCityPrivacy: String = "Public",
  val hometownPrivacy: String = "Public",
  val relationshipPrivacy: String = "Public",
  val emailPrivacy: String = "Friends",
  val taggingPermission: String = "ALLOW_ANYONE",
  val reviewTagsBeforeAppearing: Boolean = false
)

@Entity(tableName = "posts")
data class PostEntity(
  @PrimaryKey val id: String,
  val authorId: String,
  val authorName: String,
  val authorUsername: String,
  val authorAvatar: String,
  val isAuthorVerified: Boolean = false,
  val timestamp: Long,
  val content: String,
  val mediaUrlsString: String = "", // Comma-separated or empty
  val feelingOrActivity: String? = null,
  val audience: String = "Public",
  val likesCount: Int = 0,
  val commentsCount: Int = 0,
  val sharesCount: Int = 0,
  val myReaction: String? = null,
  val isSaved: Boolean = false,
  val postType: String = "NORMAL", // NORMAL, PROFILE_PICTURE_UPDATE, COVER_PHOTO_UPDATE, SHARED_POST
  val originalPostId: String? = null,
  val actionContextText: String? = null,
  val authorType: String = "PERSONAL", // "PERSONAL" or "PAGE"
  val targetGroupId: String? = null,
  val targetGroupName: String? = null
)

@Entity(
  tableName = "post_reactions",
  indices = [
    Index(value = ["post_id", "user_id"], unique = true),
    Index(value = ["post_id"]),
    Index(value = ["user_id"])
  ]
)
data class PostReactionEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "post_id") val postId: String,
  @ColumnInfo(name = "user_id") val userId: String,
  @ColumnInfo(name = "reaction_type") val reactionType: String,
  @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
  tableName = "comments",
  indices = [
    Index(value = ["post_id"]),
    Index(value = ["parent_comment_id"]),
    Index(value = ["user_id"])
  ]
)
data class CommentEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "post_id") val postId: String,
  @ColumnInfo(name = "user_id") val authorId: String,
  val authorName: String,
  val authorAvatar: String,
  val isAuthorVerified: Boolean = false,
  val content: String,
  @ColumnInfo(name = "created_at") val timestamp: Long,
  @ColumnInfo(name = "updated_at") val updatedAt: Long = timestamp,
  @ColumnInfo(name = "parent_comment_id") val parentCommentId: String? = null,
  val likesCount: Int = 0,
  val isLiked: Boolean = false
)

@Entity(
  tableName = "comment_reactions",
  indices = [
    Index(value = ["comment_id", "user_id"], unique = true),
    Index(value = ["comment_id"]),
    Index(value = ["user_id"])
  ]
)
data class CommentReactionEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "comment_id") val commentId: String,
  @ColumnInfo(name = "user_id") val userId: String,
  @ColumnInfo(name = "reaction_type") val reactionType: String,
  @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(tableName = "stories")
data class StoryEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val userName: String,
  val userAvatar: String,
  val isUserVerified: Boolean = false,
  val mediaUrl: String? = null,
  val textOverlay: String = "",
  val backgroundGradientIndex: Int = 0,
  val timestamp: Long,
  val expiresAt: Long,
  val viewsCount: Int = 12,
  val isViewed: Boolean = false
)

@Entity(tableName = "reels")
data class ReelEntity(
  @PrimaryKey val id: String,
  val creatorId: String,
  val creatorName: String,
  val creatorUsername: String,
  val creatorAvatar: String,
  val isCreatorVerified: Boolean = false,
  val videoThumbnail: String,
  val caption: String,
  val audioTitle: String,
  val likesCount: Int = 0,
  val commentsCount: Int = 0,
  val sharesCount: Int = 0,
  val isLiked: Boolean = false,
  val isSaved: Boolean = false,
  val isFollowing: Boolean = false
)

@Entity(tableName = "conversations")
data class ConversationEntity(
  @PrimaryKey val id: String,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val participantId: String = "",
  val participantName: String = "",
  val participantUsername: String = "",
  val participantAvatar: String = "",
  val isParticipantVerified: Boolean = false,
  val lastMessage: String = "",
  val lastMessageTimestamp: Long = System.currentTimeMillis(),
  val unreadCount: Int = 0,
  val isOnline: Boolean = false
)

@Entity(
  tableName = "conversation_members",
  primaryKeys = ["conversationId", "userId"],
  indices = [
    Index(value = ["conversationId"]),
    Index(value = ["userId"])
  ]
)
data class ConversationMemberEntity(
  val conversationId: String,
  val userId: String,
  val joinedAt: Long = System.currentTimeMillis(),
  val lastReadAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "messages",
  indices = [
    Index(value = ["conversationId"]),
    Index(value = ["senderId"]),
    Index(value = ["receiverId"]),
    Index(value = ["timestamp"])
  ]
)
data class MessageEntity(
  @PrimaryKey val id: String,
  val conversationId: String,
  val senderId: String,
  val receiverId: String = "",
  val messageType: String = "TEXT",
  val text: String,
  val mediaUrl: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val timestamp: Long = System.currentTimeMillis(),
  val isSeen: Boolean = false,
  val isDeleted: Boolean = false,
  val isMine: Boolean = true
)

data class ConversationWithParticipant(
  val conversationId: String,
  val lastMessage: String,
  val lastMessageTimestamp: Long,
  val participantId: String,
  val participantName: String,
  val participantUsername: String,
  val participantAvatar: String,
  val isParticipantVerified: Boolean,
  val isOnline: Boolean,
  val lastActiveAt: Long,
  val unreadCount: Int
)

@Entity(tableName = "notifications")
data class NotificationEntity(
  @PrimaryKey val id: String,
  val type: String,
  val actorName: String,
  val actorAvatar: String,
  val isActorVerified: Boolean = false,
  val messageSnippet: String,
  val timestamp: Long,
  val isRead: Boolean = false,
  val targetPostId: String? = null,
  val recipientId: String = "user_me",
  val actionData: String? = null, // e.g. relationshipId or target user id
  val senderId: String = ""
)

@Entity(
  tableName = "friend_requests",
  indices = [
    Index(value = ["senderId", "receiverId"]),
    Index(value = ["receiverId", "status"]),
    Index(value = ["senderId", "status"])
  ]
)
data class FriendRequestEntity(
  @PrimaryKey val id: String,
  val senderId: String,
  val receiverId: String,
  val status: String, // "pending", "accepted", "rejected", "cancelled"
  val createdAt: Long,
  val updatedAt: Long = createdAt,
  val senderName: String = "",
  val senderUsername: String = "",
  val senderAvatar: String = "",
  val receiverName: String = "",
  val receiverUsername: String = "",
  val receiverAvatar: String = "",
  val mutualFriendsCount: Int = 0
) {
  // Backward compatibility accessors
  val userId: String get() = senderId
  val fullName: String get() = senderName
  val username: String get() = senderUsername
  val avatarUrl: String get() = senderAvatar
  val timestamp: Long get() = createdAt
}

@Entity(
  tableName = "friends",
  indices = [
    Index(value = ["userId", "friendId"], unique = true),
    Index(value = ["friendId"])
  ]
)
data class FriendshipEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val friendId: String,
  val createdAt: Long
)

@Entity(
  tableName = "follows",
  indices = [
    Index(value = ["followerId", "followingId"], unique = true),
    Index(value = ["followingId"])
  ]
)
data class FollowEntity(
  @PrimaryKey val id: String,
  val followerId: String,
  val followingId: String,
  val createdAt: Long
)

@Entity(tableName = "pages")
data class PageEntity(
  @PrimaryKey val id: String,
  val name: String,
  val category: String,
  val description: String,
  val avatarUrl: String = "",
  val coverUrl: String = "",
  val followersCount: Int = 0,
  val isLiked: Boolean = false,
  val isAdmin: Boolean = false,
  val ownerId: String = "user_me",
  val username: String = "",
  val website: String = "",
  val location: String = "",
  val contactEmail: String = "",
  val contactPhone: String = "",
  val followingCount: Int = 0,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "groups")
data class GroupEntity(
  @PrimaryKey val id: String,
  val name: String,
  val privacy: String = "Public group",
  val description: String = "",
  val avatarUrl: String = "",
  val membersCount: Int = 1,
  val isJoined: Boolean = false,
  val role: String = "Member",
  val ownerId: String = "user_me",
  val coverUrl: String = "",
  val rules: String = "1. Be respectful and constructive.\n2. No hate speech or bullying.\n3. Respect privacy and confidentiality.\n4. Relevant discussions only.",
  val hasPendingJoinRequest: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "page_admins",
  indices = [
    Index(value = ["pageId", "userId"], unique = true),
    Index(value = ["pageId"]),
    Index(value = ["userId"])
  ]
)
data class PageAdminEntity(
  @PrimaryKey val id: String,
  val pageId: String,
  val userId: String,
  val userName: String = "",
  val userAvatar: String = "",
  val role: String = "Admin",
  val assignedAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "group_members",
  indices = [
    Index(value = ["groupId", "userId"], unique = true),
    Index(value = ["groupId"]),
    Index(value = ["userId"])
  ]
)
data class GroupMemberEntity(
  @PrimaryKey val id: String,
  val groupId: String,
  val userId: String,
  val userName: String = "",
  val userUsername: String = "",
  val userAvatar: String = "",
  val role: String = "Member", // "Owner", "Admin", "Moderator", "Member"
  val status: String = "Active", // "Active", "Pending", "Banned"
  val joinedAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "group_join_requests",
  indices = [
    Index(value = ["groupId", "userId"], unique = true),
    Index(value = ["groupId"])
  ]
)
data class GroupJoinRequestEntity(
  @PrimaryKey val id: String,
  val groupId: String,
  val userId: String,
  val userName: String,
  val userUsername: String = "",
  val userAvatar: String = "",
  val status: String = "Pending", // "Pending", "Approved", "Rejected"
  val requestedAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "page_followers",
  indices = [
    Index(value = ["pageId", "userId"], unique = true),
    Index(value = ["pageId"]),
    Index(value = ["userId"])
  ]
)
data class PageFollowerEntity(
  @PrimaryKey val id: String,
  val pageId: String,
  val userId: String,
  val userName: String = "",
  val userAvatar: String = "",
  val followedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reports")
data class ReportEntity(
  @PrimaryKey val id: String,
  val targetType: String,
  val targetId: String,
  val targetTitle: String,
  val reason: String,
  val reportedBy: String,
  val timestamp: Long,
  val status: String = "Pending"
)

@Entity(
  tableName = "relationships",
  indices = [
    Index(value = ["requesterId"]),
    Index(value = ["receiverId"]),
    Index(value = ["status"])
  ]
)
data class RelationshipEntity(
  @PrimaryKey val id: String,
  val requesterId: String,
  val receiverId: String,
  val relationshipType: String, // "In a relationship", "Engaged", "Married", "In an open relationship", "It's complicated", "Separated", "Divorced", "Widowed", "Custom"
  val customText: String? = null,
  val status: String, // "pending", "accepted", "declined", "cancelled"
  val privacy: String = "Public", // "Public", "Friends", "Only me"
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "post_tags",
  indices = [
    Index(value = ["postId"]),
    Index(value = ["taggedUserId"]),
    Index(value = ["status"])
  ]
)
data class PostTagEntity(
  @PrimaryKey val id: String,
  val postId: String,
  val taggedUserId: String,
  val taggedByUserId: String,
  val status: String = "approved", // "pending", "approved", "removed"
  val createdAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "comment_mentions",
  indices = [
    Index(value = ["commentId"]),
    Index(value = ["mentionedUserId"])
  ]
)
data class CommentMentionEntity(
  @PrimaryKey val id: String,
  val commentId: String,
  val mentionedUserId: String,
  val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
  @PrimaryKey val userId: String,
  val twoFactorEnabled: Boolean = false,
  val twoFactorMethod: String = "AUTHENTICATOR", // "AUTHENTICATOR" or "SMS"
  val profileVisibility: String = "Public", // "Public", "Friends", "Only Me"
  val darkTheme: Boolean = false,
  val dataSaver: Boolean = false,
  val pushNotifications: Boolean = true,
  val inAppSounds: Boolean = true,
  val language: String = "English",
  val passwordLastUpdated: Long = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000), // ~3 months ago
  val profileViewHistoryEnabled: Boolean = true,
  val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "blocked_users",
  indices = [
    Index(value = ["blockerId", "blockedId"], unique = true),
    Index(value = ["blockerId"]),
    Index(value = ["blockedId"])
  ]
)
data class BlockedUserEntity(
  @PrimaryKey val id: String,
  val blockerId: String,
  val blockedId: String,
  val createdAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "profile_views",
  indices = [
    Index(value = ["viewedUserId"]),
    Index(value = ["viewerUserId"]),
    Index(value = ["viewedAt"]),
    Index(value = ["viewedUserId", "viewerUserId", "viewedAt"])
  ]
)
data class ProfileViewEntity(
  @PrimaryKey val id: String,
  val viewedUserId: String,
  val viewerUserId: String,
  val viewedAt: Long = System.currentTimeMillis(),
  val createdAt: Long = System.currentTimeMillis(),
  val seenAt: Long? = null,
  val isAnonymous: Boolean = false
)

@Entity(
  tableName = "post_views",
  indices = [
    Index(value = ["postId"]),
    Index(value = ["viewerUserId"]),
    Index(value = ["viewedAt"]),
    Index(value = ["postId", "viewerUserId", "viewedAt"])
  ]
)
data class PostViewEntity(
  @PrimaryKey val id: String,
  val postId: String,
  val viewerUserId: String,
  val viewedAt: Long = System.currentTimeMillis(),
  val createdAt: Long = System.currentTimeMillis(),
  val generatedProfileVisit: Boolean = false,
  val generatedFollow: Boolean = false,
  val source: String = "Home Feed"
)

@Entity(
  tableName = "video_watch_events",
  indices = [
    Index(value = ["postId"]),
    Index(value = ["videoId"]),
    Index(value = ["viewerId"]),
    Index(value = ["watchedAt"]),
    Index(value = ["postId", "viewerId", "watchedAt"])
  ]
)
data class VideoWatchEventEntity(
  @PrimaryKey val id: String,
  val postId: String,
  val videoId: String,
  val viewerId: String,
  val sessionId: String,
  val startedAt: Long,
  val lastPosition: Long,
  val watchedDuration: Long, // in milliseconds
  val videoDuration: Long, // in milliseconds
  val completed: Boolean = false,
  val isReplay: Boolean = false,
  val source: String = "Home Feed",
  val watchedAt: Long = System.currentTimeMillis()
)


