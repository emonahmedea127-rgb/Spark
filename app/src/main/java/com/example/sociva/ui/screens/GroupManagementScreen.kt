package com.example.sociva.ui.screens

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
import com.example.sociva.data.model.GroupJoinRequest
import com.example.sociva.data.model.GroupMember
import com.example.sociva.data.model.IdentityType
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.ui.theme.SocivaBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManagementScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  val activeGroup by viewModel.activeGroup.collectAsState()
  val activeIdentity by viewModel.activeIdentity.collectAsState()

  val group = activeGroup
  if (group == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  val members by viewModel.getGroupMembers(group.id).collectAsState(initial = emptyList())
  val joinRequests by viewModel.getGroupJoinRequests(group.id).collectAsState(initial = emptyList())
  val isActingAsThisGroup = activeIdentity.type == IdentityType.GROUP && activeIdentity.id == group.id

  var selectedTab by remember { mutableStateOf(0) } // 0: Requests, 1: Members, 2: Rules & Settings
  val tabs = listOf("Pending Requests (${joinRequests.size})", "Members (${members.size})", "Rules & Settings")

  var isEditingSettings by remember { mutableStateOf(false) }
  var editDescription by remember(group) { mutableStateOf(group.description) }
  var editRules by remember(group) { mutableStateOf(group.rules) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Admin Tools", fontWeight = FontWeight.Bold)
            Text(group.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("manage_group_back")) {
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
    ) {
      // Identity Banner
      Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(avatarUrl = group.avatarUrl, name = group.name, size = 36.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(group.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
              Text("Admin Management", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }

          Button(
            onClick = {
              if (isActingAsThisGroup) {
                viewModel.resetToPersonalIdentity()
              } else {
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
              }
            },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = if (isActingAsThisGroup) MaterialTheme.colorScheme.surfaceVariant else SocivaBlue
            )
          ) {
            Text(
              if (isActingAsThisGroup) "Acting as Group" else "Post as Group",
              color = if (isActingAsThisGroup) MaterialTheme.colorScheme.onSurface else Color.White,
              style = MaterialTheme.typography.labelMedium
            )
          }
        }
      }

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

      // Tab Content
      when (selectedTab) {
        0 -> { // Pending Join Requests
          if (joinRequests.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No Pending Requests", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("All membership applications have been reviewed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              items(joinRequests, key = { it.id }) { req ->
                Card(
                  shape = RoundedCornerShape(16.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    UserAvatar(avatarUrl = req.userAvatar, name = req.userName, size = 48.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(req.userName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                      Text("Requested to join", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      FilledTonalButton(
                        onClick = { viewModel.rejectGroupJoinRequest(req.id) },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                      ) {
                        Text("Decline", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                      }
                      Button(
                        onClick = { viewModel.approveGroupJoinRequest(req) },
                        colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                      ) {
                        Text("Approve", style = MaterialTheme.typography.labelMedium)
                      }
                    }
                  }
                }
              }
            }
          }
        }
        1 -> { // Members
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(members, key = { it.id }) { member ->
              Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  UserAvatar(avatarUrl = member.userAvatar, name = member.userName, size = 44.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(member.userName, fontWeight = FontWeight.Bold)
                    Text(member.role, style = MaterialTheme.typography.bodySmall, color = SocivaBlue)
                  }

                  if (member.role != "Owner") {
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                      IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Member Actions")
                      }
                      DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (member.role != "Admin") {
                          DropdownMenuItem(
                            text = { Text("Make Admin") },
                            onClick = {
                              viewModel.updateGroupMemberRole(group.id, member.userId, "Admin")
                              showMenu = false
                            }
                          )
                        }
                        if (member.role != "Moderator") {
                          DropdownMenuItem(
                            text = { Text("Make Moderator") },
                            onClick = {
                              viewModel.updateGroupMemberRole(group.id, member.userId, "Moderator")
                              showMenu = false
                            }
                          )
                        }
                        if (member.role != "Member") {
                          DropdownMenuItem(
                            text = { Text("Set as Regular Member") },
                            onClick = {
                              viewModel.updateGroupMemberRole(group.id, member.userId, "Member")
                              showMenu = false
                            }
                          )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                          text = { Text("Remove from Group", color = MaterialTheme.colorScheme.error) },
                          onClick = {
                            viewModel.removeGroupMember(group.id, member.userId, member.userName)
                            showMenu = false
                          }
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
        2 -> { // Rules & Settings
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Group Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  TextButton(onClick = { isEditingSettings = !isEditingSettings }) {
                    Text(if (isEditingSettings) "Cancel" else "Edit")
                  }
                }

                if (!isEditingSettings) {
                  Text("Description:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                  Text(group.description, style = MaterialTheme.typography.bodyMedium)

                  Spacer(modifier = Modifier.height(6.dp))
                  Text("Rules & Guidelines:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                  Text(if (group.rules.isNotBlank()) group.rules else "No custom rules configured yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                  OutlinedTextField(
                    value = editDescription,
                    onValueChange = { editDescription = it },
                    label = { Text("Group Description") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                  )

                  OutlinedTextField(
                    value = editRules,
                    onValueChange = { editRules = it },
                    label = { Text("Rules & Guidelines") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                  )

                  Button(
                    onClick = {
                      viewModel.updateGroupDetails(
                        group.copy(
                          description = editDescription,
                          rules = editRules
                        )
                      )
                      isEditingSettings = false
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
        }
      }
    }
  }
}
