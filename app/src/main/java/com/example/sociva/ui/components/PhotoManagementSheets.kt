package com.example.sociva.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.sociva.data.service.UploadState
import com.example.ui.theme.SocivaIndigo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom Sheet menu for managing Profile Picture
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePictureActionSheet(
  onDismiss: () -> Unit,
  onChooseFromDevice: () -> Unit,
  onChoosePreset: () -> Unit,
  onViewPhoto: () -> Unit,
  onRemovePhoto: () -> Unit,
  hasExistingPhoto: Boolean
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Text(
        text = "Profile Picture",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 12.dp)
      )

      if (hasExistingPhoto) {
        PhotoActionRow(
          icon = Icons.Default.Visibility,
          title = "View profile picture",
          subtitle = "Open full-screen photo viewer",
          onClick = {
            onDismiss()
            onViewPhoto()
          }
        )
      }

      PhotoActionRow(
        icon = Icons.Default.AddPhotoAlternate,
        title = "Upload new profile picture",
        subtitle = "Choose an image from your device gallery",
        onClick = {
          onDismiss()
          onChooseFromDevice()
        }
      )

      PhotoActionRow(
        icon = Icons.Default.Collections,
        title = "Choose from avatar gallery",
        subtitle = "Pick from curated high-resolution styles",
        onClick = {
          onDismiss()
          onChoosePreset()
        }
      )

      if (hasExistingPhoto) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        PhotoActionRow(
          icon = Icons.Default.DeleteOutline,
          title = "Remove profile picture",
          subtitle = "Reset to Spark default avatar",
          isDestructive = true,
          onClick = {
            onDismiss()
            onRemovePhoto()
          }
        )
      }
    }
  }
}

/**
 * Bottom Sheet menu for managing Cover Photo
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverPhotoActionSheet(
  onDismiss: () -> Unit,
  onChooseFromDevice: () -> Unit,
  onChoosePreset: () -> Unit,
  onViewPhoto: () -> Unit,
  onRemovePhoto: () -> Unit,
  hasExistingPhoto: Boolean
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Text(
        text = "Cover Photo",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 12.dp)
      )

      if (hasExistingPhoto) {
        PhotoActionRow(
          icon = Icons.Default.Visibility,
          title = "View cover photo",
          subtitle = "Open full-screen cover banner",
          onClick = {
            onDismiss()
            onViewPhoto()
          }
        )
      }

      PhotoActionRow(
        icon = Icons.Default.AddPhotoAlternate,
        title = "Upload new cover photo",
        subtitle = "Choose a wide banner from device",
        onClick = {
          onDismiss()
          onChooseFromDevice()
        }
      )

      PhotoActionRow(
        icon = Icons.Default.Landscape,
        title = "Choose scenic cover preset",
        subtitle = "Select from scenic & abstract headers",
        onClick = {
          onDismiss()
          onChoosePreset()
        }
      )

      if (hasExistingPhoto) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        PhotoActionRow(
          icon = Icons.Default.DeleteOutline,
          title = "Remove cover photo",
          subtitle = "Reset to Spark default gradient cover",
          isDestructive = true,
          onClick = {
            onDismiss()
            onRemovePhoto()
          }
        )
      }
    }
  }
}

@Composable
private fun PhotoActionRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  isDestructive: Boolean = false,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    color = Color.Transparent,
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 10.dp, horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(
            if (isDestructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp)
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
          color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

/**
 * Confirmation dialog for removing photo
 */
@Composable
fun RemovePhotoConfirmDialog(
  title: String,
  message: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, fontWeight = FontWeight.Bold) },
    text = { Text(message) },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
      ) {
        Text("Remove", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

/**
 * Dialog displaying live upload progress, validation, compression, and error retry
 */
@Composable
fun UploadProgressDialog(
  state: UploadState,
  onRetry: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = {
      if (state is UploadState.Error) onDismiss()
    },
    confirmButton = {
      if (state is UploadState.Error) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = onDismiss) {
            Text("Cancel")
          }
          Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = SocivaIndigo)
          ) {
            Text("Retry")
          }
        }
      }
    },
    title = {
      Text(
        text = when (state) {
          is UploadState.Validating -> "Validating..."
          is UploadState.Compressing -> "Optimizing Image..."
          is UploadState.Uploading -> "Uploading Photo..."
          is UploadState.Success -> "Upload Complete!"
          is UploadState.Error -> "Upload Failed"
          UploadState.Idle -> "Processing..."
        },
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        when (state) {
          is UploadState.Validating -> {
            CircularProgressIndicator(modifier = Modifier.size(40.dp), color = SocivaIndigo)
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
          }
          is UploadState.Compressing -> {
            CircularProgressIndicator(modifier = Modifier.size(40.dp), color = SocivaIndigo)
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
          }
          is UploadState.Uploading -> {
            LinearProgressIndicator(
              progress = { state.progress },
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
              color = SocivaIndigo
            )
            Text(
              text = "${(state.progress * 100).toInt()}% uploaded",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold
            )
          }
          is UploadState.Success -> {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = "Success",
              tint = Color(0xFF10B981),
              modifier = Modifier.size(48.dp)
            )
            Text("Your photo has been updated successfully!", style = MaterialTheme.typography.bodyMedium)
          }
          is UploadState.Error -> {
            Icon(
              imageVector = Icons.Default.ErrorOutline,
              contentDescription = "Error",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(48.dp)
            )
            Text(
              text = state.message,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium
            )
          }
          UploadState.Idle -> {}
        }
      }
    }
  )
}

/**
 * Curated Preset Photo Picker for fast testing in emulator & preview
 */
@Composable
fun PresetPhotoPickerDialog(
  isCover: Boolean,
  onPhotoSelected: (Bitmap) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var isLoadingBitmap by remember { mutableStateOf<String?>(null) }

  val presetUrls = if (!isCover) {
    listOf(
      "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=800&h=800&fit=crop",
      "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=800&h=800&fit=crop",
      "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=800&h=800&fit=crop",
      "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=800&h=800&fit=crop",
      "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=800&h=800&fit=crop",
      "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=800&h=800&fit=crop"
    )
  } else {
    listOf(
      "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=1600&h=700&fit=crop",
      "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1600&h=700&fit=crop",
      "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=1600&h=700&fit=crop",
      "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=1600&h=700&fit=crop",
      "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=1600&h=700&fit=crop",
      "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1600&h=700&fit=crop"
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (isCover) "Choose Cover Preset" else "Choose Avatar Preset",
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .height(340.dp)
      ) {
        Text(
          text = "Select an image to open in the cropper:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyVerticalGrid(
          columns = GridCells.Fixed(if (isCover) 2 else 3),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxSize()
        ) {
          items(presetUrls) { url ->
            Box(
              modifier = Modifier
                .aspectRatio(if (isCover) 16f / 9f else 1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                  isLoadingBitmap = url
                  coroutineScope.launch {
                    val loader = context.imageLoader
                    val request = ImageRequest.Builder(context)
                      .data(url)
                      .allowHardware(false)
                      .build()
                    val result = withContext(Dispatchers.IO) { loader.execute(request) }
                    if (result is SuccessResult) {
                      val drawable = result.drawable
                      val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                      if (bitmap != null) {
                        onPhotoSelected(bitmap)
                      }
                    }
                    isLoadingBitmap = null
                  }
                }
            ) {
              AsyncImage(
                model = ImageRequest.Builder(context)
                  .data(url)
                  .crossfade(true)
                  .build(),
                contentDescription = "Preset image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )

              if (isLoadingBitmap == url) {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000)),
                  contentAlignment = Alignment.Center
                ) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
