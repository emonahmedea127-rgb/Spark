package com.example.sociva.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
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
import com.example.sociva.data.model.FriendRequestItem
import com.example.sociva.data.model.FriendStatus
import com.example.sociva.data.model.User
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.sociva.ui.components.VerifiedBadge
import com.example.ui.theme.SocivaBlue

@Composable
fun FriendsScreen(
  viewModel: SocivaViewModel,
  modifier: Modifier = Modifier
) {
  val friendRequests by viewModel.friendRequests.collectAsState()
  val sentFriendRequests by viewModel.sentFriendRequests.collectAsState()
  val friends by viewModel.friends.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()

  var selectedTab by remember { mutableStateOf(0) } // 0: Requests, 1: Suggestions, 2: All Friends
  var friendsSearchQuery by remember { mutableStateOf("") }
  var friendToRemove by remember { mutableStateOf<User?>(null) }

  val suggestions = remember(allUsers, friends) {
    val friendIds = friends.map { it.id }.toSet()
    allUsers.filter { !friendIds.contains(it.id) && it.id != "user_me" }
  }

  val filteredFriends = remember(friends, friendsSearchQuery) {
    if (friendsSearchQuery.isBlank()) friends
    else friends.filter {
      it.fullName.contains(friendsSearchQuery, ignoreCase = true) ||
      it.username.contains(friendsSearchQuery, ignoreCase = true)
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .testTag("friends_screen")
  ) {
    // Top Tabs
    ScrollableTabRow(
      selectedTabIndex = selectedTab,
      edgePadding = 16.dp,
      containerColor = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.primary,
      divider = {}
    ) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Requests", fontWeight = FontWeight.Bold)
            if (friendRequests.isNotEmpty()) {
              Spacer(modifier = Modifier.width(6.dp))
              Badge(containerColor = MaterialTheme.colorScheme.error) {
                Text("${friendRequests.size}")
              }
            }
          }
        }
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = { Text("Suggestions", fontWeight = FontWeight.Bold) }
      )
      Tab(
        selected = selectedTab == 2,
        onClick = { selectedTab = 2 },
        text = { Text("All Friends (${friends.size})", fontWeight = FontWeight.Bold) }
      )
    }

    when (selectedTab) {
      // 0: Friend Requests (Received & Sent)
      0 -> {
        if (friendRequests.isEmpty() && sentFriendRequests.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = Icons.Default.PersonSearch,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "No pending friend requests",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
              )
            }
          }
        } else {
          LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            // Incoming Requests
            if (friendRequests.isNotEmpty()) {
              item {
                Text(
                  text = "Friend Requests (${friendRequests.size})",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(bottom = 4.dp)
                )
              }
              items(friendRequests, key = { it.id }) { request ->
                FriendRequestCard(
                  request = request,
                  onAccept = { viewModel.acceptFriendRequest(request) },
                  onReject = { viewModel.rejectFriendRequest(request.id) },
                  onClick = { viewModel.navigateToProfile(request.senderId) }
                )
              }
            }

            // Sent Requests
            if (sentFriendRequests.isNotEmpty()) {
              item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  text = "Sent Requests (${sentFriendRequests.size})",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(bottom = 4.dp)
                )
              }
              items(sentFriendRequests, key = { it.id }) { request ->
                SentFriendRequestCard(
                  request = request,
                  onCancel = { viewModel.cancelFriendRequestById(request.id) },
                  onClick = { viewModel.navigateToProfile(request.receiverId) }
                )
              }
            }
          }
        }
      }

      // 1: Suggestions
      1 -> {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(suggestions, key = { it.id }) { user ->
            FriendSuggestionCard(
              user = user,
              viewModel = viewModel,
              onClick = { viewModel.navigateToProfile(user.id) }
            )
          }
        }
      }

      // 2: All Friends
      2 -> {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
          OutlinedTextField(
            value = friendsSearchQuery,
            onValueChange = { friendsSearchQuery = it },
            placeholder = { Text("Search friends...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp),
            singleLine = true
          )

          if (filteredFriends.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = if (friendsSearchQuery.isBlank()) "You have no friends added yet." else "No friends match '$friendsSearchQuery'",
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          } else {
            LazyColumn(
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              items(filteredFriends, key = { it.id }) { friend ->
                FriendItemRow(
                  friend = friend,
                  onMessage = { viewModel.openOrCreateConversationWithUser(friend.id) },
                  onRemove = { friendToRemove = friend },
                  onClick = { viewModel.navigateToProfile(friend.id) }
                )
              }
            }
          }
        }
      }
    }
  }

  // Remove Friend Confirmation Dialog
  if (friendToRemove != null) {
    AlertDialog(
      onDismissRequest = { friendToRemove = null },
      title = { Text("Remove from Friends") },
      text = { Text("Are you sure you want to remove ${friendToRemove?.fullName} from your friends? You will still continue following them unless you choose to unfollow.") },
      confirmButton = {
        TextButton(
          onClick = {
            val user = friendToRemove
            friendToRemove = null
            if (user != null) {
              viewModel.removeFriend(user.id)
            }
          }
        ) {
          Text("Remove Friend", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { friendToRemove = null }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
fun FriendRequestCard(
  request: FriendRequestItem,
  onAccept: () -> Unit,
  onReject: () -> Unit,
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .testTag("friend_request_card_${request.id}")
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      UserAvatar(
        avatarUrl = request.senderAvatar,
        name = request.senderName,
        size = 56.dp
      )

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = request.senderName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        if (request.mutualFriendsCount > 0) {
          Text(
            text = "${request.mutualFriendsCount} mutual friends",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = onAccept,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp).testTag("confirm_friend_button_${request.id}")
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Confirm", fontWeight = FontWeight.Bold, fontSize = 13.sp)
          }

          FilledTonalButton(
            onClick = onReject,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp).testTag("delete_friend_button_${request.id}")
          ) {
            Text("Delete", fontSize = 13.sp)
          }
        }
      }
    }
  }
}

@Composable
fun SentFriendRequestCard(
  request: FriendRequestItem,
  onCancel: () -> Unit,
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .testTag("sent_friend_request_card_${request.id}")
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      UserAvatar(
        avatarUrl = request.receiverAvatar,
        name = request.receiverName,
        size = 52.dp
      )

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = request.receiverName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "@${request.receiverUsername}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      OutlinedButton(
        onClick = onCancel,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = MaterialTheme.colorScheme.error
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.height(36.dp).testTag("cancel_sent_request_button_${request.id}")
      ) {
        Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.Medium)
      }
    }
  }
}

@Composable
fun FriendSuggestionCard(
  user: User,
  viewModel: SocivaViewModel,
  onClick: () -> Unit
) {
  val friendStatus by viewModel.getFriendStatusFlow(user.id).collectAsState(initial = FriendStatus.NONE)

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .testTag("friend_suggestion_card_${user.id}")
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      UserAvatar(
        avatarUrl = user.avatarUrl,
        name = user.fullName,
        size = 52.dp
      )

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = user.fullName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          if (user.isVerified) {
            Spacer(modifier = Modifier.width(4.dp))
            VerifiedBadge(size = 14.dp)
          }
        }
        Text(
          text = user.bio,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1
        )
      }

      when (friendStatus) {
        FriendStatus.REQUEST_SENT -> {
          FilledTonalButton(
            onClick = { viewModel.cancelFriendRequest(user.id) },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(36.dp).testTag("cancel_request_button_${user.id}")
          ) {
            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          }
        }
        FriendStatus.FRIENDS -> {
          FilledTonalButton(
            onClick = { /* already friends */ },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(36.dp)
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp), tint = SocivaBlue)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Friends", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
        else -> {
          Button(
            onClick = { viewModel.sendFriendRequest(user.id) },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(36.dp).testTag("add_friend_suggestion_button_${user.id}")
          ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add", fontSize = 13.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
fun FriendItemRow(
  friend: User,
  onMessage: () -> Unit,
  onRemove: () -> Unit,
  onClick: () -> Unit
) {
  var showMenu by remember { mutableStateOf(false) }

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .testTag("friend_item_row_${friend.id}")
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      UserAvatar(
        avatarUrl = friend.avatarUrl,
        name = friend.fullName,
        size = 48.dp,
        showOnlineBadge = true,
        isOnline = friend.isOnline
      )

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = friend.fullName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
          if (friend.isVerified) {
            Spacer(modifier = Modifier.width(4.dp))
            VerifiedBadge(size = 14.dp)
          }
        }
        Text(
          text = "@${friend.username}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      IconButton(onClick = onMessage) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Chat,
          contentDescription = "Message",
          tint = SocivaBlue
        )
      }

      Box {
        IconButton(onClick = { showMenu = true }) {
          Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More options",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        DropdownMenu(
          expanded = showMenu,
          onDismissRequest = { showMenu = false }
        ) {
          DropdownMenuItem(
            text = { Text("View Profile") },
            onClick = {
              showMenu = false
              onClick()
            },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
          )
          DropdownMenuItem(
            text = { Text("Unfriend", color = MaterialTheme.colorScheme.error) },
            onClick = {
              showMenu = false
              onRemove()
            },
            leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
          )
        }
      }
    }
  }
}
