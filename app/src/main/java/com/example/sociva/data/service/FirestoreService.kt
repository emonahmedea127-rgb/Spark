package com.example.sociva.data.service

import android.util.Log
import com.example.sociva.data.local.CommentEntity
import com.example.sociva.data.local.MessageEntity
import com.example.sociva.data.local.PostEntity
import com.example.sociva.data.local.SocivaDao
import com.example.sociva.data.local.UserEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Service to synchronize Spark data (Posts, Comments, Messages, Profiles)
 * with Firebase Cloud Firestore in real-time.
 */
class FirestoreService(
  private val dao: SocivaDao,
  private val scope: CoroutineScope
) {
  private val TAG = "FirestoreService"

  private val firestore: FirebaseFirestore? by lazy {
    try {
      FirebaseFirestore.getInstance()
    } catch (e: Exception) {
      Log.w(TAG, "FirebaseFirestore is not initialized: ${e.message}")
      null
    }
  }

  private var postsListener: ListenerRegistration? = null
  private var messagesListener: ListenerRegistration? = null
  private var userProfileListener: ListenerRegistration? = null

  fun startSync() {
    startPostsListener()
  }

  fun stopSync() {
    postsListener?.remove()
    postsListener = null
    messagesListener?.remove()
    messagesListener = null
    userProfileListener?.remove()
    userProfileListener = null
  }

  /**
   * Listens to posts collection in real-time and merges updates into local Room database.
   */
  private fun startPostsListener() {
    val db = firestore ?: return
    try {
      postsListener = db.collection("posts")
        .orderBy("timestamp", Query.Direction.DESCENDING)
        .limit(50)
        .addSnapshotListener { snapshots, error ->
          if (error != null) {
            Log.w(TAG, "Posts snapshot error: ${error.message}")
            return@addSnapshotListener
          }
          if (snapshots != null && !snapshots.isEmpty) {
            scope.launch(Dispatchers.IO) {
              val entities = snapshots.documents.mapNotNull { doc ->
                try {
                  val id = doc.getString("postId") ?: doc.getString("id") ?: doc.id
                  val authorId = doc.getString("authorId") ?: return@mapNotNull null
                  val content = doc.getString("caption") ?: doc.getString("content") ?: ""
                  val mediaUrlsFromList = (doc.get("mediaUrls") as? List<*>)?.mapNotNull { it?.toString() }?.joinToString(",")
                  val mediaUrlsStr = doc.getString("mediaUrlsString") ?: mediaUrlsFromList ?: ""
                  val timestamp = doc.getLong("createdAt") ?: doc.getLong("timestamp") ?: System.currentTimeMillis()
                  val likes = (doc.getLong("likesCount") ?: doc.getLong("likeCount") ?: doc.getLong("reactionCount") ?: 0L).toInt()
                  val comments = (doc.getLong("commentsCount") ?: doc.getLong("commentCount") ?: 0L).toInt()
                  val shares = (doc.getLong("sharesCount") ?: doc.getLong("shareCount") ?: 0L).toInt()
                  val audience = doc.getString("audience") ?: doc.getString("visibility") ?: doc.getString("privacy") ?: "Public"
                  val feeling = doc.getString("feelingOrActivity") ?: doc.getString("feeling")
                  PostEntity(
                    id = id,
                    authorId = authorId,
                    authorName = doc.getString("authorName") ?: "Spark User",
                    authorUsername = doc.getString("authorUsername") ?: "sparkuser",
                    authorAvatar = doc.getString("authorAvatar") ?: "",
                    isAuthorVerified = doc.getBoolean("isAuthorVerified") ?: false,
                    timestamp = timestamp,
                    content = content,
                    mediaUrlsString = mediaUrlsStr,
                    feelingOrActivity = feeling,
                    audience = audience,
                    likesCount = likes,
                    commentsCount = comments,
                    sharesCount = shares,
                    myReaction = null,
                    isSaved = false,
                    authorType = doc.getString("authorType") ?: "PERSONAL",
                    targetGroupId = doc.getString("targetGroupId"),
                    targetGroupName = doc.getString("targetGroupName")
                  )
                } catch (e: Exception) {
                  null
                }
              }
              if (entities.isNotEmpty()) {
                dao.insertPosts(entities)
              }
            }
          }
        }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to attach posts listener: ${e.message}")
    }
  }

  /**
   * Persists a real post document to Cloud Firestore at collection 'posts/{postId}'.
   * Uses FirebaseAuth.currentUser?.uid as authoritative authorId.
   * Awaits completion and returns Result.success or Result.failure with the actual exception.
   */
  suspend fun publishPost(
    post: PostEntity,
    mediaUrls: List<String> = emptyList(),
    taggedUserIds: List<String> = emptyList()
  ): Result<PostEntity> = withContext(Dispatchers.IO) {
    val db = firestore
      ?: return@withContext Result.failure(
        IllegalStateException("Service is temporarily unavailable.")
      )

    val fbAuth = FirebaseAuth.getInstance()
    val currentUser = fbAuth.currentUser
    if (currentUser == null) {
      Log.e(TAG, "Cannot publish post: User is not authenticated with Firebase Authentication.")
      return@withContext Result.failure(IllegalStateException("Please sign in to publish a post."))
    }
    val authoritativeAuthorId = currentUser.uid

    val mediaList: List<String> = if (mediaUrls.isNotEmpty()) {
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

    val postMap = hashMapOf<String, Any?>(
      "postId" to post.id,
      "id" to post.id,
      "authorId" to authoritativeAuthorId,
      "authorName" to post.authorName,
      "authorUsername" to post.authorUsername,
      "authorAvatar" to post.authorAvatar,
      "isAuthorVerified" to post.isAuthorVerified,
      "caption" to post.content,
      "content" to post.content,
      "createdAt" to post.timestamp,
      "timestamp" to post.timestamp,
      "visibility" to post.audience,
      "privacy" to post.audience,
      "audience" to post.audience,
      "mediaUrls" to mediaList,
      "mediaUrlsString" to post.mediaUrlsString,
      "mediaType" to mediaType,
      "likeCount" to post.likesCount,
      "likesCount" to post.likesCount,
      "reactionCount" to post.likesCount,
      "commentCount" to post.commentsCount,
      "commentsCount" to post.commentsCount,
      "shareCount" to post.sharesCount,
      "sharesCount" to post.sharesCount,
      "feeling" to (post.feelingOrActivity ?: ""),
      "feelingOrActivity" to (post.feelingOrActivity ?: ""),
      "authorType" to post.authorType,
      "targetGroupId" to (post.targetGroupId ?: ""),
      "targetGroupName" to (post.targetGroupName ?: ""),
      "taggedUserIds" to taggedUserIds
    )

    try {
      Log.i(TAG, "Writing post ${post.id} to Cloud Firestore path 'posts/${post.id}' for author $authoritativeAuthorId")
      db.collection("posts").document(post.id).set(postMap).awaitResult()
      Log.i(TAG, "Successfully committed post ${post.id} to Cloud Firestore.")
      Result.success(post.copy(authorId = authoritativeAuthorId))
    } catch (e: Exception) {
      Log.e(TAG, "Failed to persist post ${post.id} to Firestore path 'posts/${post.id}': ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Publishes comment to Cloud Firestore.
   */
  fun publishComment(comment: CommentEntity) {
    val db = firestore ?: return
    scope.launch(Dispatchers.IO) {
      try {
        val map = hashMapOf(
          "id" to comment.id,
          "postId" to comment.postId,
          "authorId" to comment.authorId,
          "authorName" to comment.authorName,
          "authorAvatar" to comment.authorAvatar,
          "isAuthorVerified" to comment.isAuthorVerified,
          "content" to comment.content,
          "timestamp" to comment.timestamp,
          "parentCommentId" to (comment.parentCommentId ?: "")
        )
        db.collection("comments").document(comment.id).set(map, SetOptions.merge())
      } catch (e: Exception) {
        Log.w(TAG, "Failed to sync comment to Firestore: ${e.message}")
      }
    }
  }

  /**
   * Publishes message to Cloud Firestore.
   */
  fun publishMessage(message: MessageEntity) {
    val db = firestore ?: return
    scope.launch(Dispatchers.IO) {
      try {
        val map = hashMapOf(
          "id" to message.id,
          "conversationId" to message.conversationId,
          "senderId" to message.senderId,
          "receiverId" to message.receiverId,
          "messageType" to message.messageType,
          "text" to message.text,
          "mediaUrl" to (message.mediaUrl ?: ""),
          "timestamp" to message.timestamp,
          "isSeen" to message.isSeen
        )
        db.collection("messages").document(message.id).set(map, SetOptions.merge())
      } catch (e: Exception) {
        Log.w(TAG, "Failed to sync message to Firestore: ${e.message}")
      }
    }
  }

  /**
   * Syncs user profile entity to Cloud Firestore (collection: users/{uid}).
   */
  fun syncUserProfile(user: UserEntity) {
    val db = firestore ?: return
    scope.launch(Dispatchers.IO) {
      try {
        val map = hashMapOf(
          "uid" to user.id,
          "id" to user.id,
          "username" to user.username,
          "firstName" to user.firstName,
          "lastName" to user.lastName,
          "fullName" to user.fullName,
          "email" to user.email,
          "phone" to user.phone,
          "dateOfBirth" to user.dateOfBirth,
          "gender" to user.gender,
          "profilePhotoUrl" to user.avatarUrl,
          "avatarUrl" to user.avatarUrl,
          "coverPhotoUrl" to user.coverUrl,
          "coverUrl" to user.coverUrl,
          "bio" to user.bio,
          "location" to user.location,
          "relationshipStatus" to user.relationshipStatus,
          "work" to user.work,
          "education" to user.education,
          "isVerified" to user.isVerified,
          "createdAt" to user.joinedDate,
          "updatedAt" to System.currentTimeMillis()
        )
        db.collection("users").document(user.id).set(map, SetOptions.merge())
      } catch (e: Exception) {
        Log.w(TAG, "Failed to sync user profile to Firestore: ${e.message}")
      }
    }
  }

  /**
   * Updates profilePhotoUrl and avatarUrl directly in Firestore users/{uid} document.
   */
  suspend fun updateProfilePhotoUrl(uid: String, photoUrl: String): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val updates = hashMapOf<String, Any>(
        "profilePhotoUrl" to photoUrl,
        "avatarUrl" to photoUrl,
        "updatedAt" to System.currentTimeMillis()
      )
      db.collection("users").document(uid).set(updates, SetOptions.merge()).awaitResult()
      true
    } catch (e: Exception) {
      Log.w(TAG, "Failed to update profilePhotoUrl in Firestore: ${e.message}")
      false
    }
  }

  /**
   * Updates coverPhotoUrl and coverUrl directly in Firestore users/{uid} document.
   */
  suspend fun updateCoverPhotoUrl(uid: String, coverUrl: String): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val updates = hashMapOf<String, Any>(
        "coverPhotoUrl" to coverUrl,
        "coverUrl" to coverUrl,
        "updatedAt" to System.currentTimeMillis()
      )
      db.collection("users").document(uid).set(updates, SetOptions.merge()).awaitResult()
      true
    } catch (e: Exception) {
      Log.w(TAG, "Failed to update coverPhotoUrl in Firestore: ${e.message}")
      false
    }
  }

  /**
   * Fetches the user profile directly from Firestore and caches into Room.
   */
  suspend fun fetchUserProfile(uid: String): UserEntity? {
    val db = firestore ?: return null
    return try {
      val doc = db.collection("users").document(uid).get().awaitResult()
      if (doc.exists()) {
        val entity = UserEntity(
          id = uid,
          username = doc.getString("username") ?: uid,
          fullName = doc.getString("fullName") ?: "Spark User",
          firstName = doc.getString("firstName") ?: "",
          lastName = doc.getString("lastName") ?: "",
          avatarUrl = doc.getString("profilePhotoUrl") ?: doc.getString("avatarUrl") ?: "",
          coverUrl = doc.getString("coverPhotoUrl") ?: doc.getString("coverUrl") ?: "",
          bio = doc.getString("bio") ?: "",
          email = doc.getString("email") ?: "",
          phone = doc.getString("phone") ?: "",
          dateOfBirth = doc.getString("dateOfBirth") ?: "",
          gender = doc.getString("gender") ?: "",
          location = doc.getString("location") ?: "",
          relationshipStatus = doc.getString("relationshipStatus") ?: "",
          work = doc.getString("work") ?: "",
          education = doc.getString("education") ?: "",
          isVerified = doc.getBoolean("isVerified") ?: false,
          joinedDate = doc.getString("createdAt") ?: "Joined recently",
          isOnline = true
        )
        dao.insertUser(entity)
        entity
      } else {
        null
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to fetch user profile from Firestore: ${e.message}")
      null
    }
  }

  /**
   * Attaches real-time listener to user profile in Cloud Firestore.
   */
  fun startUserProfileListener(uid: String) {
    val db = firestore ?: return
    userProfileListener?.remove()
    try {
      userProfileListener = db.collection("users").document(uid)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            Log.w(TAG, "User profile listener error: ${error.message}")
            return@addSnapshotListener
          }
          if (snapshot != null && snapshot.exists()) {
            scope.launch(Dispatchers.IO) {
              try {
                val entity = UserEntity(
                  id = uid,
                  username = snapshot.getString("username") ?: uid,
                  fullName = snapshot.getString("fullName") ?: "Spark User",
                  firstName = snapshot.getString("firstName") ?: "",
                  lastName = snapshot.getString("lastName") ?: "",
                  avatarUrl = snapshot.getString("profilePhotoUrl") ?: snapshot.getString("avatarUrl") ?: "",
                  coverUrl = snapshot.getString("coverPhotoUrl") ?: snapshot.getString("coverUrl") ?: "",
                  bio = snapshot.getString("bio") ?: "",
                  email = snapshot.getString("email") ?: "",
                  phone = snapshot.getString("phone") ?: "",
                  dateOfBirth = snapshot.getString("dateOfBirth") ?: "",
                  gender = snapshot.getString("gender") ?: "",
                  location = snapshot.getString("location") ?: "",
                  relationshipStatus = snapshot.getString("relationshipStatus") ?: "",
                  work = snapshot.getString("work") ?: "",
                  education = snapshot.getString("education") ?: "",
                  isVerified = snapshot.getBoolean("isVerified") ?: false,
                  joinedDate = snapshot.getString("createdAt") ?: "Joined recently",
                  isOnline = true
                )
                dao.insertUser(entity)
              } catch (e: Exception) {
                Log.w(TAG, "Error mapping user document from Firestore: ${e.message}")
              }
            }
          }
        }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to attach user profile listener: ${e.message}")
    }
  }
}
