package com.example.sociva.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.sociva.data.service.MediaCategory
import com.example.sociva.data.service.MediaType
import com.example.sociva.data.service.ProcessedMedia
import com.example.sociva.data.service.ValidationResult
import com.example.sociva.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

/**
 * Supabase Storage implementation of [MediaRepository].
 *
 * Stores media in user-scoped folders across Supabase Storage buckets:
 * - avatars/{userId}/avatar_{timestamp}.jpg
 * - covers/{userId}/cover_{timestamp}.jpg
 * - post-media/{userId}/{postId}/{fileName}
 * - reels/{userId}/{storyId}/{fileName}
 */
class SupabaseMediaRepository(
  private val context: Context
) : MediaRepository {

  companion object {
    private const val TAG = "SupabaseMediaRepo"

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

  private val supabase
    get() = SupabaseClientProvider.client

  private fun isNetworkAvailable(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNet = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(activeNet) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  private fun getEffectiveUserId(providedUserId: String): String {
    val authUid = try {
      supabase?.auth?.currentUserOrNull()?.id
    } catch (e: Exception) {
      null
    }
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

      val effectiveUid = getEffectiveUserId(userId)
      val fileName = "avatar_${System.currentTimeMillis()}.jpg"
      val path = "$effectiveUid/$fileName"

      val client = supabase
      if (client != null && SupabaseClientProvider.isConfigured()) {
        try {
          val bucket = client.storage.from("avatars")
          onProgress(0.50f)
          bucket.upload(path, compressedBytes) {
            upsert = true
          }
          onProgress(0.85f)
          val publicUrl = bucket.publicUrl(path)
          onProgress(1.0f)
          Log.i(TAG, "Uploaded avatar to Supabase: $publicUrl")
          return@withContext Result.success(publicUrl)
        } catch (e: Exception) {
          Log.w(TAG, "Supabase storage upload failed, saving locally: ${e.message}")
        }
      }

      // Offline / Local file fallback
      val localFile = File(context.filesDir, "avatars/$path").apply {
        parentFile?.mkdirs()
        writeBytes(compressedBytes)
      }
      val localUri = Uri.fromFile(localFile).toString()
      onProgress(1.0f)
      Result.success(localUri)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to upload profile photo: ${e.message}", e)
      Result.failure(e)
    }
  }

  override suspend fun uploadCoverPhoto(
    userId: String,
    bitmap: Bitmap,
    onProgress: (Float) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
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

      val effectiveUid = getEffectiveUserId(userId)
      val fileName = "cover_${System.currentTimeMillis()}.jpg"
      val path = "$effectiveUid/$fileName"

      val client = supabase
      if (client != null && SupabaseClientProvider.isConfigured()) {
        try {
          val bucket = client.storage.from("covers")
          onProgress(0.50f)
          bucket.upload(path, compressedBytes) {
            upsert = true
          }
          onProgress(0.85f)
          val publicUrl = bucket.publicUrl(path)
          onProgress(1.0f)
          Log.i(TAG, "Uploaded cover photo to Supabase: $publicUrl")
          return@withContext Result.success(publicUrl)
        } catch (e: Exception) {
          Log.w(TAG, "Supabase cover photo upload failed, saving locally: ${e.message}")
        }
      }

      val localFile = File(context.filesDir, "covers/$path").apply {
        parentFile?.mkdirs()
        writeBytes(compressedBytes)
      }
      val localUri = Uri.fromFile(localFile).toString()
      onProgress(1.0f)
      Result.success(localUri)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to upload cover photo: ${e.message}", e)
      Result.failure(e)
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
      val validation = validateMediaUri(uri)
      if (!validation.isValid) {
        return@withContext Result.failure(IllegalArgumentException(validation.errorMessage ?: "Invalid media"))
      }

      val isVideo = validation.category == MediaCategory.VIDEO
      val effectiveUid = getEffectiveUserId(userId)
      val containerId = targetId ?: UUID.randomUUID().toString()
      val ext = if (isVideo) "mp4" else "jpg"
      val fileName = "media_${System.currentTimeMillis()}.$ext"
      val bucketName = when (type) {
        MediaType.POST_MEDIA -> "post-media"
        MediaType.STORY_MEDIA -> "reels"
        MediaType.PROFILE_PICTURE -> "avatars"
        MediaType.COVER_PHOTO -> "covers"
      }
      val path = "$effectiveUid/$containerId/$fileName"

      onProgress(0.2f)

      val bytes = if (isVideo) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: return@withContext Result.failure(IllegalStateException("Could not read video file."))
      } else {
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
          BitmapFactory.decodeStream(it)
        } ?: return@withContext Result.failure(IllegalStateException("Could not decode image."))
        compressAndResize(bitmap, type)
      }

      onProgress(0.45f)

      val client = supabase
      if (client != null && SupabaseClientProvider.isConfigured()) {
        try {
          val bucket = client.storage.from(bucketName)
          onProgress(0.65f)
          bucket.upload(path, bytes) {
            upsert = true
          }
          onProgress(0.90f)
          val publicUrl = bucket.publicUrl(path)
          onProgress(1.0f)
          return@withContext Result.success(
            ProcessedMedia(
              url = publicUrl,
              mimeType = validation.mimeType ?: if (isVideo) "video/mp4" else "image/jpeg",
              category = validation.category ?: MediaCategory.IMAGE
            )
          )
        } catch (e: Exception) {
          Log.w(TAG, "Supabase storage upload failed, saving locally: ${e.message}")
        }
      }

      // Local storage fallback
      val localFile = File(context.filesDir, "$bucketName/$path").apply {
        parentFile?.mkdirs()
        writeBytes(bytes)
      }
      val localUri = Uri.fromFile(localFile).toString()
      onProgress(1.0f)

      Result.success(
        ProcessedMedia(
          url = localUri,
          mimeType = validation.mimeType ?: if (isVideo) "video/mp4" else "image/jpeg",
          category = validation.category ?: MediaCategory.IMAGE
        )
      )
    } catch (e: Exception) {
      Log.e(TAG, "Failed to upload media: ${e.message}", e)
      Result.failure(e)
    }
  }

  override suspend fun deleteMedia(fileUrl: String): Boolean = withContext(Dispatchers.IO) {
    try {
      val client = supabase ?: return@withContext false
      if (!SupabaseClientProvider.isConfigured()) return@withContext true

      // Try to determine bucket and path from URL
      for (bucketName in listOf("avatars", "covers", "post-media", "reels")) {
        if (fileUrl.contains(bucketName)) {
          val path = fileUrl.substringAfter("$bucketName/").substringBefore("?")
          if (path.isNotBlank()) {
            client.storage.from(bucketName).delete(listOf(path))
            return@withContext true
          }
        }
      }
      true
    } catch (e: Exception) {
      Log.w(TAG, "Failed to delete media from Supabase Storage: ${e.message}")
      false
    }
  }
}
