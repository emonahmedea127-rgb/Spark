package com.example.sociva.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sociva.data.model.FriendStatus
import com.example.sociva.data.model.User
import com.example.sociva.data.service.UploadState
import com.example.sociva.ui.SocivaScreen
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.*
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaIndigo
import com.example.ui.theme.SocivaPurple
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
  userId: String,
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  val user by viewModel.getUser(userId).collectAsState(initial = null)
  val currentUser by viewModel.currentUser.collectAsState()
  val userPosts by viewModel.getPostsByUser(userId).collectAsState(initial = emptyList())
  val taggedPosts by viewModel.getTaggedPostsByUser(userId).collectAsState(initial = emptyList())
  val incomingRelationshipRequests by viewModel.incomingRelationshipRequests.collectAsState()
  val uploadState by viewModel.uploadState.collectAsState()

  var selectedTab by remember { mutableStateOf(0) } // 0: Posts, 1: About, 2: Photos, 3: Tagged

  // Photo Management States
  var showAvatarActionSheet by remember { mutableStateOf(false) }
  var showCoverActionSheet by remember { mutableStateOf(false) }
  var showAvatarLightbox by remember { mutableStateOf(false) }
  var showCoverLightbox by remember { mutableStateOf(false) }
  var showRemoveAvatarConfirm by remember { mutableStateOf(false) }
  var showRemoveCoverConfirm by remember { mutableStateOf(false) }
  var showPresetPicker by remember { mutableStateOf(false) }
  var isPresetForCover by remember { mutableStateOf(false) }

  var croppingBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var currentCropType by remember { mutableStateOf(CropType.PROFILE) }

  val isMyProfile = (userId == currentUser?.id || userId == "user_me")
  val isUserBlocked by viewModel.isUserBlockedFlow(userId).collectAsState(initial = false)
  val friendStatus by viewModel.getFriendStatusFlow(userId).collectAsState(initial = FriendStatus.NONE)
  val isFollowing by viewModel.isFollowingFlow(userId).collectAsState(initial = false)
  val profileViewStats by viewModel.getProfileViewStatsForUser(userId).collectAsState(initial = com.example.sociva.data.model.ProfileViewStats())

  LaunchedEffect(userId) {
    if (!isMyProfile) {
      viewModel.recordProfileVisit(userId)
    }
  }

  var showCancelRequestDialog by remember { mutableStateOf(false) }
  var showRemoveFriendDialog by remember { mutableStateOf(false) }
  var showBlockDialog by remember { mutableStateOf(false) }
  var showUnblockDialog by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      coroutineScope.launch {
        try {
          val inputStream = context.contentResolver.openInputStream(uri)
          val bitmap = BitmapFactory.decodeStream(inputStream)
          inputStream?.close()
          if (bitmap != null) {
            croppingBitmap = bitmap
          } else {
            viewModel.showToast("Could not decode selected image file.")
          }
        } catch (e: Exception) {
          viewModel.showToast("Failed to open image: ${e.localizedMessage}")
        }
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = user?.fullName ?: "Profile",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = { viewModel.showToast("Profile link copied to clipboard!") }) {
            Icon(Icons.Default.Share, contentDescription = "Share Profile")
          }
          if (!isMyProfile) {
            var showMenu by remember { mutableStateOf(false) }
            IconButton(onClick = { showMenu = true }) {
              Icon(Icons.Default.MoreVert, contentDescription = "More Options")
            }
            DropdownMenu(
              expanded = showMenu,
              onDismissRequest = { showMenu = false }
            ) {
              if (isUserBlocked) {
                DropdownMenuItem(
                  text = { Text("Unblock User") },
                  onClick = {
                    showMenu = false
                    showUnblockDialog = true
                  },
                  leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                )
              } else {
                DropdownMenuItem(
                  text = { Text("Block User", color = MaterialTheme.colorScheme.error) },
                  onClick = {
                    showMenu = false
                    showBlockDialog = true
                  },
                  leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )
              }
            }
          }
        }
      )
    }
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .testTag("profile_screen"),
      contentPadding = PaddingValues(bottom = 60.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // 1. Cover Photo and Avatar Header
      item {
        Box(modifier = Modifier.fillMaxWidth()) {
          // Cover Photo Container
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp)
              .clickable {
                if (isMyProfile) {
                  showCoverActionSheet = true
                } else {
                  showCoverLightbox = true
                }
              }
              .testTag("cover_photo_container")
          ) {
            if (!user?.coverUrl.isNullOrBlank()) {
              AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                  .data(user?.coverUrl)
                  .crossfade(true)
                  .build(),
                contentDescription = "Cover photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            } else {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(Brush.horizontalGradient(listOf(SocivaBlue, SocivaPurple))),
                contentAlignment = Alignment.Center
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Landscape,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                  )
                  Text(
                    text = "Add cover photo",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                  )
                }
              }
            }

            // Cover Photo Camera / Edit Button (Bottom-Right of Cover)
            if (isMyProfile) {
              Surface(
                onClick = { showCoverActionSheet = true },
                shape = CircleShape,
                color = Color(0xCC0F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                modifier = Modifier
                  .align(Alignment.BottomEnd)
                  .padding(end = 16.dp, bottom = 12.dp)
                  .size(38.dp)
                  .testTag("edit_cover_button")
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Change cover photo",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }

          // Large Profile Picture overlapping cover bottom
          Box(
            modifier = Modifier
              .padding(top = 118.dp, start = 16.dp)
              .size(108.dp)
          ) {
            // Main Circular Avatar
            Surface(
              onClick = {
                if (isMyProfile) {
                  showAvatarActionSheet = true
                } else {
                  showAvatarLightbox = true
                }
              },
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surface,
              shadowElevation = 4.dp,
              modifier = Modifier
                .size(100.dp)
                .align(Alignment.TopStart)
                .testTag("profile_avatar_view")
            ) {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(3.dp)
              ) {
                UserAvatar(
                  avatarUrl = user?.avatarUrl,
                  name = user?.fullName ?: "U",
                  size = 94.dp
                )
              }
            }

            // Small circular Camera button on bottom-right of profile picture
            if (isMyProfile) {
              Surface(
                onClick = { showAvatarActionSheet = true },
                shape = CircleShape,
                color = SocivaIndigo,
                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                shadowElevation = 3.dp,
                modifier = Modifier
                  .align(Alignment.BottomEnd)
                  .padding(end = 4.dp, bottom = 4.dp)
                  .size(34.dp)
                  .testTag("edit_avatar_button")
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Edit profile picture",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
            }
          }
        }
      }

      // 2. User Info & Actions
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = user?.fullName ?: "",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold
            )
            if (user?.isVerified == true) {
              Spacer(modifier = Modifier.width(6.dp))
              VerifiedBadge(size = 18.dp)
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "@${user?.username ?: ""}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!user?.pronouns.isNullOrBlank()) {
              Text(
                text = "(${user?.pronouns})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
              )
            }
            if (!user?.nickname.isNullOrBlank()) {
              Text(
                text = "• \"${user?.nickname}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          if (!user?.otherNames.isNullOrBlank()) {
            Text(
              text = "Also known as: ${user?.otherNames}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          if (!user?.bio.isNullOrBlank()) {
            Text(
              text = user?.bio ?: "",
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(vertical = 4.dp)
            )
          }

          // Relationship Status Badge
          val canSeeRelationship = isMyProfile || when (user?.relationshipPrivacy) {
            "Public" -> true
            "Friends" -> friendStatus == FriendStatus.FRIENDS
            "Only me" -> false
            else -> true
          }
          if (canSeeRelationship && !user?.relationshipStatus.isNullOrBlank()) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFFFDF2F8),
              modifier = Modifier.padding(vertical = 2.dp)
            ) {
              Row(
                modifier = Modifier
                  .clickable(enabled = !user?.relationshipPartnerId.isNullOrBlank()) {
                    user?.relationshipPartnerId?.let { partnerId ->
                      viewModel.navigateToProfile(partnerId)
                    }
                  }
                  .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFEC4899), modifier = Modifier.size(16.dp))
                Text(
                  text = buildString {
                    append(user?.relationshipStatus)
                    if (!user?.relationshipPartnerName.isNullOrBlank()) {
                      append(" with ")
                      append(user?.relationshipPartnerName)
                    }
                  },
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFFBE185D)
                )
              }
            }
          }

          // Incoming Relationship Request Banner (For current user)
          if (isMyProfile && incomingRelationshipRequests.isNotEmpty()) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              incomingRelationshipRequests.forEach { req ->
                Card(
                  colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE7F3)),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFEC4899), modifier = Modifier.size(18.dp))
                      Text(
                        text = "Relationship Request",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFBE185D)
                      )
                    }
                    Text(
                      text = "${req.requesterName} requested to list you as their partner (${req.relationshipType}).",
                      style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      Button(
                        onClick = { viewModel.acceptRelationshipRequest(req.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                      ) {
                        Text("Accept", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                      }
                      OutlinedButton(
                        onClick = { viewModel.declineRelationshipRequest(req.id) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                      ) {
                        Text("Decline", fontSize = 12.sp)
                      }
                    }
                  }
                }
              }
            }
          }

          // Stats Counter Row: Followers, Following, Friends, Posts
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
          ) {
            ProfileStat(label = "Posts", count = "${userPosts.size}")
            ProfileStat(label = "Followers", count = "${user?.followersCount ?: 0}")
            ProfileStat(label = "Following", count = "${user?.followingCount ?: 0}")
            ProfileStat(label = "Friends", count = "${user?.friendsCount ?: 0}")
          }

          // Profile Views Card
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("profile_views_section")
              .clip(RoundedCornerShape(12.dp))
              .clickable {
                if (isMyProfile) {
                  viewModel.openProfileVisitors()
                } else {
                  viewModel.showToast("Visitor history is private to the profile owner.")
                }
              },
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            shape = RoundedCornerShape(12.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    Icons.Default.Visibility,
                    contentDescription = "Profile Views",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                  )
                }
                Column {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Text(
                      text = "Profile Views",
                      fontWeight = FontWeight.SemiBold,
                      fontSize = 14.sp,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isMyProfile && profileViewStats.unseenCount > 0) {
                      Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                      ) {
                        Text(
                          text = "${profileViewStats.unseenCount} new",
                          color = MaterialTheme.colorScheme.onPrimary,
                          fontSize = 10.sp,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                      }
                    }
                  }
                  Text(
                    text = if (isMyProfile) "Tap to view visitor history" else "Total visits recorded",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = "${profileViewStats.totalCount}",
                  fontSize = 18.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
                if (isMyProfile) {
                  Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }
            }
          }

          // Primary Action Buttons
          if (isMyProfile) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = { viewModel.navigateTo(SocivaScreen.EDIT_PROFILE) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("edit_profile_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue)
              ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Profile", fontWeight = FontWeight.Bold)
              }

              OutlinedButton(
                onClick = { viewModel.navigateTo(SocivaScreen.ANALYTICS) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("profile_analytics_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SocivaIndigo)
              ) {
                Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Analytics", fontWeight = FontWeight.Bold)
              }
            }
          } else if (isUserBlocked) {
            // User is currently blocked
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
              shape = RoundedCornerShape(12.dp)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                  Column {
                    Text("User Blocked", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("You have blocked this account.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                  }
                }
                Button(
                  onClick = { showUnblockDialog = true },
                  colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.testTag("profile_unblock_button")
                ) {
                  Text("Unblock", fontWeight = FontWeight.SemiBold)
                }
              }
            }
          } else {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                // 1. Friend Action Button (NONE, REQUEST_SENT, REQUEST_RECEIVED, FRIENDS)
                when (friendStatus) {
                  FriendStatus.NONE -> {
                    Button(
                      onClick = { viewModel.sendFriendRequest(userId) },
                      modifier = Modifier
                        .weight(1f)
                        .testTag("add_friend_button"),
                      shape = RoundedCornerShape(12.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue)
                    ) {
                      Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Add Friend", fontWeight = FontWeight.Bold)
                    }
                  }
                  FriendStatus.REQUEST_SENT -> {
                    FilledTonalButton(
                      onClick = { showCancelRequestDialog = true },
                      modifier = Modifier
                        .weight(1f)
                        .testTag("request_sent_button"),
                      shape = RoundedCornerShape(12.dp),
                      colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                      )
                    ) {
                      Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Request Sent", fontWeight = FontWeight.SemiBold)
                    }
                  }
                  FriendStatus.REQUEST_RECEIVED -> {
                    Row(
                      modifier = Modifier.weight(1f),
                      horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      Button(
                        onClick = {
                          val req = viewModel.friendRequests.value.find { it.senderId == userId }
                          if (req != null) {
                            viewModel.acceptFriendRequest(req)
                          } else {
                            viewModel.acceptFriendRequestById(userId)
                          }
                        },
                        modifier = Modifier
                          .weight(1f)
                          .testTag("accept_friend_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                      ) {
                        Text("Accept", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                      }
                      FilledTonalButton(
                        onClick = {
                          val req = viewModel.friendRequests.value.find { it.senderId == userId }
                          if (req != null) {
                            viewModel.rejectFriendRequest(req.id)
                          }
                        },
                        modifier = Modifier
                          .weight(1f)
                          .testTag("reject_friend_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                      ) {
                        Text("Delete", fontSize = 13.sp)
                      }
                    }
                  }
                  FriendStatus.FRIENDS -> {
                    FilledTonalButton(
                      onClick = { showRemoveFriendDialog = true },
                      modifier = Modifier
                        .weight(1f)
                        .testTag("friends_button"),
                      shape = RoundedCornerShape(12.dp)
                    ) {
                      Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = SocivaBlue)
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Friends", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                  }
                }

                // 2. Message Button
                OutlinedButton(
                  onClick = { viewModel.openOrCreateConversationWithUser(userId) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("message_button"),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Message")
                }
              }

              // 3. Follow / Following Button (Completely independent from Friendship!)
              if (isFollowing) {
                OutlinedButton(
                  onClick = { viewModel.unfollowUser(userId) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("following_button"),
                  shape = RoundedCornerShape(12.dp),
                  colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                ) {
                  Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Following", fontWeight = FontWeight.Medium)
                }
              } else {
                Button(
                  onClick = { viewModel.followUser(userId) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("follow_button"),
                  shape = RoundedCornerShape(12.dp),
                  colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                  )
                ) {
                  Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Follow", fontWeight = FontWeight.SemiBold)
                }
              }
            }
          }
        }
      }

      // 3. Tab Row: Posts, About, Photos, Tagged
      item {
        TabRow(
          selectedTabIndex = selectedTab,
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = 8.dp)
        ) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("Posts", fontWeight = FontWeight.Bold) }
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("About", fontWeight = FontWeight.Bold) }
          )
          Tab(
            selected = selectedTab == 2,
            onClick = { selectedTab = 2 },
            text = { Text("Photos", fontWeight = FontWeight.Bold) }
          )
          Tab(
            selected = selectedTab == 3,
            onClick = { selectedTab = 3 },
            text = { Text("Tagged (${taggedPosts.size})", fontWeight = FontWeight.Bold) }
          )
        }
      }

      // 4. Tab Content
      when (selectedTab) {
        0 -> {
          // Posts
          if (userPosts.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(32.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "No posts yet.",
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          } else {
            items(userPosts, key = { it.id }) { post ->
              LaunchedEffect(post.id) {
                viewModel.recordPostView(post.id)
              }

              PostCard(
                post = post,
                currentUser = currentUser,
                onReaction = { reaction -> viewModel.setReaction(post.id, reaction) },
                onCommentClick = { viewModel.openComments(post.id) },
                onShareClick = { viewModel.openShareComposer(post) },
                onSaveClick = { viewModel.toggleSavePost(post.id) },
                onAuthorClick = { viewModel.navigateToProfile(post.authorId) },
                onSharedAuthorClick = { origAuthorId -> viewModel.navigateToProfile(origAuthorId) },
                onDeleteClick = { viewModel.deletePost(post.id) },
                onEditClick = { viewModel.openEditPost(post) },
                onReportClick = {
                  viewModel.submitReport("Post", post.id, post.content.take(30), "Inappropriate")
                },
                onRemoveTagClick = { viewModel.removePostTag(post.id) },
                onReactionsClick = { viewModel.openReactionsModal(post.id) },
                onAnalyticsClick = { viewModel.openPostAnalytics(post.id) }
              )
            }
          }
        }

        1 -> {
          // Comprehensive Facebook-Style About Section with Privacy Enforcement
          val isFriend = friendStatus == FriendStatus.FRIENDS
          fun canView(privacy: String): Boolean =
            isMyProfile || when (privacy) {
              "Public" -> true
              "Friends" -> isFriend
              "Only me" -> false
              else -> true
            }

          item {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              // Section A: Work & Education
              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Text("Work & Education", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                  val displayWork = user?.workplace?.ifBlank { null } ?: user?.work
                  if (!displayWork.isNullOrBlank()) {
                    val workDesc = buildString {
                      if (!user?.workPosition.isNullOrBlank()) append("${user?.workPosition} at ")
                      append(displayWork)
                      if (!user?.workStartDate.isNullOrBlank()) {
                        append(" (${user?.workStartDate} - ${user?.workEndDate?.ifBlank { "Present" } ?: "Present"})")
                      }
                    }
                    AboutInfoRow(icon = Icons.Default.Work, title = "Work", value = workDesc)
                  }

                  val college = user?.college?.ifBlank { null } ?: user?.university?.ifBlank { null } ?: user?.education
                  if (!college.isNullOrBlank()) {
                    val collegeDesc = buildString {
                      if (!user?.degree.isNullOrBlank()) append("${user?.degree}, ")
                      if (!user?.fieldOfStudy.isNullOrBlank()) append("${user?.fieldOfStudy} at ")
                      append(college)
                      if (!user?.graduationYear.isNullOrBlank()) append(" (Class of ${user?.graduationYear})")
                    }
                    AboutInfoRow(icon = Icons.Default.School, title = "College / University", value = collegeDesc)
                  }

                  if (!user?.school.isNullOrBlank()) {
                    AboutInfoRow(icon = Icons.Default.School, title = "School", value = user?.school ?: "")
                  }

                  if (displayWork.isNullOrBlank() && college.isNullOrBlank() && user?.school.isNullOrBlank()) {
                    Text("No work or education places to show", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }

              // Section B: Places Lived (Privacy Checked)
              if (canView(user?.currentCityPrivacy ?: "Public")) {
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(16.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                  Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    Text("Places Lived", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    val currentCityFormatted = buildString {
                      val baseCity = user?.currentCity?.ifBlank { null } ?: user?.location?.ifBlank { null }
                      if (!baseCity.isNullOrBlank()) {
                        append(baseCity)
                        val extra = listOfNotNull(
                          user?.currentRegion?.ifBlank { null },
                          user?.currentCountryCode?.ifBlank { null }
                        ).joinToString(", ")
                        if (extra.isNotBlank() && !baseCity.contains(extra)) {
                          append(" ($extra)")
                        }
                      }
                    }
                    if (currentCityFormatted.isNotBlank()) {
                      AboutInfoRow(icon = Icons.Default.Home, title = "Lives in", value = currentCityFormatted)
                    }

                    val hometownFormatted = buildString {
                      if (!user?.hometown.isNullOrBlank()) {
                        append(user?.hometown)
                        val extra = listOfNotNull(
                          user?.hometownRegion?.ifBlank { null },
                          user?.hometownCountryCode?.ifBlank { null }
                        ).joinToString(", ")
                        if (extra.isNotBlank() && !user?.hometown!!.contains(extra)) {
                          append(" ($extra)")
                        }
                      }
                    }
                    if (hometownFormatted.isNotBlank()) {
                      AboutInfoRow(icon = Icons.Default.LocationOn, title = "From (Hometown)", value = hometownFormatted)
                    }

                    if (!user?.country.isNullOrBlank()) {
                      val flag = CountryHelper.getFlagForCountry(user?.country ?: "")
                      val countryValue = if (flag.isNotBlank()) "$flag ${user?.country}" else user?.country ?: ""
                      AboutInfoRow(icon = Icons.Default.Public, title = "Country", value = countryValue)
                    }

                    if (currentCityFormatted.isBlank() && hometownFormatted.isBlank() && user?.country.isNullOrBlank()) {
                      Text("No places to show", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                }
              }

              // Section C: Relationship Status (Privacy Checked)
              if (canView(user?.relationshipPrivacy ?: "Public") && !user?.relationshipStatus.isNullOrBlank()) {
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(16.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                  Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    Text("Relationship", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    val relValue = buildString {
                      append(user?.relationshipStatus)
                      if (!user?.relationshipPartnerName.isNullOrBlank()) {
                        append(" with ${user?.relationshipPartnerName}")
                      }
                      if (!user?.customRelationshipText.isNullOrBlank()) {
                        append(" • ${user?.customRelationshipText}")
                      }
                    }
                    AboutInfoRow(icon = Icons.Default.Favorite, title = "Status", value = relValue)
                  }
                }
              }

              // Section D: Basic & Demographic Info
              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Text("Basic Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                  if (canView(user?.birthdayPrivacy ?: "Public") && !user?.dateOfBirth.isNullOrBlank()) {
                    AboutInfoRow(icon = Icons.Default.Cake, title = "Birthdate", value = user?.dateOfBirth ?: "")
                  }

                  if (!user?.gender.isNullOrBlank()) {
                    AboutInfoRow(icon = Icons.Default.Person, title = "Gender", value = user?.gender ?: "")
                  }

                  if (!user?.interestedIn.isNullOrBlank()) {
                    AboutInfoRow(icon = Icons.Default.FavoriteBorder, title = "Interested in", value = user?.interestedIn ?: "")
                  }

                  if (!user?.pronouns.isNullOrBlank()) {
                    AboutInfoRow(icon = Icons.Default.Badge, title = "Pronouns", value = user?.pronouns ?: "")
                  }

                  if (!user?.joinedDate.isNullOrBlank()) {
                    AboutInfoRow(
                      icon = Icons.Default.CalendarToday,
                      title = "Joined Spark",
                      value = user?.joinedDate ?: ""
                    )
                  }
                }
              }

              // Section E: Contact Info
              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Text("Contact Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                  if (canView(user?.emailPrivacy ?: "Public") && !user?.email.isNullOrBlank()) {
                    AboutInfoRow(icon = Icons.Default.Email, title = "Email", value = user?.email ?: "")
                  }

                  if (!user?.phone.isNullOrBlank()) {
                    AboutInfoRow(icon = Icons.Default.Phone, title = "Phone", value = user?.phone ?: "")
                  }

                  if (!user?.website.isNullOrBlank()) {
                    AboutInfoRow(icon = Icons.Default.Link, title = "Website", value = user?.website ?: "")
                  }
                }
              }
            }
          }
        }

        2 -> {
          // Photos Grid
          item {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
              Text(
                text = "Photos (${userPosts.flatMap { it.mediaUrls }.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
              )

              val allPhotos = userPosts.flatMap { it.mediaUrls }
              if (allPhotos.isEmpty()) {
                Text(
                  text = "No photos uploaded yet.",
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              } else {
                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  allPhotos.take(3).forEach { photoUrl ->
                    AsyncImage(
                      model = ImageRequest.Builder(LocalContext.current)
                        .data(photoUrl)
                        .crossfade(true)
                        .build(),
                      contentDescription = "Photo",
                      contentScale = ContentScale.Crop,
                      modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                    )
                  }
                }
              }
            }
          }
        }

        3 -> {
          // Tagged Posts
          if (taggedPosts.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(32.dp),
                contentAlignment = Alignment.Center
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Icon(
                    Icons.Default.Label,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                  )
                  Text(
                    text = "No tagged posts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          } else {
            items(taggedPosts, key = { it.id }) { post ->
              LaunchedEffect(post.id) {
                viewModel.recordPostView(post.id)
              }

              PostCard(
                post = post,
                currentUser = currentUser,
                onReaction = { reaction -> viewModel.setReaction(post.id, reaction) },
                onCommentClick = { viewModel.openComments(post.id) },
                onShareClick = { viewModel.sharePost(post.id) },
                onSaveClick = { viewModel.toggleSavePost(post.id) },
                onAuthorClick = { viewModel.navigateToProfile(post.authorId) },
                onDeleteClick = { viewModel.deletePost(post.id) },
                onReportClick = {
                  viewModel.submitReport("Post", post.id, post.content.take(30), "Inappropriate")
                },
                onRemoveTagClick = { viewModel.removePostTag(post.id) },
                onReactionsClick = { viewModel.openReactionsModal(post.id) },
                onAnalyticsClick = { viewModel.openPostAnalytics(post.id) }
              )
            }
          }
        }
      }
    }
  }

  // 1. Profile Picture Action Sheet
  if (showAvatarActionSheet) {
    ProfilePictureActionSheet(
      onDismiss = { showAvatarActionSheet = false },
      onChooseFromDevice = {
        currentCropType = CropType.PROFILE
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      },
      onChoosePreset = {
        currentCropType = CropType.PROFILE
        isPresetForCover = false
        showPresetPicker = true
      },
      onViewPhoto = { showAvatarLightbox = true },
      onRemovePhoto = { showRemoveAvatarConfirm = true },
      hasExistingPhoto = !user?.avatarUrl.isNullOrBlank()
    )
  }

  // 2. Cover Photo Action Sheet
  if (showCoverActionSheet) {
    CoverPhotoActionSheet(
      onDismiss = { showCoverActionSheet = false },
      onChooseFromDevice = {
        currentCropType = CropType.COVER
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      },
      onChoosePreset = {
        currentCropType = CropType.COVER
        isPresetForCover = true
        showPresetPicker = true
      },
      onViewPhoto = { showCoverLightbox = true },
      onRemovePhoto = { showRemoveCoverConfirm = true },
      hasExistingPhoto = !user?.coverUrl.isNullOrBlank()
    )
  }

  // 3. Image Cropper Modal (Pan, Zoom, Rotate, Crop)
  if (croppingBitmap != null) {
    ImageCropperModal(
      sourceBitmap = croppingBitmap!!,
      cropType = currentCropType,
      onCropCompleted = { cropped ->
        croppingBitmap = null
        if (currentCropType == CropType.PROFILE) {
          viewModel.uploadAndSetProfilePicture(cropped)
        } else {
          viewModel.uploadAndSetCoverPhoto(cropped)
        }
      },
      onDismiss = { croppingBitmap = null }
    )
  }

  // 4. Preset Gallery Picker Dialog
  if (showPresetPicker) {
    PresetPhotoPickerDialog(
      isCover = isPresetForCover,
      onPhotoSelected = { bitmap ->
        showPresetPicker = false
        croppingBitmap = bitmap
      },
      onDismiss = { showPresetPicker = false }
    )
  }

  // 5. Fullscreen Lightbox Photo Viewer (Profile Picture)
  if (showAvatarLightbox && user != null) {
    PhotoLightboxViewer(
      imageUrl = user?.avatarUrl,
      user = user!!,
      isCover = false,
      isOwner = isMyProfile,
      onDismiss = { showAvatarLightbox = false },
      onEditClick = { showAvatarActionSheet = true },
      onShareClick = { viewModel.showToast("Profile photo link shared! 🔗") },
      onDownloadClick = { viewModel.showToast("Profile picture saved to gallery 💾") }
    )
  }

  // 6. Fullscreen Lightbox Photo Viewer (Cover Photo)
  if (showCoverLightbox && user != null) {
    PhotoLightboxViewer(
      imageUrl = user?.coverUrl,
      user = user!!,
      isCover = true,
      isOwner = isMyProfile,
      onDismiss = { showCoverLightbox = false },
      onEditClick = { showCoverActionSheet = true },
      onShareClick = { viewModel.showToast("Cover photo link shared! 🔗") },
      onDownloadClick = { viewModel.showToast("Cover photo saved to gallery 💾") }
    )
  }

  // 7. Remove Profile Picture Confirmation
  if (showRemoveAvatarConfirm && user != null) {
    RemovePhotoConfirmDialog(
      title = "Remove Profile Picture?",
      message = "Are you sure you want to remove your profile picture? Your profile will display Spark's default avatar.",
      onConfirm = {
        showRemoveAvatarConfirm = false
        viewModel.removeProfilePicture(user!!.id)
      },
      onDismiss = { showRemoveAvatarConfirm = false }
    )
  }

  // 8. Remove Cover Photo Confirmation
  if (showRemoveCoverConfirm && user != null) {
    RemovePhotoConfirmDialog(
      title = "Remove Cover Photo?",
      message = "Are you sure you want to remove your cover photo? The default gradient cover will be restored.",
      onConfirm = {
        showRemoveCoverConfirm = false
        viewModel.removeCoverPhoto(user!!.id)
      },
      onDismiss = { showRemoveCoverConfirm = false }
    )
  }

  // 9. Upload Progress & Status Dialog
  if (uploadState !is UploadState.Idle) {
    UploadProgressDialog(
      state = uploadState,
      onRetry = { viewModel.retryLastUpload() },
      onDismiss = { viewModel.dismissUploadState() }
    )
  }

  // 10. Cancel Friend Request Dialog
  if (showCancelRequestDialog) {
    AlertDialog(
      onDismissRequest = { showCancelRequestDialog = false },
      title = { Text("Cancel Friend Request") },
      text = { Text("Do you want to cancel the friend request sent to ${user?.fullName ?: "this user"}? You will still be following them.") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.cancelFriendRequest(userId)
            showCancelRequestDialog = false
          }
        ) {
          Text("Cancel Request", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showCancelRequestDialog = false }) {
          Text("Keep Request")
        }
      }
    )
  }

  // 11. Remove Friend Dialog
  if (showRemoveFriendDialog) {
    AlertDialog(
      onDismissRequest = { showRemoveFriendDialog = false },
      title = { Text("Remove from Friends") },
      text = { Text("Are you sure you want to remove ${user?.fullName ?: "this user"} from your friends? You will still remain connected as a follower unless you choose to unfollow.") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.removeFriend(userId)
            showRemoveFriendDialog = false
          }
        ) {
          Text("Remove Friend", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showRemoveFriendDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // 12. Block User Confirmation Dialog
  if (showBlockDialog) {
    AlertDialog(
      onDismissRequest = { showBlockDialog = false },
      title = { Text("Block ${user?.fullName ?: "User"}?") },
      text = {
        Text("They will no longer be able to:\n• See your posts or stories\n• Send you friend requests or messages\n• Tag you in posts\n\nBlocking also automatically cancels friendships and unfollows both ways.")
      },
      confirmButton = {
        Button(
          onClick = {
            showBlockDialog = false
            viewModel.blockUser(userId)
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text("Block", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showBlockDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // 13. Unblock User Confirmation Dialog
  if (showUnblockDialog) {
    AlertDialog(
      onDismissRequest = { showUnblockDialog = false },
      title = { Text("Unblock ${user?.fullName ?: "User"}?") },
      text = {
        Text("They will be able to see your public posts, search for your profile, and send you friend requests or messages.")
      },
      confirmButton = {
        Button(
          onClick = {
            showUnblockDialog = false
            viewModel.unblockUser(userId)
          },
          colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue)
        ) {
          Text("Unblock", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showUnblockDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
fun ProfileStat(label: String, count: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = count,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
fun AboutInfoRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  value: String
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp)
    )
    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
      )
    }
  }
}


