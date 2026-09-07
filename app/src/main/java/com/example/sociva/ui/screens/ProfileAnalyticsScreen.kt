package com.example.sociva.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sociva.data.model.AnalyticsTimeWindow
import com.example.sociva.data.model.Post
import com.example.sociva.data.model.ProfileAnalytics
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.sociva.ui.components.VerifiedBadge
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaIndigo
import com.example.ui.theme.SocivaPurple
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileAnalyticsScreen(
  viewModel: SocivaViewModel,
  onBack: () -> Unit
) {
  var selectedWindow by remember { mutableStateOf(AnalyticsTimeWindow.LAST_7_DAYS) }
  val analyticsState by viewModel.getProfileAnalytics(selectedWindow).collectAsState(initial = null)
  val currentUser by viewModel.currentUser.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Professional Dashboard",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Account Insights & Analytics",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back"
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .background(MaterialTheme.colorScheme.background)
    ) {
      // Time Window Segmented Selector
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        AnalyticsTimeWindow.values().forEach { window ->
          val isSelected = selectedWindow == window
          FilterChip(
            selected = isSelected,
            onClick = { selectedWindow = window },
            label = {
              Text(
                text = window.label,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = SocivaBlue,
              selectedLabelColor = Color.White
            ),
            modifier = Modifier.testTag("time_window_${window.name.lowercase()}")
          )
        }
      }

      val analytics = analyticsState
      if (analytics == null) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            CircularProgressIndicator(color = SocivaBlue)
            Text(
              text = "Gathering database analytics...",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          contentPadding = PaddingValues(bottom = 32.dp)
        ) {
          // Account Summary Header
          item {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_analytics_summary_card"),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
              )
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  UserAvatar(
                    avatarUrl = currentUser?.avatarUrl,
                    name = currentUser?.fullName ?: "User",
                    size = 52.dp
                  )
                  Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = currentUser?.fullName ?: "Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      if (currentUser?.isVerified == true) {
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedBadge(size = 14.dp)
                      }
                    }
                    Text(
                      text = "${selectedWindow.label} performance",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = "${DecimalFormat("#,##0.1").format(analytics.engagementRate)}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = SocivaBlue
                  )
                  Text(
                    text = "Engagement Rate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }

          // Performance Metrics 2x2 Grid
          item {
            Text(
              text = "Content Performance",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                MetricCard(
                  title = "Post Impressions",
                  value = formatCount(analytics.totalPostViews),
                  icon = Icons.Outlined.Visibility,
                  iconTint = SocivaBlue,
                  subtitle = "Total post views",
                  modifier = Modifier.weight(1f).testTag("profile_total_post_views")
                )
                MetricCard(
                  title = "Profile Visits",
                  value = formatCount(analytics.profileVisits),
                  icon = Icons.Outlined.AccountCircle,
                  iconTint = Color(0xFFEC4899),
                  subtitle = "Profile views",
                  modifier = Modifier.weight(1f).testTag("profile_visits_metric")
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                MetricCard(
                  title = "Reactions",
                  value = formatCount(analytics.totalReactions),
                  icon = Icons.Outlined.ThumbUp,
                  iconTint = Color(0xFF1877F2),
                  subtitle = "Post reactions",
                  modifier = Modifier.weight(1f).testTag("profile_reactions_metric")
                )
                MetricCard(
                  title = "Comments",
                  value = formatCount(analytics.totalComments),
                  icon = Icons.Outlined.ChatBubbleOutline,
                  iconTint = Color(0xFF10B981),
                  subtitle = "Comments & replies",
                  modifier = Modifier.weight(1f).testTag("profile_comments_metric")
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                MetricCard(
                  title = "Shares",
                  value = formatCount(analytics.totalShares),
                  icon = Icons.Outlined.Share,
                  iconTint = Color(0xFFF59E0B),
                  subtitle = "Post shares",
                  modifier = Modifier.weight(1f).testTag("profile_shares_metric")
                )
                MetricCard(
                  title = "Followers Gained",
                  value = "+${analytics.followersGained}",
                  icon = Icons.Outlined.PersonAdd,
                  iconTint = SocivaPurple,
                  subtitle = "New connections",
                  modifier = Modifier.weight(1f).testTag("profile_followers_metric")
                )
              }
            }
          }

          // Daily Impressions Trend Chart
          if (analytics.dailyViewsTrend.isNotEmpty()) {
            item {
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("profile_daily_views_trend_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
              ) {
                Column(modifier = Modifier.padding(14.dp)) {
                  Text(
                    text = "Daily Post Views Trend",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Spacer(modifier = Modifier.height(12.dp))

                  val maxTrendVal = (analytics.dailyViewsTrend.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)

                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(120.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                  ) {
                    analytics.dailyViewsTrend.forEach { (label, count) ->
                      Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                      ) {
                        Text(
                          text = if (count > 0) "$count" else "",
                          style = MaterialTheme.typography.labelSmall,
                          fontSize = 9.sp,
                          fontWeight = FontWeight.Bold,
                          color = SocivaBlue
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val factor = (count.toFloat() / maxTrendVal.toFloat()).coerceIn(0.08f, 1f)
                        Box(
                          modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height((75 * factor).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                              if (count > 0) Brush.verticalGradient(
                                listOf(SocivaBlue, SocivaIndigo)
                              ) else Brush.verticalGradient(
                                listOf(
                                  MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                  MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                )
                              )
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                          text = label,
                          style = MaterialTheme.typography.labelSmall,
                          fontSize = 9.sp,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          // Top Performing Posts
          item {
            Text(
              text = "Top Performing Posts",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
          }

          if (analytics.bestPerformingPosts.isEmpty()) {
            item {
              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "No posts found for this time period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          } else {
            items(analytics.bestPerformingPosts, key = { it.id }) { post ->
              BestPostItem(
                post = post,
                onViewAnalytics = {
                  viewModel.openPostAnalytics(post.id)
                }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BestPostItem(
  post: Post,
  onViewAnalytics: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("best_post_item_${post.id}")
      .clip(RoundedCornerShape(12.dp))
      .clickable { onViewAnalytics() },
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = post.content.ifBlank { "Photo/Media post" },
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        IconButton(
          onClick = onViewAnalytics,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Insights,
            contentDescription = "Post Analytics",
            tint = SocivaBlue,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            Icons.Outlined.ThumbUp,
            contentDescription = null,
            tint = Color(0xFF1877F2),
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = "${post.likesCount}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            tint = Color(0xFF10B981),
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = "${post.commentsCount}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            Icons.Outlined.Share,
            contentDescription = null,
            tint = Color(0xFFF59E0B),
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = "${post.sharesCount}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
          text = "View Analytics",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = SocivaBlue
        )
      }
    }
  }
}

private fun formatCount(count: Int): String {
  return when {
    count >= 1_000_000 -> "${DecimalFormat("#,##0.1").format(count / 1_000_000.0)}M"
    count >= 1_000 -> "${DecimalFormat("#,##0.#").format(count / 1_000.0)}K"
    else -> DecimalFormat("#,##0").format(count)
  }
}
