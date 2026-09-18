@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.webgenius.spark.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.VideoView
import android.widget.MediaController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.webgenius.spark.*
import com.webgenius.spark.core.*
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable internal fun SocialPending(vm: SparkViewModel) {
 Surface(color=MaterialTheme.colorScheme.secondaryContainer) {
  Column(Modifier.fillMaxWidth().padding(16.dp)) {
   Text("Social features unavailable",fontWeight=FontWeight.Bold)
   Text(vm.state.socialStatus,style=MaterialTheme.typography.bodySmall)
   TextButton(vm::refreshSocial,enabled=!vm.state.busy) {Text("Retry")}
  }
 }
}

@Composable internal fun SocialLifecycle(vm: SparkViewModel) {
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle,vm) {
  val observer=LifecycleEventObserver { _,event ->
   if(event==Lifecycle.Event.ON_START) vm.setForeground(true)
   if(event==Lifecycle.Event.ON_STOP) vm.setForeground(false)
  }
  lifecycle.addObserver(observer)
  vm.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
  onDispose { lifecycle.removeObserver(observer);vm.setForeground(false) }
 }
}
@Composable internal fun SocialHeader(vm: SparkViewModel) {
 Surface { Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
  Text("spark",Modifier.weight(1f),fontSize=35.sp,fontWeight=FontWeight.ExtraBold,color=MaterialTheme.colorScheme.primary,letterSpacing=(-1.5).sp)
  IconButton({vm.tab(Tab.CREATE)},enabled=!vm.state.busy) { Icon(Icons.Rounded.AddCircle,"Create post") }
  IconButton({vm.tab(Tab.DISCOVER)},enabled=!vm.state.busy) { Icon(Icons.Rounded.Search,"Search people") }
  IconButton({vm.tab(Tab.CHATS)},enabled=!vm.state.busy) { Icon(Icons.Rounded.ChatBubble,"Open chats") }
 } }
}
@Composable internal fun SocialNavigation(vm: SparkViewModel) {
 val tabs=listOf(Triple(Tab.FEED,Icons.Rounded.Home,"Home"),Triple(Tab.REQUESTS,Icons.Rounded.People,"Friends"),
  Triple(Tab.REELS,Icons.Rounded.SmartDisplay,"Reels"),Triple(Tab.ACTIVITY,Icons.Rounded.Notifications,"Activity"),Triple(Tab.PROFILE,Icons.Rounded.AccountCircle,"Profile"))
 Surface { Row(Modifier.fillMaxWidth()) { tabs.forEach { (tab,icon,label) ->
  val selected=vm.state.tab==tab
  Column(Modifier.weight(1f).clickable(enabled=!vm.state.busy) {vm.tab(tab)},horizontalAlignment=Alignment.CenterHorizontally) {
   Box(Modifier.height(48.dp),contentAlignment=Alignment.Center) {
    BadgedBox(badge={if(tab==Tab.ACTIVITY && vm.state.socialNotice>0) Badge {Text(vm.state.socialNotice.coerceAtMost(99).toString())}}) {
     Icon(icon,label,tint=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
   }
   HorizontalDivider(thickness=3.dp,color=if(selected) MaterialTheme.colorScheme.primary else Color.Transparent)
  }
 } } }
}
@Composable internal fun FeedComposer(vm: SparkViewModel,token: String) {
 Surface { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
   vm.state.me?.let { Avatar(it,vm.api,token) {vm.openProfile(it)} }
   Surface(Modifier.weight(1f).clickable {vm.tab(Tab.CREATE)},shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.background) {
    Text("What's on your mind?",Modifier.padding(14.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
  }
  HorizontalDivider()
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
   TextButton({vm.tab(Tab.CREATE)}) {Icon(Icons.Rounded.PhotoLibrary,null,tint=Color(0xFF31A24C));Text(" Photo / video")}
   TextButton({vm.tab(Tab.REELS)}) {Icon(Icons.Rounded.SmartDisplay,null,tint=Color(0xFFE74970));Text(" Reels")}
   IconButton(vm::refresh,enabled=!vm.state.busy) {Icon(Icons.Rounded.Refresh,"Refresh feed")}
  }
 } }
}
@Composable internal fun FriendRequests(vm: SparkViewModel,token: String) {
 if(!vm.state.socialReady) {SocialPending(vm);return}
 val me=vm.state.me?.id.orEmpty()
 val pending=vm.state.friendships.filter {it.addresseeId==me && it.status=="pending"}
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
  if(pending.isEmpty()) Text("No new friend requests",color=MaterialTheme.colorScheme.onSurfaceVariant)
  pending.forEach {edge -> vm.state.friendPeople.firstOrNull {it.id==edge.requesterId}?.let {p ->
   PersonRow(p,vm,token)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    Button({vm.friend(p,true)},enabled=!vm.state.busy) {Text("Confirm")}
    OutlinedButton({vm.friend(p,false)},enabled=!vm.state.busy) {Text("Delete request")}
   }
  } }
  Text("Your friends",style=MaterialTheme.typography.titleLarge)
  vm.state.friendships.filter {it.status=="accepted"}.forEach {edge ->
   vm.state.friendPeople.firstOrNull {it.id==edge.other(me)}?.let {p -> PersonRow(p,vm,token) {
    IconButton({vm.openChat(p)},enabled=!vm.state.busy) {Icon(Icons.Rounded.ChatBubbleOutline,"Message ${p.displayName}")}
   } }
  }
  OutlinedButton({vm.tab(Tab.DISCOVER)},Modifier.fillMaxWidth()) {Text("Find friends")}
 }
}
@Composable internal fun ProfileSocial(vm: SparkViewModel,token: String,profile: Profile,own: Boolean) {
 if(!vm.state.socialReady) {SocialPending(vm);return}
 val totals=vm.state.totals
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
  Text("${totals.friends} friends",Modifier.weight(1f).padding(vertical=14.dp),fontWeight=FontWeight.Bold)
  TextButton({vm.connectionList(true)},Modifier.weight(1f)) {Text("${totals.followers} followers")}
  TextButton({vm.connectionList(false)},Modifier.weight(1f)) {Text("${totals.following} following")}
 }
 if(!own) {
  val edge=vm.friendshipWith(profile.id)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   val incoming=edge?.status=="pending" && edge.addresseeId==vm.state.me?.id
   Button({vm.friend(profile,incoming)},Modifier.weight(1f),enabled=!vm.state.busy) {
    Icon(Icons.Rounded.PersonAdd,null,Modifier.size(18.dp));Text(" "+when {edge==null->"Add friend";incoming->"Accept request";edge.status=="accepted"->"Unfriend";else->"Cancel request"})
   }
   if(edge?.status=="accepted") OutlinedButton({vm.openChat(profile)},enabled=!vm.state.busy) {Icon(Icons.Rounded.ChatBubbleOutline,null);Text(" Message")}
  }
 } else TextButton({vm.tab(Tab.ACTIVITY)}) {Icon(Icons.Rounded.Visibility,null);Text("  Profile visits & activity")}
}
@Composable internal fun ConnectionsSheet(vm: SparkViewModel,token: String) {
 ModalBottomSheet(onDismissRequest=vm::closeConnections) {
  LazyColumn(Modifier.fillMaxHeight(0.7f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
   item {Text(vm.state.connectionTitle.orEmpty(),style=MaterialTheme.typography.headlineMedium)}
   if(vm.state.connectionPeople.isEmpty()) item {Text("No visible connections yet")}
   items(vm.state.connectionPeople,key={it.id}) {p -> PersonRow(p,vm,token) {IconButton({vm.closeConnections();vm.openProfile(p)}) {Icon(Icons.Rounded.ChevronRight,"Open profile")}}}
  }
 }
}
@Composable internal fun PrivacyControls(vm: SparkViewModel) {
 if(!vm.state.socialReady) {SocialPending(vm);return}
 val prefs=vm.state.preferences
 Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Text("Privacy & visits",style=MaterialTheme.typography.titleLarge)
  Row(verticalAlignment=Alignment.CenterVertically) {
   Column(Modifier.weight(1f)) {Text("Ghost mode",fontWeight=FontWeight.Bold);Text("Hide future profile visits from other people's visitor lists. Previous visible visits remain for up to 30 days.",style=MaterialTheme.typography.bodySmall)}
   Switch(prefs.ghostMode,{vm.privacy(it,prefs.visitNotifications)},enabled=!vm.state.busy)
  }
  Row(verticalAlignment=Alignment.CenterVertically) {
   Column(Modifier.weight(1f)) {Text("Profile visit activity",fontWeight=FontWeight.Bold);Text("Show visitors who chose to make their visits visible. Ghost visitors are never listed.",style=MaterialTheme.typography.bodySmall)}
   Switch(prefs.visitNotifications,{vm.privacy(prefs.ghostMode,it)},enabled=!vm.state.busy)
  }
 }
}
@Composable internal fun ActivityScreen(vm: SparkViewModel,token: String) {
 if(!vm.state.socialReady) {SocialPending(vm);return}
 LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  item {Text("Activity",style=MaterialTheme.typography.headlineLarge)}
  item {PrivacyControls(vm)}
  item {TextButton(vm::refreshSocial) {Icon(Icons.Rounded.Refresh,null);Text(" Refresh activity")}}
  item {Text("Profile visitors · last 30 days",style=MaterialTheme.typography.titleLarge)}
  if(vm.state.visits.isEmpty()) item {EmptyState(Icons.Rounded.Visibility,"No visible visits yet","People browsing in Ghost mode won't appear here.")}
  items(vm.state.visits,key={it.visitorId}) {v ->
   Surface(shape=RoundedCornerShape(12.dp)) {Column(Modifier.padding(12.dp)) {
    PersonRow(Profile(v.visitorId,v.username,v.displayName,avatarPath=v.avatarPath),vm,token)
    Text("Visited ${v.visitedAt.take(16).replace('T',' ')} UTC",style=MaterialTheme.typography.bodySmall)
   } }
  }
 }
}
@Composable internal fun ReelsScreen(vm: SparkViewModel,token: String) {
 if(!vm.state.socialReady) {SocialPending(vm);return}
 LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
  item {Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
   Text("Reels",Modifier.weight(1f),style=MaterialTheme.typography.headlineLarge)
   IconButton(vm::loadReels) {Icon(Icons.Rounded.Refresh,"Refresh Reels")}
   IconButton({vm.tab(Tab.CREATE)}) {Icon(Icons.Rounded.Add,"Create Reel")}
  } }
  if(vm.state.reels.isEmpty()) item {EmptyState(Icons.Rounded.SmartDisplay,"Your next moment starts here","Share a short video or follow people to see their Reels.","Create Reel") {vm.tab(Tab.CREATE)}}
  items(vm.state.reels,key={it.id}) {PostCard(it,vm,token)}
  if(vm.state.reelsMore && vm.state.reels.isNotEmpty()) item {TextButton(vm::moreReels,Modifier.fillMaxWidth()) {Text("More Reels")}}
 }
}
@Composable internal fun VideoPlayer(vm: SparkViewModel,token: String,bucket: String,path: String,modifier: Modifier=Modifier) {
 var play by remember(path) {mutableStateOf(false)}
 var player by remember {mutableStateOf<VideoView?>(null)}
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 DisposableEffect(path,lifecycle) {
  val observer=LifecycleEventObserver {_,e->if(e==Lifecycle.Event.ON_STOP) {player?.stopPlayback();play=false}}
  lifecycle.addObserver(observer)
  onDispose {lifecycle.removeObserver(observer);player?.stopPlayback();player=null}
 }
 Box(modifier.background(Color.Black),contentAlignment=Alignment.Center) {
  if(play && vm.api!=null && token.isNotBlank()) AndroidView(factory={context->
   VideoView(context).also {v->
    player=v
    val controller=MediaController(context);controller.setAnchorView(v);v.setMediaController(controller)
    v.setOnErrorListener {_,_,_->play=false;vm.showMessage("Video could not play. Refresh and try again.");true}
    v.setVideoURI(Uri.parse(vm.api!!.mediaUrl(bucket,path)),mapOf("Authorization" to "Bearer $token","apikey" to vm.api!!.config.publishableKey))
    v.setOnPreparedListener {it.isLooping=bucket=="spark-videos";v.start()}
   }
  },modifier=Modifier.fillMaxSize())
  else Button({play=true}) {Icon(Icons.Rounded.PlayArrow,null);Text(" Tap to play")}
 }
}
@Composable internal fun ChatsScreen(vm: SparkViewModel,token: String) {
 if(!vm.state.socialReady) {SocialPending(vm);return}
 LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  item {Text(if(vm.state.sharedPost!=null) "Share with a friend" else "Chats",style=MaterialTheme.typography.headlineLarge)}
  item {Text("Private conversations with your friends",color=MaterialTheme.colorScheme.onSurfaceVariant)}
  if(vm.state.sharedPost!=null) item {TextButton(vm::cancelShare) {Text("Cancel sharing")}}
  val friends=vm.state.friendships.filter {it.status=="accepted"}.map {it.other(vm.state.me?.id.orEmpty())}.toSet()
  if(friends.isEmpty()) item {EmptyState(Icons.Rounded.ChatBubbleOutline,"Start a conversation","Add a friend and accept the request to exchange messages, photos and videos.","Find friends") {vm.tab(Tab.DISCOVER)}}
  items(vm.state.friendPeople.filter {it.id in friends},key={it.id}) {p->
   Surface(shape=RoundedCornerShape(12.dp)) {Row(Modifier.fillMaxWidth().clickable {vm.openChat(p)}.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
    Avatar(p,vm.api,token,50)
    Column(Modifier.weight(1f)) {Text(p.displayName,fontWeight=FontWeight.Bold);Text("@${p.username}",color=MaterialTheme.colorScheme.onSurfaceVariant)}
    Icon(Icons.Rounded.ChatBubbleOutline,"Open conversation")
   } }
  }
 }
}
@Composable internal fun ChatScreen(vm: SparkViewModel,token: String) {
 var body by rememberSaveable(vm.state.chat?.id) {mutableStateOf("")}
 var attachment by remember {mutableStateOf<Uri?>(null)}
 var video by remember {mutableStateOf(false)}
 val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {if(it!=null) {attachment=it;video=false}}
 val videoPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {if(it!=null) {attachment=it;video=true}}
 val scroll=rememberLazyListState()
 val messages=vm.state.messages
 LaunchedEffect(messages.lastOrNull()?.id) {if(messages.isNotEmpty()) scroll.animateScrollToItem(messages.size)}
 Column(Modifier.fillMaxSize().imePadding()) {
  Surface {PageHeader(vm.state.chatPeer?.displayName ?: "Chat",vm::back) {CallButtons(vm)}}
  LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=scroll,contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   item {if(vm.state.chatMore) TextButton(vm::olderMessages) {Text("Load earlier messages")}}
   items(messages,key={it.id}) {m->
    val own=m.senderId==vm.state.me?.id
    Row(Modifier.fillMaxWidth(),horizontalArrangement=if(own) Arrangement.End else Arrangement.Start) {
     Surface(Modifier.widthIn(max=290.dp),shape=RoundedCornerShape(18.dp),color=if(own) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
      Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
       if(m.body.isNotBlank()) Text(m.body)
       m.mediaPath?.let { path ->
        if(m.mediaKind=="video") VideoPlayer(vm,token,"spark-chat",path,Modifier.fillMaxWidth().height(230.dp))
        else vm.api?.let {PrivatePhoto(it,token,"spark-chat",path,"Shared photo",Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(8.dp)))}
       }
       m.postId?.let { id -> OutlinedButton({vm.openSharedPost(id)}) {Icon(Icons.Rounded.OpenInNew,null);Text(" View shared post")} }
       Text(m.createdAt.take(16).replace('T',' ')+" UTC",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      }
     }
    }
   }
  }
  if(attachment!=null || vm.state.sharedPost!=null) Row(Modifier.padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
   Text(if(vm.state.sharedPost!=null) "Post ready to share" else if(video) "Video attached" else "Photo attached",Modifier.weight(1f))
   IconButton({attachment=null;vm.cancelShare()}) {Icon(Icons.Rounded.Close,"Remove attachment")}
  }
  Surface {Column(Modifier.padding(horizontal=8.dp,vertical=6.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically) {
    IconButton({photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}) {Icon(Icons.Rounded.PhotoLibrary,"Attach photo")}
    IconButton({videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))}) {Icon(Icons.Rounded.VideoLibrary,"Attach video")}
    OutlinedTextField(body,{body=it.take(2000)},Modifier.weight(1f),placeholder={Text("Message")},shape=RoundedCornerShape(24.dp),maxLines=4)
    IconButton({vm.send(body,attachment,video) {body="";attachment=null}},enabled=!vm.state.busy && (body.isNotBlank() || attachment!=null || vm.state.sharedPost!=null)) {Icon(Icons.Rounded.Send,"Send message")}
   }
  } }
 }
}
@Composable private fun CallButtons(vm: SparkViewModel,incoming: Boolean=false) {
 val context=LocalContext.current
 var wantsVideo by remember {mutableStateOf(false)}
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {grants->
  if(grants.values.all {it}) {if(incoming) vm.acceptCall() else vm.startCall(wantsVideo)}
  else vm.showMessage("Microphone and, for video calls, camera permission are needed.")
 }
 fun call(video: Boolean) {
  wantsVideo=video
  val permissions=if(video) arrayOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
  if(permissions.all {ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED}) {
   if(incoming) vm.acceptCall() else vm.startCall(video)
  } else launcher.launch(permissions)
 }
 if(incoming) Button({call(vm.state.incoming?.video==true)},enabled=!vm.state.busy) {Text("Accept")}
 else Row {
  IconButton({call(false)},enabled=!vm.state.busy) {Icon(Icons.Rounded.Call,"Audio call")}
  IconButton({call(true)},enabled=!vm.state.busy) {Icon(Icons.Rounded.Videocam,"Video call")}
 }
}
@Composable private fun CallVideo(engine: CallEngine,track: VideoTrack?,mirror: Boolean,modifier: Modifier) {
 val context=LocalContext.current
 val renderer=remember(engine) {SurfaceViewRenderer(context).apply {init(engine.egl.eglBaseContext,null);setMirror(mirror)}}
 DisposableEffect(track,renderer) {track?.addSink(renderer);onDispose {runCatching {track?.removeSink(renderer)}}}
 DisposableEffect(renderer) {onDispose {renderer.release()}}
 AndroidView(factory={renderer},modifier=modifier)
}
@Composable internal fun CallSurface(vm: SparkViewModel) {
 val incoming=vm.state.incoming
 if(incoming!=null && vm.state.call==null) AlertDialog(onDismissRequest=vm::declineCall,title={Text(if(incoming.video) "Incoming video call" else "Incoming audio call")},
  text={Text(vm.state.friendPeople.firstOrNull {it.id==incoming.callerId}?.displayName ?: "A friend is calling")},
  confirmButton={CallButtons(vm,true)},dismissButton={TextButton(vm::declineCall) {Text("Decline")}})
 val call=vm.state.call;val engine=vm.callEngine
 if(call!=null && engine!=null) Dialog(onDismissRequest=vm::endCall,properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false)) {
  var muted by remember {mutableStateOf(false)};var camera by remember {mutableStateOf(true)};var speaker by remember {mutableStateOf(call.video)}
  Surface(Modifier.fillMaxSize(),color=Color(0xFF111827),contentColor=Color.White) {
   Column(Modifier.fillMaxSize().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
    Text(if(call.video) "Video call" else "Audio call",style=MaterialTheme.typography.headlineMedium)
    Text(vm.state.callStatus)
    if(call.video) Box(Modifier.weight(1f).fillMaxWidth()) {
     CallVideo(engine,engine.remoteVideo,false,Modifier.fillMaxSize())
     CallVideo(engine,engine.localVideo,true,Modifier.align(Alignment.TopEnd).width(110.dp).height(150.dp))
    } else Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {Icon(Icons.Rounded.AccountCircle,null,Modifier.size(120.dp))}
    Text("Keep Spark open. Some networks require a relay that is not configured.",style=MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
     IconButton({muted=!muted;engine.mute(muted)}) {Icon(if(muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,if(muted) "Unmute" else "Mute")}
     IconButton({speaker=!speaker;engine.speaker(speaker)}) {Icon(Icons.Rounded.VolumeUp,"Toggle speaker")}
     if(call.video) IconButton({camera=!camera;engine.camera(camera)}) {Icon(if(camera) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,"Toggle camera")}
     FilledIconButton(vm::endCall,colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color(0xFFE53935),contentColor=Color.White)) {Icon(Icons.Rounded.CallEnd,"End call")}
    }
   }
  }
 }
}
