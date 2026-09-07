package com.example.sociva.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sociva.data.model.NotificationItem
import com.example.sociva.data.model.NotificationType
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.sociva.ui.components.formatRelativeTime
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaIndigo
import com.example.ui.theme.SocivaPink
import com.example.ui.theme.SocivaPurple

@Composable
fun NotificationsScreen(
  viewModel: SocivaViewModel,
  modifier: Modifier = Modifier
) {
  val notifications by viewModel.notifications.collectAsState()
  var showOnlyUnread by remember { mutableStateOf(false) }

  val filteredNotifications = remember(notifications, showOnlyUnread) {
    if (showOnlyUnread) notifications.filter { !it.isRead }
    else notifications
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .testTag("notifications_screen")
  ) {
    // Header controls: Filter chips & Mark all as read
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = !showOnlyUnread,
          onClick = { showOnlyUnread = false },
          label = { Text("All") }
        )
        FilterChip(
          selected = showOnlyUnread,
          onClick = { showOnlyUnread = true },
          label = { Text("Unread") }
        )
      }

      TextButton(
        onClick = { viewModel.markAllNotificationsRead() }
      ) {
        Text("Mark all read", style = MaterialTheme.typography.labelMedium)
      }
    }

    if (filteredNotifications.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "No notifications yet",
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        items(filteredNotifications, key = { it.id }) { item ->
          NotificationRow(
            item = item,
            onClick = {
              viewModel.markNotificationRead(item.id)
              if (item.targetPostId != null) {
                viewModel.openComments(item.targetPostId)
              }
            }
          )
        }
      }
    }
  }
}

@Composable
fun NotificationRow(
  item: NotificationItem,
  onClick: () -> Unit
) {
  val typeIcon = when (item.type) {
    NotificationType.LIKE, NotificationType.STORY_REACTION, NotificationType.COMMENT_REACTION -> Icons.Default.Favorite
    NotificationType.COMMENT, NotificationType.COMMENT_REPLY -> Icons.Default.Comment
    NotificationType.SHARE -> Icons.Default.Share
    NotificationType.FRIEND_REQUEST, NotificationType.ACCEPT_REQUEST -> Icons.Default.PersonAdd
    NotificationType.FOLLOW -> Icons.Default.AddCircle
    NotificationType.MENTION -> Icons.Default.AlternateEmail
    NotificationType.MESSAGE -> Icons.Default.Chat
    NotificationType.RELATIONSHIP_REQUEST, NotificationType.RELATIONSHIP_ACCEPTED -> Icons.Default.Favorite
    NotificationType.RELATIONSHIP_DECLINED -> Icons.Default.FavoriteBorder
    NotificationType.TAG -> Icons.Default.Label
  }

  val typeColor = when (item.type) {
    NotificationType.LIKE, NotificationType.STORY_REACTION, NotificationType.COMMENT_REACTION -> SocivaPink
    NotificationType.COMMENT, NotificationType.COMMENT_REPLY -> SocivaBlue
    NotificationType.SHARE -> SocivaBlue
    NotificationType.FRIEND_REQUEST, NotificationType.ACCEPT_REQUEST -> Color(0xFF10B981)
    NotificationType.FOLLOW -> SocivaPurple
    NotificationType.MENTION -> Color(0xFFF59E0B)
    NotificationType.MESSAGE -> SocivaBlue
    NotificationType.RELATIONSHIP_REQUEST, NotificationType.RELATIONSHIP_ACCEPTED -> Color(0xFFEC4899)
    NotificationType.RELATIONSHIP_DECLINED -> Color(0xFF6B7280)
    NotificationType.TAG -> SocivaIndigo
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(
        if (!item.isRead) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else Color.Transparent
      )
      .clickable { onClick() }
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Avatar with icon badge
    Box {
      UserAvatar(
        avatarUrl = item.actorAvatar,
        name = item.actorName,
        size = 50.dp
      )

      Box(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .size(20.dp)
          .clip(CircleShape)
          .background(typeColor)
          .padding(3.dp),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = typeIcon,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(12.dp)
        )
      }
    }

    // Message Content
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = item.messageSnippet,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = formatRelativeTime(item.timestamp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    // Unread Blue Dot
    if (!item.isRead) {
      Box(
        modifier = Modifier
          .size(10.dp)
          .clip(CircleShape)
          .background(SocivaBlue)
      )
    }
  }
}
