package com.example.sociva.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.sociva.data.model.Conversation
import com.example.sociva.data.model.Message
import com.example.sociva.data.model.User
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.sociva.ui.components.VerifiedBadge
import com.example.sociva.ui.components.formatRelativeTime
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaIndigo
import com.example.ui.theme.SocivaPink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit = {},
  onOpenConversation: (String) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val conversations by viewModel.conversations.collectAsState()
  val activeNowUsers by viewModel.activeNowUsers.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val blockedUsers by viewModel.blockedUsers.collectAsState()

  var searchQuery by remember { mutableStateOf("") }
  var showNewMessageDialog by remember { mutableStateOf(false) }
  var showAccountSwitcherDialog by remember { mutableStateOf(false) }

  val blockedUserIds: Set<String> = remember(blockedUsers) { blockedUsers.map { it.blockedId }.toSet() }

  val visibleActiveNowUsers = remember(activeNowUsers, blockedUserIds) {
    activeNowUsers.filter { it.id !in blockedUserIds }
  }

  val filteredConversations = remember(conversations, searchQuery, blockedUserIds) {
    val unblocked = conversations.filter { it.participantId !in blockedUserIds }
    if (searchQuery.isBlank()) unblocked
    else unblocked.filter {
      it.participantName.contains(searchQuery, ignoreCase = true) ||
      it.participantUsername.contains(searchQuery, ignoreCase = true) ||
      it.lastMessage.contains(searchQuery, ignoreCase = true)
    }
  }

  // Also filter users when searching in case user wants to start a chat with someone not in recent conversations
  val matchingUsers = remember(allUsers, searchQuery, currentUser, blockedUserIds) {
    if (searchQuery.isBlank()) emptyList()
    else allUsers.filter { user ->
      user.id != currentUser?.id &&
      user.id !in blockedUserIds &&
      (user.fullName.contains(searchQuery, ignoreCase = true) ||
       user.username.contains(searchQuery, ignoreCase = true))
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable { showAccountSwitcherDialog = true }
            ) {
              UserAvatar(
                avatarUrl = currentUser?.avatarUrl,
                name = currentUser?.fullName ?: "User",
                size = 38.dp,
                showOnlineBadge = true,
                isOnline = true
              )
            }
            Column {
              Text(
                text = "Chats",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
              )
              currentUser?.let { user ->
                Text(
                  text = "as ${user.fullName.substringBefore(" ")}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          // Switch Account Button to easily test two-way messaging between real users
          IconButton(
            onClick = { showAccountSwitcherDialog = true },
            modifier = Modifier.testTag("messenger_switch_account_button")
          ) {
            Icon(
              imageVector = Icons.Default.SwitchAccount,
              contentDescription = "Switch Account",
              tint = MaterialTheme.colorScheme.onSurface
            )
          }

          // New Message Button
          IconButton(
            onClick = { showNewMessageDialog = true },
            modifier = Modifier.testTag("messenger_new_chat_button")
          ) {
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = "New Message",
              tint = SocivaBlue
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showNewMessageDialog = true },
        containerColor = SocivaBlue,
        contentColor = Color.White,
        shape = CircleShape,
        modifier = Modifier.testTag("messenger_fab_new_message")
      ) {
        Icon(Icons.Default.Edit, contentDescription = "New Message")
      }
    },
    modifier = modifier.testTag("messages_screen")
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(MaterialTheme.colorScheme.background)
    ) {
      // Search Bar
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search messages or people...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Default.Close, contentDescription = "Clear")
            }
          }
        },
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
          focusedBorderColor = SocivaBlue,
          unfocusedBorderColor = Color.Transparent
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp)
          .testTag("messenger_search_input"),
        singleLine = true
      )

      LazyColumn(
        modifier = Modifier.fillMaxSize()
      ) {
        // Active Now Carousel (only show when not searching)
        if (searchQuery.isBlank() && visibleActiveNowUsers.isNotEmpty()) {
          item(key = "active_now_header") {
            Text(
              text = "Active Now (${visibleActiveNowUsers.size})",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
          }

          item(key = "active_now_row") {
            LazyRow(
              contentPadding = PaddingValues(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              modifier = Modifier.padding(bottom = 12.dp)
            ) {
              items(visibleActiveNowUsers, key = { it.id }) { onlineUser ->
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                      viewModel.openOrCreateConversationWithUser(onlineUser.id)
                    }
                    .padding(4.dp)
                ) {
                  Box {
                    UserAvatar(
                      avatarUrl = onlineUser.avatarUrl,
                      name = onlineUser.fullName,
                      size = 54.dp,
                      showOnlineBadge = true,
                      isOnline = true
                    )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = onlineUser.fullName.substringBefore(" "),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
            HorizontalDivider(
              color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
          }
        }

        // Matching People when searching
        if (searchQuery.isNotBlank() && matchingUsers.isNotEmpty()) {
          item(key = "search_people_header") {
            Text(
              text = "People",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
          }

          items(matchingUsers, key = { "user_${it.id}" }) { user ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  viewModel.openOrCreateConversationWithUser(user.id)
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              UserAvatar(
                avatarUrl = user.avatarUrl,
                name = user.fullName,
                size = 48.dp,
                showOnlineBadge = true,
                isOnline = user.isOnline
              )
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = user.fullName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                  )
                  if (user.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 14.dp)
                  }
                }
                Text(
                  text = "@${user.username}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              FilledTonalButton(
                onClick = { viewModel.openOrCreateConversationWithUser(user.id) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Text("Chat", fontSize = 12.sp)
              }
            }
          }

          item(key = "search_conversations_header") {
            Text(
              text = "Conversations",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
          }
        }

        // Conversations List
        if (filteredConversations.isEmpty()) {
          item(key = "empty_state") {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp, horizontal = 24.dp),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Surface(
                  shape = CircleShape,
                  color = SocivaBlue.copy(alpha = 0.1f),
                  modifier = Modifier.size(72.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.Chat,
                      contentDescription = null,
                      tint = SocivaBlue,
                      modifier = Modifier.size(36.dp)
                    )
                  }
                }
                Text(
                  text = if (searchQuery.isNotBlank()) "No messages found" else "No messages yet",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = if (searchQuery.isNotBlank()) "Try searching for someone by name or username."
                         else "Connect with friends and start a real-time conversation!",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 24.dp)
                )
                Button(
                  onClick = { showNewMessageDialog = true },
                  shape = RoundedCornerShape(20.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue)
                ) {
                  Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Start New Chat")
                }
              }
            }
          }
        } else {
          items(filteredConversations, key = { it.id }) { conv ->
            ConversationListItem(
              conversation = conv,
              onClick = {
                onOpenConversation(conv.id)
              }
            )
          }
        }
      }
    }
  }

  // Dialog: New Message / Start Chat with any real registered Sociva user
  if (showNewMessageDialog) {
    NewMessageDialog(
      allUsers = allUsers.filter { it.id != currentUser?.id && it.id !in blockedUserIds },
      onDismiss = { showNewMessageDialog = false },
      onSelectUser = { targetUser ->
        showNewMessageDialog = false
        viewModel.openOrCreateConversationWithUser(targetUser.id)
      }
    )
  }

  // Dialog: Account Switcher (allows testing live two-way chat)
  if (showAccountSwitcherDialog) {
    AccountSwitcherDialog(
      users = allUsers,
      currentUserId = currentUser?.id ?: "user_me",
      onDismiss = { showAccountSwitcherDialog = false },
      onSelectUser = { targetUserId ->
        showAccountSwitcherDialog = false
        viewModel.switchUser(targetUserId)
      }
    )
  }
}

@Composable
fun ConversationListItem(
  conversation: Conversation,
  onClick: () -> Unit
) {
  val hasUnread = conversation.unreadCount > 0

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(horizontal = 16.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Avatar with Real-Time Online Status Badge
    Box {
      UserAvatar(
        avatarUrl = conversation.participantAvatar,
        name = conversation.participantName,
        size = 56.dp,
        showOnlineBadge = true,
        isOnline = conversation.isOnline
      )
    }

    // Content: Name, Snippet, Time, Unread Badge
    Column(modifier = Modifier.weight(1f)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f, fill = false)
        ) {
          Text(
            text = conversation.participantName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          if (conversation.isParticipantVerified) {
            Spacer(modifier = Modifier.width(4.dp))
            VerifiedBadge(size = 14.dp)
          }
        }

        Text(
          text = formatRelativeTime(conversation.lastMessageTimestamp),
          style = MaterialTheme.typography.labelSmall,
          color = if (hasUnread) SocivaBlue else MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal
        )
      }

      Spacer(modifier = Modifier.height(3.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
      ) {
        val snippetText = if (conversation.lastMessage.isBlank()) "Started a conversation" else conversation.lastMessage
        Text(
          text = snippetText,
          style = MaterialTheme.typography.bodyMedium,
          color = if (hasUnread) MaterialTheme.colorScheme.onSurface
                  else MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )

        if (hasUnread) {
          Spacer(modifier = Modifier.width(8.dp))
          Box(
            modifier = Modifier
              .size(20.dp)
              .clip(CircleShape)
              .background(SocivaBlue),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "${conversation.unreadCount}",
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
  conversationId: String,
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val currentUser by viewModel.currentUser.collectAsState()
  val currentUserId = currentUser?.id ?: "user_me"

  val conversation by viewModel.getConversation(conversationId).collectAsState(initial = null)
  val messages by viewModel.getConversationMessages(conversationId).collectAsState(initial = emptyList())
  val typingUsers by viewModel.getTypingUsers(conversationId).collectAsState(initial = emptyList())

  var messageText by remember { mutableStateOf("") }
  var selectedMessageForOptions by remember { mutableStateOf<Message?>(null) }
  val listState = rememberLazyListState()

  // Mark messages as seen when entering chat
  LaunchedEffect(conversationId, messages.size) {
    viewModel.markConversationAsRead(conversationId)
  }

  // Scroll to bottom when new messages arrive
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  // Native Photo Picker for sending real photos in chat (Google Play zero-permission)
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    uri?.let {
      viewModel.sendMessage(
        convId = conversationId,
        text = "Sent a photo",
        mediaUrl = it.toString(),
        messageType = "IMAGE"
      )
    }
  }

  val participantName = conversation?.participantName ?: "Chat"
  val isParticipantOnline = conversation?.isOnline == true
  val participantId = conversation?.participantId ?: ""
  val isParticipantBlocked by viewModel.isUserBlockedFlow(participantId).collectAsState(initial = false)

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.clickable {
              conversation?.participantId?.let { uid ->
                viewModel.navigateToProfile(uid)
              }
            }
          ) {
            UserAvatar(
              avatarUrl = conversation?.participantAvatar,
              name = participantName,
              size = 40.dp,
              showOnlineBadge = true,
              isOnline = isParticipantOnline
            )
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = participantName,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                if (conversation?.isParticipantVerified == true) {
                  Spacer(modifier = Modifier.width(4.dp))
                  VerifiedBadge(size = 14.dp)
                }
              }
              Text(
                text = if (isParticipantOnline) "Active now"
                       else if ((conversation?.lastActiveAt ?: 0L) > 0L) "Active ${formatRelativeTime(conversation?.lastActiveAt ?: 0L)}"
                       else "Offline",
                style = MaterialTheme.typography.labelSmall,
                color = if (isParticipantOnline) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = { viewModel.showToast("Starting audio call with $participantName 📞") }) {
            Icon(Icons.Default.Phone, contentDescription = "Audio Call", tint = SocivaBlue)
          }
          IconButton(onClick = { viewModel.showToast("Starting video call with $participantName 📹") }) {
            Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = SocivaBlue)
          }
          IconButton(onClick = {
            conversation?.participantId?.let { uid ->
              viewModel.navigateToProfile(uid)
            }
          }) {
            Icon(Icons.Default.Info, contentDescription = "View Profile", tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    bottomBar = {
      Surface(
        tonalElevation = 4.dp,
        modifier = Modifier
          .fillMaxWidth()
          .imePadding()
      ) {
        if (isParticipantBlocked) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "You have blocked this user. Unblock them to send messages.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error
            )
          }
        } else {
          Column {
            // Real-time Typing Indicator bar
            AnimatedVisibility(
              visible = typingUsers.isNotEmpty(),
              enter = fadeIn(),
              exit = fadeOut()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(SocivaBlue)
                )
                Text(
                  text = "$participantName is typing...",
                  style = MaterialTheme.typography.labelSmall,
                  color = SocivaBlue,
                  fontStyle = FontStyle.Italic
                )
              }
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .navigationBarsPadding(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // Photo Picker Button
              IconButton(
                onClick = {
                  photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                modifier = Modifier.size(38.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Image,
                  contentDescription = "Send photo",
                  tint = SocivaBlue
                )
              }

              // Text input field
              OutlinedTextField(
                value = messageText,
                onValueChange = { newText ->
                  messageText = newText
                  viewModel.setTyping(conversationId, newText.isNotBlank())
                },
                placeholder = { Text("Message...") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                  unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                  focusedBorderColor = Color.Transparent,
                  unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                  .weight(1f)
                  .testTag("chat_input_field"),
                maxLines = 4
              )

              // Send Button or Quick Like Thumbs Up 👍
              if (messageText.isNotBlank()) {
                IconButton(
                  onClick = {
                    val textToSend = messageText
                    messageText = ""
                    viewModel.setTyping(conversationId, false)
                    viewModel.sendMessage(
                      convId = conversationId,
                      text = textToSend,
                      messageType = "TEXT"
                    )
                  },
                  modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                      Brush.horizontalGradient(listOf(SocivaBlue, SocivaIndigo))
                    )
                  .testTag("chat_send_button")
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                  )
                }
              } else {
                IconButton(
                  onClick = {
                    viewModel.sendMessage(
                      convId = conversationId,
                      text = "👍",
                      messageType = "TEXT"
                    )
                  },
                  modifier = Modifier.size(38.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = "Thumbs Up",
                    tint = SocivaBlue
                  )
                }
              }
            }
          }
        }
      }
    }
  ) { innerPadding ->
    LazyColumn(
      state = listState,
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(vertical = 12.dp)
    ) {
      items(messages, key = { it.id }) { msg ->
        val isLastMessage = messages.lastOrNull()?.id == msg.id
        MessageBubble(
          message = msg,
          isLastMessage = isLastMessage,
          participantAvatar = conversation?.participantAvatar,
          onLongClick = {
            selectedMessageForOptions = msg
          }
        )
      }
    }
  }

  // Long press options dialog for a message
  selectedMessageForOptions?.let { msg ->
    AlertDialog(
      onDismissRequest = { selectedMessageForOptions = null },
      title = { Text("Message Options") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (!msg.isDeleted) {
            ListItem(
              headlineContent = { Text("Copy Text") },
              leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
              modifier = Modifier.clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Message", msg.text))
                viewModel.showToast("Message copied")
                selectedMessageForOptions = null
              }
            )
          }

          if (msg.isMine && !msg.isDeleted) {
            ListItem(
              headlineContent = { Text("Unsend for Everyone", color = MaterialTheme.colorScheme.error) },
              leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
              modifier = Modifier.clickable {
                viewModel.softDeleteMessage(msg.id)
                viewModel.showToast("Message unsent")
                selectedMessageForOptions = null
              }
            )
          }

          ListItem(
            headlineContent = { Text("Delete for Me") },
            leadingContent = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
            modifier = Modifier.clickable {
              viewModel.deleteMessage(msg.id)
              viewModel.showToast("Message deleted")
              selectedMessageForOptions = null
            }
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { selectedMessageForOptions = null }) {
          Text("Close")
        }
      }
    )
  }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
  message: Message,
  isLastMessage: Boolean,
  participantAvatar: String?,
  onLongClick: () -> Unit
) {
  val isMine = message.isMine

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
  ) {
    Surface(
      shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
      ),
      color = when {
        message.isDeleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        isMine -> SocivaBlue
        else -> MaterialTheme.colorScheme.surfaceVariant
      },
      modifier = Modifier
        .widthIn(max = 280.dp)
        .combinedClickable(
          onClick = {},
          onLongClick = onLongClick
        )
    ) {
      Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Image attachment if available
        if (!message.mediaUrl.isNullOrBlank() && !message.isDeleted) {
          AsyncImage(
            model = message.mediaUrl,
            contentDescription = "Attached photo",
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 220.dp)
              .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
          )
          Spacer(modifier = Modifier.height(6.dp))
        }

        // Message text
        if (message.isDeleted) {
          Text(
            text = "This message was unsent",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic
          )
        } else if (message.text.isNotEmpty()) {
          Text(
            text = message.text,
            color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(2.dp))

    // Timestamp and Seen Indicator
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Text(
        text = formatRelativeTime(message.timestamp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp
      )

      // Real Facebook Messenger style "Seen" badge
      if (isMine && isLastMessage && message.isSeen && !message.isDeleted) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Text(
            text = "• Seen",
            style = MaterialTheme.typography.labelSmall,
            color = SocivaBlue,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
          )
          if (!participantAvatar.isNullOrBlank()) {
            AsyncImage(
              model = participantAvatar,
              contentDescription = "Seen by recipient",
              modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
            )
          }
        }
      }
    }
  }
}

@Composable
fun NewMessageDialog(
  allUsers: List<User>,
  onDismiss: () -> Unit,
  onSelectUser: (User) -> Unit
) {
  var searchQuery by remember { mutableStateOf("") }
  val filteredUsers = remember(allUsers, searchQuery) {
    if (searchQuery.isBlank()) allUsers
    else allUsers.filter {
      it.fullName.contains(searchQuery, ignoreCase = true) ||
      it.username.contains(searchQuery, ignoreCase = true)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "New Message",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 400.dp)
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("To: Type a name or username") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
          singleLine = true
        )

        Text(
          text = "Suggested People",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(filteredUsers, key = { it.id }) { user ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onSelectUser(user) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              UserAvatar(
                avatarUrl = user.avatarUrl,
                name = user.fullName,
                size = 44.dp,
                showOnlineBadge = true,
                isOnline = user.isOnline
              )

              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = user.fullName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                  )
                  if (user.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 14.dp)
                  }
                }
                Text(
                  text = "@${user.username}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Chat",
                tint = SocivaBlue,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
fun AccountSwitcherDialog(
  users: List<User>,
  currentUserId: String,
  onDismiss: () -> Unit,
  onSelectUser: (String) -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column {
        Text(
          text = "Switch Active Account",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "Select a registered user to test real-time 2-way messaging across Spark accounts.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    text = {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 360.dp)
      ) {
        items(users, key = { it.id }) { user ->
          val isCurrent = user.id == currentUserId
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isCurrent) SocivaBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.5.dp, SocivaBlue) else null,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSelectUser(user.id) }
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              UserAvatar(
                avatarUrl = user.avatarUrl,
                name = user.fullName,
                size = 46.dp,
                showOnlineBadge = true,
                isOnline = user.isOnline
              )

              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = user.fullName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                  )
                  if (user.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 14.dp)
                  }
                }
                Text(
                  text = "@${user.username} • ${if (user.isOnline) "Online" else "Offline"}",
                  style = MaterialTheme.typography.bodySmall,
                  color = if (user.isOnline) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              if (isCurrent) {
                Surface(
                  shape = CircleShape,
                  color = SocivaBlue,
                  modifier = Modifier.size(24.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = "Active",
                      tint = Color.White,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    }
  )
}
