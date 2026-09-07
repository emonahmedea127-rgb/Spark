package com.example.sociva.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sociva.data.model.*
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.*
import com.example.ui.theme.*

@Composable
fun HomeScreen(
  viewModel: SocivaViewModel,
  modifier: Modifier = Modifier
) {
  val posts by viewModel.allPosts.collectAsState()
  val stories by viewModel.activeStories.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val activeIdentity by viewModel.activeIdentity.collectAsState()
  val friends by viewModel.friends.collectAsState()
  val blockedUsers by viewModel.blockedUsers.collectAsState()
  val context = LocalContext.current

  val blockedUserIds: Set<String> = remember(blockedUsers) { blockedUsers.map { it.blockedId }.toSet() }
  val friendIds = remember(friends) { friends.map { it.id }.toSet() }
  val visiblePosts = remember(posts, currentUser, friendIds, blockedUserIds) {
    posts.filter { post ->
      if (post.authorId in blockedUserIds) return@filter false
      if (post.postType == PostType.SHARED_POST && post.sharedPost?.authorId in blockedUserIds) return@filter false
      val isMyPost = post.authorId == currentUser?.id
      if (isMyPost) return@filter true
      when (post.audience) {
        PostAudience.ONLY_ME -> false
        PostAudience.FRIENDS -> post.authorId in friendIds
        PostAudience.PUBLIC -> true
      }
    }
  }

  val visibleStories = remember(stories, blockedUserIds) {
    stories.filter { it.userId !in blockedUserIds }
  }

  // 1. Direct Story Media Picker Launcher (Home -> Create Story -> Native Picker -> Story Editor)
  val storyMediaPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      val mimeType = context.contentResolver.getType(uri) ?: ""
      val isVideo = mimeType.startsWith("video")
      viewModel.selectStoryMedia(uri, isVideo, mimeType)
    }
  }

  // 2. Direct Post Multi-Media Picker Launcher (Home Photo/Video -> Native Picker -> Post Composer)
  val postMediaPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      viewModel.setPendingPostUris(uris)
      viewModel.navigateTo(com.example.sociva.ui.SocivaScreen.CREATE_POST)
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("home_feed_list"),
    contentPadding = PaddingValues(bottom = 80.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Identity Persona Banner (Facebook Style)
    if (activeIdentity.type != com.example.sociva.data.model.IdentityType.PERSONAL) {
      item {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (activeIdentity.type == com.example.sociva.data.model.IdentityType.PAGE) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFF8B5CF6).copy(alpha = 0.12f),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (activeIdentity.type == com.example.sociva.data.model.IdentityType.PAGE) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFF8B5CF6).copy(alpha = 0.4f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = if (activeIdentity.type == com.example.sociva.data.model.IdentityType.PAGE) Icons.Default.Flag else Icons.Default.Groups,
                contentDescription = null,
                tint = if (activeIdentity.type == com.example.sociva.data.model.IdentityType.PAGE) Color(0xFF10B981) else Color(0xFF8B5CF6),
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Interacting as ${activeIdentity.name}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
              )
            }
            TextButton(
              onClick = { viewModel.resetToPersonalIdentity() },
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text("Switch back", style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }
    }

    // 1. Post Composer Card
    item {
      PostComposerPromptCard(
        currentUser = currentUser,
        activeIdentity = activeIdentity,
        onOpenComposer = { viewModel.navigateTo(com.example.sociva.ui.SocivaScreen.CREATE_POST) },
        onPickPhotoVideo = {
          postMediaPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
          )
        },
        onCreateStoryDirect = {
          storyMediaPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
          )
        }
      )
    }

    // 2. Stories Carousel
    item {
      StoriesSection(
        stories = visibleStories,
        currentUser = currentUser,
        onCreateStory = {
          storyMediaPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
          )
        },
        onStoryClick = { index -> viewModel.openStoryViewer(index) }
      )
    }

    // 3. Feed Posts
    items(visiblePosts, key = { it.id }) { post ->
      LaunchedEffect(post.id) {
        viewModel.recordPostView(post.id)
      }

      PostCard(
        post = post,
        currentUser = currentUser,
        onReaction = { reaction -> viewModel.setReaction(post.id, reaction) },
        onCommentClick = { viewModel.openComments(post.id) },
        onShareClick = { viewModel.openShareComposer(post) },
        onSaveClick = { viewModel.toggleSavePost(post.id) },
        onAuthorClick = {
          viewModel.recordProfileVisit(post.authorId, originatingPostId = post.id)
          viewModel.navigateToProfile(post.authorId)
        },
        onSharedAuthorClick = { origAuthorId ->
          viewModel.recordProfileVisit(origAuthorId, originatingPostId = post.id)
          viewModel.navigateToProfile(origAuthorId)
        },
        onDeleteClick = { viewModel.deletePost(post.id) },
        onEditClick = { viewModel.openEditPost(post) },
        onReportClick = {
          viewModel.submitReport("Post", post.id, post.content.take(40), "Inappropriate or spam content")
        },
        onRemoveTagClick = { viewModel.removePostTag(post.id) },
        onReactionsClick = { viewModel.openReactionsModal(post.id) },
        onAnalyticsClick = { viewModel.openPostAnalytics(post.id) },
        onWatchVideoEvent = { event ->
          viewModel.recordVideoWatchEvent(event.copy(viewerId = currentUser?.id ?: ""))
        }
      )
    }
  }
}

@Composable
fun PostComposerPromptCard(
  currentUser: User?,
  activeIdentity: com.example.sociva.data.model.ActiveIdentity? = null,
  onOpenComposer: () -> Unit,
  onPickPhotoVideo: () -> Unit = onOpenComposer,
  onCreateStoryDirect: () -> Unit = onOpenComposer
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 6.dp)
      .testTag("home_post_composer_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        UserAvatar(
          avatarUrl = activeIdentity?.avatarUrl ?: currentUser?.avatarUrl,
          name = activeIdentity?.name ?: currentUser?.fullName ?: "User",
          size = 42.dp
        )

        Box(
          modifier = Modifier
            .weight(1f)
            .height(42.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpenComposer() }
            .padding(horizontal = 16.dp),
          contentAlignment = Alignment.CenterStart
        ) {
          Text(
            text = if (activeIdentity != null && activeIdentity.type != com.example.sociva.data.model.IdentityType.PERSONAL)
              "Post as ${activeIdentity.name}..."
            else
              "What's on your mind, ${currentUser?.fullName?.substringBefore(" ") ?: "there"}?",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }

      HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
      ) {
        ComposerActionButton(
          icon = Icons.Default.PhotoLibrary,
          iconTint = Color(0xFF10B981),
          label = "Photo/Video",
          onClick = onPickPhotoVideo
        )

        ComposerActionButton(
          icon = Icons.Default.SentimentSatisfiedAlt,
          iconTint = Color(0xFFF59E0B),
          label = "Feeling/Activity",
          onClick = onOpenComposer
        )

        ComposerActionButton(
          icon = Icons.Default.AutoAwesome,
          iconTint = SocivaPurple,
          label = "Story",
          onClick = onCreateStoryDirect
        )
      }
    }
  }
}

@Composable
fun ComposerActionButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  iconTint: Color,
  label: String,
  onClick: () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .clickable { onClick() }
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = iconTint,
      modifier = Modifier.size(20.dp)
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
fun StoriesSection(
  stories: List<Story>,
  currentUser: User?,
  onCreateStory: () -> Unit,
  onStoryClick: (Int) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .padding(vertical = 12.dp)
  ) {
    Text(
      text = "Stories",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )

    LazyRow(
      contentPadding = PaddingValues(horizontal = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.padding(top = 8.dp)
    ) {
      // 1. Create Story Tile
      item {
        CreateStoryCard(currentUser = currentUser, onClick = onCreateStory)
      }

      // 2. Active Stories
      itemsIndexed(stories) { index, story ->
        StoryItemCard(
          story = story,
          onClick = { onStoryClick(index) }
        )
      }
    }
  }
}

@Composable
fun CreateStoryCard(
  currentUser: User?,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .width(115.dp)
      .height(180.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable { onClick() }
      .testTag("create_story_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Top image / avatar
      if (!currentUser?.coverUrl.isNullOrBlank()) {
        AsyncImage(
          model = ImageRequest.Builder(LocalContext.current)
            .data(currentUser?.avatarUrl)
            .crossfade(true)
            .build(),
          contentDescription = "User Avatar",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxWidth()
            .height(125.dp)
        )
      } else {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(125.dp)
            .background(
              Brush.verticalGradient(
                listOf(SocivaBlue, SocivaPurple)
              )
            )
        )
      }

      // Plus Button in center overlapping bottom area
      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .offset(y = (-40).dp)
          .size(34.dp)
          .clip(CircleShape)
          .background(SocivaBlue)
          .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = "Add Story",
          tint = Color.White,
          modifier = Modifier.size(20.dp)
        )
      }

      Text(
        text = "Create Story",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 12.dp)
      )
    }
  }
}

@Composable
fun StoryItemCard(
  story: Story,
  onClick: () -> Unit
) {
  val gradients = listOf(
    listOf(SocivaIndigo, SocivaPurple),
    listOf(SocivaPurple, SocivaPink),
    listOf(SocivaIndigo, Color(0xFF06B6D4)),
    listOf(SocivaPink, Color(0xFFF43F5E)),
    listOf(SocivaGreen, Color(0xFF06B6D4))
  )
  val currentGradient = gradients[story.backgroundGradientIndex % gradients.size]

  Card(
    modifier = Modifier
      .width(115.dp)
      .height(180.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable { onClick() }
      .testTag("story_card_${story.id}"),
    shape = RoundedCornerShape(16.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      if (!story.mediaUrl.isNullOrBlank()) {
        AsyncImage(
          model = ImageRequest.Builder(LocalContext.current)
            .data(story.mediaUrl)
            .crossfade(true)
            .build(),
          contentDescription = "Story preview",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(currentGradient))
            .padding(10.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = story.textOverlay,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // Dim overlay gradient at top and bottom for readability
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Black.copy(alpha = 0.4f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.6f)
              )
            )
          )
      )

      // User avatar at top left with story ring
      UserAvatar(
        avatarUrl = story.userAvatar,
        name = story.userName,
        size = 36.dp,
        hasStoryRing = true,
        storyRingViewed = story.isViewed,
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(8.dp)
      )

      // Author name at bottom
      Text(
        text = story.userName,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(8.dp)
      )
    }
  }
}

@Composable
fun PostCard(
  post: Post,
  currentUser: User?,
  onReaction: (ReactionType) -> Unit,
  onCommentClick: () -> Unit,
  onShareClick: () -> Unit,
  onSaveClick: () -> Unit,
  onAuthorClick: () -> Unit,
  onDeleteClick: () -> Unit,
  onReportClick: () -> Unit,
  onEditClick: (() -> Unit)? = null,
  onSharedAuthorClick: ((String) -> Unit)? = null,
  onRemoveTagClick: (() -> Unit)? = null,
  onReactionsClick: (() -> Unit)? = null,
  onAnalyticsClick: (() -> Unit)? = null,
  onWatchVideoEvent: ((VideoWatchEvent) -> Unit)? = null
) {
  var showMenu by remember { mutableStateOf(false) }
  var showReactionPicker by remember { mutableStateOf(false) }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 10.dp)
      .testTag("post_card_${post.id}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
      // Header: Avatar, Name, Verification, Timestamp, Audience, Menu
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        UserAvatar(
          avatarUrl = post.authorAvatar,
          name = post.authorName,
          size = 42.dp,
          onClick = onAuthorClick
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = post.authorName,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.clickable { onAuthorClick() }
            )
            if (post.isAuthorVerified) {
              Spacer(modifier = Modifier.width(4.dp))
              VerifiedBadge(size = 14.dp)
            }
            if (!post.actionContextText.isNullOrBlank()) {
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = post.actionContextText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            if (post.taggedUsers.isNotEmpty()) {
              Text(
                text = " with ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = if (post.taggedUsers.size == 1) post.taggedUsers.first().fullName else "${post.taggedUsers.first().fullName} +${post.taggedUsers.size - 1}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = SocivaBlue
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = formatRelativeTime(post.timestamp),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "•",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
              imageVector = when (post.audience) {
                PostAudience.FRIENDS -> Icons.Default.Group
                PostAudience.ONLY_ME -> Icons.Default.Lock
                else -> Icons.Default.Public
              },
              contentDescription = post.audience.label,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(12.dp)
            )
          }
        }

        // More Options Menu
        Box {
          IconButton(onClick = { showMenu = true }) {
            Icon(
              imageVector = Icons.Default.MoreHoriz,
              contentDescription = "Post options",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
          ) {
            DropdownMenuItem(
              text = { Text(if (post.isSaved) "Remove from Saved" else "Save Post") },
              leadingIcon = {
                Icon(
                  if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                  contentDescription = null
                )
              },
              onClick = {
                showMenu = false
                onSaveClick()
              }
            )
            DropdownMenuItem(
              text = { Text("Report Post") },
              leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
              onClick = {
                showMenu = false
                onReportClick()
              }
            )
            val isTagged = post.taggedUsers.any { it.id == currentUser?.id }
            if (isTagged && onRemoveTagClick != null) {
              DropdownMenuItem(
                text = { Text("Remove tag from this post") },
                leadingIcon = { Icon(Icons.Outlined.LabelOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                  showMenu = false
                  onRemoveTagClick()
                }
              )
            }
            if (post.authorId == currentUser?.id) {
              if (onAnalyticsClick != null) {
                DropdownMenuItem(
                  text = { Text("View Analytics") },
                  leadingIcon = {
                    Icon(
                      Icons.Outlined.Insights,
                      contentDescription = null,
                      tint = SocivaBlue
                    )
                  },
                  onClick = {
                    showMenu = false
                    onAnalyticsClick()
                  },
                  modifier = Modifier.testTag("post_view_analytics_${post.id}")
                )
              }
              if (onEditClick != null) {
                DropdownMenuItem(
                  text = { Text(if (post.postType == PostType.SHARED_POST) "Edit Caption" else "Edit Post") },
                  leadingIcon = {
                    Icon(
                      Icons.Outlined.Edit,
                      contentDescription = null
                    )
                  },
                  onClick = {
                    showMenu = false
                    onEditClick()
                  }
                )
              }
              DropdownMenuItem(
                text = { Text("Delete Post", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                  Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                  )
                },
                onClick = {
                  showMenu = false
                  onDeleteClick()
                }
              )
            }
          }
        }
      }

      // Feeling / Activity Badge
      if (!post.feelingOrActivity.isNullOrBlank()) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = post.feelingOrActivity,
            style = MaterialTheme.typography.labelMedium,
            color = SocivaPurple,
            fontWeight = FontWeight.Medium
          )
        }
      }

      // Post Content Text
      if (post.content.isNotBlank()) {
        Text(
          text = post.content,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
      }

      // Post Media (Single image, multi-image grid, or special update layout)
      if (post.mediaUrls.isNotEmpty()) {
        if (post.postType == PostType.PROFILE_PICTURE_UPDATE) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
              .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
          ) {
            Box(
              modifier = Modifier
                .size(240.dp)
                .clip(CircleShape)
                .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
                .border(5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape)
            ) {
              AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                  .data(post.mediaUrls.first())
                  .crossfade(true)
                  .build(),
                contentDescription = "Updated profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }
          }
        } else if (post.postType == PostType.COVER_PHOTO_UPDATE) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(16f / 9f)
          ) {
            AsyncImage(
              model = ImageRequest.Builder(LocalContext.current)
                .data(post.mediaUrls.first())
                .crossfade(true)
                .build(),
              contentDescription = "Updated cover photo",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }
        } else if (post.isVideoPost()) {
          val videoUrl = post.getVideoUrl() ?: post.mediaUrls.first()
          PostVideoPlayer(
            post = post,
            videoUrl = videoUrl,
            onWatchEvent = { event -> onWatchVideoEvent?.invoke(event) }
          )
        } else {
          PostMediaGallery(mediaUrls = post.mediaUrls)
        }
      }

      // Shared Post Nested Card Preview
      if (post.postType == PostType.SHARED_POST && post.sharedPost != null) {
        SharedPostCard(
          sharedPost = post.sharedPost,
          onAuthorClick = {
            if (post.sharedPost.authorId.isNotBlank()) {
              onSharedAuthorClick?.invoke(post.sharedPost.authorId)
            }
          }
        )
      }

      // Stats Bar: Reactions, Comments, Shares
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (post.likesCount > 0) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .then(
                if (onReactionsClick != null) {
                  Modifier
                    .clickable { onReactionsClick() }
                    .testTag("post_reactions_count_${post.id}")
                } else Modifier
              )
              .padding(horizontal = 4.dp, vertical = 2.dp)
          ) {
            // Display top reaction emojis (e.g. ❤️ 👍 😂)
            val displayEmojis = if (post.topReactionEmojis.isNotEmpty()) {
              post.topReactionEmojis.take(3)
            } else {
              listOf(post.myReaction?.emoji ?: "❤️")
            }

            Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
              displayEmojis.forEach { emoji ->
                Text(text = emoji, fontSize = 14.sp)
              }
            }

            Spacer(modifier = Modifier.width(2.dp))
            Text(
              text = "${post.likesCount}",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        } else {
          Spacer(modifier = Modifier.width(1.dp))
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (post.authorId == currentUser?.id && onAnalyticsClick != null) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onAnalyticsClick() }
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .testTag("post_quick_analytics_btn_${post.id}")
            ) {
              Icon(
                Icons.Outlined.BarChart,
                contentDescription = "View Analytics",
                tint = SocivaBlue,
                modifier = Modifier.size(13.dp)
              )
              Text(
                text = "Analytics",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = SocivaBlue
              )
            }
          }
          if (post.commentsCount > 0) {
            Text(
              text = "${post.commentsCount} comments",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          if (post.sharesCount > 0) {
            Text(
              text = "${post.sharesCount} shares",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
      )

      // Action Buttons Row: Like, Comment, Share, Bookmark
      Box(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceAround,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Like Button with long press / click
          Box {
            if (post.myReaction != null) {
              Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFEEF2FF),
                modifier = Modifier
                  .clickable { onReaction(post.myReaction) }
                  .testTag("post_like_button_${post.id}")
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                  Text(text = post.myReaction.emoji, fontSize = 16.sp)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = post.myReaction.label,
                    color = SocivaIndigo,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                  )
                }
              }
            } else {
              TextButton(
                onClick = { showReactionPicker = true },
                modifier = Modifier.testTag("post_like_button_${post.id}")
              ) {
                Icon(
                  imageVector = Icons.Outlined.ThumbUp,
                  contentDescription = "Like",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Like",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  style = MaterialTheme.typography.labelMedium
                )
              }
            }
          }

          TextButton(
            onClick = onCommentClick,
            modifier = Modifier.testTag("post_comment_button_${post.id}")
          ) {
            Icon(
              imageVector = Icons.Outlined.ChatBubbleOutline,
              contentDescription = "Comment",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Comment",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.labelMedium
            )
          }

          TextButton(
            onClick = onShareClick,
            modifier = Modifier.testTag("post_share_button_${post.id}")
          ) {
            Icon(
              imageVector = Icons.Outlined.Share,
              contentDescription = "Share",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Share",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.labelMedium
            )
          }

          IconButton(
            onClick = onSaveClick,
            modifier = Modifier.testTag("post_save_button_${post.id}")
          ) {
            Icon(
              imageVector = if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
              contentDescription = "Save Post",
              tint = if (post.isSaved) SocivaBlue else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp)
            )
          }
        }

        // Reaction picker overlay
        if (showReactionPicker) {
          ReactionPickerPopup(
            onSelectReaction = { reaction ->
              onReaction(reaction)
              showReactionPicker = false
            },
            onDismiss = { showReactionPicker = false },
            modifier = Modifier
              .align(Alignment.TopStart)
              .offset(y = (-45).dp, x = 16.dp)
          )
        }
      }

      // Owner-Only "View Analytics" Action Button below the post actions
      if (post.authorId == currentUser?.id && onAnalyticsClick != null) {
        HorizontalDivider(
          modifier = Modifier.padding(horizontal = 14.dp),
          thickness = 0.5.dp,
          color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onAnalyticsClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .testTag("post_view_analytics_bottom_btn_${post.id}"),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Outlined.BarChart,
            contentDescription = "View Analytics",
            tint = SocivaBlue,
            modifier = Modifier.size(17.dp)
          )
          Spacer(modifier = Modifier.width(7.dp))
          Text(
            text = "View Analytics",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = SocivaBlue
          )
        }
      }
    }
  }
}

@Composable
fun PostMediaGallery(mediaUrls: List<String>) {
  when (mediaUrls.size) {
    1 -> {
      AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
          .data(mediaUrls[0])
          .crossfade(true)
          .build(),
        contentDescription = "Post photo",
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 200.dp, max = 340.dp)
      )
    }
    2 -> {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        AsyncImage(
          model = ImageRequest.Builder(LocalContext.current)
            .data(mediaUrls[0])
            .crossfade(true)
            .build(),
          contentDescription = "Photo 1",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
        )
        AsyncImage(
          model = ImageRequest.Builder(LocalContext.current)
            .data(mediaUrls[1])
            .crossfade(true)
            .build(),
          contentDescription = "Photo 2",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
        )
      }
    }
    else -> {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .height(280.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        AsyncImage(
          model = ImageRequest.Builder(LocalContext.current)
            .data(mediaUrls[0])
            .crossfade(true)
            .build(),
          contentDescription = "Main photo",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxWidth()
            .weight(1.3f)
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
              .data(mediaUrls[1])
              .crossfade(true)
              .build(),
            contentDescription = "Photo 2",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
          )
          AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
              .data(mediaUrls[2])
              .crossfade(true)
              .build(),
            contentDescription = "Photo 3",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
          )
        }
      }
    }
  }
}

@Composable
fun SharedPostCard(
  sharedPost: SharedPostPreview,
  onAuthorClick: (() -> Unit)? = null
) {
  if (sharedPost.isUnavailable) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 6.dp)
        .testTag("shared_post_unavailable_card"),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = Icons.Outlined.Lock,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "This content isn't available right now",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = sharedPost.content,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }
    }
  } else {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 6.dp)
        .testTag("shared_post_nested_card"),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
      Column(modifier = Modifier.padding(vertical = 10.dp)) {
        // Original Author Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          UserAvatar(
            avatarUrl = sharedPost.authorAvatar,
            name = sharedPost.authorName,
            size = 36.dp,
            onClick = onAuthorClick
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = sharedPost.authorName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = if (onAuthorClick != null) Modifier.clickable { onAuthorClick() } else Modifier
              )
              if (sharedPost.isAuthorVerified) {
                Spacer(modifier = Modifier.width(4.dp))
                VerifiedBadge(size = 12.dp)
              }
              if (!sharedPost.actionContextText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = sharedPost.actionContextText,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = formatRelativeTime(sharedPost.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "•",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Icon(
                imageVector = when (sharedPost.audience) {
                  PostAudience.FRIENDS -> Icons.Default.Group
                  PostAudience.ONLY_ME -> Icons.Default.Lock
                  else -> Icons.Default.Public
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(10.dp)
              )
            }
          }
        }

        // Original Feeling / Activity
        if (!sharedPost.feelingOrActivity.isNullOrBlank()) {
          Text(
            text = sharedPost.feelingOrActivity,
            style = MaterialTheme.typography.labelSmall,
            color = SocivaPurple,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
          )
        }

        // Original Content
        if (sharedPost.content.isNotBlank()) {
          Text(
            text = sharedPost.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }

        // Original Media Gallery or Avatar/Cover frame
        if (sharedPost.mediaUrls.isNotEmpty()) {
          if (sharedPost.postType == PostType.PROFILE_PICTURE_UPDATE) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                .padding(vertical = 16.dp),
              contentAlignment = Alignment.Center
            ) {
              Box(
                modifier = Modifier
                  .size(180.dp)
                  .clip(CircleShape)
                  .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                  .border(4.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape)
              ) {
                AsyncImage(
                  model = ImageRequest.Builder(LocalContext.current)
                    .data(sharedPost.mediaUrls.first())
                    .crossfade(true)
                    .build(),
                  contentDescription = "Original updated profile picture",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              }
            }
          } else if (sharedPost.postType == PostType.COVER_PHOTO_UPDATE) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
            ) {
              AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                  .data(sharedPost.mediaUrls.first())
                  .crossfade(true)
                  .build(),
                contentDescription = "Original updated cover photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }
          } else {
            Spacer(modifier = Modifier.height(4.dp))
            PostMediaGallery(mediaUrls = sharedPost.mediaUrls)
          }
        }
      }
    }
  }
}
