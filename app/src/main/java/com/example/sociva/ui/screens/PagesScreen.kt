package com.example.sociva.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.sociva.data.model.ActiveIdentity
import com.example.sociva.data.model.IdentityType
import com.example.sociva.data.model.SocivaPage
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.ui.theme.SocivaBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  val pages by viewModel.pages.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val activeIdentity by viewModel.activeIdentity.collectAsState()

  var selectedTab by remember { mutableStateOf(0) } // 0: Discover, 1: Your Pages, 2: Liked
  val tabs = listOf("Discover", "Your Pages", "Liked")

  val myPages = remember(pages, currentUser) {
    pages.filter { it.isAdmin || it.ownerId == (currentUser?.id ?: "user_me") }
  }
  val likedPages = remember(pages) {
    pages.filter { it.isLiked }
  }

  val displayedPages = when (selectedTab) {
    1 -> myPages
    2 -> likedPages
    else -> pages
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Pages", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("pages_screen_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          FilledTonalButton(
            onClick = { viewModel.openCreatePage() },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier
              .padding(end = 8.dp)
              .testTag("pages_create_button")
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Create")
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Tab Bar
      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = SocivaBlue
      ) {
        tabs.forEachIndexed { index, title ->
          val count = when (index) {
            1 -> myPages.size
            2 -> likedPages.size
            else -> pages.size
          }
          Tab(
            selected = selectedTab == index,
            onClick = { selectedTab = index },
            text = { Text("$title ($count)", fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
          )
        }
      }

      if (displayedPages.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              Icons.Default.Flag,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = if (selectedTab == 1) "You haven't created any Pages yet" else "No Pages found",
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.titleMedium
            )
            if (selectedTab == 1) {
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                "Create a Page to build your brand and switch between identities.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(14.dp))
              Button(
                onClick = { viewModel.openCreatePage() },
                colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create New Page")
              }
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          items(displayedPages, key = { it.id }) { page ->
            val isOwnerOrAdmin = page.isAdmin || page.ownerId == (currentUser?.id ?: "user_me")
            val isCurrentIdentity = activeIdentity.type == IdentityType.PAGE && activeIdentity.id == page.id

            PageCard(
              page = page,
              isOwnerOrAdmin = isOwnerOrAdmin,
              isCurrentIdentity = isCurrentIdentity,
              onCardClick = { viewModel.openPage(page.id) },
              onToggleLike = { viewModel.togglePageLike(page.id) },
              onSwitchIdentity = {
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
              onManageClick = { viewModel.openPageManagement(page.id) }
            )
          }
        }
      }
    }
  }
}

@Composable
fun PageCard(
  page: SocivaPage,
  isOwnerOrAdmin: Boolean,
  isCurrentIdentity: Boolean,
  onCardClick: () -> Unit,
  onToggleLike: () -> Unit,
  onSwitchIdentity: () -> Unit,
  onManageClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onCardClick() }
      .testTag("page_card_${page.id}")
  ) {
    Column {
      AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
          .data(page.coverUrl)
          .crossfade(true)
          .build(),
        contentDescription = "Cover",
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxWidth()
          .height(110.dp)
      )

      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          UserAvatar(avatarUrl = page.avatarUrl, name = page.name, size = 48.dp)
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(page.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
              if (page.followersCount > 10000 || page.id == "page_spark") {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.Verified, contentDescription = "Verified", tint = SocivaBlue, modifier = Modifier.size(16.dp))
              }
            }
            Text("${page.followersCount} followers • ${page.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = page.description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (isOwnerOrAdmin) {
            Button(
              onClick = onSwitchIdentity,
              colors = ButtonDefaults.buttonColors(
                containerColor = if (isCurrentIdentity) MaterialTheme.colorScheme.surfaceVariant else SocivaBlue
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                if (isCurrentIdentity) Icons.Default.Check else Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = if (isCurrentIdentity) MaterialTheme.colorScheme.onSurface else Color.White,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isCurrentIdentity) "Active Persona" else "Switch to Page",
                color = if (isCurrentIdentity) MaterialTheme.colorScheme.onSurface else Color.White
              )
            }

            OutlinedButton(
              onClick = onManageClick,
              shape = RoundedCornerShape(12.dp)
            ) {
              Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Manage")
            }
          } else {
            Button(
              onClick = onToggleLike,
              colors = ButtonDefaults.buttonColors(
                containerColor = if (page.isLiked) MaterialTheme.colorScheme.surfaceVariant else SocivaBlue
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = if (page.isLiked) Icons.Default.Check else Icons.Default.ThumbUp,
                contentDescription = null,
                tint = if (page.isLiked) MaterialTheme.colorScheme.onSurface else Color.White,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (page.isLiked) "Following" else "Follow",
                color = if (page.isLiked) MaterialTheme.colorScheme.onSurface else Color.White
              )
            }
          }

          OutlinedButton(
            onClick = onCardClick,
            shape = RoundedCornerShape(12.dp)
          ) {
            Text("Visit")
          }
        }
      }
    }
  }
}
