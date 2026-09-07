package com.example.sociva.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sociva.ui.SocivaViewModel
import com.example.ui.theme.SocivaPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPostsScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  val savedPosts by viewModel.savedPosts.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bookmark, contentDescription = null, tint = SocivaPurple)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Saved Items", fontWeight = FontWeight.Bold)
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { innerPadding ->
    if (savedPosts.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Default.Bookmark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp)
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "No saved posts yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentPadding = PaddingValues(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(savedPosts, key = { it.id }) { post ->
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
            onReactionsClick = { viewModel.openReactionsModal(post.id) },
            onAnalyticsClick = { viewModel.openPostAnalytics(post.id) }
          )
        }
      }
    }
  }
}
