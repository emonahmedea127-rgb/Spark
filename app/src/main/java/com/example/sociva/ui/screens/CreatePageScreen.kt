package com.example.sociva.ui.screens

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
import com.example.sociva.ui.components.UserAvatar
import com.example.ui.theme.SocivaBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePageScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  var name by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("Technology") }
  var description by remember { mutableStateOf("") }
  var username by remember { mutableStateOf("") }
  var website by remember { mutableStateOf("") }
  var location by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }

  val categories = listOf(
    "Technology",
    "Creators & Influencers",
    "Business & Brand",
    "Community",
    "Entertainment",
    "Education"
  )

  val isFormValid = name.trim().isNotBlank() && description.trim().isNotBlank()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Create a Page", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("create_page_back")) {
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
      // Facebook-style introduction banner
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Flag, contentDescription = null, tint = SocivaBlue, modifier = Modifier.size(28.dp))
          Spacer(modifier = Modifier.width(14.dp))
          Column {
            Text("Build Your Brand or Project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
              "Pages are for businesses, brands, organizations, and public figures to connect with their followers on Spark.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // Page Name
      OutlinedTextField(
        value = name,
        onValueChange = {
          name = it
          if (username.isBlank() || username == name.dropLast(1).lowercase().replace(" ", "")) {
            username = it.lowercase().replace(" ", "").filter { c -> c.isLetterOrDigit() }
          }
        },
        label = { Text("Page Name *") },
        placeholder = { Text("e.g., Spark Official Page, My Technology Page") },
        leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("input_page_name")
      )

      // Category Chips
      Text("Category *", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
      OptInCategoryChips(
        categories = categories,
        selectedCategory = category,
        onSelect = { category = it }
      )

      // Bio / Description
      OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Bio / Description *") },
        placeholder = { Text("Tell people what your Page is about...") },
        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
        minLines = 3,
        maxLines = 5,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("input_page_description")
      )

      // Username
      OutlinedTextField(
        value = username,
        onValueChange = { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } },
        label = { Text("Username / Handle (@)") },
        placeholder = { Text("e.g., mytechpage") },
        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )

      // Contact & Web section
      Text("Contact & Details (Optional)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

      OutlinedTextField(
        value = website,
        onValueChange = { website = it },
        label = { Text("Website URL") },
        placeholder = { Text("https://example.com") },
        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )

      OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Contact Email") },
        placeholder = { Text("contact@example.com") },
        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )

      OutlinedTextField(
        value = location,
        onValueChange = { location = it },
        label = { Text("Location / City") },
        placeholder = { Text("e.g., San Francisco, CA") },
        leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Create Button
      Button(
        onClick = {
          viewModel.createPage(
            name = name.trim(),
            category = category,
            description = description.trim(),
            username = username.trim(),
            website = website.trim(),
            location = location.trim(),
            email = email.trim(),
            phone = phone.trim()
          )
        },
        enabled = isFormValid,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("submit_create_page")
      ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Create Page & Switch Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
fun OptInCategoryChips(
  categories: List<String>,
  selectedCategory: String,
  onSelect: (String) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    androidx.compose.foundation.lazy.LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(categories.size) { idx ->
        val cat = categories[idx]
        val isSelected = cat == selectedCategory
        FilterChip(
          selected = isSelected,
          onClick = { onSelect(cat) },
          label = { Text(cat) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SocivaBlue.copy(alpha = 0.15f),
            selectedLabelColor = SocivaBlue
          )
        )
      }
    }
  }
}
