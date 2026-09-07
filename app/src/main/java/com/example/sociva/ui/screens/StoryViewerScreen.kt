package com.example.sociva.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sociva.data.model.Story
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.sociva.ui.components.VerifiedBadge
import com.example.sociva.ui.components.formatRelativeTime

@Composable
fun StoryViewerScreen(
  viewModel: SocivaViewModel,
  onClose: () -> Unit
) {
  val stories by viewModel.activeStories.collectAsState()
  val currentIndex by viewModel.activeStoryIndex.collectAsState()

  if (stories.isEmpty() || currentIndex !in stories.indices) {
    LaunchedEffect(Unit) { onClose() }
    return
  }

  val story = stories[currentIndex]
  var replyText by remember { mutableStateOf("") }
  val progress = remember(currentIndex) { Animatable(0f) }
  var isPaused by remember { mutableStateOf(false) }

  // Auto-advance timer (5 seconds per story)
  LaunchedEffect(currentIndex, isPaused) {
    if (!isPaused) {
      progress.animateTo(
        targetValue = 1f,
        animationSpec = tween(
          durationMillis = ((1f - progress.value) * 5000).toInt(),
          easing = LinearEasing
        )
      )
      viewModel.nextStory()
    }
  }

  val gradients = listOf(
    listOf(Color(0xFF2563EB), Color(0xFF7C3AED)),
    listOf(Color(0xFFEC4899), Color(0xFFF43F5E)),
    listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)),
    listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)),
    listOf(Color(0xFF10B981), Color(0xFF06B6D4))
  )
  val currentGradient = gradients[story.backgroundGradientIndex % gradients.size]

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
      .testTag("story_viewer_screen")
  ) {
    // 1. Story Visual Background / Image / Text
    if (!story.mediaUrl.isNullOrBlank()) {
      AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
          .data(story.mediaUrl)
          .crossfade(true)
          .build(),
        contentDescription = "Story Media",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
      if (story.textOverlay.isNotBlank()) {
        Box(
          modifier = Modifier
            .align(Alignment.Center)
            .padding(24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(16.dp)
        ) {
          Text(
            text = story.textOverlay,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
          )
        }
      }
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Brush.verticalGradient(currentGradient))
          .padding(32.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = story.textOverlay,
          color = Color.White,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          lineHeight = 36.sp
        )
      }
    }

    // Top Dark Vignette
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(140.dp)
        .background(
          Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
          )
        )
    )

    // Touch detector for navigation (tap left: prev, tap right: next, hold: pause)
    Row(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(currentIndex) {
          detectTapGestures(
            onPress = {
              isPaused = true
              tryAwaitRelease()
              isPaused = false
            },
            onTap = { offset ->
              if (offset.x < size.width * 0.35f) {
                viewModel.prevStory()
              } else {
                viewModel.nextStory()
              }
            }
          )
        }
    ) {
      Spacer(modifier = Modifier.weight(1f))
      Spacer(modifier = Modifier.weight(1f))
    }

    // 2. Top Progress Indicators & Header
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 14.dp)
        .statusBarsPadding()
    ) {
      // Progress Bars
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        stories.forEachIndexed { index, _ ->
          val segmentProgress = when {
            index < currentIndex -> 1f
            index == currentIndex -> progress.value
            else -> 0f
          }
          LinearProgressIndicator(
            progress = { segmentProgress },
            modifier = Modifier
              .weight(1f)
              .height(3.dp)
              .clip(RoundedCornerShape(2.dp)),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // User Header Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        UserAvatar(
          avatarUrl = story.userAvatar,
          name = story.userName,
          size = 40.dp
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = story.userName,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.titleSmall
            )
            if (story.isUserVerified) {
              Spacer(modifier = Modifier.width(4.dp))
              VerifiedBadge(size = 14.dp)
            }
          }
          Text(
            text = formatRelativeTime(story.timestamp),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall
          )
        }

        // View count badge
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Icon(
            imageVector = Icons.Default.RemoveRedEye,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "${story.viewsCount}",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
          )
        }

        IconButton(onClick = onClose) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Story",
            tint = Color.White
          )
        }
      }
    }

    // 3. Bottom Controls: Reactions & Reply Input
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .background(
          Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
          )
        )
        .padding(14.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Quick Reaction Emojis
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
      ) {
        listOf("🔥", "❤️", "😂", "👏", "😮", "🚀").forEach { emoji ->
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.2f))
              .clickable {
                viewModel.showToast("Sent $emoji reaction to ${story.userName}!")
              },
            contentAlignment = Alignment.Center
          ) {
            Text(text = emoji, fontSize = 22.sp)
          }
        }
      }

      // Reply message input
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = replyText,
          onValueChange = { replyText = it },
          placeholder = {
            Text(
              text = "Reply to ${story.userName}...",
              color = Color.White.copy(alpha = 0.7f),
              style = MaterialTheme.typography.bodyMedium
            )
          },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color.White.copy(alpha = 0.5f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
            focusedContainerColor = Color.Black.copy(alpha = 0.4f),
            unfocusedContainerColor = Color.Black.copy(alpha = 0.3f)
          ),
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier.weight(1f)
        )

        IconButton(
          onClick = {
            if (replyText.isNotBlank()) {
              viewModel.showToast("Reply sent to ${story.userName}!")
              replyText = ""
            }
          },
          modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send",
            tint = Color.White
          )
        }
      }
    }
  }
}
