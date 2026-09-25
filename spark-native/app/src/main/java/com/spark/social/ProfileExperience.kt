@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.spark.social

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

fun identityPatch(field:String,value:String):JSONObject {
    require(field=="display_name" || field=="bio")
    val trimmed=value.trim()
    require(if(field=="display_name")trimmed.length in 1..80 else trimmed.length<=400)
    return json(field to trimmed)
}
@Composable fun IdentityEditor(field:String,initial:String,onClose:()->Unit,onSave:suspend(String)->Unit) {
    val isName=field=="display_name"
    val label=if(isName)"Name" else "Bio"
    val limit=if(isName)80 else 400
    var value by rememberSaveable(field){mutableStateOf(initial)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var discard by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val close={if(!busy){if(value!=initial)discard=true else onClose()}}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                TopAppBar(title={Text("Edit ${label.lowercase()}",fontWeight=FontWeight.Bold)},navigationIcon={IconButton(onClick=close,enabled=!busy){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back")}},actions={
                    TextButton(enabled=!busy&&value!=initial&&value.length<=limit&&(!isName||value.isNotBlank()),onClick={scope.launch{
                        busy=true;error=null
                        try {onSave(value.trim());onClose()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Couldn't save. Try again."}finally{busy=false}
                    }}){Text(if(busy)"Saving…" else "Save",fontWeight=FontWeight.Bold)}
                })
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    Text(if(isName)"How people know you" else "A little about you",fontSize=24.sp,fontWeight=FontWeight.Bold)
                    Text(if(isName)"Choose the name people see on your profile, posts and messages." else "Share what matters to you. Your bio appears below your profile name.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value,{value=it},Modifier.fillMaxWidth(),label={Text(label)},singleLine=isName,minLines=if(isName)1 else 5,enabled=!busy,isError=value.length>limit,supportingText={Text("${value.length}/$limit",Modifier.fillMaxWidth(),textAlign=TextAlign.End)},shape=RoundedCornerShape(14.dp))
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Icon(Icons.Outlined.Public,null,Modifier.size(20.dp));Text("Visible to everyone on Spark",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                }
            }
        }
    }
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("Discard changes?")},text={Text("Your changes haven't been saved.")},confirmButton={TextButton(onClick={discard=false;onClose()}){Text("Discard")}},dismissButton={TextButton(onClick={discard=false}){Text("Keep editing")}})
}
suspend fun SparkApi.profileCounts(id:String)=JSONArray(request("/rest/v1/rpc/sparknew_profile_counts","POST",json("profile_id" to id))).rows()
suspend fun SparkApi.profilePeople(id:String,kind:String,offset:Int=0,size:Int=20)=JSONArray(request("/rest/v1/rpc/sparknew_profile_people","POST",json("profile_id" to id,"connection_kind" to kind,"page_size" to size,"page_offset" to offset))).rows()

@Composable fun ProfileStats(counts:JSONObject,onPeople:(String)->Unit,onPosts:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
        listOf("followers","following","posts").forEachIndexed{index,key->
            if(index>0)Text(" · ",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${counts.optLong(key)} $key",Modifier.clickable{if(key=="posts")onPosts() else onPeople(key)}.padding(vertical=8.dp),fontSize=14.sp,fontWeight=FontWeight.Bold)
        }
    }
}
@Composable fun ProfileFriends(vm:SparkViewModel,id:String) {
    Rows(vm,"profile-friends:$id",{vm.api.profilePeople(id,"friends",size=8)}){people->
        Column(Modifier.fillMaxWidth().padding(vertical=12.dp)) {
            Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){Text("Friends",Modifier.weight(1f),fontSize=21.sp,fontWeight=FontWeight.Bold);TextButton(onClick={vm.go("Connections",id,"friends")}){Text("See all")}}
            if(people.isEmpty())Text("Friends will appear here",Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyRow(contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                items(people,key={it.id()}){person->Column(Modifier.width(82.dp).clickable{vm.go("Profile",person.id())},horizontalAlignment=Alignment.CenterHorizontally){Avatar(vm,person,76);Text(person.s("display_name"),Modifier.padding(top=8.dp),maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center)}}
            }
        }
    }
}
@Composable fun ProfileConnectionsScreen(vm:SparkViewModel,id:String,initialKind:String) {
    var kind by rememberSaveable(id){mutableStateOf(initialKind.ifBlank{"followers"})}
    var people by remember(id,kind){mutableStateOf<List<JSONObject>>(emptyList())}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var more by remember(id,kind){mutableStateOf(true)}
    var page by remember(id,kind){mutableIntStateOf(0)}
    var retry by remember{mutableIntStateOf(0)}
    LaunchedEffect(id,kind,page,retry,vm.revision){
        loading=true;error=null
        try{val rows=vm.api.profilePeople(id,kind,page*20);people=if(page==0)rows else (people+rows).distinctBy{it.id()};more=rows.size==20}
        catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{loading=false}
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("followers","following","friends").forEach{tab->FilterChip(selected=kind==tab,onClick={kind=tab},label={Text(tab.replaceFirstChar{it.uppercase()})})}}
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
            items(people,key={it.id()}){person->Row(Modifier.fillMaxWidth().clickable{vm.go("Profile",person.id())}.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Avatar(vm,person,56);Text(person.s("display_name"),Modifier.weight(1f).padding(horizontal=14.dp),fontWeight=FontWeight.SemiBold);Icon(Icons.Outlined.ChevronRight,null)}}
            if(loading)item{Box(Modifier.fillMaxWidth().padding(20.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}}
            if(error!=null)item{Column(Modifier.padding(16.dp)){Text(error!!);TextButton(onClick={retry++}){Text("Retry")}}}
            if(!loading&&error==null&&people.isEmpty())item{Empty("No $kind yet","New connections will appear here.")}
            if(!loading&&error==null&&more&&people.isNotEmpty())item{TextButton(onClick={page++},modifier=Modifier.fillMaxWidth()){Text("Load more")}}
        }
    }
}
@Composable fun ProfileDashboard(vm:SparkViewModel) {
    var period by rememberSaveable { mutableIntStateOf(28) }
    var section by rememberSaveable { mutableStateOf("Analytics") }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    val scope=rememberCoroutineScope()
    var summary by remember(period,vm.revision){mutableStateOf(JSONObject())}
    var content by remember(vm.revision){mutableStateOf<List<JSONObject>>(emptyList())}
    var loading by remember(period,vm.revision){mutableStateOf(true)}
    var error by remember(period,vm.revision){mutableStateOf<String?>(null)}
    LaunchedEffect(period,vm.revision) {
        loading=true;error=null
        try {
            val result=JSONObject(vm.api.request("/rest/v1/rpc/sparknew_content_analytics","POST",json("days_back" to period)))
            summary=result.optJSONObject("summary")?:JSONObject()
            content=result.optJSONArray("content")?.rows()?:emptyList()
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}
        finally{loading=false}
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("Analytics","Content","Community").forEach{tab->FilterChip(selected=section==tab,onClick={section=tab},label={Text(tab)})}
        }
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            item {
                Row(verticalAlignment=Alignment.CenterVertically){
                    Avatar(vm,vm.me?:JSONObject(),56)
                    Column(Modifier.padding(start=12.dp)){
                        VerifiedName(vm,vm.me?:JSONObject())
                        Text("Professional dashboard",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if(section=="Analytics") {
                item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    listOf(28,7,1).forEach{days->FilterChip(selected=period==days,onClick={period=days},label={Text(if(days==1)"Today" else "$days days")})}
                } }
                if(error!=null)item{Text("Couldn't load analytics: $error",color=MaterialTheme.colorScheme.error)}
                if(loading)item{CircularProgressIndicator()}
                else if(error==null) {
                    item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        DashboardMetric("Views",summary.optLong("views"),Modifier.weight(1f))
                        DashboardMetric("Engagement",summary.optLong("engagement"),Modifier.weight(1f))
                    } }
                    item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        DashboardMetric("Followers",summary.optLong("followers"),Modifier.weight(1f))
                        DashboardMetric("Posts & reels",summary.optLong("content_count"),Modifier.weight(1f))
                    } }
                    item { Text("Earnings are unavailable until monetisation is enabled.",color=MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if(section=="Community")item {
                Text("Manage verification requests and your community",fontWeight=FontWeight.Bold)
                TextButton(onClick={vm.go("Badge requests")}){Text("Verification badges")}
            }
            if(section!="Community") {
                item { Text("Content",fontSize=23.sp,fontWeight=FontWeight.Bold) }
                items(content,key={it.id()}){post->
                    OutlinedCard(Modifier.fillMaxWidth().clickable{selected=post}){
                        Column(Modifier.padding(16.dp)){
                            Text(if(post.s("kind")=="reel")"Reel" else "Post",fontWeight=FontWeight.Bold,color=Blue)
                            Text(post.s("body").ifBlank{"Media post"},maxLines=2)
                            Text("Views ${post.optLong("views")}  ·  Reactions ${post.optLong("reactions")}  ·  Comments ${post.optLong("comments")}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if(content.isEmpty()&&!loading&&error==null)item{Text("No posts or reels in this period.")}
            }
        }
    }
    selected?.let{post->AlertDialog(onDismissRequest={selected=null},title={Text(if(post.s("kind")=="reel")"Reel analytics" else "Post analytics")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(post.s("body").ifBlank{"Media post"},maxLines=3)
            Text("Views: ${post.optLong("views")}")
            Text("Reactions: ${post.optLong("reactions")}")
            Text("Comments: ${post.optLong("comments")}")
            Text("Engagement: ${post.optLong("reactions")+post.optLong("comments")}")
        }
    },confirmButton={TextButton(onClick={selected=null}){Text("Close")}})}
}
@Composable private fun DashboardMetric(label:String,count:Long,modifier:Modifier=Modifier){
    OutlinedCard(modifier){Column(Modifier.padding(16.dp)){Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(count.toString(),fontSize=25.sp,fontWeight=FontWeight.Bold)}}
}

@Composable fun ProfileHero(vm:SparkViewModel,profile:JSONObject,own:Boolean,category:String,counts:JSONObject,onPhoto:(String)->Unit,onChangePhoto:(String)->Unit,onStory:()->Unit,onPosts:()->Unit) {
    Surface {
        Column {
            Box(Modifier.fillMaxWidth().height(318.dp)) {
                Box(Modifier.fillMaxWidth().height(240.dp).background(MaterialTheme.colorScheme.surfaceVariant).clickable{onPhoto("cover_path")}) {
                    if(profile.s("cover_path").isNotBlank())PrivateImage(vm,profile.s("cover_path"),Modifier.fillMaxSize())
                    else Icon(Icons.Outlined.Landscape,null,Modifier.size(72.dp).align(Alignment.Center),tint=MaterialTheme.colorScheme.outline)
                    Row(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Black.copy(alpha=.35f),androidx.compose.ui.graphics.Color.Transparent))),verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={if(vm.stack.size>1)vm.back() else vm.go("Menu")}){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back",tint=androidx.compose.ui.graphics.Color.White)}
                        Spacer(Modifier.weight(1f))
                        if(own)IconButton(onClick={vm.go("Edit profile")}){Icon(Icons.Outlined.Edit,"Edit profile",tint=androidx.compose.ui.graphics.Color.White)}
                        IconButton(onClick={vm.go("Search")}){Icon(Icons.Outlined.Search,"Search",tint=androidx.compose.ui.graphics.Color.White)}
                        if(own)IconButton(onClick={vm.go("Profile settings")}){Icon(Icons.Outlined.MoreHoriz,"Profile settings",tint=androidx.compose.ui.graphics.Color.White)}
                    }
                    if(own)IconButton(onClick={onChangePhoto("cover_path")},modifier=Modifier.align(Alignment.BottomEnd).padding(end=12.dp,bottom=26.dp).background(MaterialTheme.colorScheme.surface,CircleShape)){Icon(Icons.Outlined.PhotoCamera,"Change cover photo")}
                }
                Surface(Modifier.fillMaxWidth().height(102.dp).align(Alignment.BottomCenter),shape=RoundedCornerShape(topStart=26.dp,topEnd=26.dp)){}
                Box(Modifier.align(Alignment.BottomCenter).border(5.dp,MaterialTheme.colorScheme.surface,CircleShape).padding(5.dp)) {
                    Avatar(vm,profile,168){onPhoto("avatar_path")}
                    if(own)IconButton(onClick={onChangePhoto("avatar_path")},modifier=Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surfaceVariant,CircleShape).border(3.dp,MaterialTheme.colorScheme.surface,CircleShape)){Icon(Icons.Outlined.PhotoCamera,"Change profile picture")}
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                VerifiedName(vm,profile,large=true)
                if(category.isNotBlank())Text(category,Modifier.padding(top=3.dp),fontSize=16.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                ProfileStats(counts,{vm.go("Connections",profile.id(),it)},onPosts)
                if(profile.s("bio").isNotBlank())Text(profile.s("bio"),Modifier.padding(bottom=18.dp),fontSize=17.sp,textAlign=TextAlign.Center)
                if(own)Row(Modifier.fillMaxWidth().padding(bottom=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={vm.go("Dashboard")},modifier=Modifier.weight(1.15f),shape=RoundedCornerShape(8.dp),contentPadding=PaddingValues(horizontal=8.dp,vertical=12.dp)){Icon(Icons.Outlined.BarChart,null,Modifier.size(20.dp));Text(" Dashboard",fontWeight=FontWeight.SemiBold)}
                    FilledTonalButton(onClick=onStory,modifier=Modifier.weight(1f),shape=RoundedCornerShape(8.dp),contentPadding=PaddingValues(horizontal=8.dp,vertical=12.dp)){Icon(Icons.Outlined.AddCircleOutline,null,Modifier.size(20.dp));Text(" Add to story",fontWeight=FontWeight.SemiBold)}
                }
                if(own)TextButton(onClick={vm.go("Edit profile")},modifier=Modifier.padding(bottom=10.dp)){Icon(Icons.Outlined.Edit,null,Modifier.size(18.dp));Text(" Edit profile")}
                if(!own)BadgeAwardAction(vm,profile)
            }
        }
    }
}

@Composable fun ProfileHighlights(vm:SparkViewModel,id:String,posts:List<JSONObject>) {
    val own=id==vm.api.userId
    var create by remember{mutableStateOf(false)}
    var photo by remember{mutableStateOf("")}
    var remove by remember{mutableStateOf<JSONObject?>(null)}
    if(photo.isNotBlank())PhotoDialog(vm,photo){photo=""}
    Rows(vm,"highlights:$id",{vm.api.rows("profile_highlights","select=*,post:sparknew_posts!post_id(media_path)&owner_id=eq.$id&order=created_at.desc&limit=30")}){highlights->
        if(own||highlights.isNotEmpty())Column(Modifier.fillMaxWidth().padding(vertical=14.dp)) {
            Text("Highlights",Modifier.padding(horizontal=16.dp,vertical=12.dp),fontSize=21.sp,fontWeight=FontWeight.Bold)
            LazyRow(contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                if(own)item {OutlinedCard(onClick={create=true},modifier=Modifier.width(112.dp).height(178.dp),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.AddCircleOutline,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary);Text("Create highlight",Modifier.padding(top=18.dp),fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)}
                }}
                items(highlights,key={it.id()}){highlight->
                    OutlinedCard(onClick={photo=highlight.optJSONObject("post")?.s("media_path").orEmpty()},modifier=Modifier.width(112.dp).height(178.dp)) {
                        Box(Modifier.fillMaxSize()) {
                            PrivateImage(vm,highlight.optJSONObject("post")?.s("media_path").orEmpty(),Modifier.fillMaxSize())
                            Text(highlight.s("title"),Modifier.align(Alignment.BottomStart).fillMaxWidth().background(androidx.compose.ui.graphics.Color.Black.copy(alpha=.5f)).padding(8.dp),color=androidx.compose.ui.graphics.Color.White,maxLines=2)
                            if(own)IconButton(onClick={remove=highlight},modifier=Modifier.align(Alignment.TopEnd)){Icon(Icons.Outlined.Close,"Remove ${highlight.s("title")}",tint=androidx.compose.ui.graphics.Color.White)}
                        }
                    }
                }
            }
        }
        if(create)HighlightEditor(vm,posts.filter{p->p.s("kind")=="post"&&p.s("media_type")=="image"&&highlights.none{it.s("post_id")==p.id()}},{create=false})
    }
    remove?.let{row->Confirm("Remove highlight?","The original post will stay on your profile.",{remove=null}){vm.work{vm.api.delete("profile_highlights","id=eq.${row.id()}");remove=null;vm.refresh()}}}
}
@Composable private fun HighlightEditor(vm:SparkViewModel,photos:List<JSONObject>,onClose:()->Unit) {
    var title by rememberSaveable{mutableStateOf("")}
    var selected by rememberSaveable{mutableStateOf("")}
    var saving by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    Dialog(onDismissRequest={if(!saving)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            TopAppBar(title={Text("Create highlight")},navigationIcon={IconButton(onClick=onClose,enabled=!saving){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back")}},actions={TextButton(enabled=!saving&&title.isNotBlank()&&title.length<=50&&selected.isNotBlank(),onClick={scope.launch{saving=true;try{vm.api.insert("profile_highlights",json("owner_id" to vm.api.userId,"post_id" to selected,"title" to title.trim()));vm.refresh();onClose()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{saving=false}}}){Text(if(saving)"Saving…" else "Save")}})
            OutlinedTextField(title,{title=it},Modifier.fillMaxWidth().padding(16.dp),label={Text("Highlight name")},singleLine=true,enabled=!saving,isError=title.length>50)
            Text("Choose a photo post. Its existing audience also applies to this highlight.",Modifier.padding(horizontal=16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let{Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)}
            LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(photos.isEmpty())item{Text("Add a photo post first, or load more posts on your profile to choose an older photo.")}
                items(photos,key={it.id()}){p->Row(Modifier.fillMaxWidth().clickable(enabled=!saving){selected=p.id()},verticalAlignment=Alignment.CenterVertically){PrivateImage(vm,p.s("media_path"),Modifier.size(76.dp));Text(p.s("body").ifBlank{"Photo"},Modifier.weight(1f).padding(12.dp),maxLines=2,overflow=TextOverflow.Ellipsis);RadioButton(selected==p.id(),onClick={selected=p.id()},enabled=!saving)}}
            }
        }}
    }
}
