package com.example.sociva.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sociva.data.model.ActiveIdentity
import com.example.sociva.data.model.IdentityType
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.ui.theme.SocivaBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageManagementScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  val activePage by viewModel.activePage.collectAsState()
  val activeIdentity by viewModel.activeIdentity.collectAsState()

  val page = activePage
  if (page == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  val pageFollowers by viewModel.getPageFollowers(page.id).collectAsState(initial = emptyList())
  val isActingAsThisPage = activeIdentity.type == IdentityType.PAGE && activeIdentity.id == page.id

  var isEditingInfo by remember { mutableStateOf(false) }
  var editName by remember(page) { mutableStateOf(page.name) }
  var editBio by remember(page) { mutableStateOf(page.description) }
  var editCategory by remember(page) { mutableStateOf(page.category) }
  var editWebsite by remember(page) { mutableStateOf(page.website) }
  var editLocation by remember(page) { mutableStateOf(page.location) }
  var editEmail by remember(page) { mutableStateOf(page.contactEmail) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Page Dashboard", fontWeight = FontWeight.Bold)
            Text(page.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("manage_page_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (!isActingAsThisPage) {
            TextButton(
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
              }
            ) {
              Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Switch")
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
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Identity Action Banner
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (isActingAsThisPage) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
          )
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            UserAvatar(avatarUrl = page.avatarUrl, name = page.name, size = 48.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                if (isActingAsThisPage) "Active Persona: ${page.name}" else "Manage as ${page.name}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
              )
              Text(
                if (isActingAsThisPage) "You are currently interacting across Spark as this Page." else "Switch into this Page to publish posts and respond to comments.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = {
                if (isActingAsThisPage) {
                  viewModel.resetToPersonalIdentity()
                } else {
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
                }
              },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = if (isActingAsThisPage) MaterialTheme.colorScheme.surfaceVariant else SocivaBlue
              )
            ) {
              Text(
                if (isActingAsThisPage) "Reset" else "Switch",
                color = if (isActingAsThisPage) MaterialTheme.colorScheme.onSurface else Color.White
              )
            }
          }
        }
      }

      // Insights Section
      item {
        Text("Page Insights & Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          InsightMetricCard(
            title = "Followers",
            value = "${page.followersCount}",
            icon = Icons.Default.People,
            modifier = Modifier.weight(1f)
          )
          InsightMetricCard(
            title = "Likes",
            value = "${page.likesCount}",
            icon = Icons.Default.Favorite,
            modifier = Modifier.weight(1f)
          )
        }
      }

      // Edit Page Info Card
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Page Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              TextButton(onClick = { isEditingInfo = !isEditingInfo }) {
                Text(if (isEditingInfo) "Cancel" else "Edit")
              }
            }

            if (!isEditingInfo) {
              Text("Category: ${page.category}", style = MaterialTheme.typography.bodyMedium)
              Text("Bio: ${page.description}", style = MaterialTheme.typography.bodyMedium)
              if (page.website.isNotBlank()) Text("Website: ${page.website}", style = MaterialTheme.typography.bodySmall)
              if (page.location.isNotBlank()) Text("Location: ${page.location}", style = MaterialTheme.typography.bodySmall)
              if (page.contactEmail.isNotBlank()) Text("Email: ${page.contactEmail}", style = MaterialTheme.typography.bodySmall)
            } else {
              OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                label = { Text("Page Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
              OutlinedTextField(
                value = editCategory,
                onValueChange = { editCategory = it },
                label = { Text("Category") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
              OutlinedTextField(
                value = editBio,
                onValueChange = { editBio = it },
                label = { Text("Description / Bio") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
              )
              OutlinedTextField(
                value = editWebsite,
                onValueChange = { editWebsite = it },
                label = { Text("Website") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
              OutlinedTextField(
                value = editLocation,
                onValueChange = { editLocation = it },
                label = { Text("Location") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
              OutlinedTextField(
                value = editEmail,
                onValueChange = { editEmail = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )

              Button(
                onClick = {
                  viewModel.updatePageDetails(
                    page.copy(
                      name = editName,
                      category = editCategory,
                      description = editBio,
                      website = editWebsite,
                      location = editLocation,
                      contactEmail = editEmail
                    )
                  )
                  isEditingInfo = false
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("Save Changes")
              }
            }
          }
        }
      }

      // Recent Followers
      item {
        Text("Recent Followers (${pageFollowers.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }

      if (pageFollowers.isEmpty()) {
        item {
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
              Text("No followers have joined yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      } else {
        items(pageFollowers, key = { it.id }) { follower ->
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              UserAvatar(avatarUrl = follower.userAvatar, name = follower.userName, size = 42.dp)
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(follower.userName, fontWeight = FontWeight.Bold)
                Text("Follower", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun InsightMetricCard(
  title: String,
  value: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = modifier
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(imageVector = icon, contentDescription = null, tint = SocivaBlue, modifier = Modifier.size(20.dp))
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
  }
}
