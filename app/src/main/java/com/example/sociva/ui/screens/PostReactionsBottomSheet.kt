package com.example.sociva.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sociva.data.model.PostReactionUser
import com.example.sociva.data.model.ReactionRelationshipStatus
import com.example.sociva.data.model.ReactionType
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.sociva.ui.components.VerifiedBadge
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostReactionsBottomSheet(
  postId: String,
  viewModel: SocivaViewModel,
  onDismiss: () -> Unit
) {
  val reactionUsers by viewModel.getPostReactionUsers(postId).collectAsState(initial = emptyList())
  var selectedTab by remember { mutableStateOf<ReactionType?>(null) } // null means "All"
  var searchQuery by remember { mutableStateOf("") }

  // Filter reactions based on selected tab and search query
  val filteredReactions = remember(reactionUsers, selectedTab, searchQuery) {
    reactionUsers
      .filter { userReaction ->
        if (selectedTab == null) true else userReaction.reactionType == selectedTab
      }
      .filter { userReaction ->
        if (searchQuery.isBlank()) true else {
          userReaction.user.fullName.contains(searchQuery.trim(), ignoreCase = true) ||
            userReaction.user.username.contains(searchQuery.trim(), ignoreCase = true)
        }
      }
  }

  // Count per reaction type for the filter tabs
  val reactionCounts = remember(reactionUsers) {
    reactionUsers.groupingBy { it.reactionType }.eachCount()
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surface,
    modifier = Modifier.testTag("post_reactions_bottom_sheet")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.85f)
        .navigationBarsPadding()
    ) {
      // Top Header: Title & Close Button
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Reactions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "${reactionUsers.size} total ${if (reactionUsers.size == 1) "reaction" else "reactions"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier.testTag("close_reactions_button")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Search Bar inside reaction sheet
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search by name or username...") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
          focusedBorderColor = SocivaBlue,
          unfocusedBorderColor = Color.Transparent
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 6.dp)
          .testTag("reactions_search_input")
      )

      // Filter Tabs: [All (N)] [❤️ Love (N)] [👍 Like (N)] [😂 Haha (N)] ...
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // "All" tab
        FilterChip(
          selected = selectedTab == null,
          onClick = { selectedTab = null },
          label = {
            Text("All ${reactionUsers.size}", fontWeight = if (selectedTab == null) FontWeight.Bold else FontWeight.Normal)
          },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SocivaBlue,
            selectedLabelColor = Color.White
          ),
          modifier = Modifier.testTag("reaction_filter_all")
        )

        // Specific reaction type chips in natural order
        ReactionType.values().forEach { type ->
          val count = reactionCounts[type] ?: 0
          if (count > 0) {
            FilterChip(
              selected = selectedTab == type,
              onClick = { selectedTab = if (selectedTab == type) null else type },
              label = {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Text(type.emoji, fontSize = 15.sp)
                  Text(
                    text = "$count",
                    fontWeight = if (selectedTab == type) FontWeight.Bold else FontWeight.Normal
                  )
                }
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = when (type) {
                  ReactionType.LOVE -> Color(0xFFEF4444)
                  ReactionType.LIKE -> SocivaBlue
                  ReactionType.HAHA -> Color(0xFFF59E0B)
                  ReactionType.WOW -> Color(0xFF8B5CF6)
                  ReactionType.SAD -> Color(0xFF3B82F6)
                  ReactionType.ANGRY -> Color(0xFFEA580C)
                },
                selectedLabelColor = Color.White
              ),
              modifier = Modifier.testTag("reaction_filter_${type.name.lowercase()}")
            )
          }
        }
      }

      HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
      )

      // User List
      if (filteredReactions.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.PeopleOutline,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.size(48.dp)
            )
            Text(
              text = if (searchQuery.isNotBlank()) "No users matching \"$searchQuery\"" else "No reactions found",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("reactions_user_list"),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(filteredReactions, key = { it.reactionId }) { item ->
            ReactionUserRow(
              item = item,
              onProfileClick = {
                onDismiss()
                viewModel.navigateToProfile(item.user.id)
              },
              onMessageClick = {
                onDismiss()
                viewModel.openOrCreateConversationWithUser(item.user.id)
              },
              onAddFriendClick = {
                viewModel.sendFriendRequest(item.user.id)
              },
              onCancelRequestClick = {
                viewModel.cancelFriendRequest(item.user.id)
              },
              onAcceptRequestClick = {
                item.incomingRequestId?.let { reqId ->
                  viewModel.acceptFriendRequestById(reqId)
                }
              }
            )
          }
        }
      }
    }
  }
}

@Composable
fun ReactionUserRow(
  item: PostReactionUser,
  onProfileClick: () -> Unit,
  onMessageClick: () -> Unit,
  onAddFriendClick: () -> Unit,
  onCancelRequestClick: () -> Unit,
  onAcceptRequestClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable { onProfileClick() }
      .padding(vertical = 6.dp, horizontal = 4.dp)
      .testTag("reaction_user_row_${item.user.id}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Avatar with overlapping reaction badge
    Box(
      modifier = Modifier.size(50.dp),
      contentAlignment = Alignment.BottomEnd
    ) {
      UserAvatar(
        avatarUrl = item.user.avatarUrl,
        name = item.user.fullName,
        size = 48.dp,
        onClick = onProfileClick
      )

      // Reaction emoji badge at bottom-right corner
      Box(
        modifier = Modifier
          .size(20.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.surface)
          .padding(1.dp)
          .clip(CircleShape)
          .background(
            when (item.reactionType) {
              ReactionType.LOVE -> Color(0xFFEF4444)
              ReactionType.LIKE -> SocivaBlue
              ReactionType.HAHA -> Color(0xFFF59E0B)
              ReactionType.WOW -> Color(0xFF8B5CF6)
              ReactionType.SAD -> Color(0xFF3B82F6)
              ReactionType.ANGRY -> Color(0xFFEA580C)
            }
          ),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = item.reactionType.emoji,
          fontSize = 10.sp
        )
      }
    }

    Spacer(modifier = Modifier.width(12.dp))

    // Name, Username & Mutual/Relationship info
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = item.user.fullName,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (item.user.isVerified) {
          Spacer(modifier = Modifier.width(4.dp))
          VerifiedBadge(size = 14.dp)
        }
      }

      Text(
        text = "@${item.user.username}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      // Mutual friends / bio hint
      if (item.user.bio.isNotBlank()) {
        Text(
          text = item.user.bio,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    Spacer(modifier = Modifier.width(8.dp))

    // Relationship Action Buttons
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      when (item.relationshipStatus) {
        ReactionRelationshipStatus.YOU -> {
          // Self indicator
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(16.dp)
          ) {
            Text(
              text = "You",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
          }
        }

        ReactionRelationshipStatus.FRIEND -> {
          // Friends badge + Message button
          Button(
            onClick = onMessageClick,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = SocivaBlue,
              contentColor = Color.White
            ),
            modifier = Modifier.height(34.dp).testTag("message_user_${item.user.id}")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Chat,
              contentDescription = "Message",
              modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Message", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          }
        }

        ReactionRelationshipStatus.CAN_ADD_FRIEND,
        ReactionRelationshipStatus.FOLLOWING -> {
          // Add Friend Button + Message Button
          OutlinedButton(
            onClick = onAddFriendClick,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp).testTag("add_friend_${item.user.id}")
          ) {
            Icon(
              imageVector = Icons.Outlined.PersonAdd,
              contentDescription = "Add Friend",
              modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Add Friend", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          }

          IconButton(
            onClick = onMessageClick,
            modifier = Modifier.size(34.dp).testTag("quick_message_${item.user.id}")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Chat,
              contentDescription = "Message",
              tint = SocivaBlue,
              modifier = Modifier.size(18.dp)
            )
          }
        }

        ReactionRelationshipStatus.REQUEST_SENT -> {
          // Request Sent (can cancel)
          FilledTonalButton(
            onClick = onCancelRequestClick,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp).testTag("cancel_request_${item.user.id}")
          ) {
            Icon(
              imageVector = Icons.Default.HourglassTop,
              contentDescription = "Pending",
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Requested", fontSize = 12.sp)
          }

          IconButton(
            onClick = onMessageClick,
            modifier = Modifier.size(34.dp)
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Chat,
              contentDescription = "Message",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
          }
        }

        ReactionRelationshipStatus.REQUEST_RECEIVED -> {
          // Accept Request button
          Button(
            onClick = onAcceptRequestClick,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF10B981),
              contentColor = Color.White
            ),
            modifier = Modifier.height(34.dp).testTag("accept_request_${item.user.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "Accept",
              modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Accept", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }
  }
}
