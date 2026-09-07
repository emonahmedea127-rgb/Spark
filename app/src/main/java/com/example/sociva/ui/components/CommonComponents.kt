package com.example.sociva.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.sociva.data.model.ActiveIdentity
import com.example.sociva.data.model.IdentityType
import com.example.sociva.data.model.ReactionType
import com.example.ui.theme.*

@Composable
fun SocivaLogo(
  modifier: Modifier = Modifier,
  showTagline: Boolean = false,
  size: Dp = 38.dp
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Box(
      modifier = Modifier
        .size(size)
        .clip(RoundedCornerShape(size * 0.28f))
        .background(
          Brush.linearGradient(
            colors = listOf(SocivaIndigo, SocivaPurple)
          )
        ),
      contentAlignment = Alignment.Center
    ) {
      Image(
        painter = painterResource(id = R.drawable.spark_app_logo_1788636063462),
        contentDescription = "Spark",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
      )
    }

    Column {
      Text(
        text = "Spark",
        fontWeight = FontWeight.Black,
        fontSize = (size.value * 0.62f).sp,
        style = LocalTextStyle.current.copy(
          brush = Brush.linearGradient(
            colors = listOf(SocivaIndigo, SocivaPurple)
          )
        ),
        letterSpacing = (-0.5).sp
      )
      if (showTagline) {
        Text(
          text = "Ignite Connections. Share Moments.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
fun SparkLogo(
  modifier: Modifier = Modifier,
  showTagline: Boolean = false,
  size: Dp = 38.dp
) {
  SocivaLogo(modifier, showTagline, size)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocivaTopBar(
  title: @Composable () -> Unit = { SocivaLogo() },
  onSearchClick: () -> Unit = {},
  onMessagesClick: () -> Unit = {},
  unreadMessagesCount: Int = 1,
  onThemeToggle: () -> Unit = {},
  isDarkTheme: Boolean = false,
  navigationIcon: @Composable (() -> Unit)? = null,
  activeIdentity: ActiveIdentity? = null,
  onIdentityClick: (() -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 1.dp,
      shadowElevation = 1.dp,
      modifier = Modifier.size(38.dp)
    ) {
      IconButton(
        onClick = onSearchClick,
        modifier = Modifier.testTag("top_bar_search_button")
      ) {
        Icon(
          imageVector = Icons.Default.Search,
          contentDescription = "Search Spark",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp)
        )
      }
    }

    Spacer(modifier = Modifier.width(8.dp))

    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 1.dp,
      shadowElevation = 1.dp,
      modifier = Modifier.size(38.dp)
    ) {
      IconButton(
        onClick = onThemeToggle,
        modifier = Modifier.testTag("top_bar_theme_toggle")
      ) {
        Icon(
          imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
          contentDescription = "Toggle theme",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(19.dp)
        )
      }
    }

    Spacer(modifier = Modifier.width(8.dp))

    BadgedBox(
      badge = {
        if (unreadMessagesCount > 0) {
          Badge(
            containerColor = SocivaPink,
            contentColor = Color.White
          ) {
            Text(text = "$unreadMessagesCount")
          }
        }
      }
    ) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.size(38.dp)
      ) {
        IconButton(
          onClick = onMessagesClick,
          modifier = Modifier.testTag("top_bar_messages_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = "Direct Messages",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }

    if (onIdentityClick != null && activeIdentity != null) {
      Spacer(modifier = Modifier.width(8.dp))
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .clickable { onIdentityClick() }
          .testTag("top_bar_identity_switcher_button")
      ) {
        UserAvatar(
          avatarUrl = activeIdentity.avatarUrl,
          name = activeIdentity.name,
          size = 38.dp
        )
        Box(
          modifier = Modifier
            .size(15.dp)
            .align(Alignment.BottomEnd)
            .clip(CircleShape)
            .background(
              when (activeIdentity.type) {
                IdentityType.PERSONAL -> SocivaBlue
                IdentityType.PAGE -> Color(0xFF10B981)
                IdentityType.GROUP -> Color(0xFF8B5CF6)
              }
            )
            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = when (activeIdentity.type) {
              IdentityType.PERSONAL -> Icons.Default.Person
              IdentityType.PAGE -> Icons.Default.Flag
              IdentityType.GROUP -> Icons.Default.Groups
            },
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(9.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.width(8.dp))
  }
) {
  TopAppBar(
    title = title,
    navigationIcon = { navigationIcon?.invoke() },
    actions = actions,
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = MaterialTheme.colorScheme.background,
      titleContentColor = MaterialTheme.colorScheme.onSurface
    )
  )
}

@Composable
fun UserAvatar(
  avatarUrl: String?,
  name: String,
  modifier: Modifier = Modifier,
  size: Dp = 40.dp,
  showOnlineBadge: Boolean = false,
  isOnline: Boolean = false,
  hasStoryRing: Boolean = false,
  storyRingViewed: Boolean = false,
  onClick: (() -> Unit)? = null
) {
  val borderModifier = if (hasStoryRing) {
    Modifier.border(
      width = 2.5.dp,
      brush = if (storyRingViewed) {
        Brush.linearGradient(listOf(Color.Gray, Color.LightGray))
      } else {
        Brush.linearGradient(listOf(SocivaBlue, SocivaPurple, SocivaPink))
      },
      shape = CircleShape
    ).padding(2.5.dp)
  } else Modifier

  Box(
    modifier = modifier
      .size(size)
      .then(borderModifier)
      .clip(CircleShape)
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
  ) {
    if (!avatarUrl.isNullOrBlank()) {
      AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
          .data(avatarUrl)
          .crossfade(true)
          .build(),
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.linearGradient(
              listOf(SocivaBlue, SocivaPurple)
            )
          ),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = name.firstOrNull()?.uppercase() ?: "S",
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = (size.value * 0.45f).sp
        )
      }
    }

    if (showOnlineBadge && isOnline) {
      Box(
        modifier = Modifier
          .size(size * 0.3f)
          .align(Alignment.BottomEnd)
          .clip(CircleShape)
          .background(Color(0xFF10B981))
          .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
      )
    }
  }
}

@Composable
fun VerifiedBadge(modifier: Modifier = Modifier, size: Dp = 16.dp) {
  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .background(SocivaBlue),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = Icons.Default.Check,
      contentDescription = "Verified Profile",
      tint = Color.White,
      modifier = Modifier.size(size * 0.75f)
    )
  }
}

@Composable
fun ReactionPickerPopup(
  onSelectReaction: (ReactionType) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .padding(horizontal = 8.dp)
      .shadow(elevation = 12.dp, shape = RoundedCornerShape(32.dp)),
    shape = RoundedCornerShape(32.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    )
  ) {
    Row(
      modifier = Modifier
        .padding(horizontal = 12.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      ReactionType.values().forEach { reaction ->
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .clip(CircleShape)
            .clickable {
              onSelectReaction(reaction)
              onDismiss()
            }
            .padding(4.dp)
        ) {
          Text(
            text = reaction.emoji,
            fontSize = 24.sp
          )
        }
      }
    }
  }
}

fun formatRelativeTime(timestamp: Long): String {
  val diff = System.currentTimeMillis() - timestamp
  val seconds = diff / 1000
  val minutes = seconds / 60
  val hours = minutes / 60
  val days = hours / 24

  return when {
    minutes < 1 -> "Just now"
    minutes < 60 -> "${minutes}m ago"
    hours < 24 -> "${hours}h ago"
    days < 7 -> "${days}d ago"
    else -> "${days / 7}w ago"
  }
}
