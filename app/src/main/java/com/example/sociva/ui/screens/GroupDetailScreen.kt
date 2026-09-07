package com.example.sociva.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sociva.data.model.*
import com.example.sociva.ui.SocivaScreen
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaIndigo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  val activeGroup by viewModel.activeGroup.collectAsState()
  val activeIdentity by viewModel.activeIdentity.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  val group = activeGroup
  if (group == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  val isOwnerOrAdmin = group.role in listOf("Owner", "Admin", "Moderator") || group.ownerId == (currentUser?.id ?: "user_me")
  val isActingAsThisGroup = activeIdentity.type == IdentityType.GROUP && activeIdentity.id == group.id

  val groupPosts by viewModel.getGroupPosts(group.id).collectAsState(initial = emptyList())
  val groupMembers by viewModel.getGroupMembers(group.id).collectAsState(initial = emptyList())

  var selectedTab by remember { mutableStateOf(0) } // 0: Discussion, 1: Members, 2: About & Rules
  val tabs = listOf("Discussion", "Members", "About & Rules")

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = group.name,
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.titleMedium
            )
            Text(
              text = "${group.privacy} Group • ${group.membersCount} members",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("group_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (isOwnerOrAdmin) {
            IconButton(
              onClick = { viewModel.openGroupManagement(group.id) },
              modifier = Modifier.testTag("group_manage_settings_button")
            ) {
              Icon(Icons.Default.AdminPanelSettings, contentDescription = "Manage Group")
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
    ) {
      // Header: Cover Photo & Avatar
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
        ) {
          AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
              .data(group.coverUrl)
              .crossfade(true)
              .build(),
            contentDescription = "Cover Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxWidth()
              .height(160.dp)
          )

          // Avatar overlaid
          Box(
            modifier = Modifier
              .align(Alignment.BottomStart)
              .padding(start = 16.dp)
          ) {
            Surface(
              shape = RoundedCornerShape(16.dp),
              border = androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.surface),
              shadowElevation = 4.dp
            ) {
              UserAvatar(
                avatarUrl = group.avatarUrl,
                name = group.name,
                size = 80.dp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Group Details
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
          Text(
            text = group.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
          )

          Spacer(modifier = Modifier.height(4.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = if (group.privacy.contains("Private", ignoreCase = true)) Icons.Default.Lock else Icons.Default.Public,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "${group.privacy} Group • ${group.membersCount} members",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (group.role.isNotBlank()) {
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = SocivaBlue.copy(alpha = 0.12f)
              ) {
                Text(
                  text = group.role,
                  style = MaterialTheme.typography.labelSmall,
                  color = SocivaBlue,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }

          if (group.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = group.description,
              style = MaterialTheme.typography.bodyMedium
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Identity Banner if acting as this group
          if (isActingAsThisGroup) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "You're posting as ${group.name}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6D28D9)
                  )
                }
                TextButton(
                  onClick = { viewModel.resetToPersonalIdentity() },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                  Text("Switch back", color = SocivaBlue, style = MaterialTheme.typography.labelMedium)
                }
              }
            }
            Spacer(modifier = Modifier.height(12.dp))
          }

          // Action Buttons
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            if (group.isJoined) {
              Button(
                onClick = {
                  viewModel.switchIdentity(
                    ActiveIdentity(
                      id = group.id,
                      name = group.name,
                      username = group.name.lowercase().replace(" ", ""),
                      avatarUrl = group.avatarUrl,
                      type = IdentityType.GROUP,
                      badge = group.role
                    )
                  )
                  viewModel.navigateTo(SocivaScreen.CREATE_POST)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue)
              ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Post in Group")
              }

              if (isOwnerOrAdmin) {
                OutlinedButton(
                  onClick = { viewModel.openGroupManagement(group.id) },
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Admin Tools")
                }
              }

              OutlinedButton(
                onClick = { viewModel.joinOrLeaveGroup(group.id) },
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Joined")
              }
            } else {
              Button(
                onClick = { viewModel.joinOrLeaveGroup(group.id) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue)
              ) {
                Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (group.privacy.contains("Private", ignoreCase = true)) "Request to Join" else "Join Group")
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Tab Row
          TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = SocivaBlue
          ) {
            tabs.forEachIndexed { index, title ->
              Tab(
                selected = selectedTab == index,
                onClick = { selectedTab = index },
                text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
              )
            }
          }
        }
      }

      // Tab Contents
      when (selectedTab) {
        0 -> { // Discussion Tab
          if (group.isJoined) {
            item {
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp)
                  .clickable {
                    viewModel.switchIdentity(
                      ActiveIdentity(
                        id = group.id,
                        name = group.name,
                        username = group.name.lowercase().replace(" ", ""),
                        avatarUrl = group.avatarUrl,
                        type = IdentityType.GROUP,
                        badge = group.role
                      )
                    )
                    viewModel.navigateTo(SocivaScreen.CREATE_POST)
                  }
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  UserAvatar(avatarUrl = currentUser?.avatarUrl ?: "", name = currentUser?.fullName ?: "You", size = 40.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Text(
                    text = "Write something in ${group.name}...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Spacer(modifier = Modifier.weight(1f))
                  Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF10B981))
                }
              }
            }
          }

          if (groupPosts.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(36.dp),
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(
                    Icons.Default.Forum,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                    "No posts yet in ${group.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                  )
                  Text(
                    "Be the first member to start a discussion!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          } else {
            items(groupPosts, key = { it.id }) { post ->
              PostCard(
                post = post,
                currentUser = currentUser,
                onReaction = { reaction -> viewModel.setReaction(post.id, reaction) },
                onCommentClick = { viewModel.openComments(post.id) },
                onShareClick = { viewModel.openShareComposer(post) },
                onSaveClick = { viewModel.toggleSavePost(post.id) },
                onAuthorClick = { viewModel.navigateToProfile(post.authorId) },
                onDeleteClick = { viewModel.deletePost(post.id) },
                onReportClick = { /* Report */ },
                onAnalyticsClick = { viewModel.openPostAnalytics(post.id) }
              )
              Spacer(modifier = Modifier.height(8.dp))
            }
          }
        }
        1 -> { // Members Tab
          if (groupMembers.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(36.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  "No members recorded yet.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          } else {
            items(groupMembers, key = { it.id }) { member ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 4.dp)
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  UserAvatar(avatarUrl = member.userAvatar, name = member.userName, size = 44.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(member.userName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("Joined group", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (member.role) {
                      "Owner" -> Color(0xFFEF4444).copy(alpha = 0.12f)
                      "Admin" -> SocivaBlue.copy(alpha = 0.12f)
                      "Moderator" -> Color(0xFF10B981).copy(alpha = 0.12f)
                      else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                  ) {
                    Text(
                      text = member.role,
                      style = MaterialTheme.typography.labelSmall,
                      color = when (member.role) {
                        "Owner" -> Color(0xFFEF4444)
                        "Admin" -> SocivaBlue
                        "Moderator" -> Color(0xFF10B981)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                      },
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                  }
                }
              }
            }
          }
        }
        2 -> { // About & Rules Tab
          item {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
              ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                  Text("About this Group", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  Text(group.description, style = MaterialTheme.typography.bodyMedium)

                  HorizontalDivider()

                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                      imageVector = if (group.privacy.contains("Private", ignoreCase = true)) Icons.Default.Lock else Icons.Default.Public,
                      contentDescription = null,
                      tint = SocivaBlue
                    )
                    Column {
                      Text(group.privacy, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                      Text(
                        if (group.privacy.contains("Private", ignoreCase = true))
                          "Only members can see who's in the group and what they post."
                        else
                          "Anyone can see who's in the group and what they post.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                }
              }

              if (group.rules.isNotBlank()) {
                Card(
                  shape = RoundedCornerShape(16.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                  Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Group Rules & Guidelines", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(group.rules, style = MaterialTheme.typography.bodyMedium)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
