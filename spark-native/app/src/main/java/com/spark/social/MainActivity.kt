@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.spark.social
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
internal val Blue=Color(0xFF0866FF)
data class Page(val name:String,val id:String="",val title:String="")
class SparkViewModel(app:Application):AndroidViewModel(app) {
    val api=SparkApi.get(app)
    var me by mutableStateOf<JSONObject?>(null)
    var starting by mutableStateOf(true)
    var tasks by mutableIntStateOf(0)
    var revision by mutableIntStateOf(0)
    var notice by mutableStateOf<String?>(null)
    var dark by mutableStateOf(false)
    val stack=mutableStateListOf(Page("Home"))
    val page get()=stack.last()
    init {
        work {
            if(api.signedIn)me=api.ensureProfile()
            starting=false
        }
    }
    fun go(name:String,id:String="",title:String="") {
        stack.add(Page(name,id,title))
    }
    fun tab(name:String) {
        stack.clear()
        stack.add(Page(name,if(name=="Profile")api.userId else ""))
    }
    fun back() {
        if(stack.size>1)stack.removeAt(stack.lastIndex)
    }
    fun refresh() {
        revision++
    }
    fun work(block:suspend ()->Unit) {
        viewModelScope.launch {
            tasks++
            try {
                block()
            }catch(e:CancellationException) {
                throw e
            }catch(e:Exception) {
                notice=e.message?:"Something went wrong. Please retry."
                if(!api.signedIn)me=null
            }finally {
                tasks--
                starting=false
            }
        }
    }
    suspend fun authDone() {
        me=api.ensureProfile()
        tab("Home")
        refresh()
    }
}
class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm:SparkViewModel=viewModel()
            SparkTheme(vm.dark) {
                SparkApp(vm)
            }
        }
    }
}
@Composable fun SparkTheme(dark:Boolean,content:@Composable ()->Unit) {
    MaterialTheme(
        colorScheme=if(dark)darkColorScheme(primary=Color(0xFFB6BEFF), background=Color(0xFF10121B),surface=Color(0xFF1C1F2C))
            else lightColorScheme(primary=Blue,secondary=Color(0xFF0866FF),background=Color.White,surface=Color.White, onSurface=Color(0xFF101214)),
        shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(28.dp)),
        content=content
    )
}
@Composable fun SparkApp(vm:SparkViewModel) {
    var newPost by remember { mutableStateOf(false) }
    val snacks=remember {
        SnackbarHostState()
    }
    LaunchedEffect(vm.notice) {
        vm.notice?.let {
            snacks.showSnackbar(it)
            vm.notice=null
        }
    }
    BackHandler(vm.stack.size>1) {
        vm.back()
    }
    Scaffold(snackbarHost= {
        SnackbarHost(snacks)
    },topBar= {
        if(vm.me!=null)SparkTopBar(vm) { newPost=true }
    },bottomBar= {
        if(vm.me!=null&&vm.stack.size==1)SparkBottomBar(vm)
    }) {
        padding->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(vm.tasks>0)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(vm.starting)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                CircularProgressIndicator()
            }
            else if(vm.me==null)AuthScreen(vm)
            else {
                when(vm.page.name) {
                    "Home"->Feed(vm)
                    "Reels"->ReelsScreen(vm)
                    "Friends"->FriendsScreen(vm)
                    "Notifications"->NotificationsScreen(vm)
                    "Menu"->MenuScreen(vm)
                    "Profile"->ProfileScreen(vm,vm.page.id)
                    "Profile settings"->ProfileSettingsScreen(vm)
                    "Search"->SearchScreen(vm)
                    "Comments"->CommentsScreen(vm,vm.page.id)
                    "Chats"->ChatsScreen(vm)
                    "Chat"->ChatScreen(vm,vm.page.id)
                    "Groups","Pages"->CommunitiesScreen(vm,if(vm.page.name=="Groups")"group" else "page")
                    "Community"->CommunityScreen(vm,vm.page.id)
                    "Marketplace"->MarketplaceScreen(vm)
                    "Saved"->SavedScreen(vm)
                    "Blocked"->BlockedScreen(vm)
                }
            }
        }
    }
    if(vm.me!=null)IncomingCalls(vm)
    if(newPost&&vm.me!=null)NewPostScreen(vm,"post",null) { newPost=false }
}
@Composable fun AuthScreen(vm:SparkViewModel) {
    var register by rememberSaveable {
        mutableStateOf(false)
    }
    var name by rememberSaveable {
        mutableStateOf("")
    }
    var email by rememberSaveable {
        mutableStateOf("")
    }
    var password by rememberSaveable {
        mutableStateOf("")
    }
    var reset by remember {
        mutableStateOf(false)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)).background(Blue),contentAlignment=Alignment.Center) {
            Icon(Icons.Outlined.Bolt,null,tint=Color.White,modifier=Modifier.size(52.dp))
        }
        Text("spark",fontSize=48.sp,fontWeight=FontWeight.Bold,color=Blue)
        Text("Your people. Your everyday.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Text(if(register)"Create your account" else "Welcome back",fontSize=24.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if(register)Field(name, {
            name=it
        },"Full name")
        Field(email, {
            email=it
        },"Email address",keyboard=KeyboardType.Email)
        OutlinedTextField(password, {
            password=it
        },label= {
            Text("Password")
        },visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        Button(enabled=vm.tasks==0,onClick= {
            vm.work {
                if(register) {
                    if(vm.api.signup(name,email,password))vm.authDone() else {
                        vm.notice="Check your email to confirm your account, then sign in."
                        register=false
                    }
                }else {
                    vm.api.login(email,password)
                    vm.authDone()
                }
            }
        },modifier=Modifier.fillMaxWidth().height(50.dp)) {
            Text(if(register)"Create account" else "Log in")
        }
        TextButton(onClick= {
            register=!register
        }) {
            Text(if(register)"Already have an account? Log in" else "New to Spark? Create account")
        }
        TextButton(onClick= {
            reset=true
        }) {
            Text("Forgot password?")
        }
        Text("Connect with friends, share your moments and find your community.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
    }
    if(reset)ResetDialog(vm,email) {
        reset=false
    }
}
@Composable fun ResetDialog(vm:SparkViewModel,initial:String,onClose:()->Unit) {
    var email by remember {
        mutableStateOf(initial)
    }
    var code by remember {
        mutableStateOf("")
    }
    var password by remember {
        mutableStateOf("")
    }
    var sent by remember {
        mutableStateOf(false)
    }
    AlertDialog(onDismissRequest=onClose,title= {
        Text("Reset password")
    },text= {
        Column {
            Field(email, {
                email=it
            },"Email")
            if(sent) {
                Text("Use the recovery code from your email. If the email only has a link, reset through that link then log in.")
                Field(code, {
                    code=it
                },"Recovery code")
                OutlinedTextField(password, {
                    password=it
                },label= {
                    Text("New password")
                },visualTransformation=PasswordVisualTransformation())
            }
        }
    },confirmButton= {
        TextButton(enabled=vm.tasks==0,onClick= {
            vm.work {
                if(!sent) {
                    vm.api.recover(email)
                    sent=true
                    vm.notice="Recovery email requested."
                }else {
                    vm.api.resetPassword(email,code,password)
                    vm.authDone()
                    onClose()
                }
            }
        }) {
            Text(if(sent)"Reset" else "Send email")
        }
    },dismissButton= {
        TextButton(onClick=onClose) {
            Text("Close")
        }
    })
}
@Composable fun Field(value:String,onChange:(String)->Unit,label:String,lines:Int=1,keyboard:KeyboardType=KeyboardType.Text) {
    OutlinedTextField(value,onChange,label= {
        Text(label)
    },singleLine=lines==1,minLines=lines,modifier=Modifier.fillMaxWidth().padding(bottom=10.dp),keyboardOptions=KeyboardOptions(keyboardType=keyboard))
}
@Composable fun Empty(title:String,body:String,icon:ImageVector=Icons.Outlined.Bolt) {
    Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(icon,null,Modifier.size(48.dp),tint=Blue)
        Spacer(Modifier.height(12.dp))
        Text(title,fontSize=21.sp,fontWeight=FontWeight.Bold)
        Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
    }
}
@Composable fun Section(title:String,action:String?=null,onAction:()->Unit= {
}) {
    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
        if(action!=null)TextButton(onClick=onAction) {
            Text(action)
        }
    }
}
@Composable fun Rows(vm:SparkViewModel,key:String,loader:suspend ()->List<JSONObject>,content:@Composable (List<JSONObject>)->Unit) {
    var data by remember(key) {
        mutableStateOf<List<JSONObject>?>(null)
    }
    var failed by remember(key) {
        mutableStateOf(false)
    }
    LaunchedEffect(key,vm.revision) {
        try {
            data=loader()
            failed=false
        }catch(e:CancellationException) {
            throw e
        }catch(e:Exception) {
            failed=true
            vm.notice=e.message
        }
    }
    if(failed) {
        Empty("Couldn't load this page","Check your connection and tap Refresh.",Icons.Outlined.CloudOff)
    }else if(data==null) {
        Box(Modifier.fillMaxWidth().padding(40.dp),contentAlignment=Alignment.Center) {
            CircularProgressIndicator()
        }
    }else content(data!!)
}
@Composable fun Avatar(vm:SparkViewModel,profile:JSONObject,size:Int=44,onClick:()->Unit= {
}) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFFDCEAFF)).clickable(onClick=onClick),contentAlignment=Alignment.Center) {
        Text(profile.s("display_name").take(1).uppercase(),fontSize=(size/2).sp,color=Blue,fontWeight=FontWeight.Bold)
        if(profile.s("avatar_path").isNotEmpty())PrivateImage(vm,profile.s("avatar_path"),Modifier.fillMaxSize(),targetPx=(size*3).coerceIn(128,512))
    }
}
fun ago(raw:String):String=runCatching {
    val seconds=(Instant.now().epochSecond-Instant.parse(raw).epochSecond).coerceAtLeast(0)
    when {
        seconds<60->"Just now"
        seconds<3600->"${seconds/60}m"
        seconds<86400->"${seconds/3600}h"
        else->"${seconds/86400}d"
    }
}.getOrDefault("")
@Composable fun Feed(vm:SparkViewModel,kind:String="post",extra:String="",community:String?=null) {
    var compose by remember {
        mutableStateOf(false)
    }
    var offset by remember(kind,extra) {
        mutableIntStateOf(0)
    }
    var posts by remember(kind,extra) {
        mutableStateOf(emptyList<JSONObject>())
    }
    var loading by remember {
        mutableStateOf(true)
    }
    var more by remember {
        mutableStateOf(true)
    }
    LaunchedEffect(kind,extra,offset,vm.revision) {
        loading=true
        try {
            val loaded = mutableListOf<JSONObject>()
            for (pageOffset in 0..offset step 20) loaded.addAll(vm.api.feed(kind,extra,pageOffset))
            posts=loaded.distinctBy { it.id() }
            more=loaded.size >= offset+20
        }catch(e:Exception) {
            if(e is CancellationException)throw e
            vm.notice=e.message
        }finally {
            loading=false
        }
    }
    val listState=androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(listState.firstVisibleItemIndex,posts) {
        kotlinx.coroutines.delay(300)
        posts.drop((listState.firstVisibleItemIndex-2).coerceAtLeast(0)+1).take(2)
            .filter { it.s("media_type")=="image" }.forEach { post ->
                try { FastImages.bytes(vm.api,post.s("media_path")) } catch(e:CancellationException) { throw e } catch(_:Exception) {}
            }
    }
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing=loading&&posts.isNotEmpty(),onRefresh={vm.refresh()}) {
    LazyColumn(state=listState,contentPadding=PaddingValues(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
        item { FeedComposer(vm) { compose=true } }
        if(kind=="post"&&community==null)item { Stories(vm) }
        items(posts,key= {
            it.id()
        }) {
            PostCard(vm,it)
        }
        if(loading)item {
            Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        if(posts.isEmpty()&&!loading)item {
            Empty(if(kind=="reel")"Your next favorite moment" else "Start the conversation",if(kind=="reel")"Share your first video reel." else "There are no posts yet. Share a moment or invite a friend.")
        }
        if(more&&posts.isNotEmpty())item {
            TextButton(enabled=!loading,onClick= {
                offset+=20
            },modifier=Modifier.fillMaxWidth()) {
                Text("Load more")
            }
        }
    }
    }
    if(compose)ComposeDialog(vm,kind,community) {
        compose=false
        offset=0
    }
}
@Composable fun ComposeDialog(vm:SparkViewModel,kind:String,community:String?=null,onClose:()->Unit) {
    NewPostScreen(vm,kind,community,onClose)
}
@Composable fun PostCard(vm:SparkViewModel,post:JSONObject) {
    var menu by remember {
        mutableStateOf(false)
    }
    var react by remember {
        mutableStateOf(false)
    }
    var edit by remember {
        mutableStateOf(false)
    }
    var report by remember {
        mutableStateOf(false)
    }
    var deleting by remember {
        mutableStateOf(false)
    }
    val context=LocalContext.current
    val mine=post.rows("reactions").firstOrNull {
        it.s("user_id")==vm.api.userId
    }
    val author=post.child("author")
    Surface {
        Column {
            HorizontalDivider(thickness=6.dp,color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.8f))
            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Avatar(vm,author) {
                    vm.go("Profile",post.s("author_id"))
                }
                Column(Modifier.weight(1f).padding(start=10.dp).clickable {
                    vm.go("Profile",post.s("author_id"))
                }) {
                    Text(author.s("display_name").ifBlank {
                        "Spark member"
                    },fontWeight=FontWeight.Bold)
                    Text("${ago(post.s("created_at"))} · ${post.s("visibility")}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick= {
                        menu=true
                    }) {
                        Icon(Icons.Outlined.MoreHoriz,"Post options")
                    }
                    DropdownMenu(expanded=menu,onDismissRequest= {
                        menu=false
                    }) {
                        DropdownMenuItem(text= {
                            Text("Save post")
                        },onClick= {
                            menu=false
                            vm.work {
                                val existing=vm.api.rows("saved","post_id=eq.${post.id()}&user_id=eq.${vm.api.userId}")
                                if(existing.isEmpty())vm.api.insert("saved",json("post_id" to post.id(),"user_id" to vm.api.userId))
                                vm.notice="Post saved."
                            }
                        })
                        if(post.s("author_id")==vm.api.userId) {
                            DropdownMenuItem(text= {
                                Text("Edit post")
                            },onClick= {
                                menu=false
                                edit=true
                            })
                            DropdownMenuItem(text= {
                                Text("Delete post")
                            },onClick= {
                                menu=false
                                deleting=true
                            })
                        }else DropdownMenuItem(text= {
                            Text("Report post")
                        },onClick= {
                            menu=false
                            report=true
                        })
                    }
                }
            }
            if(post.s("media_path").isBlank()&&post.s("body").isNotBlank())Text(post.s("body"),Modifier.padding(start=14.dp,end=14.dp,bottom=14.dp),fontSize=16.sp)
            Media(vm,post.s("media_path"),post.s("media_type"),post=post)
            if(post.s("media_path").isNotBlank()&&post.s("body").isNotBlank())Text(post.s("body"),Modifier.padding(horizontal=14.dp,vertical=12.dp),fontSize=16.sp)
            Row(Modifier.fillMaxWidth().padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={react=true}) { Icon(Icons.Outlined.ThumbUp,"React",Modifier.size(24.dp),tint=if(mine!=null)Blue else MaterialTheme.colorScheme.onSurfaceVariant);Text(" ${post.rows("reactions").size}",color=MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick={vm.go("Comments",post.id())}) { Icon(Icons.Outlined.ChatBubbleOutline,"Comments",Modifier.size(24.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant);Text(" ${post.rows("comments").firstOrNull()?.optInt("count")?:0}",color=MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain";putExtra(Intent.EXTRA_TEXT,"${author.s("display_name")} on Spark:\n${post.s("body")}") },"Share post"))}) { Icon(Icons.Outlined.Share,"Share post",tint=MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick={vm.work { if(vm.api.rows("saved","post_id=eq.${post.id()}&user_id=eq.${vm.api.userId}").isEmpty())vm.api.insert("saved",json("post_id" to post.id(),"user_id" to vm.api.userId));vm.notice="Post saved." }}) { Icon(Icons.Outlined.BookmarkBorder,"Save post",tint=MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if(react)AlertDialog(onDismissRequest= {
        react=false
    },title= {
        Text("React to this post")
    },text= {
        Column {
            listOf("like" to "👍","love" to "❤️","haha" to "😆","wow" to "😮","sad" to "😢","angry" to "😠").forEach {
                (value,emoji)->TextButton(enabled=vm.tasks==0,onClick= {
                    vm.work {
                        if(mine==null)vm.api.insert("reactions",json("post_id" to post.id(),"user_id" to vm.api.userId,"reaction" to value))else if(mine.s("reaction")==value)vm.api.delete("reactions","post_id=eq.${post.id()}&user_id=eq.${vm.api.userId}")else vm.api.update("reactions","post_id=eq.${post.id()}&user_id=eq.${vm.api.userId}",json("reaction" to value))
                        react=false
                        vm.refresh()
                    }
                }) {
                    Text("$emoji  $value")
                }
            }
        }
    },confirmButton= {
        TextButton(onClick= {
            react=false
        }) {
            Text("Close")
        }
    })
    if(edit)TextForm("Edit post",listOf("Post" to post.s("body")),vm, {
        edit=false
    }) {
        values->vm.api.update("posts","id=eq.${post.id()}",json("body" to values[0]))
        edit=false
        vm.refresh()
    }
    if(report)ReportDialog(vm,"post",post.id()) {
        report=false
    }
    if(deleting)Confirm("Delete this post?","This removes the post and its comments.", {
        deleting=false
    }) {
        vm.work {
            vm.api.delete("posts","id=eq.${post.id()}")
            deleting=false
            vm.refresh()
        }
    }
}
@Composable fun TextForm(title:String,fields:List<Pair<String,String>>,vm:SparkViewModel,onClose:()->Unit,onSave:suspend(List<String>)->Unit) {
    val values=remember {
        mutableStateListOf<String>().apply {
            addAll(fields.map {
                it.second
            })
        }
    }
    AlertDialog(onDismissRequest=onClose,title= {
        Text(title)
    },text= {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            fields.forEachIndexed {
                i,f->Field(values[i], {
                    values[i]=it
                },f.first,if(f.first in listOf("Bio","Description","Post","Reason"))3 else 1)
            }
        }
    },confirmButton= {
        Button(enabled=vm.tasks==0,onClick= {
            vm.work {
                onSave(values.toList())
            }
        }) {
            Text("Save")
        }
    },dismissButton= {
        TextButton(onClick=onClose) {
            Text("Cancel")
        }
    })
}
@Composable fun Confirm(title:String,body:String,onClose:()->Unit,onYes:()->Unit) {
    AlertDialog(onDismissRequest=onClose,title= {
        Text(title)
    },text= {
        Text(body)
    },confirmButton= {
        TextButton(onClick=onYes) {
            Text("Confirm")
        }
    },dismissButton= {
        TextButton(onClick=onClose) {
            Text("Cancel")
        }
    })
}
@Composable fun ReportDialog(vm:SparkViewModel,type:String,id:String,onClose:()->Unit) {
    TextForm("Report $type",listOf("Reason" to ""),vm,onClose) {
        v->require(v[0].isNotBlank()) {
            "Please enter a reason."
        }
        vm.api.insert("reports",json("reporter_id" to vm.api.userId,"target_type" to type,"target_id" to id,"reason" to v[0]))
        vm.notice="Report submitted."
        onClose()
    }
}
@Composable fun Person(vm:SparkViewModel,p:JSONObject,detail:String="",actions:@Composable RowScope.()->Unit= {
}) {
    Surface {
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Avatar(vm,p,52) {
                vm.go("Profile",p.id())
            }
            Column(Modifier.weight(1f).padding(horizontal=12.dp).clickable {
                vm.go("Profile",p.id())
            }) {
                Text(p.s("display_name").ifBlank {
                    "Spark member"
                },fontWeight=FontWeight.SemiBold)
                if(detail.isNotBlank())Text(detail,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            actions()
        }
    }
}
@Composable fun FriendsScreen(vm:SparkViewModel) {
    Rows(vm,"friendships", {
        vm.api.rows("friendships","select=*,sender:sparknew_profiles!sender_id(*),receiver:sparknew_profiles!receiver_id(*)&order=created_at.desc")
    }) {
        friends->
        val incoming=friends.filter {
            it.s("receiver_id")==vm.api.userId&&it.s("status")=="pending"
        }
        val outgoing=friends.filter {
            it.s("sender_id")==vm.api.userId&&it.s("status")=="pending"
        }
        val accepted=friends.filter {
            it.s("status")=="accepted"
        }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            item {
                Row(Modifier.padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick={vm.go("Search")},label={Text("Find friends")},shape=CircleShape)
                    AssistChip(onClick={vm.notice="${accepted.size} friends · ${incoming.size} requests"},label={Text("Your friends · ${accepted.size}")},shape=CircleShape)
                }
            }
            item {
                Section("Requests · ${incoming.size}")
            }
            items(incoming,key= {
                "i"+it.id()
            }) {
                f->FriendRequestRow(vm,f)
            }
            if(incoming.isEmpty())item {
                Text("No new requests",Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Section("Your friends · ${accepted.size}")
            }
            items(accepted,key= {
                "a"+it.id()
            }) {
                f->val p=f.child(if(f.s("sender_id")==vm.api.userId)"receiver" else "sender")
                Person(vm,p,"Friends") {
                    IconButton(onClick= {
                        vm.work {
                            val chat=vm.api.conversation(p.id())
                            vm.go("Chat",chat.id(),p.s("display_name"))
                        }
                    }) {
                        Icon(Icons.Outlined.Chat,"Message")
                    }
                }
            }
            if(outgoing.isNotEmpty())item {
                Section("Sent requests")
            }
            items(outgoing,key= {
                "o"+it.id()
            }) {
                f->Person(vm,f.child("receiver"),"Pending") {
                    TextButton(onClick= {
                        vm.work {
                            vm.api.delete("friendships","id=eq.${f.id()}")
                            vm.refresh()
                        }
                    }) {
                        Text("Cancel")
                    }
                }
            }
            if(friends.isEmpty())item {
                Empty("Find your people","Search for a friend by name and send a request.",Icons.Outlined.People)
            }
        }
    }
}
@Composable fun SearchScreen(vm:SparkViewModel) {
    var search by rememberSaveable {
        mutableStateOf("")
    }
    var query by remember {
        mutableStateOf("")
    }
    LaunchedEffect(search) {
        delay(350)
        query=search
    }
    Column {
        Field(search, {
            search=it
        },"Search people by name")
        Rows(vm,"search:$query", {
            val term=query.replace(Regex("[%_*(),.]")," ").trim()
            vm.api.rows("profiles","id=neq.${vm.api.userId}&order=display_name&limit=50"+(if(term.isBlank())""else "&display_name=ilike."+Uri.encode("%$term%")))
        }) {
            people->LazyColumn {
                items(people,key= {
                    it.id()
                }) {
                    p->Person(vm,p,p.s("bio")) {
                        IconButton(onClick= {
                            vm.go("Profile",p.id())
                        }) {
                            Icon(Icons.Outlined.PersonAdd,"View profile")
                        }
                    }
                }
                if(people.isEmpty())item {
                    Empty("No people found","Try a different name, or invite a friend to join Spark.")
                }
            }
        }
    }
}
@Composable fun ProfileScreen(vm:SparkViewModel,id:String) {
    var profileFilter by remember { mutableStateOf("All") }
    var newStory by remember { mutableStateOf(false) }
    if(newStory)ComposeDialog(vm,"story") { newStory=false }
    var previewPhoto by remember { mutableStateOf("") }
    if(previewPhoto.isNotBlank())PhotoDialog(vm,previewPhoto) { previewPhoto="" }
    var edit by remember {
        mutableStateOf(false)
    }
    var report by remember {
        mutableStateOf(false)
    }
    var block by remember {
        mutableStateOf(false)
    }
    var photoField by remember {
        mutableStateOf("avatar_path")
    }
    val own=id==vm.api.userId
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        uri->if(uri!=null)vm.work {
            val media=vm.api.upload(uri)
            try {
                require(media.second=="image") {
                    "Choose an image."
                }
                vm.api.update("profiles","id=eq.$id",json(photoField to media.first))
                vm.me=vm.api.ensureProfile()
                vm.refresh()
            }catch(e:Exception) {
                if(e is IllegalArgumentException || (e is ApiException && e.status in 400..499))runCatching {
                    vm.api.removeMedia(media.first)
                }
                throw e
            }
        }
    }
    Rows(vm,"profile:$id", {
        vm.api.rows("profiles","id=eq.$id")
    }) {
        rows->val p=rows.firstOrNull()
        if(p==null)Empty("Profile unavailable","This profile is unavailable or blocked.")else {
            Rows(vm,"profileposts:$id", {
                vm.api.rows("posts","select=*,author:sparknew_profiles!author_id(*),reactions:sparknew_reactions(*),comments:sparknew_comments(count)&author_id=eq.$id&kind=in.(post,reel)&order=created_at.desc&limit=40")
            }) {
                posts->LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    item {
                        Surface {
                            Column {
                                Box(Modifier.fillMaxWidth().height(240.dp).background(Color(0xFFBBCBD8)).clickable {
                                    if(p.s("cover_path").isNotBlank())previewPhoto=p.s("cover_path")
                                    else if(own) { photoField="cover_path";pick.launch("image/*") }
                                }) {
                                    if(p.s("cover_path").isNotBlank())PrivateImage(vm,p.s("cover_path"),Modifier.fillMaxSize())else Text("Make room for good moments.",Modifier.align(Alignment.Center).padding(24.dp),color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold)
                                    Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.35f),Color.Transparent))),verticalAlignment=Alignment.CenterVertically) {
                                        IconButton(onClick={if(vm.stack.size>1)vm.back() else vm.go("Menu")}) { Icon(if(vm.stack.size>1)Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Menu,"Back or menu",tint=Color.White) }
                                        Spacer(Modifier.weight(1f))
                                        IconButton(onClick={vm.go("Search")}) { Icon(Icons.Outlined.Search,"Search",tint=Color.White) }
                                        if(own)IconButton(onClick={vm.go("Profile settings")}) { Icon(Icons.Outlined.MoreHoriz,"Profile settings",tint=Color.White) }
                                    }
                                    if(own)IconButton(onClick={photoField="cover_path";pick.launch("image/*")},modifier=Modifier.align(Alignment.BottomEnd).padding(10.dp).background(Color.Black.copy(alpha=.4f),CircleShape)) { Icon(Icons.Outlined.PhotoCamera,"Change cover",tint=Color.White) }
                                }
                                Column(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                    Box(Modifier.offset(y=(-48).dp).border(5.dp,MaterialTheme.colorScheme.surface,CircleShape).padding(5.dp)) {
                                    Avatar(vm,p,146) {
                                        if(p.s("avatar_path").isNotBlank())previewPhoto=p.s("avatar_path")
                                        else if(own) {
                                            photoField="avatar_path"
                                            pick.launch("image/*")
                                        }
                                    }
                                    if(own)IconButton(onClick={photoField="avatar_path";pick.launch("image/*")},modifier=Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surfaceVariant,CircleShape)) { Icon(Icons.Outlined.PhotoCamera,"Change profile photo") }
                                    }
                                    Text(p.s("display_name"),modifier=Modifier.offset(y=(-26).dp),fontSize=28.sp,fontWeight=FontWeight.Bold)
                                    if(p.s("bio").isNotBlank())Text(p.s("bio"),Modifier.padding(vertical=8.dp))
                                    Rows(vm,"profilefollows:$id", {
                                        vm.api.rows("follows","or=(follower_id.eq.$id,following_id.eq.$id)")
                                    }) {
                                        follows->Text("${follows.count{it.s("following_id")==id}} followers · ${follows.count{it.s("follower_id")==id}} following",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        if(!own) {
                                            val following=follows.any {
                                                it.s("follower_id")==vm.api.userId&&it.s("following_id")==id
                                            }
                                            TextButton(onClick= {
                                                vm.work {
                                                    if(following)vm.api.delete("follows","follower_id=eq.${vm.api.userId}&following_id=eq.$id")else vm.api.insert("follows",json("follower_id" to vm.api.userId,"following_id" to id))
                                                    vm.refresh()
                                                }
                                            }) {
                                                Text(if(following)"Unfollow" else "Follow")
                                            }
                                        }
                                    }
                                    if(own)Row(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Button(onClick={edit=true},modifier=Modifier.weight(1f),shape=RoundedCornerShape(8.dp)) { Icon(Icons.Outlined.Edit,null,Modifier.size(18.dp));Text(" Edit profile") }
                                        FilledTonalButton(onClick={newStory=true},modifier=Modifier.weight(1f),shape=RoundedCornerShape(8.dp)) { Icon(Icons.Outlined.AddCircleOutline,null,Modifier.size(18.dp));Text(" Add to story") }
                                    }else {
                                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                            Button(onClick= {
                                                vm.work {
                                                    val c=vm.api.conversation(id)
                                                    vm.go("Chat",c.id(),p.s("display_name"))
                                                }
                                            }) {
                                                Text("Message")
                                            }
                                            OutlinedButton(onClick= {
                                                block=true
                                            }) {
                                                Text("Block")
                                            }
                                            TextButton(onClick= {
                                                report=true
                                            }) {
                                                Text("Report")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if(!own)item {
                        Rows(vm,"relationship:$id", {
                            vm.api.rows("friendships","or=(and(sender_id.eq.${vm.api.userId},receiver_id.eq.$id),and(sender_id.eq.$id,receiver_id.eq.${vm.api.userId}))")
                        }) {
                            fs->val f=fs.firstOrNull()
                            Row(Modifier.padding(horizontal=16.dp)) {
                                Button(enabled=vm.tasks==0,onClick= {
                                    vm.work {
                                        if(f==null)vm.api.insert("friendships",json("sender_id" to vm.api.userId,"receiver_id" to id))else if(f.s("status")=="pending"&&f.s("receiver_id")==vm.api.userId)vm.api.update("friendships","id=eq.${f.id()}",json("status" to "accepted"))else vm.api.delete("friendships","id=eq.${f.id()}")
                                        vm.refresh()
                                    }
                                }) {
                                    Text(when {
                                        f==null->"Add friend"
                                        f.s("status")=="accepted"->"Unfriend"
                                        f.s("receiver_id")==vm.api.userId->"Accept request"
                                        else->"Cancel request"
                                    })
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            listOf("All","Reels","Photos").forEach { label -> FilterChip(selected=profileFilter==label,onClick={profileFilter=label},shape=CircleShape,label={Text(label,fontWeight=FontWeight.SemiBold)}) }
                            if(own)TextButton(onClick={vm.go("Profile settings")}) { Text("More") }
                        }
                    }
                    items(posts.filter { profileFilter=="All" || (profileFilter=="Reels"&&it.s("media_type")=="video") || (profileFilter=="Photos"&&it.s("media_type")=="image") },key= {
                        it.id()
                    }) {
                        PostCard(vm,it)
                    }
                    if(posts.isEmpty())item {
                        Empty("No posts to show","New posts will appear here.")
                    }
                }
            }
            if(edit)TextForm("Edit profile",listOf("Name" to p.s("display_name"),"Bio" to p.s("bio")),vm, {
                edit=false
            }) {
                v->require(v[0].isNotBlank()) {
                    "Enter your name."
                }
                vm.api.update("profiles","id=eq.$id",json("display_name" to v[0].trim(),"bio" to v[1]))
                vm.me=vm.api.ensureProfile()
                edit=false
                vm.refresh()
            }
        }
    }
    if(report)ReportDialog(vm,"profile",id) {
        report=false
    }
    if(block)Confirm("Block this person?","You will no longer see each other's profiles, posts or messages.", {
        block=false
    }) {
        vm.work {
            vm.api.insert("blocks",json("owner_id" to vm.api.userId,"target_id" to id))
            FastImages.clear()
            block=false
            vm.back()
            vm.refresh()
        }
    }
}
@Composable fun CommentsScreen(vm:SparkViewModel,post:String) {
    var body by rememberSaveable {
        mutableStateOf("")
    }
    Column {
        Box(Modifier.weight(1f)) {
            Rows(vm,"comments:$post", {
                vm.api.rows("comments","select=*,author:sparknew_profiles!author_id(*)&post_id=eq.$post&order=created_at.asc&limit=200")
            }) {
                comments->LazyColumn {
                    items(comments,key= {
                        it.id()
                    }) {
                        c->Row(Modifier.fillMaxWidth().padding(12.dp)) {
                            Avatar(vm,c.child("author"),36) {
                                vm.go("Profile",c.s("author_id"))
                            }
                            Column(Modifier.weight(1f).padding(start=10.dp)) {
                                Surface(shape=RoundedCornerShape(16.dp)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(c.child("author").s("display_name"),fontWeight=FontWeight.Bold)
                                        Text(c.s("body"))
                                    }
                                }
                                Text(ago(c.s("created_at")),fontSize=12.sp)
                            }
                            if(c.s("author_id")==vm.api.userId)IconButton(onClick= {
                                vm.work {
                                    vm.api.delete("comments","id=eq.${c.id()}")
                                    vm.refresh()
                                }
                            }) {
                                Icon(Icons.Outlined.Delete,"Delete comment")
                            }
                        }
                    }
                    if(comments.isEmpty())item {
                        Empty("Join the conversation","Be the first to leave a comment.",Icons.Outlined.Comment)
                    }
                }
            }
        }
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            OutlinedTextField(body, {
                body=it
            },placeholder= {
                Text("Write a comment…")
            },modifier=Modifier.weight(1f))
            IconButton(enabled=vm.tasks==0&&body.isNotBlank(),onClick= {
                vm.work {
                    vm.api.insert("comments",json("post_id" to post,"author_id" to vm.api.userId,"body" to body.trim()))
                    body=""
                    vm.refresh()
                }
            }) {
                Icon(Icons.AutoMirrored.Outlined.Send,"Send comment",tint=Blue)
            }
        }
    }
}
@Composable fun ChatsScreen(vm:SparkViewModel) {
    Rows(vm,"chats", {
        vm.api.rows("conversations","select=*,a:sparknew_profiles!user_a(*),b:sparknew_profiles!user_b(*)&order=created_at.desc&limit=100")
    }) {
        chats->LazyColumn {
            item {
                Section("Chats","New message") {
                    vm.go("Search")
                }
            }
            items(chats,key= {
                it.id()
            }) {
                c->val other=c.child(if(c.s("user_a")==vm.api.userId)"b" else "a")
                Surface(onClick= {
                    vm.go("Chat",c.id(),other.s("display_name"))
                }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                        Avatar(vm,other,54)
                        Column(Modifier.padding(start=12.dp)) {
                            Text(other.s("display_name"),fontWeight=FontWeight.Bold)
                            Text("Open conversation",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if(chats.isEmpty())item {
                Empty("Say hello","Find a friend and start a conversation.",Icons.Outlined.Chat)
            }
        }
    }
}
@Composable fun ChatScreen(vm:SparkViewModel,id:String) {
    var messages by remember(id) {
        mutableStateOf(emptyList<JSONObject>())
    }
    var text by rememberSaveable(id) {
        mutableStateOf("")
    }
    var uri by remember {
        mutableStateOf<Uri?>(null)
    }
    var limit by remember(id) {
        mutableIntStateOf(60)
    }
    var error by remember {
        mutableStateOf(false)
    }
    var conversation by remember(id) {
        mutableStateOf<JSONObject?>(null)
    }
    val lifecycle=LocalLifecycleOwner.current
    val context=LocalContext.current
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        uri=it
    }
    LaunchedEffect(id) {
        try {
            conversation=vm.api.rows("conversations","id=eq.$id").firstOrNull()
        }catch(e:Exception) {
            vm.notice=e.message
        }
    }
    LaunchedEffect(id,vm.revision,limit,lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) {
                try {
                    messages=vm.api.rows("messages","conversation_id=eq.$id&order=created_at.desc,id.desc&limit=$limit").reversed()
                    error=false
                }catch(e:CancellationException) {
                    throw e
                }catch(e:Exception) {
                    if(!error)vm.notice=e.message
                    error=true
                }
                delay(4000)
            }
        }
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(if(error)"Connection interrupted" else "Private conversation",fontSize=12.sp,modifier=Modifier.weight(1f))
            listOf(false,true).forEach {
                video->IconButton(enabled=conversation!=null&&vm.tasks==0,onClick= {
                    vm.work {
                        val c=conversation!!
                        val other=if(c.s("user_a")==vm.api.userId)c.s("user_b")else c.s("user_a")
                        val call=vm.api.insert("calls",json("conversation_id" to id,"caller_id" to vm.api.userId,"callee_id" to other,"video" to video)).first()
                        context.startActivity(Intent(context,CallActivity::class.java).putExtra("call_id",call.id()).putExtra("video",video).putExtra("caller",true))
                    }
                }) {
                    Icon(if(video)Icons.Outlined.Videocam else Icons.Outlined.Call,if(video)"Video call"else"Audio call")
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),reverseLayout=true,contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(messages.reversed(),key= {
                it.id()
            }) {
                m->val own=m.s("sender_id")==vm.api.userId
                Row(Modifier.fillMaxWidth(),horizontalArrangement=if(own)Arrangement.End else Arrangement.Start) {
                    Surface(color=if(own)Blue else MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(18.dp),modifier=Modifier.widthIn(max=290.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            if(m.s("body").isNotBlank())Text(m.s("body"),color=if(own)Color.White else MaterialTheme.colorScheme.onSurface)
                            Media(vm,m.s("media_path"),m.s("media_type"))
                            Text(ago(m.s("created_at")),fontSize=10.sp,color=if(own)Color.White.copy(alpha=.75f)else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if(messages.size>=limit)item {
                TextButton(onClick= {
                    limit+=60
                }) {
                    Text("Load earlier messages")
                }
            }
            if(messages.isEmpty())item {
                Empty("Start with hello","Your messages will appear here.")
            }
        }
        if(uri!=null)TextButton(onClick= {
            uri=null
        }) {
            Text("Attachment selected · Remove")
        }
        Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick= {
                pick.launch("*/*")
            }) {
                Icon(Icons.Outlined.AddPhotoAlternate,"Attach media")
            }
            OutlinedTextField(text, {
                text=it
            },placeholder= {
                Text("Message…")
            },modifier=Modifier.weight(1f),maxLines=4,shape=RoundedCornerShape(24.dp))
            IconButton(enabled=vm.tasks==0&&(text.isNotBlank()||uri!=null),onClick= {
                vm.work {
                    vm.api.send(id,text,uri)
                    text=""
                    uri=null
                    vm.refresh()
                }
            }) {
                Icon(Icons.AutoMirrored.Outlined.Send,"Send",tint=Blue)
            }
        }
    }
}
@Composable fun IncomingCalls(vm:SparkViewModel) {
    var incoming by remember {
        mutableStateOf<JSONObject?>(null)
    }
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    LaunchedEffect(vm.api.userId,lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while(true) {
                try {
                    incoming=vm.api.rows("calls","callee_id=eq.${vm.api.userId}&status=eq.ringing&created_at=gt.${Uri.encode(Instant.now().minusSeconds(90).toString())}&order=created_at.desc&limit=1").firstOrNull()
                }catch(e:CancellationException) {
                    throw e
                }catch(_:Exception) {
                }
                delay(7000)
            }
        }
    }
    incoming?.let {
        call->AlertDialog(onDismissRequest= {
        },title= {
            Text(if(call.optBoolean("video"))"Incoming video call"else"Incoming audio call")
        },text= {
            Text("A Spark contact is calling you.")
        },confirmButton= {
            Button(onClick= {
                context.startActivity(Intent(context,CallActivity::class.java).putExtra("call_id",call.id()).putExtra("video",call.optBoolean("video")).putExtra("caller",false))
                incoming=null
            }) {
                Text("Answer")
            }
        },dismissButton= {
            TextButton(onClick= {
                vm.work {
                    vm.api.update("calls","id=eq.${call.id()}",json("status" to "declined"))
                    incoming=null
                }
            }) {
                Text("Decline")
            }
        })
    }
}
@Composable fun NotificationsScreen(vm:SparkViewModel) {
    Rows(vm,"notices", {
        vm.api.rows("notifications","select=*,actor:sparknew_profiles!actor_id(*)&order=created_at.desc&limit=100")
    }) {
        rows->LazyColumn {
            item {
                Section("Notifications","Mark all read") {
                    vm.work {
                        vm.api.update("notifications","recipient_id=eq.${vm.api.userId}",json("is_read" to true))
                        vm.refresh()
                    }
                }
            }
            items(rows,key= {
                it.id()
            }) {
                n->val kind=n.s("kind")
                Surface(color=if(n.optBoolean("is_read"))MaterialTheme.colorScheme.surface else Blue.copy(alpha=.09f),onClick= {
                    vm.work {
                        vm.api.update("notifications","id=eq.${n.id()}",json("is_read" to true))
                        when(kind) {
                            "message"->vm.go("Chat",n.s("target_id"),n.child("actor").s("display_name"))
                            "friend_request","friend_accepted"->vm.go("Friends")
                            else->vm.go("Comments",n.s("target_id"))
                        }
                        vm.refresh()
                    }
                }) {
                    Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                        Avatar(vm,n.child("actor"),48)
                        Column(Modifier.padding(start=12.dp)) {
                            Text(n.child("actor").s("display_name"),fontWeight=FontWeight.Bold)
                            Text(when(kind) {
                                "message"->"sent you a message"
                                "friend_request"->"sent a friend request"
                                "friend_accepted"->"accepted your friend request"
                                "reaction"->"reacted to your post"
                                else->"commented on your post"
                            })
                            Text(ago(n.s("created_at")),fontSize=12.sp,color=Blue)
                        }
                    }
                }
            }
            if(rows.isEmpty())item {
                Empty("You're all caught up","New reactions, requests and messages appear here.",Icons.Outlined.Notifications)
            }
        }
    }
}
@Composable fun MenuScreen(vm:SparkViewModel) {
    var logout by remember {
        mutableStateOf(false)
    }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Person(vm,vm.me!!,"See your profile")
        }
        listOf(Triple("Friends",Icons.Outlined.People,"Your connections"),Triple("Chats",Icons.Outlined.Chat,"Conversations"),Triple("Groups",Icons.Outlined.Groups,"Find your community"),Triple("Pages",Icons.Outlined.Flag,"Creators and businesses"),Triple("Marketplace",Icons.Outlined.Storefront,"Buy and sell nearby"),Triple("Saved",Icons.Outlined.BookmarkBorder,"Posts for later"),Triple("Blocked",Icons.Outlined.Block,"Manage blocked accounts")).forEach {
            (name,icon,subtitle)->item {
                Card(onClick= {
                    vm.go(name)
                },colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(icon,null,tint=Blue,modifier=Modifier.size(30.dp))
                        Column(Modifier.padding(start=16.dp)) {
                            Text(name,fontWeight=FontWeight.Bold)
                            Text(subtitle,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Dark mode",modifier=Modifier.weight(1f))
                Switch(checked=vm.dark,onCheckedChange= {
                    vm.dark=it
                })
            }
        }
        item {
            OutlinedButton(onClick= {
                logout=true
            },modifier=Modifier.fillMaxWidth()) {
                Text("Log out")
            }
        }
        item {
            Text("Spark 1.0 · A place for your people",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
        }
    }
    if(logout)Confirm("Log out?","You can sign back in with your email and password.", {
        logout=false
    }) {
        vm.work {
            try {
                vm.api.logout()
            }finally {
                vm.me=null
                vm.tab("Home")
                logout=false
            }
        }
    }
}
@Composable fun CommunitiesScreen(vm:SparkViewModel,kind:String) {
    var create by remember {
        mutableStateOf(false)
    }
    Rows(vm,"communities:$kind", {
        vm.api.rows("communities","kind=eq.$kind&order=created_at.desc&limit=100")
    }) {
        communities->LazyColumn {
            item {
                Section(if(kind=="group")"Groups"else"Pages","Create") {
                    create=true
                }
            }
            items(communities,key= {
                it.id()
            }) {
                c->Card(onClick= {
                    vm.go("Community",c.id(),c.s("name"))
                },modifier=Modifier.fillMaxWidth().padding(8.dp)) {
                    Row(Modifier.padding(20.dp)) {
                        Icon(if(kind=="group")Icons.Outlined.Groups else Icons.Outlined.Flag,null,tint=Blue,modifier=Modifier.size(42.dp))
                        Column(Modifier.padding(start=16.dp)) {
                            Text(c.s("name"),fontSize=20.sp,fontWeight=FontWeight.Bold)
                            Text(c.s("description"),maxLines=3,overflow=TextOverflow.Ellipsis)
                            Text("Public $kind",fontSize=12.sp,color=Blue)
                        }
                    }
                }
            }
            if(communities.isEmpty())item {
                Empty("Bring people together","Create the first $kind and share something you care about.")
            }
        }
    }
    if(create)TextForm("Create $kind",listOf("Name" to "","Description" to ""),vm, {
        create=false
    }) {
        v->require(v[0].isNotBlank()) {
            "Enter a name."
        }
        val c=vm.api.insert("communities",json("owner_id" to vm.api.userId,"name" to v[0].trim(),"description" to v[1],"kind" to kind)).first()
        vm.api.insert("memberships",json("community_id" to c.id(),"user_id" to vm.api.userId))
        create=false
        vm.go("Community",c.id(),c.s("name"))
        vm.refresh()
    }
}
@Composable fun CommunityScreen(vm:SparkViewModel,id:String) {
    Rows(vm,"community:$id", {
        vm.api.rows("communities","id=eq.$id")
    }) {
        rows->val c=rows.firstOrNull()
        if(c==null)Empty("Community unavailable","This community is no longer available.")else Column {
            Text(c.s("description"),Modifier.padding(16.dp))
            Rows(vm,"membership:$id", {
                vm.api.rows("memberships","community_id=eq.$id")
            }) {
                members->val joined=members.any {
                    it.s("user_id")==vm.api.userId
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("${members.size} ${if(c.s("kind")=="group")"members" else "followers"}",Modifier.weight(1f))
                    TextButton(enabled=vm.tasks==0,onClick= {
                        vm.work {
                            if(joined)vm.api.delete("memberships","community_id=eq.$id&user_id=eq.${vm.api.userId}")else vm.api.insert("memberships",json("community_id" to id,"user_id" to vm.api.userId))
                            vm.refresh()
                        }
                    }) {
                        Text(if(joined)"Leave"else if(c.s("kind")=="group")"Join group"else"Follow page")
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                Feed(vm,extra="&community_id=eq.$id",community=id)
            }
        }
    }
}
@Composable fun MarketplaceScreen(vm:SparkViewModel) {
    var create by remember {
        mutableStateOf(false)
    }
    var selected by remember {
        mutableStateOf<JSONObject?>(null)
    }
    Rows(vm,"marketplace", {
        vm.api.rows("listings","select=*,seller:sparknew_profiles!seller_id(*)&order=created_at.desc&limit=100")
    }) {
        listings->LazyColumn(contentPadding=PaddingValues(8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item {
                Section("Marketplace","Sell") {
                    create=true
                }
            }
            items(listings,key= {
                it.id()
            }) {
                l->Card(onClick= {
                    selected=l
                }) {
                    Column(Modifier.fillMaxWidth()) {
                        if(l.s("media_path").isNotBlank())Media(vm,l.s("media_path"),"image")
                        Column(Modifier.padding(16.dp)) {
                            Text("৳${l.s("price")}"+(if(l.optBoolean("sold"))" · Sold"else""),fontSize=23.sp,fontWeight=FontWeight.Bold)
                            Text(l.s("title"),fontSize=18.sp)
                            Text(l.s("location"),color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if(listings.isEmpty())item {
                Empty("Find something worth sharing","Create a listing to sell an item in your community.",Icons.Outlined.Storefront)
            }
        }
    }
    if(create)ListingDialog(vm) {
        create=false
    }
    selected?.let {
        l->AlertDialog(onDismissRequest= {
            selected=null
        },title= {
            Text(l.s("title"))
        },text= {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("৳${l.s("price")}",fontSize=24.sp,fontWeight=FontWeight.Bold)
                Text(l.s("description"))
                Text("Location: ${l.s("location")}")
                Text("Seller: ${l.child("seller").s("display_name")}")
                Text("Arrange payment and delivery directly with the seller.",fontSize=12.sp)
            }
        },confirmButton= {
            if(l.s("seller_id")==vm.api.userId)TextButton(onClick= {
                vm.work {
                    vm.api.update("listings","id=eq.${l.id()}",json("sold" to !l.optBoolean("sold")))
                    selected=null
                    vm.refresh()
                }
            }) {
                Text(if(l.optBoolean("sold"))"Mark available"else"Mark sold")
            }else Button(onClick= {
                vm.work {
                    val c=vm.api.conversation(l.s("seller_id"))
                    selected=null
                    vm.go("Chat",c.id(),l.child("seller").s("display_name"))
                }
            }) {
                Text("Message seller")
            }
        },dismissButton= {
            TextButton(onClick= {
                selected=null
            }) {
                Text("Close")
            }
        })
    }
}
@Composable fun ListingDialog(vm:SparkViewModel,onClose:()->Unit) {
    var title by remember {
        mutableStateOf("")
    }
    var description by remember {
        mutableStateOf("")
    }
    var price by remember {
        mutableStateOf("")
    }
    var location by remember {
        mutableStateOf("")
    }
    var uri by remember {
        mutableStateOf<Uri?>(null)
    }
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        uri=it
    }
    AlertDialog(onDismissRequest=onClose,title= {
        Text("Create listing")
    },text= {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Field(title, {
                title=it
            },"Title")
            Field(price, {
                price=it
            },"Price (BDT)",keyboard=KeyboardType.Decimal)
            Field(location, {
                location=it
            },"Location")
            Field(description, {
                description=it
            },"Description",3)
            TextButton(onClick= {
                pick.launch("image/*")
            }) {
                Text(if(uri==null)"Add photo"else"Change photo")
            }
        }
    },confirmButton= {
        Button(enabled=vm.tasks==0,onClick= {
            vm.work {
                val amount=price.toBigDecimalOrNull()
                require(title.isNotBlank()&&amount!=null&&amount.signum()>=0) {
                    "Enter a title and valid price."
                }
                var path:String?=null
                try {
                    if(uri!=null) {
                        val media=vm.api.upload(uri!!)
                        path=media.first
                        require(media.second=="image") {
                            "Choose an image."
                        }
                    }
                    vm.api.insert("listings",json("seller_id" to vm.api.userId,"title" to title.trim(),"description" to description,"price" to amount,"location" to location,"media_path" to path))
                    onClose()
                    vm.refresh()
                }catch(e:Exception) {
                    if(e is IllegalArgumentException || (e is ApiException && e.status in 400..499))path?.let {
                        runCatching {
                            vm.api.removeMedia(it)
                        }
                    }
                    throw e
                }
            }
        }) {
            Text("Publish")
        }
    },dismissButton= {
        TextButton(onClick=onClose) {
            Text("Cancel")
        }
    })
}
@Composable fun SavedScreen(vm:SparkViewModel) {
    Rows(vm,"saved", {
        val saved=vm.api.rows("saved","user_id=eq.${vm.api.userId}")
        if(saved.isEmpty())emptyList()else vm.api.rows("posts","select=*,author:sparknew_profiles!author_id(*),reactions:sparknew_reactions(*),comments:sparknew_comments(count)&id=in.(${saved.joinToString(","){it.s("post_id")}})&order=created_at.desc")
    }) {
        posts->LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(posts,key= {
                it.id()
            }) {
                p->Column {
                    TextButton(onClick= {
                        vm.work {
                            vm.api.delete("saved","post_id=eq.${p.id()}&user_id=eq.${vm.api.userId}")
                            vm.refresh()
                        }
                    }) {
                        Text("Remove from saved")
                    }
                    PostCard(vm,p)
                }
            }
            if(posts.isEmpty())item {
                Empty("Keep the good stuff","Save a post from its options menu to find it here.",Icons.Outlined.BookmarkBorder)
            }
        }
    }
}
@Composable fun BlockedScreen(vm:SparkViewModel) {
    Rows(vm,"blocked", {
        vm.api.rows("blocks","owner_id=eq.${vm.api.userId}")
    }) {
        blocks->LazyColumn {
            items(blocks,key= {
                it.s("target_id")
            }) {
                b->Surface {
                    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Blocked account",fontWeight=FontWeight.Bold)
                            Text(b.s("target_id").take(8),fontSize=12.sp)
                        }
                        TextButton(onClick= {
                            vm.work {
                                vm.api.delete("blocks","owner_id=eq.${vm.api.userId}&target_id=eq.${b.s("target_id")}")
                                vm.refresh()
                            }
                        }) {
                            Text("Unblock")
                        }
                    }
                }
            }
            if(blocks.isEmpty())item {
                Empty("No blocked accounts","Accounts you block appear here.",Icons.Outlined.Block)
            }
        }
    }
}
