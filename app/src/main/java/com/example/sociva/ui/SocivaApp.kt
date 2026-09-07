package com.example.sociva.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.sociva.data.repository.AuthState
import com.example.sociva.ui.components.*
import com.example.sociva.ui.screens.*
import com.example.ui.theme.*

data class NavTabItem(
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String
)

@Composable
fun SocivaApp(
  viewModel: SocivaViewModel,
  isDarkTheme: Boolean
) {
  val activeScreen by viewModel.activeScreen.collectAsState()
  val selectedTab by viewModel.selectedTab.collectAsState()
  val toastMessage by viewModel.toastMessage.collectAsState()
  val authState by viewModel.authState.collectAsState()
  val isLoggedIn by viewModel.isLoggedIn.collectAsState()
  val commentsPostId by viewModel.commentsPostId.collectAsState()
  val reactionsModalPostId by viewModel.reactionsModalPostId.collectAsState()
  val analyticsPostId by viewModel.analyticsPostId.collectAsState()
  val sharingPost by viewModel.sharingPost.collectAsState()
  val editingPost by viewModel.editingPost.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val activeProfileUserId by viewModel.activeProfileUserId.collectAsState()
  val activeIdentity by viewModel.activeIdentity.collectAsState()
  val isIdentitySwitcherVisible by viewModel.isIdentitySwitcherVisible.collectAsState()
  val activeConversationId by viewModel.activeConversationId.collectAsState()
  val notifications by viewModel.notifications.collectAsState()
  val friendRequests by viewModel.friendRequests.collectAsState()
  val conversations by viewModel.conversations.collectAsState()

  val unreadNotificationsCount = remember(notifications) {
    notifications.count { !it.isRead }
  }

  val unreadMessagesCount = remember(conversations) {
    conversations.sumOf { it.unreadCount }
  }

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(toastMessage) {
    toastMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearToast()
    }
  }

  when (authState) {
    is AuthState.Loading -> {
      SplashScreen()
      return
    }
    is AuthState.Unauthenticated -> {
      AuthScreen(viewModel = viewModel)
      return
    }
    is AuthState.Authenticated -> {
      // Session authenticated: proceed to Spark home & features
    }
  }

  val configuration = LocalConfiguration.current
  val isWideScreen = configuration.screenWidthDp >= 600

  val navTabs = listOf(
    NavTabItem("Home", Icons.Filled.Home, Icons.Outlined.Home, "bottom_nav_home"),
    NavTabItem("Reels", Icons.Filled.SmartDisplay, Icons.Outlined.SmartDisplay, "bottom_nav_reels"),
    NavTabItem("Friends", Icons.Filled.People, Icons.Outlined.People, "bottom_nav_friends"),
    NavTabItem("Notifications", Icons.Filled.Notifications, Icons.Outlined.Notifications, "bottom_nav_notifications"),
    NavTabItem("Menu", Icons.Filled.Menu, Icons.Outlined.Menu, "bottom_nav_menu")
  )

  // Full Screen Modals
  when (activeScreen) {
    SocivaScreen.STORY_VIEWER -> {
      StoryViewerScreen(
        viewModel = viewModel,
        onClose = { viewModel.navigateTo(SocivaScreen.MAIN) }
      )
      return
    }
    SocivaScreen.CREATE_POST -> {
      PostComposerScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.MAIN) }
      )
      return
    }
    SocivaScreen.STORY_EDITOR -> {
      StoryEditorScreen(
        viewModel = viewModel,
        onClose = { viewModel.navigateTo(SocivaScreen.MAIN) }
      )
      return
    }
    SocivaScreen.MESSAGES -> {
      MessagesScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.MAIN) },
        onOpenConversation = { convId -> viewModel.navigateToChat(convId) }
      )
      return
    }
    SocivaScreen.CHAT_DETAIL -> {
      ChatDetailScreen(
        conversationId = activeConversationId ?: "conv_sarah",
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.MESSAGES) }
      )
      return
    }
    SocivaScreen.PROFILE -> {
      val authUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
      val profileTargetId = if (activeProfileUserId.isNullOrBlank() || activeProfileUserId == "user_me") {
        authUid.ifBlank { currentUser?.id ?: viewModel.currentUserId.value }
      } else {
        activeProfileUserId!!
      }
      ProfileScreen(
        userId = profileTargetId,
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.MAIN) }
      )
      return
    }
    SocivaScreen.EDIT_PROFILE -> {
      EditProfileScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.PROFILE) }
      )
      return
    }
    SocivaScreen.PROFILE_VISITORS -> {
      ProfileVisitorsScreen(
        viewModel = viewModel,
        onNavigateBack = { viewModel.navigateTo(SocivaScreen.PROFILE) }
      )
      return
    }
    SocivaScreen.SEARCH -> {
      SearchScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.MAIN) }
      )
      return
    }
    SocivaScreen.PAGES -> {
      PagesScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.MAIN) }
      )
      return
    }
    SocivaScreen.GROUPS -> {
      GroupsScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.MAIN) }
      )
      return
    }
    SocivaScreen.PAGE_DETAIL -> {
      PageDetailScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.PAGES) }
      )
      return
    }
    SocivaScreen.GROUP_DETAIL -> {
      GroupDetailScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.GROUPS) }
      )
      return
    }
    SocivaScreen.CREATE_PAGE -> {
      CreatePageScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.PAGES) }
      )
      return
    }
    SocivaScreen.CREATE_GROUP -> {
      CreateGroupScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.GROUPS) }
      )
      return
    }
    SocivaScreen.PAGE_MANAGEMENT -> {
      PageManagementScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.PAGE_DETAIL) }
      )
      return
    }
    SocivaScreen.GROUP_MANAGEMENT -> {
      GroupManagementScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.GROUP_DETAIL) }
      )
      return
    }
    SocivaScreen.SETTINGS -> {
      SettingsScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.MAIN) }
      )
      return
    }
    SocivaScreen.ADMIN -> {
      AdminScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.MAIN) }
      )
      return
    }
    SocivaScreen.SAVED_POSTS -> {
      SavedPostsScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.MAIN) }
      )
      return
    }
    SocivaScreen.ANALYTICS -> {
      ProfileAnalyticsScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(SocivaScreen.PROFILE) }
      )
      return
    }
    SocivaScreen.MAIN -> {
      // Continue to main shell
    }
  }

  // Main App Shell with Bottom Bar or Navigation Rail
  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      if (selectedTab != 1) { // Don't show standard top bar in Reels full screen
        SocivaTopBar(
          onSearchClick = { viewModel.navigateTo(SocivaScreen.SEARCH) },
          onMessagesClick = { viewModel.navigateTo(SocivaScreen.MESSAGES) },
          unreadMessagesCount = unreadMessagesCount,
          onThemeToggle = { viewModel.toggleTheme(!isDarkTheme) },
          isDarkTheme = isDarkTheme,
          activeIdentity = activeIdentity,
          onIdentityClick = { viewModel.showIdentitySwitcher() }
        )
      }
    },
    bottomBar = {
      if (!isWideScreen) {
        NavigationBar(
          containerColor = MaterialTheme.colorScheme.surface,
          tonalElevation = 2.dp,
          modifier = Modifier.testTag("spark_bottom_navigation")
        ) {
          navTabs.forEachIndexed { index, tab ->
            val isSelected = selectedTab == index
            NavigationBarItem(
              icon = {
                BadgedBox(
                  badge = {
                    if (index == 2 && friendRequests.isNotEmpty()) {
                      Badge(
                        containerColor = SocivaPink,
                        contentColor = Color.White
                      ) { Text("${friendRequests.size}") }
                    } else if (index == 3 && unreadNotificationsCount > 0) {
                      Badge(
                        containerColor = SocivaPink,
                        contentColor = Color.White
                      ) { Text("$unreadNotificationsCount") }
                    }
                  }
                ) {
                  Icon(
                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label
                  )
                }
              },
              label = { Text(tab.label) },
              selected = isSelected,
              onClick = { viewModel.selectTab(index) },
              colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SocivaIndigo,
                selectedTextColor = SocivaIndigo,
                indicatorColor = Color(0xFFEEF2FF),
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
              ),
              modifier = Modifier.testTag(tab.testTag)
            )
          }
        }
      }
    }
  ) { innerPadding ->
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Tablet / Desktop Navigation Rail
      if (isWideScreen) {
        NavigationRail(
          containerColor = MaterialTheme.colorScheme.surface,
          modifier = Modifier.testTag("spark_navigation_rail")
        ) {
          Spacer(modifier = Modifier.height(16.dp))
          SocivaLogo(size = 32.dp)
          Spacer(modifier = Modifier.height(24.dp))

          navTabs.forEachIndexed { index, tab ->
            val isSelected = selectedTab == index
            NavigationRailItem(
              icon = {
                BadgedBox(
                  badge = {
                    if (index == 2 && friendRequests.isNotEmpty()) {
                      Badge(
                        containerColor = SocivaPink,
                        contentColor = Color.White
                      ) { Text("${friendRequests.size}") }
                    } else if (index == 3 && unreadNotificationsCount > 0) {
                      Badge(
                        containerColor = SocivaPink,
                        contentColor = Color.White
                      ) { Text("$unreadNotificationsCount") }
                    }
                  }
                ) {
                  Icon(
                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label
                  )
                }
              },
              label = { Text(tab.label) },
              selected = isSelected,
              onClick = { viewModel.selectTab(index) },
              colors = NavigationRailItemDefaults.colors(
                selectedIconColor = SocivaIndigo,
                selectedTextColor = SocivaIndigo,
                indicatorColor = Color(0xFFEEF2FF),
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
              ),
              modifier = Modifier.testTag(tab.testTag + "_rail")
            )
          }
        }
      }

      // Tab Screen Content
      Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        when (selectedTab) {
          0 -> HomeScreen(viewModel = viewModel)
          1 -> ReelsScreen(viewModel = viewModel)
          2 -> FriendsScreen(viewModel = viewModel)
          3 -> NotificationsScreen(viewModel = viewModel)
          4 -> MenuScreen(viewModel = viewModel)
        }
      }
    }
  }

  // Comments Bottom Sheet
  if (commentsPostId != null) {
    CommentsBottomSheet(
      postId = commentsPostId!!,
      viewModel = viewModel,
      onDismiss = { viewModel.closeComments() }
    )
  }

  // Reactions Bottom Sheet
  if (reactionsModalPostId != null) {
    PostReactionsBottomSheet(
      postId = reactionsModalPostId!!,
      viewModel = viewModel,
      onDismiss = { viewModel.closeReactionsModal() }
    )
  }

  // Post Analytics Bottom Sheet
  if (analyticsPostId != null) {
    PostAnalyticsBottomSheet(
      postId = analyticsPostId!!,
      viewModel = viewModel,
      onDismiss = { viewModel.closePostAnalytics() }
    )
  }

  // Share Post Bottom Sheet
  if (sharingPost != null) {
    SharePostBottomSheet(
      post = sharingPost!!,
      currentUser = currentUser,
      onDismiss = { viewModel.closeShareComposer() },
      onShare = { caption, audience ->
        viewModel.createSharedPost(sharingPost!!, caption, audience)
      }
    )
  }

  // Edit Post Dialog
  if (editingPost != null) {
    EditPostDialog(
      post = editingPost!!,
      onDismiss = { viewModel.closeEditPost() },
      onSave = { newContent ->
        viewModel.updatePostContent(editingPost!!.id, newContent)
      }
    )
  }

  // Facebook-Style Identity Switcher Bottom Sheet
  if (isIdentitySwitcherVisible) {
    IdentitySwitcherBottomSheet(
      viewModel = viewModel,
      onDismiss = { viewModel.hideIdentitySwitcher() },
      onCreatePage = {
        viewModel.hideIdentitySwitcher()
        viewModel.openCreatePage()
      },
      onCreateGroup = {
        viewModel.hideIdentitySwitcher()
        viewModel.openCreateGroup()
      }
    )
  }
}
