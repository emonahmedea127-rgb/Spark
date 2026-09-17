@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.webgenius.spark.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.webgenius.spark.*
import com.webgenius.spark.core.*
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Composable fun SparkApp(vm: SparkViewModel) {
    SocialLifecycle(vm)
    val state = vm.state
    val snackbar = remember { SnackbarHostState() }
    val api = vm.api
    val token = if (api != null) { val session by api.session.collectAsStateWithLifecycle(); session?.accessToken.orEmpty() } else ""
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() } }
    BackHandler(state.settings || state.profile != null || state.chat != null) { if (state.settings) vm.closeSettings() else vm.back() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                api == null -> ConnectScreen(vm)
                state.resetPassword -> ResetScreen(vm)
                state.me == null -> AuthScreen(vm)
                state.settings || state.me?.deletingAt != null -> SettingsScreen(vm, token)
                state.chat != null -> ChatScreen(vm,token)
                state.profile != null -> ProfileScreen(vm, token)
                else -> Column {
                    SocialHeader(vm)
                    SocialNavigation(vm)
                    if (!state.socialReady && state.tab == Tab.FEED) SocialPending(vm)
                    when (state.tab) {
                        Tab.FEED -> FeedScreen(vm, token)
                        Tab.DISCOVER -> DiscoverScreen(vm, token)
                        Tab.CREATE -> CreateScreen(vm)
                        Tab.REQUESTS -> RequestsScreen(vm, token)
                        Tab.PROFILE -> EmptyState(Icons.Rounded.Person, "Your profile", "Open your profile to share a little about yourself.")
                        Tab.REELS -> ReelsScreen(vm,token)
                        Tab.CHATS -> ChatsScreen(vm,token)
                        Tab.ACTIVITY -> ActivityScreen(vm,token)
                    }
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    if (state.commentPost != null) CommentsSheet(vm, token)
    if(state.connectionTitle!=null) ConnectionsSheet(vm,token)
    CallSurface(vm)
}
private fun Tab.label() = when (this) { Tab.FEED -> "Home"; Tab.DISCOVER -> "Search"; Tab.CREATE -> "Post"; Tab.REQUESTS -> "Friends"; Tab.PROFILE -> "You"; Tab.REELS -> "Reels"; Tab.CHATS -> "Chats"; Tab.ACTIVITY -> "Activity" }

@Composable private fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("spark", color=MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, letterSpacing = (-1.6).sp)
    }
}
@Composable private fun Header(onRequests: () -> Unit, onRefresh: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Brand(Modifier.weight(1f))
        IconButton(onRefresh, enabled = enabled) { Icon(Icons.Rounded.Refresh, "Refresh feed") }
        IconButton(onRequests, enabled = enabled) { Icon(Icons.Rounded.PersonAdd, "Follow requests") }
    }
}
@Composable internal fun PageHeader(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        trailing?.invoke()
    }
}
@Composable private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun Field(value: String, onChange: (String) -> Unit, label: String, secret: Boolean = false,
    modifier: Modifier = Modifier, singleLine: Boolean = true) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine,
        shape = RoundedCornerShape(10.dp), visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else if (label.contains("Email")) KeyboardType.Email else KeyboardType.Text))
}
@Composable private fun FullButton(text: String, enabled: Boolean = true, click: () -> Unit) {
    Button(click, Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = enabled, shape = RoundedCornerShape(8.dp)) { Text(text, fontWeight = FontWeight.Bold) }
}
@Composable private fun ConnectScreen(vm: SparkViewModel) {
    var url by rememberSaveable { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Spacer(Modifier.height(26.dp)); Brand(); Spacer(Modifier.height(35.dp))
        Text("A little spark.\nA real connection.", style = MaterialTheme.typography.headlineLarge)
        Text("Choose which Spark project to use on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FullButton("Use spark 2", !vm.state.busy) { vm.connect(BundledProject.URL, BundledProject.PUBLISHABLE_KEY) }
        Text("Or connect another configured project", style = MaterialTheme.typography.titleSmall)
        Field(url, { url = it }, "Project URL")
        Field(key, { key = it }, "Publishable key", secret = true)
        Text("Use the sb_publishable_ key. Never paste a service-role key, secret key or database password.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FullButton("Connect Spark", url.isNotBlank() && key.isNotBlank()) { vm.connect(url, key) }
        Text("SPARK  /  ANDROID PREVIEW 0.3", fontSize = 11.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun AuthScreen(vm: SparkViewModel) {
    var signup by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ageAccepted by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Spacer(Modifier.height(24.dp)); Brand()
        Box(Modifier.fillMaxWidth().height(125.dp).clip(RoundedCornerShape(26.dp)).background(
            Brush.linearGradient(listOf(Color(0xFFE7F0FF), Color(0xFFD6E6FF), Color(0xFFF0F7FF)))) , contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.People, null, Modifier.size(80.dp), tint = SparkOrange)
        }
        Text(if (signup) "Join your people." else "Connect with your people.", style = MaterialTheme.typography.headlineLarge)
        Text(if (signup) "Start with a private account. You choose who sees your posts." else "Sign in to catch up and share what matters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Field(email, { email = it }, "Email address")
        Field(password, { password = it }, if (signup) "Password · at least 12 characters" else "Password", true)
        if (signup) Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(ageAccepted, { ageAccepted = it })
            Text("I am at least 13 and agree to share respectfully. No bullying, spam or unsafe content.", style = MaterialTheme.typography.bodySmall)
        }
        FullButton(if (signup) "Create account" else "Sign in", !vm.state.busy && email.isNotBlank() && password.isNotBlank() && (!signup || ageAccepted)) { vm.login(email, password, signup) }
        if (!signup) TextButton({ vm.forgot(email) }, enabled = !vm.state.busy) { Text("Forgot your password?") }
        TextButton({ signup = !signup; password = "" }, enabled = !vm.state.busy) { Text(if (signup) "Already have an account? Sign in" else "New here? Create an account") }
        TextButton(vm::disconnect, enabled = !vm.state.busy) { Text("Change backend connection", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun ResetScreen(vm: SparkViewModel) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    Column(Modifier.padding(26.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Brand(); Text("A fresh start.", style = MaterialTheme.typography.headlineLarge)
        Text("Choose a new password with at least 12 characters.")
        Field(password, { password = it }, "New password", true)
        Field(repeat, { repeat = it }, "Repeat password", true)
        FullButton("Update password", !vm.state.busy && password == repeat && password.length >= 12) { vm.changePassword(password) }
        TextButton(vm::logout, enabled = !vm.state.busy) { Text("Cancel and sign out") }
    }
}
@Composable private fun InfoCard(title: String, body: String, icon: ImageVector) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable internal fun EmptyState(icon: ImageVector, title: String, body: String, action: String? = null, click: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(25.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (action != null) Button(click) { Text(action) }
    }
}
@Composable internal fun Avatar(profile: Profile, api: SparkApi?, token: String, size: Int = 43, click: (() -> Unit)? = null) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
        .then(if (click != null) Modifier.clickable(onClick = click) else Modifier), contentAlignment = Alignment.Center) {
        Text(profile.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = (size / 3).sp)
        profile.avatarPath?.let { path -> if (api != null) PrivatePhoto(api, token, "spark-avatars", path, "Profile photo of ${profile.username}", Modifier.fillMaxSize()) }
    }
}
@Composable internal fun PrivatePhoto(api: SparkApi, token: String, bucket: String, path: String, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val url = runCatching { api.mediaUrl(bucket, path) }.getOrNull()
    if (url != null && token.isNotBlank()) AsyncImage(
        model = ImageRequest.Builder(context).data(url).addHeader("Authorization", "Bearer $token")
            .addHeader("apikey", api.config.publishableKey).diskCachePolicy(CachePolicy.DISABLED)
            .memoryCachePolicy(CachePolicy.DISABLED).networkCachePolicy(CachePolicy.DISABLED).crossfade(true).build(),
        contentDescription = description, modifier = modifier, contentScale = ContentScale.Crop)
}
private fun Post.profile() = Profile(authorId, username, displayName, avatarPath = avatarPath)
private fun timeAgo(timestamp: String): String = runCatching {
    val minutes = ChronoUnit.MINUTES.between(Instant.parse(timestamp), Instant.now()).coerceAtLeast(0)
    when { minutes < 1 -> "Just now"; minutes < 60 -> "${minutes}m"; minutes < 1440 -> "${minutes / 60}h"; else -> "${minutes / 1440}d" }
}.getOrDefault("")

@Composable private fun FeedScreen(vm: SparkViewModel, token: String) {
    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { FeedComposer(vm,token) }
        if (vm.state.feed.isEmpty()) item {
            EmptyState(Icons.Rounded.AutoAwesome, "Make the first spark", "Your feed is ready. Share a photo or discover people to follow.", "Share a moment") { vm.tab(Tab.CREATE) }
        }
        items(vm.state.feed, key = { it.id }) { PostCard(it, vm, token) }
        if (vm.state.more && vm.state.feed.isNotEmpty()) item { TextButton(vm::moreFeed, Modifier.fillMaxWidth(), enabled = !vm.state.busy) { Text("Load more moments") } }
    }
}
@Composable internal fun PostCard(post: Post, vm: SparkViewModel, token: String) {
    var menu by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Avatar(post.profile(), vm.api, token) { vm.openProfile(post.profile()) }
                Column(Modifier.weight(1f).clickable { vm.openProfile(post.profile()) }) {
                    Text(post.displayName, fontWeight = FontWeight.SemiBold)
                    Text(timeAgo(post.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton({ menu = true }, enabled = !vm.state.busy) { Icon(Icons.Rounded.MoreHoriz, "Post options") }
                    DropdownMenu(menu, { menu = false }) {
                        if (post.authorId == vm.state.me?.id) DropdownMenuItem(text = { Text("Delete post") }, onClick = { menu = false; delete = true })
                        else DropdownMenuItem(text = { Text("Report post") }, onClick = { menu = false; report = true })
                    }
                }
            }
            if(post.caption.isNotBlank()) Text(post.caption,Modifier.padding(horizontal=16.dp,vertical=8.dp))
            if(post.mediaKind=="video") VideoPlayer(vm,token,"spark-videos",post.imagePath,Modifier.fillMaxWidth().aspectRatio(0.75f))
            else Box(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Image, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                vm.api?.let { PrivatePhoto(it, token, "spark-media", post.imagePath, post.caption.ifBlank { "Photo by ${post.username}" }, Modifier.fillMaxSize()) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton({vm.like(post)},Modifier.weight(1f),enabled=!vm.state.busy) { Icon(Icons.Rounded.ThumbUp,null,Modifier.size(18.dp));Text(" ${post.likeCount}") }
                TextButton({vm.openComments(post)},Modifier.weight(1f),enabled=!vm.state.busy) { Icon(Icons.Rounded.ChatBubbleOutline,null,Modifier.size(18.dp));Text(" ${post.commentCount}") }
                TextButton({vm.share(post)},Modifier.weight(1f),enabled=!vm.state.busy) { Icon(Icons.Rounded.Share,null,Modifier.size(18.dp));Text(" Share") }
                IconButton({ vm.save(post) }, enabled = !vm.state.busy) { Icon(if (post.saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (post.saved) "Unsave post" else "Save post") }
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${post.likeCount} ${if (post.likeCount == 1) "like" else "likes"}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                TextButton({ vm.openComments(post) }, contentPadding = PaddingValues(0.dp), enabled = !vm.state.busy) { Text(if (post.commentCount == 0) "Start a conversation" else "View ${post.commentCount} comments", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if (delete) ConfirmDialog("Delete this post?", "It will disappear from Spark. The original photo on your phone is unchanged.", "Delete", { delete = false }) { delete = false; vm.remove(post) }
    if (report) AlertDialog(onDismissRequest = { report = false }, title = { Text("Report this post") }, text = {
        Column { listOf("spam" to "Spam", "harassment" to "Bullying or harassment", "unsafe_content" to "Unsafe content", "other" to "Something else").forEach { (reason, label) ->
            TextButton({ report = false; vm.report(post, reason) }) { Text(label) }
        } }
    }, confirmButton = {}, dismissButton = { TextButton({ report = false }) { Text("Cancel") } })
}
@Composable private fun DiscoverScreen(vm: SparkViewModel, token: String) {
    var query by rememberSaveable { mutableStateOf("") }
    Column {
        SectionTitle("Find your people.", "Search by username, then say hello with a follow.")
        OutlinedTextField(query, { query = it; vm.search(it) }, Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            placeholder = { Text("Search a username") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(18.dp))
        LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (vm.state.people.isEmpty()) item { EmptyState(Icons.Rounded.PeopleOutline, if (query.isBlank()) "A connection starts here" else "No matching accounts", "Try a username prefix. Private accounts keep their posts behind follow approval.") }
            items(vm.state.people, key = { it.id }) { profile -> PersonRow(profile, vm, token) }
        }
    }
}
@Composable internal fun PersonRow(profile: Profile, vm: SparkViewModel, token: String, trailing: (@Composable () -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().clickable(enabled = !vm.state.busy) { vm.openProfile(profile) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(profile, vm.api, token, 48)
            Column(Modifier.weight(1f)) { Text(profile.username, fontWeight = FontWeight.SemiBold); Text(profile.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (trailing == null) Icon(Icons.Rounded.ChevronRight, "View profile") else trailing()
        }
    }
}
@Composable private fun CreateScreen(vm: SparkViewModel) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var caption by rememberSaveable { mutableStateOf("") }
    var video by rememberSaveable { mutableStateOf(false) }
    val videoPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { selected=it.toString();video=true } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { selected = it.toString() } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) selected = cameraUri }
    val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) cameraUri?.let {camera.launch(Uri.parse(it))} else vm.showMessage("Camera permission is needed to take a photo. You can still choose one from Photos.")
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Create a post", style = MaterialTheme.typography.headlineLarge)
        Text("Share a photo or a short video with your people.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
            if (selected != null && !video) AsyncImage(Uri.parse(selected), "Selected photo preview", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else if(selected!=null) Text("Video selected · up to 60 seconds / 20 MB")
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Choose your moment")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ video=false;picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), enabled = !vm.state.busy) { Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Photos") }
            OutlinedButton({
                video=false
                try {
                    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
                    // Only temporary capture files created by Spark, never gallery media.
                    directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
                    val file = File(directory, "${UUID.randomUUID()}.jpg").apply { createNewFile() }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    cameraUri = uri.toString()
                    if(androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.CAMERA)==android.content.pm.PackageManager.PERMISSION_GRANTED) camera.launch(uri)
                    else cameraPermission.launch(android.Manifest.permission.CAMERA)
                } catch (_: Exception) { vm.showMessage("No camera app is available. Please choose a photo instead.") }
            }, Modifier.weight(1f), enabled = !vm.state.busy) { Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Camera") }
        }
        OutlinedButton({videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))},Modifier.fillMaxWidth(),enabled=!vm.state.busy) { Icon(Icons.Rounded.VideoLibrary,null);Text("  Choose video / Reel") }
        Field(caption, { if (it.length <= 2200) caption = it }, "Write a caption", singleLine = false)
        Text("${caption.length}/2200", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        InfoCard(if (vm.state.me?.isPrivate == true) "Only approved followers" else "Visible to people on Spark", "Photos are resized and location metadata is removed before upload.", Icons.Rounded.Shield)
        FullButton(if (vm.state.busy) "Posting…" else "Post", !vm.state.busy && selected != null) { selected?.let { if(video) vm.publishVideo(Uri.parse(it),caption) else vm.publish(Uri.parse(it), caption) } }
    }
}
@Composable private fun RequestsScreen(vm: SparkViewModel, token: String) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Friends", "Accept a request to become friends and start chatting.") }
        item { FriendRequests(vm,token) }
        item { Text("Follow requests",style=MaterialTheme.typography.titleLarge) }
        item { TextButton(vm::loadRequests, enabled = !vm.state.busy) { Icon(Icons.Rounded.Refresh, null); Text(" Refresh requests") } }
        if (vm.state.requests.isEmpty()) item { EmptyState(Icons.Rounded.PersonAdd, "All caught up", "New follow requests will appear here. Refresh to check for updates.") }
        items(vm.state.requests, key = { it.id }) { profile ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PersonRow(profile, vm, token)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ vm.resolve(profile, false) }, enabled = !vm.state.busy) { Text("Decline") }
                    Button({ vm.resolve(profile, true) }, enabled = !vm.state.busy) { Text("Accept") }
                }
            }
        }
    }
}
@Composable private fun ProfileScreen(vm: SparkViewModel, token: String) {
    val profile = vm.state.profile ?: return
    val own = profile.id == vm.state.me?.id
    var block by remember { mutableStateOf(false) }
    Column {
        PageHeader(profile.username, vm::back) { if (own) IconButton(vm::settings, enabled = !vm.state.busy) { Icon(Icons.Rounded.Settings, "Settings and edit profile") } }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 22.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.linearGradient(listOf(Color(0xFF0866FF),Color(0xFF8CC6FF)))))
                Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Avatar(profile, vm.api, token, 88)
                    Text(profile.displayName, style = MaterialTheme.typography.headlineMedium)
                    ProfileSocial(vm,token,profile,own)
                    if (profile.bio.isNotBlank()) Text(profile.bio)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(if (profile.isPrivate) Icons.Rounded.Lock else Icons.Rounded.Public, null, Modifier.size(16.dp))
                        Text(if (profile.isPrivate) "Private account" else "Public on Spark", style = MaterialTheme.typography.bodySmall)
                    }
                    if (own) OutlinedButton(vm::settings, Modifier.fillMaxWidth(), enabled = !vm.state.busy) { Text("Edit profile & settings") }
                    else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val followLabel = when (vm.state.relationship?.status) {
                            "accepted" -> "Unfollow"
                            "pending" -> "Cancel request"
                            else -> { if (profile.isPrivate) "Request to follow" else "Follow" }
                        }
                        Button(vm::follow, Modifier.weight(1f), enabled = !vm.state.busy) { Text(followLabel) }
                        OutlinedButton({ block = true }, enabled = !vm.state.busy) { Text("Block") }
                    }
                    HorizontalDivider()
                    if (own) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(!vm.state.savedOnly, { vm.saved(false) }, label = { Text("Your posts") }, enabled = !vm.state.busy)
                        FilterChip(vm.state.savedOnly, { vm.saved(true) }, label = { Text("Saved · only you") }, enabled = !vm.state.busy)
                    } else Text("Moments", style = MaterialTheme.typography.titleLarge)
                }
            }
            if (vm.state.profilePosts.isEmpty()) item { EmptyState(
                if (!own && profile.isPrivate) Icons.Rounded.Lock else Icons.Rounded.GridView,
                if (!own && profile.isPrivate && vm.state.relationship?.status != "accepted") "A private collection" else "No moments yet",
                if (!own && profile.isPrivate && vm.state.relationship?.status != "accepted") "Send a follow request. Their posts appear after approval." else "Photos will appear here when they are shared or saved.") }
            items(vm.state.profilePosts, key = { it.id }) { PostCard(it, vm, token) }
            if (vm.state.profileMore && vm.state.profilePosts.isNotEmpty()) item { TextButton(vm::moreProfile, Modifier.fillMaxWidth(), enabled = !vm.state.busy) { Text("Load more") } }
        }
    }
    if (block) ConfirmDialog("Block ${profile.username}?", "You will stop seeing each other on Spark. Existing follow connections will be removed.", "Block", { block = false }) { block = false; vm.block(profile) }
}
@Composable private fun CommentsSheet(vm: SparkViewModel, token: String) {
    var body by remember { mutableStateOf("") }
    val post = vm.state.commentPost ?: return
    ModalBottomSheet(onDismissRequest = vm::closeComments) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).imePadding().padding(horizontal = 20.dp)) {
            Text("The conversation", style = MaterialTheme.typography.titleLarge)
            Text("Keep it kind. Showing the latest 100 comments.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f).padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (vm.state.comments.isEmpty()) item { Text("Be the first to leave a comment.") }
                items(vm.state.comments, key = { it.id }) { comment ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(Profile(comment.authorId, comment.username, comment.displayName, avatarPath = comment.avatarPath), vm.api, token, 34)
                        Column(Modifier.weight(1f)) { Text(comment.username, fontWeight = FontWeight.Bold); Text(comment.body); Text(timeAgo(comment.createdAt), style = MaterialTheme.typography.bodySmall) }
                        if (comment.authorId == vm.state.me?.id || post.authorId == vm.state.me?.id) IconButton({ vm.removeComment(comment) }, enabled = !vm.state.busy) { Icon(Icons.Rounded.Close, "Delete comment") }
                    }
                }
            }
            Row(Modifier.padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(body, { if (it.length <= 1000) body = it }, Modifier.weight(1f), placeholder = { Text("Add a comment…") }, shape = RoundedCornerShape(18.dp), maxLines = 4)
                IconButton({ vm.comment(body) { body = "" } }, enabled = !vm.state.busy && body.isNotBlank()) { Icon(Icons.AutoMirrored.Rounded.Send, "Send comment", tint = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
@Composable private fun SettingsScreen(vm: SparkViewModel, token: String) {
    val me = vm.state.me ?: return
    var name by rememberSaveable(me.id) { mutableStateOf(me.displayName) }
    var username by rememberSaveable(me.id) { mutableStateOf(me.username) }
    var bio by rememberSaveable(me.id) { mutableStateOf(me.bio) }
    var privateAccount by rememberSaveable(me.id) { mutableStateOf(me.isPrivate) }
    var avatar by rememberSaveable { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { avatar = it?.toString() }
    Column {
        PageHeader("Make it yours", vm::closeSettings)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (me.deletingAt == null) {
                PrivacyControls(vm)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (avatar == null) Avatar(me, vm.api, token, 72)
                    else AsyncImage(Uri.parse(avatar), "New avatar preview", Modifier.size(72.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !vm.state.busy) { Text("Change photo") }
                }
                Field(name, { name = it.take(60) }, "Display name")
                Field(username, { username = it.take(24) }, "Username")
                Field(bio, { bio = it.take(160) }, "Bio", singleLine = false)
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) { Text("Private account", fontWeight = FontWeight.Bold); Text(if (privateAccount) "Only approved followers see your photos." else "Everyone signed into Spark can see your photos.", style = MaterialTheme.typography.bodySmall) }
                        Switch(privateAccount, { privateAccount = it }, enabled = !vm.state.busy)
                    }
                }
                FullButton("Save profile", !vm.state.busy) { vm.updateProfile(name, username, bio, privateAccount, avatar?.let(Uri::parse)) }
                HorizontalDivider()
                Text("Blocked accounts", style = MaterialTheme.typography.titleLarge)
                if (vm.state.blocked.isEmpty()) Text("You have not blocked anyone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                vm.state.blocked.forEach { id -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Account ${id.take(8)}", Modifier.weight(1f))
                    TextButton({ vm.unblock(id) }, enabled = !vm.state.busy) { Text("Unblock") }
                } }
            } else InfoCard("Deletion pending", "Your content is hidden. Retry deletion below to finish removing stored media and your account.", Icons.Rounded.Shield)
            HorizontalDivider()
            OutlinedButton(vm::logout, Modifier.fillMaxWidth(), enabled = !vm.state.busy) { Icon(Icons.AutoMirrored.Rounded.ExitToApp, null); Text("  Sign out") }
            TextButton({ delete = true }, Modifier.fillMaxWidth(), enabled = !vm.state.busy) { Text(if (me.deletingAt == null) "Delete account & data" else "Retry account deletion", color = MaterialTheme.colorScheme.error) }
            Text("Spark 2 · 0.3.0\nCalls work while both people have Spark open. Video uploads: MP4, 60 seconds, 20 MB maximum.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (delete) AlertDialog(onDismissRequest = { if (!vm.state.busy) { delete = false; password = "" } }, title = { Text("Permanently delete your account?") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("This removes your account, posts, photos, comments and follow connections. Enter your current password to confirm. This cannot be undone.")
            Field(password, { password = it }, "Current password", true)
        }
    }, confirmButton = { TextButton({ vm.deleteAccount(password) }, enabled = !vm.state.busy && password.isNotBlank()) { Text("Delete permanently", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ delete = false; password = "" }, enabled = !vm.state.busy) { Text("Keep account") } })
}
@Composable private fun ConfirmDialog(title: String, body: String, action: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(body) }, confirmButton = { TextButton(confirm) { Text(action) } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}
