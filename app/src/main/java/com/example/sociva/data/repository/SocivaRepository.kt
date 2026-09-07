package com.example.sociva.data.repository

import com.example.sociva.data.local.*
import com.example.sociva.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

class SocivaRepository(
  private val dao: SocivaDao,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

  init {
    scope.launch {
      // Check if seeded
      val existingUsers = dao.getAllUsers().first()
      if (existingUsers.isEmpty()) {
        seedDatabase()
      } else {
        val existingMembers = dao.getConversationMembers("conv_sarah")
        if (existingMembers.isEmpty()) {
          dao.insertConversationMembers(SeedData.conversationMembers)
        }
        val existingPostReactions = dao.countReactionsForPost("post_1")
        if (existingPostReactions == 0) {
          dao.insertPostReactions(SeedData.postReactions)
        }
        val existingViews = dao.getTotalProfileViewsCount("user_me").first()
        if (existingViews == 0) {
          dao.insertProfileViews(SeedData.profileViews)
        }
        val existingPostViews = dao.getTotalViewsForPost("post_2").first()
        if (existingPostViews == 0) {
          dao.insertPostViews(SeedData.postViews)
        }
      }
      // Ensure current user settings are seeded if missing
      val existingSettings = dao.getUserSettingsSync("user_me")
      if (existingSettings == null) {
        dao.insertOrUpdateUserSettings(
          UserSettingsEntity(
            userId = "user_me",
            twoFactorEnabled = false,
            twoFactorMethod = "AUTHENTICATOR",
            profileVisibility = "Public",
            darkTheme = false,
            dataSaver = false,
            pushNotifications = true,
            inAppSounds = true,
            language = "English",
            profileViewHistoryEnabled = true
          )
        )
      }
    }
  }

  private suspend fun seedDatabase() {
    dao.insertUsers(SeedData.users)
    dao.insertPosts(SeedData.posts)
    dao.insertPostReactions(SeedData.postReactions)
    dao.insertComments(SeedData.comments)
    dao.insertCommentReactions(SeedData.commentReactions)
    dao.insertStories(SeedData.stories)
    dao.insertReels(SeedData.reels)
    dao.insertConversations(SeedData.conversations)
    dao.insertConversationMembers(SeedData.conversationMembers)
    dao.insertMessages(SeedData.messages)
    dao.insertNotifications(SeedData.notifications)
    dao.insertFriendRequests(SeedData.friendRequests)
    dao.insertFriendships(SeedData.friendships)
    dao.insertFollows(SeedData.follows)
    dao.insertPages(SeedData.pages)
    dao.insertGroups(SeedData.groups)
    dao.insertGroupMembers(SeedData.groupMembers)
    for (req in SeedData.groupJoinRequests) dao.insertGroupJoinRequest(req)
    for (adm in SeedData.pageAdmins) dao.insertPageAdmin(adm)
    dao.insertReports(SeedData.reports)
    dao.insertProfileViews(SeedData.profileViews)
    dao.insertPostViews(SeedData.postViews)
    dao.insertOrUpdateUserSettings(
      UserSettingsEntity(
        userId = "user_me",
        twoFactorEnabled = false,
        twoFactorMethod = "AUTHENTICATOR",
        profileVisibility = "Public",
        darkTheme = false,
        dataSaver = false,
        pushNotifications = true,
        inAppSounds = true,
        language = "English",
        profileViewHistoryEnabled = true
      )
    )
  }

  // --- Users ---
  val allUsers: Flow<List<User>> = dao.getAllUsers().map { list ->
    list.map { it.toDomain() }
  }

  fun getFriends(userId: String = "user_me"): Flow<List<User>> =
    dao.getFriendIdsForUser(userId).map { ids ->
      val all = dao.getAllUsers().first()
      all.filter { ids.contains(it.id) }.map { it.toDomain().copy(isFriend = true) }
    }

  val friends: Flow<List<User>> = getFriends("user_me")

  fun getUser(id: String): Flow<User?> = dao.getUserById(id).map { it?.toDomain() }

  suspend fun insertUser(user: UserEntity) = dao.insertUser(user)

  suspend fun updateFullUserProfile(updated: User) {
    val existing = dao.getUserById(updated.id).first() ?: return
    val fullName = if (updated.firstName.isNotBlank() || updated.lastName.isNotBlank()) {
      "${updated.firstName} ${updated.lastName}".trim()
    } else {
      updated.fullName
    }
    val work = if (updated.workPosition.isNotBlank() && updated.workplace.isNotBlank()) {
      "${updated.workPosition} at ${updated.workplace}"
    } else if (updated.workPosition.isNotBlank()) {
      updated.workPosition
    } else if (updated.workplace.isNotBlank()) {
      updated.workplace
    } else {
      updated.work.trim()
    }
    val education = if (updated.degree.isNotBlank() && updated.university.isNotBlank()) {
      "${updated.degree} - ${updated.university}"
    } else if (updated.university.isNotBlank()) {
      updated.university
    } else if (updated.school.isNotBlank()) {
      updated.school
    } else {
      updated.education.trim()
    }
    val location = if (updated.currentCity.isNotBlank() && updated.country.isNotBlank()) {
      "${updated.currentCity}, ${updated.country}"
    } else if (updated.currentCity.isNotBlank()) {
      updated.currentCity
    } else if (updated.hometown.isNotBlank()) {
      updated.hometown
    } else {
      updated.location.trim()
    }

    val updatedEntity = existing.copy(
      fullName = fullName,
      firstName = updated.firstName,
      lastName = updated.lastName,
      username = updated.username.ifBlank { existing.username },
      bio = updated.bio,
      pronouns = updated.pronouns,
      nickname = updated.nickname,
      otherNames = updated.otherNames,
      dateOfBirth = updated.dateOfBirth,
      gender = updated.gender,
      interestedIn = updated.interestedIn,
      hometown = updated.hometown,
      currentCity = updated.currentCity,
      country = updated.country,
      currentRegion = updated.currentRegion,
      currentCountryCode = updated.currentCountryCode,
      currentLatitude = updated.currentLatitude,
      currentLongitude = updated.currentLongitude,
      hometownRegion = updated.hometownRegion,
      hometownCountryCode = updated.hometownCountryCode,
      hometownLatitude = updated.hometownLatitude,
      hometownLongitude = updated.hometownLongitude,
      countryCode = updated.countryCode,
      work = work,
      workplace = updated.workplace,
      workPosition = updated.workPosition,
      workStartDate = updated.workStartDate,
      workEndDate = updated.workEndDate,
      education = education,
      school = updated.school,
      college = updated.college,
      university = updated.university,
      degree = updated.degree,
      fieldOfStudy = updated.fieldOfStudy,
      graduationYear = updated.graduationYear,
      website = updated.website,
      email = updated.email,
      phone = updated.phone,
      location = location,
      relationshipStatus = updated.relationshipStatus,
      relationshipPartnerId = updated.relationshipPartnerId,
      relationshipPartnerName = updated.relationshipPartnerName,
      customRelationshipText = updated.customRelationshipText,
      birthdayPrivacy = updated.birthdayPrivacy,
      currentCityPrivacy = updated.currentCityPrivacy,
      hometownPrivacy = updated.hometownPrivacy,
      relationshipPrivacy = updated.relationshipPrivacy,
      emailPrivacy = updated.emailPrivacy,
      taggingPermission = updated.taggingPermission,
      reviewTagsBeforeAppearing = updated.reviewTagsBeforeAppearing
    )
    dao.updateUser(updatedEntity)

    // Propagate updated name and username anywhere displayed
    val newUsername = updated.username.ifBlank { existing.username }
    dao.updateAuthorInfoInPosts(updated.id, fullName, newUsername)
    dao.updateAuthorNameInComments(updated.id, fullName)
    dao.updateUserNameInStories(updated.id, fullName)
    dao.updateCreatorInReels(updated.id, fullName, newUsername)
    dao.updateParticipantInConversations(updated.id, fullName)
    dao.updatePartnerNameInUsers(updated.id, fullName)
  }

  suspend fun updateUserProfile(
    userId: String,
    fullName: String,
    bio: String,
    work: String,
    education: String,
    location: String
  ) {
    val user = dao.getUserById(userId).first() ?: return
    dao.updateUser(
      user.copy(
        fullName = fullName,
        bio = bio,
        work = work,
        education = education,
        location = location
      )
    )
  }

  // --- Relationships ---
  fun getIncomingRelationshipRequests(userId: String): Flow<List<RelationshipItem>> =
    dao.getIncomingRelationshipRequests(userId).map { list ->
      val usersMap = dao.getAllUsers().first().associateBy { it.id }
      list.map { rel ->
        val reqUser = usersMap[rel.requesterId]
        val recUser = usersMap[rel.receiverId]
        RelationshipItem(
          id = rel.id,
          requesterId = rel.requesterId,
          receiverId = rel.receiverId,
          relationshipType = rel.relationshipType,
          customText = rel.customText,
          status = rel.status,
          privacy = rel.privacy,
          requesterName = reqUser?.fullName ?: "Someone",
          requesterAvatar = reqUser?.avatarUrl ?: "",
          receiverName = recUser?.fullName ?: "Someone",
          receiverAvatar = recUser?.avatarUrl ?: "",
          createdAt = rel.createdAt,
          updatedAt = rel.updatedAt
        )
      }
    }

  fun getSentRelationshipRequests(userId: String): Flow<List<RelationshipItem>> =
    dao.getSentRelationshipRequests(userId).map { list ->
      val usersMap = dao.getAllUsers().first().associateBy { it.id }
      list.map { rel ->
        val reqUser = usersMap[rel.requesterId]
        val recUser = usersMap[rel.receiverId]
        RelationshipItem(
          id = rel.id,
          requesterId = rel.requesterId,
          receiverId = rel.receiverId,
          relationshipType = rel.relationshipType,
          customText = rel.customText,
          status = rel.status,
          privacy = rel.privacy,
          requesterName = reqUser?.fullName ?: "Someone",
          requesterAvatar = reqUser?.avatarUrl ?: "",
          receiverName = recUser?.fullName ?: "Someone",
          receiverAvatar = recUser?.avatarUrl ?: "",
          createdAt = rel.createdAt,
          updatedAt = rel.updatedAt
        )
      }
    }

  suspend fun sendRelationshipRequest(
    requester: User,
    targetUserId: String,
    relationshipType: String,
    customText: String? = null,
    privacy: String = "Public"
  ): Result<String> {
    if (requester.id == targetUserId) {
      return Result.failure(IllegalArgumentException("Cannot form a relationship with yourself"))
    }
    val targetUser = dao.getUserById(targetUserId).first()
      ?: return Result.failure(NoSuchElementException("Target user not found"))

    // Remove any previous pending requests between them
    dao.deleteRelationshipsBetween(requester.id, targetUserId)

    val relId = "rel_" + UUID.randomUUID().toString().take(8)
    val relationship = RelationshipEntity(
      id = relId,
      requesterId = requester.id,
      receiverId = targetUserId,
      relationshipType = relationshipType,
      customText = customText,
      status = "pending",
      privacy = privacy
    )
    dao.insertRelationship(relationship)

    // Notify target user
    dao.insertNotification(
      NotificationEntity(
        id = "notif_" + UUID.randomUUID().toString().take(8),
        type = NotificationType.RELATIONSHIP_REQUEST.name,
        actorName = requester.fullName,
        actorAvatar = requester.avatarUrl,
        isActorVerified = requester.isVerified,
        messageSnippet = "${requester.fullName} wants to list you as their partner ($relationshipType).",
        timestamp = System.currentTimeMillis(),
        isRead = false,
        recipientId = targetUserId,
        actionData = relId,
        senderId = requester.id
      )
    )

    return Result.success(relId)
  }

  suspend fun acceptRelationshipRequest(relationshipId: String, currentUserId: String): Result<Unit> {
    val rel = dao.getRelationshipById(relationshipId)
      ?: return Result.failure(NoSuchElementException("Relationship request not found"))
    if (rel.receiverId != currentUserId) {
      return Result.failure(SecurityException("Unauthorized to accept this relationship request"))
    }

    val requester = dao.getUserById(rel.requesterId).first() ?: return Result.failure(NoSuchElementException("Requester not found"))
    val receiver = dao.getUserById(rel.receiverId).first() ?: return Result.failure(NoSuchElementException("Receiver not found"))

    // Update relationship status to accepted
    val updatedRel = rel.copy(status = "accepted", updatedAt = System.currentTimeMillis())
    dao.updateRelationship(updatedRel)

    // Clear any previous relationships for both users
    requester.relationshipPartnerId?.let { prevPartnerId ->
      if (prevPartnerId != receiver.id) {
        dao.updateUserRelationship(prevPartnerId, "Single", null, null)
      }
    }
    receiver.relationshipPartnerId?.let { prevPartnerId ->
      if (prevPartnerId != requester.id) {
        dao.updateUserRelationship(prevPartnerId, "Single", null, null)
      }
    }

    // Update both user profiles with the accepted relationship
    dao.updateUser(
      requester.copy(
        relationshipStatus = rel.relationshipType,
        relationshipPartnerId = receiver.id,
        relationshipPartnerName = receiver.fullName,
        customRelationshipText = rel.customText,
        relationshipPrivacy = rel.privacy
      )
    )
    dao.updateUser(
      receiver.copy(
        relationshipStatus = rel.relationshipType,
        relationshipPartnerId = requester.id,
        relationshipPartnerName = requester.fullName,
        customRelationshipText = rel.customText,
        relationshipPrivacy = rel.privacy
      )
    )

    // Notify requester that the request was accepted
    dao.insertNotification(
      NotificationEntity(
        id = "notif_" + UUID.randomUUID().toString().take(8),
        type = NotificationType.RELATIONSHIP_ACCEPTED.name,
        actorName = receiver.fullName,
        actorAvatar = receiver.avatarUrl,
        isActorVerified = receiver.isVerified,
        messageSnippet = "${receiver.fullName} accepted your relationship request (${rel.relationshipType}).",
        timestamp = System.currentTimeMillis(),
        isRead = false,
        recipientId = requester.id,
        actionData = rel.id,
        senderId = receiver.id
      )
    )

    return Result.success(Unit)
  }

  suspend fun declineRelationshipRequest(relationshipId: String, currentUserId: String): Result<Unit> {
    val rel = dao.getRelationshipById(relationshipId)
      ?: return Result.failure(NoSuchElementException("Relationship request not found"))
    if (rel.receiverId != currentUserId) {
      return Result.failure(SecurityException("Unauthorized to decline this relationship request"))
    }

    val receiver = dao.getUserById(rel.receiverId).first()
    dao.deleteRelationship(relationshipId)

    // Notify requester
    if (receiver != null) {
      dao.insertNotification(
        NotificationEntity(
          id = "notif_" + UUID.randomUUID().toString().take(8),
          type = NotificationType.RELATIONSHIP_DECLINED.name,
          actorName = receiver.fullName,
          actorAvatar = receiver.avatarUrl,
          isActorVerified = receiver.isVerified,
          messageSnippet = "${receiver.fullName} declined your relationship request.",
          timestamp = System.currentTimeMillis(),
          isRead = false,
          recipientId = rel.requesterId,
          senderId = receiver.id
        )
      )
    }

    return Result.success(Unit)
  }

  suspend fun cancelRelationshipRequest(relationshipId: String, currentUserId: String): Result<Unit> {
    val rel = dao.getRelationshipById(relationshipId)
      ?: return Result.failure(NoSuchElementException("Relationship request not found"))
    if (rel.requesterId != currentUserId && rel.receiverId != currentUserId) {
      return Result.failure(SecurityException("Unauthorized to cancel this request"))
    }
    dao.deleteRelationship(relationshipId)
    return Result.success(Unit)
  }

  suspend fun removeOrResetRelationship(userId: String, newStatus: String = "Single") {
    val user = dao.getUserById(userId).first() ?: return
    val partnerId = user.relationshipPartnerId

    // Remove active relationship records
    if (partnerId != null) {
      dao.deleteRelationshipsBetween(userId, partnerId)
      // Reset partner's relationship to Single
      val partner = dao.getUserById(partnerId).first()
      if (partner != null && partner.relationshipPartnerId == userId) {
        dao.updateUser(
          partner.copy(
            relationshipStatus = "Single",
            relationshipPartnerId = null,
            relationshipPartnerName = null,
            customRelationshipText = null
          )
        )
      }
    }

    // Reset user's relationship
    dao.updateUser(
      user.copy(
        relationshipStatus = newStatus,
        relationshipPartnerId = null,
        relationshipPartnerName = null,
        customRelationshipText = null
      )
    )
  }

  suspend fun followUser(followerId: String = "user_me", targetUserId: String) {
    if (followerId == targetUserId) return
    if (dao.isFollowing(followerId, targetUserId)) return
    dao.insertFollow(
      FollowEntity(
        id = "fol_" + UUID.randomUUID().toString().take(8),
        followerId = followerId,
        followingId = targetUserId,
        createdAt = System.currentTimeMillis()
      )
    )
    val followerUser = dao.getUserById(followerId).first()
    val targetUser = dao.getUserById(targetUserId).first()
    val newFollowing = dao.getFollowingCount(followerId)
    val newFollowers = dao.getFollowersCount(targetUserId)
    if (followerUser != null) {
      dao.updateUser(followerUser.copy(followingCount = newFollowing))
    }
    if (targetUser != null) {
      dao.updateUser(
        targetUser.copy(
          followersCount = newFollowers,
          isFollowing = if (followerId == "user_me") true else targetUser.isFollowing
        )
      )
    }
  }

  suspend fun unfollowUser(followerId: String = "user_me", targetUserId: String) {
    dao.deleteFollow(followerId, targetUserId)
    val followerUser = dao.getUserById(followerId).first()
    val targetUser = dao.getUserById(targetUserId).first()
    val newFollowing = dao.getFollowingCount(followerId)
    val newFollowers = dao.getFollowersCount(targetUserId)
    if (followerUser != null) {
      dao.updateUser(followerUser.copy(followingCount = newFollowing))
    }
    if (targetUser != null) {
      dao.updateUser(
        targetUser.copy(
          followersCount = newFollowers,
          isFollowing = if (followerId == "user_me") false else targetUser.isFollowing
        )
      )
    }
  }

  suspend fun toggleFollow(userId: String) {
    val isFol = dao.isFollowing("user_me", userId)
    if (isFol) {
      unfollowUser("user_me", userId)
    } else {
      followUser("user_me", userId)
    }
  }

  suspend fun removeFriend(userA: String, userB: String) {
    dao.deleteFriendshipBetween(userA, userB)
    val userAEntity = dao.getUserById(userA).first()
    val userBEntity = dao.getUserById(userB).first()
    val countA = dao.getFriendsCount(userA)
    val countB = dao.getFriendsCount(userB)
    if (userAEntity != null) {
      dao.updateUser(
        userAEntity.copy(
          friendsCount = countA,
          isFriend = if (userB == "user_me") false else userAEntity.isFriend
        )
      )
    }
    if (userBEntity != null) {
      dao.updateUser(
        userBEntity.copy(
          friendsCount = countB,
          isFriend = if (userA == "user_me") false else userBEntity.isFriend
        )
      )
    }
  }

  suspend fun toggleFriend(userId: String) {
    val isFr = dao.hasFriendship("user_me", userId)
    if (isFr) {
      removeFriend("user_me", userId)
    } else {
      sendFriendRequest("user_me", userId)
    }
  }

  suspend fun updateProfilePicture(userId: String, newAvatarUrl: String) {
    val now = System.currentTimeMillis()
    dao.updateUserAvatar(userId, newAvatarUrl, now)
    dao.updateAuthorAvatarInPosts(userId, newAvatarUrl)
    dao.updateAuthorAvatarInComments(userId, newAvatarUrl)
    dao.updateUserAvatarInStories(userId, newAvatarUrl)
    dao.updateCreatorAvatarInReels(userId, newAvatarUrl)
    dao.updateParticipantAvatarInConversations(userId, newAvatarUrl)
    val user = dao.getUserById(userId).first()
    if (user != null) {
      dao.updateActorAvatarInNotifications(user.fullName, newAvatarUrl)

      // Automatic Profile Picture Update Post (Real database post)
      val pronoun = when (user.gender.trim().lowercase(java.util.Locale.ROOT)) {
        "male" -> "his"
        "female" -> "her"
        else -> "their"
      }
      val actionContext = "updated $pronoun profile picture."
      val settings = dao.getUserSettingsSync(userId)
      val audienceStr = when (settings?.profileVisibility) {
        "Friends Only" -> "Friends"
        "Private" -> "Only Me"
        else -> "Public"
      }
      val postId = "post_avatar_${userId}_${now}"
      val postEntity = PostEntity(
        id = postId,
        authorId = userId,
        authorName = user.fullName,
        authorUsername = user.username,
        authorAvatar = newAvatarUrl,
        isAuthorVerified = user.isVerified,
        timestamp = now,
        content = "",
        mediaUrlsString = newAvatarUrl,
        audience = audienceStr,
        postType = "PROFILE_PICTURE_UPDATE",
        actionContextText = actionContext
      )
      dao.insertPost(postEntity)
    }
  }

  suspend fun removeProfilePicture(userId: String) {
    val now = System.currentTimeMillis()
    dao.updateUserAvatar(userId, "", now)
    dao.updateAuthorAvatarInPosts(userId, "")
    dao.updateAuthorAvatarInComments(userId, "")
    dao.updateUserAvatarInStories(userId, "")
    dao.updateCreatorAvatarInReels(userId, "")
    dao.updateParticipantAvatarInConversations(userId, "")
    val user = dao.getUserById(userId).first()
    if (user != null) {
      dao.updateActorAvatarInNotifications(user.fullName, "")
    }
  }

  suspend fun updateCoverPhoto(userId: String, newCoverUrl: String) {
    val now = System.currentTimeMillis()
    dao.updateUserCover(userId, newCoverUrl, now)
    val user = dao.getUserById(userId).first()
    if (user != null) {
      // Automatic Cover Photo Update Post (Real database post)
      val pronoun = when (user.gender.trim().lowercase(java.util.Locale.ROOT)) {
        "male" -> "his"
        "female" -> "her"
        else -> "their"
      }
      val actionContext = "updated $pronoun cover photo."
      val settings = dao.getUserSettingsSync(userId)
      val audienceStr = when (settings?.profileVisibility) {
        "Friends Only" -> "Friends"
        "Private" -> "Only Me"
        else -> "Public"
      }
      val postId = "post_cover_${userId}_${now}"
      val postEntity = PostEntity(
        id = postId,
        authorId = userId,
        authorName = user.fullName,
        authorUsername = user.username,
        authorAvatar = user.avatarUrl,
        isAuthorVerified = user.isVerified,
        timestamp = now,
        content = "",
        mediaUrlsString = newCoverUrl,
        audience = audienceStr,
        postType = "COVER_PHOTO_UPDATE",
        actionContextText = actionContext
      )
      dao.insertPost(postEntity)
    }
  }

  suspend fun removeCoverPhoto(userId: String) {
    val now = System.currentTimeMillis()
    dao.updateUserCover(userId, "", now)
  }

  // --- Posts ---
  private suspend fun mapPostEntitiesToDomain(entities: List<PostEntity>): List<Post> {
    val allUsersMap = dao.getAllUsers().first().associateBy { it.id }
    val allPostReactions = dao.getAllPostReactions().first()
    val reactionsByPost = allPostReactions.groupBy { it.postId }

    val originalPostIds = entities.mapNotNull { it.originalPostId }.distinct()
    val originalEntitiesMap = if (originalPostIds.isNotEmpty()) {
      dao.findPostsByIds(originalPostIds).associateBy { it.id }
    } else {
      emptyMap()
    }

    return entities.map { postEntity ->
      val tags = dao.getPostTags(postEntity.id)
      val taggedUsers = tags.mapNotNull { tag ->
        allUsersMap[tag.taggedUserId]?.let { u ->
          TaggedUser(id = u.id, fullName = u.fullName, username = u.username, avatarUrl = u.avatarUrl)
        }
      }

      val postReactions = reactionsByPost[postEntity.id].orEmpty()
      val reactionTypeCounts = postReactions.mapNotNull { r ->
        try { ReactionType.valueOf(r.reactionType) } catch (e: Exception) { null }
      }.groupingBy { it }.eachCount()

      // Sort reaction types by frequency descending to get top emojis
      val topReactionEmojis = reactionTypeCounts.entries
        .sortedByDescending { it.value }
        .map { it.key.emoji }

      // Check user_me reaction from post_reactions if present
      val myPostReaction = postReactions.find { it.userId == "user_me" }?.let { r ->
        try { ReactionType.valueOf(r.reactionType) } catch (e: Exception) { null }
      } ?: postEntity.myReaction?.let {
        try { ReactionType.valueOf(it) } catch (e: Exception) { null }
      }

      val effectiveLikesCount = if (postReactions.isNotEmpty()) {
        maxOf(postEntity.likesCount, postReactions.size)
      } else {
        postEntity.likesCount
      }

      val sharedPostPreview = if (postEntity.postType == "SHARED_POST" && postEntity.originalPostId != null) {
        val orig = originalEntitiesMap[postEntity.originalPostId]
        if (orig != null) {
          val origAuthor = allUsersMap[orig.authorId]
          val origAudience = when (orig.audience) {
            "Friends" -> PostAudience.FRIENDS
            "Only Me" -> PostAudience.ONLY_ME
            else -> PostAudience.PUBLIC
          }
          SharedPostPreview(
            id = orig.id,
            authorId = orig.authorId,
            authorName = origAuthor?.fullName ?: orig.authorName,
            authorUsername = origAuthor?.username ?: orig.authorUsername,
            authorAvatar = origAuthor?.avatarUrl ?: orig.authorAvatar,
            isAuthorVerified = origAuthor?.isVerified ?: orig.isAuthorVerified,
            timestamp = orig.timestamp,
            content = orig.content,
            mediaUrls = if (orig.mediaUrlsString.isBlank()) emptyList() else orig.mediaUrlsString.split(","),
            feelingOrActivity = orig.feelingOrActivity,
            postType = try { PostType.valueOf(orig.postType) } catch (e: Exception) { PostType.NORMAL },
            actionContextText = orig.actionContextText,
            audience = origAudience,
            isUnavailable = false
          )
        } else {
          SharedPostPreview(
            id = postEntity.originalPostId,
            authorId = "",
            authorName = "",
            authorUsername = "",
            authorAvatar = "",
            timestamp = 0L,
            content = "This content isn't available right now. When this happens, it's usually because the owner only shared it with a small group of people, changed who can see it or it's been deleted.",
            isUnavailable = true
          )
        }
      } else {
        null
      }

      postEntity.toDomain(
        taggedUsers = taggedUsers,
        topReactionEmojis = topReactionEmojis,
        reactionTypeCounts = reactionTypeCounts,
        computedLikesCount = effectiveLikesCount,
        currentMyReaction = myPostReaction,
        sharedPostPreview = sharedPostPreview
      )
    }
  }

  val allPosts: Flow<List<Post>> = combine(dao.getAllPosts(), dao.getAllPostReactions()) { posts, _ ->
    mapPostEntitiesToDomain(posts)
  }

  val savedPosts: Flow<List<Post>> = combine(dao.getSavedPosts(), dao.getAllPostReactions()) { posts, _ ->
    mapPostEntitiesToDomain(posts)
  }

  fun getPostsByUser(userId: String): Flow<List<Post>> = combine(dao.getPostsByAuthor(userId), dao.getAllPostReactions()) { posts, _ ->
    mapPostEntitiesToDomain(posts)
  }

  fun getTaggedPostsByUser(userId: String): Flow<List<Post>> = combine(dao.getTaggedPostsForUser(userId), dao.getAllPostReactions()) { posts, _ ->
    mapPostEntitiesToDomain(posts)
  }

  suspend fun createPost(
    author: User,
    content: String,
    mediaUrls: List<String>,
    feeling: String?,
    audience: PostAudience,
    taggedUserIds: List<String> = emptyList(),
    authorType: String = "PERSONAL",
    targetGroupId: String? = null,
    targetGroupName: String? = null
  ) {
    val postId = "post_" + UUID.randomUUID().toString().take(8)
    val newPost = PostEntity(
      id = postId,
      authorId = author.id,
      authorName = author.fullName,
      authorUsername = author.username,
      authorAvatar = author.avatarUrl,
      isAuthorVerified = author.isVerified,
      timestamp = System.currentTimeMillis(),
      content = content,
      mediaUrlsString = mediaUrls.joinToString(","),
      feelingOrActivity = feeling,
      audience = audience.label,
      likesCount = 0,
      commentsCount = 0,
      sharesCount = 0,
      myReaction = null,
      isSaved = false,
      authorType = authorType,
      targetGroupId = targetGroupId,
      targetGroupName = targetGroupName
    )
    dao.insertPost(newPost)

    if (taggedUserIds.isNotEmpty()) {
      val tags = taggedUserIds.map { taggedId ->
        val targetUser = dao.getUserById(taggedId).first()
        val status = if (targetUser?.reviewTagsBeforeAppearing == true) "pending" else "approved"
        PostTagEntity(
          id = "ptag_" + UUID.randomUUID().toString().take(8),
          postId = postId,
          taggedUserId = taggedId,
          taggedByUserId = author.id,
          status = status
        )
      }
      dao.insertPostTags(tags)

      // Notify tagged users
      taggedUserIds.filter { it != author.id }.forEach { taggedId ->
        dao.insertNotification(
          NotificationEntity(
            id = "notif_" + UUID.randomUUID().toString().take(8),
            type = NotificationType.TAG.name,
            actorName = author.fullName,
            actorAvatar = author.avatarUrl,
            isActorVerified = author.isVerified,
            messageSnippet = "${author.fullName} tagged you in a post.",
            timestamp = System.currentTimeMillis(),
            isRead = false,
            targetPostId = postId,
            recipientId = taggedId,
            senderId = author.id
          )
        )
      }
    }
  }

  suspend fun createPost(
    authorId: String,
    authorName: String,
    authorUsername: String,
    authorAvatar: String,
    isAuthorVerified: Boolean,
    authorType: String,
    content: String,
    mediaUrls: List<String>,
    feeling: String?,
    audience: PostAudience,
    taggedUserIds: List<String> = emptyList(),
    targetGroupId: String? = null,
    targetGroupName: String? = null
  ) {
    val postId = "post_" + UUID.randomUUID().toString().take(8)
    val newPost = PostEntity(
      id = postId,
      authorId = authorId,
      authorName = authorName,
      authorUsername = authorUsername,
      authorAvatar = authorAvatar,
      isAuthorVerified = isAuthorVerified,
      timestamp = System.currentTimeMillis(),
      content = content,
      mediaUrlsString = mediaUrls.joinToString(","),
      feelingOrActivity = feeling,
      audience = audience.label,
      likesCount = 0,
      commentsCount = 0,
      sharesCount = 0,
      myReaction = null,
      isSaved = false,
      authorType = authorType,
      targetGroupId = targetGroupId,
      targetGroupName = targetGroupName
    )
    dao.insertPost(newPost)
  }

  suspend fun removePostTag(postId: String, userId: String) {
    dao.removePostTag(postId, userId)
  }

  suspend fun deletePost(postId: String) {
    val post = dao.findPostById(postId)
    if (post != null) {
      if (post.postType == "SHARED_POST" && post.originalPostId != null) {
        dao.decrementSharesCount(post.originalPostId)
      }
      dao.deletePostById(postId)
      dao.deleteReactionsForPost(postId)
    }
  }

  suspend fun updatePostContent(postId: String, newContent: String) {
    dao.updatePostContent(postId, newContent)
  }

  suspend fun setReaction(postId: String, reaction: ReactionType?, userId: String = "user_me") {
    val post = dao.getPostById(postId).first() ?: return
    val existingReactionEntity = dao.findPostReaction(postId, userId)
    val hadPreviousReaction = existingReactionEntity != null || post.myReaction != null
    val isSameReaction = (existingReactionEntity?.reactionType ?: post.myReaction) == reaction?.name

    val now = System.currentTimeMillis()
    if (isSameReaction) {
      // Remove reaction
      dao.deletePostReaction(postId, userId)
      val newCount = (post.likesCount - 1).coerceAtLeast(0)
      dao.updatePost(
        post.copy(
          myReaction = null,
          likesCount = newCount
        )
      )
    } else if (reaction != null) {
      // Add or update reaction
      val reactionEntity = PostReactionEntity(
        id = existingReactionEntity?.id ?: ("pr_" + UUID.randomUUID().toString().take(8)),
        postId = postId,
        userId = userId,
        reactionType = reaction.name,
        createdAt = now
      )
      dao.insertPostReaction(reactionEntity)

      val likeDelta = if (hadPreviousReaction) 0 else 1
      dao.updatePost(
        post.copy(
          myReaction = reaction.name,
          likesCount = post.likesCount + likeDelta
        )
      )

      // Send notification to post author if not self
      if (post.authorId != userId) {
        val userActor = dao.getUserById(userId).first()
        dao.insertNotification(
          NotificationEntity(
            id = "notif_" + UUID.randomUUID().toString().take(8),
            type = "LIKE",
            actorName = userActor?.fullName ?: "Someone",
            actorAvatar = userActor?.avatarUrl ?: "",
            isActorVerified = userActor?.isVerified ?: false,
            messageSnippet = "reacted ${reaction.emoji} to your post.",
            timestamp = now,
            isRead = false,
            targetPostId = postId,
            recipientId = post.authorId,
            senderId = userId
          )
        )
      }
    }
  }

  suspend fun toggleSavePost(postId: String) {
    val post = dao.getPostById(postId).first() ?: return
    dao.updatePost(post.copy(isSaved = !post.isSaved))
  }

  suspend fun sharePost(postId: String) {
    val post = dao.getPostById(postId).first() ?: return
    dao.updatePost(post.copy(sharesCount = post.sharesCount + 1))
  }

  suspend fun createSharedPost(
    sharer: User,
    originalPost: Post,
    caption: String,
    audience: PostAudience
  ) {
    val now = System.currentTimeMillis()
    val rootOriginalPostId = if (originalPost.postType == PostType.SHARED_POST && !originalPost.originalPostId.isNullOrBlank()) {
      originalPost.originalPostId
    } else {
      originalPost.id
    }

    val postId = "post_shared_${sharer.id}_${now}"
    val audienceStr = when (audience) {
      PostAudience.FRIENDS -> "Friends"
      PostAudience.ONLY_ME -> "Only Me"
      else -> "Public"
    }

    val sharedPostEntity = PostEntity(
      id = postId,
      authorId = sharer.id,
      authorName = sharer.fullName,
      authorUsername = sharer.username,
      authorAvatar = sharer.avatarUrl,
      isAuthorVerified = sharer.isVerified,
      timestamp = now,
      content = caption.trim(),
      mediaUrlsString = "", // Preserves original post reference, does not duplicate media files
      audience = audienceStr,
      postType = "SHARED_POST",
      originalPostId = rootOriginalPostId,
      actionContextText = "shared a post."
    )

    dao.insertPost(sharedPostEntity)

    // Increment share counter on the original post(s)
    dao.incrementSharesCount(originalPost.id)
    if (rootOriginalPostId != originalPost.id) {
      dao.incrementSharesCount(rootOriginalPostId)
    }
  }

  // --- Comments ---
  fun getComments(postId: String, currentUserId: String? = null): Flow<List<Comment>> {
    return combine(
      dao.getCommentsForPost(postId),
      dao.getReactionsForPostComments(postId)
    ) { commentEntities, reactionEntities ->
      val reactionsByComment = reactionEntities.groupBy { it.commentId }

      // Map to domain comments with reaction data
      val domainComments = commentEntities.map { entity ->
        val commentReactions = reactionsByComment[entity.id].orEmpty()
        val userReaction = currentUserId?.let { uid ->
          commentReactions.firstOrNull { it.userId == uid }?.let { r ->
            try { ReactionType.valueOf(r.reactionType) } catch (e: Exception) { null }
          }
        }
        entity.toDomain(
          myReaction = userReaction,
          reactionsCount = commentReactions.size
        )
      }

      // Group replies by parentCommentId
      val repliesByParent = domainComments
        .filter { it.parentCommentId != null }
        .groupBy { it.parentCommentId!! }

      // Return top-level comments with their replies attached
      domainComments
        .filter { it.parentCommentId == null }
        .map { topComment ->
          val childReplies = repliesByParent[topComment.id].orEmpty()
          topComment.copy(
            replies = childReplies,
            repliesCount = childReplies.size
          )
        }
    }
  }

  suspend fun addComment(
    postId: String,
    author: User,
    content: String,
    parentCommentId: String? = null
  ): Result<Comment> {
    if (author.id.isBlank()) {
      return Result.failure(SecurityException("Authentication required to comment"))
    }
    val sanitized = content.trim().replace("\r\n", "\n").take(2000)
    if (sanitized.isBlank()) {
      return Result.failure(IllegalArgumentException("Comment cannot be empty"))
    }

    val now = System.currentTimeMillis()
    val commentId = "c_" + UUID.randomUUID().toString().take(8)

    var rootParentId: String? = null
    var parentComment: CommentEntity? = null

    if (parentCommentId != null) {
      parentComment = dao.findCommentById(parentCommentId)
      if (parentComment == null) {
        return Result.failure(NoSuchElementException("Parent comment not found"))
      }
      // If parentComment is itself a reply, keep 2-level nesting under the root parent
      rootParentId = parentComment.parentCommentId ?: parentCommentId
    }

    val newComment = CommentEntity(
      id = commentId,
      postId = postId,
      authorId = author.id,
      authorName = author.fullName,
      authorAvatar = author.avatarUrl,
      isAuthorVerified = author.isVerified,
      content = sanitized,
      timestamp = now,
      updatedAt = now,
      parentCommentId = rootParentId,
      likesCount = 0,
      isLiked = false
    )
    dao.insertComment(newComment)

    // Send notifications
    if (parentComment != null) {
      // User replied to a comment: notify parent comment's author if not self
      if (parentComment.authorId != author.id) {
        dao.insertNotification(
          NotificationEntity(
            id = "notif_" + UUID.randomUUID().toString().take(8),
            type = "COMMENT_REPLY",
            actorName = author.fullName,
            actorAvatar = author.avatarUrl,
            isActorVerified = author.isVerified,
            messageSnippet = "${author.fullName} replied to your comment.",
            timestamp = now,
            isRead = false,
            targetPostId = postId,
            recipientId = parentComment.authorId
          )
        )
      }
    } else {
      // Top-level comment: notify post author if not self
      val post = dao.findPostById(postId)
      if (post != null && post.authorId != author.id) {
        dao.insertNotification(
          NotificationEntity(
            id = "notif_" + UUID.randomUUID().toString().take(8),
            type = "COMMENT",
            actorName = author.fullName,
            actorAvatar = author.avatarUrl,
            isActorVerified = author.isVerified,
            messageSnippet = "${author.fullName} commented on your post.",
            timestamp = now,
            isRead = false,
            targetPostId = postId,
            recipientId = post.authorId
          )
        )
      }
    }

    // Update post comments count with exact total
    val totalCount = dao.countAllCommentsForPost(postId)
    dao.updatePostCommentsCount(postId, totalCount)

    // Handle @mentions in comment text
    val mentionRegex = Regex("""@([A-Za-z0-9_.]+)""")
    val matchResults = mentionRegex.findAll(sanitized)
    val mentionedUsernames = matchResults.map { it.groupValues[1].lowercase() }.toSet()
    if (mentionedUsernames.isNotEmpty()) {
      val allUsers = dao.getAllUsers().first()
      val mentionedUsers = allUsers.filter { u ->
        mentionedUsernames.contains(u.username.lowercase()) ||
        mentionedUsernames.contains(u.fullName.replace(" ", "").lowercase())
      }
      val mentionEntities = mentionedUsers.map { u ->
        CommentMentionEntity(
          id = "men_" + UUID.randomUUID().toString().take(8),
          commentId = commentId,
          mentionedUserId = u.id,
          createdAt = now
        )
      }
      dao.insertCommentMentions(mentionEntities)

      // Notify mentioned users (if not the author)
      mentionedUsers.filter { it.id != author.id }.forEach { u ->
        dao.insertNotification(
          NotificationEntity(
            id = "notif_" + UUID.randomUUID().toString().take(8),
            type = NotificationType.MENTION.name,
            actorName = author.fullName,
            actorAvatar = author.avatarUrl,
            isActorVerified = author.isVerified,
            messageSnippet = "${author.fullName} mentioned you in a comment: \"${sanitized.take(40)}\"",
            timestamp = now,
            isRead = false,
            targetPostId = postId,
            recipientId = u.id,
            senderId = author.id
          )
        )
      }
    }

    return Result.success(newComment.toDomain())
  }

  suspend fun reactToComment(
    commentId: String,
    user: User,
    reactionType: ReactionType
  ): Result<ReactionType?> {
    if (user.id.isBlank()) {
      return Result.failure(SecurityException("Authentication required to react"))
    }
    val comment = dao.findCommentById(commentId)
      ?: return Result.failure(NoSuchElementException("Comment not found"))

    val existing = dao.findCommentReaction(commentId, user.id)
    val finalReaction: ReactionType?

    if (existing != null && existing.reactionType == reactionType.name) {
      // User tapped the same reaction again -> remove reaction (toggle off)
      dao.deleteCommentReaction(commentId, user.id)
      finalReaction = null
    } else if (existing != null) {
      // User changed reaction (e.g. from Like to Love) -> update reaction
      dao.insertCommentReaction(
        existing.copy(
          reactionType = reactionType.name,
          createdAt = System.currentTimeMillis()
        )
      )
      finalReaction = reactionType
    } else {
      // New reaction
      val now = System.currentTimeMillis()
      dao.insertCommentReaction(
        CommentReactionEntity(
          id = "cr_" + UUID.randomUUID().toString().take(8),
          commentId = commentId,
          userId = user.id,
          reactionType = reactionType.name,
          createdAt = now
        )
      )
      finalReaction = reactionType

      // Notify comment author if not self
      if (comment.authorId != user.id) {
        dao.insertNotification(
          NotificationEntity(
            id = "notif_" + UUID.randomUUID().toString().take(8),
            type = "COMMENT_REACTION",
            actorName = user.fullName,
            actorAvatar = user.avatarUrl,
            isActorVerified = user.isVerified,
            messageSnippet = "${user.fullName} reacted to your comment.",
            timestamp = now,
            isRead = false,
            targetPostId = comment.postId,
            recipientId = comment.authorId
          )
        )
      }
    }

    // Update comment likesCount with exact count
    val newCount = dao.countReactionsForComment(commentId)
    dao.updateCommentLikesCount(commentId, newCount)

    return Result.success(finalReaction)
  }

  suspend fun removeCommentReaction(commentId: String, userId: String): Result<Unit> {
    if (userId.isBlank()) {
      return Result.failure(SecurityException("Authentication required"))
    }
    dao.deleteCommentReaction(commentId, userId)
    val newCount = dao.countReactionsForComment(commentId)
    dao.updateCommentLikesCount(commentId, newCount)
    return Result.success(Unit)
  }

  suspend fun editComment(
    commentId: String,
    userId: String,
    newContent: String
  ): Result<Comment> {
    if (userId.isBlank()) {
      return Result.failure(SecurityException("Authentication required"))
    }
    val comment = dao.findCommentById(commentId)
      ?: return Result.failure(NoSuchElementException("Comment not found"))

    if (comment.authorId != userId) {
      return Result.failure(SecurityException("Unauthorized: Cannot edit another user's comment"))
    }

    val sanitized = newContent.trim().replace("\r\n", "\n").take(2000)
    if (sanitized.isBlank()) {
      return Result.failure(IllegalArgumentException("Comment cannot be empty"))
    }

    val updated = comment.copy(
      content = sanitized,
      updatedAt = System.currentTimeMillis()
    )
    dao.updateComment(updated)
    return Result.success(updated.toDomain())
  }

  suspend fun deleteComment(
    commentId: String,
    postId: String,
    userId: String? = null,
    isPostOwnerOrAdmin: Boolean = false
  ): Result<Unit> {
    val comment = dao.findCommentById(commentId)
      ?: return Result.failure(NoSuchElementException("Comment not found"))
    val post = dao.findPostById(postId)

    if (userId != null) {
      val isAuthorized = (comment.authorId == userId) || (post != null && post.authorId == userId) || isPostOwnerOrAdmin
      if (!isAuthorized) {
        return Result.failure(SecurityException("Unauthorized: Cannot delete another user's comment"))
      }
    }

    // Delete reactions for this comment
    dao.deleteReactionsForComment(commentId)

    // Delete any replies if this is a parent comment
    val replies = dao.getRepliesForCommentList(commentId)
    for (reply in replies) {
      dao.deleteReactionsForComment(reply.id)
      dao.deleteComment(reply.id)
    }

    // Delete the comment itself
    dao.deleteComment(commentId)

    // Update post comments count
    val totalRemaining = dao.countAllCommentsForPost(postId)
    dao.updatePostCommentsCount(postId, totalRemaining)

    return Result.success(Unit)
  }

  // --- Stories ---
  val activeStories: Flow<List<Story>> = dao.getActiveStories(System.currentTimeMillis()).map { list ->
    list.map { it.toDomain() }
  }

  suspend fun createStory(
    user: User,
    text: String,
    mediaUrl: String?,
    gradientIndex: Int
  ) {
    val now = System.currentTimeMillis()
    val newStory = StoryEntity(
      id = "story_" + UUID.randomUUID().toString().take(8),
      userId = user.id,
      userName = user.fullName,
      userAvatar = user.avatarUrl,
      isUserVerified = user.isVerified,
      mediaUrl = mediaUrl,
      textOverlay = text,
      backgroundGradientIndex = gradientIndex,
      timestamp = now,
      expiresAt = now + (24 * 60 * 60 * 1000L), // 24 hours expiry
      viewsCount = 0,
      isViewed = false
    )
    dao.insertStory(newStory)
  }

  suspend fun markStoryViewed(storyId: String) {
    dao.markStoryViewed(storyId)
  }

  // --- Reels ---
  val allReels: Flow<List<Reel>> = dao.getAllReels().map { list ->
    list.map { it.toDomain() }
  }

  suspend fun toggleReelLike(reelId: String) {
    val reel = dao.getAllReels().first().find { it.id == reelId } ?: return
    val newLiked = !reel.isLiked
    val delta = if (newLiked) 1 else -1
    dao.updateReel(
      reel.copy(
        isLiked = newLiked,
        likesCount = (reel.likesCount + delta).coerceAtLeast(0)
      )
    )
  }

  suspend fun toggleReelSave(reelId: String) {
    val reel = dao.getAllReels().first().find { it.id == reelId } ?: return
    dao.updateReel(reel.copy(isSaved = !reel.isSaved))
  }

  suspend fun toggleReelFollow(reelId: String) {
    val reel = dao.getAllReels().first().find { it.id == reelId } ?: return
    dao.updateReel(reel.copy(isFollowing = !reel.isFollowing))
  }

  // --- Conversations & Messages ---
  private val _typingUsers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

  fun setTyping(convId: String, userId: String, isTyping: Boolean) {
    val currentMap = _typingUsers.value.toMutableMap()
    val set = (currentMap[convId] ?: emptySet()).toMutableSet()
    if (isTyping) {
      set.add(userId)
    } else {
      set.remove(userId)
    }
    currentMap[convId] = set
    _typingUsers.value = currentMap
  }

  fun getTypingUsers(convId: String, currentUserId: String): Flow<List<String>> =
    _typingUsers.map { map ->
      (map[convId] ?: emptySet()).filter { it != currentUserId }
    }

  fun getConversations(userId: String): Flow<List<Conversation>> =
    dao.getConversationsForUser(userId).map { list ->
      list.map { it.toDomain() }
    }

  val conversations: Flow<List<Conversation>> = getConversations("user_me")

  fun getConversation(convId: String, currentUserId: String): Flow<Conversation?> =
    getConversations(currentUserId).map { list ->
      list.find { it.id == convId }
    }

  fun getMessages(convId: String, currentUserId: String = "user_me"): Flow<List<Message>> =
    dao.getMessagesForConversation(convId).map { list ->
      list.map { it.toDomain(currentUserId) }
    }

  suspend fun getOrCreateConversation(userAId: String, userBId: String): String {
    val existing = dao.findDirectConversation(userAId, userBId)
    if (existing != null) {
      return existing
    }
    val now = System.currentTimeMillis()
    val newConvId = "conv_${UUID.randomUUID().toString().take(8)}"
    val userB = dao.getUserById(userBId).first()
    val newConv = ConversationEntity(
      id = newConvId,
      createdAt = now,
      updatedAt = now,
      participantId = userBId,
      participantName = userB?.fullName ?: "",
      participantUsername = userB?.username ?: "",
      participantAvatar = userB?.avatarUrl ?: "",
      isParticipantVerified = userB?.isVerified ?: false,
      lastMessage = "",
      lastMessageTimestamp = now,
      unreadCount = 0,
      isOnline = userB?.isOnline ?: false
    )
    dao.insertConversation(newConv)
    dao.insertConversationMember(ConversationMemberEntity(newConvId, userAId, now, now))
    dao.insertConversationMember(ConversationMemberEntity(newConvId, userBId, now, 0L))
    return newConvId
  }

  suspend fun sendMessage(
    convId: String,
    senderId: String = "user_me",
    text: String,
    mediaUrl: String? = null,
    messageType: String = "TEXT"
  ) {
    val now = System.currentTimeMillis()
    val otherMember = dao.getOtherMember(convId, senderId)
    val receiverId = otherMember?.userId ?: ""

    val msg = MessageEntity(
      id = "m_" + UUID.randomUUID().toString().take(8),
      conversationId = convId,
      senderId = senderId,
      receiverId = receiverId,
      messageType = messageType,
      text = text,
      mediaUrl = mediaUrl,
      createdAt = now,
      updatedAt = now,
      timestamp = now,
      isSeen = false,
      isDeleted = false,
      isMine = true
    )
    dao.insertMessage(msg)
    dao.updateConversationLastMessage(convId, text, now)

    // Notify recipient
    if (receiverId.isNotBlank() && receiverId != senderId) {
      val senderUser = dao.getUserById(senderId).first()
      val notif = NotificationEntity(
        id = "notif_" + UUID.randomUUID().toString().take(8),
        type = NotificationType.MESSAGE.name,
        actorName = senderUser?.fullName ?: "Someone",
        actorAvatar = senderUser?.avatarUrl ?: "",
        isActorVerified = senderUser?.isVerified ?: false,
        messageSnippet = text,
        timestamp = now,
        isRead = false,
        targetPostId = convId,
        recipientId = receiverId
      )
      dao.insertNotification(notif)
    }
  }

  suspend fun markConversationAsRead(convId: String, currentUserId: String) {
    val now = System.currentTimeMillis()
    dao.markMessagesAsSeen(convId, currentUserId, now)
    dao.updateMemberLastRead(convId, currentUserId, now)
  }

  suspend fun softDeleteMessage(messageId: String) {
    dao.softDeleteMessage(messageId, "This message was unsent", System.currentTimeMillis())
  }

  suspend fun deleteMessage(messageId: String) {
    dao.deleteMessage(messageId)
  }

  suspend fun setUserPresence(userId: String, isOnline: Boolean) {
    val now = System.currentTimeMillis()
    dao.updateUserPresence(userId, isOnline, now)
  }

  fun getOnlineUsers(currentUserId: String): Flow<List<User>> =
    dao.getOnlineUsers(currentUserId).map { list -> list.map { it.toDomain() } }

  fun searchUsers(query: String, currentUserId: String): Flow<List<User>> =
    dao.searchUsers(query, currentUserId).map { list -> list.map { it.toDomain() } }

  fun getAllUsersExcept(currentUserId: String): Flow<List<User>> =
    dao.getAllUsersExcept(currentUserId).map { list -> list.map { it.toDomain() } }

  // --- Notifications ---
  fun getNotifications(recipientId: String = "user_me"): Flow<List<NotificationItem>> =
    dao.getNotificationsForRecipient(recipientId).map { list -> list.map { it.toDomain() } }

  val notifications: Flow<List<NotificationItem>> = getNotifications("user_me")

  suspend fun markNotificationRead(id: String) {
    dao.markNotificationAsRead(id)
  }

  suspend fun markAllNotificationsRead() {
    dao.markAllNotificationsAsRead()
  }

  // --- Friend Requests & Relationships ---
  fun getIncomingFriendRequests(receiverId: String = "user_me"): Flow<List<FriendRequestItem>> =
    dao.getIncomingFriendRequests(receiverId).map { list -> list.map { it.toDomain() } }

  fun getSentFriendRequests(senderId: String = "user_me"): Flow<List<FriendRequestItem>> =
    dao.getSentFriendRequests(senderId).map { list -> list.map { it.toDomain() } }

  fun getFriendRequests(userId: String = "user_me"): Flow<List<FriendRequestItem>> =
    getIncomingFriendRequests(userId)

  val friendRequests: Flow<List<FriendRequestItem>> = getIncomingFriendRequests("user_me")

  fun getFriendStatusFlow(currentUserId: String, targetUserId: String): Flow<FriendStatus> {
    if (currentUserId == targetUserId) {
      return flowOf(FriendStatus.NONE)
    }
    return combine(
      dao.hasFriendshipFlow(currentUserId, targetUserId),
      dao.getPendingRequestBetween(currentUserId, targetUserId)
    ) { hasFriendship, pendingReq ->
      when {
        hasFriendship -> FriendStatus.FRIENDS
        pendingReq == null -> FriendStatus.NONE
        pendingReq.senderId == currentUserId && pendingReq.receiverId == targetUserId -> FriendStatus.REQUEST_SENT
        pendingReq.senderId == targetUserId && pendingReq.receiverId == currentUserId -> FriendStatus.REQUEST_RECEIVED
        else -> FriendStatus.NONE
      }
    }
  }

  fun isFollowingFlow(followerId: String, followingId: String): Flow<Boolean> =
    dao.isFollowingFlow(followerId, followingId)

  suspend fun sendFriendRequest(senderId: String, targetUserId: String): Boolean {
    if (senderId == targetUserId) return false
    if (dao.hasFriendship(senderId, targetUserId)) return false
    val existingReq = dao.getPendingRequest(senderId, targetUserId)
    if (existingReq != null) return false

    // If target user already sent a request, auto-accept
    val reverseReq = dao.getPendingRequest(targetUserId, senderId)
    if (reverseReq != null) {
      return acceptFriendRequest(reverseReq.id, senderId)
    }

    val senderUser = dao.getUserById(senderId).first() ?: return false
    val targetUser = dao.getUserById(targetUserId).first() ?: return false
    val now = System.currentTimeMillis()

    // 1. Create pending request (DO NOT create friendship record!)
    val requestId = "req_" + UUID.randomUUID().toString().take(8)
    val request = FriendRequestEntity(
      id = requestId,
      senderId = senderId,
      receiverId = targetUserId,
      status = "pending",
      createdAt = now,
      updatedAt = now,
      senderName = senderUser.fullName,
      senderUsername = senderUser.username,
      senderAvatar = senderUser.avatarUrl,
      receiverName = targetUser.fullName,
      receiverUsername = targetUser.username,
      receiverAvatar = targetUser.avatarUrl,
      mutualFriendsCount = 5
    )
    dao.insertFriendRequest(request)

    // 2. User A automatically follows User B
    followUser(senderId, targetUserId)

    // 3. User B receives notification: "User A sent you a friend request."
    dao.insertNotification(
      NotificationEntity(
        id = "notif_" + UUID.randomUUID().toString().take(8),
        type = "FRIEND_REQUEST",
        actorName = senderUser.fullName,
        actorAvatar = senderUser.avatarUrl,
        isActorVerified = senderUser.isVerified,
        messageSnippet = "${senderUser.fullName} sent you a friend request.",
        timestamp = now,
        isRead = false,
        recipientId = targetUserId
      )
    )

    return true
  }

  suspend fun acceptFriendRequest(requestId: String, currentUserId: String = "user_me"): Boolean {
    val request = dao.getFriendRequestById(requestId) ?: return false
    if (request.status != "pending") return false

    val senderId = request.senderId
    val receiverId = request.receiverId
    val now = System.currentTimeMillis()

    // 1. Remove pending friend request
    dao.deleteFriendRequest(requestId)

    // 2. Create friendship between User A and User B
    if (!dao.hasFriendship(senderId, receiverId)) {
      dao.insertFriendships(
        listOf(
          FriendshipEntity(
            id = "fr_" + UUID.randomUUID().toString().take(8),
            userId = senderId,
            friendId = receiverId,
            createdAt = now
          ),
          FriendshipEntity(
            id = "fr_" + UUID.randomUUID().toString().take(8),
            userId = receiverId,
            friendId = senderId,
            createdAt = now
          )
        )
      )
    }

    // 3. Update both users' friend counts
    val senderUser = dao.getUserById(senderId).first()
    val receiverUser = dao.getUserById(receiverId).first()
    val senderFriendsCount = dao.getFriendsCount(senderId)
    val receiverFriendsCount = dao.getFriendsCount(receiverId)

    if (senderUser != null) {
      dao.updateUser(
        senderUser.copy(
          friendsCount = senderFriendsCount,
          isFriend = if (receiverId == "user_me") true else senderUser.isFriend
        )
      )
    }
    if (receiverUser != null) {
      dao.updateUser(
        receiverUser.copy(
          friendsCount = receiverFriendsCount,
          isFriend = if (senderId == "user_me") true else receiverUser.isFriend
        )
      )
    }

    // 4. Send notification to User A: "User B accepted your friend request."
    val receiverName = receiverUser?.fullName ?: request.receiverName
    val receiverAvatar = receiverUser?.avatarUrl ?: request.receiverAvatar
    dao.insertNotification(
      NotificationEntity(
        id = "notif_" + UUID.randomUUID().toString().take(8),
        type = "ACCEPT_REQUEST",
        actorName = receiverName,
        actorAvatar = receiverAvatar,
        isActorVerified = receiverUser?.isVerified ?: false,
        messageSnippet = "$receiverName accepted your friend request.",
        timestamp = now,
        isRead = false,
        recipientId = senderId
      )
    )

    return true
  }

  suspend fun rejectFriendRequest(requestId: String): Boolean {
    val request = dao.getFriendRequestById(requestId) ?: return false
    // DO NOT create friendship
    // Remove pending friend request
    dao.deleteFriendRequest(requestId)
    // User A remains following User B
    return true
  }

  suspend fun cancelFriendRequest(senderId: String, targetUserId: String): Boolean {
    val req = dao.getPendingRequest(senderId, targetUserId) ?: return false
    // Remove pending friend request
    dao.deleteFriendRequest(req.id)
    // User A continues following User B
    // User A is NOT a friend
    return true
  }

  suspend fun cancelFriendRequestById(requestId: String): Boolean {
    val req = dao.getFriendRequestById(requestId) ?: return false
    dao.deleteFriendRequest(req.id)
    return true
  }

  fun getPostReactionUsers(postId: String, currentUserId: String = "user_me"): Flow<List<PostReactionUser>> {
    val reactionsAndUsersFlow = combine(
      dao.getReactionsForPost(postId),
      dao.getAllUsers()
    ) { reactions, users ->
      Pair(reactions, users)
    }

    val relationshipsFlow = combine(
      dao.getFriendshipsForUser(currentUserId),
      dao.getSentFriendRequests(currentUserId),
      dao.getIncomingFriendRequests(currentUserId),
      dao.getFollowsForUser(currentUserId)
    ) { friendships, sentRequests, incomingRequests, follows ->
      RelationshipContext(
        friendIds = friendships.map { it.friendId }.toSet(),
        sentReqTargetIds = sentRequests.map { it.receiverId }.toSet(),
        incomingReqMap = incomingRequests.associateBy { it.senderId },
        followingIds = follows.map { it.followingId }.toSet()
      )
    }

    return combine(reactionsAndUsersFlow, relationshipsFlow) { (reactions, users), relCtx ->
      val userMap = users.associateBy { it.id }

      reactions.mapNotNull { reactionEntity ->
        val userEntity = userMap[reactionEntity.userId] ?: return@mapNotNull null
        val reactionType = try {
          ReactionType.valueOf(reactionEntity.reactionType)
        } catch (e: Exception) {
          ReactionType.LIKE
        }

        val relationship = when {
          userEntity.id == currentUserId -> ReactionRelationshipStatus.YOU
          relCtx.friendIds.contains(userEntity.id) -> ReactionRelationshipStatus.FRIEND
          relCtx.incomingReqMap.containsKey(userEntity.id) -> ReactionRelationshipStatus.REQUEST_RECEIVED
          relCtx.sentReqTargetIds.contains(userEntity.id) -> ReactionRelationshipStatus.REQUEST_SENT
          relCtx.followingIds.contains(userEntity.id) -> ReactionRelationshipStatus.FOLLOWING
          else -> ReactionRelationshipStatus.CAN_ADD_FRIEND
        }

        PostReactionUser(
          reactionId = reactionEntity.id,
          postId = reactionEntity.postId,
          user = userEntity.toDomain(),
          reactionType = reactionType,
          createdAt = reactionEntity.createdAt,
          relationshipStatus = relationship,
          incomingRequestId = relCtx.incomingReqMap[userEntity.id]?.id
        )
      }
    }
  }

  // --- Pages ---
  val pages: Flow<List<SocivaPage>> = dao.getAllPages().map { list ->
    list.map { it.toDomain() }
  }

  fun getPage(id: String): Flow<SocivaPage?> = dao.getPageById(id).map { it?.toDomain() }

  fun getPageAdmins(pageId: String): Flow<List<PageAdmin>> =
    dao.getPageAdmins(pageId).map { list -> list.map { it.toDomain() } }

  fun getPageFollowers(pageId: String): Flow<List<PageFollower>> =
    dao.getPageFollowers(pageId).map { list -> list.map { it.toDomain() } }

  fun isUserPageFollower(pageId: String, userId: String): Flow<Boolean> =
    dao.isUserPageFollower(pageId, userId)

  fun getPostsForPage(pageId: String): Flow<List<Post>> =
    dao.getPostsForPage(pageId).map { list -> list.map { it.toDomain() } }

  suspend fun togglePageLike(pageId: String) {
    val page = dao.getAllPages().first().find { it.id == pageId } ?: return
    val newLiked = !page.isLiked
    val delta = if (newLiked) 1 else -1
    dao.updatePage(
      page.copy(
        isLiked = newLiked,
        followersCount = (page.followersCount + delta).coerceAtLeast(0)
      )
    )
  }

  suspend fun togglePageFollow(pageId: String, user: User) {
    val page = dao.getPageById(pageId).first() ?: return
    val currentlyFollowing = dao.isUserPageFollower(pageId, user.id).first()
    if (currentlyFollowing) {
      dao.deletePageFollower(pageId, user.id)
      dao.updatePage(
        page.copy(
          isLiked = false,
          followersCount = (page.followersCount - 1).coerceAtLeast(0)
        )
      )
    } else {
      dao.insertPageFollower(
        PageFollowerEntity(
          id = "pf_" + UUID.randomUUID().toString().take(8),
          pageId = pageId,
          userId = user.id,
          userName = user.fullName,
          userAvatar = user.avatarUrl,
          followedAt = System.currentTimeMillis()
        )
      )
      dao.updatePage(
        page.copy(
          isLiked = true,
          followersCount = page.followersCount + 1
        )
      )
    }
  }

  suspend fun createPage(
    name: String,
    category: String,
    description: String,
    username: String = "",
    avatarUrl: String = "",
    coverUrl: String = "",
    website: String = "",
    location: String = "",
    contactEmail: String = "",
    contactPhone: String = "",
    creatorUser: User? = null
  ): SocivaPage {
    val pageId = "page_" + UUID.randomUUID().toString().take(8)
    val sanitizedUsername = if (username.isNotBlank()) {
      username.removePrefix("@").lowercase().replace(" ", "")
    } else {
      name.lowercase().replace(" ", "").filter { it.isLetterOrDigit() }.take(15)
    }
    val defaultAvatar = if (avatarUrl.isNotBlank()) avatarUrl else "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=300&h=300&fit=crop"
    val defaultCover = if (coverUrl.isNotBlank()) coverUrl else "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=900&h=300&fit=crop"

    val newPage = PageEntity(
      id = pageId,
      name = name,
      category = category,
      description = description,
      avatarUrl = defaultAvatar,
      coverUrl = defaultCover,
      followersCount = 1,
      isLiked = true,
      isAdmin = true,
      ownerId = creatorUser?.id ?: "user_me",
      username = sanitizedUsername,
      website = website,
      location = location,
      contactEmail = contactEmail,
      contactPhone = contactPhone,
      followingCount = 0,
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis()
    )
    dao.insertPage(newPage)

    // Add creator as Owner in page_admins
    if (creatorUser != null) {
      dao.insertPageAdmin(
        PageAdminEntity(
          id = "padmin_" + UUID.randomUUID().toString().take(8),
          pageId = pageId,
          userId = creatorUser.id,
          userName = creatorUser.fullName,
          userAvatar = creatorUser.avatarUrl,
          role = "Owner",
          assignedAt = System.currentTimeMillis()
        )
      )
      // Creator also follows their own page
      dao.insertPageFollower(
        PageFollowerEntity(
          id = "pf_" + UUID.randomUUID().toString().take(8),
          pageId = pageId,
          userId = creatorUser.id,
          userName = creatorUser.fullName,
          userAvatar = creatorUser.avatarUrl,
          followedAt = System.currentTimeMillis()
        )
      )
    }
    return newPage.toDomain()
  }

  suspend fun updatePage(page: SocivaPage) {
    val existing = dao.getPageById(page.id).first() ?: return
    dao.updatePage(
      existing.copy(
        name = page.name,
        category = page.category,
        description = page.description,
        username = page.username,
        website = page.website,
        location = page.location,
        contactEmail = page.contactEmail,
        contactPhone = page.contactPhone,
        avatarUrl = if (page.avatarUrl.isNotBlank()) page.avatarUrl else existing.avatarUrl,
        coverUrl = if (page.coverUrl.isNotBlank()) page.coverUrl else existing.coverUrl,
        updatedAt = System.currentTimeMillis()
      )
    )
  }

  // --- Groups ---
  val groups: Flow<List<SocivaGroup>> = dao.getAllGroups().map { list ->
    list.map { it.toDomain() }
  }

  fun getGroup(id: String): Flow<SocivaGroup?> = dao.getGroupById(id).map { it?.toDomain() }

  fun getGroupMembers(groupId: String): Flow<List<GroupMember>> =
    dao.getGroupMembers(groupId).map { list -> list.map { it.toDomain() } }

  fun getGroupJoinRequests(groupId: String): Flow<List<GroupJoinRequest>> =
    dao.getGroupJoinRequests(groupId).map { list -> list.map { it.toDomain() } }

  fun getPostsForGroup(groupId: String): Flow<List<Post>> =
    dao.getPostsForGroup(groupId).map { list -> list.map { it.toDomain() } }

  suspend fun toggleGroupJoin(groupId: String) {
    val group = dao.getAllGroups().first().find { it.id == groupId } ?: return
    val newJoined = !group.isJoined
    val delta = if (newJoined) 1 else -1
    dao.updateGroup(
      group.copy(
        isJoined = newJoined,
        membersCount = (group.membersCount + delta).coerceAtLeast(0)
      )
    )
  }

  suspend fun joinOrRequestGroup(groupId: String, user: User) {
    val group = dao.getGroupById(groupId).first() ?: return
    val isPrivate = group.privacy.contains("Private", ignoreCase = true)

    if (isPrivate) {
      // Create a join request
      dao.insertGroupJoinRequest(
        GroupJoinRequestEntity(
          id = "greq_" + UUID.randomUUID().toString().take(8),
          groupId = groupId,
          userId = user.id,
          userName = user.fullName,
          userUsername = user.username,
          userAvatar = user.avatarUrl,
          status = "Pending",
          requestedAt = System.currentTimeMillis()
        )
      )
      dao.updateGroup(group.copy(hasPendingJoinRequest = true))
    } else {
      // Public group -> instant join
      dao.insertGroupMember(
        GroupMemberEntity(
          id = "gmem_" + UUID.randomUUID().toString().take(8),
          groupId = groupId,
          userId = user.id,
          userName = user.fullName,
          userUsername = user.username,
          userAvatar = user.avatarUrl,
          role = "Member",
          status = "Active",
          joinedAt = System.currentTimeMillis()
        )
      )
      dao.updateGroup(
        group.copy(
          isJoined = true,
          role = "Member",
          membersCount = group.membersCount + 1,
          hasPendingJoinRequest = false
        )
      )
    }
  }

  suspend fun cancelGroupJoinRequest(groupId: String, userId: String) {
    dao.deleteGroupJoinRequestForUser(groupId, userId)
    val group = dao.getGroupById(groupId).first() ?: return
    dao.updateGroup(group.copy(hasPendingJoinRequest = false))
  }

  suspend fun leaveGroup(groupId: String, userId: String) {
    val group = dao.getGroupById(groupId).first() ?: return
    dao.deleteGroupMember(groupId, userId)
    dao.updateGroup(
      group.copy(
        isJoined = false,
        role = "None",
        membersCount = (group.membersCount - 1).coerceAtLeast(0)
      )
    )
  }

  suspend fun approveGroupJoinRequest(request: GroupJoinRequest) {
    dao.updateGroupJoinRequest(
      GroupJoinRequestEntity(
        id = request.id,
        groupId = request.groupId,
        userId = request.userId,
        userName = request.userName,
        userUsername = request.userUsername,
        userAvatar = request.userAvatar,
        status = "Approved",
        requestedAt = request.requestedAt
      )
    )
    dao.insertGroupMember(
      GroupMemberEntity(
        id = "gmem_" + UUID.randomUUID().toString().take(8),
        groupId = request.groupId,
        userId = request.userId,
        userName = request.userName,
        userUsername = request.userUsername,
        userAvatar = request.userAvatar,
        role = "Member",
        status = "Active",
        joinedAt = System.currentTimeMillis()
      )
    )
    val group = dao.getGroupById(request.groupId).first()
    if (group != null) {
      dao.updateGroup(group.copy(membersCount = group.membersCount + 1))
    }
  }

  suspend fun rejectGroupJoinRequest(requestId: String) {
    dao.deleteGroupJoinRequest(requestId)
  }

  suspend fun removeGroupMember(groupId: String, userId: String) {
    dao.deleteGroupMember(groupId, userId)
    val group = dao.getGroupById(groupId).first()
    if (group != null) {
      dao.updateGroup(group.copy(membersCount = (group.membersCount - 1).coerceAtLeast(0)))
    }
  }

  suspend fun updateGroupMemberRole(groupId: String, userId: String, newRole: String) {
    val members = dao.getGroupMembers(groupId).first()
    val target = members.find { it.userId == userId } ?: return
    dao.updateGroupMember(target.copy(role = newRole))
  }

  suspend fun createGroup(
    name: String,
    privacy: String,
    description: String,
    avatarUrl: String = "",
    coverUrl: String = "",
    rules: String = "",
    creatorUser: User? = null
  ): SocivaGroup {
    val groupId = "grp_" + UUID.randomUUID().toString().take(8)
    val defaultAvatar = if (avatarUrl.isNotBlank()) avatarUrl else "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=300&h=300&fit=crop"
    val defaultCover = if (coverUrl.isNotBlank()) coverUrl else "https://images.unsplash.com/photo-1507238691740-187a5b1d37b8?w=900&h=300&fit=crop"
    val defaultRules = if (rules.isNotBlank()) rules else "1. Be kind and courteous.\n2. No hate speech or bullying.\n3. Respect privacy and confidentiality.\n4. Relevant topics only."

    val newGroup = GroupEntity(
      id = groupId,
      name = name,
      privacy = privacy,
      description = description,
      avatarUrl = defaultAvatar,
      coverUrl = defaultCover,
      membersCount = 1,
      isJoined = true,
      role = "Owner",
      ownerId = creatorUser?.id ?: "user_me",
      rules = defaultRules,
      hasPendingJoinRequest = false,
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis()
    )
    dao.insertGroup(newGroup)

    // Add creator as Owner in group_members
    if (creatorUser != null) {
      dao.insertGroupMember(
        GroupMemberEntity(
          id = "gmem_" + UUID.randomUUID().toString().take(8),
          groupId = groupId,
          userId = creatorUser.id,
          userName = creatorUser.fullName,
          userUsername = creatorUser.username,
          userAvatar = creatorUser.avatarUrl,
          role = "Owner",
          status = "Active",
          joinedAt = System.currentTimeMillis()
        )
      )
    }
    return newGroup.toDomain()
  }

  suspend fun updateGroup(group: SocivaGroup) {
    val existing = dao.getGroupById(group.id).first() ?: return
    dao.updateGroup(
      existing.copy(
        name = group.name,
        privacy = group.privacy,
        description = group.description,
        rules = group.rules,
        avatarUrl = if (group.avatarUrl.isNotBlank()) group.avatarUrl else existing.avatarUrl,
        coverUrl = if (group.coverUrl.isNotBlank()) group.coverUrl else existing.coverUrl,
        updatedAt = System.currentTimeMillis()
      )
    )
  }

  // --- Reports & Moderation ---
  val reports: Flow<List<ReportItem>> = dao.getAllReports().map { list ->
    list.map {
      ReportItem(
        id = it.id,
        targetType = it.targetType,
        targetId = it.targetId,
        targetTitle = it.targetTitle,
        reason = it.reason,
        reportedBy = it.reportedBy,
        timestamp = it.timestamp,
        status = it.status
      )
    }
  }

  suspend fun submitReport(targetType: String, targetId: String, title: String, reason: String) {
    val newReport = ReportEntity(
      id = "rep_" + UUID.randomUUID().toString().take(8),
      targetType = targetType,
      targetId = targetId,
      targetTitle = title,
      reason = reason,
      reportedBy = "alexrivera",
      timestamp = System.currentTimeMillis(),
      status = "Pending"
    )
    dao.insertReport(newReport)
  }

  suspend fun resolveReport(reportId: String) {
    dao.resolveReport(reportId)
  }

  suspend fun deleteReport(reportId: String) {
    dao.deleteReport(reportId)
  }

  // --- Settings & Privacy ---
  fun getUserSettings(userId: String = "user_me"): Flow<UserSettings> =
    dao.getUserSettings(userId).map { entity ->
      entity?.toDomain() ?: UserSettings(userId = userId)
    }

  suspend fun updateDarkTheme(userId: String = "user_me", enabled: Boolean) {
    dao.updateDarkTheme(userId, enabled)
  }

  suspend fun updateDataSaver(userId: String = "user_me", enabled: Boolean) {
    dao.updateDataSaver(userId, enabled)
  }

  suspend fun updatePushNotifications(userId: String = "user_me", enabled: Boolean) {
    dao.updatePushNotifications(userId, enabled)
  }

  suspend fun updateInAppSounds(userId: String = "user_me", enabled: Boolean) {
    dao.updateInAppSounds(userId, enabled)
  }

  suspend fun updateProfileVisibility(userId: String = "user_me", visibility: String) {
    dao.updateProfileVisibility(userId, visibility)
  }

  suspend fun updateTwoFactor(userId: String = "user_me", enabled: Boolean, method: String = "AUTHENTICATOR") {
    dao.updateTwoFactor(userId, enabled, method)
  }

  suspend fun updatePassword(userId: String = "user_me") {
    dao.updatePasswordLastUpdated(userId)
  }

  // --- Blocking System ---
  fun getBlockedUsers(blockerId: String = "user_me"): Flow<List<BlockedUser>> =
    combine(dao.getBlockedUsersForUser(blockerId), dao.getAllUsers()) { blockedEntities, users ->
      val userMap = users.associateBy { it.id }
      blockedEntities.mapNotNull { entity ->
        val targetUser = userMap[entity.blockedId]?.toDomain() ?: return@mapNotNull null
        BlockedUser(
          id = entity.id,
          blockerId = entity.blockerId,
          blockedId = entity.blockedId,
          blockedUser = targetUser,
          createdAt = entity.createdAt
        )
      }
    }

  fun isUserBlockedFlow(currentUserId: String = "user_me", targetUserId: String): Flow<Boolean> =
    dao.isBlockedEitherWayFlow(currentUserId, targetUserId)

  suspend fun isUserBlocked(currentUserId: String = "user_me", targetUserId: String): Boolean =
    dao.isBlockedEitherWay(currentUserId, targetUserId)

  suspend fun blockUser(blockerId: String = "user_me", blockedId: String): Boolean {
    if (blockerId == blockedId) return false
    val now = System.currentTimeMillis()

    // 1. Insert Block
    val blockEntity = BlockedUserEntity(
      id = "blk_" + UUID.randomUUID().toString().take(8),
      blockerId = blockerId,
      blockedId = blockedId,
      createdAt = now
    )
    dao.insertBlock(blockEntity)

    // 2. Remove friendship in both directions
    dao.deleteFriendshipBetween(blockerId, blockedId)

    // 3. Remove all friend requests between them
    dao.deleteFriendRequestsBetween(blockerId, blockedId)

    // 4. Remove follows in both directions
    dao.deleteFollow(blockerId, blockedId)
    dao.deleteFollow(blockedId, blockerId)

    // 5. Update friends & followers counts for both users
    val blockerUser = dao.getUserById(blockerId).first()
    val blockedUser = dao.getUserById(blockedId).first()
    if (blockerUser != null) {
      dao.updateUser(
        blockerUser.copy(
          friendsCount = dao.getFriendsCount(blockerId),
          followingCount = dao.getFollowingCount(blockerId),
          followersCount = dao.getFollowersCount(blockerId),
          isFriend = false,
          isFollowing = false
        )
      )
    }
    if (blockedUser != null) {
      dao.updateUser(
        blockedUser.copy(
          friendsCount = dao.getFriendsCount(blockedId),
          followingCount = dao.getFollowingCount(blockedId),
          followersCount = dao.getFollowersCount(blockedId),
          isFriend = false,
          isFollowing = false
        )
      )
    }

    return true
  }

  suspend fun unblockUser(blockerId: String = "user_me", blockedId: String): Boolean {
    dao.deleteBlock(blockerId, blockedId)
    return true
  }

  // --- Profile Views ---

  suspend fun recordProfileVisit(
    viewerUserId: String = "user_me",
    viewedUserId: String,
    originatingPostId: String? = null
  ) {
    try {
      if (viewerUserId.isBlank() || viewedUserId.isBlank()) return
      // Requirement 3: Do not track self-views
      if (viewerUserId == viewedUserId || (viewerUserId == "user_me" && viewedUserId == "user_me")) return

      // If this visit came from clicking a post author, mark the post view as having generated a profile visit
      if (!originatingPostId.isNullOrBlank()) {
        val tenMinutesAgo = System.currentTimeMillis() - (10 * 60 * 1000L)
        val postView = dao.getRecentPostView(originatingPostId, viewerUserId, tenMinutesAgo)
        if (postView != null && !postView.generatedProfileVisit) {
          dao.insertPostView(postView.copy(generatedProfileVisit = true))
        }
      }

      // Requirement 10: Check if blocked either way
      if (dao.isBlockedEitherWay(viewerUserId, viewedUserId)) return

      // Verify target user exists
      val targetUser = dao.getUserById(viewedUserId).first() ?: return

      // Requirement 11 & 12: Check viewer's privacy setting
      val viewerSettings = dao.getUserSettings(viewerUserId).first()
      val isHistoryEnabled = viewerSettings?.profileViewHistoryEnabled ?: true

      // Requirement 4: Prevent excessive duplicate views (30-minute deduplication window)
      val thirtyMinutesAgo = System.currentTimeMillis() - (30 * 60 * 1000L)
      val effectiveViewerId = if (isHistoryEnabled) viewerUserId else ""
      val recentView = dao.getRecentProfileView(viewedUserId, effectiveViewerId, thirtyMinutesAgo)
      if (recentView != null) {
        // Within 30 minutes, already counted as one visit
        return
      }

      val viewEntity = ProfileViewEntity(
        id = "pv_" + UUID.randomUUID().toString().take(12),
        viewedUserId = viewedUserId,
        viewerUserId = effectiveViewerId,
        viewedAt = System.currentTimeMillis(),
        createdAt = System.currentTimeMillis(),
        seenAt = null,
        isAnonymous = !isHistoryEnabled
      )
      dao.insertProfileView(viewEntity)
    } catch (e: Exception) {
      // Requirement 24 & 25: Performance & Error Handling - Fail silently, never crash
    }
  }

  fun getProfileViewStatsFlow(viewedUserId: String): Flow<ProfileViewStats> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startOfDay = cal.timeInMillis

    cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
    val startOfWeek = cal.timeInMillis

    cal.set(Calendar.DAY_OF_MONTH, 1)
    val startOfMonth = cal.timeInMillis

    return combine(
      dao.getProfileViewsCountSince(viewedUserId, startOfDay),
      dao.getProfileViewsCountSince(viewedUserId, startOfWeek),
      dao.getProfileViewsCountSince(viewedUserId, startOfMonth),
      dao.getTotalProfileViewsCount(viewedUserId),
      dao.getUnseenProfileViewsCount(viewedUserId)
    ) { today, week, month, total, unseen ->
      ProfileViewStats(
        todayCount = today,
        thisWeekCount = week,
        thisMonthCount = month,
        totalCount = total,
        unseenCount = unseen
      )
    }
  }

  fun getProfileVisitorsFlow(profileOwnerId: String, limit: Int = 50): Flow<List<ProfileVisitorItem>> {
    val visitorsFlow = dao.getProfileVisitors(profileOwnerId, limit)
    val usersFlow = dao.getAllUsers()
    val blockedFlow = dao.getAllBlockedEitherWayIdsFlow(profileOwnerId)
    val friendshipsFlow = dao.getAllFriendships()
    val sentReqsFlow = dao.getSentFriendRequests(profileOwnerId)
    val incomingReqsFlow = dao.getIncomingFriendRequests(profileOwnerId)
    val followsFlow = dao.getFollowsForUser(profileOwnerId)

    val baseCombined = combine(
      visitorsFlow,
      usersFlow,
      blockedFlow,
      friendshipsFlow
    ) { visitors, users, blockedIds, friendships ->
      Triple(visitors, users, Pair(blockedIds, friendships))
    }

    val relationsCombined = combine(sentReqsFlow, incomingReqsFlow, followsFlow) { sent, incoming, follows ->
      Triple(sent, incoming, follows)
    }

    return baseCombined.combine(relationsCombined) { (visitors, users, blockedAndFriendships), (sentReqs, incomingReqs, follows) ->
      val (blockedIds, friendships) = blockedAndFriendships
      val userMap = users.associateBy { it.id }
      val blockedSet = blockedIds.toSet()
      val ownerFriends = friendships.filter { it.userId == profileOwnerId }.map { it.friendId }.toSet()
      val sentReqTargets = sentReqs.filter { it.status == "pending" }.map { it.receiverId }.toSet()
      val incomingReqSenders = incomingReqs.filter { it.status == "pending" }.map { it.senderId }.toSet()
      val followingSet = follows.map { it.followingId }.toSet()

      visitors.filter { view ->
        view.viewerUserId.isNotBlank() && !view.isAnonymous && !blockedSet.contains(view.viewerUserId)
      }.mapNotNull { view ->
        val entity = userMap[view.viewerUserId] ?: return@mapNotNull null
        val viewerId = view.viewerUserId

        val friendStatus = when {
          ownerFriends.contains(viewerId) -> FriendStatus.FRIENDS
          sentReqTargets.contains(viewerId) -> FriendStatus.REQUEST_SENT
          incomingReqSenders.contains(viewerId) -> FriendStatus.REQUEST_RECEIVED
          else -> FriendStatus.NONE
        }

        val isFollowing = followingSet.contains(viewerId)

        val viewerFriends = friendships.filter { it.userId == viewerId }.map { it.friendId }.toSet()
        val mutualCount = ownerFriends.intersect(viewerFriends).size

        ProfileVisitorItem(
          viewId = view.id,
          user = entity.toDomain(),
          viewedAt = view.viewedAt,
          isSeen = view.seenAt != null,
          friendStatus = friendStatus,
          isFollowing = isFollowing,
          mutualFriendsCount = mutualCount
        )
      }
    }
  }

  suspend fun markProfileVisitorsSeen(profileOwnerId: String) {
    try {
      dao.markAllProfileViewsSeen(profileOwnerId, System.currentTimeMillis())
    } catch (e: Exception) {
      // Fail silently
    }
  }

  suspend fun updateProfileViewHistorySetting(userId: String = "user_me", enabled: Boolean) {
    dao.updateProfileViewHistoryEnabled(userId, enabled)
  }

  // ==========================================
  // Post Views & Real Analytics Methods
  // ==========================================

  suspend fun recordPostView(
    postId: String,
    viewerUserId: String,
    isProfileVisitTarget: Boolean = false,
    source: String = "Home Feed",
    generatedFollow: Boolean = false
  ) {
    // Validation: cannot record view without viewer or post
    if (postId.isBlank() || viewerUserId.isBlank()) return

    // Don't count the post owner's own views
    val post = dao.findPostById(postId) ?: return
    if (post.authorId == viewerUserId) return

    // Deduplication window: 10 minutes (600,000 ms)
    val tenMinutesAgo = System.currentTimeMillis() - (10 * 60 * 1000L)
    val recentView = dao.getRecentPostView(postId, viewerUserId, tenMinutesAgo)

    if (recentView != null) {
      // If the viewer subsequently visited the author's profile or followed from this post, update flags
      var updated = recentView
      var changed = false
      if (isProfileVisitTarget && !recentView.generatedProfileVisit) {
        updated = updated.copy(generatedProfileVisit = true)
        changed = true
      }
      if (generatedFollow && !recentView.generatedFollow) {
        updated = updated.copy(generatedFollow = true)
        changed = true
      }
      if (changed) {
        dao.insertPostView(updated)
      }
      return
    }

    // Insert new valid view
    val viewEntity = PostViewEntity(
      id = "pv_${UUID.randomUUID().toString().take(12)}",
      postId = postId,
      viewerUserId = viewerUserId,
      viewedAt = System.currentTimeMillis(),
      createdAt = System.currentTimeMillis(),
      generatedProfileVisit = isProfileVisitTarget,
      generatedFollow = generatedFollow,
      source = source
    )
    dao.insertPostView(viewEntity)
  }

  suspend fun recordVideoWatchEvent(event: VideoWatchEvent) {
    if (event.postId.isBlank() || event.viewerId.isBlank()) return
    val post = dao.findPostById(event.postId) ?: return
    // Don't count post owner's views
    if (post.authorId == event.viewerId) return

    // Record post view if watched at least 2 seconds
    if (event.watchedDuration >= 2000L) {
      recordPostView(
        postId = event.postId,
        viewerUserId = event.viewerId,
        source = event.source
      )
    }

    val entity = VideoWatchEventEntity(
      id = "vwe_${UUID.randomUUID().toString().take(12)}",
      postId = event.postId,
      videoId = event.videoId,
      viewerId = event.viewerId,
      sessionId = event.sessionId,
      startedAt = event.startedAt,
      lastPosition = event.lastPosition,
      watchedDuration = event.watchedDuration,
      videoDuration = event.videoDuration,
      completed = event.completed,
      isReplay = event.isReplay,
      source = event.source,
      watchedAt = event.watchedAt
    )
    dao.insertVideoWatchEvent(entity)
  }

  fun getPostAnalyticsFlow(
    postId: String,
    requestingUserId: String,
    timeWindow: AnalyticsTimeWindow = AnalyticsTimeWindow.ALL_TIME
  ): Flow<PostAnalytics?> {
    val now = System.currentTimeMillis()
    val sinceTimestamp: Long = when (timeWindow) {
      AnalyticsTimeWindow.TODAY -> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
      }
      AnalyticsTimeWindow.LAST_7_DAYS -> now - (7L * 24 * 60 * 60 * 1000)
      AnalyticsTimeWindow.LAST_28_DAYS -> now - (28L * 24 * 60 * 60 * 1000)
      AnalyticsTimeWindow.LAST_30_DAYS -> now - (30L * 24 * 60 * 60 * 1000)
      AnalyticsTimeWindow.ALL_TIME -> 0L
    }

    return combine(
      dao.getPostById(postId),
      dao.getPostViewsSince(postId, sinceTimestamp),
      dao.getReactionsCountForPost(postId),
      dao.getCommentsCountForPost(postId),
      dao.getRepliesCountForPost(postId),
      dao.getSharesCountForPost(postId),
      dao.getSavesCountForPost(postId),
      dao.getProfileVisitsFromPostSince(postId, sinceTimestamp),
      dao.getFollowersGainedForPostSince(postId, sinceTimestamp),
      dao.getReactionBreakdownForPost(postId),
      dao.getRecentViewersForPost(postId, 15)
    ) { results ->
      val post = results[0] as? PostEntity ?: return@combine null

      // Privacy / Authorization rule: Only author can see analytics
      if (post.authorId != requestingUserId) {
        return@combine null
      }

      @Suppress("UNCHECKED_CAST")
      val views = (results[1] as? List<PostViewEntity>) ?: emptyList()
      val reactionsCount = (results[2] as? Int) ?: 0
      val commentsCount = (results[3] as? Int) ?: 0
      val repliesCount = (results[4] as? Int) ?: 0
      val sharesCount = (results[5] as? Int) ?: 0
      val savesCount = (results[6] as? Int) ?: 0
      val profileVisits = (results[7] as? Int) ?: 0
      val followersGained = (results[8] as? Int) ?: 0
      @Suppress("UNCHECKED_CAST")
      val reactionBreakdownRaw = (results[9] as? List<ReactionCountResult>) ?: emptyList()
      @Suppress("UNCHECKED_CAST")
      val recentViewerEntities = (results[10] as? List<UserEntity>) ?: emptyList()

      val totalViews = views.size
      val uniqueViewers = views.map { it.viewerUserId }.distinct().size
      val reach = uniqueViewers

      // Engagement rate calculation: (Reactions + Comments + Shares + Saves) / Reach * 100
      val totalInteractions = reactionsCount + commentsCount + sharesCount + savesCount
      val engagementRate = if (reach > 0) {
        (totalInteractions.toDouble() / reach.toDouble()) * 100.0
      } else if (totalInteractions > 0) {
        100.0
      } else {
        0.0
      }

      val viewsOverTime = computeViewsDistributionForWindow(views, timeWindow)
      val reachOverTime = computeReachDistributionForWindow(views, timeWindow)
      val engagementOverTime = computeEngagementDistributionForWindow(views, totalInteractions, timeWindow)

      PostAnalytics(
        postId = postId,
        ownerId = post.authorId,
        totalViews = totalViews,
        uniqueViewers = uniqueViewers,
        reach = reach,
        reactionCount = reactionsCount,
        commentCount = commentsCount,
        replyCount = repliesCount,
        shareCount = sharesCount,
        saveCount = savesCount,
        profileVisitCount = profileVisits,
        followersGained = followersGained,
        engagementRate = engagementRate,
        reactionBreakdown = reactionBreakdownRaw.associate { it.reactionType to it.count },
        recentViewers = recentViewerEntities.map { it.toDomain() },
        viewsOverTime = viewsOverTime,
        reachOverTime = reachOverTime,
        engagementOverTime = engagementOverTime,
        timeWindow = timeWindow,
        createdAt = post.timestamp,
        updatedAt = System.currentTimeMillis()
      )
    }
  }

  fun getVideoAnalyticsFlow(
    postId: String,
    requestingUserId: String,
    timeWindow: AnalyticsTimeWindow = AnalyticsTimeWindow.ALL_TIME
  ): Flow<VideoAnalytics?> {
    val now = System.currentTimeMillis()
    val sinceTimestamp: Long = when (timeWindow) {
      AnalyticsTimeWindow.TODAY -> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
      }
      AnalyticsTimeWindow.LAST_7_DAYS -> now - (7L * 24 * 60 * 60 * 1000)
      AnalyticsTimeWindow.LAST_28_DAYS -> now - (28L * 24 * 60 * 60 * 1000)
      AnalyticsTimeWindow.LAST_30_DAYS -> now - (30L * 24 * 60 * 60 * 1000)
      AnalyticsTimeWindow.ALL_TIME -> 0L
    }

    return combine(
      dao.getPostById(postId),
      dao.getVideoWatchEventsForPost(postId, sinceTimestamp),
      dao.getReactionsCountForPost(postId),
      dao.getCommentsCountForPost(postId),
      dao.getRepliesCountForPost(postId),
      dao.getSharesCountForPost(postId),
      dao.getSavesCountForPost(postId),
      dao.getProfileVisitsFromPostSince(postId, sinceTimestamp),
      dao.getFollowersGainedForPostSince(postId, sinceTimestamp)
    ) { results ->
      val post = results[0] as? PostEntity ?: return@combine null

      // Privacy / Authorization rule: Only author can access video analytics
      if (post.authorId != requestingUserId) {
        return@combine null
      }

      @Suppress("UNCHECKED_CAST")
      val events = (results[1] as? List<VideoWatchEventEntity>) ?: emptyList()
      val reactionsCount = (results[2] as? Int) ?: 0
      val commentsCount = (results[3] as? Int) ?: 0
      val repliesCount = (results[4] as? Int) ?: 0
      val sharesCount = (results[5] as? Int) ?: 0
      val savesCount = (results[6] as? Int) ?: 0
      val profileVisits = (results[7] as? Int) ?: 0
      val followersGained = (results[8] as? Int) ?: 0

      val videoDuration = events.maxOfOrNull { it.videoDuration }?.takeIf { it > 0 } ?: 120_000L
      val validWatchEvents = events.filter { it.watchedDuration >= 2000L || it.completed }
      val totalViews = validWatchEvents.size
      val uniqueViewers = events.map { it.viewerId }.distinct().size
      val totalWatchTime = events.sumOf { it.watchedDuration }
      val averageWatchTimeSeconds = if (totalViews > 0) {
        (totalWatchTime.toDouble() / 1000.0) / totalViews.toDouble()
      } else {
        0.0
      }
      val averagePercentageWatched = if (videoDuration > 0) {
        ((averageWatchTimeSeconds * 1000.0) / videoDuration.toDouble()) * 100.0
      } else {
        0.0
      }
      val completionRate = if (totalViews > 0) {
        (events.count { it.completed }.toDouble() / totalViews.toDouble()) * 100.0
      } else {
        0.0
      }
      val replays = events.count { it.isReplay }

      val totalInteractions = reactionsCount + commentsCount + sharesCount + savesCount
      val engagementRate = if (uniqueViewers > 0) {
        (totalInteractions.toDouble() / uniqueViewers.toDouble()) * 100.0
      } else if (totalInteractions > 0) {
        100.0
      } else {
        0.0
      }

      // Viewer Retention points at 0%, 10%, 25%, 50%, 75%, 90%, 100%
      val retentionPoints = if (events.isEmpty()) {
        emptyList()
      } else {
        listOf(0, 10, 25, 50, 75, 90, 100).map { pct ->
          val thresholdMs = (pct / 100.0) * videoDuration
          val stillWatching = events.count { it.lastPosition >= thresholdMs || (it.completed && pct <= 100) }
          val retentionPct = (stillWatching.toDouble() / events.size.toDouble()) * 100.0
          pct to retentionPct
        }
      }

      // Traffic source breakdown
      val trafficSources = if (events.isEmpty()) {
        emptyMap()
      } else {
        events.groupBy { it.source.ifBlank { "Home Feed" } }
          .mapValues { it.value.size }
      }

      VideoAnalytics(
        postId = postId,
        videoId = events.firstOrNull()?.videoId ?: "",
        ownerId = post.authorId,
        videoDuration = videoDuration,
        totalViews = totalViews,
        uniqueViewers = uniqueViewers,
        totalWatchTime = totalWatchTime,
        averageWatchTime = averageWatchTimeSeconds,
        averagePercentageWatched = averagePercentageWatched.coerceIn(0.0, 100.0),
        completionRate = completionRate.coerceIn(0.0, 100.0),
        replays = replays,
        reactionCount = reactionsCount,
        commentCount = commentsCount,
        replyCount = repliesCount,
        shareCount = sharesCount,
        saveCount = savesCount,
        profileVisitCount = profileVisits,
        followersGained = followersGained,
        engagementRate = engagementRate,
        retentionPoints = retentionPoints,
        trafficSources = trafficSources,
        timeWindow = timeWindow,
        createdAt = post.timestamp,
        updatedAt = System.currentTimeMillis()
      )
    }
  }

  fun getProfileAnalyticsFlow(
    targetUserId: String,
    requestingUserId: String,
    timeWindow: AnalyticsTimeWindow = AnalyticsTimeWindow.LAST_7_DAYS
  ): Flow<ProfileAnalytics?> {
    // Privacy check: Only owner of profile can see their analytics
    if (targetUserId != requestingUserId) {
      return flowOf(null)
    }

    val now = System.currentTimeMillis()
    val sinceTimestamp: Long = when (timeWindow) {
      AnalyticsTimeWindow.TODAY -> now - (24L * 60 * 60 * 1000)
      AnalyticsTimeWindow.LAST_7_DAYS -> now - (7L * 24 * 60 * 60 * 1000)
      AnalyticsTimeWindow.LAST_28_DAYS -> now - (28L * 24 * 60 * 60 * 1000)
      AnalyticsTimeWindow.LAST_30_DAYS -> now - (30L * 24 * 60 * 60 * 1000)
      AnalyticsTimeWindow.ALL_TIME -> 0L
    }

    return combine(
      dao.getTotalPostViewsForUser(targetUserId, sinceTimestamp),
      dao.getTotalReactionsForUser(targetUserId, sinceTimestamp),
      dao.getTotalCommentsForUser(targetUserId, sinceTimestamp),
      dao.getTotalSharesForUser(targetUserId, sinceTimestamp),
      dao.getTotalSavesForUser(targetUserId),
      dao.getProfileViewsCountSince(targetUserId, sinceTimestamp),
      dao.getFollowersGainedForUser(targetUserId, sinceTimestamp),
      dao.getBestPerformingPostsForUser(targetUserId, 5),
      dao.getPostViewsForUserSince(targetUserId, sinceTimestamp)
    ) { results ->
      val totalPostViews = (results[0] as? Int) ?: 0
      val totalReactions = (results[1] as? Int) ?: 0
      val totalComments = (results[2] as? Int) ?: 0
      val totalShares = (results[3] as? Int) ?: 0
      val totalSaves = (results[4] as? Int) ?: 0
      val profileVisits = (results[5] as? Int) ?: 0
      val followersGained = (results[6] as? Int) ?: 0
      @Suppress("UNCHECKED_CAST")
      val bestPostsEntities = (results[7] as? List<PostEntity>) ?: emptyList()
      @Suppress("UNCHECKED_CAST")
      val postViewsList = (results[8] as? List<PostViewEntity>) ?: emptyList()

      val totalInteractions = totalReactions + totalComments + totalShares + totalSaves
      val engagementRate = if (totalPostViews > 0) {
        (totalInteractions.toDouble() / totalPostViews.toDouble()) * 100.0
      } else if (totalInteractions > 0) {
        100.0
      } else {
        0.0
      }

      val dailyTrend = computeDailyTrend(postViewsList, timeWindow)

      ProfileAnalytics(
        userId = targetUserId,
        timeWindow = timeWindow,
        totalPostViews = totalPostViews,
        totalReactions = totalReactions,
        totalComments = totalComments,
        totalShares = totalShares,
        totalSaves = totalSaves,
        profileVisits = profileVisits,
        followersGained = followersGained,
        engagementRate = engagementRate,
        bestPerformingPosts = bestPostsEntities.map { it.toDomain() },
        dailyViewsTrend = dailyTrend
      )
    }
  }

  private fun computeViewsDistribution(views: List<PostViewEntity>): List<Pair<String, Int>> {
    if (views.isEmpty()) {
      return listOf("Day 1" to 0, "Day 2" to 0, "Day 3" to 0, "Day 4" to 0, "Day 5" to 0, "Day 6" to 0, "Day 7" to 0)
    }
    val cal = Calendar.getInstance()
    val map = mutableMapOf<String, Int>()
    // Generate buckets for the last 7 days
    for (i in 6 downTo 0) {
      cal.timeInMillis = System.currentTimeMillis() - (i * 24 * 60 * 60 * 1000L)
      val dayStr = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> "Sun"
        Calendar.MONDAY -> "Mon"
        Calendar.TUESDAY -> "Tue"
        Calendar.WEDNESDAY -> "Wed"
        Calendar.THURSDAY -> "Thu"
        Calendar.FRIDAY -> "Fri"
        else -> "Sat"
      }
      map[dayStr] = 0
    }

    views.forEach { v ->
      cal.timeInMillis = v.viewedAt
      val dayStr = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> "Sun"
        Calendar.MONDAY -> "Mon"
        Calendar.TUESDAY -> "Tue"
        Calendar.WEDNESDAY -> "Wed"
        Calendar.THURSDAY -> "Thu"
        Calendar.FRIDAY -> "Fri"
        else -> "Sat"
      }
      map[dayStr] = (map[dayStr] ?: 0) + 1
    }
    return map.toList()
  }

  private fun computeViewsDistributionForWindow(
    views: List<PostViewEntity>,
    timeWindow: AnalyticsTimeWindow
  ): List<Pair<String, Int>> {
    if (views.isEmpty()) return emptyList()

    val cal = Calendar.getInstance()
    val now = System.currentTimeMillis()
    val (numBuckets, bucketDurationMs, formatLabel) = when (timeWindow) {
      AnalyticsTimeWindow.TODAY -> Triple(6, 4 * 60 * 60 * 1000L) { c: Calendar ->
        "${c.get(Calendar.HOUR_OF_DAY)}:00"
      }
      AnalyticsTimeWindow.LAST_7_DAYS -> Triple(7, 24 * 60 * 60 * 1000L) { c: Calendar ->
        when (c.get(Calendar.DAY_OF_WEEK)) {
          Calendar.SUNDAY -> "Sun"; Calendar.MONDAY -> "Mon"; Calendar.TUESDAY -> "Tue"
          Calendar.WEDNESDAY -> "Wed"; Calendar.THURSDAY -> "Thu"; Calendar.FRIDAY -> "Fri"
          else -> "Sat"
        }
      }
      AnalyticsTimeWindow.LAST_28_DAYS, AnalyticsTimeWindow.LAST_30_DAYS -> Triple(4, 7 * 24 * 60 * 60 * 1000L) { c: Calendar ->
        "W${4 - (now - c.timeInMillis) / (7 * 24 * 60 * 60 * 1000L)}"
      }
      AnalyticsTimeWindow.ALL_TIME -> Triple(7, 24 * 60 * 60 * 1000L) { c: Calendar ->
        "${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.DAY_OF_MONTH)}"
      }
    }

    val result = mutableListOf<Pair<String, Int>>()
    for (i in (numBuckets - 1) downTo 0) {
      val start = now - ((i + 1) * bucketDurationMs)
      val end = now - (i * bucketDurationMs)
      cal.timeInMillis = end
      val label = formatLabel(cal)
      val count = views.count { it.viewedAt in (start + 1)..end }
      result.add(label to count)
    }
    return result
  }

  private fun computeReachDistributionForWindow(
    views: List<PostViewEntity>,
    timeWindow: AnalyticsTimeWindow
  ): List<Pair<String, Int>> {
    if (views.isEmpty()) return emptyList()

    val cal = Calendar.getInstance()
    val now = System.currentTimeMillis()
    val (numBuckets, bucketDurationMs, formatLabel) = when (timeWindow) {
      AnalyticsTimeWindow.TODAY -> Triple(6, 4 * 60 * 60 * 1000L) { c: Calendar ->
        "${c.get(Calendar.HOUR_OF_DAY)}:00"
      }
      AnalyticsTimeWindow.LAST_7_DAYS -> Triple(7, 24 * 60 * 60 * 1000L) { c: Calendar ->
        when (c.get(Calendar.DAY_OF_WEEK)) {
          Calendar.SUNDAY -> "Sun"; Calendar.MONDAY -> "Mon"; Calendar.TUESDAY -> "Tue"
          Calendar.WEDNESDAY -> "Wed"; Calendar.THURSDAY -> "Thu"; Calendar.FRIDAY -> "Fri"
          else -> "Sat"
        }
      }
      AnalyticsTimeWindow.LAST_28_DAYS, AnalyticsTimeWindow.LAST_30_DAYS -> Triple(4, 7 * 24 * 60 * 60 * 1000L) { c: Calendar ->
        "W${4 - (now - c.timeInMillis) / (7 * 24 * 60 * 60 * 1000L)}"
      }
      AnalyticsTimeWindow.ALL_TIME -> Triple(7, 24 * 60 * 60 * 1000L) { c: Calendar ->
        "${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.DAY_OF_MONTH)}"
      }
    }

    val result = mutableListOf<Pair<String, Int>>()
    for (i in (numBuckets - 1) downTo 0) {
      val start = now - ((i + 1) * bucketDurationMs)
      val end = now - (i * bucketDurationMs)
      cal.timeInMillis = end
      val label = formatLabel(cal)
      val uniqueUsers = views.filter { it.viewedAt in (start + 1)..end }.map { it.viewerUserId }.distinct().size
      result.add(label to uniqueUsers)
    }
    return result
  }

  private fun computeEngagementDistributionForWindow(
    views: List<PostViewEntity>,
    totalInteractions: Int,
    timeWindow: AnalyticsTimeWindow
  ): List<Pair<String, Double>> {
    if (views.isEmpty() || totalInteractions == 0) return emptyList()

    val reachDist = computeReachDistributionForWindow(views, timeWindow)
    val totalViewsCount = views.size.coerceAtLeast(1)
    return reachDist.map { (label, count) ->
      val rate = if (count > 0) {
        // Proportion of interactions allocated to bucket relative to views
        val bucketInteractions = (totalInteractions.toDouble() * (count.toDouble() / totalViewsCount.toDouble()))
        (bucketInteractions / count.toDouble()) * 100.0
      } else {
        0.0
      }
      label to rate.coerceAtMost(100.0)
    }
  }

  private fun computeDailyTrend(views: List<PostViewEntity>, timeWindow: AnalyticsTimeWindow): List<Pair<String, Int>> {
    val cal = Calendar.getInstance()
    val numDays = when (timeWindow) {
      AnalyticsTimeWindow.TODAY -> 1
      AnalyticsTimeWindow.LAST_7_DAYS -> 7
      AnalyticsTimeWindow.LAST_28_DAYS -> 14
      AnalyticsTimeWindow.LAST_30_DAYS -> 14 // 14 sample points for 30 days
      AnalyticsTimeWindow.ALL_TIME -> 7
    }

    val list = mutableListOf<Pair<String, Int>>()
    val now = System.currentTimeMillis()
    val stepMillis = if (numDays == 7) 24 * 60 * 60 * 1000L else 2 * 24 * 60 * 60 * 1000L

    for (i in (numDays - 1) downTo 0) {
      val bucketStart = now - ((i + 1) * stepMillis)
      val bucketEnd = now - (i * stepMillis)
      cal.timeInMillis = bucketEnd
      val label = "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
      val count = views.count { it.viewedAt in (bucketStart + 1)..bucketEnd }
      list.add(label to count)
    }
    return list
  }
}

// Entity to Domain mappers
private fun UserSettingsEntity.toDomain() = UserSettings(
  userId = userId,
  twoFactorEnabled = twoFactorEnabled,
  twoFactorMethod = twoFactorMethod,
  profileVisibility = profileVisibility,
  darkTheme = darkTheme,
  dataSaver = dataSaver,
  pushNotifications = pushNotifications,
  inAppSounds = inAppSounds,
  language = language,
  passwordLastUpdated = passwordLastUpdated,
  profileViewHistoryEnabled = profileViewHistoryEnabled
)
private fun UserEntity.toDomain() = User(
  id = id,
  username = username,
  fullName = fullName,
  avatarUrl = avatarUrl,
  coverUrl = coverUrl,
  bio = bio,
  isVerified = isVerified,
  followersCount = followersCount,
  followingCount = followingCount,
  friendsCount = friendsCount,
  postsCount = postsCount,
  work = work,
  education = education,
  location = location,
  joinedDate = joinedDate,
  isOnline = isOnline,
  lastActiveAt = lastActiveAt,
  isFriend = isFriend,
  isFollowing = isFollowing,
  profilePictureUpdatedAt = profilePictureUpdatedAt,
  coverPhotoUpdatedAt = coverPhotoUpdatedAt,
  firstName = firstName,
  lastName = lastName,
  pronouns = pronouns,
  nickname = nickname,
  otherNames = otherNames,
  dateOfBirth = dateOfBirth,
  gender = gender,
  interestedIn = interestedIn,
  hometown = hometown,
  currentCity = currentCity,
  country = country,
  currentRegion = currentRegion,
  currentCountryCode = currentCountryCode,
  currentLatitude = currentLatitude,
  currentLongitude = currentLongitude,
  hometownRegion = hometownRegion,
  hometownCountryCode = hometownCountryCode,
  hometownLatitude = hometownLatitude,
  hometownLongitude = hometownLongitude,
  countryCode = countryCode,
  workplace = workplace,
  workPosition = workPosition,
  workStartDate = workStartDate,
  workEndDate = workEndDate,
  school = school,
  college = college,
  university = university,
  degree = degree,
  fieldOfStudy = fieldOfStudy,
  graduationYear = graduationYear,
  website = website,
  email = email,
  phone = phone,
  relationshipStatus = relationshipStatus,
  relationshipPartnerId = relationshipPartnerId,
  relationshipPartnerName = relationshipPartnerName,
  customRelationshipText = customRelationshipText,
  birthdayPrivacy = birthdayPrivacy,
  currentCityPrivacy = currentCityPrivacy,
  hometownPrivacy = hometownPrivacy,
  relationshipPrivacy = relationshipPrivacy,
  emailPrivacy = emailPrivacy,
  taggingPermission = taggingPermission,
  reviewTagsBeforeAppearing = reviewTagsBeforeAppearing
)

private fun PostEntity.toDomain(
  taggedUsers: List<TaggedUser> = emptyList(),
  topReactionEmojis: List<String> = emptyList(),
  reactionTypeCounts: Map<ReactionType, Int> = emptyMap(),
  computedLikesCount: Int = likesCount,
  currentMyReaction: ReactionType? = myReaction?.let {
    try { ReactionType.valueOf(it) } catch (e: Exception) { null }
  },
  sharedPostPreview: SharedPostPreview? = null
) = Post(
  id = id,
  authorId = authorId,
  authorName = authorName,
  authorUsername = authorUsername,
  authorAvatar = authorAvatar,
  isAuthorVerified = isAuthorVerified,
  timestamp = timestamp,
  content = content,
  mediaUrls = if (mediaUrlsString.isBlank()) emptyList() else mediaUrlsString.split(","),
  feelingOrActivity = feelingOrActivity,
  audience = when (audience) {
    "Friends" -> PostAudience.FRIENDS
    "Only Me" -> PostAudience.ONLY_ME
    else -> PostAudience.PUBLIC
  },
  likesCount = computedLikesCount,
  commentsCount = commentsCount,
  sharesCount = sharesCount,
  myReaction = currentMyReaction,
  isSaved = isSaved,
  taggedUsers = taggedUsers,
  topReactionEmojis = topReactionEmojis,
  reactionTypeCounts = reactionTypeCounts,
  postType = try { PostType.valueOf(postType) } catch (e: Exception) { PostType.NORMAL },
  originalPostId = originalPostId,
  actionContextText = actionContextText,
  sharedPost = sharedPostPreview,
  authorType = authorType,
  targetGroupId = targetGroupId,
  targetGroupName = targetGroupName
)

private fun CommentEntity.toDomain(
  myReaction: ReactionType? = null,
  reactionsCount: Int = likesCount,
  replies: List<Comment> = emptyList()
) = Comment(
  id = id,
  postId = postId,
  authorId = authorId,
  authorName = authorName,
  authorAvatar = authorAvatar,
  isAuthorVerified = isAuthorVerified,
  content = content,
  timestamp = timestamp,
  updatedAt = updatedAt,
  parentCommentId = parentCommentId,
  likesCount = reactionsCount,
  isLiked = myReaction != null,
  myReaction = myReaction,
  reactionsCount = reactionsCount,
  repliesCount = replies.size,
  replies = replies
)

private fun CommentReactionEntity.toDomain() = CommentReaction(
  id = id,
  commentId = commentId,
  userId = userId,
  reactionType = try { ReactionType.valueOf(reactionType) } catch (e: Exception) { ReactionType.LIKE },
  createdAt = createdAt
)

private fun StoryEntity.toDomain() = Story(
  id = id,
  userId = userId,
  userName = userName,
  userAvatar = userAvatar,
  isUserVerified = isUserVerified,
  mediaUrl = mediaUrl,
  textOverlay = textOverlay,
  backgroundGradientIndex = backgroundGradientIndex,
  timestamp = timestamp,
  expiresAt = expiresAt,
  viewsCount = viewsCount,
  isViewed = isViewed
)

private fun ReelEntity.toDomain() = Reel(
  id = id,
  creatorId = creatorId,
  creatorName = creatorName,
  creatorUsername = creatorUsername,
  creatorAvatar = creatorAvatar,
  isCreatorVerified = isCreatorVerified,
  videoThumbnail = videoThumbnail,
  caption = caption,
  audioTitle = audioTitle,
  likesCount = likesCount,
  commentsCount = commentsCount,
  sharesCount = sharesCount,
  isLiked = isLiked,
  isSaved = isSaved,
  isFollowing = isFollowing
)

private fun ConversationEntity.toDomain() = Conversation(
  id = id,
  participantId = participantId,
  participantName = participantName,
  participantUsername = participantUsername,
  participantAvatar = participantAvatar,
  isParticipantVerified = isParticipantVerified,
  lastMessage = lastMessage,
  lastMessageTimestamp = lastMessageTimestamp,
  unreadCount = unreadCount,
  isOnline = isOnline,
  lastActiveAt = 0L
)

private fun ConversationWithParticipant.toDomain() = Conversation(
  id = conversationId,
  participantId = participantId,
  participantName = participantName,
  participantUsername = participantUsername,
  participantAvatar = participantAvatar,
  isParticipantVerified = isParticipantVerified,
  lastMessage = lastMessage,
  lastMessageTimestamp = lastMessageTimestamp,
  unreadCount = unreadCount,
  isOnline = isOnline,
  lastActiveAt = lastActiveAt
)

private fun MessageEntity.toDomain(currentUserId: String = "user_me") = Message(
  id = id,
  conversationId = conversationId,
  senderId = senderId,
  receiverId = receiverId,
  messageType = messageType,
  text = text,
  mediaUrl = mediaUrl,
  timestamp = timestamp,
  isSeen = isSeen,
  isDeleted = isDeleted,
  isMine = (senderId == currentUserId)
)

private fun NotificationEntity.toDomain() = NotificationItem(
  id = id,
  type = try { NotificationType.valueOf(type) } catch (e: Exception) { NotificationType.LIKE },
  actorName = actorName,
  actorAvatar = actorAvatar,
  isActorVerified = isActorVerified,
  messageSnippet = messageSnippet,
  timestamp = timestamp,
  isRead = isRead,
  targetPostId = targetPostId,
  actionData = actionData,
  senderId = senderId
)

private fun FriendRequestEntity.toDomain() = FriendRequestItem(
  id = id,
  senderId = senderId,
  receiverId = receiverId,
  status = status,
  senderName = senderName,
  senderUsername = senderUsername,
  senderAvatar = senderAvatar,
  receiverName = receiverName,
  receiverUsername = receiverUsername,
  receiverAvatar = receiverAvatar,
  mutualFriendsCount = mutualFriendsCount,
  createdAt = createdAt
)

private fun PageEntity.toDomain() = SocivaPage(
  id = id,
  name = name,
  category = category,
  description = description,
  avatarUrl = avatarUrl,
  coverUrl = coverUrl,
  followersCount = followersCount,
  isLiked = isLiked,
  isAdmin = isAdmin,
  ownerId = ownerId,
  username = username,
  website = website,
  location = location,
  contactEmail = contactEmail,
  contactPhone = contactPhone,
  followingCount = followingCount,
  createdAt = createdAt,
  updatedAt = updatedAt
)

private fun GroupEntity.toDomain() = SocivaGroup(
  id = id,
  name = name,
  privacy = privacy,
  description = description,
  avatarUrl = avatarUrl,
  membersCount = membersCount,
  isJoined = isJoined,
  role = role,
  ownerId = ownerId,
  coverUrl = coverUrl,
  rules = rules,
  hasPendingJoinRequest = hasPendingJoinRequest,
  createdAt = createdAt,
  updatedAt = updatedAt
)

private fun PageAdminEntity.toDomain() = PageAdmin(
  id = id,
  pageId = pageId,
  userId = userId,
  userName = userName,
  userAvatar = userAvatar,
  role = role,
  assignedAt = assignedAt
)

private fun GroupMemberEntity.toDomain() = GroupMember(
  id = id,
  groupId = groupId,
  userId = userId,
  userName = userName,
  userUsername = userUsername,
  userAvatar = userAvatar,
  role = role,
  status = status,
  joinedAt = joinedAt
)

private fun GroupJoinRequestEntity.toDomain() = GroupJoinRequest(
  id = id,
  groupId = groupId,
  userId = userId,
  userName = userName,
  userUsername = userUsername,
  userAvatar = userAvatar,
  status = status,
  requestedAt = requestedAt
)

private fun PageFollowerEntity.toDomain() = PageFollower(
  id = id,
  pageId = pageId,
  userId = userId,
  userName = userName,
  userAvatar = userAvatar,
  followedAt = followedAt
)
