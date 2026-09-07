package com.example.sociva.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
import com.example.sociva.data.model.ReportItem
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.formatRelativeTime
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaPink
import com.example.ui.theme.SocivaPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  val reports by viewModel.reports.collectAsState()
  val allPosts by viewModel.allPosts.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Spark Admin Console", fontWeight = FontWeight.Bold)
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(14.dp)
        .testTag("admin_screen"),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Analytics & Metrics Dashboard
      item {
        Text("Platform Analytics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            AdminMetricCard(
              title = "Total Users",
              value = "${allUsers.size + 4240}",
              icon = Icons.Default.People,
              color = SocivaBlue,
              modifier = Modifier.weight(1f)
            )
            AdminMetricCard(
              title = "Active Today",
              value = "1,842",
              icon = Icons.Default.CheckCircle,
              color = Color(0xFF10B981),
              modifier = Modifier.weight(1f)
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            AdminMetricCard(
              title = "Total Posts",
              value = "${allPosts.size + 1420}",
              icon = Icons.Default.Article,
              color = SocivaPurple,
              modifier = Modifier.weight(1f)
            )
            AdminMetricCard(
              title = "Pending Reports",
              value = "${reports.size}",
              icon = Icons.Default.Warning,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.weight(1f)
            )
          }
        }
      }

      // 2. Moderation Queue
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Moderation Queue", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          Badge(containerColor = MaterialTheme.colorScheme.error) {
            Text("${reports.size} pending")
          }
        }
      }

      if (reports.isEmpty()) {
        item {
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
          ) {
            Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
              Text("No pending reports in the moderation queue! All clean ✨", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      } else {
        items(reports, key = { it.id }) { report ->
          ReportCard(
            report = report,
            onResolve = { viewModel.resolveReport(report.id) },
            onDeleteContent = { viewModel.deleteReport(report.id) }
          )
        }
      }
    }
  }
}

@Composable
fun AdminMetricCard(
  title: String,
  value: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  color: Color,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    }
  }
}

@Composable
fun ReportCard(
  report: ReportItem,
  onResolve: () -> Unit,
  onDeleteContent: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Box(
            modifier = Modifier
              .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
              .padding(horizontal = 8.dp, vertical = 2.dp)
          ) {
            Text(report.targetType, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }
          Text("Reason: ${report.reason}", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
        }

        Text(
          text = formatRelativeTime(report.timestamp),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Text(
        text = "\"${report.targetTitle}\"",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
      )

      Text(
        text = "Reported by: @${report.reportedBy}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedButton(
          onClick = onResolve,
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
          modifier = Modifier.height(34.dp)
        ) {
          Text("Keep / Dismiss", fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
          onClick = onDeleteContent,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
          modifier = Modifier.height(34.dp)
        ) {
          Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Remove Content", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
