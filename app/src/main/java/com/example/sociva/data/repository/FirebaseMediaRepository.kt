package com.example.sociva.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.sociva.data.service.MediaCategory
import com.example.sociva.data.service.MediaType
import com.example.sociva.data.service.ProcessedMedia
import com.example.sociva.data.service.ValidationResult
import com.example.sociva.data.service.awaitResult
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID

/**
 * Concrete implementation of [MediaRepository] interacting directly with Firebase Cloud Storage.
 *
 * Implements strict user-owned folder hierarchy:
 * - users/{uid}/profile/avatar_{timestamp}.jpg
 * - users/{uid}/profile/cover_{timestamp}.jpg
 * - users/{uid}/posts/{postId}/{fileName}
 * - users/{uid}/stories/{storyId}/{fileName}
 */
class FirebaseMediaRepository(
  private val context: Context
) : MediaRepository {

  companion object {
    private const val TAG = "FirebaseMediaRepo"

    const val MAX_IMAGE_SIZE_BYTES = 15 * 1024 * 1024L // 15 MB
    const val MAX_VIDEO_SIZE_BYTES = 50 * 1024 * 1024L // 50 MB

    val SUPPORTED_IMAGE_MIMES = listOf(
      "image/jpeg",
      "image/jpg",
      "image/png",
      "image/webp"
    )
    val SUPPORTED_IMAGE_EXTS = listOf("jpg", "jpeg", "png", "webp")

    val SUPPORTED_VIDEO_MIMES = listOf(
      "video/mp4",
      "video/quicktime",
      "video/webm",
      "video/3gpp",
      "video/x-matroska",
      "video/mpeg"
    )
    val SUPPORTED_VIDEO_EXTS = listOf("mp4", "mov", "webm", "3gp", "mkv")
  }

  private val firebaseStorage: FirebaseStorage? by lazy {
    try {
      FirebaseStorage.getInstance()
    } catch (e: Exception) {
      Log.w(TAG, "Standard FirebaseStorage.getInstance() failed, attempting bucket fallback: ${e.message}")
      try {
        FirebaseStorage.getInstance("gs://helloworld-d72dc.firebasestorage.app")
      } catch (e2: Exception) {
        Log.e(TAG, "Failed to initialize FirebaseStorage: ${e2.message}")
        null
      }
    }
  }

  private val storageRef: StorageReference?
    get() = firebaseStorage?.reference

  private fun isNetworkAvailable(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNet = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(activeNet) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  private fun getEffectiveUserId(providedUserId: String): String {
    val authUid = FirebaseAuth.getInstance().currentUser?.uid
    return if (!authUid.isNullOrBlank()) {
      authUid
    } else {
      providedUserId.ifBlank { "anonymous_user" }
    }
  }

  override fun validateMediaUri(uri: Uri): ValidationResult {
    return try {
      val contentResolver = context.contentResolver
      val mimeType = contentResolver.getType(uri)?.lowercase(Locale.ROOT)
      val uriString = uri.toString().lowercase(Locale.ROOT)

      val isImageMime = mimeType != null && SUPPORTED_IMAGE_MIMES.any { mimeType.contains(it) }
      val isImageExt = SUPPORTED_IMAGE_EXTS.any { ext -> uriString.endsWith(".$ext") || uriString.contains(".$ext?") }
      val isImage = isImageMime || isImageExt

      val isVideoMime = mimeType != null && SUPPORTED_VIDEO_MIMES.any { mimeType.contains(it) }
      val isVideoExt = SUPPORTED_VIDEO_EXTS.any { ext -> uriString.endsWith(".$ext") || uriString.contains(".$ext?") }
      val isVideo = isVideoMime || isVideoExt

      if (!isImage && !isVideo) {
        return ValidationResult(
          isValid = false,
          errorMessage = "Unsupported file format. Please choose a JPG, PNG, WEBP image or MP4, MOV, WEBM video."
        )
      }

      var fileSize = 0L
      contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        fileSize = pfd.statSize
      }

      if (fileSize <= 0L) {
        // Fallback length check via input stream
        try {
          contentResolver.openInputStream(uri)?.use { stream ->
            fileSize = stream.available().toLong()
          }
        } catch (_: Exception) {}
      }

      val category = if (isVideo) MediaCategory.VIDEO else MediaCategory.IMAGE
      val maxAllowed = if (isVideo) MAX_VIDEO_SIZE_BYTES else MAX_IMAGE_SIZE_BYTES

      if (fileSize > maxAllowed) {
        val mb = fileSize / (1024 * 1024.0)
        val limitMb = if (isVideo) 50 else 15
        return ValidationResult(
          isValid = false,
          errorMessage = "${if (isVideo) "Video" else "Image"} size (${"%.1f".format(mb)} MB) exceeds the $limitMb MB limit.",
          category = category,
          sizeBytes = fileSize
        )
      }

      ValidationResult(
        isValid = true,
        mimeType = mimeType ?: if (isVideo) "video/mp4" else "image/jpeg",
        sizeBytes = fileSize,
        category = category
      )
    } catch (e: Exception) {
      ValidationResult(
        isValid = false,
        errorMessage = "Could not read selected media file: ${e.localizedMessage ?: "Unknown error"}"
      )
    }
  }

  override fun validateBitmap(bitmap: Bitmap, type: MediaType): ValidationResult {
    return try {
      if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
        return ValidationResult(
          isValid = false,
          errorMessage = "Selected image bitmap is invalid or has zero dimensions."
        )
      }
      val byteCount = bitmap.byteCount.toLong()
      if (byteCount > MAX_IMAGE_SIZE_BYTES * 2) {
        return ValidationResult(
          isValid = false,
          errorMessage = "Image dimension is too large for upload.",
          category = MediaCategory.IMAGE,
          sizeBytes = byteCount
        )
      }
      ValidationResult(
        isValid = true,
        mimeType = "image/jpeg",
        sizeBytes = byteCount,
        category = MediaCategory.IMAGE
      )
    } catch (e: Exception) {
      ValidationResult(
        isValid = false,
        errorMessage = "Bitmap validation error: ${e.localizedMessage}"
      )
    }
  }

  /**
   * Resizes and compresses bitmap into JPEG byte array.
   */
  fun compressAndResize(bitmap: Bitmap, type: MediaType): ByteArray {
    val maxDimension = when (type) {
      MediaType.PROFILE_PICTURE -> 800
      MediaType.COVER_PHOTO -> 1920
      MediaType.POST_MEDIA, MediaType.STORY_MEDIA -> 1440
    }

    val width = bitmap.width
    val height = bitmap.height
    val scaledBitmap = if (width > maxDimension || height > maxDimension) {
      val ratio = width.toFloat() / height.toFloat()
      val targetW = if (width >= height) maxDimension else (maxDimension * ratio).toInt()
      val targetH = if (height > width) maxDimension else (maxDimension / ratio).toInt()
      Bitmap.createScaledBitmap(bitmap, targetW.coerceAtLeast(1), targetH.coerceAtLeast(1), true)
    } else {
      bitmap
    }

    val outputStream = ByteArrayOutputStream()
    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 88, outputStream)
    return outputStream.toByteArray()
  }

  override suspend fun uploadProfilePhoto(
    userId: String,
    bitmap: Bitmap,
    onProgress: (Float) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      if (!isNetworkAvailable()) {
        return@withContext Result.failure(
          IllegalStateException("No internet connection. Please connect to the internet to upload your profile picture.")
        )
      }

      val validation = validateBitmap(bitmap, MediaType.PROFILE_PICTURE)
      if (!validation.isValid) {
        return@withContext Result.failure(IllegalArgumentException(validation.errorMessage ?: "Invalid photo"))
      }

      onProgress(0.15f)
      val compressedBytes = compressAndResize(bitmap, MediaType.PROFILE_PICTURE)
      onProgress(0.35f)

      if (compressedBytes.size > MAX_IMAGE_SIZE_BYTES) {
        return@withContext Result.failure(
          IllegalArgumentException("Optimized image exceeds 15 MB limit.")
        )
      }

      // Storage structure: users/{uid}/profile/avatar_{timestamp}.jpg
      val effectiveUid = getEffectiveUserId(userId)
      val root = storageRef ?: return@withContext Result.failure(
        IllegalStateException("Storage service is temporarily unavailable. Please try again later.")
      )
      val fileName = "avatar_${System.currentTimeMillis()}.jpg"
      val fileRef = root.child("users/$effectiveUid/profile/$fileName")

      val metadata = StorageMetadata.Builder()
        .setContentType("image/jpeg")
        .setCustomMetadata("userId", effectiveUid)
        .setCustomMetadata("mediaType", "avatar")
        .setCustomMetadata("uploadedAt", System.currentTimeMillis().toString())
        .build()

      val uploadTask = fileRef.putBytes(compressedBytes, metadata)
      setupProgressListener(uploadTask, baseProgress = 0.35f, weight = 0.55f, onProgress)

      uploadTask.awaitResult()
      onProgress(0.95f)

      val downloadUrl = fileRef.downloadUrl.awaitResult().toString()
      onProgress(1.0f)

      Log.i(TAG, "Profile picture uploaded successfully to Firebase Storage: $downloadUrl")
      Result.success(downloadUrl)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to upload profile picture: ${e.message}", e)
      Result.failure(mapFirebaseStorageError(e))
    }
  }

  override suspend fun uploadCoverPhoto(
    userId: String,
    bitmap: Bitmap,
    onProgress: (Float) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      if (!isNetworkAvailable()) {
        return@withContext Result.failure(
          IllegalStateException("No internet connection. Please connect to the internet to upload your cover photo.")
        )
      }

      val validation = validateBitmap(bitmap, MediaType.COVER_PHOTO)
      if (!validation.isValid) {
        return@withContext Result.failure(IllegalArgumentException(validation.errorMessage ?: "Invalid cover photo"))
      }

      onProgress(0.15f)
      val compressedBytes = compressAndResize(bitmap, MediaType.COVER_PHOTO)
      onProgress(0.35f)

      if (compressedBytes.size > MAX_IMAGE_SIZE_BYTES) {
        return@withContext Result.failure(
          IllegalArgumentException("Optimized cover photo exceeds 15 MB limit.")
        )
      }

      // Storage structure: users/{uid}/profile/cover_{timestamp}.jpg
      val effectiveUid = getEffectiveUserId(userId)
      val root = storageRef ?: return@withContext Result.failure(
        IllegalStateException("Storage service is temporarily unavailable. Please try again later.")
      )
      val fileName = "cover_${System.currentTimeMillis()}.jpg"
      val fileRef = root.child("users/$effectiveUid/profile/$fileName")

      val metadata = StorageMetadata.Builder()
        .setContentType("image/jpeg")
        .setCustomMetadata("userId", effectiveUid)
        .setCustomMetadata("mediaType", "cover")
        .setCustomMetadata("uploadedAt", System.currentTimeMillis().toString())
        .build()

      val uploadTask = fileRef.putBytes(compressedBytes, metadata)
      setupProgressListener(uploadTask, baseProgress = 0.35f, weight = 0.55f, onProgress)

      uploadTask.awaitResult()
      onProgress(0.95f)

      val downloadUrl = fileRef.downloadUrl.awaitResult().toString()
      onProgress(1.0f)

      Log.i(TAG, "Cover photo uploaded successfully to Firebase Storage: $downloadUrl")
      Result.success(downloadUrl)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to upload cover photo: ${e.message}", e)
      Result.failure(mapFirebaseStorageError(e))
    }
  }

  override suspend fun uploadPostMedia(
    userId: String,
    uri: Uri,
    postId: String,
    onProgress: (Float) -> Unit
  ): Result<ProcessedMedia> = uploadMediaFromUri(
    uri = uri,
    userId = userId,
    type = MediaType.POST_MEDIA,
    targetId = postId,
    onProgress = onProgress
  )

  override suspend fun uploadStoryMedia(
    userId: String,
    uri: Uri,
    storyId: String,
    onProgress: (Float) -> Unit
  ): Result<ProcessedMedia> = uploadMediaFromUri(
    uri = uri,
    userId = userId,
    type = MediaType.STORY_MEDIA,
    targetId = storyId,
    onProgress = onProgress
  )

  override suspend fun uploadMediaFromUri(
    uri: Uri,
    userId: String,
    type: MediaType,
    targetId: String?,
    onProgress: (Float) -> Unit
  ): Result<ProcessedMedia> = withContext(Dispatchers.IO) {
    try {
      if (!isNetworkAvailable()) {
        return@withContext Result.failure(
          IllegalStateException("No internet connection. Please verify your internet and try again.")
        )
      }

      onProgress(0.05f)
      val validation = validateMediaUri(uri)
      if (!validation.isValid) {
        return@withContext Result.failure(IllegalArgumentException(validation.errorMessage ?: "Invalid media file."))
      }

      val effectiveUid = getEffectiveUserId(userId)
      val root = storageRef ?: return@withContext Result.failure(
        IllegalStateException("Storage service is temporarily unavailable. Please try again later.")
      )

      val entityFolder = when (type) {
        MediaType.PROFILE_PICTURE, MediaType.COVER_PHOTO -> "profile"
        MediaType.POST_MEDIA -> "posts/${targetId ?: "post_${System.currentTimeMillis()}"}"
        MediaType.STORY_MEDIA -> "stories/${targetId ?: "story_${System.currentTimeMillis()}"}"
      }

      val isVideo = validation.category == MediaCategory.VIDEO

      if (isVideo) {
        // Video upload to Firebase Cloud Storage
        val ext = when {
          validation.mimeType?.contains("webm") == true -> "webm"
          validation.mimeType?.contains("quicktime") == true -> "mov"
          validation.mimeType?.contains("3gpp") == true -> "3gp"
          validation.mimeType?.contains("matroska") == true -> "mkv"
          else -> "mp4"
        }
        val videoFileName = "video_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext"
        val videoRef = root.child("users/$effectiveUid/$entityFolder/$videoFileName")

        val videoMetadata = StorageMetadata.Builder()
          .setContentType(validation.mimeType ?: "video/mp4")
          .setCustomMetadata("userId", effectiveUid)
          .setCustomMetadata("mediaType", type.name)
          .build()

        onProgress(0.15f)
        val uploadTask = videoRef.putFile(uri, videoMetadata)
        setupProgressListener(uploadTask, baseProgress = 0.15f, weight = 0.65f, onProgress)

        uploadTask.awaitResult()
        onProgress(0.85f)
        val videoDownloadUrl = videoRef.downloadUrl.awaitResult().toString()

        // Generate video thumbnail and upload
        var thumbDownloadUrl: String? = null
        try {
          val retriever = MediaMetadataRetriever()
          retriever.setDataSource(context, uri)
          val frameBitmap = retriever.getFrameAtTime(1000000) ?: retriever.frameAtTime
          retriever.release()

          if (frameBitmap != null) {
            val thumbBytes = compressAndResize(frameBitmap, type)
            val thumbFileName = "thumb_${System.currentTimeMillis()}.jpg"
            val thumbRef = root.child("users/$effectiveUid/$entityFolder/$thumbFileName")
            val thumbMeta = StorageMetadata.Builder()
              .setContentType("image/jpeg")
              .setCustomMetadata("userId", effectiveUid)
              .build()

            thumbRef.putBytes(thumbBytes, thumbMeta).awaitResult()
            thumbDownloadUrl = thumbRef.downloadUrl.awaitResult().toString()
          }
        } catch (e: Exception) {
          Log.w(TAG, "Video thumbnail generation failed: ${e.message}")
        }

        onProgress(1.0f)
        Result.success(
          ProcessedMedia(
            url = videoDownloadUrl,
            category = MediaCategory.VIDEO,
            thumbnailUrl = thumbDownloadUrl ?: videoDownloadUrl,
            mimeType = validation.mimeType ?: "video/mp4"
          )
        )
      } else {
        // Image upload to Firebase Cloud Storage
        onProgress(0.15f)
        val inputStream = context.contentResolver.openInputStream(uri)
          ?: return@withContext Result.failure(IllegalArgumentException("Unable to read image stream"))
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        if (bitmap == null) {
          return@withContext Result.failure(IllegalArgumentException("Could not decode selected image."))
        }

        onProgress(0.30f)
        val compressedBytes = compressAndResize(bitmap, type)
        val imageFileName = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
        val imageRef = root.child("users/$effectiveUid/$entityFolder/$imageFileName")

        val metadata = StorageMetadata.Builder()
          .setContentType("image/jpeg")
          .setCustomMetadata("userId", effectiveUid)
          .setCustomMetadata("mediaType", type.name)
          .build()

        val uploadTask = imageRef.putBytes(compressedBytes, metadata)
        setupProgressListener(uploadTask, baseProgress = 0.30f, weight = 0.60f, onProgress)

        uploadTask.awaitResult()
        onProgress(0.95f)

        val imageDownloadUrl = imageRef.downloadUrl.awaitResult().toString()
        onProgress(1.0f)

        Result.success(
          ProcessedMedia(
            url = imageDownloadUrl,
            category = MediaCategory.IMAGE,
            thumbnailUrl = imageDownloadUrl,
            mimeType = "image/jpeg"
          )
        )
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to upload media from URI: ${e.message}", e)
      Result.failure(mapFirebaseStorageError(e))
    }
  }

  override suspend fun deleteMedia(fileUrl: String): Boolean = withContext(Dispatchers.IO) {
    if (!fileUrl.contains("firebasestorage.googleapis.com")) {
      return@withContext true
    }
    return@withContext try {
      val storage = firebaseStorage ?: return@withContext false
      storage.getReferenceFromUrl(fileUrl).delete().awaitResult()
      true
    } catch (e: Exception) {
      Log.w(TAG, "Failed to delete file from Firebase Storage: ${e.message}")
      false
    }
  }

  private fun setupProgressListener(
    task: UploadTask,
    baseProgress: Float,
    weight: Float,
    onProgress: (Float) -> Unit
  ) {
    task.addOnProgressListener { snapshot ->
      val total = snapshot.totalByteCount
      if (total > 0) {
        val fraction = (snapshot.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        val currentProgress = (baseProgress + (fraction * weight)).coerceIn(0f, 0.99f)
        onProgress(currentProgress)
      }
    }
  }

  private fun mapFirebaseStorageError(e: Throwable): Throwable {
    return when (e) {
      is StorageException -> {
        val userFriendlyMessage = when (e.errorCode) {
          StorageException.ERROR_NOT_AUTHENTICATED ->
            "Please sign in to upload media."
          StorageException.ERROR_NOT_AUTHORIZED ->
            "You don't have permission to modify this media."
          StorageException.ERROR_RETRY_LIMIT_EXCEEDED ->
            "Upload timed out. Please check your internet connection and try again."
          StorageException.ERROR_QUOTA_EXCEEDED ->
            "Service is temporarily unavailable. Please try again later."
          StorageException.ERROR_OBJECT_NOT_FOUND ->
            "Media file not found."
          StorageException.ERROR_CANCELED ->
            "Upload was cancelled."
          else ->
            "Couldn't upload media. Please try again."
        }
        Exception(userFriendlyMessage, e)
      }
      is FirebaseNetworkException -> {
        Exception("Network connection issue. Please verify your internet and try again.", e)
      }
      is java.net.UnknownHostException, is java.io.IOException -> {
        Exception("Network connection issue. Please verify your internet connection.", e)
      }
      else -> e
    }
  }
}
