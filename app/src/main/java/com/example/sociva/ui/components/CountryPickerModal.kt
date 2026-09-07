package com.example.sociva.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerModal(
  selectedCountryName: String,
  selectedCountryCode: String,
  onSelectCountry: (countryName: String, countryCode: String) -> Unit,
  onClearCountry: () -> Unit,
  onDismiss: () -> Unit
) {
  var searchQuery by remember { mutableStateOf("") }
  val countries = remember { CountryHelper.allCountries }

  val filteredCountries = remember(searchQuery, countries) {
    if (searchQuery.isBlank()) {
      countries
    } else {
      val query = searchQuery.trim().lowercase()
      countries.filter {
        it.name.lowercase().contains(query) ||
        it.code.lowercase().contains(query)
      }
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .fillMaxHeight(0.82f)
        .clip(RoundedCornerShape(24.dp))
        .testTag("country_picker_dialog"),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      shadowElevation = 8.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(20.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Select Country",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "${filteredCountries.size} countries available",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_country_picker_btn")
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search country or code (e.g. BD, United States)") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("country_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Clear Selection Row if currently selected
        if (selectedCountryName.isNotBlank() || selectedCountryCode.isNotBlank()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .clickable {
                onClearCountry()
                onDismiss()
              }
              .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "Clear selected country",
              color = MaterialTheme.colorScheme.error,
              fontWeight = FontWeight.Medium,
              fontSize = 14.sp
            )
            Icon(
              Icons.Default.Clear,
              contentDescription = "Clear",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp)
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
        }

        // Country List
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .testTag("country_list_container"),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          items(filteredCountries, key = { it.code }) { country ->
            val isSelected = country.code.equals(selectedCountryCode, ignoreCase = true) ||
                             country.name.equals(selectedCountryName, ignoreCase = true)

            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  onSelectCountry(country.name, country.code)
                  onDismiss()
                }
                .testTag("country_item_${country.code}"),
              color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
              ) {
                // Flag emoji
                Text(
                  text = country.flag.ifBlank { "🌐" },
                  fontSize = 26.sp
                )

                // Name & ISO Code
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = country.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = "ISO: ${country.code}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                if (isSelected) {
                  Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
