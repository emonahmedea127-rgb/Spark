package com.example.sociva.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sociva.data.model.User
import com.example.sociva.data.model.StructuredLocation
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.*
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaIndigo
import com.example.ui.theme.SocivaPurple
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  val currentUser by viewModel.currentUser.collectAsState()
  val friends by viewModel.friends.collectAsState(initial = emptyList())
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  if (currentUser == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(color = SocivaBlue)
    }
    return
  }

  val user = currentUser!!

  // Section A: Basic Info
  var firstName by remember { mutableStateOf(user.firstName.ifBlank { user.fullName.split(" ").firstOrNull().orEmpty() }) }
  var lastName by remember { mutableStateOf(user.lastName.ifBlank { user.fullName.split(" ").drop(1).joinToString(" ") }) }
  var username by remember { mutableStateOf(user.username) }
  var bio by remember { mutableStateOf(user.bio) }
  var pronouns by remember { mutableStateOf(user.pronouns) }
  var nickname by remember { mutableStateOf(user.nickname) }
  var otherNames by remember { mutableStateOf(user.otherNames) }

  // Section B: Personal Info
  var dateOfBirth by remember { mutableStateOf(user.dateOfBirth) }
  var gender by remember { mutableStateOf(user.gender) }
  var interestedIn by remember { mutableStateOf(user.interestedIn) }
  var hometown by remember { mutableStateOf(user.hometown) }
  var hometownRegion by remember { mutableStateOf(user.hometownRegion) }
  var hometownCountryCode by remember { mutableStateOf(user.hometownCountryCode) }
  var hometownLatitude by remember { mutableStateOf(user.hometownLatitude) }
  var hometownLongitude by remember { mutableStateOf(user.hometownLongitude) }

  var currentCity by remember { mutableStateOf(user.currentCity) }
  var currentRegion by remember { mutableStateOf(user.currentRegion) }
  var currentCountryCode by remember { mutableStateOf(user.currentCountryCode) }
  var currentLatitude by remember { mutableStateOf(user.currentLatitude) }
  var currentLongitude by remember { mutableStateOf(user.currentLongitude) }

  var country by remember { mutableStateOf(user.country) }
  var countryCode by remember { mutableStateOf(user.countryCode) }

  // Modal Visibility
  var showCurrentCityPicker by remember { mutableStateOf(false) }
  var showHometownPicker by remember { mutableStateOf(false) }
  var showCountryPicker by remember { mutableStateOf(false) }
  var showDobPicker by remember { mutableStateOf(false) }
  var isSaving by remember { mutableStateOf(false) }

  // Privacy Settings
  var birthdayPrivacy by remember { mutableStateOf(user.birthdayPrivacy) }
  var hometownPrivacy by remember { mutableStateOf(user.hometownPrivacy) }
  var currentCityPrivacy by remember { mutableStateOf(user.currentCityPrivacy) }
  var relationshipPrivacy by remember { mutableStateOf(user.relationshipPrivacy) }
  var emailPrivacy by remember { mutableStateOf(user.emailPrivacy) }

  // Relationship
  var relationshipStatus by remember { mutableStateOf(user.relationshipStatus) }
  var selectedPartnerId by remember { mutableStateOf(user.relationshipPartnerId) }
  var selectedPartnerName by remember { mutableStateOf(user.relationshipPartnerName) }
  var customRelationshipText by remember { mutableStateOf(user.customRelationshipText.orEmpty()) }
  var showPartnerPickerDialog by remember { mutableStateOf(false) }

  // Section C: Work and Education
  var workplace by remember { mutableStateOf(user.workplace) }
  var workPosition by remember { mutableStateOf(user.workPosition) }
  var workStartDate by remember { mutableStateOf(user.workStartDate) }
  var workEndDate by remember { mutableStateOf(user.workEndDate) }

  var school by remember { mutableStateOf(user.school) }
  var college by remember { mutableStateOf(user.college) }
  var university by remember { mutableStateOf(user.university) }
  var degree by remember { mutableStateOf(user.degree) }
  var fieldOfStudy by remember { mutableStateOf(user.fieldOfStudy) }
  var graduationYear by remember { mutableStateOf(user.graduationYear) }

  // Section D: Contact Info
  var website by remember { mutableStateOf(user.website) }
  var email by remember { mutableStateOf(user.email) }
  var phone by remember { mutableStateOf(user.phone) }

  // Section E: Privacy and Permissions
  var taggingPermission by remember { mutableStateOf(user.taggingPermission) }
  var reviewTagsBeforeAppearing by remember { mutableStateOf(user.reviewTagsBeforeAppearing) }

  // Photo Cropper and Pickers
  var croppingBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var currentCropType by remember { mutableStateOf(CropType.PROFILE) }

  val avatarPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      coroutineScope.launch {
        try {
          val inputStream = context.contentResolver.openInputStream(uri)
          val bitmap = BitmapFactory.decodeStream(inputStream)
          inputStream?.close()
          if (bitmap != null) {
            currentCropType = CropType.PROFILE
            croppingBitmap = bitmap
          }
        } catch (e: Exception) {
          viewModel.showToast("Failed to load photo: ${e.localizedMessage}")
        }
      }
    }
  }

  val coverPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      coroutineScope.launch {
        try {
          val inputStream = context.contentResolver.openInputStream(uri)
          val bitmap = BitmapFactory.decodeStream(inputStream)
          inputStream?.close()
          if (bitmap != null) {
            currentCropType = CropType.COVER
            croppingBitmap = bitmap
          }
        } catch (e: Exception) {
          viewModel.showToast("Failed to load photo: ${e.localizedMessage}")
        }
      }
    }
  }

  fun saveProfile() {
    if (firstName.isBlank() && lastName.isBlank()) {
      viewModel.showToast("Please enter a name")
      return
    }

    if (isSaving) return
    isSaving = true

    coroutineScope.launch {
      val resolvedRelationshipStatus = if (relationshipStatus == "Not specified") "" else relationshipStatus
      val updatedUser = user.copy(
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        fullName = "${firstName.trim()} ${lastName.trim()}".trim().ifBlank { user.fullName },
        username = username.trim().ifBlank { user.username },
        bio = bio.trim(),
        pronouns = pronouns.trim(),
        nickname = nickname.trim(),
        otherNames = otherNames.trim(),
        dateOfBirth = dateOfBirth.trim(),
        gender = gender,
        interestedIn = interestedIn,
        hometown = hometown.trim(),
        hometownRegion = hometownRegion.trim(),
        hometownCountryCode = hometownCountryCode.trim(),
        hometownLatitude = hometownLatitude,
        hometownLongitude = hometownLongitude,
        currentCity = currentCity.trim(),
        currentRegion = currentRegion.trim(),
        currentCountryCode = currentCountryCode.trim(),
        currentLatitude = currentLatitude,
        currentLongitude = currentLongitude,
        country = country.trim(),
        countryCode = countryCode.trim(),
        birthdayPrivacy = birthdayPrivacy,
        hometownPrivacy = hometownPrivacy,
        currentCityPrivacy = currentCityPrivacy,
        relationshipPrivacy = relationshipPrivacy,
        emailPrivacy = emailPrivacy,
        relationshipStatus = resolvedRelationshipStatus,
        relationshipPartnerId = selectedPartnerId,
        relationshipPartnerName = selectedPartnerName,
        customRelationshipText = customRelationshipText.trim().ifBlank { null },
        workplace = workplace.trim(),
        workPosition = workPosition.trim(),
        workStartDate = workStartDate.trim(),
        workEndDate = workEndDate.trim(),
        school = school.trim(),
        college = college.trim(),
        university = university.trim(),
        degree = degree.trim(),
        fieldOfStudy = fieldOfStudy.trim(),
        graduationYear = graduationYear.trim(),
        website = website.trim(),
        email = email.trim(),
        phone = phone.trim(),
        taggingPermission = taggingPermission,
        reviewTagsBeforeAppearing = reviewTagsBeforeAppearing
      )

      viewModel.saveUserProfile(updatedUser)

      // Handle relationship status changes
      if (resolvedRelationshipStatus == "Single" || resolvedRelationshipStatus.isBlank()) {
        if (user.relationshipStatus.isNotBlank() && user.relationshipStatus != "Single") {
          viewModel.removeRelationship(resolvedRelationshipStatus.ifBlank { "Single" })
        }
      } else {
        if (selectedPartnerId != null && selectedPartnerId != user.relationshipPartnerId) {
          viewModel.sendRelationshipRequest(
            targetUserId = selectedPartnerId!!,
            relationshipType = resolvedRelationshipStatus,
            customText = customRelationshipText.ifBlank { null },
            privacy = relationshipPrivacy
          )
        }
      }

      isSaving = false
      onBack()
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Edit Profile",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("edit_profile_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          TextButton(
            onClick = { saveProfile() },
            enabled = !isSaving,
            modifier = Modifier.testTag("save_profile_top_btn")
          ) {
            if (isSaving) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), color = SocivaBlue, strokeWidth = 2.dp)
            } else {
              Text(
                text = "Save",
                color = SocivaBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
              )
            }
          }
        }
      )
    },
    bottomBar = {
      Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding()
        ) {
          Button(
            onClick = { saveProfile() },
            enabled = !isSaving,
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("save_profile_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue)
          ) {
            if (isSaving) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Saving...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            } else {
              Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
          }
        }
      }
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
        .testTag("edit_profile_scroll_container"),
      verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

      // ==========================================
      // SECTION A: PHOTOS & BASIC INFORMATION
      // ==========================================
      SectionHeader(
        title = "Profile Photos",
        icon = Icons.Outlined.PhotoCamera,
        subtitle = "Update your public avatar and header cover"
      )

      // Photos Preview & Edit
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          // Cover Photo Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Cover Photo", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
              Text("Header photo on profile", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
              onClick = {
                coverPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
              },
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
              Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Change")
            }
          }

          // Cover Preview
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(110.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(
                Brush.horizontalGradient(listOf(SocivaBlue.copy(alpha = 0.7f), SocivaIndigo.copy(alpha = 0.7f)))
              )
          ) {
            if (!user.coverUrl.isNullOrBlank()) {
              AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                  .data(user.coverUrl)
                  .crossfade(true)
                  .build(),
                contentDescription = "Cover Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
              )
            }
          }

          Divider()

          // Profile Picture Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              Box(
                modifier = Modifier
                  .size(64.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.surfaceVariant)
                  .border(2.dp, SocivaBlue, CircleShape)
              ) {
                AsyncImage(
                  model = ImageRequest.Builder(LocalContext.current)
                    .data(user.avatarUrl)
                    .crossfade(true)
                    .build(),
                  contentDescription = "Profile Picture",
                  modifier = Modifier.fillMaxSize(),
                  contentScale = ContentScale.Crop
                )
              }
              Column {
                Text("Profile Picture", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text("Visible to everyone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            Button(
              onClick = {
                avatarPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
              },
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
              Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Change")
            }
          }
        }
      }

      SectionHeader(
        title = "Basic Information",
        icon = Icons.Outlined.Person,
        subtitle = "Your primary identity details"
      )

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = firstName,
          onValueChange = { firstName = it },
          label = { Text("First Name *") },
          modifier = Modifier.weight(1f).testTag("input_first_name"),
          shape = RoundedCornerShape(12.dp),
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )
        OutlinedTextField(
          value = lastName,
          onValueChange = { lastName = it },
          label = { Text("Last Name") },
          modifier = Modifier.weight(1f).testTag("input_last_name"),
          shape = RoundedCornerShape(12.dp),
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )
      }

      OutlinedTextField(
        value = username,
        onValueChange = { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '.' } },
        label = { Text("Username") },
        prefix = { Text("@", color = SocivaBlue, fontWeight = FontWeight.Bold) },
        modifier = Modifier.fillMaxWidth().testTag("input_username"),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
      )

      OutlinedTextField(
        value = bio,
        onValueChange = { bio = it },
        label = { Text("Bio") },
        placeholder = { Text("Tell everyone a little bit about yourself...") },
        modifier = Modifier.fillMaxWidth().testTag("input_bio"),
        shape = RoundedCornerShape(12.dp),
        minLines = 3,
        maxLines = 5
      )

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = pronouns,
          onValueChange = { pronouns = it },
          label = { Text("Pronouns (Optional)") },
          placeholder = { Text("e.g. they/them, she/her") },
          modifier = Modifier.weight(1f).testTag("input_pronouns"),
          shape = RoundedCornerShape(12.dp),
          singleLine = true
        )
        OutlinedTextField(
          value = nickname,
          onValueChange = { nickname = it },
          label = { Text("Nickname") },
          modifier = Modifier.weight(1f).testTag("input_nickname"),
          shape = RoundedCornerShape(12.dp),
          singleLine = true
        )
      }

      OutlinedTextField(
        value = otherNames,
        onValueChange = { otherNames = it },
        label = { Text("Other Names") },
        placeholder = { Text("Maiden name, former name, etc.") },
        modifier = Modifier.fillMaxWidth().testTag("input_other_names"),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
      )

      // ==========================================
      // SECTION B: PERSONAL INFORMATION
      // ==========================================
      SectionHeader(
        title = "Personal Information",
        icon = Icons.Outlined.Info,
        subtitle = "Demographics, origins, and relationship status"
      )

      // Date of Birth & Privacy
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Date of Birth", fontWeight = FontWeight.Medium)
          PrivacyBadgeSelector(
            currentPrivacy = birthdayPrivacy,
            onPrivacySelected = { birthdayPrivacy = it }
          )
        }

        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { showDobPicker = true }
            .testTag("input_dob"),
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(Icons.Outlined.Cake, contentDescription = null, tint = SocivaBlue)
              if (dateOfBirth.isNotBlank()) {
                Text(
                  text = dateOfBirth,
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface
                )
              } else {
                Text(
                  text = "Select Date of Birth",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            if (dateOfBirth.isNotBlank()) {
              IconButton(
                onClick = { dateOfBirth = "" },
                modifier = Modifier.size(28.dp)
              ) {
                Icon(Icons.Default.Close, contentDescription = "Clear Birthday", modifier = Modifier.size(16.dp))
              }
            } else {
              Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = SocivaBlue, modifier = Modifier.size(20.dp))
            }
          }
        }
      }

      // Gender Selector
      DropdownField(
        label = "Gender",
        selectedValue = gender,
        options = listOf("Male", "Female", "Non-binary", "Custom", "Prefer not to say"),
        onSelect = { gender = it }
      )

      // Interested In
      DropdownField(
        label = "Interested in",
        selectedValue = interestedIn,
        options = listOf("All", "Men", "Women", "None"),
        onSelect = { interestedIn = it }
      )

      // Relationship Status & Partner
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Relationship Status", fontWeight = FontWeight.Medium)
          PrivacyBadgeSelector(
            currentPrivacy = relationshipPrivacy,
            onPrivacySelected = { relationshipPrivacy = it }
          )
        }

        val relationshipOptions = listOf(
          "Not specified",
          "Single",
          "In a relationship",
          "Engaged",
          "Married",
          "In a civil partnership",
          "In a domestic partnership",
          "In an open relationship",
          "It's complicated",
          "Separated",
          "Divorced",
          "Widowed"
        )

        DropdownField(
          label = "Status",
          selectedValue = if (relationshipStatus.isBlank()) "Not specified" else relationshipStatus,
          options = relationshipOptions,
          onSelect = { status ->
            if (status == "Not specified" || status == "Single") {
              relationshipStatus = if (status == "Single") "Single" else ""
              selectedPartnerId = null
              selectedPartnerName = null
              customRelationshipText = ""
            } else {
              relationshipStatus = status
            }
          }
        )

        // If in a non-single relationship: Partner Picker or Custom Partner
        AnimatedVisibility(visible = relationshipStatus.isNotBlank() && relationshipStatus != "Single" && relationshipStatus != "Not specified") {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SocivaPurple.copy(alpha = 0.08f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, SocivaPurple.copy(alpha = 0.3f))
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Icon(Icons.Filled.Favorite, contentDescription = null, tint = SocivaPurple, modifier = Modifier.size(18.dp))
                  Text("Relationship Partner", fontWeight = FontWeight.SemiBold, color = SocivaPurple)
                }

                TextButton(
                  onClick = { showPartnerPickerDialog = true },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Text(if (selectedPartnerId == null) "Select Friend" else "Change")
                }
              }

              if (selectedPartnerName != null) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = SocivaBlue)
                    Column {
                      Text(selectedPartnerName!!, fontWeight = FontWeight.Bold)
                      Text("Partner link will require approval", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                  IconButton(
                    onClick = {
                      selectedPartnerId = null
                      selectedPartnerName = null
                    },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove Partner", tint = MaterialTheme.colorScheme.error)
                  }
                }
              }

              OutlinedTextField(
                value = customRelationshipText,
                onValueChange = { customRelationshipText = it },
                label = { Text("Custom Relationship Note / Partner Name") },
                placeholder = { Text("Or describe your partner here...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
              )

              Text(
                text = "ℹ️ Tagging a Spark friend sends a relationship request. Once approved, it appears reciprocally on both profiles.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      // Location Section: Current City, Hometown & Country
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Current City
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Current City", fontWeight = FontWeight.Medium)
            PrivacyBadgeSelector(
              currentPrivacy = currentCityPrivacy,
              onPrivacySelected = { currentCityPrivacy = it }
            )
          }

          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { showCurrentCityPicker = true }
              .testTag("input_current_city"),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
              ) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = SocivaBlue)
                if (currentCity.isNotBlank()) {
                  Column {
                    Text(
                      text = currentCity,
                      style = MaterialTheme.typography.bodyLarge,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    if (currentRegion.isNotBlank() || currentCountryCode.isNotBlank()) {
                      Text(
                        text = listOf(currentRegion, currentCountryCode).filter { it.isNotBlank() }.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                } else {
                  Text(
                    text = "Pick current city on map / search",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentCity.isNotBlank()) {
                  IconButton(
                    onClick = {
                      currentCity = ""
                      currentRegion = ""
                      currentCountryCode = ""
                      currentLatitude = null
                      currentLongitude = null
                    },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(Icons.Default.Close, contentDescription = "Clear City", modifier = Modifier.size(16.dp))
                  }
                }
                Icon(Icons.Outlined.Map, contentDescription = "Open Map", tint = SocivaBlue, modifier = Modifier.size(20.dp))
              }
            }
          }
        }

        // Hometown
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Hometown", fontWeight = FontWeight.Medium)
            PrivacyBadgeSelector(
              currentPrivacy = hometownPrivacy,
              onPrivacySelected = { hometownPrivacy = it }
            )
          }

          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { showHometownPicker = true }
              .testTag("input_hometown"),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
              ) {
                Icon(Icons.Outlined.Home, contentDescription = null, tint = SocivaIndigo)
                if (hometown.isNotBlank()) {
                  Column {
                    Text(
                      text = hometown,
                      style = MaterialTheme.typography.bodyLarge,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    if (hometownRegion.isNotBlank() || hometownCountryCode.isNotBlank()) {
                      Text(
                        text = listOf(hometownRegion, hometownCountryCode).filter { it.isNotBlank() }.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                } else {
                  Text(
                    text = "Pick hometown on map / search",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (hometown.isNotBlank()) {
                  IconButton(
                    onClick = {
                      hometown = ""
                      hometownRegion = ""
                      hometownCountryCode = ""
                      hometownLatitude = null
                      hometownLongitude = null
                    },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Hometown", modifier = Modifier.size(16.dp))
                  }
                }
                Icon(Icons.Outlined.Map, contentDescription = "Open Map", tint = SocivaIndigo, modifier = Modifier.size(20.dp))
              }
            }
          }
        }

        // Country
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Country", fontWeight = FontWeight.Medium)
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { showCountryPicker = true }
              .testTag("input_country"),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
              ) {
                val flag = remember(country, countryCode) {
                  CountryHelper.getFlagForCountry(country)
                }
                if (country.isNotBlank()) {
                  Text(flag, fontSize = 22.sp)
                  Column {
                    Text(
                      text = country,
                      style = MaterialTheme.typography.bodyLarge,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    if (countryCode.isNotBlank()) {
                      Text(
                        text = "Code: $countryCode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                } else {
                  Icon(Icons.Outlined.Public, contentDescription = null, tint = SocivaBlue)
                  Text(
                    text = "Select Country",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (country.isNotBlank()) {
                  IconButton(
                    onClick = {
                      country = ""
                      countryCode = ""
                    },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Country", modifier = Modifier.size(16.dp))
                  }
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Country")
              }
            }
          }
        }
      }

      // ==========================================
      // SECTION C: WORK AND EDUCATION
      // ==========================================
      SectionHeader(
        title = "Work and Education",
        icon = Icons.Outlined.Work,
        subtitle = "Your professional experience and academic background"
      )

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Work Experience", fontWeight = FontWeight.Bold, color = SocivaBlue)

          OutlinedTextField(
            value = workplace,
            onValueChange = { workplace = it },
            label = { Text("Company / Workplace") },
            placeholder = { Text("e.g. Google, Studio Pulse") },
            leadingIcon = { Icon(Icons.Outlined.Business, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().testTag("input_workplace"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
          )

          OutlinedTextField(
            value = workPosition,
            onValueChange = { workPosition = it },
            label = { Text("Job Title / Position") },
            placeholder = { Text("e.g. Design Lead, Software Engineer") },
            leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().testTag("input_work_position"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
          )

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = workStartDate,
              onValueChange = { workStartDate = it },
              label = { Text("Start Year") },
              placeholder = { Text("e.g. 2021") },
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(12.dp),
              singleLine = true
            )
            OutlinedTextField(
              value = workEndDate,
              onValueChange = { workEndDate = it },
              label = { Text("End Year") },
              placeholder = { Text("Present") },
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(12.dp),
              singleLine = true
            )
          }
        }
      }

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Education", fontWeight = FontWeight.Bold, color = SocivaIndigo)

          OutlinedTextField(
            value = university,
            onValueChange = { university = it },
            label = { Text("College / University") },
            placeholder = { Text("e.g. Stanford University") },
            leadingIcon = { Icon(Icons.Outlined.School, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().testTag("input_university"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
          )

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = degree,
              onValueChange = { degree = it },
              label = { Text("Degree") },
              placeholder = { Text("e.g. B.S., Ph.D.") },
              modifier = Modifier.weight(1f).testTag("input_degree"),
              shape = RoundedCornerShape(12.dp),
              singleLine = true
            )
            OutlinedTextField(
              value = graduationYear,
              onValueChange = { graduationYear = it },
              label = { Text("Graduation Year") },
              placeholder = { Text("e.g. 2020") },
              modifier = Modifier.weight(1f).testTag("input_grad_year"),
              shape = RoundedCornerShape(12.dp),
              singleLine = true
            )
          }

          OutlinedTextField(
            value = fieldOfStudy,
            onValueChange = { fieldOfStudy = it },
            label = { Text("Field of Study") },
            placeholder = { Text("e.g. Computer Science, Design") },
            modifier = Modifier.fillMaxWidth().testTag("input_field_of_study"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
          )

          OutlinedTextField(
            value = school,
            onValueChange = { school = it },
            label = { Text("High School") },
            placeholder = { Text("Optional high school") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
          )
        }
      }

      // ==========================================
      // SECTION D: CONTACT INFORMATION
      // ==========================================
      SectionHeader(
        title = "Contact Information",
        icon = Icons.Outlined.ContactMail,
        subtitle = "Websites, social links, and communication channels"
      )

      OutlinedTextField(
        value = website,
        onValueChange = { website = it },
        label = { Text("Website / Portfolio") },
        placeholder = { Text("https://yourname.com") },
        leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null, tint = SocivaBlue) },
        modifier = Modifier.fillMaxWidth().testTag("input_website"),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
      )

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Email", fontWeight = FontWeight.Medium)
          PrivacyBadgeSelector(
            currentPrivacy = emailPrivacy,
            onPrivacySelected = { emailPrivacy = it }
          )
        }
        OutlinedTextField(
          value = email,
          onValueChange = { email = it },
          placeholder = { Text("your.email@example.com") },
          leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
          modifier = Modifier.fillMaxWidth().testTag("input_email"),
          shape = RoundedCornerShape(12.dp),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
      }

      OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("Phone Number (Optional)") },
        placeholder = { Text("+1 (555) 000-0000") },
        leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().testTag("input_phone"),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
      )

      // ==========================================
      // SECTION E: PRIVACY AND PERMISSIONS
      // ==========================================
      SectionHeader(
        title = "Privacy and Permissions",
        icon = Icons.Outlined.Security,
        subtitle = "Tagging rules and profile review settings"
      )

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          // Who can tag you
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Who can tag you in posts?", fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("Everyone", "Friends", "No one").forEach { opt ->
                FilterChip(
                  selected = taggingPermission == opt,
                  onClick = { taggingPermission = opt },
                  label = { Text(opt) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SocivaBlue,
                    selectedLabelColor = Color.White
                  )
                )
              }
            }
          }

          Divider()

          // Review tags before appearing
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text("Review tags before appearing", fontWeight = FontWeight.SemiBold)
              Text(
                "When someone tags you in a post, review it before it appears on your profile timeline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = reviewTagsBeforeAppearing,
              onCheckedChange = { reviewTagsBeforeAppearing = it },
              modifier = Modifier.testTag("toggle_review_tags"),
              colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SocivaBlue)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(60.dp))
    }
  }

  // Partner Picker Dialog
  if (showPartnerPickerDialog) {
    Dialog(onDismissRequest = { showPartnerPickerDialog = false }) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 500.dp),
        shape = RoundedCornerShape(16.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Select Partner", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { showPartnerPickerDialog = false }) {
              Icon(Icons.Default.Close, contentDescription = "Close")
            }
          }

          Text(
            "Choose from your Spark friends to link your relationship:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
          )

          if (friends.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
              contentAlignment = Alignment.Center
            ) {
              Text("No friends found yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              items(friends) { friend ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                      selectedPartnerId = friend.id
                      selectedPartnerName = friend.fullName
                      showPartnerPickerDialog = false
                    }
                    .padding(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  AsyncImage(
                    model = friend.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                      .size(44.dp)
                      .clip(CircleShape)
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(friend.fullName, fontWeight = FontWeight.SemiBold)
                    Text("@${friend.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  if (selectedPartnerId == friend.id) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = SocivaBlue)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Image Cropper Modal
  if (croppingBitmap != null) {
    ImageCropperModal(
      sourceBitmap = croppingBitmap!!,
      cropType = currentCropType,
      onCropCompleted = { cropped ->
        croppingBitmap = null
        if (currentCropType == CropType.PROFILE) {
          viewModel.uploadAndSetProfilePicture(cropped)
        } else {
          viewModel.uploadAndSetCoverPhoto(cropped)
        }
      },
      onDismiss = { croppingBitmap = null }
    )
  }

  // Date of Birth Picker Modal
  if (showDobPicker) {
    DateOfBirthPickerModal(
      initialDate = dateOfBirth,
      onDateSelected = { dateOfBirth = it },
      onClearDate = { dateOfBirth = "" },
      onDismiss = { showDobPicker = false }
    )
  }

  // Current City Location Picker Modal
  if (showCurrentCityPicker) {
    LocationPickerModal(
      title = "Current City",
      initialLocation = com.example.sociva.data.model.StructuredLocation(
        city = currentCity,
        region = currentRegion,
        country = country,
        countryCode = currentCountryCode,
        latitude = currentLatitude ?: 0.0,
        longitude = currentLongitude ?: 0.0
      ),
      onLocationConfirmed = { loc ->
        currentCity = loc.city
        currentRegion = loc.region
        currentCountryCode = loc.countryCode
        currentLatitude = loc.latitude
        currentLongitude = loc.longitude
        if (country.isBlank() && loc.country.isNotBlank()) {
          country = loc.country
          countryCode = loc.countryCode
        }
      },
      onClearLocation = {
        currentCity = ""
        currentRegion = ""
        currentCountryCode = ""
        currentLatitude = 0.0
        currentLongitude = 0.0
      },
      onDismiss = { showCurrentCityPicker = false }
    )
  }

  // Hometown Location Picker Modal
  if (showHometownPicker) {
    LocationPickerModal(
      title = "Hometown",
      initialLocation = com.example.sociva.data.model.StructuredLocation(
        city = hometown,
        region = hometownRegion,
        countryCode = hometownCountryCode,
        latitude = hometownLatitude ?: 0.0,
        longitude = hometownLongitude ?: 0.0
      ),
      onLocationConfirmed = { loc ->
        hometown = loc.city
        hometownRegion = loc.region
        hometownCountryCode = loc.countryCode
        hometownLatitude = loc.latitude
        hometownLongitude = loc.longitude
      },
      onClearLocation = {
        hometown = ""
        hometownRegion = ""
        hometownCountryCode = ""
        hometownLatitude = 0.0
        hometownLongitude = 0.0
      },
      onDismiss = { showHometownPicker = false }
    )
  }

  // Country Picker Modal
  if (showCountryPicker) {
    CountryPickerModal(
      selectedCountryName = country,
      selectedCountryCode = countryCode,
      onSelectCountry = { cName, cCode ->
        country = cName
        countryCode = cCode
      },
      onClearCountry = {
        country = ""
        countryCode = ""
      },
      onDismiss = { showCountryPicker = false }
    )
  }
}

@Composable
fun SectionHeader(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  subtitle: String
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.padding(top = 8.dp)
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(SocivaBlue.copy(alpha = 0.12f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = SocivaBlue, modifier = Modifier.size(20.dp))
    }
    Column {
      Text(
        text = title,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
  label: String,
  selectedValue: String,
  options: List<String>,
  onSelect: (String) -> Unit
) {
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded },
    modifier = Modifier.fillMaxWidth()
  ) {
    OutlinedTextField(
      value = selectedValue,
      onValueChange = {},
      readOnly = true,
      label = { Text(label) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .menuAnchor()
        .fillMaxWidth(),
      shape = RoundedCornerShape(12.dp)
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      options.forEach { option ->
        DropdownMenuItem(
          text = { Text(option) },
          onClick = {
            onSelect(option)
            expanded = false
          },
          leadingIcon = {
            if (selectedValue == option) {
              Icon(Icons.Default.Check, contentDescription = null, tint = SocivaBlue)
            }
          }
        )
      }
    }
  }
}

@Composable
fun PrivacyBadgeSelector(
  currentPrivacy: String,
  onPrivacySelected: (String) -> Unit
) {
  var expanded by remember { mutableStateOf(false) }

  Box {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier
        .clip(RoundedCornerShape(16.dp))
        .clickable { expanded = true }
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        val icon = when (currentPrivacy) {
          "Friends" -> Icons.Default.People
          "Only me" -> Icons.Default.Lock
          else -> Icons.Default.Public
        }
        Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = SocivaBlue)
        Text(currentPrivacy, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
      }
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      listOf("Public", "Friends", "Only me").forEach { option ->
        DropdownMenuItem(
          text = { Text(option) },
          onClick = {
            onPrivacySelected(option)
            expanded = false
          },
          leadingIcon = {
            val ic = when (option) {
              "Friends" -> Icons.Default.People
              "Only me" -> Icons.Default.Lock
              else -> Icons.Default.Public
            }
            Icon(ic, contentDescription = null, tint = SocivaBlue)
          }
        )
      }
    }
  }
}
