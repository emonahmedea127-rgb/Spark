@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.spark.social

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import org.json.JSONObject
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable fun SparkTopBar(vm:SparkViewModel,onCreate:()->Unit) {
    if(vm.page.name in listOf("Reels","Profile"))return
    TopAppBar(colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.surface),title={
        Text(if(vm.page.name=="Home")"spark" else vm.page.title.ifBlank { vm.page.name },fontWeight=FontWeight.Bold,
            fontSize=if(vm.page.name=="Home")34.sp else 28.sp,color=if(vm.page.name=="Home")Blue else MaterialTheme.colorScheme.onSurface)
    },navigationIcon={ IconButton(onClick={if(vm.stack.size>1)vm.back() else vm.go("Menu")}) {
        Icon(if(vm.stack.size>1)Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Menu,"Menu or back",Modifier.size(28.dp))
    } },actions={
        if(vm.page.name=="Home")IconButton(onClick=onCreate) { Icon(Icons.Outlined.AddBox,"New post",Modifier.size(27.dp)) }
        IconButton(onClick={vm.go("Search")}) { Icon(Icons.Outlined.Search,"Search",Modifier.size(29.dp)) }
        if(vm.page.name=="Home")IconButton(onClick={vm.go("Chats")}) { Icon(Icons.Outlined.Chat,"Messages",Modifier.size(27.dp)) }
    })
}

@Composable fun SparkBottomBar(vm:SparkViewModel) {
    val dark=vm.page.name=="Reels"
    val color=if(dark)Color(0xFF252525) else MaterialTheme.colorScheme.surface
    val ink=if(dark)Color.LightGray else MaterialTheme.colorScheme.onSurface
    Surface(color=color,tonalElevation=0.dp) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
            Row(Modifier.fillMaxWidth().height(56.dp)) {
                listOf("Home" to Icons.Outlined.Home,"Reels" to Icons.Outlined.SmartDisplay,"Friends" to Icons.Outlined.PeopleOutline,"Marketplace" to Icons.Outlined.Storefront,"Notifications" to Icons.Outlined.NotificationsNone,"Profile" to Icons.Outlined.AccountCircle).forEach { (name,icon) ->
                    val selected=vm.page.name==name
                    Column(Modifier.weight(1f).fillMaxHeight().clickable { vm.tab(name) },horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().padding(horizontal=4.dp).height(3.dp).background(if(selected)Blue else Color.Transparent,RoundedCornerShape(bottomStart=4.dp,bottomEnd=4.dp)))
                        Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                            if(name=="Profile")Box(Modifier.border(if(selected)2.dp else 0.dp,if(selected)Blue else Color.Transparent,CircleShape).padding(3.dp)) { Avatar(vm,vm.me!!,28) { vm.tab("Profile") } }
                            else Box{
                                Icon(if(name=="Home"&&selected)Icons.Filled.Home else icon,name,tint=if(selected)if(dark)Color.White else Blue else ink,modifier=Modifier.size(28.dp))
                                val number=when(name){"Home"->vm.newPostCount;"Notifications"->vm.unreadCount;else->0}
                                if(number>0)Surface(Modifier.align(Alignment.TopEnd).offset(x=10.dp,y=(-7).dp).defaultMinSize(minWidth=18.dp,minHeight=18.dp),shape=CircleShape,color=Color(0xFFE82E43)){
                                    Box(contentAlignment=Alignment.Center){Text(if(number>99)"99+" else number.toString(),Modifier.padding(horizontal=4.dp),color=Color.White,fontSize=10.sp,fontWeight=FontWeight.Bold)}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun UnreadBadges(vm:SparkViewModel){
    val lifecycle=LocalLifecycleOwner.current
    var known by remember(vm.api.userId){mutableStateOf<Set<String>>(emptySet())}
    var initialized by remember(vm.api.userId){mutableStateOf(false)}
    var postsInitialized by remember(vm.api.userId){mutableStateOf(false)}
    LaunchedEffect(vm.api.userId,lifecycle,vm.revision){
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            while(true){
                try{
                    val notices=vm.api.rows("notifications","select=id,kind&is_read=eq.false&order=created_at.desc&limit=99")
                    val ids=notices.map{it.id()}.toSet()
                    if(initialized){
                        val arrivals=notices.filter{it.id() !in known}
                        if(arrivals.isNotEmpty())vm.sound(if(arrivals.any{it.s("kind")=="message"})SparkSounds.Event.MESSAGE else SparkSounds.Event.NOTICE)
                    }
                    known=ids;initialized=true
                    vm.unreadCount=notices.size
                    if(vm.page.name!="Home"){
                        val result=vm.api.request("/rest/v1/rpc/sparknew_unread_friend_posts?since_time=${Uri.encode(vm.lastHomeSeen())}")
                        val count=result.trim().toIntOrNull()?.coerceIn(0,99)?:0
                        if(count>vm.newPostCount&&postsInitialized)vm.sound(SparkSounds.Event.POST)
                        vm.newPostCount=count
                        postsInitialized=true
                    }else{vm.newPostCount=0;postsInitialized=true}
                }catch(e:CancellationException){throw e}catch(_:Exception){}
                delay(12000)
            }
        }
    }
}

@Composable fun FeedComposer(vm:SparkViewModel,onCreate:()->Unit) {
    Surface { Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
        Avatar(vm,vm.me!!,44) { vm.tab("Profile") }
        Surface(onClick=onCreate,shape=CircleShape,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.weight(1f).padding(horizontal=10.dp)) {
            Text("What's on your mind?",Modifier.padding(horizontal=18.dp,vertical=10.dp),fontSize=17.sp)
        }
        IconButton(onClick=onCreate,modifier=Modifier.size(32.dp)) { Icon(Icons.Outlined.Image,"Add photo",tint=MaterialTheme.colorScheme.onSurfaceVariant) }
    } }
}

@Composable fun NewPostScreen(vm:SparkViewModel,kind:String,community:String?,onClose:()->Unit) {
    var body by rememberSaveable { mutableStateOf("") }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var audience by rememberSaveable { mutableStateOf("public") }
    var audienceMenu by remember { mutableStateOf(false) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri=it }
    Dialog(onDismissRequest={if(vm.tasks==0)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(enabled=vm.tasks==0,onClick=onClose) { Icon(Icons.Outlined.Close,"Close composer") }
                    Text(if(kind=="story")"New story" else if(kind=="reel")"New reel" else "New post",fontWeight=FontWeight.Bold,fontSize=20.sp,modifier=Modifier.weight(1f),textAlign=TextAlign.Center)
                    Button(enabled=vm.tasks==0&&(body.isNotBlank()||uri!=null),shape=RoundedCornerShape(8.dp),onClick={vm.work { vm.api.createPost(body,uri,kind,audience,community);vm.sound(SparkSounds.Event.POST);vm.refresh();onClose() }}) { Text(if(vm.tasks>0)"Posting…" else "Post") }
                }
                if(vm.tasks>0)LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Avatar(vm,vm.me!!,58)
                    Column(Modifier.padding(start=12.dp)) {
                        Text(vm.me!!.s("display_name"),fontWeight=FontWeight.Bold,fontSize=19.sp)
                        Text(if(kind=="story")"Visible for 24 hours" else "Share a moment with your people",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Box(Modifier.fillMaxWidth().heightIn(min=160.dp)) {
                        if(body.isEmpty())Text("What's on your mind?",fontSize=23.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(value=body,onValueChange={if(it.length<=10000)body=it},textStyle=MaterialTheme.typography.headlineSmall.copy(color=MaterialTheme.colorScheme.onSurface),modifier=Modifier.fillMaxWidth())
                    }
                    uri?.let { selected ->
                        val context=LocalContext.current
                        val video=remember(selected) { context.contentResolver.getType(selected)?.startsWith("video/")==true }
                        if(video)Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant) { Row(Modifier.fillMaxWidth().padding(28.dp)) { Icon(Icons.Outlined.VideoFile,null);Text(" Video attached") } }
                        else AsyncImage(model=selected,contentDescription="Selected photo",modifier=Modifier.fillMaxWidth().height(260.dp),contentScale=ContentScale.Fit)
                        TextButton(onClick={uri=null}) { Text("Remove attachment") }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    ComposerTile("Gallery",Icons.Outlined.PhotoLibrary,Modifier.weight(1f)) { picker.launch(if(kind=="reel")"video/*" else "image/*") }
                    ComposerTile("Video",Icons.Outlined.Videocam,Modifier.weight(1f)) { picker.launch("video/*") }
                    ComposerTile("Audience",Icons.Outlined.Public,Modifier.weight(1f)) { audienceMenu=true }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box { AssistChip(onClick={audienceMenu=true},label={Text(if(audience=="private")"Only me" else audience.replaceFirstChar { it.uppercase() })},leadingIcon={Icon(Icons.Outlined.Public,null,Modifier.size(18.dp))})
                        DropdownMenu(expanded=audienceMenu,onDismissRequest={audienceMenu=false}) { listOf("public","friends","private").forEach { value->DropdownMenuItem(text={Text(if(value=="private")"Only me" else value.replaceFirstChar{it.uppercase()})},onClick={audience=value;audienceMenu=false}) } }
                    }
                    Spacer(Modifier.width(12.dp));Text("Photos optimized for faster loading",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
@Composable private fun ComposerTile(label:String,icon:ImageVector,modifier:Modifier,onClick:()->Unit) {
    Surface(onClick=onClick,modifier=modifier.height(78.dp),shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),shadowElevation=1.dp) {
        Column(verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) { Icon(icon,label);Spacer(Modifier.height(8.dp));Text(label,fontWeight=FontWeight.SemiBold,fontSize=13.sp) }
    }
}

@Composable fun ReelsScreen(vm:SparkViewModel) {
    var create by remember { mutableStateOf(false) }
    val feed=remember { FeedPager {vm.api.feed("reel",offset=it)} }
    val scope=rememberCoroutineScope()
    var selected by remember(vm.page.id){mutableStateOf<org.json.JSONObject?>(null)}
    LaunchedEffect(vm.page.id){if(vm.page.id.isNotBlank())try{selected=vm.api.rows("posts","${vm.api.postSelect}&id=eq.${vm.page.id}&kind=eq.reel").firstOrNull()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){vm.notice=e.message}}
    val videos=(listOfNotNull(selected)+feed.posts).distinctBy{it.id()}
    val loading=feed.loading
    val more=feed.more
    LaunchedEffect(vm.revision) {feed.load(refresh=true)}
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if(videos.isNotEmpty()) {
            val pager=rememberPagerState(pageCount={videos.size})
            LaunchedEffect(pager.currentPage,videos) {
                videos.getOrNull(pager.currentPage)?.let { post ->
                    if(post.s("author_id")!=vm.api.userId)runCatching {
                        vm.api.request("/rest/v1/rpc/sparknew_record_view","POST",json("content_id" to post.id()))
                    }
                }
            }
            LaunchedEffect(pager.currentPage,videos.size) { if(pager.currentPage>=videos.lastIndex-2&&more&&feed.error==null)feed.load() }
            VerticalPager(state=pager,key={videos[it].id()},modifier=Modifier.fillMaxSize()) { index ->
                val post=videos[index]
                var paused by remember(post.id()) { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) {
                    if(index==pager.currentPage)SparkVideo(vm,post.s("media_path"),Modifier.fillMaxSize(),loop=true,controls=false,paused=paused)
                    Box(Modifier.fillMaxSize().clickable { paused=!paused })
                    if(paused)Icon(Icons.Outlined.PlayCircle,"Resume reel",tint=Color.White,modifier=Modifier.align(Alignment.Center).size(68.dp))
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.8f)))).padding(top=30.dp,bottom=48.dp)) {
                        Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                            Avatar(vm,post.child("author"),36) { vm.go("Profile",post.s("author_id")) }
                            Text(post.child("author").s("display_name"),Modifier.padding(start=10.dp),color=Color.White,fontWeight=FontWeight.Bold)
                        }
                        Text(post.s("body"),Modifier.padding(horizontal=16.dp,vertical=8.dp),color=Color.White,maxLines=3)
                        PostActions(vm,post,white=true)
                    }
                }
            }
        }else if(!loading&&feed.error==null)Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally) { Icon(Icons.Outlined.SmartDisplay,null,tint=Color.White,modifier=Modifier.size(60.dp));Text("Share your first reel",color=Color.White,modifier=Modifier.padding(16.dp));Button(onClick={create=true}) { Text("Create reel") } }
        if(feed.error!=null)Button(onClick={scope.launch {feed.load(refresh=videos.isEmpty())}},modifier=Modifier.align(Alignment.Center)) {Text("Couldn't load reels. Retry")}
        if(loading&&videos.isEmpty())CircularProgressIndicator(Modifier.align(Alignment.Center),color=Color.White)
        Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.6f),Color.Transparent))).padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={vm.go("Menu")}) { Icon(Icons.Outlined.Menu,"Menu",tint=Color.White) }
            Text("Reels",color=Color.White,fontWeight=FontWeight.Bold,fontSize=26.sp,modifier=Modifier.weight(1f))
            IconButton(onClick={create=true}) { Icon(Icons.Outlined.PhotoCamera,"New reel",tint=Color.White) }
            IconButton(onClick={vm.go("Search")}) { Icon(Icons.Outlined.Search,"Search",tint=Color.White) }
        }
    }
    if(create)NewPostScreen(vm,"reel",null) { create=false }
}

@Composable fun ProfileSettingsScreen(vm:SparkViewModel) {
    LazyColumn {
        item { SettingsRow("Edit profile",Icons.Outlined.Edit) { vm.go("Edit profile") } }
        item { SettingsRow("Saved posts",Icons.Outlined.BookmarkBorder) { vm.go("Saved") } }
        item { SettingsRow("Blocked accounts",Icons.Outlined.Block) { vm.go("Blocked") } }
        item { SettingsRow("Find people",Icons.Outlined.Search) { vm.go("Search") } }
        item { SettingsRow("Request a verification badge",Icons.Outlined.Verified) { vm.go("Badge requests") } }
        item { SettingsRow("Dark mode",Icons.Outlined.DarkMode) { vm.dark=!vm.dark } }
        item { SettingsRow("Refresh profile",Icons.Outlined.Refresh) { vm.refresh();vm.back() } }
        item { SettingsRow("More settings",Icons.Outlined.Settings) { vm.go("Menu") } }
    }
}
@Composable private fun SettingsRow(label:String,icon:ImageVector,onClick:()->Unit) {
    Column { Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=18.dp,vertical=22.dp),verticalAlignment=Alignment.CenterVertically) { Icon(icon,null,Modifier.size(28.dp));Text(label,Modifier.padding(start=18.dp),fontSize=18.sp) };HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f)) }
}

@Composable fun FriendRequestRow(vm:SparkViewModel,request:JSONObject) {
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Avatar(vm,request.child("sender"),88) { vm.go("Profile",request.s("sender_id")) }
        Column(Modifier.weight(1f).padding(start=12.dp)) {
            Text(request.child("sender").s("display_name"),fontWeight=FontWeight.SemiBold,fontSize=19.sp)
            Text("${ago(request.s("created_at"))} · Sent you a request",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(enabled=vm.tasks==0,onClick={vm.work { vm.api.update("friendships","id=eq.${request.id()}",json("status" to "accepted"));vm.refresh() }},modifier=Modifier.weight(1f),shape=RoundedCornerShape(8.dp),contentPadding=PaddingValues(horizontal=8.dp)) { Text("Confirm") }
                FilledTonalButton(enabled=vm.tasks==0,onClick={vm.work { vm.api.delete("friendships","id=eq.${request.id()}");vm.refresh() }},modifier=Modifier.weight(1f),shape=RoundedCornerShape(8.dp),contentPadding=PaddingValues(horizontal=8.dp)) { Text("Delete") }
            }
        }
    }
}
