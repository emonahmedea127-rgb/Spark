package com.example.sociva.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sociva.data.model.IdentityType
import com.example.sociva.data.model.PostAudience
import com.example.sociva.data.model.User
import com.example.sociva.data.service.UploadState
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.MediaPickerActionBar
import com.example.sociva.ui.components.SelectedMediaThumbnailItem
import com.example.sociva.ui.components.UserAvatar
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostComposerScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  val currentUser by viewModel.currentUser.collectAsState()
  val activeIdentity by viewModel.activeIdentity.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val pendingUrisFromVm by viewModel.pendingPostUris.collectAsState()
  val uploadState by viewModel.uploadState.collectAsState()
  val context = LocalContext.current

  var postText by remember { mutableStateOf("") }
  var selectedAudience by remember { mutableStateOf(PostAudience.PUBLIC) }
  var selectedFeeling by remember { mutableStateOf<String?>(null) }
  var taggedUsers by remember { mutableStateOf<List<User>>(emptyList()) }
  var showTagPeopleDialog by remember { mutableStateOf(false) }
  var showAudienceDropdown by remember { mutableStateOf(false) }
  var showFeelingPicker by remember { mutableStateOf(false) }
  var isCreatingStory by remember { mutableStateOf(false) }
  var selectedGradientIndex by remember { mutableStateOf(0) }

  // Media URIs state (synced with pending post URIs from direct picker)
  var localSelectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

  LaunchedEffect(pendingUrisFromVm) {
    if (pendingUrisFromVm.isNotEmpty()) {
      localSelectedUris = pendingUrisFromVm
    }
  }

  // Native Multiple Photo & Video Picker (Android PhotoPicker / Gallery)
  val multiMediaPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      val combined = (localSelectedUris + uris).distinct()
      localSelectedUris = combined
      viewModel.setPendingPostUris(combined)
    }
  }

  // Camera capture fallback launcher
  val takePhotoLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { bitmap ->
    if (bitmap != null) {
      val uri = viewModel.saveBitmapToTempUri(bitmap)
      if (uri != null) {
        val combined = localSelectedUris + uri
        localSelectedUris = combined
        viewModel.setPendingPostUris(combined)
      }
    }
  }

  val gradients = listOf(
    listOf(Color(0xFF2563EB), Color(0xFF7C3AED)),
    listOf(Color(0xFFEC4899), Color(0xFFF43F5E)),
    listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)),
    listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)),
    listOf(Color(0xFF10B981), Color(0xFF06B6D4))
  )

  val isUploading = uploadState is UploadState.Uploading || uploadState is UploadState.Validating

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            if (isCreatingStory) "Create Story" else "Create Post",
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(
            onClick = {
              viewModel.clearPendingPostUris()
              onBack()
            },
            modifier = Modifier.testTag("composer_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          Button(
            onClick = {
              if (isCreatingStory) {
                val firstUri = localSelectedUris.firstOrNull()
                if (firstUri != null) {
                  val mime = context.contentResolver.getType(firstUri) ?: ""
                  viewModel.uploadAndCreateStory(
                    uri = firstUri,
                    text = postText,
                    gradientIndex = selectedGradientIndex,
                    isVideo = mime.startsWith("video")
                  )
                } else {
                  viewModel.createStory(
                    text = postText,
                    mediaUrl = null,
                    gradientIndex = selectedGradientIndex
                  )
                }
              } else {
                viewModel.uploadAndCreatePost(
                  content = postText,
                  uris = localSelectedUris,
                  feeling = selectedFeeling,
                  audience = selectedAudience,
                  taggedUserIds = taggedUsers.map { it.id }
                )
              }
            },
            enabled = !isUploading && (postText.isNotBlank() || localSelectedUris.isNotEmpty()),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = SocivaBlue,
              disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
              .padding(end = 12.dp)
              .testTag("publish_post_button")
          ) {
            Text(
              if (isCreatingStory) "Share Story" else "Publish",
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
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
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Switch between "Feed Post" and "24h Story"
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .padding(4.dp),
        horizontalArrangement = Arrangement.Center
      ) {
        TabButton(
          title = "Feed Post",
          isSelected = !isCreatingStory,
          onClick = { isCreatingStory = false },
          modifier = Modifier.weight(1f)
        )
        TabButton(
          title = "24h Story",
          isSelected = isCreatingStory,
          onClick = { isCreatingStory = true },
          modifier = Modifier.weight(1f)
        )
      }

      // Author Info Row
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        UserAvatar(
          avatarUrl = if (!isCreatingStory) activeIdentity.avatarUrl else currentUser?.avatarUrl,
          name = if (!isCreatingStory) activeIdentity.name else (currentUser?.fullName ?: "User"),
          size = 46.dp
        )

        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = if (!isCreatingStory) activeIdentity.name else (currentUser?.fullName ?: "User"),
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
            if (taggedUsers.isNotEmpty()) {
              Text(
                text = " with ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = if (taggedUsers.size == 1) taggedUsers.first().fullName else "${taggedUsers.first().fullName} +${taggedUsers.size - 1}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = SocivaBlue
              )
            }
          }

          if (!isCreatingStory) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Identity Switcher Chip
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = when (activeIdentity.type) {
                  IdentityType.PERSONAL -> MaterialTheme.colorScheme.surfaceVariant
                  IdentityType.PAGE -> Color(0xFF10B981).copy(alpha = 0.15f)
                  IdentityType.GROUP -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                },
                modifier = Modifier
                  .height(28.dp)
                  .clickable { viewModel.showIdentitySwitcher() }
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = when (activeIdentity.type) {
                      IdentityType.PERSONAL -> Icons.Default.Person
                      IdentityType.PAGE -> Icons.Default.Flag
                      IdentityType.GROUP -> Icons.Default.Groups
                    },
                    contentDescription = null,
                    tint = when (activeIdentity.type) {
                      IdentityType.PERSONAL -> SocivaBlue
                      IdentityType.PAGE -> Color(0xFF10B981)
                      IdentityType.GROUP -> Color(0xFF8B5CF6)
                    },
                    modifier = Modifier.size(12.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = when (activeIdentity.type) {
                      IdentityType.PERSONAL -> "Personal"
                      IdentityType.PAGE -> "Page"
                      IdentityType.GROUP -> "Group"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                  )
                  Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                  )
                }
              }

              // Audience Selector Chip
              Box {
              OutlinedButton(
                onClick = { showAudienceDropdown = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(28.dp)
              ) {
                Icon(
                  imageVector = when (selectedAudience) {
                    PostAudience.FRIENDS -> Icons.Default.Group
                    PostAudience.ONLY_ME -> Icons.Default.Lock
                    else -> Icons.Default.Public
                  },
                  contentDescription = null,
                  modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = selectedAudience.label,
                  style = MaterialTheme.typography.labelSmall
                )
                Icon(
                  imageVector = Icons.Default.ArrowDropDown,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp)
                )
              }

              DropdownMenu(
                expanded = showAudienceDropdown,
                onDismissRequest = { showAudienceDropdown = false }
              ) {
                PostAudience.values().forEach { aud ->
                  DropdownMenuItem(
                    text = { Text(aud.label) },
                    onClick = {
                      selectedAudience = aud
                      showAudienceDropdown = false
                    },
                    leadingIcon = {
                      Icon(
                        imageVector = when (aud) {
                          PostAudience.FRIENDS -> Icons.Default.Group
                          PostAudience.ONLY_ME -> Icons.Default.Lock
                          else -> Icons.Default.Public
                        },
                        contentDescription = null
                      )
                    }
                  )
                }
              }
            }
          }
          } else {
            Text(
              text = "Expires in 24 hours",
              style = MaterialTheme.typography.labelSmall,
              color = SocivaPurple
            )
          }
        }
      }

      // Feeling / Activity tag if selected
      if (selectedFeeling != null) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SocivaPurple.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Text(
            text = selectedFeeling ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = SocivaPurple
          )
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove feeling",
            modifier = Modifier
              .size(14.dp)
              .clickable { selectedFeeling = null },
            tint = SocivaPurple
          )
        }
      }

      // Tagged Users Chips Preview
      if (taggedUsers.isNotEmpty()) {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(taggedUsers) { taggedUser ->
            InputChip(
              selected = true,
              onClick = {},
              label = { Text(taggedUser.fullName, fontSize = 12.sp) },
              trailingIcon = {
                Icon(
                  Icons.Default.Close,
                  contentDescription = "Remove tag",
                  modifier = Modifier
                    .size(16.dp)
                    .clickable { taggedUsers = taggedUsers.filter { it.id != taggedUser.id } }
                )
              }
            )
          }
        }
      }

      // Main Text Input Area
      OutlinedTextField(
        value = postText,
        onValueChange = { postText = it },
        placeholder = {
          Text(
            if (isCreatingStory) "Write your story message..."
            else "What's on your mind, ${currentUser?.fullName?.substringBefore(" ") ?: ""}?"
          )
        },
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 120.dp, max = 220.dp)
          .testTag("post_text_input"),
        colors = OutlinedTextFieldDefaults.colors(
          unfocusedBorderColor = Color.Transparent,
          focusedBorderColor = Color.Transparent
        )
      )

      // Story Gradient Picker if in Story Mode
      if (isCreatingStory) {
        Text(
          text = "Story Background Palette",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          gradients.forEachIndexed { index, grad ->
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(grad))
                .border(
                  width = if (selectedGradientIndex == index) 3.dp else 1.dp,
                  color = if (selectedGradientIndex == index) MaterialTheme.colorScheme.primary else Color.Transparent,
                  shape = CircleShape
                )
                .clickable { selectedGradientIndex = index }
            )
          }
        }
      }

      // Native Selected Media Preview (Photos / Videos)
      if (localSelectedUris.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Selected Media (${localSelectedUris.size})",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
          )
          TextButton(
            onClick = {
              multiMediaPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
              )
            }
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add More")
          }
        }

        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("selected_media_gallery_preview")
        ) {
          items(localSelectedUris) { uri ->
            val mime = context.contentResolver.getType(uri) ?: ""
            val isVid = mime.startsWith("video")
            SelectedMediaThumbnailItem(
              uri = uri,
              isVideo = isVid,
              onRemove = {
                val updated = localSelectedUris.filter { it != uri }
                localSelectedUris = updated
                viewModel.removePendingPostUri(uri)
              }
            )
          }
        }
      }

      // Native Media Picker Action Bar (Facebook-Style "Add to your post")
      MediaPickerActionBar(
        onPickMedia = {
          multiMediaPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
          )
        },
        onPickCamera = {
          takePhotoLauncher.launch(null)
        },
        onFeelingClick = {
          showFeelingPicker = true
        },
        onTagPeopleClick = {
          showTagPeopleDialog = true
        }
      )

      // Tag Friends / People Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .clickable { showTagPeopleDialog = true }
          .padding(14.dp)
          .testTag("composer_tag_people_row"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.PersonAdd,
            contentDescription = null,
            tint = Color(0xFF8B5CF6)
          )
          Text(
            text = if (taggedUsers.isEmpty()) "Tag Friends / People" else "Tagged: ${taggedUsers.joinToString { it.fullName }}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
          )
        }
        Icon(
          imageVector = Icons.Default.ChevronRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // Bottom Add Options: Feeling/Activity
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .clickable { showFeelingPicker = true }
          .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.SentimentSatisfiedAlt,
            contentDescription = null,
            tint = Color(0xFFF59E0B)
          )
          Text(
            text = selectedFeeling ?: "Add Feeling / Activity",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
          )
        }
        Icon(
          imageVector = Icons.Default.ChevronRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    // Uploading Indicator Overlay
    if (isUploading) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
      ) {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
          modifier = Modifier.padding(24.dp)
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            CircularProgressIndicator(color = SocivaBlue)
            val label = when (val st = uploadState) {
              is UploadState.Uploading -> "Uploading media... ${(st.progress * 100).toInt()}%"
              is UploadState.Compressing -> "Optimizing..."
              is UploadState.Validating -> "Publishing..."
              else -> "Publishing..."
            }
            Text(
              text = label,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }
    }

    // Expose error state when post creation or upload fails
    if (uploadState is UploadState.Error) {
      val err = uploadState as UploadState.Error
      val userMessage = if (err.message.contains("Firebase", ignoreCase = true) ||
          err.message.contains("Firestore", ignoreCase = true) ||
          err.message.contains("PERMISSION_DENIED", ignoreCase = true) ||
          err.message.contains("Exception", ignoreCase = true)) {
        "Couldn't publish your post. Please try again."
      } else {
        err.message
      }
      AlertDialog(
        onDismissRequest = { viewModel.dismissUploadState() },
        title = { Text("Notice") },
        text = { Text(userMessage) },
        confirmButton = {
          TextButton(onClick = { viewModel.dismissUploadState() }) {
            Text("OK")
          }
        }
      )
    }
  }

  // Feeling Picker Modal Dialog
  if (showFeelingPicker) {
    val feelings = listOf(
      "feeling happy 😊",
      "feeling excited 🎉",
      "feeling peaceful 🌲",
      "feeling creative 🎨",
      "drinking coffee ☕",
      "listening to music 🎧",
      "traveling ✈️",
      "feeling motivated 🚀",
      "working hard 💼",
      "feeling loved ❤️"
    )
    AlertDialog(
      onDismissRequest = { showFeelingPicker = false },
      title = { Text("How are you feeling?", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          feelings.forEach { feeling ->
            Text(
              text = feeling,
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                  selectedFeeling = feeling
                  showFeelingPicker = false
                }
                .padding(vertical = 8.dp, horizontal = 12.dp),
              style = MaterialTheme.typography.bodyLarge
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showFeelingPicker = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // Tag People Dialog
  if (showTagPeopleDialog) {
    TagPeopleDialog(
      allUsers = allUsers.filter { it.id != currentUser?.id },
      currentTagged = taggedUsers,
      onConfirm = { updated ->
        taggedUsers = updated
        showTagPeopleDialog = false
      },
      onDismiss = { showTagPeopleDialog = false }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPeopleDialog(
  allUsers: List<User>,
  currentTagged: List<User>,
  onConfirm: (List<User>) -> Unit,
  onDismiss: () -> Unit
) {
  var searchQuery by remember { mutableStateOf("") }
  var selectedList by remember { mutableStateOf(currentTagged) }

  val filteredUsers = remember(searchQuery, allUsers) {
    if (searchQuery.isBlank()) allUsers
    else allUsers.filter {
      it.fullName.contains(searchQuery, ignoreCase = true) ||
        it.username.contains(searchQuery, ignoreCase = true)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.fillMaxWidth().heightIn(max = 550.dp),
    title = {
      Text(
        text = "Tag Friends",
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleLarge
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search friends by name or @username...") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("tag_search_field"),
          singleLine = true
        )

        if (selectedList.isNotEmpty()) {
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            items(selectedList) { user ->
              InputChip(
                selected = true,
                onClick = { selectedList = selectedList.filter { it.id != user.id } },
                label = { Text(user.fullName, fontSize = 12.sp) },
                trailingIcon = {
                  Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                }
              )
            }
          }
        }

        Divider()

        if (filteredUsers.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("No users found", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            items(filteredUsers, key = { it.id }) { user ->
              val isSelected = selectedList.any { it.id == user.id }
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .clickable {
                    selectedList = if (isSelected) {
                      selectedList.filter { it.id != user.id }
                    } else {
                      selectedList + user
                    }
                  }
                  .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                UserAvatar(
                  avatarUrl = user.avatarUrl,
                  name = user.fullName,
                  size = 36.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                  Text(user.fullName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                  Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Checkbox(
                  checked = isSelected,
                  onCheckedChange = { checked ->
                    selectedList = if (checked) {
                      selectedList + user
                    } else {
                      selectedList.filter { it.id != user.id }
                    }
                  }
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(selectedList) },
        colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue)
      ) {
        Text("Done (${selectedList.size})", fontWeight = FontWeight.Bold)
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
fun TabButton(
  title: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
      .clickable { onClick() }
      .padding(vertical = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
