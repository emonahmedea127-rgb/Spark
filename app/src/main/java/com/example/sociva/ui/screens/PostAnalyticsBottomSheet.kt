package com.example.sociva.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sociva.data.model.*
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.UserAvatar
import com.example.sociva.ui.components.VerifiedBadge
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaIndigo
import com.example.ui.theme.SocivaPurple
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostAnalyticsBottomSheet(
  postId: String,
  viewModel: SocivaViewModel,
  onDismiss: () -> Unit
) {
  var selectedTimeWindow by remember { mutableStateOf(AnalyticsTimeWindow.ALL_TIME) }
  val analyticsState by viewModel.getPostAnalytics(postId, selectedTimeWindow).collectAsState(initial = null)
  val videoAnalyticsState by viewModel.getVideoAnalytics(postId, selectedTimeWindow).collectAsState(initial = null)
  val allPosts by viewModel.allPosts.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val friends by viewModel.friends.collectAsState()

  val post = remember(allPosts, postId) { allPosts.find { it.id == postId } }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    modifier = Modifier.testTag("post_analytics_sheet")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 28.dp)
    ) {
      // 1. Header Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(listOf(SocivaBlue, SocivaIndigo))
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Insights,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(24.dp)
            )
          }
          Column {
            Text(
              text = "Post Analytics",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Private to you as the post creator",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier.testTag("close_post_analytics_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      HorizontalDivider(
        modifier = Modifier.padding(vertical = 10.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
      )

      // Privacy Check: Only owner can see post analytics
      if (post != null && currentUser != null && post.authorId != currentUser?.id) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(48.dp)
            )
            Text(
              text = "Analytics are Private",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Only the creator of this post can view detailed analytics.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
            Button(
              onClick = onDismiss,
              shape = RoundedCornerShape(10.dp)
            ) {
              Text("Close")
            }
          }
        }
        return@ModalBottomSheet
      }

      val analytics = analyticsState
      if (analytics == null) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            CircularProgressIndicator(
              color = SocivaBlue,
              modifier = Modifier.size(36.dp)
            )
            Text(
              text = "Calculating post analytics...",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          // 2. Post Overview Card
          if (post != null) {
            item {
              PostOverviewCard(post = post)
            }
          }

          // 3. Time Filter Chips (Today, Last 7 days, Last 28 days, All time)
          item {
            TimeFilterRow(
              selected = selectedTimeWindow,
              onSelect = { selectedTimeWindow = it }
            )
          }

          // 4. Hero Engagement Rate Card
          item {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("analytics_hero_card"),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
              )
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Engagement Rate",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "${DecimalFormat("#,##0.1").format(analytics.engagementRate)}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = SocivaBlue
                  )
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = "Based on interactions ÷ reach",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                Box(
                  modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(SocivaBlue.copy(alpha = 0.15f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = SocivaBlue,
                    modifier = Modifier.size(32.dp)
                  )
                }
              }
            }
          }

          // 5. Core Post Metrics Grid
          item {
            Text(
              text = "Core Metrics",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            val totalInteractions = analytics.reactionCount + analytics.commentCount + analytics.shareCount + analytics.saveCount

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                MetricCard(
                  title = "Impressions",
                  value = formatCount(analytics.totalViews),
                  icon = Icons.Outlined.Visibility,
                  iconTint = SocivaBlue,
                  subtitle = "Total post views",
                  modifier = Modifier.weight(1f).testTag("metric_impressions")
                )
                MetricCard(
                  title = "Reach",
                  value = formatCount(analytics.reach),
                  icon = Icons.Outlined.People,
                  iconTint = SocivaIndigo,
                  subtitle = "Unique viewers",
                  modifier = Modifier.weight(1f).testTag("metric_reach")
                )
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                MetricCard(
                  title = "Engagements",
                  value = formatCount(totalInteractions),
                  icon = Icons.Outlined.TouchApp,
                  iconTint = Color(0xFF10B981),
                  subtitle = "Total interactions",
                  modifier = Modifier.weight(1f).testTag("metric_engagements")
                )
                MetricCard(
                  title = "Profile Visits",
                  value = formatCount(analytics.profileVisitCount),
                  icon = Icons.Outlined.AccountCircle,
                  iconTint = Color(0xFFEC4899),
                  subtitle = "From this post",
                  modifier = Modifier.weight(1f).testTag("metric_profile_visits")
                )
              }
            }
          }

          // 6. Engagement Breakdown
          item {
            Text(
              text = "Engagement Breakdown",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp)
            )

            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("engagement_breakdown_card"),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
              )
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                EngagementRow(
                  icon = Icons.Outlined.ThumbUp,
                  label = "Reactions",
                  value = analytics.reactionCount,
                  tint = Color(0xFF1877F2)
                )

                // Reaction Type breakdown chips if available
                if (analytics.reactionBreakdown.isNotEmpty()) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(start = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    analytics.reactionBreakdown.forEach { (type, count) ->
                      Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(vertical = 2.dp)
                      ) {
                        Row(
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                          Text(text = reactionEmojiForType(type), fontSize = 13.sp)
                          Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                          )
                        }
                      }
                    }
                  }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                EngagementRow(
                  icon = Icons.Outlined.ChatBubbleOutline,
                  label = "Comments",
                  value = analytics.commentCount,
                  subtitle = if (analytics.replyCount > 0) "${analytics.replyCount} replies" else null,
                  tint = Color(0xFF10B981)
                )

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                EngagementRow(
                  icon = Icons.Outlined.Share,
                  label = "Shares",
                  value = analytics.shareCount,
                  tint = Color(0xFFF59E0B)
                )

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                EngagementRow(
                  icon = Icons.Outlined.BookmarkBorder,
                  label = "Saves",
                  value = analytics.saveCount,
                  tint = SocivaPurple
                )

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                EngagementRow(
                  icon = Icons.Outlined.PersonAdd,
                  label = "Followers Gained",
                  value = analytics.followersGained,
                  tint = Color(0xFF8B5CF6)
                )
              }
            }
          }

          // 7. Visual Performance Graphs
          item {
            VisualPerformanceGraphsCard(analytics = analytics)
          }

          // 8. Video Analytics (If Post is Video)
          val isVideo = post?.isVideoPost() == true || videoAnalyticsState != null
          if (isVideo) {
            item {
              VideoAnalyticsSection(
                videoAnalytics = videoAnalyticsState,
                post = post
              )
            }
          }

          // 9. Audience Demographics Section
          item {
            AudienceDemographicsSection(
              analytics = analytics,
              friendsList = friends,
              onViewUser = { userId ->
                onDismiss()
                viewModel.navigateToProfile(userId)
              }
            )
          }
        }
      }
    }
  }
}

@Composable
fun PostOverviewCard(post: Post) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("post_overview_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      UserAvatar(
        avatarUrl = post.authorAvatar,
        name = post.authorName,
        size = 46.dp
      )

      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = post.authorName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          if (post.isAuthorVerified) {
            VerifiedBadge(size = 13.dp)
          }
        }

        val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }
        Text(
          text = dateFormat.format(Date(post.timestamp)),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (post.content.isNotBlank()) {
          Spacer(modifier = Modifier.height(3.dp))
          Text(
            text = post.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Post Type Badge
        val (typeLabel, typeIcon, typeColor) = when {
          post.isVideoPost() -> Triple("Video Post", Icons.Outlined.Videocam, Color(0xFFEF4444))
          post.postType == PostType.SHARED_POST -> Triple("Shared Post", Icons.Outlined.Repeat, Color(0xFFF59E0B))
          post.mediaUrls.isNotEmpty() -> Triple("Photo Post", Icons.Outlined.Image, SocivaBlue)
          else -> Triple("Text Post", Icons.Outlined.Article, Color(0xFF10B981))
        }

        Surface(
          shape = RoundedCornerShape(6.dp),
          color = typeColor.copy(alpha = 0.12f)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = typeIcon,
              contentDescription = null,
              tint = typeColor,
              modifier = Modifier.size(12.dp)
            )
            Text(
              text = typeLabel,
              style = MaterialTheme.typography.labelSmall,
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold,
              color = typeColor
            )
          }
        }
      }

      // Thumbnail preview if post has media
      if (post.mediaUrls.isNotEmpty()) {
        Box(
          modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
        ) {
          AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
              .data(post.mediaUrls.first())
              .crossfade(true)
              .build(),
            contentDescription = "Post thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
          if (post.isVideoPost()) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun TimeFilterRow(
  selected: AnalyticsTimeWindow,
  onSelect: (AnalyticsTimeWindow) -> Unit
) {
  LazyRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    items(AnalyticsTimeWindow.values()) { window ->
      val isSelected = selected == window
      FilterChip(
        selected = isSelected,
        onClick = { onSelect(window) },
        label = {
          Text(
            text = window.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
          )
        },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = SocivaBlue,
          selectedLabelColor = Color.White
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.testTag("time_filter_${window.name}")
      )
    }
  }
}

@Composable
fun EngagementRow(
  icon: ImageVector,
  label: String,
  value: Int,
  subtitle: String? = null,
  tint: Color
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = tint,
          modifier = Modifier.size(16.dp)
        )
      }
      Column {
        Text(
          text = label,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface
        )
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    Text(
      text = formatCount(value),
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
fun VisualPerformanceGraphsCard(analytics: PostAnalytics) {
  var selectedTab by remember { mutableIntStateOf(0) }
  val tabs = listOf("Views", "Reach", "Engagement")

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("visual_performance_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Performance Over Time",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.height(10.dp))

      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = Color.Transparent,
        contentColor = SocivaBlue,
        divider = {},
        indicator = {}
      ) {
        tabs.forEachIndexed { index, title ->
          val isSelected = selectedTab == index
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isSelected) SocivaBlue else MaterialTheme.colorScheme.surface,
            modifier = Modifier
              .padding(horizontal = 4.dp, vertical = 2.dp)
              .clickable { selectedTab = index }
          ) {
            Text(
              text = title,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              textAlign = TextAlign.Center
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      val activeData: List<Pair<String, Number>> = when (selectedTab) {
        0 -> analytics.viewsOverTime
        1 -> analytics.reachOverTime
        else -> analytics.engagementOverTime
      }

      val hasRealData = activeData.any { it.second.toDouble() > 0.0 }

      if (!hasRealData) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.BarChart,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.size(32.dp)
            )
            Text(
              text = "No analytics data yet.",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "Graphs will plot as audience views and interacts with this post.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
              textAlign = TextAlign.Center
            )
          }
        }
      } else {
        val maxVal = activeData.maxOfOrNull { it.second.toDouble() }?.coerceAtLeast(1.0) ?: 1.0

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.Bottom
        ) {
          activeData.forEach { (label, countNum) ->
            val count = countNum.toDouble()
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Bottom,
              modifier = Modifier.weight(1f)
            ) {
              if (count > 0.0) {
                Text(
                  text = if (selectedTab == 2) "${DecimalFormat("#,##0.1").format(count)}%" else count.toInt().toString(),
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = SocivaBlue
                )
                Spacer(modifier = Modifier.height(3.dp))
              }
              val barHeightFactor = (count / maxVal).toFloat().coerceIn(0.08f, 1f)
              Box(
                modifier = Modifier
                  .fillMaxWidth(0.55f)
                  .height((75 * barHeightFactor).dp)
                  .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                  .background(
                    if (count > 0.0) {
                      Brush.verticalGradient(listOf(SocivaBlue, SocivaIndigo))
                    } else {
                      Brush.verticalGradient(
                        listOf(
                          MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                          MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                        )
                      )
                    }
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

@Composable
fun VideoAnalyticsSection(
  videoAnalytics: VideoAnalytics?,
  post: Post?
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    ) {
      Box(
        modifier = Modifier
          .size(28.dp)
          .clip(CircleShape)
          .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Outlined.Videocam,
          contentDescription = null,
          tint = Color(0xFFEF4444),
          modifier = Modifier.size(16.dp)
        )
      }
      Text(
        text = "Video Performance",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
    }

    if (videoAnalytics == null) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "No video analytics data yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    } else {
      // 1. Video Core Metrics Grid
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          MetricCard(
            title = "Video Views",
            value = formatCount(videoAnalytics.totalViews),
            icon = Icons.Outlined.PlayCircleOutline,
            iconTint = Color(0xFFEF4444),
            subtitle = "Watched ≥ 2 seconds",
            modifier = Modifier.weight(1f).testTag("metric_video_views")
          )
          MetricCard(
            title = "Unique Viewers",
            value = formatCount(videoAnalytics.uniqueViewers),
            icon = Icons.Outlined.People,
            iconTint = SocivaIndigo,
            subtitle = "Individual accounts",
            modifier = Modifier.weight(1f).testTag("metric_video_viewers")
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          MetricCard(
            title = "Total Watch Time",
            value = formatWatchTime(videoAnalytics.totalWatchTime),
            icon = Icons.Outlined.Schedule,
            iconTint = Color(0xFF10B981),
            subtitle = "Cumulative watch time",
            modifier = Modifier.weight(1f).testTag("metric_video_watch_time")
          )
          MetricCard(
            title = "Avg. Watch Time",
            value = formatWatchSeconds(videoAnalytics.averageWatchTime),
            icon = Icons.Outlined.Timelapse,
            iconTint = Color(0xFFF59E0B),
            subtitle = "Per video view",
            modifier = Modifier.weight(1f).testTag("metric_avg_watch_time")
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          MetricCard(
            title = "Avg % Watched",
            value = "${DecimalFormat("#,##0.1").format(videoAnalytics.averagePercentageWatched)}%",
            icon = Icons.Outlined.PieChart,
            iconTint = SocivaPurple,
            subtitle = "Video completion avg",
            modifier = Modifier.weight(1f).testTag("metric_avg_pct_watched")
          )
          MetricCard(
            title = "Completion Rate",
            value = "${DecimalFormat("#,##0.1").format(videoAnalytics.completionRate)}%",
            icon = Icons.Outlined.CheckCircleOutline,
            iconTint = Color(0xFF06B6D4),
            subtitle = "Watched to 100%",
            modifier = Modifier.weight(1f).testTag("metric_completion_rate")
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          MetricCard(
            title = "Replays",
            value = formatCount(videoAnalytics.replays),
            icon = Icons.Outlined.Replay,
            iconTint = Color(0xFFEC4899),
            subtitle = "Times rewatched",
            modifier = Modifier.fillMaxWidth().testTag("metric_video_replays")
          )
        }
      }

      // 2. Viewer Retention Graph
      ViewerRetentionCard(retentionPoints = videoAnalytics.retentionPoints)

      // 3. Traffic Sources Breakdown
      TrafficSourcesCard(trafficSources = videoAnalytics.trafficSources)
    }
  }
}

@Composable
fun ViewerRetentionCard(retentionPoints: List<Pair<Int, Double>>) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("viewer_retention_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Viewer Retention",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "% of viewers still watching over video duration",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      val hasData = retentionPoints.isNotEmpty() && retentionPoints.any { it.second > 0.0 }

      if (!hasData) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.ShowChart,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.size(28.dp)
            )
            Text(
              text = "No retention data yet.",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "Retention points calculate as viewers progress through the video.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
              textAlign = TextAlign.Center
            )
          }
        }
      } else {
        // Line chart with Canvas
        Canvas(
          modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
          val width = size.width
          val height = size.height
          val points = retentionPoints

          if (points.size >= 2) {
            val stepX = width / (points.size - 1)
            val path = Path()
            val fillPath = Path()

            fillPath.moveTo(0f, height)

            points.forEachIndexed { index, (_, pct) ->
              val x = index * stepX
              val y = height - (pct.toFloat() / 100f * height).coerceIn(0f, height)
              if (index == 0) {
                path.moveTo(x, y)
                fillPath.lineTo(x, y)
              } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
              }
            }

            fillPath.lineTo(width, height)
            fillPath.close()

            // Draw Area Fill
            drawPath(
              path = fillPath,
              brush = Brush.verticalGradient(
                listOf(
                  Color(0xFFEF4444).copy(alpha = 0.35f),
                  Color(0xFFEF4444).copy(alpha = 0.05f)
                )
              )
            )

            // Draw Retention Line
            drawPath(
              path = path,
              color = Color(0xFFEF4444),
              style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw Dots
            points.forEachIndexed { index, (_, pct) ->
              val x = index * stepX
              val y = height - (pct.toFloat() / 100f * height).coerceIn(0f, height)
              drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
              )
              drawCircle(
                color = Color(0xFFEF4444),
                radius = 2.5.dp.toPx(),
                center = Offset(x, y)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Retention X-axis points (0%, 10%, 25%, 50%, 75%, 90%, 100%)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          retentionPoints.forEach { (checkpoint, pct) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "${DecimalFormat("#,##0").format(pct)}%",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (pct > 0.0) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "$checkpoint%",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun TrafficSourcesCard(trafficSources: Map<String, Int>) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("traffic_sources_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Traffic Sources",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = "Where viewers discovered this video",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(12.dp))

      if (trafficSources.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "No traffic source data yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else {
        val total = trafficSources.values.sum().coerceAtLeast(1)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          trafficSources.forEach { (source, count) ->
            val percentage = (count.toFloat() / total.toFloat()) * 100f

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = source,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "${DecimalFormat("#,##0.1").format(percentage)}% ($count)",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = SocivaBlue
                )
              }
              LinearProgressIndicator(
                progress = { percentage / 100f },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(RoundedCornerShape(3.dp)),
                color = SocivaBlue,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun AudienceDemographicsSection(
  analytics: PostAnalytics,
  friendsList: List<User>,
  onViewUser: (String) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = "Audience Insights",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )

    if (analytics.recentViewers.isEmpty()) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.PeopleOutline,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.size(32.dp)
            )
            Text(
              text = "Audience data will appear as more people view this post.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    } else {
      // Follower vs Non-Follower Views
      val friendIds = remember(friendsList) { friendsList.map { it.id }.toSet() }
      val totalViewersCount = analytics.recentViewers.size.coerceAtLeast(1)
      val followerCount = analytics.recentViewers.count { friendIds.contains(it.id) }
      val nonFollowerCount = totalViewersCount - followerCount
      val followerPct = (followerCount.toFloat() / totalViewersCount.toFloat()) * 100f
      val nonFollowerPct = (nonFollowerCount.toFloat() / totalViewersCount.toFloat()) * 100f

      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("follower_split_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "Followers vs. Non-Followers",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "Followers: ${DecimalFormat("#,##0.1").format(followerPct)}%",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = SocivaBlue
            )
            Text(
              text = "Non-Followers: ${DecimalFormat("#,##0.1").format(nonFollowerPct)}%",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = SocivaPurple
            )
          }
          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(8.dp)
              .clip(RoundedCornerShape(4.dp))
          ) {
            Box(
              modifier = Modifier
                .weight(followerPct.coerceAtLeast(1f))
                .fillMaxHeight()
                .background(SocivaBlue)
            )
            Box(
              modifier = Modifier
                .weight(nonFollowerPct.coerceAtLeast(1f))
                .fillMaxHeight()
                .background(SocivaPurple)
            )
          }
        }
      }

      // Recent Viewers List
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("recent_viewers_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Recent Viewers",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "${analytics.uniqueViewers} total",
              style = MaterialTheme.typography.labelSmall,
              color = SocivaBlue,
              fontWeight = FontWeight.SemiBold
            )
          }
          Spacer(modifier = Modifier.height(10.dp))

          analytics.recentViewers.forEach { viewer ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onViewUser(viewer.id) }
                .padding(vertical = 6.dp, horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              UserAvatar(
                avatarUrl = viewer.avatarUrl,
                name = viewer.fullName,
                size = 38.dp
              )
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = viewer.fullName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  if (viewer.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 12.dp)
                  }
                }
                Text(
                  text = "@${viewer.username}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "View Profile",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun MetricCard(
  title: String,
  value: String,
  icon: ImageVector,
  iconTint: Color,
  subtitle: String,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
          modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(iconTint.copy(alpha = 0.12f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )

      Spacer(modifier = Modifier.height(2.dp))

      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
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

private fun formatWatchTime(millis: Long): String {
  val totalSeconds = millis / 1000
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60
  return when {
    hours > 0 -> "${hours}h ${minutes}m"
    minutes > 0 -> "${minutes}m ${seconds}s"
    else -> "${seconds}s"
  }
}

private fun formatWatchSeconds(secondsDouble: Double): String {
  val totalSeconds = secondsDouble.toLong()
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return "%d:%02d".format(minutes, seconds)
}

private fun reactionEmojiForType(type: String): String {
  return when (type.lowercase()) {
    "like" -> "👍"
    "love" -> "❤️"
    "haha" -> "😆"
    "wow" -> "😮"
    "sad" -> "😢"
    "angry" -> "😡"
    "care" -> "🥰"
    else -> "👍"
  }
}
