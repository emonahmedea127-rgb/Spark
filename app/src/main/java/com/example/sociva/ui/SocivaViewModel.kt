package com.example.sociva.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sociva.data.local.SocivaDatabase
import com.example.sociva.data.model.*
import com.example.sociva.data.repository.*
import com.example.sociva.data.service.MediaService
import com.example.sociva.data.service.MediaType
import com.example.sociva.data.service.UploadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

enum class SocivaScreen {
  MAIN,
  PROFILE,
  EDIT_PROFILE,
  PROFILE_VISITORS,
  MESSAGES,
  CHAT_DETAIL,
  CREATE_POST,
  STORY_VIEWER,
  STORY_EDITOR,
  SEARCH,
  PAGES,
  GROUPS,
  PAGE_DETAIL,
  GROUP_DETAIL,
  CREATE_PAGE,
  CREATE_GROUP,
  PAGE_MANAGEMENT,
  GROUP_MANAGEMENT,
  SETTINGS,
  ADMIN,
  SAVED_POSTS,
  ANALYTICS
}

data class StoryMediaSelection(
  val uri: android.net.Uri,
  val isVideo: Boolean = false,
  val mimeType: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class SocivaViewModel(application: Application) : AndroidViewModel(application) {

  private val database = SocivaDatabase.getDatabase(application)
  private val repository = SocivaRepository(database.socivaDao(), viewModelScope)
  private val mediaService = MediaService(application)
  private val authRepository: AuthRepository = LocalAuthRepository(application, database.socivaDao())

  val authState: StateFlow<AuthState> = authRepository.authState

  private val _isAuthLoading = MutableStateFlow(false)
  val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

  private val _authErrorMessage = MutableStateFlow<String?>(null)
  val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

  fun clearAuthError() {
    _authErrorMessage.value = null
  }

  // Photo Upload & Management State
  private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
  val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()
  private var lastUploadTask: (suspend () -> Unit)? = null

  // Navigation State
  private val _selectedTab = MutableStateFlow(0) // 0: Home, 1: Reels, 2: Friends, 3: Notifications, 4: Menu
  val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

  private val _activeScreen = MutableStateFlow(SocivaScreen.MAIN)
  val activeScreen: StateFlow<SocivaScreen> = _activeScreen.asStateFlow()

  private val _activeProfileUserId = MutableStateFlow<String?>("user_me")
  val activeProfileUserId: StateFlow<String?> = _activeProfileUserId.asStateFlow()

  private val _activePageId = MutableStateFlow<String?>("page_spark")
  val activePageId: StateFlow<String?> = _activePageId.asStateFlow()

  private val _activeGroupId = MutableStateFlow<String?>("grp_my")
  val activeGroupId: StateFlow<String?> = _activeGroupId.asStateFlow()

  private val _isIdentitySwitcherVisible = MutableStateFlow(false)
  val isIdentitySwitcherVisible: StateFlow<Boolean> = _isIdentitySwitcherVisible.asStateFlow()

  private val _activeIdentity = MutableStateFlow(
    ActiveIdentity(
      id = "user_me",
      name = "Emon Ahmed",
      username = "emonahmed",
      avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&h=300&fit=crop",
      type = IdentityType.PERSONAL,
      isVerified = true
    )
  )
  val activeIdentity: StateFlow<ActiveIdentity> = _activeIdentity.asStateFlow()

  private val _activeConversationId = MutableStateFlow<String?>("conv_sarah")
  val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

  // Story & Post Media Selection for native picker
  private val _pendingStoryMedia = MutableStateFlow<StoryMediaSelection?>(null)
  val pendingStoryMedia: StateFlow<StoryMediaSelection?> = _pendingStoryMedia.asStateFlow()

  private val _pendingPostUris = MutableStateFlow<List<android.net.Uri>>(emptyList())
  val pendingPostUris: StateFlow<List<android.net.Uri>> = _pendingPostUris.asStateFlow()

  private val _activeStoryIndex = MutableStateFlow(0)
  val activeStoryIndex: StateFlow<Int> = _activeStoryIndex.asStateFlow()

  private val _commentsPostId = MutableStateFlow<String?>(null)
  val commentsPostId: StateFlow<String?> = _commentsPostId.asStateFlow()

  private val _reactionsModalPostId = MutableStateFlow<String?>(null)
  val reactionsModalPostId: StateFlow<String?> = _reactionsModalPostId.asStateFlow()

  // Post Analytics Modal State
  private val _analyticsPostId = MutableStateFlow<String?>(null)
  val analyticsPostId: StateFlow<String?> = _analyticsPostId.asStateFlow()

  // Share Post Composer State
  private val _sharingPost = MutableStateFlow<Post?>(null)
  val sharingPost: StateFlow<Post?> = _sharingPost.asStateFlow()

  // Edit Post Dialog State
  private val _editingPost = MutableStateFlow<Post?>(null)
  val editingPost: StateFlow<Post?> = _editingPost.asStateFlow()

  // Search State
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _searchCategory = MutableStateFlow("All")
  val searchCategory: StateFlow<String> = _searchCategory.asStateFlow()

  // Toast / Status Message
  private val _toastMessage = MutableStateFlow<String?>(null)
  val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

  // Appearance & Settings - default to Light Mode (false) as preferred
  private val _isDarkTheme = MutableStateFlow<Boolean?>(false) // false = Light Mode
  val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

  private val _currentLanguage = MutableStateFlow("English")
  val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

  private val prefs = getApplication<Application>().getSharedPreferences("sociva_session", android.content.Context.MODE_PRIVATE)

  // Authentication & Current User State
  private val _currentUserId = MutableStateFlow(prefs.getString("auth_user_id", "user_me") ?: "user_me")
  val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

  private val _isLoggedIn = MutableStateFlow(false)
  val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

  val userSettings: StateFlow<UserSettings> = _currentUserId.flatMapLatest { uid ->
    repository.getUserSettings(uid)
  }.stateIn(
    viewModelScope, SharingStarted.Eagerly, UserSettings(userId = _currentUserId.value)
  )

  val blockedUsers: StateFlow<List<BlockedUser>> = _currentUserId.flatMapLatest { uid ->
    repository.getBlockedUsers(uid)
  }.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val myProfileViewStats: StateFlow<ProfileViewStats> = _currentUserId.flatMapLatest { uid ->
    repository.getProfileViewStatsFlow(uid)
  }.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileViewStats()
  )

  fun getProfileViewStatsForUser(userId: String): Flow<ProfileViewStats> =
    repository.getProfileViewStatsFlow(userId)

  fun getProfileVisitorsFlow(limit: Int = 50): Flow<List<ProfileVisitorItem>> =
    repository.getProfileVisitorsFlow(_currentUserId.value, limit)

  fun recordProfileVisit(targetUserId: String, originatingPostId: String? = null) {
    viewModelScope.launch {
      repository.recordProfileVisit(_currentUserId.value, targetUserId, originatingPostId)
    }
  }

  fun recordPostView(postId: String) {
    viewModelScope.launch {
      repository.recordPostView(postId, _currentUserId.value)
    }
  }

  fun recordPostViewWithSource(
    postId: String,
    isProfileVisitTarget: Boolean = false,
    source: String = "Home Feed",
    generatedFollow: Boolean = false
  ) {
    viewModelScope.launch {
      repository.recordPostView(
        postId = postId,
        viewerUserId = _currentUserId.value,
        isProfileVisitTarget = isProfileVisitTarget,
        source = source,
        generatedFollow = generatedFollow
      )
    }
  }

  fun recordVideoWatchEvent(event: VideoWatchEvent) {
    viewModelScope.launch {
      repository.recordVideoWatchEvent(event)
    }
  }

  fun getPostAnalytics(postId: String, timeWindow: AnalyticsTimeWindow = AnalyticsTimeWindow.ALL_TIME): Flow<PostAnalytics?> {
    return repository.getPostAnalyticsFlow(postId, _currentUserId.value, timeWindow)
  }

  fun getVideoAnalytics(postId: String, timeWindow: AnalyticsTimeWindow = AnalyticsTimeWindow.ALL_TIME): Flow<VideoAnalytics?> {
    return repository.getVideoAnalyticsFlow(postId, _currentUserId.value, timeWindow)
  }

  fun getProfileAnalytics(timeWindow: AnalyticsTimeWindow = AnalyticsTimeWindow.LAST_7_DAYS): Flow<ProfileAnalytics?> {
    return repository.getProfileAnalyticsFlow(_currentUserId.value, _currentUserId.value, timeWindow)
  }

  fun openPostAnalytics(postId: String) {
    _analyticsPostId.value = postId
  }

  fun closePostAnalytics() {
    _analyticsPostId.value = null
  }

  fun markProfileVisitorsSeen() {
    viewModelScope.launch {
      repository.markProfileVisitorsSeen(_currentUserId.value)
    }
  }

  fun updateProfileViewHistoryEnabled(enabled: Boolean) {
    viewModelScope.launch {
      repository.updateProfileViewHistorySetting(_currentUserId.value, enabled)
      showToast(if (enabled) "Profile View History turned on" else "Profile View History turned off")
    }
  }

  init {
    viewModelScope.launch {
      // Allow splash screen to render smoothly on startup while checking authentication session
      delay(750)
      val state = authRepository.checkAuthState()
      if (state is AuthState.Authenticated) {
        _currentUserId.value = state.session.userId
        _activeProfileUserId.value = state.session.userId
        _isLoggedIn.value = true
        _activeIdentity.value = ActiveIdentity(
          id = state.session.userId,
          name = state.session.displayName,
          username = state.session.email.substringBefore("@").lowercase(),
          avatarUrl = state.session.photoUrl ?: "",
          type = IdentityType.PERSONAL,
          isVerified = false
        )
        repository.setUserPresence(state.session.userId, isOnline = true)
      } else {
        _isLoggedIn.value = false
      }
    }
    viewModelScope.launch {
      userSettings.collect { settings ->
        _isDarkTheme.value = settings.darkTheme
      }
    }
  }

  // Repositories Data Streams
  val allPosts: StateFlow<List<Post>> = repository.allPosts.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val activeStories: StateFlow<List<Story>> = repository.activeStories.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val allReels: StateFlow<List<Reel>> = repository.allReels.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val conversations: StateFlow<List<Conversation>> = _currentUserId.flatMapLatest { uid ->
    repository.getConversations(uid)
  }.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val activeNowUsers: StateFlow<List<User>> = _currentUserId.flatMapLatest { uid ->
    repository.getOnlineUsers(uid)
  }.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val notifications: StateFlow<List<NotificationItem>> = _currentUserId.flatMapLatest { uid ->
    repository.getNotifications(uid)
  }.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val friendRequests: StateFlow<List<FriendRequestItem>> = _currentUserId.flatMapLatest { uid ->
    repository.getFriendRequests(uid)
  }.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val incomingRelationshipRequests: StateFlow<List<RelationshipItem>> = _currentUserId.flatMapLatest { uid ->
    repository.getIncomingRelationshipRequests(uid)
  }.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val sentRelationshipRequests: StateFlow<List<RelationshipItem>> = _currentUserId.flatMapLatest { uid ->
    repository.getSentRelationshipRequests(uid)
  }.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val sentFriendRequests: StateFlow<List<FriendRequestItem>> = _currentUserId.flatMapLatest { uid ->
    repository.getSentFriendRequests(uid)
  }.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val friends: StateFlow<List<User>> = _currentUserId.flatMapLatest { uid ->
    repository.getFriends(uid)
  }.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val allUsers: StateFlow<List<User>> = repository.allUsers.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val pages: StateFlow<List<SocivaPage>> = repository.pages.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val groups: StateFlow<List<SocivaGroup>> = repository.groups.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val reports: StateFlow<List<ReportItem>> = repository.reports.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val savedPosts: StateFlow<List<Post>> = repository.savedPosts.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
  )

  val currentUser: StateFlow<User?> = _currentUserId.flatMapLatest { uid ->
    repository.getUser(uid)
  }.stateIn(
    viewModelScope, SharingStarted.Eagerly, null
  )

  val activePage: StateFlow<SocivaPage?> = _activePageId.flatMapLatest { pageId ->
    if (pageId != null) repository.getPage(pageId) else flowOf(null)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  val activeGroup: StateFlow<SocivaGroup?> = _activeGroupId.flatMapLatest { groupId ->
    if (groupId != null) repository.getGroup(groupId) else flowOf(null)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  val availableIdentities: StateFlow<List<ActiveIdentity>> = combine(
    currentUser,
    pages,
    groups
  ) { user, pageList, groupList ->
    val list = mutableListOf<ActiveIdentity>()
    if (user != null) {
      list.add(
        ActiveIdentity(
          id = user.id,
          name = user.fullName,
          username = user.username,
          avatarUrl = user.avatarUrl,
          type = IdentityType.PERSONAL,
          isVerified = user.isVerified
        )
      )
    }
    pageList.filter { it.isAdmin || it.ownerId == (user?.id ?: "user_me") }.forEach { page ->
      list.add(
        ActiveIdentity(
          id = page.id,
          name = page.name,
          username = if (page.username.isNotBlank()) page.username else page.name.lowercase().replace(" ", ""),
          avatarUrl = page.avatarUrl,
          type = IdentityType.PAGE,
          badge = page.category,
          isVerified = page.followersCount > 10000
        )
      )
    }
    groupList.filter { it.role in listOf("Owner", "Admin", "Moderator") || it.ownerId == (user?.id ?: "user_me") }.forEach { group ->
      list.add(
        ActiveIdentity(
          id = group.id,
          name = group.name,
          username = group.name.lowercase().replace(" ", ""),
          avatarUrl = group.avatarUrl,
          type = IdentityType.GROUP,
          badge = group.role
        )
      )
    }
    list
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  fun switchIdentity(identity: ActiveIdentity) {
    _activeIdentity.value = identity
    _isIdentitySwitcherVisible.value = false
    showToast("Switched to ${identity.name}")
    when (identity.type) {
      IdentityType.PAGE -> {
        _activePageId.value = identity.id
      }
      IdentityType.GROUP -> {
        _activeGroupId.value = identity.id
      }
      IdentityType.PERSONAL -> {
        _activeProfileUserId.value = identity.id
      }
    }
  }

  fun showIdentitySwitcher() {
    _isIdentitySwitcherVisible.value = true
  }

  fun hideIdentitySwitcher() {
    _isIdentitySwitcherVisible.value = false
  }

  fun resetToPersonalIdentity() {
    val user = currentUser.value
    if (user != null) {
      _activeIdentity.value = ActiveIdentity(
        id = user.id,
        name = user.fullName,
        username = user.username,
        avatarUrl = user.avatarUrl,
        type = IdentityType.PERSONAL,
        isVerified = user.isVerified
      )
      showToast("Switched to Personal Profile: ${user.fullName}")
    }
  }

  fun openPage(pageId: String) {
    _activePageId.value = pageId
    _activeScreen.value = SocivaScreen.PAGE_DETAIL
  }

  fun openGroup(groupId: String) {
    _activeGroupId.value = groupId
    _activeScreen.value = SocivaScreen.GROUP_DETAIL
  }

  fun openCreatePage() {
    _activeScreen.value = SocivaScreen.CREATE_PAGE
  }

  fun openCreateGroup() {
    _activeScreen.value = SocivaScreen.CREATE_GROUP
  }

  fun openPageManagement(pageId: String) {
    _activePageId.value = pageId
    _activeScreen.value = SocivaScreen.PAGE_MANAGEMENT
  }

  fun openGroupManagement(groupId: String) {
    _activeGroupId.value = groupId
    _activeScreen.value = SocivaScreen.GROUP_MANAGEMENT
  }

  fun getPagePosts(pageId: String): Flow<List<Post>> = repository.getPostsForPage(pageId)

  fun getGroupPosts(groupId: String): Flow<List<Post>> = repository.getPostsForGroup(groupId)

  fun getPageFollowers(pageId: String): Flow<List<PageFollower>> = repository.getPageFollowers(pageId)

  fun isUserPageFollower(pageId: String): Flow<Boolean> =
    repository.isUserPageFollower(pageId, currentUser.value?.id ?: "user_me")

  fun getGroupMembers(groupId: String): Flow<List<GroupMember>> = repository.getGroupMembers(groupId)

  fun getGroupJoinRequests(groupId: String): Flow<List<GroupJoinRequest>> = repository.getGroupJoinRequests(groupId)

  fun toggleFollowPage(pageId: String) {
    val user = currentUser.value ?: return
    viewModelScope.launch {
      repository.togglePageFollow(pageId, user)
      showToast("Follow status updated")
    }
  }

  fun joinOrLeaveGroup(groupId: String) {
    val user = currentUser.value ?: return
    viewModelScope.launch {
      val group = repository.getGroup(groupId).first()
      if (group != null) {
        if (group.isJoined) {
          repository.leaveGroup(groupId, user.id)
          showToast("Left group ${group.name}")
        } else {
          repository.joinOrRequestGroup(groupId, user)
          if (group.privacy.contains("Private", ignoreCase = true)) {
            showToast("Join request sent to group admins")
          } else {
            showToast("Joined group ${group.name}!")
          }
        }
      }
    }
  }

  fun approveGroupJoinRequest(request: GroupJoinRequest) {
    viewModelScope.launch {
      repository.approveGroupJoinRequest(request)
      showToast("Approved ${request.userName} into the group")
    }
  }

  fun rejectGroupJoinRequest(requestId: String) {
    viewModelScope.launch {
      repository.rejectGroupJoinRequest(requestId)
      showToast("Declined join request")
    }
  }

  fun removeGroupMember(groupId: String, userId: String, userName: String) {
    viewModelScope.launch {
      repository.removeGroupMember(groupId, userId)
      showToast("Removed $userName from group")
    }
  }

  fun updateGroupMemberRole(groupId: String, userId: String, newRole: String) {
    viewModelScope.launch {
      repository.updateGroupMemberRole(groupId, userId, newRole)
      showToast("Updated role to $newRole")
    }
  }

  fun updatePageDetails(page: SocivaPage) {
    viewModelScope.launch {
      repository.updatePage(page)
      showToast("Page settings updated")
    }
  }

  fun updateGroupDetails(group: SocivaGroup) {
    viewModelScope.launch {
      repository.updateGroup(group)
      showToast("Group settings updated")
    }
  }

  fun createPage(
    name: String,
    category: String,
    description: String,
    username: String = "",
    website: String = "",
    location: String = "",
    email: String = "",
    phone: String = ""
  ) {
    val user = currentUser.value
    viewModelScope.launch {
      val newPage = repository.createPage(
        name = name,
        category = category,
        description = description,
        username = username,
        website = website,
        location = location,
        contactEmail = email,
        contactPhone = phone,
        creatorUser = user
      )
      showToast("Page '${newPage.name}' created! Switched to your new Page.")
      switchIdentity(
        ActiveIdentity(
          id = newPage.id,
          name = newPage.name,
          username = newPage.username,
          avatarUrl = newPage.avatarUrl,
          type = IdentityType.PAGE,
          badge = newPage.category
        )
      )
      _activePageId.value = newPage.id
      _activeScreen.value = SocivaScreen.PAGE_DETAIL
    }
  }

  fun createGroup(
    name: String,
    privacy: String,
    description: String,
    rules: String = ""
  ) {
    val user = currentUser.value
    viewModelScope.launch {
      val newGroup = repository.createGroup(
        name = name,
        privacy = privacy,
        description = description,
        rules = rules,
        creatorUser = user
      )
      showToast("Group '${newGroup.name}' created! Switched to your new Group.")
      switchIdentity(
        ActiveIdentity(
          id = newGroup.id,
          name = newGroup.name,
          username = newGroup.name.lowercase().replace(" ", ""),
          avatarUrl = newGroup.avatarUrl,
          type = IdentityType.GROUP,
          badge = "Owner"
        )
      )
      _activeGroupId.value = newGroup.id
      _activeScreen.value = SocivaScreen.GROUP_DETAIL
    }
  }

  fun getPostComments(postId: String): Flow<List<Comment>> {
    val currentUserId = currentUser.value?.id
    return repository.getComments(postId, currentUserId)
  }

  fun getConversationMessages(convId: String): Flow<List<Message>> {
    val uid = _currentUserId.value
    return repository.getMessages(convId, uid)
  }

  fun getConversation(convId: String): Flow<Conversation?> {
    val uid = _currentUserId.value
    return repository.getConversation(convId, uid)
  }

  fun getUser(userId: String): Flow<User?> = repository.getUser(userId)

  fun isUserBlockedFlow(userId: String): Flow<Boolean> {
    val uid = _currentUserId.value
    return repository.isUserBlockedFlow(uid, userId)
  }

  fun getPostsByUser(userId: String): Flow<List<Post>> = repository.getPostsByUser(userId)

  // Navigation Methods
  fun selectTab(tabIndex: Int) {
    _selectedTab.value = tabIndex
    _activeScreen.value = SocivaScreen.MAIN
  }

  fun navigateTo(screen: SocivaScreen) {
    _activeScreen.value = screen
  }

  fun navigateToProfile(userId: String) {
    _activeProfileUserId.value = userId
    _activeScreen.value = SocivaScreen.PROFILE
  }

  fun openProfileVisitors() {
    _activeScreen.value = SocivaScreen.PROFILE_VISITORS
    markProfileVisitorsSeen()
  }

  fun navigateToChat(convId: String) {
    _activeConversationId.value = convId
    _activeScreen.value = SocivaScreen.CHAT_DETAIL
  }

  fun openStoryViewer(index: Int) {
    _activeStoryIndex.value = index
    _activeScreen.value = SocivaScreen.STORY_VIEWER
    val storiesList = activeStories.value
    if (index in storiesList.indices) {
      viewModelScope.launch {
        repository.markStoryViewed(storiesList[index].id)
      }
    }
  }

  fun nextStory() {
    val max = activeStories.value.size
    if (_activeStoryIndex.value < max - 1) {
      _activeStoryIndex.value += 1
      val story = activeStories.value[_activeStoryIndex.value]
      viewModelScope.launch { repository.markStoryViewed(story.id) }
    } else {
      _activeScreen.value = SocivaScreen.MAIN
    }
  }

  fun prevStory() {
    if (_activeStoryIndex.value > 0) {
      _activeStoryIndex.value -= 1
    } else {
      _activeScreen.value = SocivaScreen.MAIN
    }
  }

  fun openComments(postId: String) {
    _commentsPostId.value = postId
  }

  fun closeComments() {
    _commentsPostId.value = null
  }

  fun openReactionsModal(postId: String) {
    _reactionsModalPostId.value = postId
  }

  fun closeReactionsModal() {
    _reactionsModalPostId.value = null
  }

  fun getPostReactionUsers(postId: String): Flow<List<PostReactionUser>> {
    return repository.getPostReactionUsers(postId, _currentUserId.value)
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun setSearchCategory(category: String) {
    _searchCategory.value = category
  }

  fun showToast(msg: String) {
    _toastMessage.value = msg
  }

  fun clearToast() {
    _toastMessage.value = null
  }

  fun toggleTheme(dark: Boolean?) {
    val enabled = dark == true
    _isDarkTheme.value = enabled
    val uid = _currentUserId.value
    viewModelScope.launch {
      repository.updateDarkTheme(uid, enabled)
    }
    if (enabled) {
      showToast("Dark mode enabled 🌙")
    } else {
      showToast("Light mode enabled ☀️")
    }
  }

  fun updateDataSaver(enabled: Boolean) {
    val uid = _currentUserId.value
    viewModelScope.launch {
      repository.updateDataSaver(uid, enabled)
    }
    showToast(if (enabled) "Data Saver enabled" else "Data Saver disabled")
  }

  fun updatePushNotifications(enabled: Boolean) {
    val uid = _currentUserId.value
    viewModelScope.launch {
      repository.updatePushNotifications(uid, enabled)
    }
    showToast(if (enabled) "Push notifications enabled" else "Push notifications disabled")
  }

  fun updateInAppSounds(enabled: Boolean) {
    val uid = _currentUserId.value
    viewModelScope.launch {
      repository.updateInAppSounds(uid, enabled)
    }
    showToast(if (enabled) "In-app sounds enabled 🔊" else "In-app sounds muted 🔇")
  }

  fun updateProfileVisibility(visibility: String) {
    val uid = _currentUserId.value
    viewModelScope.launch {
      repository.updateProfileVisibility(uid, visibility)
    }
    showToast("Profile visibility set to $visibility")
  }

  fun updateTwoFactor(enabled: Boolean, method: String = "AUTHENTICATOR") {
    val uid = _currentUserId.value
    viewModelScope.launch {
      repository.updateTwoFactor(uid, enabled, method)
    }
    showToast(if (enabled) "Two-Factor Authentication activated ($method) 🛡️" else "2FA deactivated")
  }

  fun changePassword() {
    val uid = _currentUserId.value
    viewModelScope.launch {
      repository.updatePassword(uid)
    }
    showToast("Password updated successfully 🔒")
  }

  fun blockUser(targetUserId: String) {
    val uid = _currentUserId.value
    viewModelScope.launch {
      val targetUser = repository.getUser(targetUserId).first()
      val name = targetUser?.fullName ?: "User"
      val success = repository.blockUser(uid, targetUserId)
      if (success) {
        showToast("Blocked $name. They cannot view your profile or message you.")
      } else {
        showToast("Could not block user.")
      }
    }
  }

  fun unblockUser(targetUserId: String) {
    val uid = _currentUserId.value
    viewModelScope.launch {
      val targetUser = repository.getUser(targetUserId).first()
      val name = targetUser?.fullName ?: "User"
      repository.unblockUser(uid, targetUserId)
      showToast("Unblocked $name.")
    }
  }

  fun setLanguage(lang: String) {
    _currentLanguage.value = lang
    showToast("Language changed to $lang")
  }

  // Auth Operations
  fun login(emailOrPhone: String, password: String, onComplete: ((AuthResult) -> Unit)? = null) {
    if (_isAuthLoading.value) return
    _isAuthLoading.value = true
    _authErrorMessage.value = null

    viewModelScope.launch {
      try {
        val result = authRepository.login(emailOrPhone, password)
        when (result) {
          is AuthResult.Success -> {
            _currentUserId.value = result.session.userId
            _activeProfileUserId.value = result.session.userId
            _isLoggedIn.value = true
            _activeIdentity.value = ActiveIdentity(
              id = result.session.userId,
              name = result.session.displayName,
              username = result.session.email.substringBefore("@").lowercase(),
              avatarUrl = result.session.photoUrl ?: "",
              type = IdentityType.PERSONAL,
              isVerified = false
            )
            _activeScreen.value = SocivaScreen.MAIN
            prefs.edit().putString("auth_user_id", result.session.userId).putBoolean("is_logged_in", true).apply()
            repository.setUserPresence(result.session.userId, isOnline = true)
            showToast("Welcome back, ${result.session.displayName}!")
          }
          is AuthResult.Error -> {
            _authErrorMessage.value = result.message
          }
        }
        onComplete?.invoke(result)
      } catch (e: Exception) {
        _authErrorMessage.value = e.localizedMessage ?: "An unexpected error occurred during login."
      } finally {
        _isAuthLoading.value = false
      }
    }
  }

  fun register(
    firstName: String,
    lastName: String,
    email: String,
    phone: String?,
    password: String,
    dateOfBirth: String,
    gender: String,
    onComplete: ((AuthResult) -> Unit)? = null
  ) {
    if (_isAuthLoading.value) return
    _isAuthLoading.value = true
    _authErrorMessage.value = null

    viewModelScope.launch {
      try {
        val result = authRepository.register(
          firstName = firstName,
          lastName = lastName,
          email = email,
          phone = phone,
          password = password,
          dateOfBirth = dateOfBirth,
          gender = gender
        )
        when (result) {
          is AuthResult.Success -> {
            _currentUserId.value = result.session.userId
            _activeProfileUserId.value = result.session.userId
            _isLoggedIn.value = true
            _activeIdentity.value = ActiveIdentity(
              id = result.session.userId,
              name = result.session.displayName,
              username = result.session.email.substringBefore("@").lowercase(),
              avatarUrl = result.session.photoUrl ?: "",
              type = IdentityType.PERSONAL,
              isVerified = false
            )
            _activeScreen.value = SocivaScreen.MAIN
            prefs.edit().putString("auth_user_id", result.session.userId).putBoolean("is_logged_in", true).apply()
            repository.setUserPresence(result.session.userId, isOnline = true)
            showToast("Account created! Welcome to Spark, ${result.session.displayName}.")
          }
          is AuthResult.Error -> {
            _authErrorMessage.value = result.message
          }
        }
        onComplete?.invoke(result)
      } catch (e: Exception) {
        _authErrorMessage.value = e.localizedMessage ?: "Failed to create account. Please try again."
      } finally {
        _isAuthLoading.value = false
      }
    }
  }

  fun sendPasswordReset(email: String, onResult: (PasswordResetResult) -> Unit) {
    if (_isAuthLoading.value) return
    _isAuthLoading.value = true
    _authErrorMessage.value = null

    viewModelScope.launch {
      try {
        val result = authRepository.sendPasswordReset(email)
        when (result) {
          is PasswordResetResult.Success -> {
            _authErrorMessage.value = null
          }
          is PasswordResetResult.Error -> {
            _authErrorMessage.value = result.message
          }
        }
        onResult(result)
      } catch (e: Exception) {
        val error = PasswordResetResult.Error(e.localizedMessage ?: "Failed to process password reset.")
        _authErrorMessage.value = error.message
        onResult(error)
      } finally {
        _isAuthLoading.value = false
      }
    }
  }

  fun logout() {
    val uid = _currentUserId.value
    viewModelScope.launch {
      try {
        repository.setUserPresence(uid, isOnline = false)
        authRepository.logout()
      } catch (e: Exception) {
        // Safe catch
      }
      _isLoggedIn.value = false
      prefs.edit().putBoolean("is_logged_in", false).apply()
      _activeScreen.value = SocivaScreen.MAIN
      showToast("You have been logged out.")
    }
  }

  fun switchUser(userId: String) {
    val prev = _currentUserId.value
    viewModelScope.launch {
      repository.setUserPresence(prev, isOnline = false)
      _currentUserId.value = userId
      _activeProfileUserId.value = userId
      prefs.edit().putString("auth_user_id", userId).apply()
      repository.setUserPresence(userId, isOnline = true)
      val user = repository.getUser(userId).first()
      showToast("Active user: ${user?.fullName ?: userId}")
    }
  }

  // Actions
  fun createPost(
    content: String,
    mediaUrls: List<String>,
    feeling: String?,
    audience: PostAudience,
    taggedUserIds: List<String> = emptyList()
  ) {
    val currentIdent = activeIdentity.value
    val author = currentUser.value ?: return
    viewModelScope.launch {
      if (currentIdent.type == IdentityType.PAGE) {
        repository.createPost(
          authorId = currentIdent.id,
          authorName = currentIdent.name,
          authorUsername = currentIdent.username,
          authorAvatar = currentIdent.avatarUrl,
          isAuthorVerified = currentIdent.isVerified,
          authorType = "PAGE",
          content = content,
          mediaUrls = mediaUrls,
          feeling = feeling,
          audience = audience,
          taggedUserIds = taggedUserIds
        )
      } else if (currentIdent.type == IdentityType.GROUP) {
        repository.createPost(
          author = author,
          content = content,
          mediaUrls = mediaUrls,
          feeling = feeling,
          audience = audience,
          taggedUserIds = taggedUserIds,
          authorType = "PERSONAL",
          targetGroupId = currentIdent.id,
          targetGroupName = currentIdent.name
        )
      } else {
        repository.createPost(
          author = author,
          content = content,
          mediaUrls = mediaUrls,
          feeling = feeling,
          audience = audience,
          taggedUserIds = taggedUserIds,
          authorType = "PERSONAL"
        )
      }
      _activeScreen.value = SocivaScreen.MAIN
      showToast("Your post has been published to Spark! 🎉")
    }
  }

  fun deletePost(postId: String) {
    viewModelScope.launch {
      repository.deletePost(postId)
      showToast("Post deleted")
    }
  }

  fun setReaction(postId: String, reaction: ReactionType) {
    viewModelScope.launch {
      repository.setReaction(postId, reaction)
    }
  }

  fun toggleSavePost(postId: String) {
    viewModelScope.launch {
      repository.toggleSavePost(postId)
      showToast("Post bookmark updated")
    }
  }

  fun openShareComposer(post: Post) {
    if (post.audience == PostAudience.ONLY_ME && post.authorId != _currentUserId.value) {
      showToast("This post's privacy settings prevent sharing.")
      return
    }
    _sharingPost.value = post
  }

  fun closeShareComposer() {
    _sharingPost.value = null
  }

  fun createSharedPost(originalPost: Post, caption: String, audience: PostAudience) {
    val author = currentUser.value ?: return
    viewModelScope.launch {
      repository.createSharedPost(
        sharer = author,
        originalPost = originalPost,
        caption = caption,
        audience = audience
      )
      _sharingPost.value = null
      showToast("Post shared to your timeline! 🚀")
    }
  }

  fun openEditPost(post: Post) {
    _editingPost.value = post
  }

  fun closeEditPost() {
    _editingPost.value = null
  }

  fun updatePostContent(postId: String, newContent: String) {
    viewModelScope.launch {
      repository.updatePostContent(postId, newContent.trim())
      _editingPost.value = null
      showToast("Post updated! ✏️")
    }
  }

  fun sharePost(postId: String) {
    viewModelScope.launch {
      val p = allPosts.value.find { it.id == postId }
      if (p != null) {
        openShareComposer(p)
      } else {
        repository.sharePost(postId)
        showToast("Post shared to your timeline! 🚀")
      }
    }
  }

  fun addComment(postId: String, text: String, parentCommentId: String? = null) {
    val author = currentUser.value ?: return
    if (text.isBlank()) return
    viewModelScope.launch {
      repository.addComment(postId, author, text.trim(), parentCommentId)
    }
  }

  fun reactToComment(commentId: String, reactionType: ReactionType) {
    val user = currentUser.value ?: return
    viewModelScope.launch {
      repository.reactToComment(commentId, user, reactionType)
    }
  }

  fun removeCommentReaction(commentId: String) {
    val user = currentUser.value ?: return
    viewModelScope.launch {
      repository.removeCommentReaction(commentId, user.id)
    }
  }

  fun editComment(commentId: String, newContent: String) {
    val user = currentUser.value ?: return
    if (newContent.isBlank()) return
    viewModelScope.launch {
      repository.editComment(commentId, user.id, newContent.trim())
    }
  }

  fun deleteComment(commentId: String, postId: String) {
    val user = currentUser.value ?: return
    viewModelScope.launch {
      repository.deleteComment(
        commentId = commentId,
        postId = postId,
        userId = user.id,
        isPostOwnerOrAdmin = false
      )
    }
  }

  fun reportComment(commentId: String, snippet: String, reason: String) {
    viewModelScope.launch {
      repository.submitReport(
        targetType = "comment",
        targetId = commentId,
        title = "Comment: ${snippet.take(40)}",
        reason = reason
      )
    }
  }

  fun selectStoryMedia(uri: android.net.Uri, isVideo: Boolean, mimeType: String) {
    _pendingStoryMedia.value = StoryMediaSelection(uri, isVideo, mimeType)
    _activeScreen.value = SocivaScreen.STORY_EDITOR
  }

  fun clearStoryMedia() {
    _pendingStoryMedia.value = null
  }

  fun setPendingPostUris(uris: List<android.net.Uri>) {
    _pendingPostUris.value = uris
  }

  fun appendPendingPostUris(uris: List<android.net.Uri>) {
    val current = _pendingPostUris.value.toMutableList()
    uris.forEach { if (!current.contains(it)) current.add(it) }
    _pendingPostUris.value = current
  }

  fun removePendingPostUri(uri: android.net.Uri) {
    _pendingPostUris.value = _pendingPostUris.value.filter { it != uri }
  }

  fun clearPendingPostUris() {
    _pendingPostUris.value = emptyList()
  }

  fun createStory(text: String, mediaUrl: String?, gradientIndex: Int) {
    val user = currentUser.value ?: return
    viewModelScope.launch {
      repository.createStory(user, text, mediaUrl, gradientIndex)
      _pendingStoryMedia.value = null
      _activeScreen.value = SocivaScreen.MAIN
      showToast("Your story is live for 24 hours! ✨")
    }
  }

  fun uploadAndCreateStory(
    uri: android.net.Uri,
    text: String,
    gradientIndex: Int,
    isVideo: Boolean = false
  ) {
    val user = currentUser.value ?: return
    viewModelScope.launch {
      _uploadState.value = UploadState.Validating("Validating story media...")
      val validation = mediaService.validateMediaUri(uri)
      if (!validation.isValid) {
        _uploadState.value = UploadState.Error(validation.errorMessage ?: "Invalid media file.")
        showToast(validation.errorMessage ?: "Invalid media file.")
        return@launch
      }

      val mediaType = MediaType.STORY_MEDIA
      val result = mediaService.uploadMediaFromUri(
        uri = uri,
        userId = user.id,
        type = mediaType,
        onProgress = { p ->
          _uploadState.value = UploadState.Uploading(p)
        }
      )

      result.fold(
        onSuccess = { processed ->
          repository.createStory(
            user = user,
            text = text,
            mediaUrl = processed.url,
            gradientIndex = gradientIndex
          )
          _uploadState.value = UploadState.Success(processed.url, mediaType)
          _pendingStoryMedia.value = null
          _activeScreen.value = SocivaScreen.MAIN
          showToast("Story shared! 🌟")
          delay(800)
          _uploadState.value = UploadState.Idle
        },
        onFailure = { err ->
          _uploadState.value = UploadState.Error(err.localizedMessage ?: "Failed to upload story media.")
          showToast("Upload failed: ${err.localizedMessage ?: "Unknown error"}")
        }
      )
    }
  }

  fun uploadAndCreatePost(
    content: String,
    uris: List<android.net.Uri>,
    additionalUrls: List<String> = emptyList(),
    feeling: String?,
    audience: PostAudience,
    taggedUserIds: List<String> = emptyList()
  ) {
    val user = currentUser.value ?: return
    viewModelScope.launch {
      val uploadedUrls = mutableListOf<String>()
      uploadedUrls.addAll(additionalUrls)

      if (uris.isNotEmpty()) {
        _uploadState.value = UploadState.Validating("Preparing ${uris.size} media file(s)...")
        for ((idx, uri) in uris.withIndex()) {
          val validation = mediaService.validateMediaUri(uri)
          if (!validation.isValid) {
            _uploadState.value = UploadState.Error(validation.errorMessage ?: "Media file #$idx is invalid.")
            showToast("Error with media: ${validation.errorMessage}")
            return@launch
          }
          val mediaType = MediaType.POST_MEDIA

          val uploadRes = mediaService.uploadMediaFromUri(
            uri = uri,
            userId = user.id,
            type = mediaType,
            onProgress = { p ->
              val overall = (idx.toFloat() + p) / uris.size.toFloat()
              _uploadState.value = UploadState.Uploading(overall)
            }
          )

          uploadRes.fold(
            onSuccess = { processed ->
              uploadedUrls.add(processed.url)
            },
            onFailure = { err ->
              _uploadState.value = UploadState.Error("Upload failed for item #${idx + 1}: ${err.localizedMessage}")
              showToast("Failed to upload media: ${err.localizedMessage}")
              return@launch
            }
          )
        }
      }

      // Save post to repository
      val currentIdent = activeIdentity.value
      if (currentIdent.type == IdentityType.PAGE) {
        repository.createPost(
          authorId = currentIdent.id,
          authorName = currentIdent.name,
          authorUsername = currentIdent.username,
          authorAvatar = currentIdent.avatarUrl,
          isAuthorVerified = currentIdent.isVerified,
          authorType = "PAGE",
          content = content,
          mediaUrls = uploadedUrls,
          feeling = feeling,
          audience = audience,
          taggedUserIds = taggedUserIds
        )
      } else if (currentIdent.type == IdentityType.GROUP) {
        repository.createPost(
          author = user,
          content = content,
          mediaUrls = uploadedUrls,
          feeling = feeling,
          audience = audience,
          taggedUserIds = taggedUserIds,
          authorType = "PERSONAL",
          targetGroupId = currentIdent.id,
          targetGroupName = currentIdent.name
        )
      } else {
        repository.createPost(
          author = user,
          content = content,
          mediaUrls = uploadedUrls,
          feeling = feeling,
          audience = audience,
          taggedUserIds = taggedUserIds,
          authorType = "PERSONAL"
        )
      }

      _uploadState.value = UploadState.Success(uploadedUrls.firstOrNull() ?: "", MediaType.POST_MEDIA)
      _pendingPostUris.value = emptyList()
      _activeScreen.value = SocivaScreen.MAIN
      showToast("Your post has been published to Spark! 🎉")
      delay(800)
      _uploadState.value = UploadState.Idle
    }
  }

  fun toggleReelLike(reelId: String) {
    viewModelScope.launch { repository.toggleReelLike(reelId) }
  }

  fun toggleReelSave(reelId: String) {
    viewModelScope.launch { repository.toggleReelSave(reelId) }
  }

  fun toggleReelFollow(reelId: String) {
    viewModelScope.launch { repository.toggleReelFollow(reelId) }
  }

  fun sendMessage(convId: String, text: String, mediaUrl: String? = null, messageType: String = "TEXT") {
    if (text.isBlank() && mediaUrl.isNullOrBlank()) return
    val senderId = _currentUserId.value
    viewModelScope.launch {
      repository.sendMessage(convId, senderId, text.trim(), mediaUrl, messageType)
    }
  }

  fun markConversationAsRead(convId: String) {
    val myId = _currentUserId.value
    viewModelScope.launch {
      repository.markConversationAsRead(convId, myId)
    }
  }

  fun setTyping(convId: String, isTyping: Boolean) {
    val myId = _currentUserId.value
    repository.setTyping(convId, myId, isTyping)
  }

  fun getTypingUsers(convId: String): Flow<List<String>> {
    val myId = _currentUserId.value
    return repository.getTypingUsers(convId, myId)
  }

  fun softDeleteMessage(messageId: String) {
    viewModelScope.launch {
      repository.softDeleteMessage(messageId)
    }
  }

  fun deleteMessage(messageId: String) {
    viewModelScope.launch {
      repository.deleteMessage(messageId)
    }
  }

  fun openOrCreateConversationWithUser(targetUserId: String) {
    val myId = _currentUserId.value
    viewModelScope.launch {
      val convId = repository.getOrCreateConversation(myId, targetUserId)
      _activeConversationId.value = convId
      _activeScreen.value = SocivaScreen.CHAT_DETAIL
    }
  }

  fun searchUsers(query: String): Flow<List<User>> {
    val myId = _currentUserId.value
    return repository.searchUsers(query, myId)
  }

  fun getAllUsersExceptMe(): Flow<List<User>> {
    val myId = _currentUserId.value
    return repository.getAllUsersExcept(myId)
  }

  fun getFriendStatusFlow(userId: String): Flow<FriendStatus> =
    repository.getFriendStatusFlow("user_me", userId)

  fun isFollowingFlow(userId: String): Flow<Boolean> =
    repository.isFollowingFlow("user_me", userId)

  fun sendFriendRequest(targetUserId: String) {
    viewModelScope.launch {
      val success = repository.sendFriendRequest("user_me", targetUserId)
      if (success) {
        showToast("Friend request sent! You are now following them.")
      }
    }
  }

  fun cancelFriendRequest(targetUserId: String) {
    viewModelScope.launch {
      val success = repository.cancelFriendRequest("user_me", targetUserId)
      if (success) {
        showToast("Friend request cancelled")
      }
    }
  }

  fun cancelFriendRequestById(requestId: String) {
    viewModelScope.launch {
      val success = repository.cancelFriendRequestById(requestId)
      if (success) {
        showToast("Friend request cancelled")
      }
    }
  }

  fun acceptFriendRequest(request: FriendRequestItem) {
    viewModelScope.launch {
      repository.acceptFriendRequest(request.id, "user_me")
      showToast("You and ${request.fullName} are now friends! 🤝")
    }
  }

  fun acceptFriendRequestById(requestId: String) {
    viewModelScope.launch {
      val req = friendRequests.value.find { it.id == requestId }
      repository.acceptFriendRequest(requestId, "user_me")
      showToast("You and ${req?.fullName ?: "user"} are now friends! 🤝")
    }
  }

  fun acceptFriendRequestFromUser(senderUserId: String) {
    viewModelScope.launch {
      val req = friendRequests.value.find { it.senderId == senderUserId }
      if (req != null) {
        repository.acceptFriendRequest(req.id, "user_me")
        showToast("You and ${req.fullName} are now friends! 🤝")
      } else {
        // Fallback: check pending request between
        val directReq = repository.getIncomingFriendRequests(_currentUserId.value).first().find { it.senderId == senderUserId }
        if (directReq != null) {
          repository.acceptFriendRequest(directReq.id, "user_me")
          showToast("Friend request accepted! 🤝")
        }
      }
    }
  }

  fun rejectFriendRequest(requestId: String) {
    viewModelScope.launch {
      repository.rejectFriendRequest(requestId)
      showToast("Friend request deleted")
    }
  }

  fun removeFriend(userId: String) {
    viewModelScope.launch {
      repository.removeFriend("user_me", userId)
      showToast("Removed from friends")
    }
  }

  fun followUser(userId: String) {
    viewModelScope.launch {
      repository.followUser("user_me", userId)
      showToast("Following")
    }
  }

  fun unfollowUser(userId: String) {
    viewModelScope.launch {
      repository.unfollowUser("user_me", userId)
      showToast("Unfollowed")
    }
  }

  fun toggleFriend(userId: String) {
    viewModelScope.launch {
      repository.toggleFriend(userId)
    }
  }

  fun toggleFollow(userId: String) {
    viewModelScope.launch {
      repository.toggleFollow(userId)
    }
  }

  fun updateUserProfile(fullName: String, bio: String, work: String, education: String, location: String) {
    viewModelScope.launch {
      repository.updateUserProfile("user_me", fullName, bio, work, education, location)
      showToast("Profile updated successfully! ✨")
    }
  }

  fun updateFullUserProfile(updated: User) {
    viewModelScope.launch {
      repository.updateFullUserProfile(updated)
      showToast("Profile updated successfully! ✨")
    }
  }

  suspend fun saveUserProfile(updated: User): Result<Unit> {
    return try {
      repository.updateFullUserProfile(updated)
      showToast("Profile updated successfully! ✨")
      Result.success(Unit)
    } catch (e: Exception) {
      showToast("Error updating profile: ${e.message}")
      Result.failure(e)
    }
  }

  fun navigateToMyProfile() {
    _activeProfileUserId.value = _currentUserId.value
    _activeScreen.value = SocivaScreen.PROFILE
  }

  // --- Relationship Management ---
  fun sendRelationshipRequest(
    targetUserId: String,
    relationshipType: String,
    customText: String? = null,
    privacy: String = "Public"
  ) {
    val user = currentUser.value ?: return
    viewModelScope.launch {
      repository.sendRelationshipRequest(user, targetUserId, relationshipType, customText, privacy)
        .onSuccess {
          showToast("Relationship request sent")
        }
        .onFailure {
          showToast(it.message ?: "Failed to send relationship request")
        }
    }
  }

  fun acceptRelationshipRequest(requestId: String) {
    viewModelScope.launch {
      repository.acceptRelationshipRequest(requestId, _currentUserId.value)
        .onSuccess {
          showToast("Relationship accepted! 💕")
        }
        .onFailure {
          showToast(it.message ?: "Failed to accept relationship")
        }
    }
  }

  fun declineRelationshipRequest(requestId: String) {
    viewModelScope.launch {
      repository.declineRelationshipRequest(requestId, _currentUserId.value)
        .onSuccess {
          showToast("Relationship request declined")
        }
        .onFailure {
          showToast(it.message ?: "Failed to decline request")
        }
    }
  }

  fun cancelRelationshipRequest(requestId: String) {
    viewModelScope.launch {
      repository.cancelRelationshipRequest(requestId, _currentUserId.value)
        .onSuccess {
          showToast("Relationship request cancelled")
        }
    }
  }

  fun removeRelationship(newStatus: String = "Single") {
    viewModelScope.launch {
      repository.removeOrResetRelationship(_currentUserId.value, newStatus)
      showToast("Relationship status updated")
    }
  }

  // --- Post Tagging Actions ---
  fun getTaggedPostsByUser(userId: String): Flow<List<Post>> = repository.getTaggedPostsByUser(userId)

  fun removePostTag(postId: String) {
    viewModelScope.launch {
      repository.removePostTag(postId, _currentUserId.value)
      showToast("You were removed from this post")
    }
  }

  fun markNotificationRead(id: String) {
    viewModelScope.launch { repository.markNotificationRead(id) }
  }

  fun markAllNotificationsRead() {
    viewModelScope.launch {
      repository.markAllNotificationsRead()
      showToast("All notifications marked as read")
    }
  }

  fun togglePageLike(pageId: String) {
    viewModelScope.launch { repository.togglePageLike(pageId) }
  }

  fun createPage(name: String, category: String, description: String) {
    viewModelScope.launch {
      repository.createPage(name, category, description)
      showToast("Page '$name' created successfully! 🌟")
    }
  }

  fun toggleGroupJoin(groupId: String) {
    viewModelScope.launch { repository.toggleGroupJoin(groupId) }
  }

  fun createGroup(name: String, privacy: String, description: String) {
    viewModelScope.launch {
      repository.createGroup(name, privacy, description)
      showToast("Group '$name' created! 👥")
    }
  }

  fun submitReport(targetType: String, targetId: String, title: String, reason: String) {
    viewModelScope.launch {
      repository.submitReport(targetType, targetId, title, reason)
      showToast("Report submitted to moderation team. Thank you!")
    }
  }

  fun resolveReport(reportId: String) {
    viewModelScope.launch {
      repository.resolveReport(reportId)
      showToast("Report resolved by admin")
    }
  }

  fun deleteReport(reportId: String) {
    viewModelScope.launch {
      repository.deleteReport(reportId)
      showToast("Content removed by admin")
    }
  }

  // --- Profile Picture & Cover Photo Management ---

  fun uploadAndSetProfilePicture(bitmap: Bitmap) {
    val currentUserId = currentUser.value?.id ?: "user_me"
    val task: suspend () -> Unit = {
      _uploadState.value = UploadState.Validating()
      delay(120)
      _uploadState.value = UploadState.Compressing()
      delay(150)

      val result = mediaService.uploadImage(
        bitmap = bitmap,
        userId = currentUserId,
        type = MediaType.PROFILE_PICTURE,
        onProgress = { progress ->
          _uploadState.value = UploadState.Uploading(progress)
        }
      )

      result.fold(
        onSuccess = { photoUrl ->
          repository.updateProfilePicture(currentUserId, photoUrl)
          _uploadState.value = UploadState.Success(photoUrl, MediaType.PROFILE_PICTURE)
          showToast("Profile picture updated successfully! ✨")
          delay(1200)
          _uploadState.value = UploadState.Idle
        },
        onFailure = { error ->
          _uploadState.value = UploadState.Error(
            message = error.localizedMessage ?: "Failed to upload profile picture. Please check network or file size.",
            canRetry = true
          )
        }
      )
    }

    lastUploadTask = task
    viewModelScope.launch { task() }
  }

  fun uploadAndSetCoverPhoto(bitmap: Bitmap) {
    val currentUserId = currentUser.value?.id ?: "user_me"
    val task: suspend () -> Unit = {
      _uploadState.value = UploadState.Validating()
      delay(120)
      _uploadState.value = UploadState.Compressing()
      delay(150)

      val result = mediaService.uploadImage(
        bitmap = bitmap,
        userId = currentUserId,
        type = MediaType.COVER_PHOTO,
        onProgress = { progress ->
          _uploadState.value = UploadState.Uploading(progress)
        }
      )

      result.fold(
        onSuccess = { photoUrl ->
          repository.updateCoverPhoto(currentUserId, photoUrl)
          _uploadState.value = UploadState.Success(photoUrl, MediaType.COVER_PHOTO)
          showToast("Cover photo updated successfully! 🌄")
          delay(1200)
          _uploadState.value = UploadState.Idle
        },
        onFailure = { error ->
          _uploadState.value = UploadState.Error(
            message = error.localizedMessage ?: "Failed to upload cover photo. Please try again.",
            canRetry = true
          )
        }
      )
    }

    lastUploadTask = task
    viewModelScope.launch { task() }
  }

  fun removeProfilePicture(userId: String) {
    viewModelScope.launch {
      repository.removeProfilePicture(userId)
      showToast("Profile picture removed. Default avatar restored.")
    }
  }

  fun removeCoverPhoto(userId: String) {
    viewModelScope.launch {
      repository.removeCoverPhoto(userId)
      showToast("Cover photo removed. Default cover restored.")
    }
  }

  fun retryLastUpload() {
    val task = lastUploadTask
    if (task != null) {
      viewModelScope.launch { task() }
    }
  }

  fun dismissUploadState() {
    _uploadState.value = UploadState.Idle
  }

  fun saveBitmapToTempUri(bitmap: android.graphics.Bitmap): android.net.Uri? {
    return try {
      val file = java.io.File(getApplication<Application>().cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
      java.io.FileOutputStream(file).use { out ->
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
      }
      android.net.Uri.fromFile(file)
    } catch (e: Exception) {
      null
    }
  }
}
