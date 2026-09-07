package com.example.sociva.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.sociva.ui.SocivaViewModel
import com.example.ui.theme.SocivaBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  var name by remember { mutableStateOf("") }
  var privacy by remember { mutableStateOf("Public") } // Public or Private
  var description by remember { mutableStateOf("") }
  var rules by remember { mutableStateOf("1. Be respectful and supportive\n2. No spam or self-promotion\n3. Protect everyone's privacy") }

  val isFormValid = name.trim().isNotBlank() && description.trim().isNotBlank()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Create a Group", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("create_group_back")) {
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
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Introduction Banner
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Groups, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(28.dp))
          Spacer(modifier = Modifier.width(14.dp))
          Column {
            Text("Create Your Community", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
              "Groups are spaces for members to share mutual interests, ask questions, and build discussions together.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // Group Name
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Group Name *") },
        placeholder = { Text("e.g., My Group, Mobile Developers Club") },
        leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("input_group_name")
      )

      // Privacy Selector
      Text("Choose Privacy *", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
          containerColor = if (privacy == "Public") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
          if (privacy == "Public") 1.5.dp else 1.dp,
          if (privacy == "Public") SocivaBlue else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
          .fillMaxWidth()
          .clickable { privacy = "Public" }
      ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
          RadioButton(selected = privacy == "Public", onClick = { privacy = "Public" })
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp), tint = SocivaBlue)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Public", fontWeight = FontWeight.Bold)
            }
            Text("Anyone can see who's in the group and what they post.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
          containerColor = if (privacy == "Private") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
          if (privacy == "Private") 1.5.dp else 1.dp,
          if (privacy == "Private") SocivaBlue else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
          .fillMaxWidth()
          .clickable { privacy = "Private" }
      ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
          RadioButton(selected = privacy == "Private", onClick = { privacy = "Private" })
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = SocivaBlue)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Private", fontWeight = FontWeight.Bold)
            }
            Text("Only members can see who's in the group and what they post. Admins approve requests.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      // Description
      OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Group Description *") },
        placeholder = { Text("What is the goal of this community?") },
        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
        minLines = 3,
        maxLines = 5,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("input_group_description")
      )

      // Rules
      OutlinedTextField(
        value = rules,
        onValueChange = { rules = it },
        label = { Text("Group Rules & Guidelines") },
        placeholder = { Text("Add guidelines for group members...") },
        leadingIcon = { Icon(Icons.Default.Gavel, contentDescription = null) },
        minLines = 3,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Submit Button
      Button(
        onClick = {
          viewModel.createGroup(
            name = name.trim(),
            privacy = privacy,
            description = description.trim(),
            rules = rules.trim()
          )
        },
        enabled = isFormValid,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("submit_create_group")
      ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Create Group", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
    }
  }
}
