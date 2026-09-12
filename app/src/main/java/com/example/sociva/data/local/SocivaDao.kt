package com.example.sociva.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SocivaDao {

  // Users
  @Query("SELECT * FROM users ORDER BY fullName ASC")
  fun getAllUsers(): Flow<List<UserEntity>>

  @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
  fun getUserById(userId: String): Flow<UserEntity?>

  @Query("SELECT * FROM users WHERE isFriend = 1")
  fun getFriends(): Flow<List<UserEntity>>

  @Query("DELETE FROM users WHERE id = :userId")
  suspend fun deleteUserById(userId: String)

  @Query("DELETE FROM users WHERE id = 'user_me'")
  suspend fun deleteDemoUser()

  @Query("DELETE FROM friends WHERE userId = 'user_me' OR friendId = 'user_me'")
  suspend fun deleteDemoFriendships()

  @Query("DELETE FROM follows WHERE followerId = 'user_me' OR followingId = 'user_me'")
  suspend fun deleteDemoFollows()

  @Query("DELETE FROM friend_requests WHERE senderId = 'user_me' OR receiverId = 'user_me'")
  suspend fun deleteDemoFriendRequests()

  @Query("DELETE FROM profile_views WHERE viewerUserId = 'user_me' OR viewedUserId = 'user_me' OR id LIKE 'pv_seed_%'")
  suspend fun deleteDemoProfileViews()

  @Query("DELETE FROM post_views WHERE viewerUserId = 'user_me' OR id LIKE 'pv_post%'")
  suspend fun deleteDemoPostViews()

  @Query("DELETE FROM user_settings WHERE userId = 'user_me'")
  suspend fun deleteDemoUserSettings()

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertUsers(users: List<UserEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertUser(user: UserEntity)

  @Update
  suspend fun updateUser(user: UserEntity)

  @Query("UPDATE users SET avatarUrl = :avatarUrl, profilePictureUpdatedAt = :timestamp WHERE id = :userId")
  suspend fun updateUserAvatar(userId: String, avatarUrl: String, timestamp: Long)

  @Query("UPDATE users SET coverUrl = :coverUrl, coverPhotoUpdatedAt = :timestamp WHERE id = :userId")
  suspend fun updateUserCover(userId: String, coverUrl: String, timestamp: Long)

  @Query("UPDATE posts SET authorAvatar = :avatarUrl WHERE authorId = :userId")
  suspend fun updateAuthorAvatarInPosts(userId: String, avatarUrl: String)

  @Query("UPDATE posts SET authorName = :name, authorUsername = :username WHERE authorId = :userId")
  suspend fun updateAuthorInfoInPosts(userId: String, name: String, username: String)

  @Query("UPDATE comments SET authorName = :name WHERE user_id = :userId")
  suspend fun updateAuthorNameInComments(userId: String, name: String)

  @Query("UPDATE stories SET userName = :name WHERE userId = :userId")
  suspend fun updateUserNameInStories(userId: String, name: String)

  @Query("UPDATE reels SET creatorName = :name, creatorUsername = :username WHERE creatorId = :userId")
  suspend fun updateCreatorInReels(userId: String, name: String, username: String)

  @Query("UPDATE conversations SET participantName = :name WHERE participantId = :userId")
  suspend fun updateParticipantInConversations(userId: String, name: String)

  @Query("UPDATE users SET relationshipPartnerName = :name WHERE relationshipPartnerId = :userId")
  suspend fun updatePartnerNameInUsers(userId: String, name: String)

  @Query("UPDATE comments SET authorAvatar = :avatarUrl WHERE user_id = :userId")
  suspend fun updateAuthorAvatarInComments(userId: String, avatarUrl: String)

  @Query("UPDATE stories SET userAvatar = :avatarUrl WHERE userId = :userId")
  suspend fun updateUserAvatarInStories(userId: String, avatarUrl: String)

  @Query("UPDATE reels SET creatorAvatar = :avatarUrl WHERE creatorId = :userId")
  suspend fun updateCreatorAvatarInReels(userId: String, avatarUrl: String)

  @Query("UPDATE conversations SET participantAvatar = :avatarUrl WHERE participantId = :userId")
  suspend fun updateParticipantAvatarInConversations(userId: String, avatarUrl: String)

  @Query("UPDATE notifications SET actorAvatar = :avatarUrl WHERE actorName = :actorName")
  suspend fun updateActorAvatarInNotifications(actorName: String, avatarUrl: String)

  // Posts
  @Query("SELECT * FROM posts ORDER BY timestamp DESC")
  fun getAllPosts(): Flow<List<PostEntity>>

  @Query("SELECT * FROM posts WHERE id = :postId LIMIT 1")
  fun getPostById(postId: String): Flow<PostEntity?>

  @Query("SELECT * FROM posts WHERE id = :postId LIMIT 1")
  suspend fun findPostById(postId: String): PostEntity?

  @Query("SELECT * FROM posts WHERE authorId = :userId ORDER BY timestamp DESC")
  fun getPostsByAuthor(userId: String): Flow<List<PostEntity>>

  @Query("SELECT * FROM posts WHERE isSaved = 1 ORDER BY timestamp DESC")
  fun getSavedPosts(): Flow<List<PostEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPosts(posts: List<PostEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPost(post: PostEntity)

  @Update
  suspend fun updatePost(post: PostEntity)

  @Query("DELETE FROM posts WHERE id = :postId")
  suspend fun deletePostById(postId: String)

  @Query("SELECT * FROM posts WHERE id IN (:postIds)")
  suspend fun findPostsByIds(postIds: List<String>): List<PostEntity>

  @Query("UPDATE posts SET sharesCount = sharesCount + 1 WHERE id = :postId")
  suspend fun incrementSharesCount(postId: String)

  @Query("UPDATE posts SET sharesCount = CASE WHEN sharesCount > 0 THEN sharesCount - 1 ELSE 0 END WHERE id = :postId")
  suspend fun decrementSharesCount(postId: String)

  @Query("UPDATE posts SET content = :newContent WHERE id = :postId")
  suspend fun updatePostContent(postId: String, newContent: String)

  // Comments
  @Query("SELECT * FROM comments WHERE post_id = :postId ORDER BY created_at ASC")
  fun getCommentsForPost(postId: String): Flow<List<CommentEntity>>

  @Query("SELECT * FROM comments WHERE id = :commentId LIMIT 1")
  fun getCommentById(commentId: String): Flow<CommentEntity?>

  @Query("SELECT * FROM comments WHERE id = :commentId LIMIT 1")
  suspend fun findCommentById(commentId: String): CommentEntity?

  @Query("SELECT * FROM comments WHERE parent_comment_id = :parentCommentId ORDER BY created_at ASC")
  fun getRepliesForComment(parentCommentId: String): Flow<List<CommentEntity>>

  @Query("SELECT * FROM comments WHERE parent_comment_id = :parentCommentId ORDER BY created_at ASC")
  suspend fun getRepliesForCommentList(parentCommentId: String): List<CommentEntity>

  @Query("SELECT COUNT(*) FROM comments WHERE post_id = :postId")
  suspend fun countAllCommentsForPost(postId: String): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertComments(comments: List<CommentEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertComment(comment: CommentEntity)

  @Update
  suspend fun updateComment(comment: CommentEntity)

  @Query("UPDATE comments SET likesCount = :likesCount WHERE id = :commentId")
  suspend fun updateCommentLikesCount(commentId: String, likesCount: Int)

  @Query("UPDATE posts SET commentsCount = :count WHERE id = :postId")
  suspend fun updatePostCommentsCount(postId: String, count: Int)

  @Query("DELETE FROM comments WHERE id = :commentId")
  suspend fun deleteComment(commentId: String)

  @Query("DELETE FROM comments WHERE parent_comment_id = :parentCommentId")
  suspend fun deleteRepliesForComment(parentCommentId: String)

  // Comment Reactions
  @Query("SELECT * FROM comment_reactions WHERE comment_id IN (SELECT id FROM comments WHERE post_id = :postId)")
  fun getReactionsForPostComments(postId: String): Flow<List<CommentReactionEntity>>

  @Query("SELECT * FROM comment_reactions WHERE comment_id = :commentId")
  fun getReactionsForComment(commentId: String): Flow<List<CommentReactionEntity>>

  @Query("SELECT * FROM comment_reactions WHERE comment_id = :commentId AND user_id = :userId LIMIT 1")
  fun getCommentReaction(commentId: String, userId: String): Flow<CommentReactionEntity?>

  @Query("SELECT * FROM comment_reactions WHERE comment_id = :commentId AND user_id = :userId LIMIT 1")
  suspend fun findCommentReaction(commentId: String, userId: String): CommentReactionEntity?

  @Query("SELECT COUNT(*) FROM comment_reactions WHERE comment_id = :commentId")
  suspend fun countReactionsForComment(commentId: String): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCommentReaction(reaction: CommentReactionEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCommentReactions(reactions: List<CommentReactionEntity>)

  @Query("DELETE FROM comment_reactions WHERE comment_id = :commentId AND user_id = :userId")
  suspend fun deleteCommentReaction(commentId: String, userId: String)

  @Query("DELETE FROM comment_reactions WHERE comment_id = :commentId")
  suspend fun deleteReactionsForComment(commentId: String)

  // Post Reactions
  @Query("SELECT * FROM post_reactions ORDER BY created_at DESC")
  fun getAllPostReactions(): Flow<List<PostReactionEntity>>

  @Query("SELECT * FROM post_reactions WHERE post_id = :postId ORDER BY created_at DESC")
  fun getReactionsForPost(postId: String): Flow<List<PostReactionEntity>>

  @Query("SELECT * FROM post_reactions WHERE post_id = :postId AND reaction_type = :reactionType ORDER BY created_at DESC")
  fun getReactionsForPostByType(postId: String, reactionType: String): Flow<List<PostReactionEntity>>

  @Query("SELECT * FROM post_reactions WHERE post_id = :postId AND user_id = :userId LIMIT 1")
  fun getPostReaction(postId: String, userId: String): Flow<PostReactionEntity?>

  @Query("SELECT * FROM post_reactions WHERE post_id = :postId AND user_id = :userId LIMIT 1")
  suspend fun findPostReaction(postId: String, userId: String): PostReactionEntity?

  @Query("SELECT COUNT(*) FROM post_reactions WHERE post_id = :postId")
  suspend fun countReactionsForPost(postId: String): Int

  @Query("SELECT COUNT(*) FROM post_reactions WHERE post_id = :postId AND reaction_type = :reactionType")
  suspend fun countReactionsForPostByType(postId: String, reactionType: String): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPostReaction(reaction: PostReactionEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPostReactions(reactions: List<PostReactionEntity>)

  @Query("DELETE FROM post_reactions WHERE post_id = :postId AND user_id = :userId")
  suspend fun deletePostReaction(postId: String, userId: String)

  @Query("DELETE FROM post_reactions WHERE post_id = :postId")
  suspend fun deleteReactionsForPost(postId: String)

  // Stories
  @Query("SELECT * FROM stories WHERE expiresAt > :currentTime ORDER BY timestamp DESC")
  fun getActiveStories(currentTime: Long): Flow<List<StoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertStories(stories: List<StoryEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertStory(story: StoryEntity)

  @Query("UPDATE stories SET isViewed = 1, viewsCount = viewsCount + 1 WHERE id = :storyId")
  suspend fun markStoryViewed(storyId: String)

  // Reels
  @Query("SELECT * FROM reels")
  fun getAllReels(): Flow<List<ReelEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertReels(reels: List<ReelEntity>)

  @Update
  suspend fun updateReel(reel: ReelEntity)

  // Conversations & Messages
  @Query("""
    SELECT 
      c.id AS conversationId,
      c.lastMessage AS lastMessage,
      c.lastMessageTimestamp AS lastMessageTimestamp,
      u.id AS participantId,
      u.fullName AS participantName,
      u.username AS participantUsername,
      u.avatarUrl AS participantAvatar,
      u.isVerified AS isParticipantVerified,
      u.isOnline AS isOnline,
      u.lastActiveAt AS lastActiveAt,
      (SELECT COUNT(*) FROM messages m 
       WHERE m.conversationId = c.id 
         AND (m.receiverId = :userId OR (m.receiverId = '' AND m.senderId != :userId))
         AND m.isSeen = 0 
         AND m.isDeleted = 0) AS unreadCount
    FROM conversations c
    INNER JOIN conversation_members my_mem ON c.id = my_mem.conversationId AND my_mem.userId = :userId
    INNER JOIN conversation_members other_mem ON c.id = other_mem.conversationId AND other_mem.userId != :userId
    INNER JOIN users u ON other_mem.userId = u.id
    ORDER BY c.lastMessageTimestamp DESC
  """)
  fun getConversationsForUser(userId: String): Flow<List<ConversationWithParticipant>>

  @Query("""
    SELECT cm1.conversationId FROM conversation_members cm1
    INNER JOIN conversation_members cm2 ON cm1.conversationId = cm2.conversationId
    WHERE cm1.userId = :userA AND cm2.userId = :userB
    LIMIT 1
  """)
  suspend fun findDirectConversation(userA: String, userB: String): String?

  @Query("SELECT * FROM conversations WHERE id = :convId LIMIT 1")
  suspend fun getConversationById(convId: String): ConversationEntity?

  @Query("SELECT * FROM conversations ORDER BY lastMessageTimestamp DESC")
  fun getAllConversations(): Flow<List<ConversationEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertConversations(conversations: List<ConversationEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertConversation(conversation: ConversationEntity)

  @Query("UPDATE conversations SET lastMessage = :lastMsg, lastMessageTimestamp = :timestamp, updatedAt = :timestamp WHERE id = :convId")
  suspend fun updateConversationLastMessage(convId: String, lastMsg: String, timestamp: Long)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertConversationMember(member: ConversationMemberEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertConversationMembers(members: List<ConversationMemberEntity>)

  @Query("SELECT * FROM conversation_members WHERE conversationId = :convId")
  suspend fun getConversationMembers(convId: String): List<ConversationMemberEntity>

  @Query("SELECT * FROM conversation_members WHERE conversationId = :convId AND userId != :currentUserId LIMIT 1")
  suspend fun getOtherMember(convId: String, currentUserId: String): ConversationMemberEntity?

  @Query("UPDATE conversation_members SET lastReadAt = :readTime WHERE conversationId = :convId AND userId = :userId")
  suspend fun updateMemberLastRead(convId: String, userId: String, readTime: Long)

  @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
  fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessages(messages: List<MessageEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(message: MessageEntity)

  @Query("UPDATE messages SET isSeen = 1, updatedAt = :seenTime WHERE conversationId = :convId AND senderId != :currentUserId AND isSeen = 0")
  suspend fun markMessagesAsSeen(convId: String, currentUserId: String, seenTime: Long)

  @Query("UPDATE messages SET isDeleted = 1, text = :unsentText, updatedAt = :timestamp WHERE id = :messageId")
  suspend fun softDeleteMessage(messageId: String, unsentText: String = "This message was unsent", timestamp: Long = System.currentTimeMillis())

  @Query("DELETE FROM messages WHERE id = :messageId")
  suspend fun deleteMessage(messageId: String)

  @Query("UPDATE users SET isOnline = :isOnline, lastActiveAt = :lastActiveAt WHERE id = :userId")
  suspend fun updateUserPresence(userId: String, isOnline: Boolean, lastActiveAt: Long)

  @Query("SELECT * FROM users WHERE id != :currentUserId AND isOnline = 1 ORDER BY lastActiveAt DESC")
  fun getOnlineUsers(currentUserId: String): Flow<List<UserEntity>>

  @Query("SELECT * FROM users WHERE id != :currentUserId AND (fullName LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%') ORDER BY fullName ASC")
  fun searchUsers(query: String, currentUserId: String): Flow<List<UserEntity>>

  @Query("SELECT * FROM users WHERE id != :currentUserId ORDER BY fullName ASC")
  fun getAllUsersExcept(currentUserId: String): Flow<List<UserEntity>>

  // Notifications
  @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
  fun getAllNotifications(): Flow<List<NotificationEntity>>

  @Query("SELECT * FROM notifications WHERE recipientId = :recipientId ORDER BY timestamp DESC")
  fun getNotificationsForRecipient(recipientId: String): Flow<List<NotificationEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertNotifications(notifications: List<NotificationEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertNotification(notification: NotificationEntity)

  @Query("UPDATE notifications SET isRead = 1")
  suspend fun markAllNotificationsAsRead()

  @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
  suspend fun markNotificationAsRead(id: String)

  // Friend Requests
  @Query("SELECT * FROM friend_requests ORDER BY createdAt DESC")
  fun getAllFriendRequests(): Flow<List<FriendRequestEntity>>

  @Query("SELECT * FROM friend_requests WHERE receiverId = :receiverId AND status = 'pending' ORDER BY createdAt DESC")
  fun getIncomingFriendRequests(receiverId: String): Flow<List<FriendRequestEntity>>

  @Query("SELECT * FROM friend_requests WHERE senderId = :senderId AND status = 'pending' ORDER BY createdAt DESC")
  fun getSentFriendRequests(senderId: String): Flow<List<FriendRequestEntity>>

  @Query("SELECT * FROM friend_requests WHERE id = :requestId LIMIT 1")
  suspend fun getFriendRequestById(requestId: String): FriendRequestEntity?

  @Query("SELECT * FROM friend_requests WHERE senderId = :senderId AND receiverId = :receiverId AND status = 'pending' LIMIT 1")
  suspend fun getPendingRequest(senderId: String, receiverId: String): FriendRequestEntity?

  @Query("SELECT * FROM friend_requests WHERE ((senderId = :userA AND receiverId = :userB) OR (senderId = :userB AND receiverId = :userA)) AND status = 'pending' LIMIT 1")
  fun getPendingRequestBetween(userA: String, userB: String): Flow<FriendRequestEntity?>

  @Query("SELECT * FROM friend_requests WHERE ((senderId = :userA AND receiverId = :userB) OR (senderId = :userB AND receiverId = :userA)) AND status = 'pending' LIMIT 1")
  suspend fun getPendingRequestBetweenSync(userA: String, userB: String): FriendRequestEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFriendRequests(requests: List<FriendRequestEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFriendRequest(request: FriendRequestEntity)

  @Update
  suspend fun updateFriendRequest(request: FriendRequestEntity)

  @Query("DELETE FROM friend_requests WHERE id = :requestId")
  suspend fun deleteFriendRequest(requestId: String)

  @Query("DELETE FROM friend_requests WHERE (senderId = :userA AND receiverId = :userB) OR (senderId = :userB AND receiverId = :userA)")
  suspend fun deleteFriendRequestsBetween(userA: String, userB: String)

  // Friendships
  @Query("SELECT * FROM friends WHERE userId = :userId ORDER BY createdAt DESC")
  fun getFriendshipsForUser(userId: String): Flow<List<FriendshipEntity>>

  @Query("SELECT * FROM friends")
  fun getAllFriendships(): Flow<List<FriendshipEntity>>

  @Query("SELECT friendId FROM friends WHERE userId = :userId")
  fun getFriendIdsForUser(userId: String): Flow<List<String>>

  @Query("SELECT COUNT(*) > 0 FROM friends WHERE (userId = :userA AND friendId = :userB) OR (userId = :userB AND friendId = :userA)")
  fun hasFriendshipFlow(userA: String, userB: String): Flow<Boolean>

  @Query("SELECT COUNT(*) > 0 FROM friends WHERE (userId = :userA AND friendId = :userB) OR (userId = :userB AND friendId = :userA)")
  suspend fun hasFriendship(userA: String, userB: String): Boolean

  @Query("SELECT COUNT(*) FROM friends WHERE userId = :userId")
  suspend fun getFriendsCount(userId: String): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFriendship(friendship: FriendshipEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFriendships(friendships: List<FriendshipEntity>)

  @Query("DELETE FROM friends WHERE (userId = :userA AND friendId = :userB) OR (userId = :userB AND friendId = :userA)")
  suspend fun deleteFriendshipBetween(userA: String, userB: String)

  // Follows
  @Query("SELECT * FROM follows WHERE followerId = :followerId ORDER BY createdAt DESC")
  fun getFollowsForUser(followerId: String): Flow<List<FollowEntity>>

  @Query("SELECT COUNT(*) > 0 FROM follows WHERE followerId = :followerId AND followingId = :followingId")
  fun isFollowingFlow(followerId: String, followingId: String): Flow<Boolean>

  @Query("SELECT COUNT(*) > 0 FROM follows WHERE followerId = :followerId AND followingId = :followingId")
  suspend fun isFollowing(followerId: String, followingId: String): Boolean

  @Query("SELECT COUNT(*) FROM follows WHERE followingId = :userId")
  suspend fun getFollowersCount(userId: String): Int

  @Query("SELECT COUNT(*) FROM follows WHERE followerId = :userId")
  suspend fun getFollowingCount(userId: String): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFollow(follow: FollowEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFollows(follows: List<FollowEntity>)

  @Query("DELETE FROM follows WHERE followerId = :followerId AND followingId = :followingId")
  suspend fun deleteFollow(followerId: String, followingId: String)

  // Pages
  @Query("SELECT * FROM pages")
  fun getAllPages(): Flow<List<PageEntity>>

  @Query("SELECT * FROM pages WHERE id = :id")
  fun getPageById(id: String): Flow<PageEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPages(pages: List<PageEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPage(page: PageEntity)

  @Update
  suspend fun updatePage(page: PageEntity)

  @Query("DELETE FROM pages WHERE id = :id")
  suspend fun deletePage(id: String)

  // Page Admins
  @Query("SELECT * FROM page_admins WHERE pageId = :pageId")
  fun getPageAdmins(pageId: String): Flow<List<PageAdminEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPageAdmin(admin: PageAdminEntity)

  @Query("DELETE FROM page_admins WHERE pageId = :pageId AND userId = :userId")
  suspend fun deletePageAdmin(pageId: String, userId: String)

  // Page Followers
  @Query("SELECT * FROM page_followers WHERE pageId = :pageId")
  fun getPageFollowers(pageId: String): Flow<List<PageFollowerEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPageFollower(follower: PageFollowerEntity)

  @Query("DELETE FROM page_followers WHERE pageId = :pageId AND userId = :userId")
  suspend fun deletePageFollower(pageId: String, userId: String)

  @Query("SELECT EXISTS(SELECT 1 FROM page_followers WHERE pageId = :pageId AND userId = :userId)")
  fun isUserPageFollower(pageId: String, userId: String): Flow<Boolean>

  // Groups
  @Query("SELECT * FROM groups")
  fun getAllGroups(): Flow<List<GroupEntity>>

  @Query("SELECT * FROM groups WHERE id = :id")
  fun getGroupById(id: String): Flow<GroupEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGroups(groups: List<GroupEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGroup(group: GroupEntity)

  @Update
  suspend fun updateGroup(group: GroupEntity)

  @Query("DELETE FROM groups WHERE id = :id")
  suspend fun deleteGroup(id: String)

  // Group Members
  @Query("SELECT * FROM group_members WHERE groupId = :groupId")
  fun getGroupMembers(groupId: String): Flow<List<GroupMemberEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGroupMember(member: GroupMemberEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGroupMembers(members: List<GroupMemberEntity>)

  @Update
  suspend fun updateGroupMember(member: GroupMemberEntity)

  @Query("DELETE FROM group_members WHERE groupId = :groupId AND userId = :userId")
  suspend fun deleteGroupMember(groupId: String, userId: String)

  // Group Join Requests
  @Query("SELECT * FROM group_join_requests WHERE groupId = :groupId AND status = 'Pending' ORDER BY requestedAt DESC")
  fun getGroupJoinRequests(groupId: String): Flow<List<GroupJoinRequestEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGroupJoinRequest(request: GroupJoinRequestEntity)

  @Update
  suspend fun updateGroupJoinRequest(request: GroupJoinRequestEntity)

  @Query("DELETE FROM group_join_requests WHERE id = :id")
  suspend fun deleteGroupJoinRequest(id: String)

  @Query("DELETE FROM group_join_requests WHERE groupId = :groupId AND userId = :userId")
  suspend fun deleteGroupJoinRequestForUser(groupId: String, userId: String)

  // Page & Group Posts
  @Query("SELECT * FROM posts WHERE authorId = :pageId OR (authorType = 'PAGE' AND authorId = :pageId) ORDER BY timestamp DESC")
  fun getPostsForPage(pageId: String): Flow<List<PostEntity>>

  @Query("SELECT * FROM posts WHERE targetGroupId = :groupId ORDER BY timestamp DESC")
  fun getPostsForGroup(groupId: String): Flow<List<PostEntity>>

  // Reports
  @Query("SELECT * FROM reports ORDER BY timestamp DESC")
  fun getAllReports(): Flow<List<ReportEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertReports(reports: List<ReportEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertReport(report: ReportEntity)

  @Query("UPDATE reports SET status = 'Resolved' WHERE id = :reportId")
  suspend fun resolveReport(reportId: String)

  @Query("DELETE FROM reports WHERE id = :reportId")
  suspend fun deleteReport(reportId: String)

  // Relationships
  @Query("SELECT * FROM relationships WHERE (requesterId = :userId OR receiverId = :userId) AND status = 'accepted' LIMIT 1")
  fun getActiveRelationship(userId: String): Flow<RelationshipEntity?>

  @Query("SELECT * FROM relationships WHERE (requesterId = :userId OR receiverId = :userId) AND status = 'accepted' LIMIT 1")
  suspend fun getActiveRelationshipSync(userId: String): RelationshipEntity?

  @Query("SELECT * FROM relationships WHERE receiverId = :userId AND status = 'pending' ORDER BY createdAt DESC")
  fun getIncomingRelationshipRequests(userId: String): Flow<List<RelationshipEntity>>

  @Query("SELECT * FROM relationships WHERE requesterId = :userId AND status = 'pending' ORDER BY createdAt DESC")
  fun getSentRelationshipRequests(userId: String): Flow<List<RelationshipEntity>>

  @Query("SELECT * FROM relationships WHERE ((requesterId = :userA AND receiverId = :userB) OR (requesterId = :userB AND receiverId = :userA)) AND status = 'pending' LIMIT 1")
  suspend fun getPendingRelationshipBetween(userA: String, userB: String): RelationshipEntity?

  @Query("SELECT * FROM relationships WHERE id = :relationshipId LIMIT 1")
  suspend fun getRelationshipById(relationshipId: String): RelationshipEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRelationship(relationship: RelationshipEntity)

  @Update
  suspend fun updateRelationship(relationship: RelationshipEntity)

  @Query("DELETE FROM relationships WHERE id = :relationshipId")
  suspend fun deleteRelationship(relationshipId: String)

  @Query("DELETE FROM relationships WHERE (requesterId = :userA AND receiverId = :userB) OR (requesterId = :userB AND receiverId = :userA)")
  suspend fun deleteRelationshipsBetween(userA: String, userB: String)

  @Query("UPDATE users SET relationshipStatus = :status, relationshipPartnerId = :partnerId, relationshipPartnerName = :partnerName WHERE id = :userId")
  suspend fun updateUserRelationship(userId: String, status: String, partnerId: String?, partnerName: String?)

  // Post Tags
  @Query("SELECT * FROM post_tags WHERE postId = :postId AND status = 'approved'")
  fun getPostTagsFlow(postId: String): Flow<List<PostTagEntity>>

  @Query("SELECT * FROM post_tags WHERE postId = :postId AND status = 'approved'")
  suspend fun getPostTags(postId: String): List<PostTagEntity>

  @Query("SELECT p.* FROM posts p INNER JOIN post_tags pt ON p.id = pt.postId WHERE pt.taggedUserId = :userId AND pt.status = 'approved' ORDER BY p.timestamp DESC")
  fun getTaggedPostsForUser(userId: String): Flow<List<PostEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPostTag(tag: PostTagEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPostTags(tags: List<PostTagEntity>)

  @Query("DELETE FROM post_tags WHERE postId = :postId AND taggedUserId = :userId")
  suspend fun removePostTag(postId: String, userId: String)

  @Query("UPDATE post_tags SET status = :status WHERE postId = :postId AND taggedUserId = :userId")
  suspend fun updatePostTagStatus(postId: String, userId: String, status: String)

  // Comment Mentions
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCommentMention(mention: CommentMentionEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCommentMentions(mentions: List<CommentMentionEntity>)

  @Query("SELECT * FROM comment_mentions WHERE commentId = :commentId")
  suspend fun getCommentMentions(commentId: String): List<CommentMentionEntity>

  // User Settings
  @Query("SELECT * FROM user_settings WHERE userId = :userId LIMIT 1")
  fun getUserSettings(userId: String): Flow<UserSettingsEntity?>

  @Query("SELECT * FROM user_settings WHERE userId = :userId LIMIT 1")
  suspend fun getUserSettingsSync(userId: String): UserSettingsEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdateUserSettings(settings: UserSettingsEntity)

  @Query("UPDATE user_settings SET darkTheme = :darkTheme, updatedAt = :timestamp WHERE userId = :userId")
  suspend fun updateDarkTheme(userId: String, darkTheme: Boolean, timestamp: Long = System.currentTimeMillis())

  @Query("UPDATE user_settings SET dataSaver = :dataSaver, updatedAt = :timestamp WHERE userId = :userId")
  suspend fun updateDataSaver(userId: String, dataSaver: Boolean, timestamp: Long = System.currentTimeMillis())

  @Query("UPDATE user_settings SET pushNotifications = :pushNotifications, updatedAt = :timestamp WHERE userId = :userId")
  suspend fun updatePushNotifications(userId: String, pushNotifications: Boolean, timestamp: Long = System.currentTimeMillis())

  @Query("UPDATE user_settings SET inAppSounds = :inAppSounds, updatedAt = :timestamp WHERE userId = :userId")
  suspend fun updateInAppSounds(userId: String, inAppSounds: Boolean, timestamp: Long = System.currentTimeMillis())

  @Query("UPDATE user_settings SET profileVisibility = :visibility, updatedAt = :timestamp WHERE userId = :userId")
  suspend fun updateProfileVisibility(userId: String, visibility: String, timestamp: Long = System.currentTimeMillis())

  @Query("UPDATE user_settings SET twoFactorEnabled = :enabled, twoFactorMethod = :method, updatedAt = :timestamp WHERE userId = :userId")
  suspend fun updateTwoFactor(userId: String, enabled: Boolean, method: String, timestamp: Long = System.currentTimeMillis())

  @Query("UPDATE user_settings SET passwordLastUpdated = :timestamp, updatedAt = :timestamp WHERE userId = :userId")
  suspend fun updatePasswordLastUpdated(userId: String, timestamp: Long = System.currentTimeMillis())

  @Query("UPDATE user_settings SET profileViewHistoryEnabled = :enabled, updatedAt = :timestamp WHERE userId = :userId")
  suspend fun updateProfileViewHistoryEnabled(userId: String, enabled: Boolean, timestamp: Long = System.currentTimeMillis())

  // Blocked Users
  @Query("SELECT * FROM blocked_users WHERE blockerId = :blockerId ORDER BY createdAt DESC")
  fun getBlockedUsersForUser(blockerId: String): Flow<List<BlockedUserEntity>>

  @Query("SELECT * FROM blocked_users WHERE blockerId = :blockerId ORDER BY createdAt DESC")
  suspend fun getBlockedUsersForUserSync(blockerId: String): List<BlockedUserEntity>

  @Query("SELECT COUNT(*) > 0 FROM blocked_users WHERE blockerId = :blockerId AND blockedId = :blockedId")
  fun isUserBlockedFlow(blockerId: String, blockedId: String): Flow<Boolean>

  @Query("SELECT COUNT(*) > 0 FROM blocked_users WHERE blockerId = :blockerId AND blockedId = :blockedId")
  suspend fun isUserBlocked(blockerId: String, blockedId: String): Boolean

  @Query("SELECT COUNT(*) > 0 FROM blocked_users WHERE (blockerId = :userA AND blockedId = :userB) OR (blockerId = :userB AND blockedId = :userA)")
  fun isBlockedEitherWayFlow(userA: String, userB: String): Flow<Boolean>

  @Query("SELECT COUNT(*) > 0 FROM blocked_users WHERE (blockerId = :userA AND blockedId = :userB) OR (blockerId = :userB AND blockedId = :userA)")
  suspend fun isBlockedEitherWay(userA: String, userB: String): Boolean

  @Query("SELECT blockedId FROM blocked_users WHERE blockerId = :userId")
  fun getBlockedUserIdsFlow(userId: String): Flow<List<String>>

  @Query("SELECT blockedId FROM blocked_users WHERE blockerId = :userId")
  suspend fun getBlockedUserIds(userId: String): List<String>

  @Query("SELECT blockerId FROM blocked_users WHERE blockedId = :userId")
  suspend fun getBlockerIds(userId: String): List<String>

  @Query("SELECT blockedId FROM blocked_users WHERE blockerId = :userId UNION SELECT blockerId FROM blocked_users WHERE blockedId = :userId")
  fun getAllBlockedEitherWayIdsFlow(userId: String): Flow<List<String>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBlock(block: BlockedUserEntity)

  @Query("DELETE FROM blocked_users WHERE blockerId = :blockerId AND blockedId = :blockedId")
  suspend fun deleteBlock(blockerId: String, blockedId: String)

  // Profile Views
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertProfileView(view: ProfileViewEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertProfileViews(views: List<ProfileViewEntity>)

  @Query("""
    SELECT * FROM profile_views 
    WHERE viewedUserId = :viewedUserId 
      AND viewerUserId = :viewerUserId 
      AND viewedAt >= :thresholdTime 
    ORDER BY viewedAt DESC 
    LIMIT 1
  """)
  suspend fun getRecentProfileView(viewedUserId: String, viewerUserId: String, thresholdTime: Long): ProfileViewEntity?

  @Query("SELECT COUNT(*) FROM profile_views WHERE viewedUserId = :viewedUserId")
  fun getTotalProfileViewsCount(viewedUserId: String): Flow<Int>

  @Query("SELECT COUNT(*) FROM profile_views WHERE viewedUserId = :viewedUserId AND viewedAt >= :sinceTimestamp")
  fun getProfileViewsCountSince(viewedUserId: String, sinceTimestamp: Long): Flow<Int>

  @Query("SELECT COUNT(*) FROM profile_views WHERE viewedUserId = :viewedUserId AND seenAt IS NULL AND isAnonymous = 0 AND viewerUserId != ''")
  fun getUnseenProfileViewsCount(viewedUserId: String): Flow<Int>

  @Query("UPDATE profile_views SET seenAt = :seenAt WHERE viewedUserId = :viewedUserId AND seenAt IS NULL")
  suspend fun markAllProfileViewsSeen(viewedUserId: String, seenAt: Long = System.currentTimeMillis())

  @Query("""
    SELECT * FROM profile_views 
    WHERE viewedUserId = :viewedUserId 
      AND isAnonymous = 0 
      AND viewerUserId != '' 
    ORDER BY viewedAt DESC 
    LIMIT :limit OFFSET :offset
  """)
  fun getProfileVisitorsPaged(viewedUserId: String, limit: Int, offset: Int): Flow<List<ProfileViewEntity>>

  @Query("""
    SELECT * FROM profile_views 
    WHERE viewedUserId = :viewedUserId 
      AND isAnonymous = 0 
      AND viewerUserId != '' 
    ORDER BY viewedAt DESC 
    LIMIT :limit
  """)
  fun getProfileVisitors(viewedUserId: String, limit: Int): Flow<List<ProfileViewEntity>>

  @Query("""
    SELECT COUNT(*) FROM friends f1 
    INNER JOIN friends f2 ON f1.friendId = f2.friendId 
    WHERE f1.userId = :userA AND f2.userId = :userB
  """)
  suspend fun getMutualFriendsCount(userA: String, userB: String): Int

  @Query("DELETE FROM profile_views WHERE viewedUserId = :userId OR viewerUserId = :userId")
  suspend fun deleteProfileViewsForUser(userId: String)

  // ==========================================
  // Post Views & Analytics Queries
  // ==========================================

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPostView(postView: PostViewEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPostViews(postViews: List<PostViewEntity>)

  @Query("""
    SELECT * FROM post_views 
    WHERE postId = :postId 
      AND viewerUserId = :viewerUserId 
      AND viewedAt >= :thresholdTime 
    ORDER BY viewedAt DESC 
    LIMIT 1
  """)
  suspend fun getRecentPostView(postId: String, viewerUserId: String, thresholdTime: Long): PostViewEntity?

  @Query("SELECT COUNT(*) FROM post_views WHERE postId = :postId")
  fun getTotalViewsForPost(postId: String): Flow<Int>

  @Query("SELECT COUNT(DISTINCT viewerUserId) FROM post_views WHERE postId = :postId")
  fun getUniqueViewersForPost(postId: String): Flow<Int>

  @Query("SELECT COUNT(*) FROM post_reactions WHERE post_id = :postId")
  fun getReactionsCountForPost(postId: String): Flow<Int>

  @Query("SELECT COUNT(*) FROM comments WHERE post_id = :postId")
  fun getCommentsCountForPost(postId: String): Flow<Int>

  @Query("SELECT COUNT(*) FROM comments WHERE post_id = :postId AND parent_comment_id IS NOT NULL")
  fun getRepliesCountForPost(postId: String): Flow<Int>

  @Query("SELECT COUNT(*) FROM posts WHERE originalPostId = :postId")
  fun getSharesCountForPost(postId: String): Flow<Int>

  @Query("SELECT COUNT(*) FROM posts WHERE id = :postId AND isSaved = 1")
  fun getSavesCountForPost(postId: String): Flow<Int>

  @Query("SELECT COUNT(*) FROM post_views WHERE postId = :postId AND generatedProfileVisit = 1")
  fun getProfileVisitsFromPost(postId: String): Flow<Int>

  @Query("SELECT reaction_type AS reactionType, COUNT(*) as count FROM post_reactions WHERE post_id = :postId GROUP BY reaction_type")
  fun getReactionBreakdownForPost(postId: String): Flow<List<ReactionCountResult>>

  @Query("""
    SELECT u.* FROM users u 
    INNER JOIN post_views pv ON u.id = pv.viewerUserId 
    WHERE pv.postId = :postId 
    GROUP BY u.id 
    ORDER BY MAX(pv.viewedAt) DESC 
    LIMIT :limit
  """)
  fun getRecentViewersForPost(postId: String, limit: Int = 10): Flow<List<UserEntity>>

  @Query("SELECT * FROM post_views WHERE postId = :postId ORDER BY viewedAt ASC")
  fun getAllViewsForPost(postId: String): Flow<List<PostViewEntity>>

  // Profile Analytics Aggregations
  @Query("""
    SELECT COUNT(pv.id) FROM post_views pv 
    INNER JOIN posts p ON pv.postId = p.id 
    WHERE p.authorId = :userId 
      AND (:sinceTimestamp <= 0 OR pv.viewedAt >= :sinceTimestamp)
  """)
  fun getTotalPostViewsForUser(userId: String, sinceTimestamp: Long): Flow<Int>

  @Query("""
    SELECT COUNT(pr.id) FROM post_reactions pr 
    INNER JOIN posts p ON pr.post_id = p.id 
    WHERE p.authorId = :userId 
      AND (:sinceTimestamp <= 0 OR pr.created_at >= :sinceTimestamp)
  """)
  fun getTotalReactionsForUser(userId: String, sinceTimestamp: Long): Flow<Int>

  @Query("""
    SELECT COUNT(c.id) FROM comments c 
    INNER JOIN posts p ON c.post_id = p.id 
    WHERE p.authorId = :userId 
      AND (:sinceTimestamp <= 0 OR c.created_at >= :sinceTimestamp)
  """)
  fun getTotalCommentsForUser(userId: String, sinceTimestamp: Long): Flow<Int>

  @Query("""
    SELECT COUNT(*) FROM posts 
    WHERE originalPostId IN (SELECT id FROM posts WHERE authorId = :userId)
      AND (:sinceTimestamp <= 0 OR timestamp >= :sinceTimestamp)
  """)
  fun getTotalSharesForUser(userId: String, sinceTimestamp: Long): Flow<Int>

  @Query("""
    SELECT COUNT(*) FROM posts 
    WHERE authorId = :userId AND isSaved = 1
  """)
  fun getTotalSavesForUser(userId: String): Flow<Int>

  @Query("""
    SELECT COUNT(*) FROM follows 
    WHERE followingId = :userId 
      AND (:sinceTimestamp <= 0 OR createdAt >= :sinceTimestamp)
  """)
  fun getFollowersGainedForUser(userId: String, sinceTimestamp: Long): Flow<Int>

  @Query("""
    SELECT * FROM posts 
    WHERE authorId = :userId 
    ORDER BY (likesCount + commentsCount * 2 + sharesCount * 3) DESC 
    LIMIT :limit
  """)
  fun getBestPerformingPostsForUser(userId: String, limit: Int = 5): Flow<List<PostEntity>>

  @Query("""
    SELECT pv.* FROM post_views pv 
    INNER JOIN posts p ON pv.postId = p.id 
    WHERE p.authorId = :userId 
      AND pv.viewedAt >= :sinceTimestamp 
    ORDER BY pv.viewedAt ASC
  """)
  fun getPostViewsForUserSince(userId: String, sinceTimestamp: Long): Flow<List<PostViewEntity>>

  // ==========================================
  // Video Analytics & Watched Events Queries
  // ==========================================

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertVideoWatchEvent(event: VideoWatchEventEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertVideoWatchEvents(events: List<VideoWatchEventEntity>)

  @Query("""
    SELECT * FROM video_watch_events 
    WHERE postId = :postId 
      AND (:sinceTimestamp <= 0 OR watchedAt >= :sinceTimestamp)
    ORDER BY watchedAt ASC
  """)
  fun getVideoWatchEventsForPost(postId: String, sinceTimestamp: Long = 0L): Flow<List<VideoWatchEventEntity>>

  @Query("""
    SELECT COUNT(*) FROM video_watch_events 
    WHERE postId = :postId 
      AND (:sinceTimestamp <= 0 OR watchedAt >= :sinceTimestamp)
  """)
  fun getTotalVideoWatchCountForPost(postId: String, sinceTimestamp: Long = 0L): Flow<Int>

  @Query("""
    SELECT COUNT(DISTINCT viewerId) FROM video_watch_events 
    WHERE postId = :postId 
      AND (:sinceTimestamp <= 0 OR watchedAt >= :sinceTimestamp)
  """)
  fun getUniqueVideoViewersForPost(postId: String, sinceTimestamp: Long = 0L): Flow<Int>

  @Query("""
    SELECT COALESCE(SUM(watchedDuration), 0) FROM video_watch_events 
    WHERE postId = :postId 
      AND (:sinceTimestamp <= 0 OR watchedAt >= :sinceTimestamp)
  """)
  fun getTotalVideoWatchTimeForPost(postId: String, sinceTimestamp: Long = 0L): Flow<Long>

  @Query("""
    SELECT COUNT(*) FROM video_watch_events 
    WHERE postId = :postId 
      AND completed = 1
      AND (:sinceTimestamp <= 0 OR watchedAt >= :sinceTimestamp)
  """)
  fun getCompletedVideoViewsForPost(postId: String, sinceTimestamp: Long = 0L): Flow<Int>

  @Query("""
    SELECT COUNT(*) FROM video_watch_events 
    WHERE postId = :postId 
      AND isReplay = 1
      AND (:sinceTimestamp <= 0 OR watchedAt >= :sinceTimestamp)
  """)
  fun getReplaysCountForPost(postId: String, sinceTimestamp: Long = 0L): Flow<Int>

  // Additional Post Analytics Queries with timestamp filter
  @Query("""
    SELECT * FROM post_views 
    WHERE postId = :postId 
      AND (:sinceTimestamp <= 0 OR viewedAt >= :sinceTimestamp)
    ORDER BY viewedAt ASC
  """)
  fun getPostViewsSince(postId: String, sinceTimestamp: Long): Flow<List<PostViewEntity>>

  @Query("""
    SELECT COUNT(*) FROM post_views 
    WHERE postId = :postId 
      AND (:sinceTimestamp <= 0 OR viewedAt >= :sinceTimestamp)
  """)
  fun getTotalViewsForPostSince(postId: String, sinceTimestamp: Long): Flow<Int>

  @Query("""
    SELECT COUNT(DISTINCT viewerUserId) FROM post_views 
    WHERE postId = :postId 
      AND (:sinceTimestamp <= 0 OR viewedAt >= :sinceTimestamp)
  """)
  fun getUniqueViewersForPostSince(postId: String, sinceTimestamp: Long): Flow<Int>

  @Query("""
    SELECT COUNT(*) FROM post_views 
    WHERE postId = :postId 
      AND generatedFollow = 1
      AND (:sinceTimestamp <= 0 OR viewedAt >= :sinceTimestamp)
  """)
  fun getFollowersGainedForPostSince(postId: String, sinceTimestamp: Long): Flow<Int>

  @Query("""
    SELECT COUNT(*) FROM post_views 
    WHERE postId = :postId 
      AND generatedProfileVisit = 1
      AND (:sinceTimestamp <= 0 OR viewedAt >= :sinceTimestamp)
  """)
  fun getProfileVisitsFromPostSince(postId: String, sinceTimestamp: Long): Flow<Int>
}

data class ReactionCountResult(
  val reactionType: String,
  val count: Int
)

