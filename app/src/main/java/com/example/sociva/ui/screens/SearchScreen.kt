package com.example.sociva.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.sociva.ui.components.VerifiedBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  val searchQuery by viewModel.searchQuery.collectAsState()
  val selectedCategory by viewModel.searchCategory.collectAsState()

  val allUsers by viewModel.allUsers.collectAsState()
  val allPosts by viewModel.allPosts.collectAsState()
  val allPages by viewModel.pages.collectAsState()
  val allGroups by viewModel.groups.collectAsState()

  val categories = listOf("All", "People", "Posts", "Pages", "Groups")

  val filteredUsers = remember(allUsers, searchQuery) {
    if (searchQuery.isBlank()) emptyList()
    else allUsers.filter {
      it.fullName.contains(searchQuery, ignoreCase = true) ||
      it.username.contains(searchQuery, ignoreCase = true)
    }
  }

  val filteredPosts = remember(allPosts, searchQuery) {
    if (searchQuery.isBlank()) emptyList()
    else allPosts.filter { it.content.contains(searchQuery, ignoreCase = true) }
  }

  val filteredPages = remember(allPages, searchQuery) {
    if (searchQuery.isBlank()) emptyList()
    else allPages.filter { it.name.contains(searchQuery, ignoreCase = true) }
  }

  val filteredGroups = remember(allGroups, searchQuery) {
    if (searchQuery.isBlank()) emptyList()
    else allGroups.filter { it.name.contains(searchQuery, ignoreCase = true) }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search Spark...") },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                  Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
              }
            },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("search_input_field"),
            singleLine = true
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(MaterialTheme.colorScheme.background)
    ) {
      // Category Filter Chips
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(categories) { category ->
          FilterChip(
            selected = selectedCategory == category,
            onClick = { viewModel.setSearchCategory(category) },
            label = { Text(category) }
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

      if (searchQuery.isBlank()) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Search for people, posts, groups and pages",
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.fillMaxSize()
        ) {
          // Users / People Results
          if (selectedCategory in listOf("All", "People") && filteredUsers.isNotEmpty()) {
            item {
              Text("People", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            items(filteredUsers) { user ->
              Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { viewModel.navigateToProfile(user.id) }
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  UserAvatar(avatarUrl = user.avatarUrl, name = user.fullName, size = 46.dp)
                  Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(user.fullName, fontWeight = FontWeight.Bold)
                      if (user.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedBadge(size = 14.dp)
                      }
                    }
                    Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }
            }
          }

          // Pages Results
          if (selectedCategory in listOf("All", "Pages") && filteredPages.isNotEmpty()) {
            item {
              Text("Pages", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            items(filteredPages) { page ->
              Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { viewModel.openPage(page.id) }
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  UserAvatar(avatarUrl = page.avatarUrl, name = page.name, size = 46.dp)
                  Column(modifier = Modifier.weight(1f)) {
                    Text(page.name, fontWeight = FontWeight.Bold)
                    Text("${page.followersCount} followers • ${page.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Button(onClick = { viewModel.togglePageLike(page.id) }) {
                    Text(if (page.isLiked) "Liked" else "Like")
                  }
                }
              }
            }
          }

          // Groups Results
          if (selectedCategory in listOf("All", "Groups") && filteredGroups.isNotEmpty()) {
            item {
              Text("Groups", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            items(filteredGroups) { group ->
              Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { viewModel.openGroup(group.id) }
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  UserAvatar(avatarUrl = group.avatarUrl, name = group.name, size = 46.dp)
                  Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, fontWeight = FontWeight.Bold)
                    Text("${group.membersCount} members • ${group.privacy}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Button(onClick = { viewModel.toggleGroupJoin(group.id) }) {
                    Text(if (group.isJoined) "Joined" else "Join")
                  }
                }
              }
            }
          }

          // Posts Results
          if (selectedCategory in listOf("All", "Posts") && filteredPosts.isNotEmpty()) {
            item {
              Text("Posts", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            items(filteredPosts) { post ->
              PostCard(
                post = post,
                currentUser = null,
                onReaction = { reaction -> viewModel.setReaction(post.id, reaction) },
                onCommentClick = { viewModel.openComments(post.id) },
                onShareClick = { viewModel.sharePost(post.id) },
                onSaveClick = { viewModel.toggleSavePost(post.id) },
                onAuthorClick = { viewModel.navigateToProfile(post.authorId) },
                onDeleteClick = {},
                onReportClick = {},
                onReactionsClick = { viewModel.openReactionsModal(post.id) }
              )
            }
          }
        }
      }
    }
  }
}
