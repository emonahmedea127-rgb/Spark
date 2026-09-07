package com.example.sociva.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.sociva.data.local.SocivaDao
import com.example.sociva.data.service.FirestoreService
import com.example.sociva.data.service.MediaService
import com.example.sociva.data.service.MediaType
import com.example.sociva.data.service.ProcessedMedia
import com.example.sociva.data.service.ValidationResult
import com.example.sociva.data.service.awaitResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Reusable Media Repository interface for managing media validation,
 * Firebase Cloud Storage upload flows, download URLs, Firestore profile sync,
 * and Room caching.
 */
interface MediaRepository {
  fun validateMedia(uri: Uri): ValidationResult

  suspend fun uploadProfilePhoto(
    userId: String,
    bitmap: Bitmap,
    onProgress: (Float) -> Unit = {}
  ): Result<String>

  suspend fun uploadProfilePhotoFromUri(
    userId: String,
    uri: Uri,
    onProgress: (Float) -> Unit = {}
  ): Result<String>

  suspend fun uploadCoverPhoto(
    userId: String,
    bitmap: Bitmap,
    onProgress: (Float) -> Unit = {}
  ): Result<String>

  suspend fun uploadCoverPhotoFromUri(
    userId: String,
    uri: Uri,
    onProgress: (Float) -> Unit = {}
  ): Result<String>

  suspend fun uploadPostMedia(
    userId: String,
    postId: String,
    uri: Uri,
    onProgress: (Float) -> Unit = {}
  ): Result<ProcessedMedia>

  suspend fun uploadStoryMedia(
    userId: String,
    storyId: String,
    uri: Uri,
    onProgress: (Float) -> Unit = {}
  ): Result<ProcessedMedia>

  suspend fun deleteMedia(fileUrl: String): Boolean
}

/**
 * Implementation of [MediaRepository] coordinating [MediaService]
 * (Firebase Cloud Storage), [FirestoreService] (Firestore User Document),
 * and [SocivaDao] (Room Cache).
 */
class DefaultMediaRepository(
  private val context: Context,
  private val dao: SocivaDao,
  private val firestoreService: FirestoreService,
  private val mediaService: MediaService = MediaService(context)
) : MediaRepository {

  private val TAG = "DefaultMediaRepository"

  override fun validateMedia(uri: Uri): ValidationResult {
    return mediaService.validateMediaUri(uri)
  }

  private fun resolveAuthenticatedUid(userId: String): String {
    val currentAuthUid = try {
      FirebaseAuth.getInstance().currentUser?.uid
    } catch (e: Exception) {
      null
    }
    return when {
      !currentAuthUid.isNullOrBlank() -> currentAuthUid
      userId.isNotBlank() -> userId
      else -> "anonymous"
    }
  }

  override suspend fun uploadProfilePhoto(
    userId: String,
    bitmap: Bitmap,
    onProgress: (Float) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val uid = resolveAuthenticatedUid(userId)
      if (uid == "anonymous") {
        return@withContext Result.failure(
          IllegalStateException("User is not authenticated. Please log in to upload a profile picture.")
        )
      }

      // Step 1: Upload to Firebase Storage
      val uploadResult = mediaService.uploadImage(
        bitmap = bitmap,
        userId = uid,
        type = MediaType.PROFILE_PICTURE,
        onProgress = onProgress
      )

      val downloadUrl = uploadResult.getOrElse { error ->
        return@withContext Result.failure(error)
      }

      // Step 2: Update Room Cache
      val now = System.currentTimeMillis()
      dao.updateUserAvatar(uid, downloadUrl, now)
      dao.updateAuthorAvatarInPosts(uid, downloadUrl)
      dao.updateAuthorAvatarInComments(uid, downloadUrl)
      dao.updateUserAvatarInStories(uid, downloadUrl)
      dao.updateCreatorAvatarInReels(uid, downloadUrl)
      dao.updateParticipantAvatarInConversations(uid, downloadUrl)

      // Step 3: Update Firestore user profile
      firestoreService.updateProfilePhotoUrl(uid, downloadUrl)
      try {
        val userEntity = dao.getUserById(uid).first()
        if (userEntity != null) {
          firestoreService.syncUserProfile(userEntity.copy(avatarUrl = downloadUrl))
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed syncing user entity after avatar upload: ${e.message}")
      }

      // Step 4: Sync FirebaseAuth profile photo
      try {
        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser != null && authUser.uid == uid) {
          val changeRequest = UserProfileChangeRequest.Builder()
            .setPhotoUri(Uri.parse(downloadUrl))
            .build()
          authUser.updateProfile(changeRequest).awaitResult()
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to update FirebaseAuth photoUri: ${e.message}")
      }

      Result.success(downloadUrl)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun uploadProfilePhotoFromUri(
    userId: String,
    uri: Uri,
    onProgress: (Float) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val uid = resolveAuthenticatedUid(userId)
      if (uid == "anonymous") {
        return@withContext Result.failure(
          IllegalStateException("User is not authenticated. Please log in to upload a profile picture.")
        )
      }

      val uploadResult = mediaService.uploadImageFromUri(
        uri = uri,
        userId = uid,
        type = MediaType.PROFILE_PICTURE,
        onProgress = onProgress
      )

      val downloadUrl = uploadResult.getOrElse { error ->
        return@withContext Result.failure(error)
      }

      val now = System.currentTimeMillis()
      dao.updateUserAvatar(uid, downloadUrl, now)
      dao.updateAuthorAvatarInPosts(uid, downloadUrl)
      dao.updateAuthorAvatarInComments(uid, downloadUrl)
      dao.updateUserAvatarInStories(uid, downloadUrl)
      dao.updateCreatorAvatarInReels(uid, downloadUrl)
      dao.updateParticipantAvatarInConversations(uid, downloadUrl)

      firestoreService.updateProfilePhotoUrl(uid, downloadUrl)
      try {
        val userEntity = dao.getUserById(uid).first()
        if (userEntity != null) {
          firestoreService.syncUserProfile(userEntity.copy(avatarUrl = downloadUrl))
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed syncing user entity after avatar URI upload: ${e.message}")
      }

      Result.success(downloadUrl)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun uploadCoverPhoto(
    userId: String,
    bitmap: Bitmap,
    onProgress: (Float) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val uid = resolveAuthenticatedUid(userId)
      if (uid == "anonymous") {
        return@withContext Result.failure(
          IllegalStateException("User is not authenticated. Please log in to upload a cover photo.")
        )
      }

      val uploadResult = mediaService.uploadImage(
        bitmap = bitmap,
        userId = uid,
        type = MediaType.COVER_PHOTO,
        onProgress = onProgress
      )

      val downloadUrl = uploadResult.getOrElse { error ->
        return@withContext Result.failure(error)
      }

      val now = System.currentTimeMillis()
      dao.updateUserCover(uid, downloadUrl, now)

      firestoreService.updateCoverPhotoUrl(uid, downloadUrl)
      try {
        val userEntity = dao.getUserById(uid).first()
        if (userEntity != null) {
          firestoreService.syncUserProfile(userEntity.copy(coverUrl = downloadUrl))
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed syncing user entity after cover upload: ${e.message}")
      }

      Result.success(downloadUrl)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun uploadCoverPhotoFromUri(
    userId: String,
    uri: Uri,
    onProgress: (Float) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val uid = resolveAuthenticatedUid(userId)
      if (uid == "anonymous") {
        return@withContext Result.failure(
          IllegalStateException("User is not authenticated. Please log in to upload a cover photo.")
        )
      }

      val uploadResult = mediaService.uploadImageFromUri(
        uri = uri,
        userId = uid,
        type = MediaType.COVER_PHOTO,
        onProgress = onProgress
      )

      val downloadUrl = uploadResult.getOrElse { error ->
        return@withContext Result.failure(error)
      }

      val now = System.currentTimeMillis()
      dao.updateUserCover(uid, downloadUrl, now)

      firestoreService.updateCoverPhotoUrl(uid, downloadUrl)
      try {
        val userEntity = dao.getUserById(uid).first()
        if (userEntity != null) {
          firestoreService.syncUserProfile(userEntity.copy(coverUrl = downloadUrl))
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed syncing user entity after cover URI upload: ${e.message}")
      }

      Result.success(downloadUrl)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun uploadPostMedia(
    userId: String,
    postId: String,
    uri: Uri,
    onProgress: (Float) -> Unit
  ): Result<ProcessedMedia> = withContext(Dispatchers.IO) {
    val uid = resolveAuthenticatedUid(userId)
    mediaService.uploadMediaFromUri(
      uri = uri,
      userId = uid,
      type = MediaType.POST_MEDIA,
      targetId = postId,
      onProgress = onProgress
    )
  }

  override suspend fun uploadStoryMedia(
    userId: String,
    storyId: String,
    uri: Uri,
    onProgress: (Float) -> Unit
  ): Result<ProcessedMedia> = withContext(Dispatchers.IO) {
    val uid = resolveAuthenticatedUid(userId)
    mediaService.uploadMediaFromUri(
      uri = uri,
      userId = uid,
      type = MediaType.STORY_MEDIA,
      targetId = storyId,
      onProgress = onProgress
    )
  }

  override suspend fun deleteMedia(fileUrl: String): Boolean = withContext(Dispatchers.IO) {
    mediaService.deleteImage(fileUrl)
  }
}
