package com.example.sociva.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sociva.data.model.Reel
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.sociva.ui.components.VerifiedBadge
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaPink

@Composable
fun ReelsScreen(
  viewModel: SocivaViewModel,
  modifier: Modifier = Modifier
) {
  val reels by viewModel.allReels.collectAsState()
  val listState = rememberLazyListState()
  val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
      .testTag("reels_screen")
  ) {
    LazyColumn(
      state = listState,
      flingBehavior = snapFlingBehavior,
      modifier = Modifier.fillMaxSize()
    ) {
      items(reels, key = { it.id }) { reel ->
        ReelItem(
          reel = reel,
          onLike = { viewModel.toggleReelLike(reel.id) },
          onComment = { viewModel.openComments(reel.id) },
          onShare = {
            viewModel.showToast("Reel shared to your followers!")
          },
          onSave = { viewModel.toggleReelSave(reel.id) },
          onFollow = { viewModel.toggleReelFollow(reel.id) },
          onCreatorClick = { viewModel.navigateToProfile(reel.creatorId) }
        )
      }
    }

    // Top overlay branding
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Reels",
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = Color.White
      )
      IconButton(onClick = { viewModel.showToast("Camera opening for Reel recording...") }) {
        Icon(
          imageVector = Icons.Default.CameraAlt,
          contentDescription = "Create Reel",
          tint = Color.White
        )
      }
    }
  }
}

@Composable
fun androidx.compose.foundation.lazy.LazyItemScope.ReelItem(
  reel: Reel,
  onLike: () -> Unit,
  onComment: () -> Unit,
  onShare: () -> Unit,
  onSave: () -> Unit,
  onFollow: () -> Unit,
  onCreatorClick: () -> Unit
) {
  var isPlaying by remember { mutableStateOf(true) }
  var showPlayPauseFeedback by remember { mutableStateOf(false) }

  // Rotating vinyl animation
  val infiniteTransition = rememberInfiniteTransition(label = "music_disc")
  val rotation by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(4000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "rotation"
  )

  Box(
    modifier = Modifier
      .fillParentMaxSize()
      .background(Color.Black)
      .clickable {
        isPlaying = !isPlaying
        showPlayPauseFeedback = true
      }
  ) {
    // 1. Reel Video/Thumbnail Image
    AsyncImage(
      model = ImageRequest.Builder(LocalContext.current)
        .data(reel.videoThumbnail)
        .crossfade(true)
        .build(),
      contentDescription = "Reel video",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize()
    )

    // Dark Bottom Vignette for readable overlays
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.65f)
        .align(Alignment.BottomCenter)
        .background(
          Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
          )
        )
    )

    // Play / Pause Icon Feedback Indicator
    LaunchedEffect(showPlayPauseFeedback) {
      if (showPlayPauseFeedback) {
        kotlinx.coroutines.delay(600)
        showPlayPauseFeedback = false
      }
    }

    AnimatedVisibility(
      visible = showPlayPauseFeedback,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.Center)
    ) {
      Box(
        modifier = Modifier
          .size(72.dp)
          .clip(CircleShape)
          .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(44.dp)
        )
      }
    }

    // 2. Right Side Action Bar: Like, Comment, Share, Save, Disc
    Column(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 12.dp, bottom = 90.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Like Button
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
          onClick = onLike,
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
        ) {
          Icon(
            imageVector = if (reel.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = "Like Reel",
            tint = if (reel.isLiked) SocivaPink else Color.White,
            modifier = Modifier.size(28.dp)
          )
        }
        Text(
          text = "${reel.likesCount}",
          color = Color.White,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold
        )
      }

      // Comment Button
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
          onClick = onComment,
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
        ) {
          Icon(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            contentDescription = "Comment",
            tint = Color.White,
            modifier = Modifier.size(26.dp)
          )
        }
        Text(
          text = "${reel.commentsCount}",
          color = Color.White,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold
        )
      }

      // Share Button
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
          onClick = onShare,
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
        ) {
          Icon(
            imageVector = Icons.Outlined.Share,
            contentDescription = "Share",
            tint = Color.White,
            modifier = Modifier.size(26.dp)
          )
        }
        Text(
          text = "${reel.sharesCount}",
          color = Color.White,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold
        )
      }

      // Save / Bookmark
      IconButton(
        onClick = onSave,
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(Color.Black.copy(alpha = 0.35f))
      ) {
        Icon(
          imageVector = if (reel.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
          contentDescription = "Save Reel",
          tint = if (reel.isSaved) SocivaBlue else Color.White,
          modifier = Modifier.size(26.dp)
        )
      }

      // Rotating Audio Vinyl Disc
      Box(
        modifier = Modifier
          .size(38.dp)
          .rotate(if (isPlaying) rotation else 0f)
          .clip(CircleShape)
          .background(Color.DarkGray)
          .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        AsyncImage(
          model = ImageRequest.Builder(LocalContext.current)
            .data(reel.creatorAvatar)
            .crossfade(true)
            .build(),
          contentDescription = "Audio track",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
        )
      }
    }

    // 3. Bottom Left Creator Info & Audio Info
    Column(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .fillMaxWidth(0.8f)
        .padding(start = 16.dp, bottom = 90.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Creator row with Follow button
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        UserAvatar(
          avatarUrl = reel.creatorAvatar,
          name = reel.creatorName,
          size = 38.dp,
          onClick = onCreatorClick
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.clickable { onCreatorClick() }
        ) {
          Text(
            text = reel.creatorName,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
          )
          if (reel.isCreatorVerified) {
            Spacer(modifier = Modifier.width(4.dp))
            VerifiedBadge(size = 14.dp)
          }
        }

        OutlinedButton(
          onClick = onFollow,
          shape = RoundedCornerShape(16.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
          modifier = Modifier.height(28.dp),
          colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White
          ),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
        ) {
          Text(
            text = if (reel.isFollowing) "Following" else "Follow",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
          )
        }
      }

      // Caption
      Text(
        text = reel.caption,
        color = Color.White,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
      )

      // Audio Information Bar
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
          .clip(RoundedCornerShape(12.dp))
          .background(Color.Black.copy(alpha = 0.4f))
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Icon(
          imageVector = Icons.Default.MusicNote,
          contentDescription = "Audio track",
          tint = Color.White,
          modifier = Modifier.size(14.dp)
        )
        Text(
          text = reel.audioTitle,
          color = Color.White,
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
