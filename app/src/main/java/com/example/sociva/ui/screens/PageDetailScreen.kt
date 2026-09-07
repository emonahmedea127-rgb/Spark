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
import androidx.compose.ui.graphics.Brush
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
fun PageDetailScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  val activePage by viewModel.activePage.collectAsState()
  val activeIdentity by viewModel.activeIdentity.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  val page = activePage
  if (page == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  val isOwnerOrAdmin = page.isAdmin || page.ownerId == (currentUser?.id ?: "user_me")
  val isActingAsThisPage = activeIdentity.type == IdentityType.PAGE && activeIdentity.id == page.id

  val pagePosts by viewModel.getPagePosts(page.id).collectAsState(initial = emptyList())
  val pageFollowers by viewModel.getPageFollowers(page.id).collectAsState(initial = emptyList())
  val isFollowing by viewModel.isUserPageFollower(page.id).collectAsState(initial = page.isLiked)

  var selectedTab by remember { mutableStateOf(0) } // 0: Posts, 1: About, 2: Community
  val tabs = listOf("Posts", "About", "Community")

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = page.name,
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.titleMedium
            )
            Text(
              text = "${page.followersCount} followers • ${page.category}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("page_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (isOwnerOrAdmin) {
            IconButton(
              onClick = { viewModel.openPageManagement(page.id) },
              modifier = Modifier.testTag("page_manage_settings_button")
            ) {
              Icon(Icons.Default.Settings, contentDescription = "Manage Page")
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
              .data(page.coverUrl)
              .crossfade(true)
              .build(),
            contentDescription = "Cover Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxWidth()
              .height(160.dp)
          )

          // Avatar overlaid on cover
          Box(
            modifier = Modifier
              .align(Alignment.BottomStart)
              .padding(start = 16.dp)
          ) {
            Surface(
              shape = CircleShape,
              border = androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.surface),
              shadowElevation = 4.dp
            ) {
              UserAvatar(
                avatarUrl = page.avatarUrl,
                name = page.name,
                size = 80.dp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Page Details & Title
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = page.name,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold
            )
            if (page.followersCount > 10000 || page.id == "page_spark") {
              Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = "Verified Page",
                tint = SocivaBlue,
                modifier = Modifier.size(20.dp)
              )
            }
          }

          if (page.username.isNotBlank()) {
            Text(
              text = "@${page.username}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "${page.category} • ${page.followersCount} followers • ${page.likesCount} likes",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
          )

          if (page.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = page.description,
              style = MaterialTheme.typography.bodyMedium
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Identity Banner / Switcher Banner
          if (isActingAsThisPage) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFF10B981).copy(alpha = 0.12f),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
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
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "You're acting as this Page",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF047857)
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
            if (isOwnerOrAdmin) {
              if (!isActingAsThisPage) {
                Button(
                  onClick = {
                    viewModel.switchIdentity(
                      ActiveIdentity(
                        id = page.id,
                        name = page.name,
                        username = page.username,
                        avatarUrl = page.avatarUrl,
                        type = IdentityType.PAGE,
                        badge = page.category,
                        isVerified = page.followersCount > 10000
                      )
                    )
                  },
                  modifier = Modifier.weight(1f),
                  shape = RoundedCornerShape(12.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue)
                ) {
                  Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Switch into Page")
                }
              }

              OutlinedButton(
                onClick = { viewModel.openPageManagement(page.id) },
                modifier = if (isActingAsThisPage) Modifier.weight(1f) else Modifier,
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Manage Page")
              }
            } else {
              Button(
                onClick = { viewModel.toggleFollowPage(page.id) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant else SocivaBlue
                )
              ) {
                Icon(
                  imageVector = if (isFollowing) Icons.Default.Check else Icons.Default.ThumbUp,
                  contentDescription = null,
                  tint = if (isFollowing) MaterialTheme.colorScheme.onSurface else Color.White,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (isFollowing) "Following" else "Follow",
                  color = if (isFollowing) MaterialTheme.colorScheme.onSurface else Color.White
                )
              }

              OutlinedButton(
                onClick = { viewModel.togglePageLike(page.id) },
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Like")
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
        0 -> { // Posts Tab
          if (isActingAsThisPage || isOwnerOrAdmin) {
            item {
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp)
                  .clickable { viewModel.navigateTo(SocivaScreen.CREATE_POST) }
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  UserAvatar(avatarUrl = page.avatarUrl, name = page.name, size = 40.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Text(
                    text = "Post as ${page.name}...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Spacer(modifier = Modifier.weight(1f))
                  Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF10B981))
                }
              }
            }
          }

          if (pagePosts.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(36.dp),
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(
                    Icons.Default.Article,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                    "No posts yet on ${page.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                  )
                  Text(
                    "Posts published by this Page will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          } else {
            items(pagePosts, key = { it.id }) { post ->
              PostCard(
                post = post,
                currentUser = currentUser,
                onReaction = { reaction -> viewModel.setReaction(post.id, reaction) },
                onCommentClick = { viewModel.openComments(post.id) },
                onShareClick = { viewModel.openShareComposer(post) },
                onSaveClick = { viewModel.toggleSavePost(post.id) },
                onAuthorClick = { /* Already on page */ },
                onDeleteClick = { viewModel.deletePost(post.id) },
                onReportClick = { /* Report */ },
                onAnalyticsClick = { viewModel.openPostAnalytics(post.id) }
              )
              Spacer(modifier = Modifier.height(8.dp))
            }
          }
        }
        1 -> { // About Tab
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
                  Text("Page Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                  if (page.category.isNotBlank()) {
                    PageDetailAboutInfoRow(icon = Icons.Default.Category, label = "Category", value = page.category)
                  }
                  if (page.description.isNotBlank()) {
                    PageDetailAboutInfoRow(icon = Icons.Default.Info, label = "Bio", value = page.description)
                  }
                  if (page.website.isNotBlank()) {
                    PageDetailAboutInfoRow(icon = Icons.Default.Language, label = "Website", value = page.website)
                  }
                  if (page.location.isNotBlank()) {
                    PageDetailAboutInfoRow(icon = Icons.Default.Place, label = "Location", value = page.location)
                  }
                  if (page.contactEmail.isNotBlank()) {
                    PageDetailAboutInfoRow(icon = Icons.Default.Email, label = "Email", value = page.contactEmail)
                  }
                  if (page.contactPhone.isNotBlank()) {
                    PageDetailAboutInfoRow(icon = Icons.Default.Phone, label = "Phone", value = page.contactPhone)
                  }
                }
              }
            }
          }
        }
        2 -> { // Community / Followers Tab
          if (pageFollowers.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(36.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  "No followers recorded yet.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          } else {
            items(pageFollowers, key = { it.id }) { follower ->
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
                  UserAvatar(avatarUrl = follower.userAvatar, name = follower.userName, size = 44.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column {
                    Text(follower.userName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("Following ${page.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun PageDetailAboutInfoRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  value: String
) {
  Row(
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = SocivaBlue,
      modifier = Modifier.size(20.dp)
    )
    Column {
      Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
  }
}
