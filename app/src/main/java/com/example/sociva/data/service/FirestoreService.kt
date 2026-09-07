package com.example.sociva.data.service

import android.util.Log
import com.example.sociva.data.local.CommentEntity
import com.example.sociva.data.local.MessageEntity
import com.example.sociva.data.local.PostEntity
import com.example.sociva.data.local.SocivaDao
import com.example.sociva.data.local.UserEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                  val id = doc.getString("id") ?: doc.id
                  val authorId = doc.getString("authorId") ?: return@mapNotNull null
                  val content = doc.getString("content") ?: ""
                  PostEntity(
                    id = id,
                    authorId = authorId,
                    authorName = doc.getString("authorName") ?: "Spark User",
                    authorUsername = doc.getString("authorUsername") ?: "sparkuser",
                    authorAvatar = doc.getString("authorAvatar") ?: "",
                    isAuthorVerified = doc.getBoolean("isAuthorVerified") ?: false,
                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                    content = content,
                    mediaUrlsString = doc.getString("mediaUrlsString") ?: "",
                    feelingOrActivity = doc.getString("feelingOrActivity"),
                    audience = doc.getString("audience") ?: "Public",
                    likesCount = (doc.getLong("likesCount") ?: 0L).toInt(),
                    commentsCount = (doc.getLong("commentsCount") ?: 0L).toInt(),
                    sharesCount = (doc.getLong("sharesCount") ?: 0L).toInt(),
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
   * Uploads or updates a post in Cloud Firestore.
   */
  fun publishPost(post: PostEntity) {
    val db = firestore ?: return
    scope.launch(Dispatchers.IO) {
      try {
        val map = hashMapOf(
          "id" to post.id,
          "authorId" to post.authorId,
          "authorName" to post.authorName,
          "authorUsername" to post.authorUsername,
          "authorAvatar" to post.authorAvatar,
          "isAuthorVerified" to post.isAuthorVerified,
          "timestamp" to post.timestamp,
          "content" to post.content,
          "mediaUrlsString" to post.mediaUrlsString,
          "feelingOrActivity" to (post.feelingOrActivity ?: ""),
          "audience" to post.audience,
          "likesCount" to post.likesCount,
          "commentsCount" to post.commentsCount,
          "sharesCount" to post.sharesCount,
          "authorType" to post.authorType,
          "targetGroupId" to (post.targetGroupId ?: ""),
          "targetGroupName" to (post.targetGroupName ?: "")
        )
        db.collection("posts").document(post.id).set(map, SetOptions.merge())
      } catch (e: Exception) {
        Log.w(TAG, "Failed to sync post to Firestore: ${e.message}")
      }
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
