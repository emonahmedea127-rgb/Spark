package com.example.sociva.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.BuildConfig
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
  suspend fun store(fileName: String, bytes: ByteArray, mimeType: String): Result<String>
  suspend fun storeStream(fileName: String, inputStream: InputStream, mimeType: String): Result<String>
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

  override suspend fun store(fileName: String, bytes: ByteArray, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
    try {
      val targetFile = File(mediaDir, fileName)
      FileOutputStream(targetFile).use { fos ->
        fos.write(bytes)
        fos.flush()
      }
      val localUri = targetFile.toURI().toString()
      Result.success(localUri)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun storeStream(fileName: String, inputStream: InputStream, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
    try {
      val targetFile = File(mediaDir, fileName)
      FileOutputStream(targetFile).use { fos ->
        inputStream.copyTo(fos)
        fos.flush()
      }
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

class MediaService(
  private val context: Context,
  private val storageAdapter: MediaStorageAdapter = SocivaStorageAdapter(context)
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
   * Uploads an edited/cropped bitmap with progress tracking.
   */
  suspend fun uploadImage(
    bitmap: Bitmap,
    userId: String,
    type: MediaType,
    onProgress: (Float) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      onProgress(0.20f)
      delay(50)

      val compressedBytes = compressAndResize(bitmap, type)
      onProgress(0.50f)
      delay(50)

      if (compressedBytes.size > MAX_IMAGE_SIZE_BYTES) {
        return@withContext Result.failure(
          IllegalArgumentException("Optimized image exceeds 15MB limit.")
        )
      }

      onProgress(0.75f)
      val prefix = when (type) {
        MediaType.PROFILE_PICTURE -> "avatar"
        MediaType.COVER_PHOTO -> "cover"
        MediaType.POST_MEDIA -> "post"
        MediaType.STORY_MEDIA -> "story"
      }
      val fileName = "${prefix}_${userId}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"

      onProgress(0.90f)
      val storeResult = storageAdapter.store(fileName, compressedBytes, "image/jpeg")
      onProgress(1.0f)
      storeResult
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Uploads an image or video from an existing local Uri.
   */
  suspend fun uploadMediaFromUri(
    uri: Uri,
    userId: String,
    type: MediaType = MediaType.POST_MEDIA,
    onProgress: (Float) -> Unit
  ): Result<ProcessedMedia> = withContext(Dispatchers.IO) {
    try {
      onProgress(0.10f)
      val validation = validateMediaUri(uri)
      if (!validation.isValid) {
        return@withContext Result.failure(IllegalArgumentException(validation.errorMessage ?: "Validation failed"))
      }

      onProgress(0.30f)

      if (validation.category == MediaCategory.VIDEO) {
        // Video processing & thumbnail generation
        val videoPrefix = "video_${type.name.lowercase(Locale.ROOT)}"
        val videoFileName = "${videoPrefix}_${userId}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.mp4"

        val inputStream = context.contentResolver.openInputStream(uri)
          ?: return@withContext Result.failure(IllegalArgumentException("Unable to read video stream"))

        onProgress(0.60f)
        val videoResult = storageAdapter.storeStream(videoFileName, inputStream, validation.mimeType ?: "video/mp4")
        val videoUrl = videoResult.getOrThrow()

        // Generate thumbnail
        onProgress(0.80f)
        var thumbUrl: String? = null
        try {
          val retriever = MediaMetadataRetriever()
          retriever.setDataSource(context, uri)
          val frameBitmap = retriever.getFrameAtTime(1000000) ?: retriever.frameAtTime
          retriever.release()

          if (frameBitmap != null) {
            val thumbBytes = compressAndResize(frameBitmap, type)
            val thumbFileName = "thumb_${videoFileName.removeSuffix(".mp4")}.jpg"
            val thumbResult = storageAdapter.store(thumbFileName, thumbBytes, "image/jpeg")
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
        // Image processing
        val inputStream = context.contentResolver.openInputStream(uri)
          ?: return@withContext Result.failure(IllegalArgumentException("Unable to read image stream"))
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        if (bitmap == null) {
          return@withContext Result.failure(IllegalArgumentException("Could not decode image content"))
        }

        onProgress(0.60f)
        val uploadResult = uploadImage(bitmap, userId, type, onProgress = { p ->
          onProgress(0.60f + (p * 0.40f))
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

  suspend fun uploadImageFromUri(
    uri: Uri,
    userId: String,
    type: MediaType,
    onProgress: (Float) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    val res = uploadMediaFromUri(uri, userId, type, onProgress)
    res.map { it.url }
  }

  /**
   * Deletes an image or video from storage.
   */
  suspend fun deleteImage(imageUrl: String): Boolean = withContext(Dispatchers.IO) {
    storageAdapter.delete(imageUrl)
  }

  fun getImageUrl(mediaId: String): String = storageAdapter.getUrl(mediaId)
}
