package com.example.sociva.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sociva.data.model.BlockedUser
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.ui.theme.SocivaBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  val settings by viewModel.userSettings.collectAsState()
  val blockedUsers by viewModel.blockedUsers.collectAsState()

  var show2FADialog by remember { mutableStateOf(false) }
  var showPasswordDialog by remember { mutableStateOf(false) }
  var showVisibilityDialog by remember { mutableStateOf(false) }
  var showBlockedUsersSheet by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings & Privacy", fontWeight = FontWeight.Bold) },
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
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Security & Login
      Text("Security & Login", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column {
          SettingSwitchRow(
            title = "Two-Factor Authentication (2FA)",
            subtitle = if (settings.twoFactorEnabled) {
              "Enabled via ${if (settings.twoFactorMethod == "SMS") "SMS verification" else "Authenticator App"}"
            } else {
              "Require security SMS or authenticator code on login"
            },
            icon = Icons.Default.Security,
            checked = settings.twoFactorEnabled,
            onCheckedChange = { willEnable ->
              if (willEnable) {
                show2FADialog = true
              } else {
                viewModel.updateTwoFactor(false)
              }
            },
            tag = "setting_2fa_switch"
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
          val lastPwFormatted = remember(settings.passwordLastUpdated) {
            try {
              SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(settings.passwordLastUpdated))
            } catch (e: Exception) {
              "Recently"
            }
          }
          SettingClickRow(
            title = "Change Password",
            subtitle = "Last updated $lastPwFormatted",
            icon = Icons.Default.Lock,
            onClick = { showPasswordDialog = true },
            tag = "setting_change_password_row"
          )
        }
      }

      // Privacy
      Text("Privacy", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column {
          SettingClickRow(
            title = "Profile Visibility",
            subtitle = when (settings.profileVisibility) {
              "Friends" -> "Friends Only - Only your friends can view your full profile"
              "Only Me" -> "Private - Only you can view your profile details"
              else -> "Public - Anyone on or off Spark can see your profile"
            },
            icon = Icons.Default.Visibility,
            onClick = { showVisibilityDialog = true },
            tag = "setting_visibility_row"
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
          SettingClickRow(
            title = "Blocked Users",
            subtitle = if (blockedUsers.isEmpty()) "0 users blocked" else "${blockedUsers.size} user${if (blockedUsers.size > 1) "s" else ""} blocked",
            icon = Icons.Default.Block,
            onClick = { showBlockedUsersSheet = true },
            tag = "setting_blocked_users_row"
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
          SettingSwitchRow(
            title = "Profile View History",
            subtitle = if (settings.profileViewHistoryEnabled)
              "Control whether your profile visits appear in other people's Profile Visitors list (ON)"
            else
              "Visits are anonymous and hidden from visitor lists (OFF)",
            icon = Icons.Default.RemoveRedEye,
            checked = settings.profileViewHistoryEnabled,
            onCheckedChange = { viewModel.updateProfileViewHistoryEnabled(it) },
            tag = "setting_profile_view_history_switch"
          )
        }
      }

      // Preferences & Media
      Text("Preferences & Media", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column {
          SettingSwitchRow(
            title = "Dark Theme",
            subtitle = if (settings.darkTheme) "Dark theme enabled" else "Light theme enabled (Default)",
            icon = if (settings.darkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
            checked = settings.darkTheme,
            onCheckedChange = { viewModel.toggleTheme(it) },
            tag = "setting_dark_theme_switch"
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
          SettingSwitchRow(
            title = "Data Saver",
            subtitle = if (settings.dataSaver) "Low data mode active (reduced media quality)" else "Standard media resolution on cellular networks",
            icon = Icons.Default.DataSaverOn,
            checked = settings.dataSaver,
            onCheckedChange = { viewModel.updateDataSaver(it) },
            tag = "setting_data_saver_switch"
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
          SettingSwitchRow(
            title = "Push Notifications",
            subtitle = if (settings.pushNotifications) "Receive alerts for reactions, comments, and messages" else "Notifications paused",
            icon = Icons.Default.Notifications,
            checked = settings.pushNotifications,
            onCheckedChange = { viewModel.updatePushNotifications(it) },
            tag = "setting_push_notifications_switch"
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
          SettingSwitchRow(
            title = "In-App Sounds",
            subtitle = if (settings.inAppSounds) "Play sound for message alerts and reactions" else "Sounds muted",
            icon = Icons.Default.VolumeUp,
            checked = settings.inAppSounds,
            onCheckedChange = { viewModel.updateInAppSounds(it) },
            tag = "setting_sounds_switch"
          )
        }
      }

      // About
      Text("About Spark", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text("Spark v2.4.0 (Build 2026)", fontWeight = FontWeight.Bold)
          Text("Ignite Connections. Share Moments.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            "Spark provides dynamic community experiences with real-time conversations, rich post reactions, interactive media stories, and robust personal privacy controls.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }

  // --- Two-Factor Authentication Setup Dialog ---
  if (show2FADialog) {
    TwoFactorSetupDialog(
      onDismiss = { show2FADialog = false },
      onConfirm = { method ->
        show2FADialog = false
        viewModel.updateTwoFactor(true, method)
      }
    )
  }

  // --- Change Password Dialog ---
  if (showPasswordDialog) {
    ChangePasswordDialog(
      onDismiss = { showPasswordDialog = false },
      onConfirm = {
        showPasswordDialog = false
        viewModel.changePassword()
      }
    )
  }

  // --- Profile Visibility Dialog ---
  if (showVisibilityDialog) {
    ProfileVisibilityDialog(
      currentVisibility = settings.profileVisibility,
      onDismiss = { showVisibilityDialog = false },
      onSelect = { newVisibility ->
        showVisibilityDialog = false
        viewModel.updateProfileVisibility(newVisibility)
      }
    )
  }

  // --- Blocked Users Modal Bottom Sheet ---
  if (showBlockedUsersSheet) {
    BlockedUsersModalBottomSheet(
      blockedUsers = blockedUsers,
      onDismiss = { showBlockedUsersSheet = false },
      onUnblock = { targetUserId ->
        viewModel.unblockUser(targetUserId)
      }
    )
  }
}

@Composable
fun SettingSwitchRow(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  tag: String = ""
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
      Column {
        Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = if (tag.isNotBlank()) Modifier.testTag(tag) else Modifier
    )
  }
}

@Composable
fun SettingClickRow(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit,
  tag: String = ""
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .then(if (tag.isNotBlank()) Modifier.testTag(tag) else Modifier)
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
      Column {
        Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
fun TwoFactorSetupDialog(
  onDismiss: () -> Unit,
  onConfirm: (method: String) -> Unit
) {
  var selectedMethod by remember { mutableStateOf("AUTHENTICATOR") }
  var verificationCode by remember { mutableStateOf("") }
  var codeError by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Security, contentDescription = null, tint = SocivaBlue)
        Text("Enable 2-Step Verification", fontWeight = FontWeight.Bold)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          "Choose your preferred two-factor verification method to protect your account:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { selectedMethod = "AUTHENTICATOR" }
            .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          RadioButton(
            selected = selectedMethod == "AUTHENTICATOR",
            onClick = { selectedMethod = "AUTHENTICATOR" }
          )
          Column {
            Text("Authenticator App (Recommended)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text("Google Authenticator, Authy, or Microsoft Authenticator", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { selectedMethod = "SMS" }
            .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          RadioButton(
            selected = selectedMethod == "SMS",
            onClick = { selectedMethod = "SMS" }
          )
          Column {
            Text("SMS Text Message", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text("Receive a 6-digit one-time code via registered phone", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }

        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
          value = verificationCode,
          onValueChange = {
            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
              verificationCode = it
              codeError = null
            }
          },
          label = { Text("6-Digit Verification Code") },
          placeholder = { Text("e.g. 582914") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth().testTag("2fa_code_input"),
          isError = codeError != null,
          supportingText = {
            if (codeError != null) {
              Text(codeError!!, color = MaterialTheme.colorScheme.error)
            } else {
              Text("Enter any 6 digits to verify activation", style = MaterialTheme.typography.labelSmall)
            }
          }
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (verificationCode.length != 6) {
            codeError = "Please enter a valid 6-digit code"
          } else {
            onConfirm(selectedMethod)
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
        modifier = Modifier.testTag("confirm_2fa_button")
      ) {
        Text("Activate 2FA")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
fun ChangePasswordDialog(
  onDismiss: () -> Unit,
  onConfirm: () -> Unit
) {
  var currentPassword by remember { mutableStateOf("") }
  var newPassword by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var showPasswords by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = SocivaBlue)
        Text("Change Password", fontWeight = FontWeight.Bold)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = currentPassword,
          onValueChange = { currentPassword = it; errorMessage = null },
          label = { Text("Current Password") },
          visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation(),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("current_password_input")
        )

        OutlinedTextField(
          value = newPassword,
          onValueChange = { newPassword = it; errorMessage = null },
          label = { Text("New Password (min 6 characters)") },
          visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation(),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("new_password_input")
        )

        OutlinedTextField(
          value = confirmPassword,
          onValueChange = { confirmPassword = it; errorMessage = null },
          label = { Text("Confirm New Password") },
          visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation(),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("confirm_password_input")
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = showPasswords,
            onCheckedChange = { showPasswords = it }
          )
          Text("Show passwords", style = MaterialTheme.typography.bodySmall)
        }

        if (errorMessage != null) {
          Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          when {
            currentPassword.isBlank() -> {
              errorMessage = "Please enter your current password"
            }
            newPassword.length < 6 -> {
              errorMessage = "New password must be at least 6 characters"
            }
            newPassword != confirmPassword -> {
              errorMessage = "New passwords do not match"
            }
            else -> {
              onConfirm()
            }
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
        modifier = Modifier.testTag("save_new_password_button")
      ) {
        Text("Update Password")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
fun ProfileVisibilityDialog(
  currentVisibility: String,
  onDismiss: () -> Unit,
  onSelect: (String) -> Unit
) {
  val options = listOf(
    Triple("Public", "Anyone on or off Spark", Icons.Default.Public),
    Triple("Friends", "Only your confirmed friends", Icons.Default.People),
    Triple("Only Me", "Only you can view your profile", Icons.Default.Lock)
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Visibility, contentDescription = null, tint = SocivaBlue)
        Text("Profile Visibility", fontWeight = FontWeight.Bold)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "Control who can see your profile timeline, friends list, and bio information:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        options.forEach { (option, description, icon) ->
          val isSelected = currentVisibility.equals(option, ignoreCase = true)
          Surface(
            onClick = { onSelect(option) },
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
            modifier = Modifier.fillMaxWidth().testTag("visibility_option_$option")
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Icon(icon, contentDescription = null, tint = if (isSelected) SocivaBlue else MaterialTheme.colorScheme.onSurfaceVariant)
              Column(modifier = Modifier.weight(1f)) {
                Text(option, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = SocivaBlue, modifier = Modifier.size(18.dp))
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersModalBottomSheet(
  blockedUsers: List<BlockedUser>,
  onDismiss: () -> Unit,
  onUnblock: (userId: String) -> Unit
) {
  var userToUnblock by remember { mutableStateOf<BlockedUser?>(null) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text("Blocked Users", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
          Text(
            if (blockedUsers.isEmpty()) "You haven't blocked anyone." else "${blockedUsers.size} blocked contact${if (blockedUsers.size > 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close")
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

      if (blockedUsers.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
            Text("No Blocked Users", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
              "When you block someone, they won't be able to see your posts, message you, or add you as a friend.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 20.dp),
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(blockedUsers, key = { it.id }) { item ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
              ) {
                UserAvatar(
                  avatarUrl = item.blockedUser.avatarUrl,
                  name = item.blockedUser.fullName,
                  size = 44.dp
                )
                Column {
                  Text(item.blockedUser.fullName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                  Text("@${item.blockedUser.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
              OutlinedButton(
                onClick = { userToUnblock = item },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("unblock_button_${item.blockedId}")
              ) {
                Text("Unblock", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      }
    }
  }

  // Unblock confirmation dialog
  if (userToUnblock != null) {
    val target = userToUnblock!!
    AlertDialog(
      onDismissRequest = { userToUnblock = null },
      title = { Text("Unblock ${target.blockedUser.fullName}?") },
      text = {
        Text("They will once again be able to see your public posts, find your profile in search, and send you friend requests or messages.")
      },
      confirmButton = {
        Button(
          onClick = {
            val uid = target.blockedId
            userToUnblock = null
            onUnblock(uid)
          },
          colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue)
        ) {
          Text("Unblock")
        }
      },
      dismissButton = {
        TextButton(onClick = { userToUnblock = null }) {
          Text("Cancel")
        }
      }
    )
  }
}
