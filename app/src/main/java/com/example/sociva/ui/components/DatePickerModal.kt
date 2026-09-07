package com.example.sociva.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import com.example.ui.theme.SocivaBlue
import java.util.Calendar

@Composable
fun DateOfBirthPickerModal(
  initialDate: String,
  onDateSelected: (String) -> Unit,
  onClearDate: () -> Unit,
  onDismiss: () -> Unit
) {
  val currentCalendar = remember { Calendar.getInstance() }
  val currentYear = remember { currentCalendar.get(Calendar.YEAR) }

  val months = remember {
    listOf(
      "January", "February", "March", "April", "May", "June",
      "July", "August", "September", "October", "November", "December"
    )
  }

  // Parse initial date if present (e.g., "May 18, 1996" or "1996-05-18")
  var selectedDay by remember { mutableStateOf(18) }
  var selectedMonthIndex by remember { mutableStateOf(4) } // May
  var selectedYear by remember { mutableStateOf(1996) }

  LaunchedEffect(initialDate) {
    if (initialDate.isNotBlank()) {
      try {
        // Try parsing "Month Day, Year"
        for ((idx, m) in months.withIndex()) {
          if (initialDate.contains(m, ignoreCase = true)) {
            selectedMonthIndex = idx
            break
          }
        }
        val numbers = Regex("\\d+").findAll(initialDate).map { it.value.toInt() }.toList()
        if (numbers.size >= 2) {
          if (numbers[0] in 1..31 && numbers[1] in 1900..currentYear) {
            selectedDay = numbers[0]
            selectedYear = numbers[1]
          } else if (numbers[1] in 1..31 && numbers[0] in 1900..currentYear) {
            selectedYear = numbers[0]
            selectedDay = numbers[1]
          }
        }
      } catch (e: Exception) {
        // Fallback to default
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
        .clip(RoundedCornerShape(24.dp))
        .testTag("dob_picker_dialog"),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      shadowElevation = 8.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Cake, contentDescription = null, tint = SocivaBlue)
            Text(
              text = "Date of Birth",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }

        // Preview banner
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "${months[selectedMonthIndex]} $selectedDay, $selectedYear",
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        // Three columns: Month, Day, Year
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Month
          Column(modifier = Modifier.weight(1.2f)) {
            Text("Month", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(
              modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
              items(months.indices.toList()) { idx ->
                val isSelected = selectedMonthIndex == idx
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedMonthIndex = idx }
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(vertical = 8.dp, horizontal = 10.dp)
                ) {
                  Text(
                    text = months[idx],
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
                  )
                }
              }
            }
          }

          // Day
          Column(modifier = Modifier.weight(0.8f)) {
            Text("Day", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            val daysInMonth = when (selectedMonthIndex) {
              1 -> if (selectedYear % 4 == 0) 29 else 28
              3, 5, 8, 10 -> 30
              else -> 31
            }
            if (selectedDay > daysInMonth) selectedDay = daysInMonth

            LazyColumn(
              modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
              items((1..daysInMonth).toList()) { d ->
                val isSelected = selectedDay == d
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedDay = d }
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "$d",
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
                  )
                }
              }
            }
          }

          // Year
          val years = remember { (currentYear downTo 1920).toList() }
          val yearListState = rememberLazyListState(initialFirstVisibleItemIndex = (currentYear - selectedYear).coerceAtLeast(0))

          Column(modifier = Modifier.weight(1f)) {
            Text("Year", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(
              state = yearListState,
              modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
              items(years) { y ->
                val isSelected = selectedYear == y
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedYear = y }
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "$y",
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
                  )
                }
              }
            }
          }
        }

        // Action Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = {
              onClearDate()
              onDismiss()
            },
            modifier = Modifier.weight(0.4f),
            shape = RoundedCornerShape(12.dp)
          ) {
            Text("Clear")
          }

          Button(
            onClick = {
              val formatted = "${months[selectedMonthIndex]} $selectedDay, $selectedYear"
              onDateSelected(formatted)
              onDismiss()
            },
            modifier = Modifier.weight(0.6f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue)
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Set Birthday", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
