package com.example.sociva.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sociva.data.model.ActiveIdentity
import com.example.sociva.data.model.IdentityType
import com.example.sociva.data.model.User
import com.example.sociva.ui.SocivaScreen
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.sociva.ui.components.VerifiedBadge
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaPurple

data class MenuItem(
  val title: String,
  val icon: ImageVector,
  val iconColor: Color,
  val onClick: () -> Unit
)

@Composable
fun MenuScreen(
  viewModel: SocivaViewModel,
  modifier: Modifier = Modifier
) {
  val currentUser by viewModel.currentUser.collectAsState()
  val activeIdentity by viewModel.activeIdentity.collectAsState()
  val availableIdentities by viewModel.availableIdentities.collectAsState()
  val isDarkTheme by viewModel.isDarkTheme.collectAsState()
  val currentLanguage by viewModel.currentLanguage.collectAsState()

  var showLanguageDialog by remember { mutableStateOf(false) }

  val menuShortcuts = listOf(
    MenuItem(
      title = "Saved Posts",
      icon = Icons.Default.Bookmark,
      iconColor = SocivaPurple,
      onClick = { viewModel.navigateTo(SocivaScreen.SAVED_POSTS) }
    ),
    MenuItem(
      title = "Friends",
      icon = Icons.Default.People,
      iconColor = SocivaBlue,
      onClick = { viewModel.selectTab(2) }
    ),
    MenuItem(
      title = "Pages",
      icon = Icons.Default.Flag,
      iconColor = Color(0xFFF59E0B),
      onClick = { viewModel.navigateTo(SocivaScreen.PAGES) }
    ),
    MenuItem(
      title = "Groups",
      icon = Icons.Default.Groups,
      iconColor = Color(0xFF10B981),
      onClick = { viewModel.navigateTo(SocivaScreen.GROUPS) }
    ),
    MenuItem(
      title = "Reels",
      icon = Icons.Default.SmartDisplay,
      iconColor = Color(0xFFEC4899),
      onClick = { viewModel.selectTab(1) }
    ),
    MenuItem(
      title = "Professional Analytics",
      icon = Icons.Default.Insights,
      iconColor = Color(0xFF6366F1),
      onClick = { viewModel.navigateTo(SocivaScreen.ANALYTICS) }
    ),
    MenuItem(
      title = "Admin Portal",
      icon = Icons.Default.AdminPanelSettings,
      iconColor = Color(0xFFEF4444),
      onClick = { viewModel.navigateTo(SocivaScreen.ADMIN) }
    )
  )

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .verticalScroll(rememberScrollState())
      .padding(16.dp)
      .padding(bottom = 60.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Top Profile & Identity Switcher Card (Facebook Style)
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("menu_profile_card")
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              if (activeIdentity.type == IdentityType.PERSONAL) {
                viewModel.navigateToMyProfile()
              } else if (activeIdentity.type == IdentityType.PAGE) {
                viewModel.openPage(activeIdentity.id)
              } else {
                viewModel.openGroup(activeIdentity.id)
              }
            }
        ) {
          UserAvatar(
            avatarUrl = activeIdentity.avatarUrl,
            name = activeIdentity.name,
            size = 52.dp
          )

          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = activeIdentity.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              if (activeIdentity.isVerified) {
                Spacer(modifier = Modifier.width(4.dp))
                VerifiedBadge(size = 14.dp)
              }
            }
            Text(
              text = when (activeIdentity.type) {
                IdentityType.PERSONAL -> "Personal Profile • View profile"
                IdentityType.PAGE -> "Managed Page • View page"
                IdentityType.GROUP -> "Active Group • View group"
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          // Facebook-Style Switch Button
          Surface(
            shape = CircleShape,
            color = SocivaBlue.copy(alpha = 0.12f),
            modifier = Modifier
              .size(40.dp)
              .clickable { viewModel.showIdentitySwitcher() }
              .testTag("menu_identity_switch_button")
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Switch Profile",
                tint = SocivaBlue,
                modifier = Modifier.size(22.dp)
              )
            }
          }
        }

        // Quick Identity Horizontal Strip if multiple identities available
        val otherIdentities = remember(availableIdentities, activeIdentity) {
          availableIdentities.filter { it.id != activeIdentity.id }
        }
        if (otherIdentities.isNotEmpty()) {
          HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "Switch into another identity",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(
              onClick = { viewModel.showIdentitySwitcher() },
              contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
              Text("See all (${availableIdentities.size})", style = MaterialTheme.typography.labelSmall, color = SocivaBlue)
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            otherIdentities.take(3).forEach { identity ->
              Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                  .clickable { viewModel.switchIdentity(identity) }
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  UserAvatar(avatarUrl = identity.avatarUrl, name = identity.name, size = 22.dp)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = identity.name.take(12) + if (identity.name.length > 12) "..." else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                  )
                }
              }
            }
          }
        }
      }
    }

    Text(
      text = "All Shortcuts",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )

    // Shortcuts Grid
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      for (i in menuShortcuts.indices step 2) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          ShortcutCard(item = menuShortcuts[i], modifier = Modifier.weight(1f))
          if (i + 1 < menuShortcuts.size) {
            ShortcutCard(item = menuShortcuts[i + 1], modifier = Modifier.weight(1f))
          } else {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }

    // Settings & Preferences List
    Text(
      text = "Settings & Preferences",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(top = 8.dp)
    )

    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column {
        // Dark Mode Toggle
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Icon(Icons.Default.DarkMode, contentDescription = null, tint = SocivaPurple)
            Text("Dark Mode", fontWeight = FontWeight.Medium)
          }
          Switch(
            checked = isDarkTheme == true,
            onCheckedChange = { viewModel.toggleTheme(it) }
          )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        // Language Option (Bengali, English, etc.)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showLanguageDialog = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Icon(Icons.Default.Language, contentDescription = null, tint = SocivaBlue)
            Text("Language", fontWeight = FontWeight.Medium)
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = currentLanguage,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
              imageVector = Icons.Default.ChevronRight,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        // Detailed Settings
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.navigateTo(SocivaScreen.SETTINGS) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF10B981))
            Text("Account & Privacy Settings", fontWeight = FontWeight.Medium)
          }
          Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    // Log Out Button
    OutlinedButton(
      onClick = { viewModel.logout() },
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 10.dp)
        .testTag("logout_button"),
      shape = RoundedCornerShape(12.dp),
      colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) {
      Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text("Log Out", fontWeight = FontWeight.Bold)
    }
  }

  // Language Selector Dialog
  if (showLanguageDialog) {
    val languages = listOf("English", "Bengali (বাংলা)", "Spanish (Español)", "French (Français)")
    AlertDialog(
      onDismissRequest = { showLanguageDialog = false },
      title = { Text("Select Language", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          languages.forEach { lang ->
            Text(
              text = lang,
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                  viewModel.setLanguage(lang.substringBefore(" "))
                  showLanguageDialog = false
                }
                .padding(vertical = 10.dp, horizontal = 12.dp),
              style = MaterialTheme.typography.bodyMedium
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showLanguageDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
fun ShortcutCard(
  item: MenuItem,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
      .clickable { item.onClick() }
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(
        imageVector = item.icon,
        contentDescription = item.title,
        tint = item.iconColor,
        modifier = Modifier.size(28.dp)
      )
      Text(
        text = item.title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
      )
    }
  }
}
