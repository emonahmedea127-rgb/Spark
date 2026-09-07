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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sociva.data.model.ActiveIdentity
import com.example.sociva.data.model.IdentityType
import com.example.sociva.data.model.SocivaGroup
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.ui.theme.SocivaBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  val groups by viewModel.groups.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val activeIdentity by viewModel.activeIdentity.collectAsState()

  var selectedTab by remember { mutableStateOf(0) } // 0: Discover, 1: Your Groups
  val tabs = listOf("Discover", "Your Groups")

  val myGroups = remember(groups) {
    groups.filter { it.isJoined }
  }

  val displayedGroups = when (selectedTab) {
    1 -> myGroups
    else -> groups
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Groups", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("groups_screen_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          FilledTonalButton(
            onClick = { viewModel.openCreateGroup() },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier
              .padding(end = 8.dp)
              .testTag("groups_create_button")
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
          val count = if (index == 1) myGroups.size else groups.size
          Tab(
            selected = selectedTab == index,
            onClick = { selectedTab = index },
            text = { Text("$title ($count)", fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
          )
        }
      }

      if (displayedGroups.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              Icons.Default.Groups,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = if (selectedTab == 1) "You haven't joined any groups yet" else "No Groups found",
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.titleMedium
            )
            if (selectedTab == 1) {
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                "Join communities or create your own group on Spark.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(14.dp))
              Button(
                onClick = { viewModel.openCreateGroup() },
                colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create New Group")
              }
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(displayedGroups, key = { it.id }) { group ->
            val isOwnerOrAdmin = group.role in listOf("Owner", "Admin", "Moderator") || group.ownerId == (currentUser?.id ?: "user_me")
            val isCurrentIdentity = activeIdentity.type == IdentityType.GROUP && activeIdentity.id == group.id

            GroupCard(
              group = group,
              isOwnerOrAdmin = isOwnerOrAdmin,
              isCurrentIdentity = isCurrentIdentity,
              onCardClick = { viewModel.openGroup(group.id) },
              onToggleJoin = { viewModel.joinOrLeaveGroup(group.id) },
              onSwitchIdentity = {
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
              },
              onManageClick = { viewModel.openGroupManagement(group.id) }
            )
          }
        }
      }
    }
  }
}

@Composable
fun GroupCard(
  group: SocivaGroup,
  isOwnerOrAdmin: Boolean,
  isCurrentIdentity: Boolean,
  onCardClick: () -> Unit,
  onToggleJoin: () -> Unit,
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
      .testTag("group_card_${group.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        UserAvatar(avatarUrl = group.avatarUrl, name = group.name, size = 52.dp)
        Column(modifier = Modifier.weight(1f)) {
          Text(group.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = if (group.privacy.contains("Private", ignoreCase = true)) Icons.Default.Lock else Icons.Default.Public,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "${group.privacy} • ${group.membersCount} members",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          if (group.role.isNotBlank()) {
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = SocivaBlue.copy(alpha = 0.12f),
              modifier = Modifier.padding(top = 2.dp)
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
      }

      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = group.description,
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
              containerColor = if (isCurrentIdentity) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF8B5CF6)
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
              text = if (isCurrentIdentity) "Active Group" else "Switch to Group",
              color = if (isCurrentIdentity) MaterialTheme.colorScheme.onSurface else Color.White
            )
          }

          OutlinedButton(
            onClick = onManageClick,
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Admin")
          }
        } else {
          Button(
            onClick = onToggleJoin,
            colors = ButtonDefaults.buttonColors(
              containerColor = if (group.isJoined) MaterialTheme.colorScheme.surfaceVariant else SocivaBlue
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = if (group.isJoined) Icons.Default.Check else Icons.Default.GroupAdd,
              contentDescription = null,
              tint = if (group.isJoined) MaterialTheme.colorScheme.onSurface else Color.White,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (group.isJoined) "Joined" else if (group.privacy.contains("Private", ignoreCase = true)) "Request" else "Join",
              color = if (group.isJoined) MaterialTheme.colorScheme.onSurface else Color.White
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
