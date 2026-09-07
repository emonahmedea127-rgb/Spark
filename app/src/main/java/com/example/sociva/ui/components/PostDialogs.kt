package com.example.sociva.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sociva.data.model.*
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePostBottomSheet(
  post: Post,
  currentUser: User?,
  onDismiss: () -> Unit,
  onShare: (caption: String, audience: PostAudience) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var caption by remember { mutableStateOf("") }
  var selectedAudience by remember { mutableStateOf(PostAudience.PUBLIC) }
  var showAudienceMenu by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    dragHandle = { BottomSheetDefaults.DragHandle() },
    modifier = Modifier.testTag("share_post_bottom_sheet")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .navigationBarsPadding()
        .imePadding()
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Share to Feed",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Current User Info & Audience Selector
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        UserAvatar(
          avatarUrl = currentUser?.avatarUrl ?: "",
          name = currentUser?.fullName ?: "Me",
          size = 46.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = currentUser?.fullName ?: "Me",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.height(4.dp))
          Box {
            Surface(
              modifier = Modifier
                .clickable { showAudienceMenu = true }
                .testTag("share_audience_selector"),
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
              border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  imageVector = when (selectedAudience) {
                    PostAudience.FRIENDS -> Icons.Default.Group
                    PostAudience.ONLY_ME -> Icons.Default.Lock
                    else -> Icons.Default.Public
                  },
                  contentDescription = null,
                  modifier = Modifier.size(12.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = selectedAudience.label,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontWeight = FontWeight.Medium
                )
                Icon(
                  imageVector = Icons.Default.ArrowDropDown,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            DropdownMenu(
              expanded = showAudienceMenu,
              onDismissRequest = { showAudienceMenu = false }
            ) {
              PostAudience.values().forEach { aud ->
                DropdownMenuItem(
                  text = { Text(aud.label) },
                  leadingIcon = {
                    Icon(
                      imageVector = when (aud) {
                        PostAudience.FRIENDS -> Icons.Default.Group
                        PostAudience.ONLY_ME -> Icons.Default.Lock
                        else -> Icons.Default.Public
                      },
                      contentDescription = null,
                      modifier = Modifier.size(18.dp)
                    )
                  },
                  onClick = {
                    selectedAudience = aud
                    showAudienceMenu = false
                  }
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Caption Input Field
      OutlinedTextField(
        value = caption,
        onValueChange = { caption = it },
        placeholder = { Text("Say something about this...") },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("share_caption_input"),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = MaterialTheme.colorScheme.primary,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp),
        minLines = 2,
        maxLines = 5
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Original Post Nested Card Preview
      val isOrigShared = post.postType == PostType.SHARED_POST && post.sharedPost != null
      val previewAuthorAvatar = if (isOrigShared) post.sharedPost?.authorAvatar ?: "" else post.authorAvatar
      val previewAuthorName = if (isOrigShared) post.sharedPost?.authorName ?: "" else post.authorName
      val previewIsVerified = if (isOrigShared) post.sharedPost?.isAuthorVerified ?: false else post.isAuthorVerified
      val previewTimestamp = if (isOrigShared) post.sharedPost?.timestamp ?: 0L else post.timestamp
      val previewAudience = if (isOrigShared) post.sharedPost?.audience ?: PostAudience.PUBLIC else post.audience
      val previewContent = if (isOrigShared) post.sharedPost?.content ?: "" else post.content
      val previewMedia = if (isOrigShared) post.sharedPost?.mediaUrls ?: emptyList() else post.mediaUrls
      val previewActionContext = if (isOrigShared) post.sharedPost?.actionContextText else post.actionContextText

      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("original_post_preview_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          // Author Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            UserAvatar(
              avatarUrl = previewAuthorAvatar,
              name = previewAuthorName,
              size = 36.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = previewAuthorName,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                if (previewIsVerified) {
                  Spacer(modifier = Modifier.width(4.dp))
                  VerifiedBadge(size = 12.dp)
                }
                if (!previewActionContext.isNullOrBlank()) {
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = previewActionContext,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = formatRelativeTime(previewTimestamp),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = "•",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                  imageVector = when (previewAudience) {
                    PostAudience.FRIENDS -> Icons.Default.Group
                    PostAudience.ONLY_ME -> Icons.Default.Lock
                    else -> Icons.Default.Public
                  },
                  contentDescription = null,
                  modifier = Modifier.size(10.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          if (previewContent.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = previewContent,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface
            )
          }

          if (previewMedia.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            AsyncImage(
              model = ImageRequest.Builder(LocalContext.current)
                .data(previewMedia.first())
                .crossfade(true)
                .build(),
              contentDescription = "Original post preview image",
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(8.dp))
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Share Now Action Button
      Button(
        onClick = { onShare(caption, selectedAudience) },
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("share_now_button"),
        colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
        shape = RoundedCornerShape(12.dp)
      ) {
        Icon(
          imageVector = Icons.Outlined.Share,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Share Now", fontWeight = FontWeight.Bold)
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
fun EditPostDialog(
  post: Post,
  onDismiss: () -> Unit,
  onSave: (newContent: String) -> Unit
) {
  var content by remember { mutableStateOf(post.content) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (post.postType == PostType.SHARED_POST) "Edit Share Caption" else "Edit Post",
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = content,
          onValueChange = { content = it },
          placeholder = { Text("What's on your mind?") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_post_content_input"),
          shape = RoundedCornerShape(12.dp),
          minLines = 3,
          maxLines = 8
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(content) },
        colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
        modifier = Modifier.testTag("save_edit_post_button")
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
