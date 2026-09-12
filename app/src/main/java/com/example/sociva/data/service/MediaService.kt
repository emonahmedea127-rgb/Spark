package com.example.sociva.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.sociva.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID

enum class MediaType {
  PROFILE_PICTURE,
  COVER_PHOTO,
  POST_MEDIA,
  STORY_MEDIA
}

enum class MediaCategory {
  IMAGE,
  VIDEO
}

data class ProcessedMedia(
  val url: String,
  val category: MediaCategory,
  val thumbnailUrl: String? = null,
  val mimeType: String = ""
)

sealed class UploadState {
  object Idle : UploadState()
  data class Validating(val message: String = "Validating media format & size...") : UploadState()
  data class Compressing(val message: String = "Optimizing media...") : UploadState()
  data class Uploading(val progress: Float) : UploadState()
  data class Success(val url: String, val mediaType: MediaType = MediaType.POST_MEDIA) : UploadState()
  data class Error(val message: String, val canRetry: Boolean = true) : UploadState()
}

data class ValidationResult(
  val isValid: Boolean,
  val errorMessage: String? = null,
  val mimeType: String? = null,
  val sizeBytes: Long = 0L,
  val category: MediaCategory = MediaCategory.IMAGE
)

interface MediaStorageAdapter {
  suspend fun store(
    storagePath: String,
    bytes: ByteArray,
    mimeType: String,
    onProgress: (Float) -> Unit = {}
  ): Result<String>

  suspend fun storeStream(
    storagePath: String,
    inputStream: InputStream,
    mimeType: String,
    onProgress: (Float) -> Unit = {}
  ): Result<String>

  suspend fun delete(fileUrl: String): Boolean
  fun getUrl(mediaId: String): String
}

/**
 * Storage adapter that persists to application private object storage for local testing and offline capability.
 */
class SocivaStorageAdapter(private val context: Context) : MediaStorageAdapter {
  private val cloudBucketEndpoint: String? = try {
    System.getenv("SOCIVA_STORAGE_ENDPOINT")
  } catch (e: Exception) {
    null
  }

  private val mediaDir: File
    get() {
      val dir = File(context.filesDir, "sociva_media")
      if (!dir.exists()) {
        dir.mkdirs()
      }
      return dir
    }

  override suspend fun store(
    storagePath: String,
    bytes: ByteArray,
    mimeType: String,
    onProgress: (Float) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val targetFile = File(mediaDir, storagePath)
      targetFile.parentFile?.mkdirs()
      FileOutputStream(targetFile).use { fos ->
        fos.write(bytes)
        fos.flush()
      }
      onProgress(1.0f)
      val localUri = targetFile.toURI().toString()
      Result.success(localUri)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun storeStream(
    storagePath: String,
    inputStream: InputStream,
    mimeType: String,
    onProgress: (Float) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val targetFile = File(mediaDir, storagePath)
      targetFile.parentFile?.mkdirs()
      FileOutputStream(targetFile).use { fos ->
        inputStream.copyTo(fos)
        fos.flush()
      }
      onProgress(1.0f)
      val localUri = targetFile.toURI().toString()
      Result.success(localUri)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun delete(fileUrl: String): Boolean = withContext(Dispatchers.IO) {
    try {
      if (fileUrl.startsWith("file:")) {
        val file = File(java.net.URI(fileUrl))
        if (file.exists()) {
          return@withContext file.delete()
        }
      }
      true
    } catch (e: Exception) {
      false
    }
  }

  override fun getUrl(mediaId: String): String {
    val cloud = cloudBucketEndpoint
    return if (!cloud.isNullOrBlank()) {
      "$cloud/$mediaId"
    } else {
      File(mediaDir, mediaId).toURI().toString()
    }
  }
}

/**
 * Storage adapter that uploads photos and videos to Supabase Storage,
 * enforcing bucket hierarchy and reporting upload progress.
 */
class SupabaseStorageAdapter(
  private val context: Context,
  private val fallbackAdapter: MediaStorageAdapter = SocivaStorageAdapter(context)
) : MediaStorageAdapter {

  private val TAG = "SupabaseStorageAdapter"

  override suspend fun store(
    storagePath: String,
    bytes: ByteArray,
    mimeType: String,
    onProgress: (Float) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    val client = SupabaseClientProvider.client
    if (client != null && SupabaseClientProvider.isConfigured()) {
      try {
        onProgress(0.1f)
        val bucketName = when {
          storagePath.startsWith("avatars/") -> "avatars"
          storagePath.startsWith("covers/") -> "covers"
          storagePath.startsWith("reels/") -> "reels"
          else -> "post-media"
        }
        val cleanPath = storagePath.substringAfter("/")
        val bucket = client.storage[bucketName]
        onProgress(0.5f)
        bucket.upload(cleanPath, bytes) {
          upsert = true
        }
        val publicUrl = bucket.publicUrl(cleanPath)
        onProgress(1.0f)
        return@withContext Result.success(publicUrl)
      } catch (e: Exception) {
        Log.w(TAG, "Supabase storage upload failed, falling back to local storage: ${e.message}")
      }
    }
    fallbackAdapter.store(storagePath, bytes, mimeType, onProgress)
  }

  override suspend fun storeStream(
    storagePath: String,
    inputStream: InputStream,
    mimeType: String,
    onProgress: (Float) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val bytes = inputStream.use { it.readBytes() }
      store(storagePath, bytes, mimeType, onProgress)
    } catch (e: Exception) {
      fallbackAdapter.storeStream(storagePath, inputStream, mimeType, onProgress)
    }
  }

  override suspend fun delete(fileUrl: String): Boolean = withContext(Dispatchers.IO) {
    val client = SupabaseClientProvider.client
    if (client != null && SupabaseClientProvider.isConfigured() && fileUrl.contains("/storage/v1/object/public/")) {
      try {
        val pathAfterPublic = fileUrl.substringAfter("/storage/v1/object/public/")
        val bucketName = pathAfterPublic.substringBefore("/")
        val itemPath = pathAfterPublic.substringAfter("/")
        client.storage[bucketName].delete(itemPath)
        return@withContext true
      } catch (e: Exception) {
        Log.w(TAG, "Failed to delete file from Supabase Storage: ${e.message}")
      }
    }
    fallbackAdapter.delete(fileUrl)
  }

  override fun getUrl(mediaId: String): String {
    return fallbackAdapter.getUrl(mediaId)
  }
}

class MediaService(
  private val context: Context,
  private val storageAdapter: MediaStorageAdapter = SupabaseStorageAdapter(context)
) {

  companion object {
    const val MAX_IMAGE_SIZE_BYTES = 15 * 1024 * 1024L // 15 MB limit
    const val MAX_VIDEO_SIZE_BYTES = 50 * 1024 * 1024L // 50 MB limit

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

  /**
   * Validates file type (Image or Video) and size from URI before processing.
   */
  fun validateMediaUri(uri: Uri): ValidationResult {
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

      val category = if (isVideo) MediaCategory.VIDEO else MediaCategory.IMAGE
      val maxAllowed = if (isVideo) MAX_VIDEO_SIZE_BYTES else MAX_IMAGE_SIZE_BYTES

      if (fileSize > maxAllowed) {
        val mb = fileSize / (1024 * 1024.0)
        val limitMb = if (isVideo) 50 else 15
        return ValidationResult(
          isValid = false,
          errorMessage = "${if (isVideo) "Video" else "Image"} size (${"%.1f".format(mb)} MB) exceeds the $limitMb MB limit.",
          category = category
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
        errorMessage = "Could not read selected file: ${e.localizedMessage ?: "Unknown error"}"
      )
    }
  }

  fun validateUri(uri: Uri): ValidationResult = validateMediaUri(uri)

  /**
   * Compresses and resizes a bitmap according to target media type.
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

  /**
   * Generates the organized cloud storage path according to Spark requirements:
   * users/{uid}/profile/avatar/
   * users/{uid}/profile/cover/
   * users/{uid}/posts/{postId}/
   * users/{uid}/stories/{storyId}/
   */
  fun generateStoragePath(
    userId: String,
    type: MediaType,
    fileName: String,
    targetId: String? = null
  ): String {
    val cleanUserId = userId.trim().ifBlank { "anonymous" }
    return when (type) {
      MediaType.PROFILE_PICTURE -> "users/$cleanUserId/profile/avatar/$fileName"
      MediaType.COVER_PHOTO -> "users/$cleanUserId/profile/cover/$fileName"
      MediaType.POST_MEDIA -> {
        val postId = targetId?.ifBlank { null } ?: "post_${System.currentTimeMillis()}"
        "users/$cleanUserId/posts/$postId/$fileName"
      }
      MediaType.STORY_MEDIA -> {
        val storyId = targetId?.ifBlank { null } ?: "story_${System.currentTimeMillis()}"
        "users/$cleanUserId/stories/$storyId/$fileName"
      }
    }
  }

  /**
   * Uploads an edited/cropped bitmap with progress tracking.
   */
  suspend fun uploadImage(
    bitmap: Bitmap,
    userId: String,
    type: MediaType,
    targetId: String? = null,
    onProgress: (Float) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      if (userId.isBlank()) {
        return@withContext Result.failure(
          IllegalStateException("User is not authenticated. Please log in to upload images.")
        )
      }

      onProgress(0.15f)
      delay(30)

      val compressedBytes = compressAndResize(bitmap, type)
      onProgress(0.40f)
      delay(30)

      if (compressedBytes.size > MAX_IMAGE_SIZE_BYTES) {
        val mb = compressedBytes.size / (1024 * 1024.0)
        return@withContext Result.failure(
          IllegalArgumentException("Optimized image size (${"%.1f".format(mb)} MB) exceeds 15 MB limit.")
        )
      }

      val prefix = when (type) {
        MediaType.PROFILE_PICTURE -> "avatar"
        MediaType.COVER_PHOTO -> "cover"
        MediaType.POST_MEDIA -> "post"
        MediaType.STORY_MEDIA -> "story"
      }
      val fileName = "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
      val storagePath = generateStoragePath(userId, type, fileName, targetId)

      onProgress(0.50f)
      val storeResult = storageAdapter.store(storagePath, compressedBytes, "image/jpeg") { progress ->
        val mapped = 0.50f + (progress * 0.50f)
        onProgress(mapped.coerceIn(0f, 1f))
      }
      onProgress(1.0f)
      storeResult
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun uploadImage(
    bitmap: Bitmap,
    userId: String,
    type: MediaType,
    onProgress: (Float) -> Unit
  ): Result<String> = uploadImage(bitmap, userId, type, null, onProgress)

  /**
   * Uploads an image or video from an existing local Uri.
   */
  suspend fun uploadMediaFromUri(
    uri: Uri,
    userId: String,
    type: MediaType = MediaType.POST_MEDIA,
    targetId: String? = null,
    onProgress: (Float) -> Unit
  ): Result<ProcessedMedia> = withContext(Dispatchers.IO) {
    try {
      if (userId.isBlank()) {
        return@withContext Result.failure(
          IllegalStateException("User is not authenticated. Please log in to upload media.")
        )
      }

      onProgress(0.10f)
      val validation = validateMediaUri(uri)
      if (!validation.isValid) {
        return@withContext Result.failure(IllegalArgumentException(validation.errorMessage ?: "Validation failed"))
      }

      onProgress(0.25f)

      if (validation.category == MediaCategory.VIDEO) {
        val videoFileName = "video_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.mp4"
        val storagePath = generateStoragePath(userId, type, videoFileName, targetId)

        val inputStream = context.contentResolver.openInputStream(uri)
          ?: return@withContext Result.failure(IllegalArgumentException("Unable to read video stream from selected file."))

        val videoResult = storageAdapter.storeStream(storagePath, inputStream, validation.mimeType ?: "video/mp4") { p ->
          val mapped = 0.25f + (p * 0.55f)
          onProgress(mapped.coerceIn(0f, 1f))
        }
        val videoUrl = videoResult.getOrThrow()

        // Generate and upload video thumbnail
        onProgress(0.85f)
        var thumbUrl: String? = null
        try {
          val retriever = MediaMetadataRetriever()
          retriever.setDataSource(context, uri)
          val frameBitmap = retriever.getFrameAtTime(1000000) ?: retriever.frameAtTime
          retriever.release()

          if (frameBitmap != null) {
            val thumbBytes = compressAndResize(frameBitmap, type)
            val thumbFileName = "thumb_${videoFileName.removeSuffix(".mp4")}.jpg"
            val thumbStoragePath = generateStoragePath(userId, type, thumbFileName, targetId)
            val thumbResult = storageAdapter.store(thumbStoragePath, thumbBytes, "image/jpeg") { _ -> }
            thumbUrl = thumbResult.getOrNull()
          }
        } catch (_: Exception) {}

        onProgress(1.0f)
        Result.success(
          ProcessedMedia(
            url = videoUrl,
            category = MediaCategory.VIDEO,
            thumbnailUrl = thumbUrl ?: videoUrl,
            mimeType = validation.mimeType ?: "video/mp4"
          )
        )
      } else {
        val inputStream = context.contentResolver.openInputStream(uri)
          ?: return@withContext Result.failure(IllegalArgumentException("Unable to read image stream."))
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        if (bitmap == null) {
          return@withContext Result.failure(IllegalArgumentException("Could not decode image content. Please select a valid JPG, PNG, or WEBP image."))
        }

        onProgress(0.35f)
        val uploadResult = uploadImage(bitmap, userId, type, targetId, onProgress = { p ->
          val mapped = 0.35f + (p * 0.65f)
          onProgress(mapped.coerceIn(0f, 1f))
        })
        val imageUrl = uploadResult.getOrThrow()

        Result.success(
          ProcessedMedia(
            url = imageUrl,
            category = MediaCategory.IMAGE,
            thumbnailUrl = imageUrl,
            mimeType = validation.mimeType ?: "image/jpeg"
          )
        )
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun uploadMediaFromUri(
    uri: Uri,
    userId: String,
    type: MediaType = MediaType.POST_MEDIA,
    onProgress: (Float) -> Unit
  ): Result<ProcessedMedia> = uploadMediaFromUri(uri, userId, type, null, onProgress)

  suspend fun uploadImageFromUri(
    uri: Uri,
    userId: String,
    type: MediaType,
    targetId: String? = null,
    onProgress: (Float) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    val res = uploadMediaFromUri(uri, userId, type, targetId, onProgress)
    res.map { it.url }
  }

  suspend fun uploadImageFromUri(
    uri: Uri,
    userId: String,
    type: MediaType,
    onProgress: (Float) -> Unit
  ): Result<String> = uploadImageFromUri(uri, userId, type, null, onProgress)

  /**
   * Deletes an image or video from storage.
   */
  suspend fun deleteImage(imageUrl: String): Boolean = withContext(Dispatchers.IO) {
    storageAdapter.delete(imageUrl)
  }

  fun getImageUrl(mediaId: String): String = storageAdapter.getUrl(mediaId)
}
