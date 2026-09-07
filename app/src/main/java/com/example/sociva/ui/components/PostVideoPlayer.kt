package com.example.sociva.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sociva.data.model.Post
import com.example.sociva.data.model.VideoWatchEvent
import kotlinx.coroutines.delay
import java.util.UUID

@Composable
fun PostVideoPlayer(
  post: Post,
  videoUrl: String,
  onWatchEvent: (VideoWatchEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  var isPlaying by remember { mutableStateOf(false) }
  var isMuted by remember { mutableStateOf(false) }
  var currentPositionMs by remember { mutableLongStateOf(0L) }
  val totalDurationMs = remember { 75_000L } // 1m 15s standard video length
  var totalWatchedThisSession by remember { mutableLongStateOf(0L) }
  var hasCompletedOnce by remember { mutableStateOf(false) }
  val sessionId = remember { UUID.randomUUID().toString().take(8) }
  val startedAt = remember { System.currentTimeMillis() }
  var showControls by remember { mutableStateOf(true) }

  // Playback timer & tracking loop
  LaunchedEffect(isPlaying) {
    if (isPlaying) {
      while (isPlaying) {
        delay(500)
        currentPositionMs += 500
        totalWatchedThisSession += 500

        if (currentPositionMs >= totalDurationMs) {
          // Reached end of video
          currentPositionMs = totalDurationMs
          val completed = true
          hasCompletedOnce = true
          isPlaying = false

          onWatchEvent(
            VideoWatchEvent(
              postId = post.id,
              videoId = videoUrl,
              viewerId = "", // Filled by repository / current user
              sessionId = sessionId,
              startedAt = startedAt,
              lastPosition = currentPositionMs,
              watchedDuration = totalWatchedThisSession,
              videoDuration = totalDurationMs,
              completed = completed,
              isReplay = hasCompletedOnce,
              source = "Home Feed",
              watchedAt = System.currentTimeMillis()
            )
          )
          break
        }
      }
    }
  }

  // Record watch event when composable leaves composition (e.g. user scrolled past the video)
  DisposableEffect(sessionId) {
    onDispose {
      if (totalWatchedThisSession >= 1000L) {
        onWatchEvent(
          VideoWatchEvent(
            postId = post.id,
            videoId = videoUrl,
            viewerId = "",
            sessionId = sessionId,
            startedAt = startedAt,
            lastPosition = currentPositionMs,
            watchedDuration = totalWatchedThisSession,
            videoDuration = totalDurationMs,
            completed = currentPositionMs >= (totalDurationMs * 0.95),
            isReplay = hasCompletedOnce,
            source = "Home Feed",
            watchedAt = System.currentTimeMillis()
          )
        )
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(16f / 9f)
      .background(Color.Black)
      .clip(RoundedCornerShape(0.dp))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) {
        showControls = !showControls
      }
      .testTag("post_video_player_${post.id}")
  ) {
    // Video Thumbnail / Poster
    AsyncImage(
      model = ImageRequest.Builder(LocalContext.current)
        .data(videoUrl)
        .crossfade(true)
        .build(),
      contentDescription = "Video preview",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize()
    )

    // Dark Gradient Overlays for controls visibility
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .align(Alignment.TopCenter)
        .background(
          Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
          )
        )
    )

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(72.dp)
        .align(Alignment.BottomCenter)
        .background(
          Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
          )
        )
    )

    // Top Bar: Video Badge & Mute Toggle
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .align(Alignment.TopCenter),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.65f)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            imageVector = Icons.Outlined.Videocam,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = "VIDEO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 0.8.sp
          )
        }
      }

      IconButton(
        onClick = { isMuted = !isMuted },
        modifier = Modifier
          .size(32.dp)
          .background(Color.Black.copy(alpha = 0.5f), CircleShape)
          .testTag("post_video_mute_btn_${post.id}")
      ) {
        Icon(
          imageVector = if (isMuted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
          contentDescription = if (isMuted) "Unmute" else "Mute",
          tint = Color.White,
          modifier = Modifier.size(16.dp)
        )
      }
    }

    // Center Play/Pause Overlay Button
    AnimatedVisibility(
      visible = !isPlaying || showControls,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.Center)
    ) {
      IconButton(
        onClick = {
          if (currentPositionMs >= totalDurationMs) {
            currentPositionMs = 0L
          }
          isPlaying = !isPlaying
        },
        modifier = Modifier
          .size(56.dp)
          .background(Color.Black.copy(alpha = 0.65f), CircleShape)
          .testTag("post_video_play_pause_btn_${post.id}")
      ) {
        Icon(
          imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription = if (isPlaying) "Pause" else "Play",
          tint = Color.White,
          modifier = Modifier.size(32.dp)
        )
      }
    }

    // Bottom Controls Bar: Progress Slider & Time Elapsed / Total
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomCenter)
        .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
      // Progress Slider
      val progress = (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
      Slider(
        value = progress,
        onValueChange = { newProgress ->
          currentPositionMs = (newProgress * totalDurationMs).toLong()
        },
        colors = SliderDefaults.colors(
          thumbColor = Color.White,
          activeTrackColor = Color(0xFF1877F2),
          inactiveTrackColor = Color.White.copy(alpha = 0.35f)
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(18.dp)
          .testTag("post_video_slider_${post.id}")
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = formatDuration(currentPositionMs),
          style = MaterialTheme.typography.labelSmall,
          color = Color.White,
          fontWeight = FontWeight.Medium
        )
        Text(
          text = formatDuration(totalDurationMs),
          style = MaterialTheme.typography.labelSmall,
          color = Color.White.copy(alpha = 0.7f),
          fontWeight = FontWeight.Medium
        )
      }
    }
  }
}

private fun formatDuration(millis: Long): String {
  val totalSeconds = millis / 1000
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return "%d:%02d".format(minutes, seconds)
}
