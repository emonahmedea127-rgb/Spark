package com.example.sociva.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.sociva.data.model.FriendStatus
import com.example.sociva.data.model.ProfileVisitorItem
import com.example.sociva.ui.SocivaScreen
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.formatRelativeTime
import com.example.ui.theme.SocivaBlue
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileVisitorsScreen(
  viewModel: SocivaViewModel,
  onNavigateBack: () -> Unit
) {
  var limit by remember { mutableIntStateOf(50) }
  val visitors by viewModel.getProfileVisitorsFlow(limit).collectAsState(initial = emptyList())
  val stats by viewModel.myProfileViewStats.collectAsState()
  val userSettings by viewModel.userSettings.collectAsState()
  var showPrivacyInfoDialog by remember { mutableStateOf(false) }

  // Mark all profile visitors as seen upon entering
  LaunchedEffect(Unit) {
    viewModel.markProfileVisitorsSeen()
  }

  if (showPrivacyInfoDialog) {
    AlertDialog(
      onDismissRequest = { showPrivacyInfoDialog = false },
      icon = {
        Icon(
          Icons.Default.Security,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp)
        )
      },
      title = {
        Text("About Profile View History", fontWeight = FontWeight.Bold)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Profile View History lets you see who recently visited your profile in real-time.",
            fontSize = 14.sp
          )
          Text(
            "Privacy guarantee: If you turn Profile View History off in Settings, your visits won't appear as an identified visitor to other people. Your visitor history is only visible to you.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            "30-minute deduplication prevents duplicate counts from repeated visits.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { showPrivacyInfoDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue)
        ) {
          Text("Got It")
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            showPrivacyInfoDialog = false
            viewModel.navigateTo(SocivaScreen.SETTINGS)
          }
        ) {
          Text("Privacy Settings")
        }
      }
    )
  }

  Scaffold(
    modifier = Modifier.testTag("profile_visitors_screen"),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              "Profile Visitors",
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp
            )
            Text(
              "People who viewed your profile",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(
            onClick = { showPrivacyInfoDialog = true },
            modifier = Modifier.testTag("privacy_info_button")
          ) {
            Icon(
              Icons.Default.Info,
              contentDescription = "Privacy Information",
              tint = MaterialTheme.colorScheme.primary
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Analytics & Counter Grid
      item {
        VisitorStatsCard(
          stats = stats,
          isHistoryEnabled = userSettings.profileViewHistoryEnabled,
          onToggleHistory = { enabled ->
            viewModel.updateProfileViewHistoryEnabled(enabled)
          },
          onOpenSettings = {
            viewModel.navigateTo(SocivaScreen.SETTINGS)
          }
        )
      }

      // 2. Visitors List Header
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Recent Visitors (${visitors.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
          )
          if (!userSettings.profileViewHistoryEnabled) {
            Surface(
              color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text(
                text = "History Paused",
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
        }
      }

      // 3. Visitor Items or Empty State
      if (visitors.isEmpty()) {
        item {
          EmptyVisitorsState(
            onExploreFriends = {
              viewModel.navigateTo(SocivaScreen.MAIN)
            }
          )
        }
      } else {
        val groupedVisitors = groupVisitorsByDate(visitors)
        groupedVisitors.forEach { (sectionTitle, sectionVisitors) ->
          item {
            Text(
              text = sectionTitle,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
            )
          }

          items(sectionVisitors, key = { it.viewId }) { item ->
            VisitorCard(
              item = item,
              onProfileClick = {
                viewModel.navigateToProfile(item.user.id)
              },
              onMessageClick = {
                viewModel.openOrCreateConversationWithUser(item.user.id)
              },
              onAddFriendClick = {
                viewModel.sendFriendRequest(item.user.id)
              },
              onCancelFriendRequestClick = {
                viewModel.cancelFriendRequest(item.user.id)
              },
              onAcceptFriendRequestClick = {
                viewModel.acceptFriendRequestFromUser(item.user.id)
              },
              onToggleFollowClick = {
                if (item.isFollowing) {
                  viewModel.unfollowUser(item.user.id)
                } else {
                  viewModel.followUser(item.user.id)
                }
              }
            )
          }
        }

        if (visitors.size >= limit) {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
              contentAlignment = Alignment.Center
            ) {
              OutlinedButton(
                onClick = { limit += 30 },
                modifier = Modifier.testTag("load_more_visitors_button"),
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Load More Visitors")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun VisitorStatsCard(
  stats: com.example.sociva.data.model.ProfileViewStats,
  isHistoryEnabled: Boolean,
  onToggleHistory: (Boolean) -> Unit,
  onOpenSettings: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("visitor_stats_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            Icons.Default.QueryStats,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
          )
          Text(
            "Profile Views Overview",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
          )
        }
        if (stats.unseenCount > 0) {
          Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(12.dp)
          ) {
            Text(
              "${stats.unseenCount} new",
              color = MaterialTheme.colorScheme.onPrimary,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }
        }
      }

      // Stats 4-grid
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        StatPill(title = "Today", count = "${stats.todayCount}", modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        StatPill(title = "This Week", count = "${stats.thisWeekCount}", modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        StatPill(title = "This Month", count = "${stats.thisMonthCount}", modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        StatPill(title = "Total", count = "${stats.totalCount}", isPrimary = true, modifier = Modifier.weight(1f))
      }

      Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      // Privacy status row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (isHistoryEnabled) "Profile View History is ON" else "Profile View History is OFF",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (isHistoryEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
          )
          Text(
            text = if (isHistoryEnabled)
              "Your visits appear to others and you can see who visits you."
            else
              "Your visits are anonymous and others cannot identify you.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
          checked = isHistoryEnabled,
          onCheckedChange = onToggleHistory,
          modifier = Modifier.testTag("history_toggle_switch")
        )
      }
    }
  }
}

@Composable
fun StatPill(
  title: String,
  count: String,
  isPrimary: Boolean = false,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = if (isPrimary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = count,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = title,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun VisitorCard(
  item: ProfileVisitorItem,
  onProfileClick: () -> Unit,
  onMessageClick: () -> Unit,
  onAddFriendClick: () -> Unit,
  onCancelFriendRequestClick: () -> Unit,
  onAcceptFriendRequestClick: () -> Unit,
  onToggleFollowClick: () -> Unit
) {
  val user = item.user

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("visitor_card_${user.id}")
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onProfileClick),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (!item.isSeen)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
      else
        MaterialTheme.colorScheme.surface
    ),
    border = if (!item.isSeen)
      androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    else
      androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Top info row: Avatar + Name + Relative Time
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          // Avatar with online green dot
          Box {
            AsyncImage(
              model = user.avatarUrl,
              contentDescription = "${user.fullName}'s avatar",
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
              contentScale = ContentScale.Crop
            )
            if (user.isOnline) {
              Box(
                modifier = Modifier
                  .size(13.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF4CAF50))
                  .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                  .align(Alignment.BottomEnd)
              )
            }
          }

          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = user.fullName,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (user.isVerified) {
                Icon(
                  Icons.Default.Verified,
                  contentDescription = "Verified",
                  tint = SocivaBlue,
                  modifier = Modifier.size(15.dp)
                )
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = "@${user.username}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text("•", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(
                text = formatRelativeTime(item.viewedAt),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
              )
            }

            if (item.mutualFriendsCount > 0) {
              Text(
                text = "${item.mutualFriendsCount} mutual friend${if (item.mutualFriendsCount > 1) "s" else ""}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        if (!item.isSeen) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary)
          )
        }
      }

      // Action Buttons Row: Friend Status + Follow Status
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Friend action
        when (item.friendStatus) {
          FriendStatus.FRIENDS -> {
            Button(
              onClick = onMessageClick,
              modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .testTag("message_button_${user.id}"),
              colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
              Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Message", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
          }
          FriendStatus.REQUEST_SENT -> {
            OutlinedButton(
              onClick = onCancelFriendRequestClick,
              modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .testTag("request_sent_button_${user.id}"),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
              Icon(Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Request Sent", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
          }
          FriendStatus.REQUEST_RECEIVED -> {
            Button(
              onClick = onAcceptFriendRequestClick,
              modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .testTag("accept_friend_button_${user.id}"),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
              Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Accept", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
          }
          FriendStatus.NONE -> {
            Button(
              onClick = onAddFriendClick,
              modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .testTag("add_friend_button_${user.id}"),
              colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
              Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Add Friend", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
          }
        }

        // Follow / Following button
        OutlinedButton(
          onClick = onToggleFollowClick,
          modifier = Modifier
            .weight(1f)
            .height(36.dp)
            .testTag("follow_button_${user.id}"),
          shape = RoundedCornerShape(10.dp),
          colors = if (item.isFollowing)
            ButtonDefaults.outlinedButtonColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
          else
            ButtonDefaults.outlinedButtonColors(),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
          Icon(
            if (item.isFollowing) Icons.Default.Check else Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            if (item.isFollowing) "Following" else "Follow",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
          )
        }
      }
    }
  }
}

@Composable
fun EmptyVisitorsState(
  onExploreFriends: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 24.dp)
      .testTag("empty_visitors_state"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(72.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          Icons.Default.Visibility,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(36.dp)
        )
      }
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = "No profile visitors yet",
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "When other users view your profile, they will appear here. Post updates, join groups, or share stories to get discovered!",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        lineHeight = 18.sp
      )
      Spacer(modifier = Modifier.height(20.dp))
      Button(
        onClick = onExploreFriends,
        colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
        shape = RoundedCornerShape(12.dp)
      ) {
        Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Explore Feed", fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

fun groupVisitorsByDate(visitors: List<ProfileVisitorItem>): Map<String, List<ProfileVisitorItem>> {
  val now = Calendar.getInstance()
  val startOfToday = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
  }.timeInMillis

  val startOfYesterday = startOfToday - (24 * 60 * 60 * 1000L)
  val startOfThisWeek = startOfToday - (7 * 24 * 60 * 60 * 1000L)

  val result = LinkedHashMap<String, MutableList<ProfileVisitorItem>>()
  for (item in visitors) {
    val category = when {
      item.viewedAt >= startOfToday -> "Today"
      item.viewedAt >= startOfYesterday -> "Yesterday"
      item.viewedAt >= startOfThisWeek -> "This Week"
      else -> "Earlier"
    }
    result.getOrPut(category) { mutableListOf() }.add(item)
  }
  return result
}
