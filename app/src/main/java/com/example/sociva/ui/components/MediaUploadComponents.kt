package com.example.sociva.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaIndigo

@Composable
fun SelectedMediaThumbnailItem(
  uri: Uri,
  isVideo: Boolean = false,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .size(105.dp)
      .clip(RoundedCornerShape(12.dp))
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
      .testTag("selected_media_thumb")
  ) {
    AsyncImage(
      model = ImageRequest.Builder(LocalContext.current)
        .data(uri)
        .crossfade(true)
        .build(),
      contentDescription = "Selected media",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize()
    )

    if (isVideo) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Video",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }

    // Remove Button
    IconButton(
      onClick = onRemove,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(4.dp)
        .size(24.dp)
        .clip(CircleShape)
        .background(Color.Black.copy(alpha = 0.65f))
        .testTag("remove_media_button")
    ) {
      Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "Remove",
        tint = Color.White,
        modifier = Modifier.size(14.dp)
      )
    }
  }
}

@Composable
fun MediaPickerActionBar(
  onPickMedia: () -> Unit,
  onPickCamera: () -> Unit,
  onFeelingClick: () -> Unit,
  onTagPeopleClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Add to your post",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      )

      Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Native Photo/Video Gallery Button
        IconButton(
          onClick = onPickMedia,
          modifier = Modifier.testTag("composer_pick_media_btn")
        ) {
          Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = "Photo/Video Gallery",
            tint = Color(0xFF10B981),
            modifier = Modifier.size(24.dp)
          )
        }

        // Native Camera Capture Button
        IconButton(
          onClick = onPickCamera,
          modifier = Modifier.testTag("composer_pick_camera_btn")
        ) {
          Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = "Take Photo",
            tint = SocivaBlue,
            modifier = Modifier.size(24.dp)
          )
        }

        // Tag People Button
        if (onTagPeopleClick != null) {
          IconButton(
            onClick = onTagPeopleClick,
            modifier = Modifier.testTag("composer_pick_tag_btn")
          ) {
            Icon(
              imageVector = Icons.Default.PersonAdd,
              contentDescription = "Tag People",
              tint = Color(0xFF8B5CF6),
              modifier = Modifier.size(24.dp)
            )
          }
        }

        // Feeling / Activity Button
        IconButton(
          onClick = onFeelingClick,
          modifier = Modifier.testTag("composer_pick_feeling_btn")
        ) {
          Icon(
            imageVector = Icons.Default.SentimentSatisfiedAlt,
            contentDescription = "Feeling or Activity",
            tint = Color(0xFFF59E0B),
            modifier = Modifier.size(24.dp)
          )
        }
      }
    }
  }
}
