package com.example.sociva.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sociva.data.model.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotoLightboxViewer(
  imageUrl: String?,
  user: User,
  isCover: Boolean = false,
  isOwner: Boolean = false,
  onDismiss: () -> Unit,
  onEditClick: (() -> Unit)? = null,
  onShareClick: () -> Unit,
  onDownloadClick: () -> Unit
) {
  var scale by remember { mutableStateOf(1f) }
  var offsetX by remember { mutableStateOf(0f) }
  var offsetY by remember { mutableStateOf(0f) }

  val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
    scale = (scale * zoomChange).coerceIn(0.8f, 5f)
    offsetX += panChange.x
    offsetY += panChange.y
  }

  val updatedTimestamp = if (isCover) user.coverPhotoUpdatedAt else user.profilePictureUpdatedAt
  val formattedDate = remember(updatedTimestamp) {
    if (updatedTimestamp > 0) {
      val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
      sdf.format(Date(updatedTimestamp))
    } else {
      user.joinedDate
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .testTag("photo_lightbox_viewer")
    ) {
      // 1. Center Interactive Image
      Box(
        modifier = Modifier
          .fillMaxSize()
          .transformable(state = transformableState)
          .pointerInput(Unit) {
            detectTapGestures(
              onDoubleTap = {
                if (scale > 1f) {
                  scale = 1f
                  offsetX = 0f
                  offsetY = 0f
                } else {
                  scale = 2.5f
                }
              }
            )
          },
        contentAlignment = Alignment.Center
      ) {
        if (!imageUrl.isNullOrBlank()) {
          AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
              .data(imageUrl)
              .crossfade(true)
              .build(),
            contentDescription = if (isCover) "Cover Photo" else "Profile Picture",
            contentScale = if (isCover) ContentScale.Fit else ContentScale.Fit,
            modifier = Modifier
              .fillMaxWidth()
              .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
              )
          )
        } else {
          // Default placeholder graphic
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            Box(
              modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(Color(0xFF334155)),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = user.fullName.firstOrNull()?.uppercase() ?: "U",
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold
              )
            }
            Text(
              text = if (isCover) "No Cover Photo" else "Default Avatar",
              color = Color.LightGray,
              fontSize = 16.sp
            )
          }
        }
      }

      // 2. Top Header Bar
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.TopCenter),
        color = Color(0xCC000000)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_lightbox_button")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = Color.White
            )
          }

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
          ) {
            Text(
              text = user.fullName,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp
            )
            Text(
              text = if (isCover) "Cover Photo • $formattedDate" else "Profile Picture • $formattedDate",
              color = Color(0xFF94A3B8),
              fontSize = 12.sp
            )
          }

          if (isOwner && onEditClick != null) {
            TextButton(onClick = {
              onDismiss()
              onEditClick()
            }) {
              Text("Edit", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
            }
          } else {
            Spacer(modifier = Modifier.size(48.dp))
          }
        }
      }

      // 3. Bottom Action Bar
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter),
        color = Color(0xCC000000)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceAround
        ) {
          IconButton(onClick = onShareClick) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
              Text("Share", color = Color.LightGray, fontSize = 11.sp)
            }
          }

          IconButton(onClick = onDownloadClick) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(Icons.Default.Download, contentDescription = "Save", tint = Color.White)
              Text("Save", color = Color.LightGray, fontSize = 11.sp)
            }
          }

          IconButton(
            onClick = {
              scale = 1f
              offsetX = 0f
              offsetY = 0f
            }
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset Zoom", tint = Color.White)
              Text("Reset", color = Color.LightGray, fontSize = 11.sp)
            }
          }
        }
      }
    }
  }
}
