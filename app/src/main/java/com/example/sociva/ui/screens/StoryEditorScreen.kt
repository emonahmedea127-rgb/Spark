package com.example.sociva.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sociva.data.service.UploadState
import com.example.sociva.ui.SocivaScreen
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.ui.theme.*

@Composable
fun StoryEditorScreen(
  viewModel: SocivaViewModel,
  onClose: () -> Unit
) {
  val pendingMedia by viewModel.pendingStoryMedia.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val uploadState by viewModel.uploadState.collectAsState()

  var storyText by remember { mutableStateOf("") }
  var isTextEditing by remember { mutableStateOf(false) }
  var selectedGradientIndex by remember { mutableStateOf(0) }
  var isVideoPlaying by remember { mutableStateOf(true) }
  var selectedSticker by remember { mutableStateOf<String?>(null) }
  var showStickerPicker by remember { mutableStateOf(false) }

  val context = LocalContext.current

  // Change / Re-select media launcher
  val mediaPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      val mimeType = context.contentResolver.getType(uri) ?: ""
      val isVideo = mimeType.startsWith("video")
      viewModel.selectStoryMedia(uri, isVideo, mimeType)
    }
  }

  val gradients = listOf(
    listOf(Color(0xFF6366F1), Color(0xFFA855F7)),
    listOf(Color(0xFFEC4899), Color(0xFFF43F5E)),
    listOf(Color(0xFF3B82F6), Color(0xFF06B6D4)),
    listOf(Color(0xFF10B981), Color(0xFF059669)),
    listOf(Color(0xFFF59E0B), Color(0xFFEF4444)),
    listOf(Color(0xFF1E1E2E), Color(0xFF2D2B55))
  )

  val currentGradient = gradients.getOrElse(selectedGradientIndex) { gradients[0] }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
      .testTag("story_editor_screen")
  ) {
    // 1. Media Preview (Image or Video) or Gradient Canvas
    if (pendingMedia != null) {
      Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
          model = ImageRequest.Builder(LocalContext.current)
            .data(pendingMedia?.uri)
            .crossfade(true)
            .build(),
          contentDescription = "Story preview media",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )

        if (pendingMedia?.isVideo == true) {
          // Video indicator badge
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
              .align(Alignment.BottomStart)
              .padding(start = 16.dp, bottom = 90.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.Black.copy(alpha = 0.6f))
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Icon(
              imageVector = if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = "Video status",
              tint = Color.White,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = if (isVideoPlaying) "Video Story Preview" else "Paused",
              color = Color.White,
              style = MaterialTheme.typography.labelSmall
            )
          }
        }
      }
    } else {
      // Fallback gradient canvas if no media selected
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Brush.verticalGradient(currentGradient))
      )
    }

    // Vignette shadows top and bottom for readability
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(130.dp)
        .align(Alignment.TopCenter)
        .background(
          Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
          )
        )
    )

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
        .align(Alignment.BottomCenter)
        .background(
          Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
          )
        )
    )

    // 2. Interactive Text & Sticker Overlay in Center
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 100.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        if (selectedSticker != null) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(20.dp))
              .background(Color.Black.copy(alpha = 0.5f))
              .padding(horizontal = 16.dp, vertical = 8.dp)
              .clickable { showStickerPicker = true }
          ) {
            Text(text = selectedSticker!!, fontSize = 42.sp)
          }
        }

        // Editable Text Overlay
        if (isTextEditing) {
          BasicTextField(
            value = storyText,
            onValueChange = { storyText = it },
            textStyle = TextStyle(
              color = Color.White,
              fontSize = 24.sp,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
              lineHeight = 32.sp
            ),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(Color.Black.copy(alpha = 0.6f))
              .padding(16.dp)
              .testTag("story_text_overlay_input")
          )
        } else if (storyText.isNotBlank()) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .background(Color.Black.copy(alpha = 0.55f))
              .clickable { isTextEditing = true }
              .padding(horizontal = 20.dp, vertical = 12.dp)
          ) {
            Text(
              text = storyText,
              color = Color.White,
              fontSize = 22.sp,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }

    // 3. Top Controls Bar: Back, User Info, Tools (Text, Stickers, Change Media)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = {
            viewModel.clearStoryMedia()
            onClose()
          },
          modifier = Modifier.testTag("story_editor_close_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Cancel Story",
            tint = Color.White
          )
        }

        Spacer(modifier = Modifier.width(4.dp))

        UserAvatar(
          avatarUrl = currentUser?.avatarUrl,
          name = currentUser?.fullName ?: "User",
          size = 36.dp
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
          text = "Your Story",
          color = Color.White,
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.titleMedium
        )
      }

      // Quick Tools: Change Photo, Add Text, Add Sticker
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Change photo/video button
        IconButton(
          onClick = {
            mediaPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
          },
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .testTag("story_change_media_btn")
        ) {
          Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = "Change Media",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }

        // Add / Edit Text
        IconButton(
          onClick = { isTextEditing = !isTextEditing },
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (isTextEditing) SocivaBlue else Color.Black.copy(alpha = 0.45f))
            .testTag("story_add_text_btn")
        ) {
          Icon(
            imageVector = Icons.Default.TextFields,
            contentDescription = "Text Overlay",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }

        // Add Sticker / Emoji
        IconButton(
          onClick = { showStickerPicker = true },
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .testTag("story_add_sticker_btn")
        ) {
          Icon(
            imageVector = Icons.Default.SentimentSatisfiedAlt,
            contentDescription = "Add Sticker",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }

    // 4. Upload Progress Overlay if uploading
    if (uploadState is UploadState.Uploading || uploadState is UploadState.Validating) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.padding(32.dp)
        ) {
          CircularProgressIndicator(color = SocivaBlue)
          val statusText = when (val st = uploadState) {
            is UploadState.Uploading -> "Sharing to Your Story... ${(st.progress * 100).toInt()}%"
            is UploadState.Validating -> st.message
            else -> "Processing story..."
          }
          Text(
            text = statusText,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    // 5. Bottom Share Bar
    Row(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Discard Button
      OutlinedButton(
        onClick = {
          viewModel.clearStoryMedia()
          onClose()
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        border = ButtonDefaults.outlinedButtonBorder(true),
        modifier = Modifier.testTag("story_discard_btn")
      ) {
        Text("Discard")
      }

      // Share to Story Primary Button
      Button(
        onClick = {
          val media = pendingMedia
          if (media != null) {
            val combinedText = if (selectedSticker != null && storyText.isNotBlank()) {
              "${selectedSticker} ${storyText}"
            } else if (selectedSticker != null) {
              selectedSticker ?: ""
            } else {
              storyText
            }

            viewModel.uploadAndCreateStory(
              uri = media.uri,
              text = combinedText,
              gradientIndex = selectedGradientIndex,
              isVideo = media.isVideo
            )
          } else {
            // Text only story
            viewModel.createStory(
              text = storyText.ifBlank { "Hello Spark! ✨" },
              mediaUrl = null,
              gradientIndex = selectedGradientIndex
            )
          }
        },
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
        modifier = Modifier.testTag("story_share_submit_btn")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Send,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Share to Story",
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }
    }
  }

  // Sticker Picker Modal
  if (showStickerPicker) {
    val stickers = listOf(
      "🔥", "✨", "❤️", "🎉", "🌴", "🚀", "☕", "💯",
      "🙌", "😍", "🥳", "⚡", "🌟", "🌸", "🍕", "🐶"
    )
    AlertDialog(
      onDismissRequest = { showStickerPicker = false },
      title = { Text("Choose a Sticker", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          val rows = stickers.chunked(4)
          rows.forEach { rowStickers ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceAround
            ) {
              rowStickers.forEach { s ->
                Box(
                  modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                      selectedSticker = s
                      showStickerPicker = false
                    },
                  contentAlignment = Alignment.Center
                ) {
                  Text(text = s, fontSize = 24.sp)
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = {
          selectedSticker = null
          showStickerPicker = false
        }) {
          Text("Remove Sticker")
        }
      },
      dismissButton = {
        TextButton(onClick = { showStickerPicker = false }) {
          Text("Close")
        }
      }
    )
  }
}
