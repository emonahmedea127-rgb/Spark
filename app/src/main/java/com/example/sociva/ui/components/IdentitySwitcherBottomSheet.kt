package com.example.sociva.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sociva.data.model.ActiveIdentity
import com.example.sociva.data.model.IdentityType
import com.example.sociva.ui.SocivaViewModel
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaIndigo

@Composable
fun IdentitySwitcherBottomSheet(
  viewModel: SocivaViewModel,
  onDismiss: () -> Unit,
  onCreatePage: () -> Unit,
  onCreateGroup: () -> Unit
) {
  val activeIdentity by viewModel.activeIdentity.collectAsState()
  val availableIdentities by viewModel.availableIdentities.collectAsState()
  IdentitySwitcherBottomSheet(
    activeIdentity = activeIdentity,
    availableIdentities = availableIdentities,
    onSelectIdentity = {
      viewModel.switchIdentity(it)
      onDismiss()
    },
    onDismiss = onDismiss,
    onCreatePageClick = onCreatePage,
    onCreateGroupClick = onCreateGroup
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentitySwitcherBottomSheet(
  activeIdentity: ActiveIdentity,
  availableIdentities: List<ActiveIdentity>,
  onSelectIdentity: (ActiveIdentity) -> Unit,
  onDismiss: () -> Unit,
  onCreatePageClick: () -> Unit,
  onCreateGroupClick: () -> Unit
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    modifier = Modifier.testTag("identity_switcher_bottom_sheet")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 32.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Switch Profile or Page",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Choose the identity you want to browse and post as",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.testTag("identity_switcher_close")
        ) {
          Icon(Icons.Default.Close, contentDescription = "Close")
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f, fill = false)
      ) {
        // Section: Personal Profile
        val personalIdentities = availableIdentities.filter { it.type == IdentityType.PERSONAL }
        if (personalIdentities.isNotEmpty()) {
          item {
            Text(
              text = "PERSONAL PROFILE",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              letterSpacing = 1.sp,
              modifier = Modifier.padding(vertical = 4.dp)
            )
          }
          items(personalIdentities, key = { "personal_${it.id}" }) { identity ->
            IdentityItemRow(
              identity = identity,
              isActive = identity.id == activeIdentity.id && identity.type == activeIdentity.type,
              onSelect = { onSelectIdentity(identity) }
            )
          }
        }

        // Section: Managed Pages
        val pageIdentities = availableIdentities.filter { it.type == IdentityType.PAGE }
        item {
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "PAGES YOU MANAGE",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              letterSpacing = 1.sp
            )
            TextButton(
              onClick = {
                onDismiss()
                onCreatePageClick()
              },
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Create Page", style = MaterialTheme.typography.labelMedium)
            }
          }
        }

        if (pageIdentities.isEmpty()) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("You don't manage any Pages yet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                  Text("Create a Page to connect with your audience", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        } else {
          items(pageIdentities, key = { "page_${it.id}" }) { identity ->
            IdentityItemRow(
              identity = identity,
              isActive = identity.id == activeIdentity.id && identity.type == activeIdentity.type,
              onSelect = { onSelectIdentity(identity) }
            )
          }
        }

        // Section: Managed Groups
        val groupIdentities = availableIdentities.filter { it.type == IdentityType.GROUP }
        item {
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "GROUPS YOU MANAGE",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              letterSpacing = 1.sp
            )
            TextButton(
              onClick = {
                onDismiss()
                onCreateGroupClick()
              },
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Create Group", style = MaterialTheme.typography.labelMedium)
            }
          }
        }

        if (groupIdentities.isEmpty()) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("You don't manage any Groups yet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                  Text("Start a community around shared interests", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        } else {
          items(groupIdentities, key = { "group_${it.id}" }) { identity ->
            IdentityItemRow(
              identity = identity,
              isActive = identity.id == activeIdentity.id && identity.type == activeIdentity.type,
              onSelect = { onSelectIdentity(identity) }
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Informative footer tip
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.SwapHoriz,
            contentDescription = null,
            tint = SocivaBlue,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "Switching identity lets you post, react, and comment as that Page or Group without exposing your personal account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
fun IdentityItemRow(
  identity: ActiveIdentity,
  isActive: Boolean,
  onSelect: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
    border = if (isActive) {
      androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
      androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    },
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onSelect() }
      .testTag("identity_row_${identity.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box {
        UserAvatar(
          avatarUrl = identity.avatarUrl,
          name = identity.name,
          size = 48.dp
        )
        // Icon badge on bottom corner
        Surface(
          shape = CircleShape,
          color = when (identity.type) {
            IdentityType.PERSONAL -> SocivaBlue
            IdentityType.PAGE -> Color(0xFF10B981)
            IdentityType.GROUP -> Color(0xFF8B5CF6)
          },
          border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
          modifier = Modifier
            .size(18.dp)
            .align(Alignment.BottomEnd)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = when (identity.type) {
                IdentityType.PERSONAL -> Icons.Default.Person
                IdentityType.PAGE -> Icons.Default.Flag
                IdentityType.GROUP -> Icons.Default.Groups
              },
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(10.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = identity.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          if (identity.isVerified) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.Default.Verified,
              contentDescription = "Verified",
              tint = SocivaBlue,
              modifier = Modifier.size(16.dp)
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = when (identity.type) {
              IdentityType.PERSONAL -> "@${identity.username}"
              IdentityType.PAGE -> if (identity.badge.isNotBlank()) identity.badge else "Page"
              IdentityType.GROUP -> if (identity.badge.isNotBlank()) "${identity.badge} • Group" else "Group"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (isActive) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Default.CheckCircle,
              contentDescription = "Active",
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      } else {
        FilledTonalButton(
          onClick = onSelect,
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
          modifier = Modifier.height(34.dp)
        ) {
          Text("Switch", style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}
