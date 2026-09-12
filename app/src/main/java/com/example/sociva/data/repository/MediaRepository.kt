package com.example.sociva.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.example.sociva.data.service.MediaType
import com.example.sociva.data.service.ProcessedMedia
import com.example.sociva.data.service.ValidationResult

/**
 * Reusable repository interface for media validation, uploading, and management
 * using Supabase Storage.
 */
interface MediaRepository {
  /**
   * Validates media URI for supported MIME types, file size limits, and readability.
   */
  fun validateMediaUri(uri: Uri): ValidationResult

  /**
   * Validates bitmap size and dimensions before upload.
   */
  fun validateBitmap(bitmap: Bitmap, type: MediaType): ValidationResult

  /**
   * Uploads user profile photo to Supabase Storage under avatars bucket.
   */
  suspend fun uploadProfilePhoto(
    userId: String,
    bitmap: Bitmap,
    onProgress: (Float) -> Unit
  ): Result<String>

  /**
   * Uploads user cover photo to Supabase Storage under covers bucket.
   */
  suspend fun uploadCoverPhoto(
    userId: String,
    bitmap: Bitmap,
    onProgress: (Float) -> Unit
  ): Result<String>

  /**
   * Uploads post media (image or video) to Supabase Storage under post-media bucket.
   */
  suspend fun uploadPostMedia(
    userId: String,
    uri: Uri,
    postId: String = "post_${System.currentTimeMillis()}",
    onProgress: (Float) -> Unit
  ): Result<ProcessedMedia>

  /**
   * Uploads story media (image or video) to Supabase Storage under post-media bucket.
   */
  suspend fun uploadStoryMedia(
    userId: String,
    uri: Uri,
    storyId: String = "story_${System.currentTimeMillis()}",
    onProgress: (Float) -> Unit
  ): Result<ProcessedMedia>

  /**
   * Uploads arbitrary media from Uri for general usage.
   */
  suspend fun uploadMediaFromUri(
    uri: Uri,
    userId: String,
    type: MediaType,
    targetId: String? = null,
    onProgress: (Float) -> Unit
  ): Result<ProcessedMedia>

  /**
   * Deletes a media file from Supabase Storage.
   */
  suspend fun deleteMedia(fileUrl: String): Boolean
}
